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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import edu.mit.media.funf.config.Configurable;
import edu.mit.media.funf.probe.Probe;
import edu.mit.media.funf.probe.Probe.ContinuableProbe;
import edu.mit.media.funf.time.DecimalTimeUnit;
import edu.mit.media.funf.time.TimeUtil;

public abstract class DatedContentProviderProbe extends ContentProviderProbe implements ContinuableProbe {

	@Configurable
	protected BigDecimal afterDate = null;
	
	@Configurable
	protected boolean afterInstall = false; 
	
	private BigDecimal latestTimestamp = null;
	
	protected DatedContentProviderProbe() {
		if (getContext() != null) {
			SharedPreferences prefs = getContext().getSharedPreferences(Probe.FUNF_PROBE_PREFS, Context.MODE_PRIVATE);
			if (prefs.contains(getTimestampPrefKey())) {
				latestTimestamp = BigDecimal.valueOf(prefs.getLong(getTimestampPrefKey(), 0));
			} else if (afterInstall) {
				// If there's no preference stored for the latestTimestamp, and afterInstall is set, then this is the first run
				// Store the current timestamp as the latest so we don't take historical data
				latestTimestamp = TimeUtil.getTimestamp();
				prefs.edit().putLong(getTimestampPrefKey(), latestTimestamp.longValue()).commit();
			}
		}
	}
	
	@Override
	protected Cursor getCursor(String[] projection) {
		String dateColumn = getDateColumnName();
		// Used the code below when we specified projection exactly
		List<String> projectionList = Arrays.asList(projection);
		if (!Arrays.asList(projection).contains(dateColumn)) {
			projectionList = new ArrayList<String>(projectionList);
			projectionList.add(dateColumn);
			projection = new String[projectionList.size()];
			projectionList.toArray(projection);
		}
		// If there's no latestTimestamp set, let's try and pull one from sharedPreferences one more time...
		if (latestTimestamp == null) {
			SharedPreferences prefs = getContext().getSharedPreferences(Probe.FUNF_PROBE_PREFS, Context.MODE_PRIVATE);
			if (prefs.contains(getTimestampPrefKey())) {
				latestTimestamp = BigDecimal.valueOf(prefs.getLong(getTimestampPrefKey(), 0));
			}
		}
		
		String dateFilter = null;
		String[] dateFilterParams = null;
		if (afterDate != null || latestTimestamp != null) {
			dateFilter = dateColumn + " > ?";
			BigDecimal startingDate = afterDate == null ? latestTimestamp : 
						afterDate.max(latestTimestamp == null ? BigDecimal.ZERO : latestTimestamp);
			dateFilterParams = new String[] {String.valueOf(getDateColumnTimeUnit().convert(startingDate, DecimalTimeUnit.SECONDS))};
		}
		return getContext().getContentResolver().query(
				getContentProviderUri(),
				projection, // TODO: different platforms have different fields supported for content providers, need to resolve this
				dateFilter, 
				dateFilterParams,
                dateColumn + " ASC");
	}
	
	protected abstract Uri getContentProviderUri();
	
	protected abstract String getDateColumnName();

	protected DecimalTimeUnit getDateColumnTimeUnit() {
		return DecimalTimeUnit.MILLISECONDS;
	}
	
	protected String getTimestampPrefKey() {
		return getConfig().getAsJsonPrimitive("@type").getAsString() + "_timestamp";
	}
	
	protected void saveCheckpoint() {
		if (latestTimestamp != null) {
			getContext().getSharedPreferences(Probe.FUNF_PROBE_PREFS, Context.MODE_PRIVATE).edit().putLong(getTimestampPrefKey(), latestTimestamp.longValue()).commit();
		}
	}
	
	
	@Override
	protected void sendData(JsonObject data) {
		super.sendData(data);
		latestTimestamp = getTimestamp(data);
	}
	
	@Override
	protected void onStop() {
		saveCheckpoint();
	}

	@Override
	protected BigDecimal getTimestamp(JsonObject data) {
		return getDateColumnTimeUnit().toSeconds(data.get(getDateColumnName()).getAsLong());
	}

	@Override
	public JsonElement getCheckpoint() {
		return getGson().toJsonTree(latestTimestamp);
	}

	@Override
	public void setCheckpoint(JsonElement checkpoint) {
		latestTimestamp = (checkpoint == null || checkpoint.isJsonNull()) ? null : checkpoint.getAsBigDecimal();
		saveCheckpoint();
	}
	
	
	
}
