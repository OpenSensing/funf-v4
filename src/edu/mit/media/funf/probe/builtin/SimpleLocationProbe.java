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
 */
package edu.mit.media.funf.probe.builtin;



import java.math.BigDecimal;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.mit.media.funf.Schedule;
import edu.mit.media.funf.Schedule.DefaultSchedule;
import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.json.IJsonObject;
import edu.mit.media.funf.probe.Probe.Base;
import edu.mit.media.funf.probe.Probe.PassiveProbe;
import edu.mit.media.funf.probe.Probe.RequiredFeatures;
import edu.mit.media.funf.probe.Probe.RequiredPermissions;
import edu.mit.media.funf.probe.Probe.RequiredProbes;
import edu.mit.media.funf.probe.builtin.ProbeKeys.LocationKeys;
import edu.mit.media.funf.time.TimeUtil;
import edu.mit.media.funf.util.LogUtil;

/**
 * Filters the verbose location set for the most accurate location within a max wait time,
 * ending early if it finds a location that has at most the goodEnoughAccuracy.
 * Useful for sparse polling of location to limit battery usage.
 * @author alangardner
 *
 */
@RequiredPermissions({android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION})
@RequiredFeatures("android.hardware.location")
@Schedule.DefaultSchedule(interval=1800)
@RequiredProbes(LocationProbe.class)
public class SimpleLocationProbe extends Base implements PassiveProbe, LocationKeys {

	@Configurable
	private BigDecimal maxWaitTime = BigDecimal.valueOf(120);
	
	@Configurable
	private BigDecimal maxAge =  BigDecimal.valueOf(120); 
	
	@Configurable
	private BigDecimal goodEnoughAccuracy = BigDecimal.valueOf(80);

	@Configurable
	private boolean useGps = true;
	
	@Configurable
	private boolean useNetwork = true;
	
	@Configurable
	private boolean useCache = true;


	private LocationProbe locationProbe;
	
	private BigDecimal startTime;
	private IJsonObject bestLocation;
	
	private Runnable sendLocationRunnable = new Runnable() {
		@Override
		public void run() {
			sendCurrentBestLocation();
		}
	};
	
	private DataListener listener = new DataListener() {
		
		@Override
		public void onDataReceived(IJsonObject completeProbeUri, IJsonObject data) {
			Log.d(LogUtil.TAG, "SimpleLocationProbe received data: " + data.toString());
			if (startTime == null) {
				startTime = TimeUtil.getTimestamp();
				getHandler().postDelayed(sendLocationRunnable, TimeUtil.secondsToMillis(maxWaitTime));
			}
			if (isBetterThanCurrent(data)) {
				Log.d(LogUtil.TAG, "SimpleLocationProbe evaluated better location.");
				bestLocation = data;
			}
			BigDecimal age = startTime.subtract(bestLocation.get(TIMESTAMP).getAsBigDecimal());
			if (goodEnoughAccuracy != null && 
					bestLocation.get(ACCURACY).getAsDouble() < goodEnoughAccuracy.doubleValue() &&
					age.doubleValue() < maxAge.doubleValue()) {
				Log.d(LogUtil.TAG, "SimpleLocationProbe evaluated good enough location.");
				Log.d(LogUtil.TAG, String.format("Age, Accuracy: %s %f", age.toString(), bestLocation.get(ACCURACY).getAsDouble()));
				if (getState() == State.RUNNING) { // Actively Running
					stop();
				} 
			}
		}
		
		@Override
		public void onDataCompleted(IJsonObject completeProbeUri, JsonElement checkpoint) {
		}
	};
	
	private void sendCurrentBestLocation() {
		Log.d(LogUtil.TAG, "SimpleLocationProbe sending current best location.");
		if (bestLocation != null) {
			JsonObject data = bestLocation.getAsJsonObject();
			data.remove(PROBE); // Remove probe so that it fills with our probe name
			if (data.has("mExtras")) {
				// We're removing the extras if they're available as they are never used and take up space, bandwidth, and time during archiving and uploading.
				data.remove("mExtras");
			}
			Log.i(LogUtil.TAG, data.toString());
			sendData(data);
		}
		startTime = null;
		bestLocation = null;
	}
	
	private boolean isBetterThanCurrent(IJsonObject newLocation) {
		double newAge = startTime.subtract(newLocation.get(TIMESTAMP).getAsBigDecimal()).doubleValue();
		double newAccuracy = newLocation.get(ACCURACY).getAsDouble();
		double bestLocationAccuracy = (bestLocation == null)? Double.MAX_VALUE:bestLocation.get(ACCURACY).getAsDouble();
		double bestLocationAge = (bestLocation == null)? Double.MAX_VALUE:bestLocation.get(TIMESTAMP).getAsDouble();
		Log.i(LogUtil.TAG, String.format("Location newAge, newAccuracy, bestAge, bestAccuracy: %s, %s, %s, %s", newAge, newAccuracy, bestLocationAge, bestLocationAccuracy));
		// location is better than the best if any of the following are true:
		// 1) best is null 
		// 2) best is older than maxAge (should only occur for cached locations)
		// 3) newLocation is younger than maxAge and has better accuracy than bestLocation
		return 
			bestLocation == null ||
			bestLocationAge > maxAge.doubleValue() ||
			(newAge < maxAge.doubleValue() && newAccuracy < bestLocationAccuracy);
	}
	
	@Override
	protected void onEnable() {
		super.onEnable();
		JsonObject config = new JsonObject();
		if (!useGps) {
			config.addProperty("useGps", false);
		}
		if (!useNetwork) {
			config.addProperty("useNetwork", false);
		}
		if (!useCache) {
			config.addProperty("useCache", false);
		}
		locationProbe = getGson().fromJson(config, LocationProbe.class);
		bestLocation = null;
	}

	@Override
	protected void onStart() {
		super.onStart();
		Log.d(LogUtil.TAG, "SimpleLocationProbe starting, registering listener");
		bestLocation = null;
		startTime = TimeUtil.getTimestamp();
		locationProbe.registerListener(listener);
		getHandler().sendMessageDelayed(getHandler().obtainMessage(STOP_MESSAGE), TimeUtil.secondsToMillis(maxWaitTime));
	}

	@Override
	protected void onStop() {
		Log.d(LogUtil.TAG, "SimpleLocationProbe stopping");
		getHandler().removeMessages(STOP_MESSAGE);
		locationProbe.unregisterListener(listener);
		sendCurrentBestLocation();
		super.onStop();
	}

	@Override
	protected void onDisable() {
		super.onDisable();
	}
	
	
	
}
