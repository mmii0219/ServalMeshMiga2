package org.servalproject.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.view.Display;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ScrollView;
import android.util.Log;

import org.servalproject.wifidirect.MyAccessibilityService;
import org.servalproject.wifidirect.WiFiDirectActivity;
import org.servalproject.Control;
import org.servalproject.PreparationWizard;
import org.servalproject.R;
import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.servald.ServalD;
import org.servalproject.system.CommotionAdhoc;
import org.servalproject.system.NetworkManager;
import org.servalproject.system.NetworkState;
import org.servalproject.system.ScanResults;
import org.servalproject.system.WifiAdhocControl;
import org.servalproject.system.WifiAdhocNetwork;
import org.servalproject.system.WifiApControl;
import org.servalproject.ui.SimpleAdapter.ViewBinder;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

public class Networks extends Activity implements CompoundButton.OnCheckedChangeListener {
    private SimpleAdapter<NetworkControl> adapter;
    private ListView listView;
    private ServalBatPhoneApplication app;
    private NetworkManager nm;
    private TextView status;
    private TextView process_text;
    private ScrollView scrollview;
    Thread thread_process;
    String s_process;
    String s_record;
    boolean activity_on = true;
    private CheckBox enabled;
    private CheckBox enabledstep2;////Miga 20180409
    private CheckBox enabledcontroller;////Miga 20180508
    private CheckBox enabledFile;////Miga 20180730
    private static final String TAG = "Networks";
    private static boolean WiFiDirectChecked = false;
    private Intent serviceIntent = null;
    private Control WiFiDirectService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            WiFiDirectService = ((Control.MyBinder) service).getService();
        }

        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        switch (compoundButton.getId()) {
            case R.id.enabled:
                setEnabled(isChecked);
                break;
        }
    }

    private void setEnabled(boolean isEnabled) {
        SharedPreferences.Editor e = app.settings.edit();
        e.putBoolean("meshRunning", isEnabled);
        e.commit();
        serviceIntent = new Intent(this, Control.class);
        
        // Leaf0923
        if (isEnabled) {
            startService(serviceIntent);
            bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
        } else {
            unbindService(mServiceConnection);
           stopService(serviceIntent);
        }
        if (enabled.isChecked() != isEnabled) {
            enabled.setChecked(isEnabled);
        }
    }

    private abstract class NetworkControl implements OnClickListener {
        CheckBox enabled;
        TextView status;
        ImageView icon;
        int Connetion_flag;

        abstract CharSequence getTitle();

        abstract NetworkState getState();

        abstract void enable();

        abstract void clicked();

        CharSequence getStatus() {
            NetworkState state = getState();
            if (state == null || state == NetworkState.Disabled)
                return null;
            return state.toString(Networks.this);
        }

        void updateStatus() {
            if (status == null)
                return;
            status.setText(getStatus());
        }

        boolean isEnabled(NetworkState state) {
            return state != NetworkState.Disabling && state != NetworkState.Enabling;
        }

        boolean isChecked(NetworkState state) {
            return state != null && state != NetworkState.Disabled && state != NetworkState.Error;
        }

        void updateEnabled() {
            if (Connetion_flag == 0) {
                if (enabled == null)
                    return;
                NetworkState s = getState();
                boolean isEnabled = isEnabled(s);
                if (enabled.isEnabled() != isEnabled)
                    enabled.setEnabled(isEnabled);
                boolean isChecked = isChecked(s);
                if (enabled.isChecked() != isChecked) {
                    enabled.setChecked(isChecked);
                }
            } else {
                enabled.setChecked(WiFiDirectChecked);
            }
        }

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.enabled: {
                    if (Connetion_flag == 0) {
                        boolean isChecked = enabled.isChecked();
                        NetworkState state = getState();
                        if (state == NetworkState.Enabled && !isChecked)
                            nm.control.off(null);
                        else if ((state == null || state == NetworkState.Disabled || state == NetworkState.Error) && isChecked)
                            enable();
                        this.updateEnabled();
                    } else if (Connetion_flag == 1) {
                        WiFiDirectChecked = !WiFiDirectChecked;
                        this.updateEnabled();
                        enable();
                    }
                }
                break;
                default:
                    clicked();
            }
        }

        protected void setIcon(Intent i) {
            if (i != null) {
                PackageManager packageManager = getPackageManager();
                ResolveInfo r = packageManager.resolveActivity(i, 0);
                if (r != null) {
                    icon.setVisibility(View.VISIBLE);
                    icon.setImageDrawable(r.loadIcon(packageManager));
                    return;
                }
            }
            icon.setVisibility(View.GONE);
        }

        public abstract void setIcon();
    }

    private NetworkControl WifiClient = new NetworkControl() {
        @Override
        CharSequence getTitle() {
            Connetion_flag = 0;
            return getText(R.string.wifi_client);
        }

        @Override
        NetworkState getState() {
            return nm.control.getWifiClientState();
        }

        @Override
        CharSequence getStatus() {
            if (getState() != NetworkState.Enabled)
                return super.getStatus();

            NetworkInfo networkInfo = nm.control.connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            WifiInfo connection = nm.control.wifiManager.getConnectionInfo();
            if (connection == null)
                return null;

            String ssid = "";
            if (connection.getBSSID() != null) {
                ssid = connection.getSSID();
                if (ssid == null || ssid.equals(""))
                    ssid = getString(R.string.ssid_none);
            }

            if (networkInfo != null && networkInfo.isConnected())
                return getString(R.string.connected_to, ssid);

            if (!ssid.equals(""))
                return getString(R.string.connecting_to, ssid);

            Collection<ScanResults> results = nm.getScanResults();
            if (results != null) {
                int servalCount = 0;
                int openCount = 0;
                int knownCount = 0;
                int adhocCount = 0;
                for (ScanResults s : results) {
                    if (s.isAdhoc())
                        adhocCount++;
                    else {
                        if (s.isServal())
                            servalCount++;
                        if (!s.isSecure())
                            openCount++;
                        if (s.getConfiguration() != null)
                            knownCount++;
                    }
                }
                if (servalCount > 0)
                    return getResources().getQuantityString(R.plurals.serval_networks, servalCount, servalCount);
                if (knownCount > 0)
                    return getResources().getQuantityString(R.plurals.known_networks, knownCount, knownCount);
                if (openCount > 0)
                    return getResources().getQuantityString(R.plurals.open_networks, openCount, openCount);
            }
            return super.getStatus();
        }

        private Intent getIntentAction() {
            return new Intent(WifiManager.ACTION_PICK_WIFI_NETWORK);
        }

        @Override
        public void setIcon() {
            setIcon(getIntentAction());
        }

        @Override
        public void enable() {
            setEnabled(true);
            nm.control.startClientMode(null);
        }

        @Override
        public void clicked() {
            startActivity(getIntentAction());
        }
    };
    // Leaf0714
    private NetworkControl WifiDirect = new NetworkControl() {
        @Override
        CharSequence getTitle() {
            Connetion_flag = 1;
            return "Auto Wi-Fi Direct";
        }

        @Override
        NetworkState getState() {
            return null;
        }

        @Override
        CharSequence getStatus() {
            return null;
        }

        private Intent getIntentAction() {
            return null;
        }

        @Override
        public void setIcon() {
        }

        @Override
        public void enable() {
            //Intent accessibilityIntent = new Intent(Networks.this, MyAccessibilityService.class);
            if (WiFiDirectChecked == true) {
                if (WiFiDirectService != null)
                    WiFiDirectService.Auto = true;
                //startService(accessibilityIntent);
                //bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
            } else {
                if (WiFiDirectService != null)
                    WiFiDirectService.Auto = false;
                //stopService(accessibilityIntent);
            }
        }

        @Override
        public void clicked() {
            startActivity(new Intent(Networks.this, WiFiDirectActivity.class));
        }
    };
    private NetworkControl HotSpot = new NetworkControl() {
        @Override
        CharSequence getTitle() {
            Connetion_flag = 0;
            return getText(R.string.hotspot);
        }

        @Override
        NetworkState getState() {
            return nm.control.wifiApManager.getNetworkState();
        }

        @Override
        CharSequence getStatus() {
            if (getState() != NetworkState.Enabled)
                return super.getStatus();
            WifiConfiguration config = nm.control.wifiApManager.getWifiApConfiguration();
            if (config == null || config.SSID == null || config.SSID.equals("")) {
                // Looks like this handset is hiding hotspot config, we probably can't set it either.
                return super.getStatus();
            }
            return config.SSID;
        }

        private Intent getIntentAction() {
            PackageManager packageManager = getPackageManager();

            Intent i = new Intent();
            // Android 4(-ish)
            i.setClassName("com.android.settings", "com.android.settings.TetherSettings");
            ResolveInfo r = packageManager.resolveActivity(i, 0);
            if (r != null) {
                i.setClassName(r.activityInfo.packageName, r.activityInfo.name);
                return i;
            }
            // HTC roms
            i.setClassName("com.htc.WifiRouter", "com.htc.WifiRouter.WifiRouter");
            r = packageManager.resolveActivity(i, 0);
            if (r != null) {
                i.setClassName(r.activityInfo.packageName, r.activityInfo.name);
                return i;
            }
            // AOSP v2(-ish)
            i.setClassName("com.android.settings", "com.android.settings.wifi.WifiApSettings");
            r = packageManager.resolveActivity(i, 0);
            if (r != null) {
                i.setClassName(r.activityInfo.packageName, r.activityInfo.name);
                return i;
            }
            return null;
        }

        @Override
        public void setIcon() {
            setIcon(getIntentAction());
        }

        @Override
        public void enable() {
            new AlertDialog.Builder(Networks.this)
                    .setTitle(
                            getText(R.string.openhotspottitle)
                    )
                    .setMessage(
                            getText(R.string.openhotspotmessage)
                    )
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(
                            getText(R.string.connectbutton),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int button) {
                                    setEnabled(true);
                                    nm.control.connectAp(false, null);
                                }
                            }
                    )
                    .show();
        }

        @Override
        public void clicked() {
            Intent i = getIntentAction();
            if (i != null)
                startActivity(i);
        }
    };

    private NetworkControl Adhoc = new NetworkControl() {

        @Override
        CharSequence getTitle() {
            return getText(R.string.adhoc);
        }

        @Override
        NetworkState getState() {
            return nm.control.adhocControl.getState();
        }

        private boolean testDialog() {
            if (WifiAdhocControl.isAdhocSupported())
                return true;

            new AlertDialog.Builder(Networks.this)
                    .setTitle(
                            getText(R.string.adhoctesttitle))
                    .setIcon(R.drawable.ic_dragon)
                    .setMessage(
                            getText(R.string.adhoctestmessage))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(
                            getText(R.string.testbutton),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int button) {
                                    Intent intent = new Intent(
                                            Networks.this,
                                            PreparationWizard.class);
                                    startActivity(intent);
                                }
                            })
                    .show();
            return false;
        }

        @Override
        public void enable() {
            WifiAdhocNetwork network = nm.control.adhocControl.getDefaultNetwork();
            setEnabled(true);
            nm.control.connectAdhoc(network, null);
        }

        @Override
        public boolean isEnabled(NetworkState state) {
            if (!WifiAdhocControl.isAdhocSupported())
                return false;
            return super.isEnabled(state);
        }

        @Override
        public void setIcon() {
            icon.setVisibility(View.GONE);
        }

        @Override
        public void clicked() {
            if (testDialog()) {
                WifiAdhocNetwork network = nm.control.adhocControl.getDefaultNetwork();
                Intent intent = new Intent(
                        Networks.this,
                        AdhocPreferences.class);
                intent.putExtra(
                        AdhocPreferences.EXTRA_PROFILE_NAME,
                        network.preferenceName);
                startActivity(intent);
            }
        }
    };

    private NetworkControl Commotion = new NetworkControl() {
        @Override
        CharSequence getTitle() {
            return CommotionAdhoc.appName;
        }

        @Override
        NetworkState getState() {
            return nm.control.commotionAdhoc.getState();
        }

        @Override
        void enable() {
            setEnabled(true);
            nm.control.connectMeshTether(null);
        }

        @Override
        void clicked() {
            Intent i = getIntentAction();
            if (i != null)
                startActivity(i);
        }

        private Intent getIntentAction() {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setPackage(CommotionAdhoc.PACKAGE_NAME);
            PackageManager packageManager = getPackageManager();
            ResolveInfo r = packageManager.resolveActivity(i, 0);
            if (r.activityInfo != null) {
                i.setClassName(r.activityInfo.packageName, r.activityInfo.name);
                return i;
            }
            return null;
        }

        @Override
        public void setIcon() {
            setIcon(getIntentAction());
        }

    };
    //Miga 20180409
    private CheckBox.OnCheckedChangeListener chklistener = new CheckBox.OnCheckedChangeListener(){
        @Override
        public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
            if(enabledstep2.isChecked()){
                WiFiDirectService.Step2Auto=true;
                //WiFiDirectService.step2_start_time = Calendar.getInstance().getTimeInMillis();
            }else if (!enabledstep2.isChecked()){
                WiFiDirectService.Step2Auto=false;
                WiFiDirectService.step2_start_time = 0;
            }
        }
    };
    //Miga End
    //Miga 20180508
    private CheckBox.OnCheckedChangeListener chkcontrollerlistener = new CheckBox.OnCheckedChangeListener(){
        @Override
        public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
            if(enabledcontroller.isChecked()){
                WiFiDirectService.ControllerAuto=true;
                //WiFiDirectService.step2_start_time = Calendar.getInstance().getTimeInMillis();
            }else if (!enabledcontroller.isChecked()){
                WiFiDirectService.ControllerAuto=false;
            }
        }
    };
    //Miga End
