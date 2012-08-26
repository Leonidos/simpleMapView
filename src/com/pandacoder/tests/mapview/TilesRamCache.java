package com.pandacoder.tests.mapview;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

import android.graphics.Bitmap;

/**
 * Кеш для тайлов в оперативной памяти заданного размера. В кеше хранятся копии.
 *  
 */
public class TilesRamCache {
	
	private final int size;
	private LinkedHashMap<TileRequest, Bitmap> cache;
	private LinkedList<Bitmap> bitmapPool;
	private final ByteBuffer bitmapPixelsBuffer;
	
	/**
	 * Создает кеш для тайлов заданного размера.
	 * @param size
	 */
	public TilesRamCache(int size) {
		this.size = size;
		
		// используем LinkedHashMap, чтобы знать самый старый элемент и выкидывать его
		// если заканчивается место
		cache = new LinkedHashMap<TileRequest, Bitmap>(this.size) {

			private static final long serialVersionUID = 3570299093021796984L;

			@Override
			protected boolean removeEldestEntry(Map.Entry<TileRequest, Bitmap> eldest) {
		        if (size() > TilesRamCache.this.size) {
		        	remove(eldest.getKey());
		        	TilesRamCache.this.bitmapPool.add(eldest.getValue());	        	
		        }
		        return false;
		    }
		};
		
		bitmapPool = new LinkedList<Bitmap>();
		for (int i = 0; i <= this.size; i++) {
			bitmapPool.add(Bitmap.createBitmap(TileSpecs.TILE_SIZE_WH_PX, TileSpecs.TILE_SIZE_WH_PX, TileSpecs.TILE_BITMAP_CONFIG));
		}
		
		bitmapPixelsBuffer = ByteBuffer.allocate(TileSpecs.TILE_BITMAP_SIZE_BYTES);
	}
	
	public synchronized void put(TileRequest tileRequest, Bitmap tileBitmap) {
		if (cache != null && cache.get(tileRequest) == null) { // такого тайла у нас еще нет
			tileBitmap.copyPixelsToBuffer(bitmapPixelsBuffer);
			bitmapPixelsBuffer.rewind();
		    Bitmap temp = bitmapPool.remove();
		    temp.copyPixelsFromBuffer(bitmapPixelsBuffer);
		    cache.put(tileRequest, temp);
		}
	}
	
	public synchronized Bitmap get(TileRequest tileRequest) {
		if (cache != null) return cache.get(tileRequest);
		else return null;
	}
	
	public synchronized void destroy() {
		if (cache != null) {
			for (Bitmap tileBitmap:cache.values()) {
				tileBitmap.recycle();
			}
			
			for (Bitmap bitmap : bitmapPool) {
		        bitmap.recycle();
		    }
			
			cache.clear();
			cache = null;
		}
	}
}
