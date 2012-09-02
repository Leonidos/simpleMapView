package com.pandacoder.tests.mapview;

import java.util.LinkedList;
import java.util.Stack;
import java.util.concurrent.RejectedExecutionException;

import android.graphics.Bitmap;
import android.util.Log;

/**
 * Центр обработки тайлов. Принимает запросы на выдачу изображений тайлов. Качает тайлы из сети или берет из
 * кеша в постоянной памяти. Скачанные тайлы ложит в кеш. Скачивание происходит параллельно, без прерывания
 * процесса обоработки запросов на тайлы и работы с кешем.
 * 
 * Используйте {@link#destroy}, чтобы остановить центр обработки тайлой и очистить ресурсы
 * 
 */
public class TilesProcessorCenter extends Thread {
	
	private final static String LOG_TAG = TilesProcessorCenter.class.getSimpleName();
	private final static int TILE_MINER_EXECUTOR_POOL_SIZE = getTileDownloaderExecutorPoolSize();
	
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
	private final LinkedList<TileRequest> delayedTileMiningJobs;
	private final YandexTileMiner tileMiner;
	private final TileMinerExecutorService tileMineExecutor;
	
	private final TilesPersistentMemoryCache tilesPersistentCache;
	
	private Bitmap requestedTileBitmap;
	
	private boolean paused = true;
	private boolean delayedTileMiningJobChecked = true;
	
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
		this.delayedTileMiningJobs = new LinkedList<TileRequest>();
		this.tileMiner = new YandexTileMiner();
		this.tileMineExecutor = new TileMinerExecutorService(TILE_MINER_EXECUTOR_POOL_SIZE);
		
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
		
			/*
			 *	Сначала смотрим стоит ли нам работать 
			 */
			synchronized(this) {
				
				/*
				 *	если мы на паузе - ждем
				 *  если не на паузе, смотрим
				 *  	если нет запросов на тайлы и нам не нужно проперить отложенные запросы - ждем
				 *  в других случаях работаем 
				 */
				if ((tileRequestsStackQueue.isEmpty() && delayedTileMiningJobChecked == true) || paused == true) {
					
					try {
						wait();
					} catch (InterruptedException ex) {
						interrupt();
					}
				}
			}
			
			if (isInterrupted()) break;
				
			TileRequest currentTileRequest = null;		// задание обрабатываемое на этой итерации
			synchronized(this) {
				
				if (tileRequestsStackQueue.isEmpty() == false) { 		// если есть запросы на тайлы 
					currentTileRequest = tileRequestsStackQueue.pop();	// берем на обработку самый свежий	
				} else {												// если запросов нет, то
					if (delayedTileMiningJobChecked == false) {			// нужно проверить если ли отложенные работы
						if (delayedTileMiningJobs.isEmpty() == false) {	// если есть отложенные запросы
							currentTileRequest = 						// достаем отложенный запрос
									delayedTileMiningJobs.remove();		// будет его обрабатывать
						} else {										// если отложенных запросов тоже нет
							delayedTileMiningJobChecked = true;			// ставим флаг, что все проверили
						}
					}					
				}
				
				if (currentTileRequest == null) continue; // если задания нет, переходим к следующей итерации
			}
			
			if (isInterrupted()) break;
			
			boolean tileWasInCache = false;
			if (tilesPersistentCache != null) {
				tileWasInCache = tilesPersistentCache.get(currentTileRequest, requestedTileBitmap);
				if (tileWasInCache == true) {
					mapView.addTileOnMapBitmap(currentTileRequest, requestedTileBitmap);
				}
			}
			
			if (isInterrupted()) break;
			
			if (tileWasInCache == false) { // нужно скачать тайл
				
				Runnable tileDownloadJob = buildRunnableForTileMinerExecutor(currentTileRequest);
				
				try {
					tileMineExecutor.execute(tileDownloadJob);
				} catch (RejectedExecutionException ex) { 
					// задание не было принято... видимо все потоки заняты
					// положим его в очеред к отложенным
					synchronized(this) {	
						delayedTileMiningJobs.add(currentTileRequest);
						delayedTileMiningJobChecked = true;
					}
				}		
			}			
		}
	}
	
	
	private Runnable buildRunnableForTileMinerExecutor(TileRequest tileRequest) {
		 
		Runnable tileDownloadJob = new TileMinerExecutorService.TileMinerRunnable(tileRequest) {

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
				TilesProcessorCenter.this.checkDelayedTileMiningJobs();
			}			
		};
		
		return tileDownloadJob;
	}
	
	/**
	 * Запускает процесс проверки очереди отложенный заданий на добычу тайтов.
	 */
	private synchronized void checkDelayedTileMiningJobs() {
		delayedTileMiningJobChecked = false;
		notify();
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
		delayedTileMiningJobs.clear();
	}
	
	/**
	 * Инициирует обработку запросов, из очереди. Вызывать после добавления новых 
	 * запросов в очередь методом request.
	 */
	public synchronized void doRequests() {
		notify();
	}
	
	/**
	 * Ставит процессор тайлов на паузу. Новые задание не начинают обработку, старые доделываются.
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
		
		// сначала останавливаем tileMineExecutor
		if (tileMineExecutor.isShutdown() == false) {
			tileMineExecutor.shutdownNow();
		}
		
		// теперь останавливаем себя
		interrupt();
		try {
			join(200);	// подождем немного пока все остановится
			Log.i(LOG_TAG, "TileProcessor's thread dead now");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
		// очищаем ресурсы
		if (requestedTileBitmap != null) {
			requestedTileBitmap.recycle();
			requestedTileBitmap = null;
		}
	} 
}
