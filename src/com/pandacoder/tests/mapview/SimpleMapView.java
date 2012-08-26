package com.pandacoder.tests.mapview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ViewGroup;


/**
 * Оцень простой рендерер карты из тайлов от яндекса.
 * Пользваваться с осторожность.
 * 
 * <p>Не забывать вызывать:
 * <ul>
 * 	<li> {@link #destroy} чтобы очистить ресурсы 
 * 	<li> {@link #resumeTileProcessing} когда карта активна
 * 	<li> {@link #pauseTileProcessing} когда карта в фоне
 * </ul>
 * 
 * 
 *
 */
public class SimpleMapView extends ViewGroup {
	
	private final String LOG_TAG = SimpleMapView.class.getSimpleName();
	
	private final int MAP_BG_COLOR = 0x000000;
	
	// по заданию карта должна быть 100х100, сервер отдает намного больше тайлов
	// поэтому не будем давать пользователю сдвинуть карту больше чем
	// на 50 тайлов в любую сторону
	private final static int MAP_MAXMIN_XY_ALLOWED_COORDS = 100*TileSpecs.TILE_SIZE_WH_PX/2;
	
	private int currentMapCenterOffsetXp = 0,
				currentMapCenterOffsetYp = 0;
	
	private Bitmap mapViewBitmap1;	// это основной, который рисуем 	// оказалось нужно 2 битмала чтобы
	private Bitmap mapViewBitmap2;	// это вспомогательный			 	// оптимальней пепедвигать карту 
	private Canvas mapViewCanvas;
	private Matrix mapViewBitmapMatrix;	// матрица, определяющая смещение mapViewBitmap1. использовать только из synchronized секции.
		
	private TilesProcessorCenter tileProcessor;
	private MapProjection mapProjection;
	
	private TouchEventHandler touchEventHandler;
	
	private final static int TILES_RAM_CACHE_SIZE = 16;	// tiles 16*256*256*2 ~ 2.1Mb ram
	private final static int TILES_PERSISTENT_MEMORY_CACHE_SIZE = 55; // tiles 55*256*256*2 ~7.2Mb flash 
	
	private TilesRamCache tilesRamCache; 
	private TilesPersistentMemoryCache tilesMemoryCache;
	

