package com.pandacoder.tests.mapview;

import java.util.LinkedHashMap;
import java.util.Map;

import android.graphics.Bitmap;

/**
 * Кеш для тайлов в оперативной памяти заданного размера. Изображения копируются перед помещение в кеш
 * 
 */
public class TilesRamCache {
	
	private final int size;
	private LinkedHashMap<TileRequest, Bitmap> cache;
	
	/**
	 * Создает кеш для тайлов заданного размера.
	 * @param size
	 */
	TilesRamCache(int size) {
		this.size = size;
		
		// используем LinkedHashMap, чтобы знать самый старый элемент и выкидывать его
		// если заканчивается место
		cache = new LinkedHashMap<TileRequest, Bitmap>(this.size) {

			private static final long serialVersionUID = 3570299093021796984L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<TileRequest, Bitmap> eldest) {
		        if (size() > TilesRamCache.this.size) {
		        	remove(eldest.getKey());
		        	eldest.getValue().recycle();		        	
		        }
		        return false;
		    }
		};
	}
	
	synchronized void put(TileRequest tileRequest, Bitmap tileBitmap) {
		if (cache != null && cache.get(tileRequest) == null) { // такого тайла у нас еще нет
			cache.put(tileRequest, Bitmap.createBitmap(tileBitmap));
		}
	}
	
	synchronized Bitmap get(TileRequest tileRequest) {
		if (cache != null) return cache.get(tileRequest);
		else return null;
	}
	
	synchronized void destroy() {
		if (cache != null) {
			for (Bitmap tileBitmap:cache.values()) {
				tileBitmap.recycle();
			}
			
			cache.clear();
			cache = null;
		}
	}
}
