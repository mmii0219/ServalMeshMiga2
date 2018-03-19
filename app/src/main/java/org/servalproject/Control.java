package org.servalproject;


import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.ScanResult;
import android.content.BroadcastReceiver;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.format.Time;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.servalproject.Control.Initial;
import org.servalproject.Control.RoleFlag;
import org.servalproject.Control.StateFlag;
import org.servalproject.ServalBatPhoneApplication.State;
import org.servalproject.servald.IPeer;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDFailureException;
import org.servalproject.ui.Networks;
import org.servalproject.wifidirect.AutoWiFiDirect;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Control service responsible for turning Serval on and off and changing the
 * Wifi radio mode.
 */
public class Control extends Service {
    private ServalBatPhoneApplication app;
    private boolean servicesRunning = false;
    private boolean serviceRunning = false;
    private SimpleWebServer webServer;
    private int peerCount = -1;
    private PowerManager.WakeLock cpuLock;
    private WifiManager.MulticastLock multicastLock = null;
    private static final String TAG = "Control";
    // Leaf0818
    private WifiP2pManager manager;
    private Channel channel;
    private final IntentFilter intentFilter = new IntentFilter();
    private BroadcastReceiver receiver = null;
    private BroadcastReceiver receiver_scan = null;
    public static boolean Isconnect = false;
    public String myDeviceName = null;

    private Thread t_findPeer = null;
    private Thread t_checkGO = null;
    private Thread t_wifi_connect = null;
    private Thread t_reconnection_wifiAp = null;
    private Thread t_collectIP = null;
    private Thread t_send_peer_count = null;
    private Thread t_receive_peer_count = null;
    private boolean isRunning = false;
    static public boolean Auto = false;
    private MyBinder mBinder = new MyBinder();
    // Leaf1104
    public int STATE;
    private WifiManager wifi = null;
    private String GOpasswd = null;
    private String WiFiApName = null;
    private String Cluster_Name = null;

    private ConnectivityManager mConnectivityManager = null;
    private NetworkInfo mNetworkInfo = null;
    private WifiP2pDnsSdServiceRequest serviceRequest = null;
    private WifiP2pDnsSdServiceInfo serviceInfo = null;
    private Map record = null;
    private Map record_re = null;
    // Leaf0616
    private int result_size = 0;
    private boolean pre_connect = false;
    private List<ScanResult> wifi_scan_results;
    public String s_status = "";
    private long start_time, total_time, sleep_time;
    private static int IP_port_for_IPModify = 2555;
    private static int IP_port_for_peer_counting = 2666;
    private ServerSocket ss = null;
    private Map<String, Integer> IPTable = new HashMap<String, Integer>();
    private Map<String, Integer> PeerTable = new HashMap<String, Integer>();
    private Socket sc; // for CollectIP_server
    private DatagramSocket receiveds; // for receive_peer_count
    private int NumRound;


    public enum StateFlag {
        GO_INITIAL(0), ADD_SERVICE(1), DISCOVERY_SERVICE(2), GO_FORMATION(3), MULTI_CONNECT(4),WAITING(5);
        private int index;

        StateFlag(int idx) {
            this.index = idx;
        }

        public int getIndex() {
            return index;
        }
    }

    // <aqua0722>
    private Thread t_native = null;
    private Thread t_register = null;
    private String PublicIP = "140.114.77.81";
    //private final String AnchorAP_SSID = "WMNET";
    //private final String AnchorAP_PWD = "lab741lab741";
    //private int forwardingPort=-1;
    private String GDIPandFP = "";
    private LocalServerSocket Localserver;
    private LocalSocket Localreceiver;
    private BufferedOutputStream Localout;
    public int ROLE;

    //Miga
    private int power_level = 0;
    private int peercount, InfoChangeTime,discoverpeernum, grouppeer =0;
    private boolean writeLog=false,isCheck=false, isOpenSWIAThread=false;
    private int ExpDeviceNum=3;//目前要測試的裝置數量,有2-6隻
    private String GO_mac;
    private Thread initial = null;
    private Thread CheckWhichGroup = null;
    private Thread SendWiFiIpAddr = null;
    //private Thread ChechSWIAThread = null;//跑一個thread來確認
    private Map<String, Map> record_set = new HashMap<String, Map>();
    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    private WifiP2pDeviceList peerList;
    private BroadcastReceiver receiver_peer = null;
    private int TryNum = 15;
    private boolean wifiScanCheck = false;
    private boolean isWifiConnect = false;//學長的temp
    //for multicast socket add by Miga 20180129
    private  Enumeration<NetworkInterface> enumeration ;
    private  NetworkInterface p2p0 = null;
    private MulticastSocket recvSocket;//Miga , for ip table
    private MulticastSocket recvPeerSocket;//Miga , for peer table
    //for multicast socket End add by Miga
    private boolean IsConnecting=false;
    private String WiFiIpAddr = null;
    private String GO_ClusterName=null;
    private InetAddress multicgroup;
    private boolean IsP2Pconnect = false;//Miga 20180314 連線後不論是GO或是CLIENT都會將這個值設為True, send/receive_peer_count 會使用到

    public enum RoleFlag {
        NONE(0), GO(1), CLIENT(2), BRIDGE(3), WIFI_CLIENT(4);//BRIDGE就是之前的RELAY
        private int index;

        RoleFlag(int idx) {
            this.index = idx;
        }

        public int getIndex() {
            return this.index;
        }
    }


    private List<Step1Data_set> Collect_record;// Wang ,用來儲存裝置彼此交換後的info
    // 0 : none, 1 : go, 2 : client 3: relay

    public class Step1Data_set {//進行步驟1,選擇GO加入的排序
        private String SSID;
        private String key;
        private String Name;
        private String PEER;
        private String MAC;
        private String POWER;
        private String GroupPEER;
        //private String GO;

        public Step1Data_set(String SSID, String key, String Name, String PEER, String MAC,
                             String POWER, String GroupPEER) {
            this.SSID = SSID;
            this.key = key;
            this.Name = Name;
            this.PEER = PEER;
            this.MAC = MAC;
            this.POWER = POWER;
            this.GroupPEER = GroupPEER;
            //this.GO = GO;
        }

        String getSSID() {
            return this.SSID;
        }

        String getkey() {
            return this.key;
        }

        String getName() {
            return this.Name;
        }

        String getPEER() {
            return this.PEER;
        }

        String getMAC() {
            return this.MAC;
        }

        String getPOWER() {
            return this.POWER;
        }

        String getGroupPEER() {
            return this.GroupPEER;
        }

        /*_String getGO() {
            return this.GO;
        }*/

        public boolean equals(Object object) {//判斷SSID是不是一樣的
            Step1Data_set other = (Step1Data_set) object;
            if (this.SSID.equals(other.SSID) == true)
                return true;

            return false;
        }

        public String toString() {
            return this.SSID + " " + this.Name + " " + this.PEER + " " + this.MAC + " " + this.POWER;
        }

        public int compareTo(Step1Data_set data) {
            String SSID = data.getSSID();
            String PEER = data.getPEER();
            String POWER = data.getPOWER();
            String GroupPeer = data.getGroupPEER();

            if (this.PEER.compareTo(PEER) < 0) {
                return 1;
            } else if (this.PEER.compareTo(PEER) > 0) {
                return -1;
            }

            /*if (this.POWER.equals("100")) {
                return -1;
            } else if(POWER.equals("100")) {
                return 1;
            }else */if (this.POWER.compareTo(POWER) < 0) {
                return 1;
            } else if (this.POWER.compareTo(POWER) > 0) {
                return -1;
            }

            if (this.SSID.compareTo(SSID) < 0) {
                return 1;
            } else if (this.SSID.compareTo(SSID) > 0) {
                return -1;
            }

            return 0;
        }
    }


    // </aqua0722>
    public void onNetworkStateChanged() {
        if (serviceRunning) {
            Log.d("Leaf", "onNetworkStateChanged()");
            new AsyncTask<Object, Object, Object>() {
                @Override
                protected Object doInBackground(Object... params) {
                    modeChanged();
                    return null;
                }
            }.execute();
        }
    }


