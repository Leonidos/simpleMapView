package com.pandacoder.tests.mapview;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;

import android.graphics.Bitmap;
import android.os.StatFs;
import android.util.Log;

import com.pandacoder.tests.Utils.IOUtils;

/**
 * Класс для кеширования тайлов в постоянной памяти. Кеш умеет автоматически масштабировать себя
 * не занимая более 90% свободного места на диске, где он расположен.
 *
 */
public class TilesPersistentMemoryCache {
	
	private static final String LOG_TAG = TilesPersistentMemoryCache.class.getSimpleName();
	
	static class TilesPersistentMemoryCacheException extends RuntimeException {
		
		private static final long serialVersionUID = 647081435217110678L;

		TilesPersistentMemoryCacheException(String message) {
			super(message);
		}
		
		TilesPersistentMemoryCacheException(String message, Throwable cause) {
			super(message, cause);
		}
	} 
	
	/**
	 * После каждых RECHECK_AVAILABLE_SPACE_INTERVAL put операций в кеш, будет проверятся
	 * доступное место в директории отведенное под кеш. Кеш старается занять не больше 90%
	 * доступного на данный момент места.
	 * 
	 */
	private final static int RECHECK_AVAILABLE_SPACE_INTERVAL = 20;
	
	private final File cacheDir;
	private final LinkedHashMap<TileRequest, File> cacheMap ;
	private final int maxAllowedCacheMapSize;
	private int currentAllowedCacheMapSize;
	private final ByteBuffer tilePixelsBuffer;
	
	/**
	 * Создает кеш
	 * 
	 * @param cacheDirName директория, где будут храниться файлы
	 * @param sizeTiles размер кеша
	 * 
	 * @throws IllegalArgumentException, NullPointerException
	 */
	public TilesPersistentMemoryCache(String cacheDirName, int sizeTiles) {
		
		if (sizeTiles < 0) {
			throw new IllegalArgumentException("Tiles cache size shoulde be >= 0");
		}
		
		if (cacheDirName == null) {
			throw new NullPointerException("Tiels cacheDir is null. It's wrong.");
		}
				
		// Начинаем инициализировать кеш
		cacheDir = new File(cacheDirName);
		
		if (cacheDir.exists() == false) { // если директории для кеша еще нет, нужно ее создать
			if (!cacheDir.mkdirs()) {
				throw new TilesPersistentMemoryCacheException("Fail to create cache dir");
			}
		} else if (!cacheDir.isDirectory()) { // нам зачемто передали имя существующего файла	
			throw new TilesPersistentMemoryCacheException("Cache dir is a file. File is not a dir!");
		} else if (cacheDir.canRead() == false || cacheDir.canWrite() == false) { // не можем пичать/читать директорию
			throw new TilesPersistentMemoryCacheException("Cant read/write cache dir. Cant work.");
		}

		this.currentAllowedCacheMapSize = this.maxAllowedCacheMapSize = sizeTiles;
		this.cacheMap = new LinkedHashMap<TileRequest, File>(sizeTiles) {

			private static final long serialVersionUID = 232181184804078247L;

			@Override
			protected boolean removeEldestEntry(Entry<TileRequest, File> eldest) {
				
				if (size() > TilesPersistentMemoryCache.this.currentAllowedCacheMapSize) {
					removeCachedItem(eldest.getKey());
				}
				
				return false;
			}

			@Override
			public File remove(Object key) {
				Log.i(LOG_TAG, "removing item " + key.toString());
				return removeCachedItem(key);
			}
			
			private File removeCachedItem(Object key) {
				File tileFile = super.remove(key);
				if (tileFile != null) tileFile.delete();
				return null;
			}
		};
		tilePixelsBuffer = ByteBuffer.allocate(TileSpecs.TILE_BITMAP_SIZE_BYTES);
	}
	
	/**
	 * Возращает количество тайлов, для которых достаточно места в директории кеша. Вычисляет 
	 * общее свободное место в байтах, берет 90% от этого количества, чтобы совсем не забить 
	 * весь раздел. Полученное число делит на размер одного тайла.
	 * 
	 * @return количество тайлов
	 */
	private int getAvailableFsSpaceInTiles() {
		
		StatFs stat = new StatFs(cacheDir.getPath());
		long availableBytes = (long)stat.getAvailableBlocks() * stat.getBlockSize();
		long availableTiles = availableBytes / TileSpecs.TILE_BITMAP_SIZE_BYTES * 9 / 10; 

		return (int) availableTiles;
	}
	
	private String getTileFileNameFromTileRequest(TileRequest tileRequest) {
		TileSpecs tileSpecs = tileRequest.getTileSpecs();
		return tileSpecs.xSn + "x" + tileSpecs.ySn;
	}
	
