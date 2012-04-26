package com.bubbletastic.wallpapercycler;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;

public class WallpaperCycler extends Application {
	
	public static final boolean DEBUG_MODE = true;
	
	//Screen information
	public static int SCREEN_RESOURCE_SIZE_SELECTOR;
	public static int SCREEN_CONFIGURATION_DENSITY;
	public static boolean isTablet;
	public static boolean isPortrait;
	public static Integer DEVICE_SDK_VERSION;
	public static int SCREEN_WIDTH;
	public static int SCREEN_HEIGHT;
	public static float SCREEN_SCALING_DENSITY;

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate() {
		super.onCreate();
		DEVICE_SDK_VERSION = Integer.valueOf(android.os.Build.VERSION.SDK);
		
		Point size = new Point();
		((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(size);
		SCREEN_WIDTH = size.x;
		SCREEN_HEIGHT = size.y;
		
		SCREEN_RESOURCE_SIZE_SELECTOR = (getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK);
		DisplayMetrics metrics = new DisplayMetrics();
		((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);
		SCREEN_CONFIGURATION_DENSITY = metrics.densityDpi;
		SCREEN_SCALING_DENSITY = metrics.density;

		switch (SCREEN_RESOURCE_SIZE_SELECTOR) {
		case Configuration.SCREENLAYOUT_SIZE_SMALL:
			break;
		case Configuration.SCREENLAYOUT_SIZE_NORMAL:
			break;
		case Configuration.SCREENLAYOUT_SIZE_LARGE:
			if (SCREEN_CONFIGURATION_DENSITY >= DisplayMetrics.DENSITY_HIGH) {
				isTablet = true;
			}
			break;
		case Configuration.SCREENLAYOUT_SIZE_XLARGE:
			isTablet = true;
			break;
		default:
			break;
		}
		
		if (DEVICE_SDK_VERSION >= android.os.Build.VERSION_CODES.HONEYCOMB && DEVICE_SDK_VERSION < android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			isTablet = true;
		}
		
		checkOrientation(metrics);
		metrics = null;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		
		Point size = new Point();
		((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getSize(size);
		SCREEN_WIDTH = size.x;
		SCREEN_HEIGHT = size.y;
		
		DisplayMetrics metrics = new DisplayMetrics();
		((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(metrics);
		
		checkOrientation(metrics);
		metrics = null;
	}
	
	private void checkOrientation(DisplayMetrics metrics)	 {
		if (metrics.heightPixels > metrics.widthPixels) {
			isPortrait = true;
		} else {
			isPortrait = false;
		}
	}
	
//	public static int getHighlightColor(Context context) {
//		int color = Color.rgb(0x83, 0xCF, 0xF1); // a default
//		ContextThemeWrapper contextThemeWrapper = null;
//		if (DEVICE_SDK_VERSION >= android.os.Build.VERSION_CODES.HONEYCOMB) {
//			contextThemeWrapper = new ContextThemeWrapper(context, android.R.style.Theme_Holo_Light);
//		} else {
//			contextThemeWrapper = new ContextThemeWrapper(context, android.R.style.Theme_Light);
//		}
//		if (contextThemeWrapper != null) {
//			TypedArray appearance = contextThemeWrapper.getTheme().obtainStyledAttributes(new int[] {android.R.attr.colorActivatedHighlight});
//
//			if (0 < appearance.getIndexCount()) {
//				int attr = appearance.getIndex(0);
//				color = appearance.getColor(attr, color);
//			}
//			appearance.recycle();
//		}
//		return color;
//	}
	
	public static int getDefaultBackgroundColor(Context context) {
		int color = Color.rgb(0x83, 0xCF, 0xF1); // a default
		ContextThemeWrapper contextThemeWrapper = null;
		if (DEVICE_SDK_VERSION >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			contextThemeWrapper = new ContextThemeWrapper(context, android.R.style.Theme_Holo_Light);
		} else {
			contextThemeWrapper = new ContextThemeWrapper(context, android.R.style.Theme_Light);
		}
		final TypedArray appearance = contextThemeWrapper.getTheme().obtainStyledAttributes(new int[] {android.R.attr.colorBackground});

		if (0 < appearance.getIndexCount()) {
			int attr = appearance.getIndex(0);
			color = appearance.getColor(attr, color);
		}
		appearance.recycle();
		return color;
	}
}
