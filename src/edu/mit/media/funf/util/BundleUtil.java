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
package edu.mit.media.funf.util;

import static edu.mit.media.funf.util.LogUtil.TAG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import edu.mit.media.funf.probe.ProbeExceptions.UnstorableTypeException;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;

public class BundleUtil {

	private BundleUtil() {
		
	}
	
	public static Bundle[] copyBundleArray(Parcelable[] parcelables) {
		if (parcelables == null) {
			return new Bundle[0];
		}
		Bundle[] bundles = new Bundle[parcelables.length];
		System.arraycopy(parcelables, 0, bundles, 0, parcelables.length);
		return bundles;
	}
	
	public static Map<String,Object> getValues(final Bundle bundle) {
		HashMap<String, Object> values = new HashMap<String, Object>();
		if (bundle == null) {
			return values;
		}
		for (String key : bundle.keySet()) {
			values.put(key, bundle.get(key));
		}
		return values;
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends Parcelable> ArrayList<T> getArrayList(Bundle bundle, String key) {
		if (bundle == null) {
			return null;
		}
        Object o = bundle.get(key);
        try {
        	return (ArrayList<T>) o;
        } catch (ClassCastException e) {
        	try {
        		return new ArrayList<T>(Arrays.asList((T[])o));
        	} catch (ClassCastException e2) {
        		Log.w(TAG, "Unable to succesfully parse ArrayList from '" + key + "'");
                return null;
			}
        }
	}
	
	/**
	 * Convenience function for putting the object form of primitives into a Bundle
	 * @param bundle
	 * @param key
	 * @param value Must be Boolean, Float, Integer, Long, or String
	 * @throws UnstorableTypeException
	 */
	public static void putInBundle(Bundle bundle, String key, Object value) throws UnstorableTypeException {
		Class<?> valueClass = value.getClass();
		if (Boolean.class.isAssignableFrom(valueClass)) {
			bundle.putBoolean(key, ((Boolean)value).booleanValue());
		} else if (Short.class.isAssignableFrom(valueClass)) {
				bundle.putShort(key, ((Short) value).shortValue());
		} else if (Integer.class.isAssignableFrom(valueClass)) {
			bundle.putInt(key, ((Integer) value).intValue());
		} else if (Long.class.isAssignableFrom(valueClass)) {
			bundle.putLong(key, ((Long) value).longValue());
		} else if (Float.class.isAssignableFrom(valueClass)) {
			bundle.putFloat(key, ((Float) value).floatValue());
		} else if (Double.class.isAssignableFrom(valueClass)) {
			bundle.putDouble(key, ((Double) value).doubleValue());
		}  else if (String.class.isAssignableFrom(valueClass)) {
			bundle.putString(key, ((String) value));
		} else {
			throw new UnstorableTypeException(valueClass);
		}
	}
	
	

	/**
	 * Useful function to coerce value to a long, no matter what type of object is in the bundle
	 * @param bundle
	 * @param key
	 * @param defaultValue
	 * @return
	 */
	public static long getLong(Bundle bundle, String key, long defaultValue) {
		Object value = bundle.get(key);
		if (value instanceof Long) {
			return (Long)value;
		} else if (value instanceof Integer) {
			return ((Integer)value).longValue();
		} else if (value instanceof String) {
			try {
				return Long.valueOf((String)value);
			} catch (NumberFormatException e) {
				// We did our best, value is not a long
			}
		}
		return defaultValue;
	}
}