/*    //Miga 20180802
    private CheckBox.OnCheckedChangeListener chkfilelistener = new CheckBox.OnCheckedChangeListener(){
        @Override
        public void onCheckedChanged(CompoundButton buttonView,boolean isChecked) {
            if(enabledFile.isChecked()){
                WiFiDirectService.SendFileAuto=true;
            }else if (!enabledFile.isChecked()){
                WiFiDirectService.SendFileAuto=false;
            }
        }
    };
    //Miga End*/
    //Miga 20180807 Send file button
    private Button.OnClickListener btn1Listener = new Button.OnClickListener() {
        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub
            Log.d("Miga","Onclick!");
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            try {
                startActivityForResult(
                        Intent.createChooser(intent, "Select a File to Upload"), 1);
            } catch (android.content.ActivityNotFoundException ex) {
                // Potentially direct the user to the Market with a Dialog
                //Toast.makeText(this, "Please install a File Manager.",Toast.LENGTH_SHORT).show();
            }
        }
    };
    //取得所選擇的檔案路徑+名稱
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        String [] temp,resultFilePath;
        String filepaths1="";
        switch(requestCode) {
            case 1:
                Log.i("Test", "Result URI " + data.getData());
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    String uriString = uri.toString();
                    File myFile = new File(uriString);
                    String path = myFile.getAbsolutePath();
                    String displayName = null;
                    if (uriString.startsWith("content://")) {
                        Cursor cursor = null;
                        try {
                            cursor = getApplicationContext().getContentResolver().query(uri, null, null, null, null);
                            if (cursor != null && cursor.moveToFirst()) {
                                displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                                Log.d("Miga","content://displayName:"+displayName);
                                temp = path.split("3A");
                                //Log.d("Miga","temp:"+temp);
                                filepaths1 = temp[1].replace("%2F","/");
                                Log.d("Miga","filepath:"+filepaths1);
                                resultFilePath = filepaths1.split(displayName);
                                Log.d("Miga","resultFilePath:"+resultFilePath[0]);
                                WiFiDirectService.FileNamee = displayName;//e.g. P_20180807_163146.jpg
                                WiFiDirectService.FilePathh = resultFilePath[0];//e.g. DCIM/Camera/
                                WiFiDirectService.SendFileAuto = true;
                                WiFiDirectService.BtnOnclick = true;//拿來讓HYBRID判斷是否是自己這個裝置觸發的按鈕
                            }
                        } finally {
                            cursor.close();
                        }
                    } else if (uriString.startsWith("file://")) {
                        displayName = myFile.getName();
                        Log.d("Miga","displayName:"+displayName);
                    }
                }
                break;
        }
    }
    //Miga End
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.networks);
        this.listView = (ListView) this.findViewById(R.id.listView);
        this.status = (TextView) this.findViewById(R.id.serval_status);
        this.enabled = (CheckBox) this.findViewById(R.id.enabled);

        this.enabled.setOnCheckedChangeListener(this);
        //Miga 20180409
        this.enabledstep2 = (CheckBox) this.findViewById(R.id.enablestep2);
        this.enabledstep2.setOnCheckedChangeListener(chklistener);
        //Miga End
        //Miga 20180508
        this.enabledcontroller = (CheckBox) this.findViewById(R.id.enablecontroller);
        this.enabledcontroller.setOnCheckedChangeListener(chkcontrollerlistener);
        //Miga End
       /* //Miga 20180802
        this.enabledFile = (CheckBox) this.findViewById(R.id.enabledFile);
        this.enabledFile.setOnCheckedChangeListener(chkfilelistener);
        //Miga End*/
        //Miga 20180807
        Button btn1 = (Button) findViewById(R.id.SendFileBtn);
        btn1.setOnClickListener(btn1Listener);
        this.app = (ServalBatPhoneApplication) this.getApplication();
        this.nm = NetworkManager.getNetworkManager(app);
        adapter = new SimpleAdapter<NetworkControl>(this, binder);
        List<NetworkControl> data = new ArrayList<NetworkControl>();
        data.add(this.WifiClient);
        if (nm.control.wifiApManager != null)
            data.add(this.HotSpot);
        // Leaf0714
        data.add(this.WifiDirect);
        if (CommotionAdhoc.isInstalled())
            data.add(this.Commotion);
        adapter.setItems(data);
        listView.setAdapter(adapter);


        this.process_text = (TextView) this.findViewById(R.id.process);
        this.scrollview = (ScrollView) this.findViewById(R.id.scroll);
        activity_on = true;
        s_process = "Service start";
        s_record = "";
        thread_process = new Thread(new Runnable() {
            @Override
            public void run() {
                while(activity_on) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if(WiFiDirectService !=null) {
                                if(s_record.compareTo(WiFiDirectService.s_status)!=0) {
                                    s_record = WiFiDirectService.s_status;
                                    s_process = s_process + "\n" + WiFiDirectService.s_status;
                                    process_text.setText(s_process);
                                    scrollview.fullScroll(View.FOCUS_DOWN);
                                    if(s_process.length() > 8000)
                                        s_process = "Clean log";
                                }
                            }
                        }
                    });
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread_process.start();
    }

    private void statusChanged(String status) {
        this.status.setText(status);
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ServalD.ACTION_STATUS)) {
                statusChanged(intent.getStringExtra(ServalD.EXTRA_STATUS));
            } else {
                adapter.notifyDataSetChanged();
            }
        }

    };

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ServalD.ACTION_STATUS);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(WifiAdhocControl.ADHOC_STATE_CHANGED_ACTION);
        if (nm.control.wifiApManager != null)
            filter.addAction(WifiApControl.WIFI_AP_STATE_CHANGED_ACTION);
        this.registerReceiver(receiver, filter);

        this.enabled.setChecked(app.settings.getBoolean("meshRunning", false));
        statusChanged(app.server.getStatus());


    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activity_on = false;
        if (this.enabled.isChecked())
            unbindService(mServiceConnection);
    }

    @Override
    protected void onPause() {
        super.onPause();
        this.unregisterReceiver(receiver);
    }

    private ViewBinder<NetworkControl> binder = new ViewBinder<NetworkControl>() {
        @Override
        public long getId(int position, NetworkControl t) {
            return position;
        }

        @Override
        public int[] getResourceIds() {
            return new int[]{R.layout.network};
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isEnabled(NetworkControl networkControl) {
            return true;
        }

        @Override
        public int getViewType(int position, NetworkControl t) {
            return 0;
        }

        @Override
        public void bindView(int position, NetworkControl t, View view) {
            TextView title = (TextView) view.findViewById(R.id.title);
            t.icon = (ImageView) view.findViewById(R.id.icon);
            t.status = (TextView) view.findViewById(R.id.status);
            t.enabled = (CheckBox) view.findViewById(R.id.enabled);

            title.setText(t.getTitle());
            t.enabled.setTag(t);
            t.enabled.setOnClickListener(t);
            view.setOnClickListener(t);
            t.updateStatus();
            t.updateEnabled();
            t.setIcon();
        }
    };
}
