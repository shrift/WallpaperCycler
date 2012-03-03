package com.bubbletastic.wallpapercycler;

import android.util.Log;

public class LogHelper {

	public static void postDebugLog(String logMessage, Class<?> classRef) {
		if (WallpaperCycler.DEBUG_MODE) Log.d(classRef.getName(), logMessage);
	}
	
	public static void postErrorLog(String logMessage, Class<?> classRef) {
		Log.e(classRef.getName(), logMessage);
	}
}
