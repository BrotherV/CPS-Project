package com.bvtech.hidduplicator;

import android.Manifest;
import android.os.Environment;

/**
 * This class contains necessary static (constant) values of the application
 */
public class Constants {
	public static final int PERMISSION_REQUEST = 5219;
	public static final String Mesh_Name = "Network_Al001";
	public static final String Mesh_Password = "@Cps#2022$";
	public static final int Mesh_Port = 8080;

	public static final String[] MANIFEST_ACCESS_FINE_LOCATION = {Manifest.permission.ACCESS_FINE_LOCATION};

	public static final int PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 5221;
	public static final int PERMISSION_REQUEST_READ_EXTERNAL_STORAGE = 5222;

	public static final int LOCATION_REFRESH_DISTANCE = 50;
	public static final int LOCATION_REFRESH_TIME = 5000;
	public static final int REQUEST_CHECK_SETTINGS = 200;

}
