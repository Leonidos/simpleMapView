package com.pandacoder.tests.mapview;

import android.graphics.Rect;

public class MapProjection {
	
	
	private Rect visibleRect;
	
	// номер тайлов, которые сейчас пересекают область видимости
	int topLeftTileXsn,			// верхний			номер по Х
	    topLeftTileYsn,			// левый тайл		номер по y
	    bottomRightTileXsn,		// нижний			номер по Х
	    bottomRightTileYsn;		// правый тайл		номер по Y
	
	// левая верхняя вершиная этого тайла, выбрана за нулевые координаты
	public  final static int MAP_CENTER_TILE_X_SN = 619,
							 MAP_CENTER_TILE_Y_SN = 321;
	
	public MapProjection() {
		visibleRect = new Rect();
	}
	
	public void setProjectionsParams(int screenWidth, int screenHeight, int mapCenterOffsetX, int mapCenterOffsetY) {
		
		visibleRect.left  = mapCenterOffsetX - screenWidth/2;
		visibleRect.right = mapCenterOffsetX + screenWidth/2;
		visibleRect.top = mapCenterOffsetY - screenHeight/2;
		visibleRect.bottom = mapCenterOffsetY + screenHeight/2;
		
		topLeftTileXsn = calcTileSnHalper(visibleRect.left, TileSpecs.TILE_SIZE_WH_PX, MAP_CENTER_TILE_X_SN);
		topLeftTileYsn = calcTileSnHalper(visibleRect.top, TileSpecs.TILE_SIZE_WH_PX, MAP_CENTER_TILE_Y_SN);
		
		bottomRightTileXsn = calcTileSnHalper(visibleRect.right, TileSpecs.TILE_SIZE_WH_PX, MAP_CENTER_TILE_X_SN);
		bottomRightTileYsn = calcTileSnHalper(visibleRect.bottom, TileSpecs.TILE_SIZE_WH_PX, MAP_CENTER_TILE_Y_SN);
	}
	
	private int calcTileSnHalper(int coord, int tileSizePx, int mapCenterTileSn) {
		int offset = (coord < 0)?tileSizePx:0;
		return (coord - offset)  / tileSizePx + mapCenterTileSn;
	}
	
	public int getMinTileSnX() {
		return topLeftTileXsn;
	}
	
	public int getMinTileSnY() {
		return topLeftTileYsn;
	}
	
	public int getMaxTileSnX() {
		return bottomRightTileXsn;
	}
	
	public int getMaxTileSnY() {
		return bottomRightTileYsn;
	}
	
	public boolean isTileNotVisible(TileSpecs tile) {
		return (tile.xSn < topLeftTileXsn) || (tile.xSn > bottomRightTileXsn) ||
			   (tile.ySn < topLeftTileYsn) || (tile.ySn > bottomRightTileYsn);
	}
	
	public int getTileScreenX(TileSpecs tile) {
		return (tile.xSn - MAP_CENTER_TILE_X_SN)*TileSpecs.TILE_SIZE_WH_PX - visibleRect.left;
	}
	
	public int getTileScreenY(TileSpecs tile) {
		return (tile.ySn - MAP_CENTER_TILE_Y_SN)*TileSpecs.TILE_SIZE_WH_PX - visibleRect.top;
	}
}
