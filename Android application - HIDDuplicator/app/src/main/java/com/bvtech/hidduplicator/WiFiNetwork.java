package com.bvtech.hidduplicator;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * A library for network communication (WiFi)
 */
public class WiFiNetwork {

	/**
	 * OnSearchListener is an interface that uses to retrieve list of WiFi devices
	 */
	public interface OnSearchListener {
		void searchDone(List<ScanResult> arrayList);
	}

	/**
	 * Search for WiFi nodes
	 * @param context is an interface to global information about an application environment
	 * @param listener is an interface that returns data of search
	 */
	public static void searchWiFiNodes(Activity context, OnSearchListener listener) {
		SearchWiFiNodes task = new SearchWiFiNodes(context, listener);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		else
			task.execute();
	}

	/**
	 * SearchWiFiNodes is an extended class from AsyncTask to run codes of search in the background.
	 */
	private static class SearchWiFiNodes extends AsyncTask<Void, Void, Void> {

		private Activity context;
		private OnSearchListener listener;

		private SearchWiFiNodes(Activity context, OnSearchListener listener) {
			this.context = context;
			this.listener = listener;
		}

		@Override
		protected Void doInBackground(Void... voids) {
			try {
				if (!isWifiEnable(context)) {
					setWifiEnable(context, true);
				}
				listener.searchDone(getWifiList(context));
			} catch (Exception e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	/**
	 * Check if WiFi is enable or not
	 */
	public static boolean isWifiEnable(Context context) {
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		return wifiManager.isWifiEnabled();
	}

	/**
	 * Enable or disable WiFi
	 */
	public static void setWifiEnable(Context context, boolean b) {
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		if (!wifiManager.isWifiEnabled()) {
			wifiManager.setWifiEnabled(b);
		}
	}

	/**
	 * Stop Wifi connection if setWifiEnable(false) does not work
	 * @param context an interface to global information about an application environment
	 */
	public static void stopWifiConnection(Context context) {
		try {
			WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

			Method[] wmMethods = wifiManager.getClass().getDeclaredMethods();

			for (Method method : wmMethods) {
				if (method.getName().equals("setWifiApEnabled")) {
					try {
						method.invoke(wifiManager, null, false);
					} catch (IllegalArgumentException e) {

						e.printStackTrace();
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					} catch (InvocationTargetException e) {

						e.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			Log.d("Log", "error Stopping Hotspot");
		}
	}

	/**
	 * Disconnect device from WiFi access point and remove it from the connected list.
	 */
	public static void disconnectAndRemoveSSID(Context context, String SSID) {
		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			return;
		}
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		List<WifiConfiguration> netlist = wifiManager.getConfiguredNetworks();
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		boolean ctrl = false;

		if (WifiInfo.getDetailedStateOf(wifiInfo.getSupplicantState()) == NetworkInfo.DetailedState.CONNECTED) {
			String ssid = wifiInfo.getSSID();
			if (ssid.equals(SSID)) {
				ctrl = true;
				int networkId = wifiManager.getConnectionInfo().getNetworkId();
				wifiManager.disconnect();
				// Log.i("Tag", "Disconnected");
				wifiManager.removeNetwork(networkId);
				wifiManager.saveConfiguration();
			}
		}

		if (!ctrl) {
			for (WifiConfiguration net : netlist) {
				if (net.SSID.contains(SSID)) {
					int networkId = wifiManager.getConnectionInfo()
							.getNetworkId();
					wifiManager.removeNetwork(networkId);
					wifiManager.saveConfiguration();
				}
			}
		}

	}

	/**
	 * Get the the list of WiFi devices by name
	 *
	 * @param context an interface to global information about an application environment
	 * @return a list of String that contains name of access points
	 */
	public static ArrayList<String> getWifiListName(Context context) {
		ArrayList<String> scanResult = new ArrayList<>();

		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		if (wifiManager.startScan()) {
			List<ScanResult> networkList = wifiManager.getScanResults();
			if (networkList != null) {
				for (ScanResult network : networkList) {
					if (network.SSID != null && !network.SSID.isEmpty()) {
						scanResult.add(network.SSID);
					}
				}
			}
			return scanResult;
		}
		return null;
	}

	/**
	 * Get the the list of WiFi devices with all information
	 *
	 * @param context an interface to global information about an application environment
	 * @return a list of ScanResult object that contains all information of access points
	 */
	public static List<ScanResult> getWifiList(Context context) {
		ArrayList<String> scanResult = new ArrayList<>();

		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		if (wifiManager.startScan()) {
			List<ScanResult> networkList = wifiManager.getScanResults();
			if (networkList != null) {
				return networkList;
			}
		}
		return null;
	}

	/**
	 * Get the IP of device that connected to the access point
	 * @return a String that contain the IP of device
	 */
	public static String getLocalIpAddress() {
		String ip = "";
		try {
			Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
					.getNetworkInterfaces();
			while (enumNetworkInterfaces.hasMoreElements()) {
				NetworkInterface networkInterface = enumNetworkInterfaces
						.nextElement();
				Enumeration<InetAddress> enumInetAddress = networkInterface
						.getInetAddresses();
				while (enumInetAddress.hasMoreElements()) {
					InetAddress inetAddress = enumInetAddress
							.nextElement();

					if (inetAddress.isSiteLocalAddress()) {
						/*ip += "Server running at : "
								+ inetAddress.getHostAddress();*/
						ip += inetAddress.getHostAddress();
					}
				}
			}

		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ip += "Something Wrong! " + e.toString() + "\n";
			return null;
		}
		return ip;
	}

	/**
	 * Check if WiFi is connected to the network
	 */
	public static boolean isWifiConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		return mWifi.isConnected();
	}

	/**
	 * Check if Cellular data is connected to the network
	 */
	public static boolean isCellularNetworkConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
		return mWifi.isConnected();
	}

	/**
	 * Check if VPN is connected to the network
	 */
	public static boolean isVpnConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_VPN);
		return mWifi.isConnected();
	}

