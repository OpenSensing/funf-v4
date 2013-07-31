/**
 * 
 * Funf: Open Sensing Framework
 * Copyright (C) 2010-2011 Nadav Aharony, Wei Pan, Alex Pentland.
 * Acknowledgments: Alan Gardner
 * Contact: nadav@media.mit.edu
 * 
 * This file is part of Funf.
 * 
 * Funf is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Funf is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with Funf. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Last edited by Fuming Shih at Aug 7, 2012
 * 
 */
package edu.mit.media.funf.probe.builtin;


import java.math.BigDecimal;

import android.os.AsyncTask;
import android.os.Debug;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.Schedule.DefaultSchedule;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.json.IJsonObject;
import edu.mit.media.funf.probe.Probe.Base;
import edu.mit.media.funf.probe.Probe.ContinuousProbe;
import edu.mit.media.funf.probe.Probe.DataListener;
import edu.mit.media.funf.probe.Probe.PassiveProbe;
import edu.mit.media.funf.probe.Probe.RequiredFeatures;
import edu.mit.media.funf.probe.Probe.RequiredProbes;
import edu.mit.media.funf.probe.builtin.ProbeKeys.ActivityKeys;
import edu.mit.media.funf.time.TimeUtil;
import edu.mit.media.funf.util.LogUtil;

@Schedule.DefaultSchedule(interval=120, duration=15)
@RequiredFeatures("android.hardware.sensor.accelerometer")
@RequiredProbes(AccelerometerSensorProbe.class)
public class ActivityProbe extends Base implements ContinuousProbe, PassiveProbe, ActivityKeys {

	@Configurable
	private double interval = 1.0;
	private static final long INTERVAL = 1L;
	
	private double startTime;
	private int intervalCount;
	private int lowActivityIntervalCount;
	private int highActivityIntervalCount;
	private boolean dataSent;
	
	private ActivityCounter activityCounter = new ActivityCounter();

	@Override
	protected void onStart() {
		super.onStart();
		getAccelerometerProbe().registerListener(activityCounter);
		dataSent = false;
	}

	@Override
	protected void onStop() {
		getAccelerometerProbe().unregisterListener(activityCounter);
		// Rather than sending data for every interval, we're aggregating like v0.3 does.
		// This has the advantage of less entries overall and less data being uploaded = lower data
		// consumption and hopefully better battery life (as well as lower space requirements).
		// HOWEVER - this leads to complications (see unregisterListener method in Probe.java).
		// An alternative (and potentially better way) is to sendData on every interval like standard v0.4
		// and handle aggregation in the pipeline onDataCompleted method.
		sendDataOnce(activityCounter.getData());

		super.onStop();
	}

	private void sendDataOnce(JsonObject data) {
		if (!dataSent) {
			sendData(data);
			dataSent = true;
		}
	}
	
	@Override
	public void unregisterListener(final DataListener... listeners) {
		sendDataOnce(activityCounter.getData());
		
		// Essentially a copy-past from Probe.java, but we're running it in the handler to assure that the sendData completes
		// before listeners are removed...
		getHandler().post(new Runnable() {
			@Override
			public void run() {			
				if (listeners != null) {
				JsonElement checkpoint = new JsonObject();
				for (DataListener listener : listeners) {
					getDataListeners().remove(listener);
					listener.onDataCompleted(getConfig(), checkpoint);
				}
				// If no one is listening, stop using device resources
				if (getDataListeners().isEmpty()) {
					stop();
				}
				if (getPassiveDataListeners().isEmpty()) {
					disable();
				}
			}
			}
		});
	}
	
	private AccelerometerSensorProbe getAccelerometerProbe() {
		return getGson().fromJson("{ 'sensorDelay': 3 }", AccelerometerSensorProbe.class);
	}

	private class ActivityCounter implements DataListener {
		private double intervalStartTime;
		private float varianceSum;
		private float avg;
		private float sum;
		private int count;
		
		public JsonObject getData() {
			JsonObject data = new JsonObject();
			data.addProperty(TOTAL_INTERVALS, intervalCount);
			data.addProperty(LOW_ACTIVITY_INTERVALS, lowActivityIntervalCount);
			data.addProperty(HIGH_ACTIVITY_INTERVALS, highActivityIntervalCount);
			return data;
		}
		
		private void reset(double timestamp) {
			// If more than an interval away, start a new scan
			varianceSum = avg = sum = count = 0;
			startTime = intervalStartTime = timestamp;
			varianceSum = avg = sum = count = 0;
			intervalCount = 0;
			lowActivityIntervalCount = 0;
			highActivityIntervalCount = 0;
		}
		
		private void intervalReset() {
			Log.d(LogUtil.TAG, "interval RESET");
			// Calculate activity and reset
			intervalCount++;
			//JsonObject data = new JsonObject();
			if (varianceSum >= 10.0f) {
				//data.addProperty(ACTIVITY_LEVEL, ACTIVITY_LEVEL_HIGH);
				highActivityIntervalCount++;
			} else if (varianceSum < 10.0f && varianceSum > 3.0f) {
				//data.addProperty(ACTIVITY_LEVEL, ACTIVITY_LEVEL_LOW);
				lowActivityIntervalCount++;
			} else {
				//data.addProperty(ACTIVITY_LEVEL, ACTIVITY_LEVEL_NONE);
			}
			//sendData(data);
			intervalStartTime += INTERVAL; // Ensure 1 second intervals
			varianceSum = avg = sum = count = 0;
		}
		
		private void update(float x, float y, float z) {
			//Log.d(TAG, "UPDATE:(" + x + "," + y + "," + z + ")");
			// Iteratively calculate variance sum
			count++;
			float magnitude = (float)Math.sqrt(x*x + y*y + z*z);
			float newAvg = (count - 1)*avg/count + magnitude/count;
			float deltaAvg = newAvg - avg;
			varianceSum += (magnitude - newAvg) * (magnitude - newAvg)
				- 2*(sum - (count-1)*avg) 
				+ (count - 1) *(deltaAvg * deltaAvg);
			sum += magnitude;
			avg = newAvg;
			//Log.d(TAG, "UPDATED VALUES:(count, varianceSum, sum, avg) " + count + ", " + varianceSum+ ", " + sum+ ", " + avg);
		}
		

		@Override
		public void onDataReceived(IJsonObject completeProbeUri, final IJsonObject data) {
			double timestamp = data.get(TIMESTAMP).getAsDouble();
			//Log.d(LogUtil.TAG, "Starttime: " + startTime + " intervalStartTime: " + intervalStartTime);
			//Log.d(LogUtil.TAG, "RECEIVED:" + timestamp);
			if (timestamp >= intervalStartTime + 2 * interval) {
				//Log.d(LogUtil.TAG, "RESET:" + timestamp);
				reset(timestamp);
			} else if (timestamp >= intervalStartTime + interval) {
				//Log.d(LogUtil.TAG, "interval Reset:" + timestamp);
				intervalReset();
			}
			float x = data.get(AccelerometerSensorProbe.X).getAsFloat();
			float y = data.get(AccelerometerSensorProbe.Y).getAsFloat();
			float z = data.get(AccelerometerSensorProbe.Z).getAsFloat();
			update(x, y, z);
		}

		@Override
		
		public void onDataCompleted(IJsonObject completeProbeUri, JsonElement checkpoint) {
			// Do nothing
		}
	}
}
