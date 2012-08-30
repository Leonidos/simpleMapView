package com.pandacoder.tests.mapview;

import java.util.LinkedList;
import java.util.Stack;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * Центр обработки тайлов. Принимает запросы на выдачу изображений тайлов. Качает тайлы из сети или берет из
 * кеша в постоянной памяти. Скачанные тайлы ложит в кеш. Скачивание происходит параллельно, без прерывания
 * процесса обоработки запросов на тайлы и работы с кешем.
 * 
 */
public class TilesProcessorCenter extends Thread {
	
	private final static String LOG_TAG = TilesProcessorCenter.class.getSimpleName();
	private final static int TILE_MINER_EXECUTOR_POOL_SIZE = getTileDownloaderExecutorPoolSize();
	private final static int TILE_MINER_EXECUTOR_TASK_QUEUE_SIZE = 64; // хватит на все случаи жизни
	
	/**
	 * Определяет количество рабочих потоков для скачивания тайлов, скачивание в несколько
	 * потоков может дать прирост скорости
	 * 
	 * @return 1 - на одноядерных устройствах, 2 - на многоядерных
	 */
	private final static int getTileDownloaderExecutorPoolSize() {
		return (Runtime.getRuntime().availableProcessors() == 1)?1:2;
	}
	
	private final SimpleMapView mapView;
	private final Stack<TileRequest> tileRequestsStackQueue;
	private final LinkedList<Future<?>> runningTileMiners;	// состояния запрошеных работ
	private final YandexTileMiner tileMiner;
	private final TileMinerExecutorService tileMineExecutor;
	
	private final TilesPersistentMemoryCache tilesPersistentCache;
	
	private Bitmap requestedTileBitmap;
	
	private boolean paused = true;
	
	/**
	 * Создает центр обработки тайлов.
	 * 
	 * @param mapView вид-карта
	 * @param tilesPersistentCache кеш в постоянной памяти, если null - не используется
	 * 
	 * @throws NullPointerException если mapView == null
	 */
	TilesProcessorCenter(SimpleMapView mapView, TilesPersistentMemoryCache tilesPersistentCache) {
		
		if (mapView == null) throw new NullPointerException("mapView can't be null");
		
		this.mapView = mapView;
		this.tilesPersistentCache = tilesPersistentCache;
		
		this.tileRequestsStackQueue = new Stack<TileRequest>();
		this.tileMiner = new YandexTileMiner();
		this.tileMineExecutor = new TileMinerExecutorService(TILE_MINER_EXECUTOR_POOL_SIZE, TILE_MINER_EXECUTOR_TASK_QUEUE_SIZE);
		this.runningTileMiners = new LinkedList<Future<?>>();
		
		this.requestedTileBitmap = Bitmap.createBitmap(TileSpecs.TILE_SIZE_WH_PX, TileSpecs.TILE_SIZE_WH_PX, TileSpecs.TILE_BITMAP_CONFIG);
	}

	//TODO надо бы переделать это место
	/**
	 * Запускает поток, с основной логикой обрабоки запросов на тайлы. 
	 * Пытается восстановить tilesPersistentCache, потом:
	 * ждет новых запросов, если очередь запросов пуста;
	 * просматривает кеш тайлов;
	 * скачивает новые тайлы из сети, если не попал в кеш.
	 */
	@Override
	public void run() {
		
		if (tilesPersistentCache != null) tilesPersistentCache.restore();
		
		while (!isInterrupted()) {
		
			// ждем пока нам не дадут новых заданий или не прервут нас
			synchronized(this) {
				if (tileRequestsStackQueue.isEmpty() || paused == true) {
					try {
						wait();
					} catch (InterruptedException ex) {
						interrupt();
					}
				}
			}
			
			if (isInterrupted()) break;
				
			TileRequest currentTileRequest = null;
			synchronized(this) {
				if (tileRequestsStackQueue.isEmpty()) {
					continue;
				} else {
					currentTileRequest = tileRequestsStackQueue.pop();
				}
			}
			
			if (isInterrupted()) break;
			
			boolean tileWasInCache = false;
			if (tilesPersistentCache != null) {
				tileWasInCache = tilesPersistentCache.get(currentTileRequest, requestedTileBitmap);
				if (tileWasInCache == true) {
					mapView.addTileOnMapBitmap(currentTileRequest, requestedTileBitmap);
					//Log.i(LOG_TAG, "FROM FLASH: " + currentTileRequest);
				}
			}
			
			if (isInterrupted()) break;
			
			if (tileWasInCache == false) { // нужно скачать тайл
				addTileRequestToTileMinerExecutor(currentTileRequest);
			}			
		}
	}
	
	private void addTileRequestToTileMinerExecutor(TileRequest tileRequest) {
		 
		Runnable tileDownloadJob = new TileMinerExecutorService.TileMinerRunnable(tileRequest){

			@Override
			public void run() {
				if (isCanceled() == false) { // если задание не отменили 
					Bitmap tileBitmap = tileMiner.getTileBitmap(tileRequest);
					if (tileBitmap != null) {
						
						if (tilesPersistentCache != null) {	// если ест кеш во флеше
							tilesPersistentCache.put(tileRequest, tileBitmap);
						}
						
						mapView.addTileOnMapBitmap(tileRequest, tileBitmap);					
						tileBitmap.recycle();
					}				
				}
			}			
		};
		
		try {
			runningTileMiners.add(tileMineExecutor.submit(tileDownloadJob));
		} catch (RejectedExecutionException ex) { 
			// задание не было принято... видимо по какой-то причине заполнилась
			// очередь, попробуем ее очистить
			tileMineExecutor.clearTaskQueue();			
		}		
	}

	/**
	 * Добавляет в очередь запрос на опеределенный тайл.
	 * @param tileRequest запрос
	 */
	public synchronized void request(TileRequest tileRequest) {
		if (!tileRequestsStackQueue.contains(tileRequest)) {
			tileRequestsStackQueue.push(tileRequest);
		}
	}
	
	/**
	 * Очищает очередь запросов на тайлы. Можно вызвать
	 * перед добавлением запросов на новые тайлы, чтобы отменить
	 * старые еще не обработанные запросы.
	 * 
	 * Тайлы, которые успели начать качаться - докачиваются.
	 */
	public synchronized void clearRequestQueue() {
		tileRequestsStackQueue.clear();
		for (Future<?> runningTask :runningTileMiners) {
			runningTask.cancel(false);	
		}
		runningTileMiners.clear();
	}
	
	/**
	 * Инициирует обработку запросов, из очереди. Вызывать после добавления новых 
	 * запросов в очередь методом request.
	 */
	public synchronized void doRequests() {
		notify();
	}
	
	/**
	 * Ставит процессор тайлов на паузу. Новые задание не начинают обработку.
	 */
	public synchronized void pauseProcessing() {
		paused = true;
		notify();
	}
	
	/**
	 * Возобновляет процесс обработки тайлов, если он до этого был остановлен
	 * функцией {@link #pauseProcessing} 
	 */
	public synchronized void resumeProcessing() {
		paused = false;
		notify();
	}
	
	
	/**
	 * Очищает ресурсы
	 */
	public synchronized void destroy() {
		
		if (requestedTileBitmap != null) {
			requestedTileBitmap.recycle();
			requestedTileBitmap = null;
		}		
		
		if (tileMineExecutor.isShutdown() == false) {
			tileMineExecutor.shutdownNow();
		}
	} 
}
