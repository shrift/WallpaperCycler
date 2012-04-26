package com.bubbletastic.wallpapercycler;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Random;

import android.app.WallpaperManager;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class WallpaperCyclerService extends WallpaperService {

	@Override
	public void onCreate() {
		super.onCreate();

	}

	@Override
	public Engine onCreateEngine() {
		return new WallpaperCyclerEngine();
	}

	private class WallpaperCyclerEngine extends Engine implements OnSharedPreferenceChangeListener {

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
			if (key.equals(getString(R.string.wallpapercycler_preferences_folder_key))) {
				loadFileList();
			}
		}

		//Preferences
		private boolean usePseudoRandomOrder = true;
		private boolean changeWallpaperWithVisibility = true;
		private boolean useAnimatedTransision = false;

		private Bitmap previosWallpaperImage;
		private Bitmap currentWallpaperImage;
		private File[] fileList;
		private int currentFileIndex;
		private int xPixelOffset;

		//Animated transition fields.
		private TransitionRunnable slideTransitionRunnable;

		private Handler handler = new Handler();
		private Handler transitionsHandler;
		private int desiredWallpaperWidth;
		private int desiredWallpaperHeight;

		public WallpaperCyclerEngine() {
			super();
		}

		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			super.onCreate(surfaceHolder);
			desiredWallpaperWidth = (WallpaperCycler.SCREEN_WIDTH * 2);
			desiredWallpaperHeight = WallpaperCycler.SCREEN_HEIGHT;
			getSharedPreferences(getPackageName(), MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
			loadFileList();
		}

		@Override
		public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
			if (action.equals(WallpaperManager.COMMAND_TAP)) {
				changeCurrentWallpaper();
				Thread getWallpaperThread = new Thread(new Runnable() {

					@Override
					public void run() {
						Looper.prepare();
						transitionsHandler = new Handler();
						Looper.loop();
					}
				});
				getWallpaperThread.setPriority(Thread.MAX_PRIORITY);
				getWallpaperThread.setName("transitionWallpaperThread");
				getWallpaperThread.start();
				transitionWallpaper();
			}
			return super.onCommand(action, x, y, z, extras, resultRequested);
		}

		/**
		 * Load a list of wallpapers from the filesystem to cycle through.
		 */
		private void loadFileList() {
			LogHelper.postDebugLog(getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.wallpapercycler_preferences_folder_key), "no saved wallpaper folder found"), getClass());
			fileList = getFileList(getSharedPreferences(getPackageName(), MODE_PRIVATE).getString(getString(R.string.wallpapercycler_preferences_folder_key), Environment.getExternalStorageDirectory()+"/Wallpapers"));
			if (fileList != null && fileList.length > 0) {
				setNextFileIndex();
				changeCurrentWallpaper();
				draw();
			}
		}

		/**
		 * Change the wallpaper to a new one.
		 */
		private void changeCurrentWallpaper() {
			if (fileList == null) {
				return;
			}
			if (currentFileIndex > fileList.length) {
				//If we have somehow gotten out of bounds of the array, set a new index to use.
				setNextFileIndex();
			}
			previosWallpaperImage = currentWallpaperImage;
			try {
				currentWallpaperImage = getImageFromFileSystem(fileList[currentFileIndex].toString());
			} catch (OutOfMemoryError oom) {
				currentWallpaperImage = null;
				oom.printStackTrace();
			} catch (ArrayIndexOutOfBoundsException e) {
				currentWallpaperImage = null;
				LogHelper.postDebugLog("Array index attempted: "+currentFileIndex+"     with array size of: "+fileList.length, getClass());
				e.printStackTrace();
			}
			if (currentWallpaperImage == null) {
				LogHelper.postErrorLog("A wallpaper image failed to load: "+fileList[currentFileIndex], getClass());
				//If the decode failed for some reason, try again with the next file.
				setNextFileIndex();
				changeCurrentWallpaper();
			} else {
				setNextFileIndex();
			}
		}
		
		private Bitmap getImageFromFileSystem(String file) {
			BitmapFactory.Options bounds = new BitmapFactory.Options();
			bounds.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(file, bounds);
			
			int bmWidth = bounds.outWidth;
			int bmHeight = bounds.outHeight;
			LogHelper.postDebugLog("Desired wallpaper width is: "+desiredWallpaperWidth, getClass());
			LogHelper.postDebugLog("Desired wallpaper height is: "+desiredWallpaperHeight, getClass());
			LogHelper.postDebugLog("Found bitmap with width of: "+bmWidth, getClass());
			LogHelper.postDebugLog("Found bitmap with height of: "+bmHeight, getClass());
			
			if (bmWidth > desiredWallpaperWidth && bmHeight > desiredWallpaperHeight) {
			    float sampleSizeF = (float) bmWidth / (float) desiredWallpaperWidth;
			    int sampleSize = Math.round(sampleSizeF);
//			    if (sampleSize >= 2) {
//			    	sampleSize--;
//			    }
			    LogHelper.postDebugLog("Image file was too large, using sample size of: "+sampleSize, getClass());
			    BitmapFactory.Options resample = new BitmapFactory.Options();
			    resample.inSampleSize = sampleSize;
			    Bitmap tempBitmap = BitmapFactory.decodeFile(file, resample);
			    LogHelper.postDebugLog("Resampled bitmap width: "+tempBitmap.getWidth(), getClass());
			    LogHelper.postDebugLog("Resampled bitmap height: "+tempBitmap.getHeight(), getClass());
			    if (tempBitmap.getWidth() != desiredWallpaperWidth || tempBitmap.getHeight() != desiredWallpaperHeight) {
			    	tempBitmap = Bitmap.createScaledBitmap(tempBitmap, desiredWallpaperWidth, desiredWallpaperHeight, true);
			    	int xOffset = 0;
					if (tempBitmap.getWidth() > desiredWallpaperWidth) {
						float tempX = tempBitmap.getWidth();
						float desiredX = desiredWallpaperWidth;
			    		xOffset = (int) ((tempX - desiredX) / 2);
			    	}
					int yOffset = 0;
					if (tempBitmap.getHeight() > desiredWallpaperHeight) {
						float tempY = tempBitmap.getHeight();
						float desiredY = desiredWallpaperHeight;
			    		yOffset = (int) ((tempY - desiredY) / 2);
			    	}
			    	LogHelper.postDebugLog("Using xOffset of : "+xOffset, getClass());
			    	LogHelper.postDebugLog("Using yOffset of : "+yOffset, getClass());
//			    	Bitmap resizedBitmap = Bitmap.createBitmap(desiredWallpaperWidth, desiredWallpaperHeight, null);
//			    	Canvas resizedCanvas = new Canvas(resizedBitmap);
//			    	resizedCanvas.drawBitmap(tempBitmap, xOffset, yOffset, null);
					tempBitmap = Bitmap.createBitmap(tempBitmap, xOffset, yOffset, desiredWallpaperWidth, desiredWallpaperHeight);
			    	LogHelper.postDebugLog("Final width of resized bitmap: "+tempBitmap.getWidth(), getClass());
			    	LogHelper.postDebugLog("Final height of resized bitmap: "+tempBitmap.getHeight(), getClass());
			    }
			    return tempBitmap;
			}
			Bitmap bitmap = BitmapFactory.decodeFile(file);
			LogHelper.postDebugLog("Returning non resized bitmap with width: "+bitmap.getWidth(), getClass());
			LogHelper.postDebugLog("Returning non resized bitmap with height: "+bitmap.getHeight(), getClass());
			return bitmap;
		}

		/**
		 * Sets the next wallpaper to be displayed from the set.
		 * If usePseudoRandomOrder is true then the wallpapers will be shown randomly, otherwise sequentially in the order they exist in the folder.
		 */
		private void setNextFileIndex() {
			if (usePseudoRandomOrder) {
				Random random = new Random();
				currentFileIndex = random.nextInt(fileList.length);
			} else {
				currentFileIndex++;
				if (currentFileIndex > (fileList.length - 1)) {
					//If we have somehow gotten out of bounds of the array, set a new index to use.
					currentFileIndex = 0;
				}
			}
		}

		private Runnable changeWallpapers = new Runnable() {

			@Override
			public void run() {
				if (fileList != null && fileList.length > 0) {
					Thread getWallpaperThread = new Thread(new Runnable() {
						@Override
						public void run() {
							changeCurrentWallpaper();
						}
					});
					getWallpaperThread.setPriority(Thread.MIN_PRIORITY);
					getWallpaperThread.setName("getWallpaperThread");
					getWallpaperThread.start();
				}
			}
		};

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
			if (currentWallpaperImage != null && !currentWallpaperImage.isRecycled()) {
				Canvas canvas = getCurrentCanvas();
				if (canvas == null) return;

				canvas.drawBitmap(currentWallpaperImage, xPixelOffset, 0, null);
				getSurfaceHolder().unlockCanvasAndPost(canvas);
			}
		}

		/**
		 * 
		 * This runnable is used to display an effect while wallpapers are changing.
		 * @author brendanmartens
		 *
		 */
		private class TransitionRunnable implements Runnable {

			//The joint is the left most edge of the incoming wallpaper, so canvas width +1 is offscreen, 0 is fully on screen.
			private int xJointLocation = -1;

			private class DrawTransitionFrameRunnable implements Runnable {

				private int xJointLocation;

				public DrawTransitionFrameRunnable(int xJointLocation) {
					this.xJointLocation = xJointLocation;
				}

				@Override
				public void run() {
					drawTransitionFrame(xJointLocation);
				}
			};

			@Override
			public void run() {
				for (int i = xJointLocation; i > -1; i--) {
					transitionsHandler.postDelayed(new DrawTransitionFrameRunnable(i), 30);
				}
			}

			private Rect previousCanvasRect;
			private Rect currentCanvasRect;
			private Rect previousBitmapRect;
			private Rect currentBitmapRect;

			private void drawTransitionFrame(int xJointLocation) {

				Canvas canvas = getCurrentCanvas();
				if (canvas == null) return;

				int previousBitmapRectRight = previousBitmapRect.right;
				previousBitmapRect.right = Math.abs(xPixelOffset) + canvas.getWidth();

				if (previousBitmapRectRight != previousBitmapRect.right) {
					if (previousBitmapRectRight > previousBitmapRect.right) {
						xJointLocation = xJointLocation + (previousBitmapRect.right - previousBitmapRectRight);
					} else {
						xJointLocation = xJointLocation - (previousBitmapRectRight - previousBitmapRect.right);
					}
				}

				previousCanvasRect.right = (xJointLocation - 1);
				currentCanvasRect.left = xJointLocation;

				previousBitmapRect.left = (previousBitmapRect.right - (xJointLocation - 1));
				currentBitmapRect.left = Math.abs(xPixelOffset);
				currentBitmapRect.right = (currentBitmapRect.left + (canvas.getWidth() - xJointLocation));

				//				LogHelper.postDebugLog("drawing with values; current xPixelOffset: "+xPixelOffset +
				//						"   Pcanvasr: "+previousCanvasRect.right+" Pcanvsl: "+previousCanvasRect.left +
				//						"   Ccanvasr: "+currentCanvasRect.right+" Ccanvsl: "+currentCanvasRect.left +
				//						"   Pbitmapr: "+previousBitmapRect.right+" Pbitmapl: "+previousBitmapRect.left +
				//						"   Cbitmapr: "+currentBitmapRect.right+" Cbitmapl: "+currentBitmapRect.left
				//						, getClass());

				canvas.drawBitmap(previosWallpaperImage, previousBitmapRect, previousCanvasRect, null);
				canvas.drawBitmap(currentWallpaperImage, currentBitmapRect, currentCanvasRect, null);
				getSurfaceHolder().unlockCanvasAndPost(canvas);
			}

			/**
			 * Do any cleanup/prep work before animation the wallpaper transition.
			 */
			public void prepareForNewTransition() {
				Canvas canvas = getCurrentCanvas();
				if (canvas != null) {
					xJointLocation = (canvas.getWidth() + 1);
					previousCanvasRect = new Rect(0, 0, canvas.getWidth() -1, canvas.getHeight());
					currentCanvasRect = new Rect(canvas.getWidth(), 0, canvas.getWidth(), canvas.getHeight());

					previousBitmapRect = new Rect(Math.abs(xPixelOffset), 0, Math.abs(xPixelOffset) + canvas.getWidth(), canvas.getHeight());
					currentBitmapRect = new Rect(Math.abs(xPixelOffset), 0, Math.abs(xPixelOffset), canvas.getHeight());

					//This avoids a flicker before the animation.
					getSurfaceHolder().unlockCanvasAndPost(canvas);
				}
			}
		}

		/**
		 * Change wallpapers.
		 */
		private void transitionWallpaper() {
			if (!useAnimatedTransision) {
				draw();
				return;
			}
			if (currentWallpaperImage != null && !currentWallpaperImage.isRecycled()) {
				if (previosWallpaperImage == null || previosWallpaperImage.isRecycled()) {
					//If we don't have a previous wallpaper to transition with, use the normal draw method.
					LogHelper.postDebugLog("Previous wallpaper was not available, not using transition effect.", getClass());
					draw();
					return;
				}

				if (transitionsHandler == null) {
					LogHelper.postDebugLog("Could not get transitions handler.", getClass());
					return;
				}

				if (slideTransitionRunnable == null) {
					slideTransitionRunnable = new TransitionRunnable();
				}
				slideTransitionRunnable.prepareForNewTransition();

				transitionsHandler.post(slideTransitionRunnable);
			}

		}

		/**
		 * Returns the current surface canvas. Note that this method places a lock on the canvas, a subsequent call to SurfaceHolder.unlockCanvasandPost(canvas) is expected.
		 * @return The current surface holder canvas.
		 */
		private Canvas getCurrentCanvas() {
			SurfaceHolder holder = getSurfaceHolder();
			if (holder == null) {
				LogHelper.postDebugLog("Could not get surface holder to draw wallpaper?", getClass());
				return null;
			}
			Canvas canvas = holder.lockCanvas();
			if (canvas == null) {
				LogHelper.postDebugLog("Could not get lock on canvas!", getClass());
				return null;
			}
			return canvas;
		}
	}



	/**
	 * Parses a raw list of files to determine if they are images that we can use.
	 * @param dirPath
	 * @return
	 */
	private File[] getFileList(String dirPath) {
		if (dirPath == null || dirPath.trim().length() < 1) {
			LogHelper.postErrorLog("This path is null or too short: "+dirPath, getClass());
			return null;
		}
		File directory = new File(dirPath);
		if (directory == null || !directory.exists() || directory.list() == null)  {
			LogHelper.postErrorLog("This path does not exist: "+dirPath, getClass());
			return null;
		}

		return directory.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String filename) {
				LogHelper.postDebugLog("Found a file to use: "+filename, getClass());
				return new File(filename).isHidden() == false && new File(filename).isDirectory() == false &&
						(filename.toLowerCase().endsWith("png") || filename.toLowerCase().endsWith("jpg"));
			}
		});

	}
}