	private TileRequest getTileRequestFromTileFileName(String fileName) {
		String [] parts = fileName.split("x");
		if (parts.length == 2) {
			try {
				int snX = Integer.parseInt(parts[0]);
				int snY = Integer.parseInt(parts[1]);
				if (snX > 0 && snY > 0) {
					return new TileRequest(new TileSpecs(snX, snY));
				}
				
			} catch (NumberFormatException ex) { 
				// ничего не поделаешь
				// возвращаем null
			}
		}
		
		return null;
	}
	
	/**
	 * Достает из кеша изображение запрошенного тайла
	 * @param tileRequest запрос тайла
	 * @param tileBitmap созданный заранее битмат требуемого размера, в который запишется результат
	 * @return true - если в кеше был такой тайл, false - если в кеш не попали
	 */
	public synchronized boolean get(TileRequest tileRequest, Bitmap tileBitmap) {
		
		if (cacheMap.containsKey(tileRequest) == false) return false;
		
		FileInputStream fis = null;
		try {
			File tileFile = cacheMap.get(tileRequest);
			if (tileFile != null) {
				fis = new FileInputStream(tileFile);
				int bytesRead = fis.read(tilePixelsBuffer.array());
				fis.close();
				
				if (bytesRead == tilePixelsBuffer.array().length) {
					tilePixelsBuffer.rewind();
					tileBitmap.copyPixelsFromBuffer(tilePixelsBuffer);
					return true;
				}
			}			
		} catch(Exception ex) { 
			// чтото пошло не так, ловим все исключения и говорим что в кеше ничего нет		
		} finally {
			if (fis != null) IOUtils.closeSilent(fis);
		}
		
		// не смогли для заданного запроса выдать информацию
		// удаляем информацию о нем
		cacheMap.remove(tileRequest);
		return false;
	}
	
	private int checkAvailableSpaceCounter = 0;
	private void scaleCacheMap() {
		if (--checkAvailableSpaceCounter < 0 || currentAllowedCacheMapSize <= cacheMap.size()) {
			long availablePlaceTiles = getAvailableFsSpaceInTiles();
			currentAllowedCacheMapSize = (int) (cacheMap.size() + availablePlaceTiles);
			
			if (availablePlaceTiles > 0) {	// если еще есть место под тайлы 
				if (currentAllowedCacheMapSize > maxAllowedCacheMapSize) {
					currentAllowedCacheMapSize = maxAllowedCacheMapSize;
				}
				
				checkAvailableSpaceCounter = RECHECK_AVAILABLE_SPACE_INTERVAL;
			} else {
				// нужно уменьшить кеш
				// удалим из него одну запись
				Iterator<TileRequest> it = cacheMap.keySet().iterator();
				if (it.hasNext()) {
					cacheMap.remove(it.next());
				}
				// уменьшим разрешенный размер
				--currentAllowedCacheMapSize;
				if (currentAllowedCacheMapSize < 0) currentAllowedCacheMapSize = 0;
				checkAvailableSpaceCounter = 0;
			}
			
		}
	}
	/**
	 * Кладет в кеш изображение тайла. Содержимое битмапа записывается в файл. Если положить в кеш не удалось - молчит.
	 * @param tileRequest
	 * @param tileBitmap
	 */
	public synchronized void put(TileRequest tileRequest, Bitmap tileBitmap) {
	
		scaleCacheMap();
		if (checkAvailableSpaceCounter <= 0) return;
		
		FileOutputStream fos = null;
		try {			
			//  сначала копируем пиксели в буфер
			tileBitmap.copyPixelsToBuffer(tilePixelsBuffer);
			tilePixelsBuffer.rewind();
						
			File imageFile = new File(cacheDir, getTileFileNameFromTileRequest(tileRequest));
			fos = new FileOutputStream(imageFile, false);
			fos.write(tilePixelsBuffer.array());
			fos.close();
			
			cacheMap.put(tileRequest, imageFile);
			
		} catch(Exception ex) {	// ловим все исключения 
			// тут ничего не поделаешь, чтото пошло не так
		} finally {
			IOUtils.closeSilent(fos);
		}		
	}

	
	/**
	 * Восстанавливает кеш. Получает файлы тайлов из директории кеша и начинает из использовать 
	 */
	public synchronized void restore() {
		cacheMap.clear();
		File[] cachedFolderFiles = cacheDir.listFiles();
		for (File cachedFile : cachedFolderFiles) {
			String tileFileName = cachedFile.getName();
			if (tileFileName != null) {
				TileRequest tileRequest = getTileRequestFromTileFileName(tileFileName);
				if (tileRequest != null) {
					cacheMap.put(tileRequest, cachedFile);
				}
			}
		}
	} 
}