    private synchronized void startServices() {
        if (servicesRunning)
            return;
        Log.d(TAG, "Starting services");
        servicesRunning = true;
        cpuLock.acquire();
        multicastLock.acquire();
        try {
            app.server.isRunning();
        } catch (ServalDFailureException e) {
            app.displayToastMessage(e.getMessage());
            Log.e(TAG, e.getMessage(), e);
        }
        peerCount = 0;
        updateNotification();
        try {
            ServalDCommand.configActions(
                    ServalDCommand.ConfigAction.del, "interfaces.0.exclude",
                    ServalDCommand.ConfigAction.sync
            );
        } catch (ServalDFailureException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        try {
            if (webServer == null)
                webServer = new SimpleWebServer(8080);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    private synchronized void stopServices() {
        if (!servicesRunning)
            return;

        Log.d(TAG, "Stopping services");
        servicesRunning = false;
        multicastLock.release();
        try {
            ServalDCommand.configActions(
                    ServalDCommand.ConfigAction.set, "interfaces.0.exclude", "on",
                    ServalDCommand.ConfigAction.sync
            );
        } catch (ServalDFailureException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        peerCount = -1;
        if (webServer != null) {
            webServer.interrupt();
            webServer = null;
        }

        this.stopForeground(true);
        cpuLock.release();
    }

    private synchronized void modeChanged() {
        boolean wifiOn = app.nm.isUsableNetworkConnected();

        Log.d(TAG, "modeChanged(" + wifiOn + ")");

        // if the software is disabled, or the radio has cycled to sleeping,
        // make sure everything is turned off.
        if (!serviceRunning)
            wifiOn = false;

        if (multicastLock == null) {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);//Edit by Miga 20180205 , eclipse ver:(WifiManager)getSystemService(Context.WIFI_SERVICE);
            multicastLock = wm.createMulticastLock("org.servalproject");
        }

        if (wifiOn == true || Isconnect == true) {
            Log.d("Leaf0709", "Start Sevice");
            startServices();
        } else {
            stopServices();
        }
    }

    private void updateNotification() {
        if (!servicesRunning)
            return;

        /*Notification notification = new Notification(
                R.drawable.ic_serval_logo, getString(R.string.app_name),
                System.currentTimeMillis());

        Intent intent = new Intent(app, Main.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        notification.setLatestEventInfo(Control.this, getString(R.string.app_name),
                app.getResources().getQuantityString(R.plurals.peers_label, peerCount, peerCount),
                PendingIntent.getActivity(app, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT));

        notification.flags = Notification.FLAG_ONGOING_EVENT;
        */
        // For API23+ Add by Miga 20180205
        Intent intent = new Intent(app, Main.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        Notification notification = new Notification.Builder(Control.this)
                .setAutoCancel(true)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(app.getResources().getQuantityString(R.plurals.peers_label, peerCount, peerCount))
                .setContentIntent(PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT))
                .setSmallIcon(R.drawable.ic_serval_logo)
                .setWhen(System.currentTimeMillis())
                .build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        this.startForeground(-1, notification);
    }

    private synchronized void startService() {
        Log.e("Leaf", "modeChanged");
        app.controlService = this;
        app.setState(State.Starting);
        try {
            this.modeChanged();
            app.setState(State.On);
        } catch (Exception e) {
            app.setState(State.Off);
            Log.e("BatPhone", e.getMessage(), e);
            app.displayToastMessage(e.getMessage());
        }
    }

    private synchronized void stopService() {
        Log.e("Leaf", "Control_stopService()");
        app.setState(State.Stopping);
        app.nm.onStopService();
        stopServices();
        app.setState(State.Off);
        app.controlService = null;
    }

    public void updatePeerCount(int peerCount) {
        if (this.peerCount == peerCount)
            return;
        this.peerCount = peerCount;
        updateNotification();
    }

    class Task extends AsyncTask<State, Object, Object> {
        @Override
        protected Object doInBackground(State... params) {
            if (app.getState() == params[0])
                return null;

            if (params[0] == State.Off) {
                stopService();
            } else {
                startService();
            }
            return null;
        }
    }

    private int Newcompare(String a, String b) {
        int alength = a.length();
        int blength = b.length();
        char[] A = a.toCharArray();
        char[] B = b.toCharArray();
        int i, j;
        int result = 0;
        for (i = 0, j = 0; i < alength && j < blength; i++, j++) {
            if (A[i] != B[j]) {
                return A[i] - B[j];
            }
        }
        if (alength > blength) {
            return 1;
        } else if (alength < blength) {
            return -1;
        }
        return result;
    }

    public class WiFi_Connect extends Thread {
        //int TryNum;

        public void run() {
            try {
                //Log.d("Miga", "Enter WiFi_Connect ");
                //record_set.clear();
                int collect_num = 10;
                //String thisTimeMAC = record.get("MAC").toString();//record是對方的服務內容（在discovery Service時指定了record=re_record）
                while (collect_num > 0) {
                    record_set.put(record.get("SSID").toString(), record);//將蒐集到的其他裝置的服務根據SSID存放個別的服務
                    //Log.d("Miga","WiFi_Connect/Receive record size:"+record_set.size());
                    //寫log, 只適用於android 5.0. 因為目前是以6.0來測試,因此先註解
                    /*if(CanWriteLogFiles()&&(!writeLog)&&record_set.size()==(ExpDeviceNum-1)) {//ExpDeviceNum為目前參與實驗的裝置數量, writelog為false表示還沒寫過log file
                        WriteLog.appendLog("WiFi_Connect/參與實驗裝置數:"+ExpDeviceNum+"更新服務次數:"+InfoChangeTime+"sleep time:"+sleep_time+"\r\n",WiFiApName);
                        Log.d("Miga", "WiFi_Connect/參與實驗裝置數:"+ExpDeviceNum+"更新服務次數:"+InfoChangeTime+"sleep time:"+sleep_time);
                        writeLog=true;
                    }*/
                    Thread.sleep(100);
                    collect_num--;
                }
                Log.d("Miga", "WiFi_Connect/Collect data and record size : " + record_set+record_set.size());
                s_status="state: WiFi_Connect/Receive record size:"+record_set.size();

                if(InfoChangeTime < 5) {//交換次數少於5次
                    STATE = StateFlag.ADD_SERVICE.getIndex();//再去重新加入資料並交換
                    return;
                }

                Log.d("Miga", "WiFi_Connect/InfoChangeTime>=5");
                if (record_set.size() == 0) {//都沒蒐集到其他人的裝置,則重新再去收集資料
                    Log.d("Miga", "WiFi_Connect/Collect data and record size = 0");
                    STATE = StateFlag.ADD_SERVICE.getIndex();
                    InfoChangeTime=0;//InfoChangeTime交換次數歸零
                    return;
                }
                //Log.d("Miga", "WiFi_Connect/ROLE:"+ROLE);
                if(ROLE == RoleFlag.NONE.getIndex()) {//還沒檢查並連線過,則進行判斷
                    String SSID = null;
                    String key = null;
                    String Name = null;//Cluster_Name
                    String PEER = null;
                    String MAC = null;
                    String POWER = null;
                    String GroupPEER = null;
                    //String GO;
                    Collect_record.clear();
                    for (Object set_key : record_set.keySet()) {
                        record = record_set.get(set_key);
                        SSID = record.get("SSID").toString();
                        key = record.get("PWD").toString();
                        Name = record.get("Name").toString();//Cluster_Name
                        PEER = record.get("PEER").toString();//可以發現到周圍的peer數,不表示為group內的device數量
                        MAC = record.get("MAC").toString();
                        POWER = record.get("POWER").toString();
                        GroupPEER = record.get("GroupPEER").toString();//Group內的device數量
                        //GO = record.get("GO").toString();
                        //Log.d("Miga", "WiFi_Connect/Insert data");

                        if (!Name.equals(Cluster_Name)) {//只儲存不同Cluster的device資料
                            Step1Data_set data = new Step1Data_set(SSID, key, Name, PEER, MAC, POWER, GroupPEER);
                            if (!Collect_record.contains(data)) {
                                Collect_record.add(data);
                            }
                        }
                    }
                    //也加入自己的data
                    Step1Data_set self = new Step1Data_set(WiFiApName, GOpasswd, Cluster_Name,
                            String.valueOf(peercount), GO_mac, String.valueOf(power_level),String.valueOf(grouppeer+1));

                    if (!Collect_record.contains(self)) {
                        Collect_record.add(self);
                    }
                    //Collect_record進行排序來選出Group來加入
                    Collections.sort(Collect_record, new Comparator<Step1Data_set>() {
                        public int compare(Step1Data_set o1, Step1Data_set o2) {
                            return o1.compareTo(o2);
                        }
                    });
                    //目的應該只是要print出有收集到哪些data
                    int obj_num = 0;
                    String Collect_contain = "";
                    Step1Data_set tmp;
                    for (int i = 0; i < Collect_record.size(); i++) {
                        tmp = (Step1Data_set) Collect_record.get(i);
                        Collect_contain = Collect_contain + obj_num + " : " + tmp.toString() + " ";
                        obj_num++;
                    }
                    Log.d("Miga", "WiFi_Connect/Collect records contain " + Collect_contain);

                    //取出排序第一個的
                    SSID = Collect_record.get(0).getSSID();
                    key = Collect_record.get(0).getkey();
                    Name = Collect_record.get(0).getName();
                    PEER = Collect_record.get(0).getPEER();
                    MAC = Collect_record.get(0).getMAC();
                    if (mConnectivityManager != null) {
                        mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                        if (!mNetworkInfo.isConnected()) {//Wifi還沒連上其他GO,則進行連線
                            if (SSID.equals(WiFiApName)) {
                                //如果自己是排序第一個的話則不做事,只需等待別人來連線
                                ROLE = RoleFlag.GO.getIndex();//變為GO
                                Log.d("Miga", "WiFi_Connect/ROLE: " + ROLE);
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                                Isconnect=true;//p2p已有人連上/被連上,目前主要是用來判斷是否可以開始進行Group內peer的計算
                                IsP2Pconnect=true;
                                return;
                            } else {//連上別人
                                // Try to connect Ap(連上排序第一個or第二個的裝置)
                                IsConnecting=true;//正在連線中,避免Reconnecting_Wifi繼續執行
                                WifiConfiguration wc = new WifiConfiguration();
                                s_status = "State: choosing peer done, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key;
                                Log.d("Miga", "State: choosing peer done, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key);
                                wc.SSID = "\"" + SSID + "\"";
                                wc.preSharedKey = "\"" + key + "\"";
                                wc.hiddenSSID = true;
                                wc.status = WifiConfiguration.Status.ENABLED;
                                wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                                wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                                wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                                wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                                wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                                TryNum = 25;
                                //檢查我們所要連的GO是否還存在
                                wifiScanCheck = false;
                                wifi.startScan();//startScan完畢後，wifi會呼叫SCAN_RESULTS_AVAILABLE_ACTION
                                long wifiscan_time_start = Calendar.getInstance().getTimeInMillis();
                                while (wifiScanCheck == false) {//在onCreate時有註冊一個廣播器,專門來徵測wifi scan的結果,wifi.startscan完畢後,wifiScanCheck應該會變為true
                                }
                                ;
                                //sleep_time = sleep_time + Calendar.getInstance().getTimeInMillis() - wifiscan_time_start;
                                wifiScanCheck = false;
                                boolean findIsGoExist = false;

                                for (int i = 0; i < wifi_scan_results.size(); i++) {//檢查接下來要連上的GO還在不在,wifi_scan_results:會列出掃描到的所有AP
                                    ScanResult sr = wifi_scan_results.get(i);
                                    if (sr.SSID.equals(SSID)) {//去比對每一個掃描到的AP,看是不是我們要連上的GO,若是則將findIsGoExist設為true並跳出for迴圈
                                        findIsGoExist = true;
                                        break;
                                    }
                                }
                                //Log.d("Miga", "WiFi_Connect/findIsGoExist : " + findIsGoExist);
                                if (findIsGoExist == false) {//若我們要連的GO不見的話,則回到ADD_SERVICE,重新再收集資料一次.20180307Miga 這裡可能要再想一下後面的流程
                                    STATE = StateFlag.ADD_SERVICE.getIndex();
                                    return;
                                }

                                //使用wifi interface連線,連上GO
                                int res = wifi.addNetwork(wc);
                                isWifiConnect = wifi.enableNetwork(res, true);//學長的temp
                                while (!mNetworkInfo.isConnected() && TryNum > 0) {//wifi interface沒成功連上,開始不斷嘗試連接
                                    isWifiConnect = wifi.enableNetwork(res, true);
                                    Thread.sleep(1000);
                                    sleep_time = sleep_time + 1000;
                                    TryNum--;

                                    s_status = "State: associating GO, enable true:?" + isWifiConnect + " remainder #attempt:"
                                            + TryNum;
                                    Log.d("Miga", "State: associating GO, enable true:?" + isWifiConnect
                                            + " remainder #attempt:" + TryNum);
                                    mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                }//End While

                                //成功連上GO
                                if (mNetworkInfo.isConnected()) {
                                    // renew service record information
                                    Cluster_Name = Name;//將自己的Cluster_Name更新為新的GO的Cluster_Name //Miga 20180118 將自己的clusterName更新了
                                    ROLE = RoleFlag.CLIENT.getIndex();//變為CLIENT
                                    WiFiIpAddr = wifiIpAddress();//取得wifi IP address
                                    Log.d("Miga", "WiFi_Connect/ROLE:" + ROLE + "Cluster_Name:" + Cluster_Name+" wifiIpAddress:"+WiFiIpAddr);
                                    s_status="state: WiFiIpAddress="+WiFiIpAddr;
                                    if(!isOpenSWIAThread){//開啟client傳送wifi ip address thread給GO
                                        if (SendWiFiIpAddr == null) {
                                            SendWiFiIpAddr = new SendWiFiIpAddr();
                                            SendWiFiIpAddr.start();
                                        }
                                        isOpenSWIAThread=true;
                                    }

                                    //CheckChangeIP(WiFiIpAddr);// Miga Add 20180307. 讓client丟自己的wifi ip addr.給GO檢查,並讓GO的IPTable內有這組ip(為了讓GO進行unicast傳送訊息用)
                                    STATE = StateFlag.ADD_SERVICE.getIndex();
                                    Isconnect=true;//p2p已有人連上/被連上,目前主要是用來判斷是否可以開始進行Group內peer的計算
                                    IsP2Pconnect=true;
                                    //isCheck = true;//檢查完畢
                                }
                                IsConnecting=false;
                            }

                        }
                    }//End mConnectivityManager != null
                }
                /*if(ROLE == RoleFlag.CLIENT.getIndex()) {
                    if (mNetworkInfo.isConnected()) {
                        // renew service record information
                        Cluster_Name = Name;//將自己的Cluster_Name更新為新的GO的Cluster_Name //Miga 20180118 將自己的clusterName更新了
                        ROLE = RoleFlag.CLIENT.getIndex();//變為CLIENT
                        WiFiIpAddr = wifiIpAddress();//取得wifi IP address
                        Log.d("Miga", "WiFi_Connect/ROLE:" + ROLE + "Cluster_Name:" + Cluster_Name + " wifiIpAddress:" + WiFiIpAddr);
                        s_status = "state: WiFiIpAddress=" + WiFiIpAddr;
                        if (!isOpenSWIAThread) {//開啟client傳送wifi ip address thread給GO
                            if (SendWiFiIpAddr == null) {
                                SendWiFiIpAddr = new SendWiFiIpAddr();
                                SendWiFiIpAddr.start();
                            }
                            isOpenSWIAThread = true;
                        }
                        //CheckChangeIP(WiFiIpAddr);// Miga Add 20180307. 讓client丟自己的wifi ip addr.給GO檢查,並讓GO的IPTable內有這組ip(為了讓GO進行unicast傳送訊息用)
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        Isconnect = true;//p2p已有人連上/被連上,目前主要是用來判斷是否可以開始進行Group內peer的計算
                        //isCheck = true;//檢查完畢
                    }
                }
*/
                //Log.d("Miga", "Collect data and record size : " + record_set+record_set.size());
            	/*
                String SSID = record.get("SSID").toString();
                String key = record.get("PWD").toString();
                String Name = record.get("Name").toString();
                String PEER = record.get("PEER").toString();
                //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: choosing peer, step 1";
                s_status = "State: choosing peer";
                Log.d("Leaf0419", "State: choosing peer, step 1");
                if (Isconnect == true && STATE == StateFlag.DISCOVERY_SERVICE.getIndex()) {
                    Log.d("Leaf0419", "State: choosing peer, step 2,  with: " + SSID);
                    if (Newcompare(Name, Cluster_Name) == 0) {
                        return;
                    }

                        int peercount = count_peer();
                        if (Integer.valueOf(PEER) < peercount) {
                            return;
                        } else if (Integer.valueOf(PEER) == peercount) {
                            if (Newcompare(Name, Cluster_Name) <= 0) {
                                return;
                            }
                        }

                    Log.d("Leaf0419", "State: choosing peer, step 3");
                    STATE = StateFlag.REMOVE_GROUP.getIndex();
                    try {
                        // Leaf0616
                        if (mConnectivityManager != null) {
                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                            if (mNetworkInfo != null) {
                                if (mNetworkInfo.isConnected() == true) {
                                    wifi.disconnect();
                                    Thread.sleep(1000);
                                }
                            }
                        }
                        List<WifiConfiguration> list = wifi.getConfiguredNetworks();
                        for (WifiConfiguration i : list) {
                            wifi.removeNetwork(i.networkId);
                            wifi.saveConfiguration();
                        }

                        STATE = StateFlag.WIFI_CONNECT.getIndex();
                        // Try to connect Ap
                        WifiConfiguration wc = new WifiConfiguration();
                        total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                        //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: choosing peer done, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key;
                        s_status = "State: choosing peer done, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key;
                        Log.d("Leaf0419", "State: choosing peer done, try to associate with" + ": SSID name: " + SSID + " , passwd: " + key);
                        wc.SSID = "\"" + SSID + "\"";
                        wc.preSharedKey = "\"" + key + "\"";
                        wc.hiddenSSID = true;
                        wc.status = WifiConfiguration.Status.ENABLED;
                        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                        wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                        wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                        wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                        wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                        TryNum = 4;
                        wifi.startScan();
                        Thread.sleep(5000);
                        int res = wifi.addNetwork(wc);
                        boolean temp = wifi.enableNetwork(res, true);
                        if (mConnectivityManager != null) {
                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                            if (mNetworkInfo != null) {
                                while (!mNetworkInfo.isConnected() && TryNum >= 0) {
                                    //res = wifi.addNetwork(wc);
                                    temp = wifi.enableNetwork(res, true);
                                    Thread.sleep(5000);
                                    TryNum--;
                                    total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                                    //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: associating GO, enable true:?" + temp +" remainder #attempt:"+ TryNum;
                                    s_status = "State: associating GO, enable true:?" + temp + " remainder #attempt:" + TryNum;
                                    Log.d("Leaf0419", "State: associating GO, enable true:?" + temp + " remainder #attempt:" + TryNum);
                                    mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                }
                                if (mNetworkInfo.isConnected() == true) {
                                    // renew service record information
                                    Cluster_Name = Name;
                                    if (manager != null) {
                                        manager.removeGroup(channel, null);
                                    }
                                    Thread.sleep(3000);
                                    // check whether change IP
                                    // EditLeaf0802
                                    String message = wifiIpAddress();
                                    IPTable = new HashMap<String, Integer>();
                                    IPTable.put(message, 0);
                                    Log.d("Leaf0419", "State: set IPTable: " + Integer.valueOf(message.substring(message.lastIndexOf(".") + 1)));
                                    TryNum = 0;
                                    while (TryNum < 5) {
                                        try {
                                            Socket Client_socket = new Socket("192.168.49.1", IP_port_for_IPModify);
                                            PrintWriter out = new PrintWriter(Client_socket.getOutputStream());
                                            Log.d("Leaf0419", "Send message: " + message);
                                            out.println(message);
                                            out.flush();
                                            BufferedReader in = new BufferedReader(new InputStreamReader(Client_socket.getInputStream()));
                                            message = in.readLine();
                                            Log.d("Leaf0419", "Receive message: " + message);
                                            String[] s = message.split(":");
                                            Log.d("Leaf0419", "Split result: " + s[0] + " " + s[1]);
                                            if (Newcompare(s[0], "YES") == 0) {
                                                boolean result = setIpWithTfiStaticIp(s[1]);
                                                Log.d("Leaf0419", "Modify the static IP address: " + result);
                                            }
                                            TryNum = 5;
                                            in.close();
                                            out.close();
                                            Client_socket.close();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                        TryNum++;
                                    }
                                    TryNum = 0;
                                    while (peerCount <= 0 && TryNum < 15) {
                                        total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                                        //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: acquiring the newest information";
                                        s_status = "State: acquiring the newest information";
                                        Log.d("Leaf0419", "State: acquiring the newest information");
                                        peerCount = ServalDCommand.peerCount();
                                        Thread.sleep(1000);
                                        TryNum++;
                                    }
                                    STATE = StateFlag.GO_FORMATION.getIndex();
                                } else {
                                    STATE = StateFlag.ADD_SERVICE.getIndex();
                                }
                            } else {
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                                Log.d("Leaf0419", "State: associating GO, mNetworkInfo is null");
                            }
                        } else {
                            STATE = StateFlag.ADD_SERVICE.getIndex();
                            Log.d("Leaf0419", "State: associating GO, mConnectivityManager is null");
                        }
                    } catch (Exception e) {
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        e.printStackTrace();
                    }
                }
           */
            } catch (Exception e) {
                STATE = StateFlag.ADD_SERVICE.getIndex();
                e.printStackTrace();
            }
        }
    }

    private void discoverService() {
        manager.setDnsSdResponseListeners(channel,
                new WifiP2pManager.DnsSdServiceResponseListener() {
                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) {

                    }
                }, new WifiP2pManager.DnsSdTxtRecordListener() {
                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> re_record,
                            WifiP2pDevice device) {
                        total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                        //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: advertising service, receive frame";
                        s_status = "State: advertising service, receive frame";
                        Log.d("Miga", "State: advertising service, receive frame");
                        record = re_record;
                        if (t_wifi_connect != null) {
                            if (t_wifi_connect.isAlive()) {
                                //Log.d("Miga", " WiFi_Connect isAlive  ");
                                return;
                            }
                        }
                        if (t_wifi_connect == null) {
                            //Log.d("Miga", " WiFi_Connect start  ");
                            t_wifi_connect = new WiFi_Connect();
                            t_wifi_connect.start();
                        } else {
                            if (!t_wifi_connect.isAlive()) {
                                //Log.d("Miga", " WiFi_Connect start  ");
                                t_wifi_connect = new WiFi_Connect();
                                t_wifi_connect.start();
                            }
                        }
                        return;
                    }
                });
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
    }

    private void startRegistration() {

        InfoChangeTime+=1;//加入新的資訊並交換過得次數
        //Log.d("Miga", "startRegistration/InfoChangeTime"+InfoChangeTime);
        record_re = new HashMap();
        //int peercount = count_peer();
        //peercount=record_set.size();//蒐集到周遭的info,初始值為0
        peercount = discoverpeernum;//於listener接收到時會做更新
        grouppeer = count_peer();
        //Log.d("Miga", "startRegistration/discoverpeernum"+discoverpeernum);
        /*try {
            peerCount = ServalDCommand.peerCount();
        } catch (ServalDFailureException e) {
            e.printStackTrace();
        }*/
        peerCount=grouppeer+1;//Miga 測試看看是不是會出現在app的Enable Services旁邊的字
        updatePeerCount(peerCount);

        if (Cluster_Name == null) {
            Cluster_Name = WiFiApName;
        }
        record_re.put("Name", Cluster_Name);
        record_re.put("SSID", WiFiApName);
        record_re.put("PWD", GOpasswd);
        record_re.put("PEER", String.valueOf(peercount));//可發現的peer數,不是group內的device數
        record_re.put("MAC", GO_mac);
        //Wang, power level 一定要轉成 string
        record_re.put("POWER", Integer.toString(power_level));
        record_re.put("GroupPEER",Integer.toString(grouppeer+1));//count_peer(): PeerTable內有幾個peer,+1表示group內有幾個peer
        // total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
        //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: advertising service with " + record_re.toString();
        s_status = "State: advertising service with " + record_re.toString();
        Log.d("Miga", "State: advertising service with " + record_re.toString());
        serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("Wi-Fi_Info", "_presence._tcp", record_re);
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                manager.addLocalService(channel, serviceInfo,
                        new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                // service broadcasting started
                                //Log.d("Miga", "State: advertising service, addLocalService onSuccess");
                                STATE = StateFlag.DISCOVERY_SERVICE.getIndex();
                            }

                            @Override
                            public void onFailure(int error) {
                                Log.d("Miga", "State: advertising service, addLocalService onFailure");
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                            }
                        });
            }

