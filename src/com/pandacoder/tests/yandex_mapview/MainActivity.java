package com.pandacoder.tests.yandex_mapview;

import com.pandacoder.tests.mapview.SimpleMapView;

import android.os.Bundle;
import android.app.Activity;

/**
 * Пример простой активити с созданной картой
 * @author Leonidos
 *
 */
public class MainActivity extends Activity {
	
	private SimpleMapView mapView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);        
        mapView = (SimpleMapView) findViewById(R.id.mapView);
    }

	@Override
	protected void onStart() {
		super.onStart();		
		mapView.resumeTileProcessing();
	}

	@Override
	protected void onStop() {
		mapView.pauseTileProcessing();
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		mapView.destroy();
		super.onDestroy();
	}    
}