	public SimpleMapView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initThis();
	}

	public SimpleMapView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initThis();
	}

	public SimpleMapView(Context context) {
		super(context);
		initThis();
	}
	
	private String generateCacheDirectoryName() {
		String dirName = Environment.getExternalStorageDirectory().getAbsolutePath();
		dirName += "/" + getContext().getPackageName() + "/SimpleMapViewCache";
		return dirName;
	}
	
	private void initThis() {
		setBackgroundColor(MAP_BG_COLOR);
		
		tilesRamCache = new TilesRamCache(TILES_RAM_CACHE_SIZE);
		try {
			String cacheDirectoryName = generateCacheDirectoryName();
			tilesMemoryCache = new TilesPersistentMemoryCache(cacheDirectoryName, TILES_PERSISTENT_MEMORY_CACHE_SIZE);
		} catch(Exception ex) {
			// чтото пошло не так при инициализации кеша в постоянной памяти
			// ничего не поделаешь, работаем без этого кеша
		}
		
		mapViewBitmapMatrix = new Matrix();
		mapProjection = new MapProjection();
		
		touchEventHandler = new TouchEventHandler();
		
		tileProcessor = new TilesProcessorCenter(this, tilesRamCache, tilesMemoryCache);
		tileProcessor.start();
	}
	
	/**
	 * Запускает обработку тайлов процессинговым центром
	 */
	public void resumeTileProcessing() {
		tileProcessor.resumeProcessing();
	}
	
	/**
	 * Останавливает обработку тайлов процессинтовым центором
	 */
	public void pauseTileProcessing() {
		tileProcessor.pauseProcessing();
	}

	/**
	 * Простенький обработчик прикосновений, позволяет перетаскивать карту пальцем
	 * и центрировать ее по двойному нажатию
	 *  	
	 */
	private class TouchEventHandler {
		
		private int previousActionDownX,
					previousActionDownY;
		
		boolean handleTouchEvent(MotionEvent event) {
			
			int eventX = (int) event.getX(),
				eventY = (int) event.getY();
			
			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				previousActionDownX = eventX;
				previousActionDownY = eventY;
				return true;
				
			case MotionEvent.ACTION_UP:
				break;
				
			case MotionEvent.ACTION_MOVE:
				
				int moveMapX = eventX - previousActionDownX,
					moveMapY = eventY - previousActionDownY;
				
				previousActionDownX = eventX;
				previousActionDownY = eventY;
				
				if (moveMapX != 0 || moveMapY != 0) {
					translateMap(moveMapX, moveMapY);
					requestRequiredTiles();
				}
				
				return true;
			}

			return false;
		}
	}

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		/*
		 *		 на будущее 
		 */
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return touchEventHandler.handleTouchEvent(event);
	}
	
	private void translateMap(int dx, int dy) {
		synchronized(mapViewBitmapMatrix) {
			
			// ограничение на передвижение карты
			if (Math.abs(currentMapCenterOffsetXp - dx) > MAP_MAXMIN_XY_ALLOWED_COORDS) dx = 0;
			if (Math.abs(currentMapCenterOffsetYp - dy) > MAP_MAXMIN_XY_ALLOWED_COORDS) dy = 0;
			
			mapViewBitmapMatrix.postTranslate(dx, dy);
			currentMapCenterOffsetXp -= dx;
			currentMapCenterOffsetYp -= dy;
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		
		if (mapViewBitmap1 == null) return;
		
		synchronized(mapViewBitmapMatrix) {
			canvas.drawBitmap(mapViewBitmap1, mapViewBitmapMatrix, null);
		}
	}
		
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {

		if (mapViewBitmap1 != null) {
			mapViewBitmap1.recycle();
		}
		
		if (mapViewBitmap2 != null) {
			mapViewBitmap2.recycle();
		}
		
		mapViewBitmap1 = Bitmap.createBitmap(w, h, TileSpecs.TILE_BITMAP_CONFIG);
		mapViewBitmap2 = Bitmap.createBitmap(w, h, TileSpecs.TILE_BITMAP_CONFIG);
		mapViewCanvas = new Canvas(mapViewBitmap1);
		
		requestRequiredTiles();
	}
	
	private void requestRequiredTiles() {
		
		int viewWidth = getWidth();
		int viewHeight = getHeight();
		
		mapProjection.setProjectionsParams(viewWidth, viewHeight, currentMapCenterOffsetXp, currentMapCenterOffsetYp);
				
		int minXrequiredTile = mapProjection.getMinTileSnX(),
			maxXrequiredTile = mapProjection.getMaxTileSnX(),
			minYrequiredTile = mapProjection.getMinTileSnY(),
			maxYrequiredTile = mapProjection.getMaxTileSnY();
		
		tileProcessor.clearRequestQueue();
				
		for (int x = minXrequiredTile; x <= maxXrequiredTile; x++) {
			for (int y = minYrequiredTile; y <= maxYrequiredTile; y++) {
				
				// запрос на необхожимый тайл
				TileRequest tileRequest = new TileRequest(new TileSpecs(x,y));
				
				synchronized (tilesRamCache) {
					// сначала проверим, может быть тайл есть в РАМ кеше
					Bitmap tileBitmap = tilesRamCache.get(tileRequest);
					if (tileBitmap != null) {
						if (tileBitmap.getConfig() != TileSpecs.TILE_BITMAP_CONFIG) Log.e(LOG_TAG, "Tile Bitmap Config Error");
						addTileOnMapBitmap(tileRequest, tileBitmap);
						continue;
					}
				}
								
				tileProcessor.request(tileRequest);
				Log.i(LOG_TAG, "requested tile: snX=" + x + " snY=" + y);
			}
		}
		
		tileProcessor.doRequests();
		
		invalidate();
	}
	
	/**
	 * Отрисовывает на карте тайл. Может вызываться из разных потоков.
	 * 
	 * @param tileRequest запрос тайла
	 * @param tileBitmap изображение тайла
	 */
	synchronized public void addTileOnMapBitmap(TileRequest tileRequest, Bitmap tileBitmap) {
		
		// сейчас будет интересное место, где надо разобраться, что делать если
		// карта была сдвинута
		
		int tileScreenX, tileScreenY;
		synchronized(mapViewBitmapMatrix) {
			if (!mapViewBitmapMatrix.isIdentity()) {
				
				// берем запасной битмап
				mapViewBitmap2.eraseColor(MAP_BG_COLOR);	// стираем его
				mapViewCanvas.setBitmap(mapViewBitmap2);	// готовимся на нем рисовать
				
				mapViewCanvas.drawBitmap(mapViewBitmap1, mapViewBitmapMatrix, null);
				mapViewBitmapMatrix.reset();
				
				Bitmap temp = mapViewBitmap1;
				mapViewBitmap1 = mapViewBitmap2;
				mapViewBitmap2 = temp;			
			}
			
			tileScreenX = mapProjection.getTileScreenX(tileRequest.getTileSpecs());
			tileScreenY	= mapProjection.getTileScreenY(tileRequest.getTileSpecs());		
		}
		
		mapViewCanvas.drawBitmap(tileBitmap, tileScreenX, tileScreenY, null);
		postInvalidate();
	}
	
	/**
	 * Правильно очищает ресурсы. Вызывать, когда карта больше не нужна.
	 */
	public void destroy() {
		
		if (tileProcessor != null) {
			tileProcessor.interrupt();
			try {
				tileProcessor.join();
				Log.i(LOG_TAG, "TileProcessor's thread dead now");
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			tileProcessor.destroy();
			tileProcessor = null;
		}		
		
		if (mapViewBitmap1 != null) {
			mapViewBitmap1.recycle();
			mapViewBitmap1 = null;
		}
		
		if (mapViewBitmap2 != null) {
			mapViewBitmap2.recycle();
			mapViewBitmap2 = null;
		}
		
		if (tilesRamCache != null) {
			tilesRamCache.destroy();
			tilesRamCache = null;
		}		
	}
}