            @Override
            public void onFailure(int error) {
                Log.d("Miga", "State: advertising service, clearLocalServices onFailure");
                STATE = StateFlag.ADD_SERVICE.getIndex();
            }
        });
    }
    // Leaf1105
    public class Reconnection_wifiAp extends Thread {
        ServerSocket GO_serversocket, Client_sersocket;
        Socket GO_socket, Client_socket;
        boolean can_I_connectAP;
        int TryNum;

        public void run() {
            while (isRunning) {
                try {
                    Thread.sleep(1000);
                    if (Auto) {
                        //開啟discovery serivce的listener,來接收其他device的info
                        if(start_time == 0) {//OnCreate時將start_time=0;
                            start_time = Calendar.getInstance().getTimeInMillis();
                            sleep_time = 0;
                            discoverService();
                            //peerdiscover();
                        }
                        if ((!IsConnecting)&&STATE == StateFlag.ADD_SERVICE.getIndex()) {
                            s_status = "State: advertising service";
                            Log.d("Miga", "State: advertising service");
                            STATE = StateFlag.WAITING.getIndex();
                            startRegistration();
                            // 一定要 sleep 否則無法觸發discovery_service_flag
                            // 造成一直執行 add_service_flag
                            Thread.sleep(2000);
                            //sleep_time = sleep_time + 2000;
                            //discoverService();
                        }
                        if ((!IsConnecting)&&STATE == StateFlag.DISCOVERY_SERVICE.getIndex()) {
                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: discovering service";
                            s_status = "State: discovering service";
                            Log.d("Miga", "State: discovering service");
                            //stop在remove是因為peer discovery和service discovery衝突
                            manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    //Log.d("Miga", "State: discovering service, stopPeerDiscovery onSuccess");
                                    manager.removeServiceRequest(channel, serviceRequest,
                                            new WifiP2pManager.ActionListener() {
                                                @Override
                                                public void onSuccess() {
                                                    manager.addServiceRequest(channel, serviceRequest,
                                                            new WifiP2pManager.ActionListener() {// addServiceReauest(): create a service discovery request
                                                                @Override
                                                                public void onSuccess() {
                                                                    manager.discoverServices(channel,
                                                                            new WifiP2pManager.ActionListener() {
                                                                                @Override
                                                                                public void onSuccess() {
                                                                                    //Log.d("Miga", "State: discovering service, discoverServices onSuccess");
                                                                                }

                                                                                @Override
                                                                                public void onFailure(int error) {
                                                                                    //Log.d("Miga", "State: discovering service, discoverServices onFailure " + error);
                                                                                    manager.discoverPeers(channel, null);
                                                                                    STATE = StateFlag.ADD_SERVICE.getIndex();
                                                                                }
                                                                            });
                                                                }

                                                                @Override
                                                                public void onFailure(int error) {
                                                                    Log.d("Miga", "State: discovering service, addServiceRequest onFailure ");
                                                                    STATE = StateFlag.ADD_SERVICE.getIndex();
                                                                }
                                                            });
                                                }

                                                @Override
                                                public void onFailure(int reason) {
                                                    Log.d("Miga", "State: discovering service, removeServiceRequest onFailure");
                                                    STATE = StateFlag.ADD_SERVICE.getIndex();
                                                }
                                            });
                                }

                                @Override
                                public void onFailure(int reasonCode) {
                                    Log.d("Miga", "State: discovering service, stopPeerDiscovery onFailure");
                                    STATE = StateFlag.ADD_SERVICE.getIndex();
                                }
                            });
                            NumRound++;
                            if(InfoChangeTime < 5) {
                                Thread.sleep(8000);
                                sleep_time = sleep_time + 8;
                            }else{
                                Thread.sleep(10000);
                                sleep_time = sleep_time + 10;
                            }

                            STATE = StateFlag.ADD_SERVICE.getIndex();
                        }//End DISCOVERY_SERVICE
                       /* Log.d("Leaf0419", "STATE: " + STATE);
                        if (STATE >= StateFlag.REMOVE_GROUP.getIndex()) continue;

                        // Leaf0616

                        mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                        if (pre_connect == true && mNetworkInfo.isConnected() == false) {
                            STATE = StateFlag.DETECTGAW.getIndex();
                            pre_connect = mNetworkInfo.isConnected();
                            continue;
                        }
                        pre_connect = mNetworkInfo.isConnected();
                        if (Isconnect == false) {
                            STATE = StateFlag.GO_FORMATION.getIndex();
                        }

                        if (STATE == StateFlag.GO_FORMATION.getIndex()) {
                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: creating GO";
                            s_status = "State: creating GO";
                            Log.d("Leaf0419", "State: creating GO");
                            if (Isconnect == false) {
                                if (manager != null) {
                                    STATE = StateFlag.WAITING.getIndex();
                                    manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                                        @Override
                                        public void onSuccess() {
                                            STATE = StateFlag.ADD_SERVICE.getIndex();
                                        }

                                        @Override
                                        public void onFailure(int error) {
                                            Log.d("Leaf0419", "createGroup onFailure");
                                            STATE = StateFlag.GO_FORMATION.getIndex();
                                        }
                                    });
                                }
                            } else {
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                            }
                            continue;
                        }
                        if (STATE == StateFlag.ADD_SERVICE.getIndex()) {
                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: advertising service";
                            s_status = "State: advertising service";
                            Log.d("Leaf0419", "State: advertising service");
                            // startRegistration
                            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                                @Override
                                public void onGroupInfoAvailable(WifiP2pGroup group) {
                                    if (group != null) {
                                        GOpasswd = group.getPassphrase();
                                        WiFiApName = group.getNetworkName();
                                    }
                                }
                            });
                            Thread.sleep(1000);
                            STATE = StateFlag.WAITING.getIndex();
                            startRegistration();
                            discoverService();

                        }
                        if (STATE == StateFlag.DISCOVERY_SERVICE.getIndex()) {
                            total_time = total_time + (Calendar.getInstance().getTimeInMillis() - start_time) / 1000;
                            //s_status = Long.toString((Calendar.getInstance().getTimeInMillis() - start_time ) / 1000) + "s/ " + sleep_time + "s, round: " + NumRound + ", State: discovering service";
                            s_status = "State: discovering service";
                            Log.d("Leaf0419", "State: discovering service");
                            manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Leaf0419", "State: discovering service, stopPeerDiscovery onSuccess");
                                    manager.removeServiceRequest(channel, serviceRequest,
                                            new WifiP2pManager.ActionListener() {
                                                @Override
                                                public void onSuccess() {
                                                    manager.addServiceRequest(channel, serviceRequest,
                                                            new WifiP2pManager.ActionListener() {
                                                                @Override
                                                                public void onSuccess() {
                                                                    manager.discoverServices(channel,
                                                                            new WifiP2pManager.ActionListener() {
                                                                                @Override
                                                                                public void onSuccess() {
                                                                                    Log.d("Leaf0419", "State: discovering service, discoverServices onSuccess");
                                                                                }

                                                                                @Override
                                                                                public void onFailure(int error) {
                                                                                    Log.d("Leaf0419", "State: discovering service, discoverServices onFailure " + error);
                                                                                    manager.discoverPeers(channel, null);
                                                                                    STATE = StateFlag.DETECTGAW.getIndex();
                                                                                }
                                                                            });
                                                                }

                                                                @Override
                                                                public void onFailure(int error) {
                                                                    Log.d("Leaf0419", "State: discovering service, addServiceRequest onFailure ");
                                                                    STATE = StateFlag.DETECTGAW.getIndex();
                                                                }
                                                            });
                                                }

                                                @Override
                                                public void onFailure(int reason) {
                                                    Log.d("Leaf0419", "State: discovering service, removeServiceRequest onFailure");
                                                    STATE = StateFlag.DETECTGAW.getIndex();
                                                }
                                            });
                                }

                                @Override
                                public void onFailure(int reasonCode) {
                                    Log.d("Leaf0419", "State: discovering service, stopPeerDiscovery onFailure");
                                    STATE = StateFlag.DETECTGAW.getIndex();
                                }
                            });
                            NumRound++;
                            Thread.sleep(15000);
                            sleep_time = sleep_time + 15;
                            if (STATE == StateFlag.DETECTGAW.getIndex()) {
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                                wifi.setWifiEnabled(false);
                                Thread.sleep(500);
                                wifi.setWifiEnabled(true);
                                Thread.sleep(1000);
                            } else if (STATE == StateFlag.DISCOVERY_SERVICE.getIndex()) {
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                            }
                        }*/
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (Client_socket != null) {
                        try {
                            Client_socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (Client_sersocket != null) {
                        try {
                            Client_sersocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (GO_socket != null) {
                        try {
                            GO_socket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (GO_serversocket != null) {
                        try {
                            GO_serversocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private String wifiIpAddress() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);//Edit by Miga 20180205 , eclipse ver:(WifiManager)getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wm.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }

    // Miga edit 20180314 主要是讓GO來接收client傳送過來的wifi ip address
    // 並將ip儲存於IP Table
    public class CollectIP_server extends Thread {

        private PrintWriter out;
        private String recMessagetemp,recWiFiIpAddr, temp, sendbackmessage;
        private String[] recMessage;
        private int i;
        //private MulticastSocket clientSocket;//Miga
        private DatagramPacket msgPkt;//Miga
        private boolean isJoin=false;
        /*private ServerSocket ss = null;
        private Map<String, Integer> IPTable = new HashMap<String, Integer>();
        private Socket sc; // for CollectIP_server*/

        public void run() {
            try {


                //Miga add multicast 20180309 (接收使用multicast)
                byte[] buf=new byte[256];
                //回傳使用 unicast
                // 20180312 目前回傳還沒寫
                //ss = new ServerSocket(IP_port_for_IPModify);
                while(true){
                    //sc = ss.accept();//unicast

                    //讀取數據
                    msgPkt=new DatagramPacket(buf, buf.length);

                    if(msgPkt!=null){
                        recvSocket.receive(msgPkt);
                        recMessagetemp=new String(buf, 0, msgPkt.getLength());
                        recMessage = recMessagetemp.split("#");//[0]:WiFiApName(SSID),[1]: WifiIP
                        recWiFiIpAddr=recMessage[1];
                        if(recWiFiIpAddr!="fromself") {
                            if(!(recMessage[0].equals(WiFiApName))) {//接收到的不是自己,則可以進行IP判斷
                                //Log.d("Miga", "I got multicast message from:" + recMessagetemp);
                                //s_status = "I got multicast message from:" + recMessagetemp;
                                if (IPTable.containsKey(recWiFiIpAddr)) {
                                    //temp = recWiFiIpAddr;
                                    for (i = 2; i < 254; i++) {
                                        temp = "192.168.49." + String.valueOf(i);
                                        if (IPTable.containsKey(temp) == false) break;
                                    }
                                    IPTable.put(temp, 0);
                                    //for test
                                    s_status=" IPTABLE " + IPTable;
                                    Log.d("Miga", "IPTABLE: " + IPTable);
                                    // test end
                                    //sendbackmessage = "YES:" + temp;
                                } else {
                                    IPTable.put(recWiFiIpAddr, 0);
                                    //for test
                                    s_status=" IPTABLE " + IPTable;
                                    Log.d("Miga", "IPTable: " + IPTable);
                                    // test end
                                    //sendbackmessage = "NO:X";
                                }
                                //回傳訊息給client(使用unicast)
                                /*out = new PrintWriter(sc.getOutputStream());
                                out.println(sendbackmessage);
                                Log.d("Miga", "Send back the message: " + sendbackmessage);
                                out.flush();
                                out.close();
                                sc.close();*/
                            }
                        }
                    }

                }
                //ss = new ServerSocket(IP_port_for_IPModify);
                /*while (true) {

                    sc = ss.accept();
                    in = new BufferedReader(new InputStreamReader(sc.getInputStream()));
                    recMessagetemp = in.readLine();
                    recMessage = recMessagetemp.split("#");//[0]: WifiIP , [1]:WiFiApName(SSID)
                    WiFiIpAddr=recMessage[0];
                    if(!(recMessage[1].equals(WiFiApName))) {//接收到的不是自己,則可以進行IP判斷
                        Log.d("Miga", "Receive IP: " + WiFiIpAddr);
                        if (IPTable.containsKey(WiFiIpAddr)) {
                            //temp = WiFiIpAddr;
                            for (i = 2; i < 254; i++) {
                                temp = "192.168.49." + String.valueOf(i);
                                if (IPTable.containsKey(temp) == false) break;
                            }
                            IPTable.put(temp, 0);
                            //for test
                            s_status=" IPTABLE " + IPTable;
                            Log.d("Miga", "IPTABLE: " + IPTable);
                            // test end
                            message = "YES:" + temp;
                        } else {
                            IPTable.put(WiFiIpAddr, 0);
                            //for test
                            s_status=" IPTABLE " + IPTable;
                            Log.d("Miga", "IPTable: " + IPTable);
                            // test end
                            message = "NO:X";
                        }
                        out = new PrintWriter(sc.getOutputStream());
                        out.println(message);
                        Log.d("Miga", "Send the message: " + message);
                        out.flush();
                        out.close();
                        in.close();
                        sc.close();
                    }
                }*/
            } catch (SocketException e) {
                e.printStackTrace();
                Log.d("Miga", "CollectIP_server Socket exception" + e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Miga", "CollectIP_server IOException" + e.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "CollectIP_server Exception" + e.toString());
            } finally {
                try {

                    if (out != null) {
                        out.close();
                    }
                    if (sc != null) {
                        sc.close();
                    }
                    if (ss != null) {
                        ss.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d("Miga","CollectIP_Server Exception:"+e);
                }
            }
        }
    }


    public class SendWiFiIpAddr extends Thread{
        MulticastSocket multicsk;//Miga20180129
        DatagramPacket msgPkt;//Miga
        String message;

        public void run() {
            try {
                //while(true) {
                    //Thread.sleep(20000);
                    multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                    multicsk = new MulticastSocket(6789);
                    /*if(WiFiIpAddr==null||WiFiIpAddr==""){
                        WiFiIpAddr = "fromself";//WiFiIpAddr
                    }*/
                    message =  WiFiApName+"#" +WiFiIpAddr;
                    msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6789);
                    multicsk.send(msgPkt);
                    //Log.v("Miga", "(Proactive)multicsk send message:" + message);
                    //s_status = "(Proactive)multicsk send message" + message;
                    //Thread.sleep(5000);

                //}
            }catch (Exception e){
                Log.v("Miga", "SendWiFiIpAddr Exception:" + e);
            }

        }
    }
    // Miga, For multicast ------------Start-------------
    public void allowMulticast(){
        // WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        if(wifi!=null){
            WifiManager.MulticastLock lock=wifi.createMulticastLock("Miga");
            lock.acquire();
            //Log.d("Miga", "multicast lock open!");
        }
    }
    //For multicast socket
    public void getP2P0() throws SocketException{
        //receive時要取得p2p0,用p2p0去加入multicast group.否則會跳Exception
        enumeration = NetworkInterface.getNetworkInterfaces();
        //Log.d("Miga", "Enter getP2P0()" );
        while (enumeration.hasMoreElements()) {
            p2p0 = enumeration.nextElement();
            if (p2p0.getName().equals("p2p0")) {
                //there is probably a better way to find ethernet interface
                //Log.d("Miga", "getP2P():FindP2P0" );
                break;
            }
        }
    }
    //加入接收IPTable的multicast, 於Initial()呼叫
    public void JoinUpdateIPMultiCst(){

        //MulticastSocket recvSocket;//Miga
        DatagramPacket msgPkt;//Miga
        boolean isJoin=false;
        try {
            getP2P0();
            if(!isJoin) {
                recvSocket = new MulticastSocket(6789);
                multicgroup = InetAddress.getByName("224.0.0.3"); //客戶客戶端將自己加入到指定的multicast group中,這樣就能夠收到來自該組的消息
                //clientSocket.joinGroup(multicgroup);
                recvSocket.joinGroup(new InetSocketAddress(multicgroup, 6789), p2p0);//用p2p0 interface來接收muticast pkt
                Log.d("Miga", "I join iptable multicast group success" + multicgroup);
                s_status="I join multicast group!!!!!!!!";
                isJoin = true;//已加入multicast group
                //開啟接收multicast的thread
                if (t_collectIP == null) {
                    t_collectIP = new CollectIP_server();
                    t_collectIP.start();
                }
            }
        }catch (Exception e){
            Log.d("Miga", "JoinUpdateIPMultiCst Exception:" + e);
        }
    }
    //加入接收PeerTable的multicast, 於Initial()呼叫
    public void JoinUpdatePeerMultiCst(){

        try {
            //getP2P0();

            recvPeerSocket = new MulticastSocket(6790);
            multicgroup = InetAddress.getByName("224.0.0.3"); //客戶客戶端將自己加入到指定的multicast group中,這樣就能夠收到來自該組的消息
            //clientSocket.joinGroup(multicgroup);
            recvPeerSocket.joinGroup(new InetSocketAddress(multicgroup, 6790), p2p0);//用p2p0 interface來接收muticast pkt
            Log.d("Miga", "I join peertable multicast group success" + multicgroup);
            //s_status="I join multicast group!!!!!!!!";

        }catch (Exception e){
            Log.d("Miga", "JoinUpdatePeerMultiCst Exception:" + e);
        }
    }
    // Miga, For multicast ------------End-------------

    //20180314 可成功使用multi, uni接收並relay(GO) pkt出去
    public class Receive_peer_count extends Thread {
        private byte[] lMsg,buf;
        private DatagramPacket receivedp, senddp;
        private DatagramSocket sendds;
        private Iterator iterator;
        private String message, tempkey;
        private String[] temp;
        private MulticastSocket multicsk;//Miga20180312


        private String recMessagetemp,recWiFiIpAddr;
        private String[] recMessage;
        private int i;
        private DatagramPacket msgPkt;//Miga


        public void run() {

            try{
                lMsg = new byte[8192];
                receivedp = new DatagramPacket(lMsg, lMsg.length);//接收到的message會存在IMsg
                receiveds = null;
                receiveds = new DatagramSocket(IP_port_for_peer_counting);//接收的Socket

                //Miga add multicast 20180313 (接收使用multicast)
                //buf=new byte[8192];

                while(true){
                    if(IsP2Pconnect) {
                        if (receivedp != null) {
                            if (ROLE == RoleFlag.GO.getIndex()) {
                                //multicast
                                recvPeerSocket.receive(receivedp);//recvPeerSocket_ port:6790
                                message = new String(lMsg, 0, receivedp.getLength());//將接收到的IMsg轉換成String型態
                                //Log.d("Miga", "I got message from multicast" + message);
                                //s_status = "I got message from multicast" + message;
                            } else if (ROLE == RoleFlag.CLIENT.getIndex()) {
                                //unicast
                                receiveds.receive(receivedp);//把接收到的data存在receivedp.
                                message = new String(lMsg, 0, receivedp.getLength());//將接收到的IMsg轉換成String型態
                                //Log.d("Miga", "I got message from unicast" + message);
                                //s_status = "I got message from unicast" + message;
                            }

                            temp = message.split("#");//將message之中有#則分開存到tmep陣列裡;message = WiFiApName + "#" + Cluster_Name + "#" + "5";
                            if (temp[0] != null && temp[1] != null && temp[2] != null && WiFiApName != null) {
                                if (Newcompare(temp[0], WiFiApName) != 0) {//接收到的data和此裝置的SSID不同; 若A>B則reutrn 1
                                    // TTL -1
                                    temp[2] = String.valueOf(Integer.valueOf(temp[2]) - 1);//經過一個router因此-1
                                    // update peer table
                                    if (Newcompare(temp[1], Cluster_Name) == 0) {//相同Cluster_Name
                                        PeerTable.put(temp[0], 10);//填入收到data的SSID(WiFiApName)
                                        Log.v("Miga", "PeerTable:" + PeerTable);
                                        s_status = "PeerTable:" + PeerTable;
                                    }
                                    // relay packet
                                    if (Integer.valueOf(temp[2]) > 0) {
                                        message = temp[0] + "#" + temp[1] + "#" + temp[2];
                                        sendds = null;
                                        sendds = new DatagramSocket();
                                        // unicast
                                        iterator = IPTable.keySet().iterator();
                                        while (iterator.hasNext()) {
                                            tempkey = iterator.next().toString();
                                            senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                    InetAddress.getByName(tempkey), IP_port_for_peer_counting);
                                            sendds.send(senddp);
                                            //Log.d("Miga", "(Relay)Send the message: " + message + " to " + tempkey);
                                            //s_status = "State : (Relay)Send the message: " + message + " to " + tempkey;

                                        }


                                        sendds.close();
                                    }
                                }
                            }
                        }
                    }

                }


            }catch (SocketException e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_peer_count Socket exception" + e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_peer_count IOException" + e.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_peer_count Exception" + e.toString());
            } finally {
                if (receiveds != null) {
                    receiveds.close();
                    Log.d("Miga", "Receive_peer_count receiveds is close");
                }
                if (sendds != null) {
                    sendds.close();
                    Log.d("Miga", "Receive_peer_count sendds is close");
                }
            }
/*            lMsg = new byte[8192];
            receivedp = new DatagramPacket(lMsg, lMsg.length);
            receiveds = null;

            try {
                receiveds = new DatagramSocket(IP_port_for_peer_counting);
                while (true) {
                    //for testing
                    //ds.setSoTimeout(100000);
                    receiveds.receive(receivedp);
                    message = new String(lMsg, 0, receivedp.getLength());
                    temp = message.split("#");
                    if (temp[0] != null && temp[1] != null && temp[2] != null && WiFiApName != null) {
                        // 0: source SSID     1: cluster name    2: TTL
                        if (Newcompare(temp[0], WiFiApName) != 0) {
                            // TTL -1
                            temp[2] = String.valueOf(Integer.valueOf(temp[2]) - 1);
                            // update peer table
                            if (Newcompare(temp[1], Cluster_Name) == 0) {
                                PeerTable.put(temp[0], 5);
                            }
                            // relay packet
                            if (Integer.valueOf(temp[2]) > 0) {
                                message = temp[0] + "#" + temp[1] + "#" + temp[2];
                                sendds = null;
                                sendds = new DatagramSocket();
                                // broadcast
                                senddp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName("192.168.49.255"), IP_port_for_peer_counting);
                                sendds.send(senddp);
                                Log.d("Leaf0419", "(Relay)Send the message: " + message + " to broadcast");
                                // unicast
                                iterator = IPTable.keySet().iterator();
                                while (iterator.hasNext()) {
                                    tempkey = iterator.next().toString();
                                    senddp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(tempkey), IP_port_for_peer_counting);
                                    sendds.send(senddp);
                                    Log.d("Leaf0419", "(Relay)Send the message: " + message + " to " + tempkey);
                                }
                                sendds.close();
                            }

                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                if (receiveds != null) {
                    receiveds.close();
                }
                if (sendds != null) {
                    sendds.close();
                }
            }*/
        }
    }

    public int count_peer() {
        //By Leaf
        int result = 0;
        Iterator iterator = PeerTable.keySet().iterator();
        String tempkey;
        while (iterator.hasNext()) {
            tempkey = iterator.next().toString();
            result++;
        }
        Log.d("Miga", "The peer count result is : " + result);
        //By Serval Mesh
        /*try {
            result = ServalDCommand.peerCount();
        }catch (ServalDFailureException e) {
            e.printStackTrace();
        }*/
        return result;
    }

    public class Send_peer_count extends Thread {
        private DatagramPacket dp;
        private DatagramSocket sendds;
        private Iterator iterator;
        private String message, tempkey;
        MulticastSocket multicsk;//Miga20180313
        DatagramPacket msgPkt;//Miga

        public void run() {
            try{
                sendds = null;
                sendds = new DatagramSocket();
                while(true) {
                    if (IsP2Pconnect) {
                        message = WiFiApName + "#" + Cluster_Name + "#" + "5";// 0: source SSID 1: cluster name 2: TTL

                        // unicast
                        iterator = IPTable.keySet().iterator();//IPTable的keySet為許多IP所組成
                        while (iterator.hasNext()) {
                            tempkey = iterator.next().toString();
                            dp = new DatagramPacket(message.getBytes(), message.length(),
                                    InetAddress.getByName(tempkey), IP_port_for_peer_counting);
                            sendds.send(dp);//一一傳送給IPTable內的所有IP
                            //Log.v("Miga", "I send unicast message:" + message);
                            //s_status="I send unicast message:"+message;

                        }

                        //multicast
                        if (mConnectivityManager != null) {
                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                            if (mNetworkInfo.isConnected()) {
                                multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                                multicsk = new MulticastSocket(6790);//6790: for peertable update
                                msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6790);
                                multicsk.send(msgPkt);
                                //Log.v("Miga", "multicsk send message:" + message);
                                //s_status = "multicsk send message" + message;
                            }
                        }
                        //下面的update感覺是為了確保peer還在 group內所需,
                        //因為若peer不在了,則他的值會在每一次執行這個thread時,所對應的value會一直遞減.
                        //若peer還在group內,則他會藉由再傳送過來的peer資料,將所對應的value更新為10 (receive_peer_count)
                        // update peer table
                        iterator = PeerTable.keySet().iterator();
                        while (iterator.hasNext()) {
                            tempkey = iterator.next().toString();
                            PeerTable.put(tempkey, PeerTable.get(tempkey) - 1);//一一把PeerTable內對應到的SSID的value-1
                            if (PeerTable.get(tempkey) <= 0) {//value值
                                PeerTable.remove(tempkey);//將此SSID移除
                                Log.v("Miga", "remove PeerTable("+tempkey+"):" + PeerTable);
                                s_status = "remove PeerTable("+tempkey+"):" + PeerTable;
                            }
                        }
                        //Log.v("Miga", "PeerTable:" + PeerTable);
                        //s_status = "PeerTable:" + PeerTable;
                    }
                    Thread.sleep(1000);
                }
            } catch (SocketException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_peer_count Socket exception" + e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_peer_count IOException" + e.toString());
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_peer_count InterruptedException" + e.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Send_peer_count other exception" + e.toString());
            } finally {
                if (sendds != null) {
                    sendds.close();
                    Log.d("Miga", "Send_peer_count sendds is close");
                }
            }
            /*try {
                sendds = null;
                sendds = new DatagramSocket();
                while (true) {
                    try {
                        if (Isconnect) {
                            message = WiFiApName + "#" + Cluster_Name + "#" + "5";// 0: source SSID 1: cluster name 2: TTL
                            // broadcast
                            dp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName("192.168.49.255"), IP_port_for_peer_counting);
                            sendds.send(dp);
                            Log.d("Leaf0419", "(Proactive)Send the message: " + message + " to broadcast");
                            // unicast
                            iterator = IPTable.keySet().iterator();
                            while (iterator.hasNext()) {
                                tempkey = iterator.next().toString();
                                dp = new DatagramPacket(message.getBytes(), message.length(), InetAddress.getByName(tempkey), IP_port_for_peer_counting);
                                sendds.send(dp);
                                Log.d("Leaf0419", "(Proactive)Send the message: " + message + " to " + tempkey);
                            }

                            //update peer table
                            iterator = PeerTable.keySet().iterator();
                            while (iterator.hasNext()) {
                                tempkey = iterator.next().toString();
                                PeerTable.put(tempkey, PeerTable.get(tempkey) - 1);
                                if (PeerTable.get(tempkey) <= 0) {
                                    PeerTable.remove(tempkey);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Thread.sleep(500);
                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            } finally {
                if (sendds != null) {
                    sendds.close();
                }
            }*/
        }
    }

    @Override
    public void onCreate() {
        // Leaf0818
        Log.d("Leaf1110", "Control_onCreate()");
        this.app = (ServalBatPhoneApplication) this.getApplication();
        PowerManager pm = (PowerManager) app
                .getSystemService(Context.POWER_SERVICE);
        cpuLock = pm
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Services");
        super.onCreate();

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);//Edit by Miga 20180205 , eclipse ver:(WifiManager)getSystemService(Context.WIFI_SERVICE);
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        registerReceiver(receiver_scan = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                wifi_scan_results = wifi.getScanResults();
                result_size = wifi_scan_results.size();
                wifiScanCheck = true;
                //Log.d("Miga", "State: detecting gateway, get the scan result" + wifi_scan_results.toString());
            }
        }, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));


        // Experiment
        NumRound = 1;
        sleep_time = 0;
        total_time = 0;
        //start_time = Calendar.getInstance().getTimeInMillis();

        //Miga
        start_time=0;
        this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        //this.registerReceiver(this.mPeerInfoReceiver, new IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION));//Receiver，當peer數量改變時則近來，WIFI_P2P_PEERS_CHANGED_ACTION
        //Miga multicast
        allowMulticast();
        // Get Go Info
        if (initial == null) {
            initial = new Initial();
            initial.start();
        }


        registerReceiver(receiver_peer = new BroadcastReceiver() {//註冊用來接收peer discovery的peer數量變化的結果
            @Override
            public void onReceive(Context c, Intent intent) {
                if (manager != null) {
                    manager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
                        @Override
                        public void onPeersAvailable(WifiP2pDeviceList peers) {
                            //Log.d("Miga",String.format("PeerListListener: %d peers available, updating device list", peers.getDeviceList().size()));
                            discoverpeernum = peers.getDeviceList().size();//取得發現附近裝置的數量
                            // DO WHATEVER YOU WANT HERE
                            // YOU CAN GET ACCESS TO ALL THE DEVICES YOU FOUND FROM peers OBJECT

                        }
                    });
                }
            }
        }, new IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION));

        Collect_record = new ArrayList<Step1Data_set>();// Wang
        //getBatteryCapacity();



    }
    //Miga for power
    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            power_level = level;
        }
    };
    //Miga
    public void getBatteryCapacity() {
        Object mPowerProfile_ = null;
        final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";
        try {
            mPowerProfile_ = Class.forName(POWER_PROFILE_CLASS)
                    .getConstructor(Context.class).newInstance(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            double batteryCapacity = (Double) Class
                    .forName(POWER_PROFILE_CLASS)
                    .getMethod("getAveragePower", java.lang.String.class)
                    .invoke(mPowerProfile_, "battery.capacity");
            //s_status="batteryCapacity:"+batteryCapacity+"mAh";
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //Miga for device initial create
    public class  Initial extends Thread{
        public void run() {
            manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d("Miga", "initial createGroup Success");
                    //Isconnect = true;
                }
                @Override
                public void onFailure(int error) {
                    Log.d("Miga", "initial createGroup onFailure");
                }
            });
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null) {
                        GOpasswd = group.getPassphrase();
                        WiFiApName = group.getNetworkName();
                        Cluster_Name = WiFiApName;
                        GO_mac = group.getOwner().deviceAddress.toString();
                        STATE = StateFlag.ADD_SERVICE.getIndex();//1

                        ROLE = RoleFlag.NONE.getIndex();
                        Log.d("Miga", "State: Initial Complete , SSID : " + WiFiApName + " Cluster_Name : " + Cluster_Name);
                    }
                }
            });
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            Log.d("Miga", "Initial group success!");
            //updatePeerCount(peerCount);
            //s_status = "State: Initial Complete : time : " +( (Calendar.getInstance().getTimeInMillis() - initial_start_time)/1000 + " SSID : " + WiFiApName + " Cluster_Name : " + Cluster_Name)+
            //		" ROLE : " + ROLE + " IPTABLE " + IPTable;


            JoinUpdateIPMultiCst();//加入multicast group,為了讓GO來接收連上他的client向他傳送的ip address(ip用來更新IPTable)
            JoinUpdatePeerMultiCst();//加入multicast group,為了讓所有member來更新peer table
            s_status = "State: Initial Complete : " + " SSID : " + WiFiApName + " Cluster_Name : " + Cluster_Name ;


        }
    }
    //Miga 判斷這支手機的android版本能不能寫入Log Files
    public boolean CanWriteLogFiles(){
        /*if(Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1)// 現在SDK版本 < 22的話則進入寫LOG , 只有Android 5.0.2版本可以成功寫log : 818b, f418, 3c06
            return true;
        else*/
            return false;
    }
    //Miga for discoverPeers, 在進行device彼此交換資料之前, 先去得到此裝置周圍裝置數量有幾個 (取得peer數)
    public void peerdiscover(){
        manager.stopPeerDiscovery(channel,new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                //Log.d("Miga", "stopPeerDiscovery onSuccess");
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        //Log.d("Miga", "discoverPeers onSuccess");
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Log.d("Miga", "discoverPeers onFailure");
                    }
                });
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d("Miga", "stopPeerDiscovery onFailure");
            }
        });
    }

    @Override
    public void onDestroy() {
        Log.d("Leaf1110", "Control Services Destroy");
        new Task().execute(State.Off);
        app.controlService = null;
        serviceRunning = false;
        if (receiver != null)
            unregisterReceiver(receiver);
        if (receiver_scan != null)
            unregisterReceiver(receiver_scan);
        isRunning = false;
        if (t_findPeer != null)
            t_findPeer.interrupt();
        if (t_checkGO != null)
            t_checkGO.interrupt();
        if (t_reconnection_wifiAp != null)
            t_reconnection_wifiAp.interrupt();
        if (t_collectIP != null)
            t_collectIP.interrupt();
        if (t_send_peer_count != null)
            t_send_peer_count.interrupt();
        if (t_receive_peer_count != null)
            t_receive_peer_count.interrupt();

        // <aqua0722>
        if (t_native != null)
            t_native.interrupt();
        if (t_register != null)
            t_register.interrupt();

        t_native = null;
        t_register = null;
        // </aqua0722>
        receiver = null;
        t_findPeer = null;
        t_checkGO = null;
        t_reconnection_wifiAp = null;
        t_collectIP = null;
        t_send_peer_count = null;
        t_receive_peer_count = null;
        if (receiveds != null)
            receiveds.close();
        try {
            if (sc != null)
                sc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (manager != null && serviceInfo != null && serviceRequest != null) {
            manager.removeLocalService(channel, serviceInfo, null);
            manager.removeServiceRequest(channel, serviceRequest, null);
            manager.clearLocalServices(channel, null);
            manager.clearServiceRequests(channel, null);
        }

        // EditLeaf0802
        try {
            if (ss != null)
                ss.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Leaf0818
        Log.d("Leaf1110", "Control Services StartCommand");
        State existing = app.getState();
        // Don't attempt to start the service if the current state is invalid
        // (ie Installing...)
        if (existing != State.Off && existing != State.On) {
            Log.v("Control", "Unable to process request as app state is "
                    + existing);
            return START_NOT_STICKY;
        }
        if (receiver == null) {
            receiver = new AutoWiFiDirect(manager, channel, this, Isconnect, myDeviceName);
            registerReceiver(receiver, intentFilter);
        }
        isRunning = true;
        if (t_reconnection_wifiAp == null) {
            t_reconnection_wifiAp = new Reconnection_wifiAp();
            t_reconnection_wifiAp.start();
        }

        // Following two threads is for counting peers by our module,
        // since Serval Mesh has already supported a similar function,
        // you can decide whether utilized following code
        if (t_send_peer_count == null) {
            t_send_peer_count = new Send_peer_count();
            t_send_peer_count.start();
        }
        if (t_receive_peer_count == null) {
            t_receive_peer_count = new Receive_peer_count();
            t_receive_peer_count.start();
        }


        // </aqua0722>
        new Task().execute(State.On);
        serviceRunning = true;

        peerdiscover();//進行discoverPeers,一開始就先去搜尋附近有誰, Miga add 0226
        //STATE = StateFlag.WAITING.getIndex();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    public class MyBinder extends Binder {
        public Control getService() {
            return Control.this;
        }
    }


    // Following code is for setting static IP address
    private boolean setIpWithTfiStaticIp(String IP) {
        WifiConfiguration wifiConfig = null;
        WifiInfo connectionInfo = wifi.getConnectionInfo();

        List<WifiConfiguration> configuredNetworks = wifi.getConfiguredNetworks();
        for (WifiConfiguration conf : configuredNetworks) {
            if (conf.networkId == connectionInfo.getNetworkId()) {
                wifiConfig = conf;
                break;
            }
        }
        try {
            setIpAssignment("STATIC", wifiConfig);
            setIpAddress(InetAddress.getByName(IP), 24, wifiConfig);
            wifi.updateNetwork(wifiConfig); // apply the setting
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void setIpAssignment(String assign, WifiConfiguration wifiConf)
            throws SecurityException, IllegalArgumentException,
            NoSuchFieldException, IllegalAccessException {
        setEnumField(wifiConf, assign, "ipAssignment");
    }


    private static void setIpAddress(InetAddress addr, int prefixLength,
                                     WifiConfiguration wifiConf) throws SecurityException,
            IllegalArgumentException, NoSuchFieldException,
            IllegalAccessException, NoSuchMethodException,
            ClassNotFoundException, InstantiationException,
            InvocationTargetException {
        Object linkProperties = getField(wifiConf, "linkProperties");
        if (linkProperties == null)
            return;
        Class<?> laClass = Class.forName("android.net.LinkAddress");
        Constructor<?> laConstructor = laClass.getConstructor(new Class[]{
                InetAddress.class, int.class});
        Object linkAddress = laConstructor.newInstance(addr, prefixLength);


        ArrayList<Object> mLinkAddresses = (ArrayList<Object>) getDeclaredField(
                linkProperties, "mLinkAddresses");
        mLinkAddresses.clear();
        mLinkAddresses.add(linkAddress);
    }

    private static Object getField(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getField(name);
        Object out = f.get(obj);
        return out;
    }

    private static Object getDeclaredField(Object obj, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        Object out = f.get(obj);
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setEnumField(Object obj, String value, String name)
            throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
        Field f = obj.getClass().getField(name);
        f.set(obj, Enum.valueOf((Class<Enum>) f.getType(), value));
    }


    //Miga for 取得連上裝置的IPAddress
    private byte[] getLocalIPAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        if (inetAddress instanceof Inet4Address) { // fix for Galaxy Nexus. IPv4 is easy to use :-)
                            return inetAddress.getAddress();
                        }
                        //return inetAddress.getHostAddress().toString(); // Galaxy Nexus returns IPv6
                    }
                }
            }
        } catch (SocketException ex) {
            //Log.e("AndroidNetworkAddressFactory", "getLocalIPAddress()", ex);
        } catch (NullPointerException ex) {
            //Log.e("AndroidNetworkAddressFactory", "getLocalIPAddress()", ex);
        }
        return null;
    }

    private String getDottedDecimalIP(byte[] ipAddr) {
        //convert to dotted decimal notation:
        String ipAddrStr = "";
        for (int i=0; i<ipAddr.length; i++) {
            if (i > 0) {
                ipAddrStr += ".";
            }
            ipAddrStr += ipAddr[i]&0xFF;
        }
        return ipAddrStr;
    }

}
