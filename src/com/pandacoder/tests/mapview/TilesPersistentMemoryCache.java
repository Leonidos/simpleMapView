package com.pandacoder.tests.mapview;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;

import android.graphics.Bitmap;
import android.util.Log;

import com.pandacoder.tests.Utils.IOUtils;

/**
 * Класс для кеширования тайлов в постоянной памяти
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
	
	private final File cacheDir;
	private final LinkedHashMap<TileRequest, File> cacheMap ;
	private final int size;
	private final ByteBuffer tilePixelsBuffer;
	
	/**
	 * Создает кеш
	 * 
	 * @param cacheDirName директория, где будут храниться файлы
	 * @param size размер кеша
	 * 
	 * @throws IllegalArgumentException, NullPointerException
	 */
	public TilesPersistentMemoryCache(String cacheDirName, int size) {
		
		if (size < 0) {
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

		this.size = size;		
		this.cacheMap = new LinkedHashMap<TileRequest, File>(size) {

			private static final long serialVersionUID = 232181184804078247L;

			@Override
			protected boolean removeEldestEntry(Entry<TileRequest, File> eldest) {
				
				if (size() > TilesPersistentMemoryCache.this.size) {
					removeCachedItem(eldest.getKey());
					//Log.i(LOG_TAG, "Deleted file: " + eldest.getKey());
				}
				
				return false;
			}

			@Override
			public File remove(Object key) {
				// TODO Auto-generated method stub
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
	
	/**
	 * Кладет в кеш изображение тайла. Содержимое битмапа записывается в файл.
	 * @param tileRequest
	 * @param tileBitmap
	 */
	public synchronized void put(TileRequest tileRequest, Bitmap tileBitmap) {
		
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
