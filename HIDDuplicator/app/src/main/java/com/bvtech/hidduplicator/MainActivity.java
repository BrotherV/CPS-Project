package com.bvtech.hidduplicator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;

import com.bvtech.toolslibrary.utility.ViewUtility;
import com.bvtech.toolslibrary.widget.ExtendToast;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.StrictMode;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.bvtech.hidduplicator.databinding.ActivityMainBinding;
import com.robinhood.ticker.TickerUtils;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;

import org.joda.time.DateTime;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    /** Tag for debug messages of service*/
    private static final String DBG_TAG = "ActivityMesh";
    private final int CIRCULAR_REVEAL_ANIMATION_DURATION = 500;

    private WiFiNetwork.NetworkChangeReceiver networkChangeReceiver;
    /** WiFi manager to connect to Mesh network */
    private WifiManager wifiMgr;

    private ConnectivityManager.NetworkCallback mNetworkCallback;

    // Variables
    /** Mesh name == Mesh SSID */
    private String meshName;
    /** Mesh password == Mesh network password */
    private String meshPw;
    /** Mesh port == TCP port number */
    private int meshPort;

    /** WiFi AP to which device was connected before connecting to mesh */
    private String oldAPName = "";
    /** Mesh network entry IP */
    private String meshIP;
    /** Filter for incoming messages */
    private long filterId = 0;
    /** Flag if log file should be written */
    private boolean doLogging = true;
    /** Path to storage folder */
    private String sdcardPath;
    /** Flag if we try to connect to Mesh */
    private boolean tryToConnect = false;
    /** Flag if connection to Mesh was started */
    private boolean isConnected = false;
    /** Flag when user stops connection */
    private boolean userDisConRequest = false;

    Handler handler = new Handler();
    TimerTask timerTask;
    boolean shouldConnectToAp, timeForNodeReq, isBroadCastRegistered, status;
    boolean isStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.tickerTime.setCharacterLists(TickerUtils.provideNumberList());
        binding.tickerStatus.setCharacterLists(TickerUtils.provideNumberList());
        binding.tickerHome.setCharacterLists(TickerUtils.provideNumberList());

        //Set default value for Text Views
        binding.tickerHome.setText(getResources().getString(R.string.connect));
        binding.tickerStatus.setText(getString(R.string.status) + " Not connected");

        //Get Mesh port
        meshPort = Constants.Mesh_Port;
        //Mesh Name
        meshName = Constants.Mesh_Name;
        //Mesh Password
        meshPw = Constants.Mesh_Password;

        /**
         * Attaching click event on "Connect" button
         * Connect button functions, by clicking on connect device will search for mesh network
         */

        ViewUtility.shrinkExpandAnimation(binding.btnConnect, 0.95f, v -> {
            //Check if the cellular network is enabled, if yes it shows a dialog to open setting window to let user turn off cellular network
            if (WiFiNetwork.isCellularNetworkConnected(getApplicationContext())) {
                DialogNotify dialogNotify = new DialogNotify(MainActivity.this, "Message", "To enable WiFi mesh network you must disable the Cellular network", new DialogNotify.OnConfirmListener() {
                    @Override
                    public void onClick() {
                        //ExtendToast.toastNotify(ActivityMain2.this, "Please turn off cellular data").show();
                        Intent intent = new Intent(Settings.ACTION_DATA_USAGE_SETTINGS);
                        startActivity(intent);
                    }
                });
                dialogNotify.setCancelable(false);
                dialogNotify.show();
                return;
            }

            //First it checks if the permission is set
            if (!checkPermissionAndConnectivity()) {
                //handleConnection();
                //it check if wifi is on
                if (WiFiNetwork.isWifiEnable(getApplicationContext())) {
                    if (!isConnected) {
                        //it check if Mesh network is not connected, it tries to connect to the mesh network
                        connectToNetwork();
                        binding.btnConnect.setText(getResources().getString(R.string.mesh_connecting));
                    } else {
                        if (MeshCommunicator.isConnected()) {
                            MeshCommunicator.Disconnect();
                        }
                        stopConnection();
                    }
                } else {
                    WiFiNetwork.setWifiEnable(getApplicationContext(), true);
                    shouldConnectToAp = true;
                }

                binding.btnConnect.setEnabled(false);
            }
        });

        /**
         * Attaching click event to the "SendData" button
         * Send data to the ESP chip by clicking "Send Data" button
         */
        ViewUtility.shrinkExpandAnimation(binding.btnStart, 0.95f, listener -> {
            //Check if devices has been connected to mesh network or not
            if (MeshCommunicator.isConnected()) {
                //Creating JSON object
                //adding necessary items to json object
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put(isStarted ? "stop" : "start", true);
                    if(!isStarted)
                        binding.btnCopy.setVisibility(View.INVISIBLE);
                    String json = jsonObject.toString();    //converting json to string
                    MeshHandler.sendNodeMessage(0, json);   //sending data to all mesh network
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        /**
         * Attaching click event to the reset button
         * Resetting devices by pressing the Reset button
         */
        ViewUtility.shrinkExpandAnimation(binding.btnCopy, 0.95f, listener -> {
            if (MeshCommunicator.isConnected()) {
                //Creating JSON object
                //adding necessary items to json object
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("copy", true);  //adding reset value to json object
                    String json = jsonObject.toString();    //converting json to string
                    MeshHandler.sendNodeMessage(0, json);   //sending data to all nodes
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        /**
         * This timer allows the system checks the status of input and output in every second
         */
        Timer timer = new Timer(false);
        timer.schedule(new TimerTask() {

            @Override
            public void run() {
                //Create a date and time value in a String object to show the current time
                DateTime dateTime = new DateTime();
                String h = "" + (dateTime.getHourOfDay() < 10 ? "0" + dateTime.getHourOfDay() : dateTime.getHourOfDay());
                String m = "" + (dateTime.getMinuteOfHour() < 10 ? "0" + dateTime.getMinuteOfHour() : dateTime.getMinuteOfHour());
                String s = "" + (dateTime.getSecondOfMinute() < 10 ? "0" + dateTime.getSecondOfMinute() : dateTime.getSecondOfMinute());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Print date and time on tickerTime view
                        binding.tickerTime.setText(getResources().getString(R.string.time) + " "
                                + h + ":" + m + ":" + s);
                    }
                });

            }
        }, 0, 1000);

        // Start handler to send node sync request and time sync request every 3 seconds
        // Keeps socket connection more stable
        Timer pingTimer = new Timer(false);
        pingTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                if (MeshCommunicator.isConnected()) {
                    if (timeForNodeReq) {
                        MeshHandler.sendNodeSyncRequest();
                        timeForNodeReq = false;
                    } else {
                        MeshHandler.sendTimeSyncRequest();
                        timeForNodeReq = true;
                    }
                }
            }
        }, 0, 3000);
    }

    /**
     * Callback for the result from requesting permissions. This method is invoked for every call on requestPermissions.
     *
     * @param requestCode is the passed code into the requestPermissions, we use it to know which permission is requested
     * @param permissions contains a list of permissions that passed into the requestPermissions method
     * @param grantResults contains a list of grant status for each request
     *
     * In this method, we check the request of "READ_EXTERNAL_STORAGE", if it is granted, app can read file from device
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
    /**
     * onResume() When the activity enters the Resumed state, it comes to the foreground,
     * and then the system invokes the onResume() callback. This is the state in which
     * the app interacts with the user. The app stays in this state until something happens
     * to take focus away from the app.
     *
     * IntentFilters of "MeshCommunicator" broadcasting set and register to receive data from the Broadcast service
     */
    @Override
    protected void onResume() {
        super.onResume();

        /**
         * Check the permissions for the application
         */
        checkPermissionAndConnectivity();
        //setMeshBroadCaster();
    }

    /**
     *
     */
    private void setMeshBroadCaster(){
        if(!isBroadCastRegistered){
            isBroadCastRegistered = true;

            // Register Mesh events
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(MeshCommunicator.MESH_DATA_RECVD);
            intentFilter.addAction(MeshCommunicator.MESH_SOCKET_ERR);
            intentFilter.addAction(MeshCommunicator.MESH_CONNECTED);
            intentFilter.addAction(MeshCommunicator.MESH_NODES);
            intentFilter.addAction(MeshCommunicator.MESH_OTA);
            intentFilter.addAction(MeshCommunicator.MESH_OTA_REQ);
            // Register receiver
            registerReceiver(meshBroadcastReceiver, intentFilter);
        }
    }

    /**
     * The final call you receive before your activity is destroyed. This can happen either
     * because the activity is finishing (someone called finish() on it), or because the system
     * is temporarily destroying this instance of the activity to save space.
     *
     * This method destroy all connections, and unregister the broadcast receiver
     */
    @Override
    protected void onDestroy() {
        if (MeshCommunicator.isConnected()) {
            MeshCommunicator.Disconnect();
        }
        //stopLogging();
        stopConnection();
        // unregister the broadcast receiver
        super.onDestroy();
    }

    /**
     * Back navigation is how users move backward through the history of screens they previously visited.
     * Depending on the user’s Android device, this button might be a physical button or a software button.
     * Android maintains a back stack of destinations as the user navigates throughout your application.
     *
     * By clicking on back pressed, if the debug dialog is visible, is closes first, otherwise, it closes
     * all connections and closes the application
     */
    @Override
    public void onBackPressed() {
        if (MeshCommunicator.isConnected()) {
            MeshCommunicator.Disconnect();
        }
        //stopLogging();
        stopConnection();
        super.onBackPressed();
        //this.finish();
    }

    /**
     * check for necessary permissions and enable connectivity access
     */
    private boolean checkPermissionAndConnectivity() {
        boolean ret = false;
        // Ask for permissions if necessary
        ArrayList<String> arrPerm = new ArrayList<>();
        // On newer Android versions it is required to get the permission of the user to
        // get the location of the device. This is necessary to do a WiFi scan for APs.
        // I am not sure at all what that has to be with
        // the permission to use Bluetooth or BLE, but you need to get it anyway
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            arrPerm.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            arrPerm.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            arrPerm.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
//        }
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//            arrPerm.add(Manifest.permission.READ_EXTERNAL_STORAGE);
//        }

        if (!arrPerm.isEmpty()) {
            ret = true;
            String[] permissions = new String[arrPerm.size()];
            permissions = arrPerm.toArray(permissions);
            ActivityCompat.requestPermissions(this, permissions, Constants.PERMISSION_REQUEST);
        }

        // Enable access to connectivity
        // ThreadPolicy to get permission to access connectivity
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // Get the wifi manager
        wifiMgr = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        return ret;
    }


    /**
     * This method calls by clicking on the "Connect" button
     * This method adds mesh network AP to the devices list of Wifi Access points
     * Enable mesh network AP to initiate connection to the mesh AP
     */
    public void connectToNetwork() {
        setMeshBroadCaster();
        binding.imgConnection.setImageDrawable(getResources().getDrawable(R.drawable.avd_connect));
        binding.circularProgressBar.setIndeterminateMode(true);
        tryToConnect = true;
        userDisConRequest = false;

        binding.tickerHome.setText(getResources().getString(R.string.mesh_connecting));
        binding.tickerStatus.setText(getResources().getString(R.string.status) + " " + getResources().getString(R.string.mesh_connecting));

        // Get current active WiFi AP
        oldAPName = "";

        /**
         * NetworkRequest object is an object to handle network connections.
         * In this particular design, "TransportType" must be set to "WIFI"
         * and "NET_CAPABILITY_TRUSTED" must be set as a Capability
         */
        NetworkRequest.Builder networkRequestBuilder = new NetworkRequest.Builder();
        networkRequestBuilder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        networkRequestBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED);

        //If the device has android Q (API 29) or above, "WifiNetworkSpecifier" must be used to connect to "WiFi".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WifiNetworkSpecifier wifiNetworkSpecifier = null;
            wifiNetworkSpecifier = new WifiNetworkSpecifier.Builder()
                    .setSsid(meshName)
                    .setWpa2Passphrase(meshPw)
                    .build();

            //set "wifiNetworkSpecifier" to the "networkRequestBuilder" only for android Q (API 29) or above
            networkRequestBuilder.setNetworkSpecifier(wifiNetworkSpecifier);
        }

        ConnectivityManager mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        //Attaching network events to a "NetworkCallback"
        mNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                super.onAvailable(network);
                //phone is connected to wifi network
                if (tryToConnect) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        mConnectivityManager.bindProcessToNetwork(network);
                    } else {
                        mConnectivityManager.setProcessDefaultNetwork(network);
                    }
                    /* Access to connectivity manager */
                    WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
                    String ssid = wifiInfo.getSSID();

                    boolean isConnectToMesh = false;
                    if (ssid != null && ssid.equalsIgnoreCase("\"" + meshName + "\"")) {
                        connectMeshNetwork();
                    } else {
                        ExtendToast.toastError(MainActivity.this, "Unable to connect to mesh network").show();
                    }
                } else {
                    if (shouldConnectToAp) {
                        shouldConnectToAp = false;
                        connectToNetwork();
                    }
                }
            }

            @Override
            public void onLosing(@NonNull Network network, int maxMsToLive) {
                super.onLosing(network, maxMsToLive);
                //phone is about to lose connection to network
            }

            @Override
            public void onLost(@NonNull Network network) {
                super.onLost(network);
                //phone lost connection to network
                stopConnection();
            }

            @Override
            public void onUnavailable() {
                super.onUnavailable();
                //user cancelled wifi connection
                stopConnection();
            }
        };

        NetworkRequest networkRequest = networkRequestBuilder.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //If the device has Android Q (API 29) or above, requestNetwork must be used in ConnectivityManager
            mConnectivityManager.requestNetwork(networkRequest, mNetworkCallback);
        }else {
            //For devices with Android lower than API 29, registerNetworkCallback must be used in ConnectivityManager
            mConnectivityManager.registerNetworkCallback(networkRequest, mNetworkCallback);

            /**
             * connection Process for devices with Android lower than API 29
             */
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiConfiguration config = new WifiConfiguration();
            config.SSID = String.format("\"%s\"", meshName);

            int netId = -1;

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            //Checking the list of WiFi devices, If the netId is -1, device must be set to the list manually.
            List<WifiConfiguration> apList = wifiManager.getConfiguredNetworks();
            for (WifiConfiguration i : apList) {

                if (i.SSID != null && i.SSID.equals("\"" + meshName + "\"")) {
                    netId = i.networkId;
                }
            }

            // Add network in Saves network list if it is not available in list
            if (netId == -1) {

                if (TextUtils.isEmpty(meshPw)) {
                    Log.d("WiFi", "====== Connect to open network");
                    config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                } else {
                    Log.d("WiFi", "====== Connect to secure network");
                    config.preSharedKey = String.format("\"%s\"", meshPw);
                }

                netId = wifiManager.addNetwork(config);
            }

            Log.d("WiFi", "Connect to Network : " + netId);
            wifiManager.enableNetwork(netId, true);

            /*config.SSID = "\"" + meshName + "\"";
            config.preSharedKey = "\"" + meshPw + "\"";
            int newId = wifiMgr.addNetwork(config);
            if (BuildConfig.DEBUG) Log.i(DBG_TAG, "Result of addNetwork: " + newId);
            wifiManager.disconnect();
            wifiManager.enableNetwork(newId, true);
            wifiManager.reconnect();*/
        }
    }

    /**
     * Stop connection to the mesh network
     * Disable the mesh AP in the device Wifi AP list so that
     * the device reconnects to its default AP
     */
    private void stopConnection() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        //Disconnecting Mesh network if it is connected
        if (MeshCommunicator.isConnected()) {
            MeshCommunicator.Disconnect();
        }

        /**
         * Unregistering "NetworkCallback" from the ConnectivityManager
         */
        ConnectivityManager mConnectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mConnectivityManager.unregisterNetworkCallback(mNetworkCallback);
        /**
         * disconnect and remove the device from the list of WiFi devices
         */
        WiFiNetwork.disconnectAndRemoveSSID(getApplicationContext(), meshName);

        /**
         * Resetting values and widgets
         */
        status = false;
        isConnected = false;
        tryToConnect = false;
        isBroadCastRegistered = false;
        userDisConRequest = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Print date and time on tickerTime view
                binding.imgConnection.setImageDrawable(getResources().getDrawable(R.drawable.avd_disconnect));
                binding.tickerStatus.setText(getString(R.string.status) + " Not connected");
                binding.tickerHome.setText(getResources().getString(R.string.mesh_disconnected));
                binding.circularProgressBar.setProgressMax(100);
                binding.circularProgressBar.setProgressWithAnimation(0f, (long) 1000);
                //circularProgressBar.setIndeterminateMode(false);

                binding.btnStart.setVisibility(View.INVISIBLE);
                binding.btnCopy.setVisibility(View.INVISIBLE);
            }
        });

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                binding.btnConnect.setBackground(getResources().getDrawable(R.drawable.button_oval_green));
                binding.btnConnect.setText("Connect");
                binding.btnConnect.setEnabled(true);
            }
        }, 1000);

        unregisterReceiver(meshBroadcastReceiver);
    }

    /**
     * This method calls when the CellPhone connects to the Mesh access point.
     * After the, it creates an Id for the device and opens a socket and connect
     * to the Mesh server.
     */
    private void connectMeshNetwork(){
        // Create the mesh AP node ID from the AP MAC address
        WifiInfo wifiInf = wifiMgr.getConnectionInfo();
        String bssid = WiFiNetwork.getDeviceMacId(getApplicationContext());
        if(bssid.isEmpty()){
            bssid = "A1:B2:C3:D4:E5:F6";
        }
        /*if (bssid.equalsIgnoreCase("02:00:00:00:00:00")) {
            bssid = WiFiNetwork.getDeviceMacId(getApplicationContext());
        }*/

        MeshHandler.setApNodeId(MeshHandler.createMeshID(bssid));

        DhcpInfo dhcpInfo = wifiMgr.getDhcpInfo();
        // Get the mesh AP IP
        int meshIPasNumber = dhcpInfo.gateway;
        meshIP = ((meshIPasNumber & 0xFF) + "." +
                ((meshIPasNumber >>>= 8) & 0xFF) + "." +
                ((meshIPasNumber >>>= 8) & 0xFF) + "." +
                (meshIPasNumber >>> 8 & 0xFF));

        // Create our node ID
        MeshHandler.setMyNodeId(MeshHandler.createMeshID(WiFiNetwork.getWifiMACAddress()));
//        WifiManager wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);
//        MeshHandler.setMyNodeId(MeshHandler.createMeshID(WiFiNetwork.requestAddressMAC(wifiManager)));

        /**
         * Changing values and Widgets when it connects to the network
         */
        runOnUiThread(() -> {
            tryToConnect = false;

            String connMsg = MeshHandler.getMyNodeId() + " ID set to network";

            // Set flag that we are connected
            isConnected = true;
            binding.btnConnect.setEnabled(true);
            binding.btnConnect.setBackground(getResources().getDrawable(R.drawable.button_oval_red));
            binding.btnConnect.setText("Disconnect");

            binding.circularProgressBar.setIndeterminateMode(false);
            binding.circularProgressBar.setProgressWithAnimation(100, (long) 1000);
            binding.btnStart.setVisibility(View.VISIBLE);
            //btnReset.setVisibility(View.VISIBLE);
            // Connected to the Mesh network, start network task now
            MeshCommunicator.Connect(meshIP, meshPort, getApplicationContext());

        });
    }

    /**
     * Local broadcast receiver
     * Registered for
     * - WiFi connection change events
     * - Mesh network data events
     * - Mesh network error events
     */
    private final BroadcastReceiver meshBroadcastReceiver = new BroadcastReceiver() {
        @SuppressLint("DefaultLocale")
        @Override
        public void onReceive(final Context context, Intent intent) {
            // Connection change
            String intentAction = intent.getAction();
            Log.d(DBG_TAG, "Received broadcast: " + intentAction);

            String dataSet;
            DateTime now = new DateTime();
            dataSet = String.format ("[%02d:%02d:%02d:%03d] ",
                    now.getHourOfDay(),
                    now.getMinuteOfHour(),
                    now.getSecondOfMinute(),
                    now.getMillisOfSecond());

            // Mesh events
            // If is true when intent contains "DATA" from the broadcast service
            // Broadcast listens to the all incoming data and this data can be
            // filtered checking intent.getAction() from the broadcaster
            if (MeshCommunicator.MESH_DATA_RECVD.equals(intentAction)) {
                getStatus();
                String rcvdMsg = intent.getStringExtra("msg");
                String oldText;
                try {
                    JSONObject rcvdJSON = new JSONObject(rcvdMsg);
                    int msgType = rcvdJSON.getInt("type");
                    long fromNode = rcvdJSON.getLong("from");
                    switch (msgType) {
                        case 3: // TIME_DELAY
                            binding.tickerHome.setText(getString(R.string.mesh_event_time_delay));
                            dataSet += "Received TIME_DELAY\n";
                            break;
                        case 4: // TIME_SYNC
                            binding.tickerHome.setText(getString(R.string.mesh_event_time_sync));
                            dataSet += "Received TIME_SYNC\n";
                            break;
                        case 5: // NODE_SYNC_REQUEST
                        case 6: // NODE_SYNC_REPY
                            if (msgType != 5) {
                                binding.tickerHome.setText(getString(R.string.mesh_event_node_reply));
                                dataSet += "Received NODE_SYNC_REPLY\n";
                            } else {
                                binding.tickerHome.setText(getString(R.string.mesh_event_node_req));
                                dataSet += "Received NODE_SYNC_REQUEST\n";
                            }
                            // Generate known nodes list
                            final String nodesListString = rcvdMsg;
                            final Handler handler = new Handler();
                            handler.post(() -> MeshHandler.generateNodeList(nodesListString));
                            break;
                        case 7: // CONTROL ==> deprecated
                            dataSet += "Received CONTROL\n";
                            break;
                        case 8: // BROADCAST
                            dataSet += "Broadcast:\n" + rcvdJSON.getString("msg") + "\n";
                            if (filterId != 0) {
                                if (fromNode != filterId) {
                                    return;
                                }
                            }
                            break;
                        case 9: // SINGLE
                            dataSet += "Single Msg:\n" + rcvdJSON.getString("msg") + "\n";
                            //Check if devices have received data
                            checkIncomingData(rcvdJSON.getString("msg"));
                            // Check if the message is a OTA req message
                            JSONObject rcvdData = new JSONObject(rcvdJSON.getString("msg"));
                            String dataType = rcvdData.getString("plugin");
                            if ((dataType != null) && dataType.equalsIgnoreCase("ota")) {
                                dataType = rcvdData.getString("type");
                                if (dataType != null) {
                                    if (dataType.equalsIgnoreCase("version")) {
                                        // We received a OTA advertisment!
                                        binding.tickerHome.setText(getString(R.string.mesh_event_ota_adv));
                                        return;
                                    } else if (dataType.equalsIgnoreCase("request")) {
                                        // We received a OTA block request
                                        MeshHandler.sendOtaBlock(fromNode, rcvdData.getLong("partNo"));
                                        binding.tickerHome.setText(getString(R.string.mesh_event_ota_req));
                                    }
                                }
                            }
                            if (filterId != 0) {
                                if (fromNode != filterId) {
                                    return;
                                }
                            }
                            break;
                    }
                } catch (JSONException e) {
                    Log.d(DBG_TAG, "Received message is not a JSON Object!");
                    dataSet += "ERROR INVALID DATA:\n" + intent.getStringExtra("msg") + "\n";
                }
                if (MeshHandler.getOutBuffer() != null) {
                    try {
                        MeshHandler.getOutBuffer().append(dataSet);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
            // If the socket has an error in connection
            else if (MeshCommunicator.MESH_SOCKET_ERR.equals(intentAction)) {
                if(!WiFiNetwork.isWifiConnected(getApplicationContext())){
                    stopConnection();
                }else{
                    if (MeshHandler.getNodesList() != null) {
                        MeshHandler.getNodesList().clear();
                    }
                    if (!userDisConRequest) {
                        MeshCommunicator.Connect(meshIP, meshPort, getApplicationContext());
                        //tickerHome.setText(intent.getStringExtra("msg"));
                        binding.tickerHome.setText(getString(R.string.mesh_socket_error));
                    }
                }
            }
            // If the intentAction equals to "CON"
            // Means that the connection to the Mesh Network is established
            else if (MeshCommunicator.MESH_CONNECTED.equals(intentAction)) {
                userDisConRequest = false;
            } else if (MeshCommunicator.MESH_NODES.equals(intentAction)) {
                dataSet += intent.getStringExtra("msg") + "\n";
                if (MeshHandler.getOutBuffer() != null) {
                    try {
                        MeshHandler.getOutBuffer().append(dataSet);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    /**
     * This method checks the incoming data from the Mesh Network
     * This method calls when data receives from the nodes
     *
     * @param json contains data of the Mesh Network.
     * It retrieve nodeId and receive_status and fill list of nodes
     */
    private void checkIncomingData(String json){
        JSONObject rcvdData = null;
        try {
            rcvdData = new JSONObject(json);
            long nodeId = rcvdData.getLong("nodeId");
            try {
                boolean isCardRead = rcvdData.getBoolean("card_read");
                runOnUiThread(() ->{
                    if(isCardRead){

                        binding.btnCopy.setVisibility(View.VISIBLE);
                        binding.tickerStatus.setText("Card read successfully");
                        isStarted = false;
                        binding.btnStart.setText(getResources().getString(R.string.start));
                    }else{
                        binding.btnCopy.setVisibility(View.INVISIBLE);
                    }
                });

            }catch (Exception e){
                e.printStackTrace();
            }

            try {

                boolean isCardCopied = rcvdData.getBoolean("card_copied");
                isStarted = false;
                binding.btnStart.setText(getResources().getString(R.string.start));
                if(isCardCopied){
                    binding.tickerStatus.setText("Card copied successfully");
                }else{
                    binding.btnCopy.setVisibility(View.INVISIBLE);
                    binding.tickerStatus.setText("Duplicating failed");
                }
            }catch (Exception e){
                e.printStackTrace();
            }

            try {
                isStarted = rcvdData.getBoolean("started");
                if(isStarted){
                    binding.btnStart.setText(getResources().getString(R.string.stop));
                }else{
                    binding.btnStart.setText(getResources().getString(R.string.start));
                }
            }catch (Exception e){
                e.printStackTrace();
            }

        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getStatus(){
        if(!status){
            status = true;
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("status", true);
                String json = jsonObject.toString();    //converting json to string
                MeshHandler.sendNodeMessage(0, json);   //sending data to all mesh network
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}