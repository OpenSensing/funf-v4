package edu.mit.media.funf.util;

import android.content.SharedPreferences;
import android.os.Bundle;
import edu.mit.media.funf.probe.ProbeExceptions.UnstorableTypeException;

public class PrefsUtil {
	
	/**
	 * Convenience function for adding the object form of primitives to a SharedPreferences
	 * @param editor
	 * @param key
	 * @param value Must be Boolean, Float, Integer, Long, String, or Bundle (bundles must only contain the same)
	 * @return
	 * @throws UnstorableTypeException
	 */
	public static SharedPreferences.Editor putInPrefs(SharedPreferences.Editor editor, String key, Object value) throws UnstorableTypeException {
		if (value == null) {
			editor.putString(key, null);
			return editor;
		}
		Class<?> valueClass = value.getClass();
		if (Boolean.class.isAssignableFrom(valueClass)) {
			editor.putBoolean(key, ((Boolean)value).booleanValue());
		} else if (Integer.class.isAssignableFrom(valueClass)) {
			editor.putInt(key, ((Integer) value).intValue());
		} else if (Float.class.isAssignableFrom(valueClass)) {
			editor.putFloat(key, ((Float) value).floatValue());
		} else if (Long.class.isAssignableFrom(valueClass)) {
			editor.putLong(key, ((Long) value).longValue());
		} else if (String.class.isAssignableFrom(valueClass)) {
			editor.putString(key, ((String) value));
		} else if (Bundle.class.isAssignableFrom(valueClass)) {
			// Serialize the bundle using the key as a prefix
			Bundle bundle = ((Bundle) value);
			for (String bundleKey : bundle.keySet()) {
				Object bundleValue = bundle.get(bundleKey);
				putInPrefs(editor, getStoredBundleParamKey(key, bundleKey), bundleValue);
			}
		} else {
			throw new UnstorableTypeException(valueClass);
		}
		return editor;
	}
	
	
	private static String getStoredBundleParamKey(final String key, final String paramKey) {
		return key + "__" + paramKey;
	}
	
	private static boolean isStoredBundleParamKey(final String key, final String storedParamKey) {
		final String prefix = key + "__";
		return key.startsWith(prefix);
	}
	
	private static String getBundleParamKey(final String key, final String storedParamKey) {
		final String prefix = key + "__";
		assert key.startsWith(prefix);
		return storedParamKey.substring(prefix.length());
	}

}
