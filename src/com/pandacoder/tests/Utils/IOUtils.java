package com.pandacoder.tests.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;

public class IOUtils {
	
	private final static String LOG_TAG = IOUtils.class.getName();
	
	public static void closeSilent(OutputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "Fail to close stream\n" + e.getMessage());
			}
		}
	}
	
	public static void closeSilent(InputStream stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				Log.e(LOG_TAG, "Fail to close stream\n" + e.getMessage());
			}
		}
	}
}