	/**
	 * Disconnect WiFi from any connection
	 */
	public static void disconnectWifi(Context context) {
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		int networkId = wifiManager.getConnectionInfo().getNetworkId();
		wifiManager.disconnect();
		// Log.i("Tag", "Disconnected");
		wifiManager.removeNetwork(networkId);
		wifiManager.saveConfiguration();
	}

	/**
	 * connect to a specific SSID, For API 29 and lower only
	 */
	public static boolean connectToSSID(Context context, final String ssid, final String pass) {
		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			return false;
		}
		WifiConfiguration conf = new WifiConfiguration();
		conf.SSID = "\"" + ssid + "\"";
		// Please note the quotes.
		// String should
		// contain ssid in quotes

		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		List<ScanResult> networkList = wifiManager.getScanResults();
		if (networkList != null) {
			for (ScanResult network : networkList) {
				// check if current connected SSID
				if (network.SSID.equals(ssid)) {
					// get capabilities of current connection
					String Capabilities = network.capabilities;
					// Log.d (TAG, network.SSID + " capabilities : " +
					// Capabilities);

					if (!pass.isEmpty()) {
						if (Capabilities.contains("WPA2")) {
							conf.preSharedKey = "\"" + pass + "\"";
						} else if (Capabilities.contains("WPA")) {
							conf.preSharedKey = "\"" + pass + "\"";
						} else if (Capabilities.contains("WEP")) {
							conf.wepKeys[0] = "\"" + pass + "\"";
							conf.wepTxKeyIndex = 0;
							conf.allowedKeyManagement
									.set(WifiConfiguration.KeyMgmt.NONE);
							conf.allowedGroupCiphers
									.set(WifiConfiguration.GroupCipher.WEP40);
						}
					} else {
						conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
					}

					wifiManager.addNetwork(conf);
					List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
					for (WifiConfiguration i : list) {
						if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
							wifiManager.disconnect();
							wifiManager.enableNetwork(i.networkId, true);
							wifiManager.reconnect();
							/*if(isWifiConnected(context)){
								setText("Connected to SSID");
								return true;
							}else{
								setText("Unable to connect");
								return false;
							}*/
							return true;
						}
					}
					break;
				}
			}
		}
		return false;
	}

	/**
	 * Check the connectivity of device to a specific SSID
	 */
	public static boolean checkDeviceConnectedToNetwork(Context context, final String ssid, final String pass) {
		ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

		boolean b = mWifi.isConnected();
		String s = getConnectedWiFiSSID(context).replaceAll("\"", "");
		if (mWifi.isConnected() && ssid.equalsIgnoreCase(s)) {
			Log.i("Tag", "Wifi connected");
			return true;
		} else {
			if (connectToSSID(context, ssid, pass)) {
				while (!mWifi.isConnected()) {
					// wait!!!
					// Log.i("Tag", "Still not connected");
					connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
					mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
				}

				if (mWifi.isConnected()) {
					Log.i("Tag", "Wifi connected");
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Get the name of current access point that system is connected
	 */
	public static String getConnectedWiFiSSID(Context context) {
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiInfo info = wifiManager.getConnectionInfo();
		String ssid = info.getSSID();
		return ssid;
	}

	/**
	 * Returns MAC address of the given interface name.
	 * @return mac address or fake MAC address
	 */
	public static String getWifiMACAddress() {
		return getAddressMacByInterface();
	}

	/**
	 * Get the device MAC id of device by reading system files
	 *
	 * @param context an interface to global information about an application environment
	 * @return a String that contains MAC id of device
	 */
	public static String getDeviceMacId(Context context) {
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		return requestAddressMAC(context, wifiManager);
	}
	///////////////////////////////////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Request for MAC address with two methods, getAddressMacByInterface and getAddressMacByFile
	 *
	 * @param context an interface to global information about an application environment
	 * @param wifiMan This class provides the primary API for managing all aspects of Wi-Fi connectivity
	 * @return MAC id value as a String
	 */
	public static String requestAddressMAC(Context context, WifiManager wifiMan) {
		WifiInfo wifiInf = wifiMan.getConnectionInfo();

		if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			return "02:00:00:00:00:00";
		}
		if (wifiInf.getMacAddress().equals("02:00:00:00:00:00")) {
			String ret = null;

			try {
				ret = getAddressMacByInterface();
				if (ret != null) {
					return ret;
				}

				ret = getAddressMacByFile(wifiMan);
				return ret;
			} catch (IOException var4) {
				Log.e("MobileAccess", "Error looking Adresse MAC");
			} catch (Exception var5) {
				Log.e("MobileAcces", "Error looking Adresse MAC ");
			}

			return "02:00:00:00:00:00";
		} else {
			if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
				return "02:00:00:00:00:00";
			}
			return wifiInf.getMacAddress();
		}
	}

	/**
	 * Get the MAC id by Interface method
	 * @return MAC id
	 */
	private static String getAddressMacByInterface() {
		try {
			List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
			for (NetworkInterface intf : interfaces) {
				if (!intf.getName().equalsIgnoreCase("wlan0")) continue;
				byte[] mac = intf.getHardwareAddress();
				if (mac == null) return "";
				StringBuilder buf = new StringBuilder();
				for (byte aMac : mac) buf.append(String.format("%02X:", aMac));
				if (buf.length() > 0) buf.deleteCharAt(buf.length() - 1);
				return buf.toString();
			}
		} catch (Exception ignored) {
		} // for now eat exceptions
		// Couldn't get a MAC address, just imagine one
		return "01:02:03:04:05:06";
	}

	/**
	 * Get the MAC id by File method, read system file
	 *
	 * @param wifiMan This class provides the primary API for managing all aspects of Wi-Fi connectivity
	 * @return MAC id
	 */
	private static String getAddressMacByFile(WifiManager wifiMan) throws Exception {
		int wifiState = wifiMan.getWifiState();
		wifiMan.setWifiEnabled(true);
		File fl = new File("/sys/class/net/wlan0/address");
		FileInputStream fin = new FileInputStream(fl);
		StringBuilder builder = new StringBuilder();

		int ch;
		while((ch = fin.read()) != -1) {
			builder.append((char)ch);
		}

		String ret = builder.toString();
		fin.close();
		boolean enabled = 3 == wifiState;
		wifiMan.setWifiEnabled(enabled);
		return ret;
	}

	/////////////////////////////////////////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * OnNetworkChangeListener is an interface that uses to handle any change events of the network
	 */
	public interface OnNetworkChangeListener{
		void onChange(Intent intent);
	}

	/**
	 * NetworkChangeReceiver is an extended class from BroadcastReceiver that can listen to any
	 * background data and filters network related data and pass throw the OnNetworkChangeListener interface
	 */
	public static class NetworkChangeReceiver extends BroadcastReceiver{
		OnNetworkChangeListener onNetworkChangeListener;
		public NetworkChangeReceiver(OnNetworkChangeListener listener){
			onNetworkChangeListener = listener;
		}
		@Override
		public void onReceive(Context context, Intent intent) {
			if(onNetworkChangeListener != null){
				onNetworkChangeListener.onChange(intent);
			}
		}
	}

	/**
	 *
	 * @return an IntentFilter which is set for WiFi connectivity
	 */
	public static IntentFilter getWiFiIntent(){
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
		intentFilter.addAction("android.net.wifi.STATE_CHANGE");
		intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
		intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
		return intentFilter;
	}
}
