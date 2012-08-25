package com.pandacoder.tests.mapview;

import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.pandacoder.tests.Utils.IOUtils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Класс для скачивания тайлов с сервера яндекса
 * @author Leonidos
 *
 */
class YandexTileMiner {
	
	private final static int CONNECTION_ESTABLISH_TIMEOUT_MS = 1000;
	private final static int SOCKET_TIMEOUT_MS = 5000;
	
	private final BitmapFactory.Options tileBitmapOptions;
	private final String baseTileSourceURL = "http://vec.maps.yandex.net/tiles?l=map&v=2.21.0&z=10";
	
	private final HttpClient httpClient;
	
	/**
	 * Скачивает тайлы с сервера яндекса.
	 * NOT THREAD SAFE.
	 */
	YandexTileMiner() {
		this.tileBitmapOptions = new BitmapFactory.Options();
		this.tileBitmapOptions.inPreferredConfig = TileSpecs.TILE_BITMAP_CONFIG;
		
		this.httpClient = new DefaultHttpClient(buildHttpClientParams());
	}
	
	private String buildURL(TileRequest tileRequest) {
		int reqTileSnX = tileRequest.getTileSpecs().xSn,
		    reqTileSnY = tileRequest.getTileSpecs().ySn;
		return baseTileSourceURL + "&x=" + reqTileSnX + "&y=" + reqTileSnY;
	}
	
	private HttpParams buildHttpClientParams() {
		HttpParams httpParameters = new BasicHttpParams();
		HttpConnectionParams.setConnectionTimeout(httpParameters, CONNECTION_ESTABLISH_TIMEOUT_MS);
		HttpConnectionParams.setSoTimeout(httpParameters, SOCKET_TIMEOUT_MS);
		return httpParameters;
	}

	/**
	 * Пытается скачать тайл с сервера яндекса. Если не получилось - возвращает null.
	 * @param tileRequest запрос на тайл
	 * @return изображение тайла или null, если скачивание не произошло
	 */
	public Bitmap getTileBitmap(TileRequest tileRequest) {

		Bitmap resultTileBitmap = null;
		HttpGet getTileRequest = new HttpGet(buildURL(tileRequest));
		
		try {
			HttpResponse response = httpClient.execute(getTileRequest);
			final int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != HttpStatus.SC_OK) {
				return null;
			}

			final HttpEntity entity = response.getEntity();
			if (entity != null) {
				InputStream inputStream = null;
				try {
					inputStream = entity.getContent();
					resultTileBitmap = BitmapFactory.decodeStream(inputStream, null, tileBitmapOptions);
				} finally {
					IOUtils.closeSilent(inputStream);
					entity.consumeContent();
				}
			}
		} catch (Exception ex) {	// не важно какое исключение произошло
			getTileRequest.abort();	// прекращаем запрос
		} 

		return resultTileBitmap;
	}
}
