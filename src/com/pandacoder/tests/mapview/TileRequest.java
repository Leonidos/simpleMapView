package com.pandacoder.tests.mapview;

/**
 * Класс-запрос на определенный тайл
 *
 */
public class TileRequest {
	
	private final TileSpecs tileSpecs;
	
	public TileRequest(TileSpecs tileSpecs) {
		this.tileSpecs = tileSpecs;
	}
	
	public TileSpecs getTileSpecs() {
		return tileSpecs;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof TileRequest) {
			TileRequest other = (TileRequest)o;			
			if (tileSpecs == null && other.tileSpecs != null) return false;
			else return tileSpecs.equals(other.tileSpecs);
		}
		return super.equals(o);
	}

	@Override
	public int hashCode() {
		return 31*tileSpecs.hashCode();
	}

	@Override
	public String toString() {
		return "Req for " + tileSpecs.toString();
	}	
}
