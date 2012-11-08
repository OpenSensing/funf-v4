package edu.mit.media.funf.probe.builtin;

import java.util.HashMap;
import android.hardware.SensorManager;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.json.IJsonObject;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.Probe.Base;
import edu.mit.media.funf.probe.Probe.ContinuousProbe;
import edu.mit.media.funf.probe.Probe.PassiveProbe;
import edu.mit.media.funf.probe.Probe.RequiredFeatures;
import edu.mit.media.funf.probe.Probe.RequiredProbes;
import edu.mit.media.funf.probe.builtin.ProbeKeys.PedometerKeys;
import edu.mit.media.funf.util.LogUtil;

@Schedule.DefaultSchedule(interval=120, duration=15)
@RequiredFeatures("android.hardware.sensor.accelerometer")
@RequiredProbes(AccelerometerSensorProbe.class)
public class PedometerProbe extends Base implements ContinuousProbe, PassiveProbe, PedometerKeys{
	
	
	@Configurable
	private String sensitivityLevel = PedometerKeys.SENSITIVITY_LEVEL_LOW;
	
//	private final int[] sensitivities = new int[9];
	private final static String TAG = "PedometerProbe";
	
	// 1.97(extra high)  2.96(very high)  4.44(high)  6.66(higher)  10.00(medium)  15.00(lower. works best for me)  
	// 22.50(low)  33.75(very low)  50.62(extra low)
	
//    private float   mScale[] = new float[2];
    private float mScale;
    private float mYOffset;
    private float mLastValues[] = new float[3*2];
    private float mLastDirections[] = new float[3*2];
    
    private float mLastExtremes[][] = { new float[3*2], new float[3*2] };
    private float mLastDiff[] = new float[3*2];
    private int mLastMatch = -1;
    private float mLimit = 10;
    
    private static final HashMap<String, Float> sensitivities = new HashMap<String, Float>();
	static{ // some kind of magic number
		sensitivities.put(PedometerKeys.SENSITIVITY_LEVEL_EXTRA_HIGH, new Float(1.97));
		sensitivities.put(PedometerKeys.SENSITIVITY_LEVEL_VERY_HIGH, new Float(2.96));
		sensitivities.put(PedometerKeys.SENSITIVITY_LEVEL_HIGH, new Float(4.44));
		sensitivities.put(PedometerKeys.SENSITIVITY_LEVEL_HIGHER, new Float(6.66));
		sensitivities.put(PedometerKeys.SENSITIVITY_LEVEL_MEDIUM, new Float(10.00));
		sensitivities.put(PedometerKeys.SENSITIVITY_LEVEL_LOWER, new Float(15.00));
		sensitivities.put(PedometerKeys.SENSITIVITY_LEVEL_LOW, new Float(22.50));
		sensitivities.put(PedometerKeys.SENSITIVITY_LEVEL_VERY_LOW, new Float(33.75));
		sensitivities.put(PedometerKeys.SENSITIVITY_LEVEL_EXTRA_LOW, new Float(50.62));
		
	}
	
	private StepCounter stepCounter = new StepCounter();
	
	@Override
	protected void onEnable() {
		super.onEnable();
        int h = 480;  
        mYOffset = h * 0.5f;
//        mScale[0] = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
        mScale = - (h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));
        
		mLimit = sensitivities.get(sensitivityLevel);
		getAccelerometerProbe().registerPassiveListener(stepCounter);
	}

	@Override
	protected void onStart() {
		super.onStart();
		getAccelerometerProbe().registerListener(stepCounter);
	}

	@Override
	protected void onStop() {
		super.onStop();
		getAccelerometerProbe().unregisterListener(stepCounter);
	}

	@Override
	protected void onDisable() {
		super.onDisable();
		getAccelerometerProbe().unregisterPassiveListener(stepCounter);
	}

	private AccelerometerSensorProbe getAccelerometerProbe() {
		return getGson().fromJson(DEFAULT_CONFIG, AccelerometerSensorProbe.class);
	}
	
	private void onStep(float diff){
		JsonObject data = new JsonObject();
		//data.addProperty(PedometerKeys.SENSITIVITY_LEVEL, sensitivity);
		data.addProperty(PedometerKeys.RAW_VALUE, diff);
		sendData(data);
	}
	
	private class StepCounter implements DataListener{

		@Override
		public void onDataReceived(IJsonObject probeConfig, IJsonObject data) {
			// TODO Auto-generated method stub
			double timestamp = data.get(TIMESTAMP).getAsDouble();
			
			Log.d(LogUtil.TAG, "RECEIVED:" + timestamp);
			
			/*
			 * Codes here below taken from http://code.google.com/p/pedometer/ (StepDetector.java)
			 * Copyright (C) 2009 Levente Bagi, under GPL license
			 */
			float x = data.get(AccelerometerSensorProbe.X).getAsFloat();
			float y = data.get(AccelerometerSensorProbe.Y).getAsFloat();
			float z = data.get(AccelerometerSensorProbe.Z).getAsFloat();
			
            float vSum = 0;
            vSum = (mYOffset + x * mScale) + (mYOffset +  y * mScale) + (mYOffset + z * mScale);

            int k = 0;
            float v = vSum / 3;
            
            float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));
            if (direction == - mLastDirections[k]) {
                // Direction changed
                int extType = (direction > 0 ? 0 : 1); // minumum or maximum?
                mLastExtremes[extType][k] = mLastValues[k];
                float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);

                if (diff > mLimit) {
                    
                    boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k]*2/3);
                    boolean isPreviousLargeEnough = mLastDiff[k] > (diff/3);
                    boolean isNotContra = (mLastMatch != 1 - extType);
                    
                    if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra) {
                        Log.i(TAG, "step");
                        // detect walking one step. send data here 
                        onStep(diff);
 
                        mLastMatch = extType;
                    }
                    else {
                        mLastMatch = -1;
                    }
                }
                mLastDiff[k] = diff;
            }
            mLastDirections[k] = direction;
            mLastValues[k] = v;
			
			
			
		}


		@Override
		public void onDataCompleted(IJsonObject probeConfig,
				JsonElement checkpoint) {
			// TODO Auto-generated method stub
			// Do nothing
			
		}
		
		
	}
	

}
