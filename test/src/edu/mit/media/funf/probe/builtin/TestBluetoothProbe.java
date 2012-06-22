package edu.mit.media.funf.probe.builtin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.os.Debug;
import android.test.AndroidTestCase;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.mit.media.funf.FunfManager;
import edu.mit.media.funf.json.IJsonObject;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.Probe.ContinuousProbe;
import edu.mit.media.funf.probe.Probe.DataListener;
import edu.mit.media.funf.probe.Probe.State;
import edu.mit.media.funf.probe.Probe.StateListener;
import edu.mit.media.funf.probe.builtin.ProbeKeys.BluetoothKeys;
import edu.mit.media.funf.probe.builtin.ProbeKeys.ProximitySensorKeys;


public class TestBluetoothProbe extends AndroidTestCase{
	public static final String TAG = "FunfTest";
	
	private DataListener listener = new DataListener() {
		@Override
		public void onDataReceived(IJsonObject completeProbeUri, IJsonObject data) {
			Log.i(TAG, "DATALL: " + completeProbeUri.toString() + " " + data.toString());
			//get values from ProximitySensorKeys 
			// 1. need to take care the NULL 
			// 2. 
 
			
			if (completeProbeUri.get("@type").getAsString().equals(ProximitySensorProbe.class.getName())){
				Log.i(TAG, "DATA11: " + completeProbeUri.toString() + " " + data.get(ProximitySensorKeys.DISTANCE).getAsFloat());
				Log.i(TAG, "DATA21: " + completeProbeUri.toString() + " " + data.get(ProximitySensorKeys.TIMESTAMP).getAsFloat());
				//Log.i(TAG, "DATA3: " + completeProbeUri.toString() + " " + data.get(ProximitySensorKeys.VENDOR).getAsString()); //nullPointerexception
				
			
			
			}//not getting the message, how do I know which type of the sensor message?
			
			Log.i(TAG, "DATA1: " + completeProbeUri.toString() + " " + data.get(ProximitySensorKeys.DISTANCE).getAsFloat());
			Log.i(TAG, "DATA2: " + completeProbeUri.toString() + " " + data.get(ProximitySensorKeys.TIMESTAMP).getAsFloat());
			//Log.i(TAG, "DATA3: " + completeProbeUri.toString() + " " + data.get(ProximitySensorKeys.VENDOR).getAsString());
			//Log.i(TAG, "BLUEDATA: " + completeProbeUri.toString() + " " + data.get(BluetoothKeys.DEVICES));

			Log.i(TAG, "TYPE: " + completeProbeUri.get("@type"));
		}

		@Override
		public void onDataCompleted(IJsonObject completeProbeUri, JsonElement checkpoint) {
			Log.i(TAG, "COMPLETE: " + completeProbeUri.toString());
		}
	};
	
	private StateListener stateListener = new StateListener() {

		@Override
		public void onStateChanged(Probe probe, State previousState) {
			Log.i(TAG, probe.getClass().getName() + ": " + probe.getState());
			Log.i(TAG, getGson().toJson(probe));
		}
		
	};
	
	private Gson gson;
	public Gson getGson() {
		if (gson == null) {
			gson = new GsonBuilder().registerTypeAdapterFactory(FunfManager.getProbeFactory(getContext())).create();
		}
		return gson;
	}
	
	@SuppressWarnings("rawtypes")
	public static final Class[] ALL_PROBES = {
		ProximitySensorProbe.class,
		BluetoothProbe.class
		
	};
	
	
	@SuppressWarnings("unchecked")
	public void testAll() throws ClassNotFoundException, IOException, InterruptedException {
		Log.i(TAG,"Running");
		Debug.startMethodTracing("calc");
		List<Class<? extends Probe>> allProbeClasses = Arrays.asList((Class<? extends Probe>[])ALL_PROBES);
		
		// Run one at a time
		Gson gson = getGson();
		for (Class<? extends Probe> probeClass : allProbeClasses) {
			JsonObject config = new JsonObject();
			config.addProperty("sensorDelay", SensorProbe.SENSOR_DELAY_NORMAL); //how do I know what other configurations can be used for Bluetooth?
			config.addProperty("asdf", 1);
			config.addProperty("zzzz", "__");
			Probe probe = gson.fromJson(config, probeClass);
			probe.addStateListener(stateListener);
			probe.registerListener(listener);
			Thread.sleep(10000L);
			if (probe instanceof ContinuousProbe) {
				((ContinuousProbe)probe).unregisterListener(listener);
			}
		}
//		// Run simultaneously
//		List<Probe> probes = new ArrayList<Probe>();
//		for (Class<? extends Probe> probeClass : allProbeClasses) {
//			probes.add(gson.fromJson(Probe.DEFAULT_CONFIG, probeClass));
//		}
//		for (Probe probe : probes) {
//			probe.addStateListener(stateListener);
//			probe.registerListener(listener);
//		}
//		Thread.sleep(10000L);
//		for (Probe probe : probes) {
//			if (probe instanceof ContinuousProbe) {
//				((ContinuousProbe)probe).unregisterListener(listener);
//			}
//		}
//		
//		Thread.sleep(1000L); // Give probes time stop

		Debug.stopMethodTracing();
	}

}
