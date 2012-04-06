package com.bubbletastic.wallpapercycler;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.provider.MediaStore;

@SuppressWarnings("deprecation")
public class WallpaperCyclerPreferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	
    @Override
    protected void onResume() {
        super.onResume();
    }

	@Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(getString(R.string.wallpapercycler_preferences_folder_key))) {
			setFolderNameToPreferenceSummary();
		}
	}

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getPreferenceManager().setSharedPreferencesName(getPackageName());
        addPreferencesFromResource(R.xml.preferences);
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        setFolderNameToPreferenceSummary();
        getPreferenceManager().findPreference(getString(R.string.wallpapercycler_preferences_folder_key)).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			
			@Override
			public boolean onPreferenceClick(Preference preference) {
				  Intent intent = new Intent();
				  intent.setType("image/*");
				  intent.setAction(Intent.ACTION_GET_CONTENT);
				  startActivityForResult(Intent.createChooser(intent, getString(R.string.wallpapercycler_preferences_folder_select_chooser_text)), 0);
				return true;
			}
		});
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	super.onActivityResult(requestCode, resultCode, data);
    	if (resultCode == RESULT_OK) {
    		switch (requestCode) {
			case 0:
				String discoveredPath = null;
				if (data.getData().getPath().startsWith("/external/images/")) {
					discoveredPath = getRealPathFromUri(this, data.getData());
				} else {
					discoveredPath = data.getData().getPath();
				}
				SharedPreferences sharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
				Editor edit = sharedPreferences.edit();
				edit.putString(getString(R.string.wallpapercycler_preferences_folder_key), new File(discoveredPath).getParentFile().toString());
				edit.commit();
				break;
			default:
				break;
			}
    	}
    }
    
    private void setFolderNameToPreferenceSummary() {
    	getPreferenceManager().findPreference(getString(R.string.wallpapercycler_preferences_folder_key)).
    	setSummary(getString(R.string.wallpapercycler_preferences_folder_current_prefix) + getPreferenceManager().getSharedPreferences().
    			getString(getString(R.string.wallpapercycler_preferences_folder_key), getString(R.string.wallpapercycler_preferences_folder_description)));
    }
    
    private String getRealPathFromUri(Activity activity, Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA };
        Cursor cursor = WallpaperCyclerPreferences.this.getContentResolver().query(contentUri, proj, null, null, null);
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String realPath = cursor.getString(column_index);
        cursor.close();
        return realPath;
    }
}
