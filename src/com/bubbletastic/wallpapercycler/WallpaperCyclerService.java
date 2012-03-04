package com.bubbletastic.wallpapercycler;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Random;

import android.app.WallpaperManager;
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

	private class WallpaperCyclerEngine extends Engine {
		
		//Preferences
		private boolean usePseudoRandomOrder = true;
		private boolean changeWallpaperWithVisibility = true;
		
		private Bitmap previousWallpaper;
		private Bitmap currentWallpaper;
		private File[] fileList;
		private int currentFileIndex;
		private int xPixelOffset;
		
		//Animated transition fields.
		private TransitionRunnable slideTransitionRunnable;
		
		private Handler handler = new Handler();
		private Handler transitionsHandler;
		
		public WallpaperCyclerEngine() {
			super();
		}
		
		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			super.onCreate(surfaceHolder);
			//TODO: allow user selected folder to fetch images from.
			fileList = getFileList(Environment.getExternalStorageDirectory()+"/Wallpapers");
			if (fileList.length > 0) {
				setNextFileIndex();
				changeCurrentWallpaper();
				draw();
			}
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

		private void changeCurrentWallpaper() {
			if (currentFileIndex > fileList.length) {
				//If we have somehow gotten out of bounds of the array, set a new index to use.
				setNextFileIndex();
			}
			previousWallpaper = currentWallpaper;
			try {
				currentWallpaper = BitmapFactory.decodeFile(fileList[currentFileIndex].toString());
			} catch (OutOfMemoryError oom) {
				currentWallpaper = null;
				oom.printStackTrace();
			} catch (ArrayIndexOutOfBoundsException e) {
				currentWallpaper = null;
				LogHelper.postDebugLog("Array index attempted: "+currentFileIndex+"     with array size of: "+fileList.length, getClass());
				e.printStackTrace();
			}
			if (currentWallpaper == null) {
				LogHelper.postErrorLog("A wallpaper image failed to load: "+fileList[currentFileIndex], getClass());
				//If the decode failed for some reason, try again with the next file.
				currentFileIndex ++;
				changeCurrentWallpaper();
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
		
		private Runnable changeWallpapers = new Runnable() {
			
			@Override
			public void run() {
				if (fileList.length > 0) {
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
			if (currentWallpaper != null && !currentWallpaper.isRecycled()) {
				Canvas canvas = getCurrentCanvas();
				if (canvas == null) return;
				
				canvas.drawBitmap(currentWallpaper, xPixelOffset, 0, null);
				getSurfaceHolder().unlockCanvasAndPost(canvas);
			}
		}
		
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
				
				canvas.drawBitmap(previousWallpaper, previousBitmapRect, previousCanvasRect, null);
				canvas.drawBitmap(currentWallpaper, currentBitmapRect, currentCanvasRect, null);
				getSurfaceHolder().unlockCanvasAndPost(canvas);
			}

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
		
		private void transitionWallpaper() {
			if (currentWallpaper != null && !currentWallpaper.isRecycled()) {
				if (previousWallpaper == null || previousWallpaper.isRecycled()) {
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
