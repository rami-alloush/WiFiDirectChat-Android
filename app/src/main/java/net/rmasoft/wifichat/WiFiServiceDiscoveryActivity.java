package net.rmasoft.wifichat;

import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.v4.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import net.rmasoft.wifichat.WiFiChatFragment.MessageTarget;
import net.rmasoft.wifichat.WiFiDirectServicesList.DeviceClickListener;
import net.rmasoft.wifichat.WiFiDirectServicesList.WiFiDevicesAdapter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class WiFiServiceDiscoveryActivity extends AppCompatActivity implements
        DeviceClickListener, Handler.Callback, MessageTarget,
        ConnectionInfoListener {

    public static final String TAG = "WiFiDirectChat";
    public SharedPreferences sharedpreferences;
    public static final String MyPREFERENCES = "MyPrefs" ;
    public static final String Name = "nameKey";

    // TXT RECORD properties
    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String SERVICE_INSTANCE = "WiFiDirectChat";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";
    public static final int MESSAGE_READ = 0x400 + 1;
    public static final int MY_HANDLE = 0x400 + 2;
    FragmentManager fragmentManager = getSupportFragmentManager();

    static final int SERVER_PORT = 4545;
    private final IntentFilter intentFilter = new IntentFilter();
    private WifiP2pManager manager;
    private Channel channel;
    private WifiP2pInfo p2pInfo;
    private Object newChatManager;
    private BroadcastReceiver receiver = null;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private Handler handler = new Handler(this);
    private WiFiChatFragment chatFragment;
    private WiFiDirectServicesList servicesList;
    private TextView statusTxtView;
    public Handler getHandler() {
        return handler;
    }
    public void setHandler(Handler handler) {
        this.handler = handler;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AppTheme);
        setContentView(R.layout.main);

//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        statusTxtView = findViewById(R.id.status_text);
        if (isWifiDirectSupported(this)) {
            appendStatus("WiFi Direct is Supported :)");
        } else {
            appendStatus("WiFi Direct is NOT supported :(");
        }

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        startRegistrationAndDiscovery();

        servicesList = new WiFiDirectServicesList();
        fragmentManager.beginTransaction().add(R.id.container_root, servicesList, "services").commit();
    }

    @Override
    protected void onRestart() {
        new Handler().post(new Runnable() {
            public void run() {
                Fragment frag = fragmentManager.findFragmentByTag("services");
                fragmentManager.beginTransaction().remove(frag).commit();
            }
        });
        super.onRestart();
    }

    @Override
    protected void onStop() {
        disconnect();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    /**
     * Registers a local service and then initiates a service discovery
     */
    private void startRegistrationAndDiscovery() {
        Map<String, String> record = new HashMap<String, String>();
        record.put(TXTRECORD_PROP_AVAILABLE, "visible");
        WifiP2pDnsSdServiceInfo service = WifiP2pDnsSdServiceInfo.newInstance(
                SERVICE_INSTANCE, SERVICE_REG_TYPE, record);
        manager.addLocalService(channel, service, new ActionListener() {
            @Override
            public void onSuccess() {
                appendStatus("Added Local Service");
            }
            @Override
            public void onFailure(int error) {
                appendStatus("Failed to add a service " + String.valueOf(error));
            }
        });
        discoverService();
    }

    private void discoverService() {
        /*
         * Register listeners for DNS-SD services. These are callbacks invoked
         * by the system when a service is actually discovered.
         */
        manager.setDnsSdResponseListeners(channel,
                new DnsSdServiceResponseListener() {
                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                            String registrationType, WifiP2pDevice srcDevice) {
                        // A service has been discovered. Is this our app?
                        if (instanceName.equalsIgnoreCase(SERVICE_INSTANCE)) {
                            // update the UI and add the item the discovered
                            // device.
                            WiFiDirectServicesList fragment = (WiFiDirectServicesList) fragmentManager.findFragmentByTag("services");
                            if (fragment != null) {
                                WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment
                                        .getListAdapter());
                                WiFiP2pService service = new WiFiP2pService();
                                service.device = srcDevice;
                                service.instanceName = instanceName;
                                service.serviceRegistrationType = registrationType;
                                adapter.add(service);
                                adapter.notifyDataSetChanged();
                                Log.d(TAG, "onBonjourServiceAvailable " + instanceName);
                            }
                        }
                    }
                }, new DnsSdTxtRecordListener() {
                    /**
                     * A new TXT record is available. Pick up the advertised
                     * buddy name.
                     */
                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> record,
                            WifiP2pDevice device) {
                        Log.d(TAG,
                                device.deviceName + " is "
                                        + record.get(TXTRECORD_PROP_AVAILABLE));
                    }
                });

        // After attaching listeners, create a service request and initiate
        // discovery.
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest,
                new ActionListener() {
                    @Override
                    public void onSuccess() {
                        appendStatus("Added service discovery request");
                    }
                    @Override
                    public void onFailure(int arg0) {
                        appendStatus("Failed adding service discovery request");
                    }
                });

        manager.discoverServices(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                appendStatus("Service discovery initiated");
            }
            @Override
            public void onFailure(int arg0) {
                appendStatus("Service discovery failed");
            }
        });
    }

    @Override
    public void connectP2p(WiFiP2pService service) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = service.device.deviceAddress;
