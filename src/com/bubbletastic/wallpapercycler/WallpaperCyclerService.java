package com.bubbletastic.wallpapercycler;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Random;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Environment;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.ViewConfiguration;

public class WallpaperCyclerService extends WallpaperService {

	@Override
	public void onCreate() {
		super.onCreate();
		
	}

	@Override
	public Engine onCreateEngine() {
		return new WallpaperCyclerEngine();
	}

	private class WallpaperCyclerEngine extends Engine {
		
		//Preferences
		private boolean usePseudoRandomOrder = true;
		private boolean changeWallpaperWithVisibility = true;
		
		//For detecting when a touch event has moved the home screen.
		private boolean motionMoved;
		
		private Bitmap wallpaper;
		private File[] fileList;
		private int currentFileIndex;
		private int xPixelOffset;
		
		private Handler handler = new Handler();
		private Runnable changeWallpapers = new Runnable() {
			

			@Override
			public void run() {
				if (fileList.length > 0) {
					Thread getWallpaperThread = new Thread(new Runnable() {
						@Override
						public void run() {
							setCurrentWallpaper();
						}
					});
					getWallpaperThread.setPriority(Thread.MIN_PRIORITY);
					getWallpaperThread.setName("getWallpaperThread");
					getWallpaperThread.start();
				}
			}
		};
		
		public WallpaperCyclerEngine() {
			super();
			setTouchEventsEnabled(true);
		}
		
		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			super.onCreate(surfaceHolder);
			//TODO: allow user selected folder to fetch images from.
			fileList = getFileList(Environment.getExternalStorageDirectory()+"/Wallpapers");
			if (fileList.length > 0) {
				setNextFileIndex();
				setCurrentWallpaper();
				draw();
			}
		}
		
		@Override
		public void onTouchEvent(MotionEvent event) {
			super.onTouchEvent(event);
			
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				motionMoved = false;
			} else if (event.getAction() == MotionEvent.ACTION_MOVE) {
				motionMoved = true;
			} else if (event.getAction() == MotionEvent.ACTION_UP) {
				if (!motionMoved && (event.getEventTime() - event.getDownTime()) < ViewConfiguration.getLongPressTimeout()) {
					handler.post(changeWallpapers);
				}
			}
		}

		private void setCurrentWallpaper() {
			if (currentFileIndex > fileList.length) {
				//If we have somehow gotten out of bounds of the array, set a new index to use.
				setNextFileIndex();
			}
			try {
				wallpaper = BitmapFactory.decodeFile(fileList[currentFileIndex].toString());
			} catch (OutOfMemoryError oom) {
				wallpaper = null;
				oom.printStackTrace();
			} catch (ArrayIndexOutOfBoundsException e) {
				wallpaper = null;
				LogHelper.postDebugLog("Array index attempted: "+currentFileIndex+"     with array size of: "+fileList.length, getClass());
				e.printStackTrace();
			}
			if (wallpaper == null) {
				LogHelper.postErrorLog("A wallpaper image failed to load: "+fileList[currentFileIndex], getClass());
				//If the decode failed for some reason, try again with the next file.
				currentFileIndex ++;
				setCurrentWallpaper();
			} else {
				setNextFileIndex();
			}
		}
		
		private void setNextFileIndex() {
			if (usePseudoRandomOrder) {
			Random random = new Random();
			currentFileIndex = random.nextInt(fileList.length);
			} else {
				currentFileIndex++;
			}
		}
		
		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);
			if (!visible && changeWallpaperWithVisibility) {
				//Change the wallpaper every time it is *hidden* so that the wallpaper does not flicker from old to new next time it's shown.
				handler.post(changeWallpapers);
			} else {
				draw();
			}
		}
		
		@Override
		public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
			super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset);
			
			//Save the offset so we can apply it in the draw method.
			this.xPixelOffset = xPixelOffset;
			draw();
		}
		
		private void draw() {
			if (wallpaper != null && !wallpaper.isRecycled()) {
				SurfaceHolder holder = getSurfaceHolder();
				if (holder == null) {
					LogHelper.postDebugLog("Could not get surface holder to draw wallpaper?", getClass());
					return;
				}
				Canvas canvas = holder.lockCanvas();
				if (canvas == null) {
					LogHelper.postDebugLog("Could not get lock on canvas!", getClass());
					return;
				}
				canvas.drawBitmap(wallpaper, xPixelOffset, 0, null);
				holder.unlockCanvasAndPost(canvas);
			}
		}
		
	}

	private File[] getFileList(String dirPath) {
		if (dirPath == null || dirPath.trim().length() < 1) {
			LogHelper.postErrorLog("This path is null or too short: "+dirPath, getClass());
			return null;
		}
		File directory = new File(dirPath);
		if (directory == null || !directory.exists())  {
			LogHelper.postErrorLog("This path does not exist: "+dirPath, getClass());
			return null;
		}

		return directory.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				String extension = filename.substring(filename.lastIndexOf(".")).replace(".", "").toLowerCase();
				return new File(filename).isHidden() == false && new File(filename).isDirectory() == false &&
						(extension.equals("png") || extension.equals("jpg"));
			}
		});

	}
}
