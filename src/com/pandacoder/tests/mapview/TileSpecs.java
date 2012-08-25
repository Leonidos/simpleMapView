package com.pandacoder.tests.mapview;

import android.graphics.Bitmap;

public class TileSpecs {
	
	public static final int TILE_SIZE_WH_PX = 256;
	public static final Bitmap.Config TILE_BITMAP_CONFIG = Bitmap.Config.RGB_565; // экономим
	public static final int TILE_BITMAP_SIZE_BYTES = calcBitmapSizeBytes();
	
	public final int xSn, ySn;
	
	private static int calcBitmapSizeBytes() {
		
		int sizePixels = TILE_SIZE_WH_PX * TILE_SIZE_WH_PX;
		int bytesPerPixel = 1;
		
		switch (TILE_BITMAP_CONFIG) {
		case ARGB_4444:
		case RGB_565: 	bytesPerPixel = 2; break;
		case ARGB_8888: bytesPerPixel = 4; break;
		case ALPHA_8:			
		default: break;
		}
		
		return sizePixels * bytesPerPixel;
	}
	
	public TileSpecs(int xSn, int ySn) {
		this.xSn = xSn;
		this.ySn = ySn;
	}

	@Override
	public boolean equals(Object o) {
		
		if (o instanceof TileSpecs) {
			
			TileSpecs other = (TileSpecs)o;
			
			return xSn == other.xSn && ySn == other.ySn;
		}
		
		return false;
	}

	@Override
	public int hashCode() {
		return (33*xSn)^ySn;
	}

	@Override
	public String toString() {
		return "Tile: xSn = " + xSn + " ySn = " + ySn;
	}	
}