//        config.wps.setup = WpsInfo.PBC;
        if (serviceRequest != null)
            manager.removeServiceRequest(channel, serviceRequest,
                    new ActionListener() {
                        @Override
                        public void onSuccess() {
                        }
                        @Override
                        public void onFailure(int arg0) {
                        }
                    });
        manager.connect(channel, config, new ActionListener() {
            @Override
            public void onSuccess() {
                appendStatus("Connecting to service");
            }

            @Override
            public void onFailure(int errorCode) {
                appendStatus("Failed connecting to service " + errorCode);
            }
        });
    }

    @Override
    public boolean handleMessage(Message msg) {
        Log.i(TAG, "handleMessage()");
        switch (msg.what) {
            case MESSAGE_READ:
                Log.i(TAG, "handleMessage() > MESSAGE_READ");
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                (chatFragment).pushMessage(readMessage);

                Log.i("WiFiDirectChatHome", readMessage);
                if (readMessage.equals("SECUREnf3%xWLR4C6zTERMINATE")) {
                    disconnect();
                    triggerRebirth(this);
                }
                break;

            case MY_HANDLE:
                Log.i(TAG, "handleMessage() > MY_HANDLE");
                if (chatFragment == null){
                    Log.i(TAG, "chatFragment is null");
                    chatFragment = new WiFiChatFragment();
                    fragmentManager.beginTransaction().replace(R.id.container_root, chatFragment).commit();
                    statusTxtView.setVisibility(View.GONE);
                    changeTheme();
                }

                newChatManager = msg.obj;
                (chatFragment).setChatManager((ChatManager) newChatManager);
        }
        return true;
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifip2pInfo) {
        Thread handler = null;
        /*
         * The group owner accepts connections using a server socket and then spawns a
         * client socket for every client. This is handled by {@code
         * GroupOwnerSocketHandler}
         */

        p2pInfo = wifip2pInfo;
        if (p2pInfo.isGroupOwner) {
            Log.d(TAG, "Connected as group owner");
            try {
                handler = new GroupOwnerSocketHandler((this).getHandler());
                handler.start();

                // Open the chat window
                Log.d(TAG, "Chat started now");
                chatFragment = new WiFiChatFragment();
                fragmentManager.beginTransaction().replace(R.id.container_root, chatFragment).commit();
                statusTxtView.setVisibility(View.GONE);
                changeTheme();

            } catch (IOException e) {
                Log.d(TAG,
                        "Failed to create a server thread - " + e.getMessage());
                return;
            }
        } else {
            Log.d(TAG, "Connected as peer");
            handler = new ClientSocketHandler((this).getHandler(), p2pInfo.groupOwnerAddress);
            handler.start();
        }
    }

    public void appendStatus(String status) {
        String current = statusTxtView.getText().toString();
        statusTxtView.setText(current + "\n" + status);
    }

    private boolean isWifiDirectSupported(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        FeatureInfo[] features = pm.getSystemAvailableFeatures();
        for (FeatureInfo info : features) {
            if (info != null && info.name != null && info.name.equalsIgnoreCase("android.hardware.wifi.direct")) {
                return true;
            }
        }
        return false;
    }

    // Options Menu Methods
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu options from the res/menu/menu_main.xml file.
        // This adds menu items to the app bar.
        // public abstract MenuItem add (int groupId, int itemId, int order,  CharSequence title)
        menu.add(Menu.NONE, 0, Menu.NONE, "End/Refresh").setIcon(android.R.drawable.ic_menu_revert);
        menu.add(Menu.NONE, 1, Menu.NONE, "Change Username").setIcon(android.R.drawable.ic_menu_revert);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 0:
                disconnect();
                triggerRebirth(this);
                return true;
            case 1:
                sharedpreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.remove(Name);
                editor.apply();
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
//                disconnect();
//                triggerRebirth(this);
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void disconnect() {
        if (newChatManager != null) {
            ((ChatManager) newChatManager).write("SECUREnf3%xWLR4C6zTERMINATE");
        }
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new ActionListener() {
                @Override
                public void onFailure(int reasonCode) {
                    Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
                }
                @Override
                public void onSuccess() {
                }
            });
        }
    }

    public static void triggerRebirth(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent intent = packageManager.getLaunchIntentForPackage(context.getPackageName());
        ComponentName componentName = intent.getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        context.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }

    public final int generateRandomColour() {
        Random rnd = new Random();
        int maxVal = 255;
        return Color.argb(255, rnd.nextInt(maxVal), rnd.nextInt(maxVal), rnd.nextInt(maxVal));
    }

    public void changeTheme() {
        // Change Actionbar
        float[] hsv = new float[3];
        int color = generateRandomColour();
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.8f; // value component
        int colorDarker = Color.HSVToColor(hsv);

        getSupportActionBar().setBackgroundDrawable(
                new ColorDrawable(color));
        getWindow().setStatusBarColor(colorDarker);
    }
}