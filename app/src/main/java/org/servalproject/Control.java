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
import android.os.Handler;
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
import java.io.StringWriter;
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
import java.util.Timer;
import java.util.TimerTask;
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
    private static int IP_port_for_IPSave = 2555;
    private static int IP_port_for_peer_counting = 2666;
    private static int IP_port_for_cluster_name = 2777;
    private static int IP_port_for_can_connect = 2888;//Step2
    private static int IP_port_for_client_ack = 2999;//Step2 client回傳p2p是否成功
    private ServerSocket ss = null;
    private Map<String, Integer> IPTable = new HashMap<String, Integer>();
    private Map<String, Integer> PeerTable = new HashMap<String, Integer>();
    private Socket sc; // for CollectIP_server
    private DatagramSocket receiveds; // for receive_peer_count
    private DatagramSocket receiveds_cn; // for receive_cluster_name
    private int NumRound;
    //private boolean is;


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
    private String GO_mac,GO_SSID;
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
    private MulticastSocket recvCNSocket;//Miga , for Cluster Name
    private MulticastSocket recvCCSocket;//Miga , for CanIConnect,確認是否可在step2進行連線
    private InetAddress multicgroup;
    //for multicast socket End add by Miga
    private boolean IsConnecting=false;
    private String WiFiIpAddr = null;
    private String GO_ClusterName=null;
    private boolean IsP2Pconnect = false;//Miga 20180314 連線後不論是GO或是CLIENT都會將這個值設為True, send/receive_peer_count 會使用到
    private int detect_run = 0;//用來讓go的裝置來++執行WiFi_Connect的次數
    private int pre_peer_count = 0;
    private boolean IsReceiveGoInfo= false;
    private String Time_Stamp="";
    private boolean IsInitial=false;
    private Thread t_Send_Cluster_Name = null;
    private Thread t_Receive_Cluster_Name = null;
    private int pre_ROLE=-1;

    //For receive_peer_count
    private byte[] lMsg_pc;
    private DatagramPacket receivedpkt_pc;
    private DatagramSocket receivedskt_pc;//unicast
    private String RecvMsg_pc="";
    private Thread t_Receive_peer_count_multi = null;
    private Thread t_Receive_peer_count_uni = null;
    //For receive_cluster_name
    private byte[] lMsg_cn;
    private DatagramPacket receivedpkt_cn;
    private DatagramSocket receivedskt_cn;//unicast
    private String RecvMsg_cn="";
    private Thread t_Receive_Cluster_Name_multi = null;
    private Thread t_Receive_Cluster_Name_uni = null;
    //For Step2 judgment
    private boolean IsManual = false;
    //For Step2
    private String Choose_Cluster_Name,PreClusterName="";
    private int Before_Step2_RunT=0;//自動連線在進入step2之前,會先跑個2次,確保step1已完成.
    static public boolean Step2Auto = false;
    static public long step2_start_time;
    private long step2_total_time, step2_sleep_time=0;
    private boolean hasclients;
    //For Step2 該group目前要進行連線的CN名稱
    private Thread t_CanConnect = null;
    private Thread t_CanConnect_uni = null;
    private Thread t_CanConnect_multi = null;
    private byte[] lMsg_cc;
    private DatagramPacket receivedpkt_cc;
    private DatagramSocket receivedskt_cc;//unicast
    private String RecvMsg_cc="";
    private Map<String, Integer> CNTable = new HashMap<String, Integer>();
    private boolean IsStep2TimeStart = false; // for step2 time 計算
    private boolean IsGOConnecting = false;//用來表示GO是不是正在嘗試連線
    private boolean IsClientConnecting = false;//用來表示Client是不是正在嘗試連線

    private BroadcastReceiver receiver_device_change = null;
    private String thisDeviceName;
    //For controller 20180508
    static public boolean ControllerAuto = false;
    private Map<String, Integer> CandidateControllerTable = new HashMap<String, Integer>();//用來讓每個裝置儲存Cluster內其他裝置的SSID及剩餘電量，主要是拿來進行Controller的決定。
    private Thread t_Conroller_Thread = null;
    private boolean IsNeighborCollect = false;//用來判斷是否已蒐集完鄰居的DATA
    private String NeighborList="";//用來儲存自己neighbor有誰
    private Integer NeighborListNum=0;//用來儲存自己鄰居有幾個
    private boolean IsController = false;//判斷自己是不是Controller
    private Thread t_Send_info = null;
    private Thread t_Receive_info = null;
    private Thread t_Receive_info_multi = null;
    private Thread t_Receive_info_uni = null;
    private static int IP_port_controller_collect = 2333;//
    private MulticastSocket recvControllerSocket;//Miga
    //For receive_info
    private byte[] lMsg_cinfo;
    private DatagramPacket receivedpkt_cinfo;
    private DatagramSocket receivedskt_cinfo;//unicast
    private String RecvMsg_cinfo="";
    private Integer TotaldevicesNum=0;//用來儲存總共在這個cluster內有幾個裝置，主要是給controller進行判斷說資料是否都已蒐集完全
    private boolean IsReceiveFromCon = false;//用來判斷是否接收到從Controller傳送過來的新連線資訊，主要是要讓Receive_info/Send_info中斷
    private boolean IsReceiveMyself = false;//用來判斷是否已從Controller那邊收到自己的新的連線
    private boolean IsSecondRoundOver = false;//用來判斷第二階段是否已完畢
    //For controller send new connect info
    private Thread t_Send_info_new_connect = null;
    private Thread t_Receive_info_new_connect = null;
    private Thread t_Receive_info_multi_new_connect = null;
    private Thread t_Receive_info_uni_new_connect = null;
    private static int IP_port_controller_new_connect = 2222;//
    private MulticastSocket recvControllerNewConnectSocket;//Miga
    private byte[] lMsg_cinfo_nwconnect;
    private DatagramPacket receivedpkt_cinfo_nwconnect;
    private DatagramSocket receivedskt_cinfo_nwconnect;//unicast
    private String RecvMsg_cinfo_nwconnect="";
    private String MyNewConnectInfo="";//用來儲存從controller那裡接收到的新的連線資訊
    private boolean p2p_connect_check = false;
    private boolean IsNewConnection = false;//用來判斷是否已開始進行NewConnection
    private boolean IsControllerOpenFirstRond = false;//用來讓這個thread在Controller開啟第一輪後先停止，等到下一次要進行controller時在開啟
    private boolean IsNewConnectionComplete = false;//判斷新的連線是否已完成
    private int RunNewConnectionTime = 0;//用來儲存執行次數
    private Thread t_New_Connection_Func = null;

    //For Controller Start
    private List<CandidateController_set> CandController_record;
    //每個裝置都會有，用來儲存所有device的SSID及電量，用來選出controller的
    public class CandidateController_set{
        private String SSID;
        private String POWER;
        public CandidateController_set(String SSID,String POWER){
            this.SSID = SSID;
            this.POWER = POWER;
        }
        String getSSID() {
            return this.SSID;
        }
        String getPOWER() {
            return this.POWER;
        }

        public String toString() {
            return this.SSID + " " + this.POWER;
        }

        public int compareTo(CandidateController_set data) {
            String SSID = data.getSSID();
            String POWER = data.getPOWER();
            if(Integer.valueOf(this.POWER)<Integer.valueOf(POWER)){
                return 1;
            }else if(Integer.valueOf(this.POWER)>Integer.valueOf(POWER)){
                return -1;
            }
            if (this.SSID.compareTo(SSID) < 0) {
                return 1;
            } else if (this.SSID.compareTo(SSID) > 0) {
                return -1;
            }
            return 0;
        }

        public boolean equals(Object object) {//判斷SSID是不是一樣的，若是表示已經有存了
            CandidateController_set other = (CandidateController_set) object;
            if (this.SSID.equals(other.SSID) == true)
                return true;

            return false;
        }
    }
    private List<CandidateController_set> CandNeighbor_record;//從鄰居當中挑出一個電量最高的

    private List<Neighbor_set> Neighbor_record;
    //每個裝置都會有，用來儲存自己這個裝置的鄰居SSID及密碼，用來接收Controller指令後，進行抓出連上該device的密碼
    public class Neighbor_set{
        private String SSID;
        private String PSW;
        public Neighbor_set(String SSID,String PSW){
            this.SSID = SSID;
            this.PSW = PSW;
        }
        String getSSID() {
            return this.SSID;
        }
        String getPSW() {
            return this.PSW;
        }

        public String toString() {
            return this.SSID + " " + this.PSW;
        }
        public boolean equals(Object object) {//判斷SSID是不是一樣的，若是表示已經有存了
            Neighbor_set other = (Neighbor_set) object;
            if (this.SSID.equals(other.SSID) == true)
                return true;

            return false;
        }
    }

    private List<ControllerData_set> Controller_record;//用來儲存所有資料
    private List<ControllerData_set> first_round_Controller_record;//用來儲存第一階段的資料(排序過後的)

    private List<ControllerData_set> second_round_Controller_record;//用來儲存第二階段的資料
    private List<ControllerData_set> Final_Controller_record;//用來儲存最終連線的資料

    public class ControllerData_set{
        private String SSID;
        private String Neighbor;
        private String NeighborNum;
        private String POWER;
        private String ClusterName;
        private String WiFiInterface;
        private String P2PInterface;

        public ControllerData_set(String SSID,String Neighbor,String NeighborNum,String POWER,String ClusterName,String WiFiInterface,String P2PInterface){
            this.SSID = SSID;
            this.Neighbor = Neighbor;
            this.NeighborNum = NeighborNum;
            this.POWER = POWER;
            this.ClusterName = ClusterName;
            this.WiFiInterface = WiFiInterface;
            this.P2PInterface = P2PInterface;
        }
        String getSSID() {
            return this.SSID;
        }
        String getNeighbor() {
            return this.Neighbor;
        }
        String getNeighborNum() {
            return this.NeighborNum;
        }
        String getPOWER() {
            return this.POWER;
        }
        String getClusterName() {
            return this.ClusterName;
        }
        String getWiFiInterface() {
            return this.WiFiInterface;
        }
        String getP2PInterface() { return this.P2PInterface; }

        public String toString() {
            return this.SSID + " "+this.Neighbor+ " "+this.NeighborNum+" "+ this.POWER+ " "+this.ClusterName+ " "+this.WiFiInterface+ " "+this.P2PInterface;
        }

        public boolean equals(Object object) {//判斷SSID是不是一樣的，若是表示已經有存了
            ControllerData_set other = (ControllerData_set) object;
            if (this.SSID.equals(other.SSID) == true)
                return true;

            return false;
        }

        public int FirstRoundcompareTo(ControllerData_set data) {//先根據鄰居數量在根據電量，已少的為優先
            String SSID = data.getSSID();
            String POWER = data.getPOWER();
            String NeighborNum = data.getNeighborNum();
            //少的排前面
            if(Integer.valueOf(this.NeighborNum)>Integer.valueOf(NeighborNum)){
                return 1;
            }else if(Integer.valueOf(this.NeighborNum)<Integer.valueOf(NeighborNum)){
                return -1;
            }
            if(Integer.valueOf(this.POWER)>Integer.valueOf(POWER)){
                return 1;
            }else if(Integer.valueOf(this.POWER)<Integer.valueOf(POWER)){
                return -1;
            }
            if (this.SSID.compareTo(SSID) < 0) {
                return 1;
            } else if (this.SSID.compareTo(SSID) > 0) {
                return -1;
            }
            return 0;
        }
        //最終要傳送出去的String
        public String toString_Final() {
            return this.SSID + "#"+this.Neighbor+ "#"+this.NeighborNum+"#"+ this.POWER+ "#"+this.ClusterName+ "#"+this.WiFiInterface+ "#"+this.P2PInterface;
        }
    }


    //For Controller End


    public enum RoleFlag {
        NONE(0), GO(1), CLIENT(2), BRIDGE(3), HYBRID(4);//BRIDGE就是之前的RELAY, HYBRID:一邊是GO一邊是Client的身分
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
        private String DROLE;
        //private String GO;

        public Step1Data_set(String SSID, String key, String Name, String PEER, String MAC,
                             String POWER, String GroupPEER, String DROLE) {
            this.SSID = SSID;
            this.key = key;
            this.Name = Name;
            this.PEER = PEER;
            this.MAC = MAC;
            this.POWER = POWER;
            this.GroupPEER = GroupPEER;
            this.DROLE = DROLE;
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

        String getROLE() {
            return this.DROLE;
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
            String MAC = data.getMAC();

            /*if (this.PEER.compareTo(PEER) < 0) {
                return 1;
            } else if (this.PEER.compareTo(PEER) > 0) {
                return -1;
            }*/

            if(Integer.valueOf(this.POWER)<Integer.valueOf(POWER)){
                return 1;
            }else if(Integer.valueOf(this.POWER)<Integer.valueOf(POWER)){
                return -1;
            }
            //用STRING比較會有問題!!!
            /*if (this.POWER.compareTo(POWER) < 0) {
                return 1;
            } else if (this.POWER.compareTo(POWER) > 0) {
                return -1;
            }*/

            if (this.MAC.compareTo(MAC) < 0) {
                return 1;
            } else if (this.MAC.compareTo(MAC) > 0) {
                return -1;
            }

            return 0;
        }
    }

    public class Step2Data_set_Comparator implements Comparator {//Step 2.用來multi-group

        public int compare(Object obj1, Object obj2) {
            Step1Data_set data1 = (Step1Data_set) obj1;
            Step1Data_set data2 = (Step1Data_set) obj2;


            if (Integer.valueOf(data1.getGroupPEER()) < Integer.valueOf(data2.getGroupPEER())) {
                return 1;
            } else if (Integer.valueOf(data1.getGroupPEER()) > Integer.valueOf(data2.getGroupPEER())) {
                return -1;
            }

            /*if (Integer.valueOf(data1.getPEER()) < Integer.valueOf(data2.getPEER())) {
                return 1;
            } else if (Integer.valueOf(data1.getPEER()) > Integer.valueOf(data2.getPEER())) {
                return -1;
            }*/
            if(Integer.valueOf(data1.getPOWER())< Integer.valueOf(data2.getPOWER())){
                return 1;
            }else if(Integer.valueOf(data1.getPOWER()) > Integer.valueOf(data2.getPOWER())){
                return -1;//回傳-1表示把data1往前排
            }


            if (data1.getMAC().compareTo(data2.getMAC()) < 0) {
                return 1;
            } else if (data1.getMAC().compareTo(data2.getMAC()) > 0) {
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
        String SSID = null;
        String key = null;
        String Name = null;//Cluster_Name
        String PEER = null;
        String MAC = null;
        String POWER = null;
        String GroupPEER = null;
        String DROLE = null;//對方的ROLE

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
                sleep_time = sleep_time + 1000;
                Log.d("Miga", "WiFi_Connect/Collect data and record size : " + record_set+record_set.size());
                //s_status="state: WiFi_Connect/Receive record size:"+record_set.size();

                if(InfoChangeTime < 3) {//交換次數少於3次
                    STATE = StateFlag.ADD_SERVICE.getIndex();//再去重新加入資料並交換
                    return;
                }
                if(Step2Auto) {
                    if(!IsStep2TimeStart) {//若是還沒進來更新step2_start_time，則近來更新
                        step2_start_time = Calendar.getInstance().getTimeInMillis();
                        IsStep2TimeStart = true;
                    }
                    if (Before_Step2_RunT < 2) {//交換次數少於2次
                        STATE = StateFlag.ADD_SERVICE.getIndex();//再去重新加入資料並交換
                        return;
                    }
                }
                Log.d("Miga", "WiFi_Connect/InfoChangeTime>=3");
                //都沒收集到資料的話根本不會進來這個thread，所以下面的if判斷式應該不用
                /*if (record_set.size() == 0) {//都沒蒐集到其他人的裝置,則重新再去收集資料
                    Log.d("Miga", "WiFi_Connect/Collect data and record size = 0");
                    STATE = StateFlag.ADD_SERVICE.getIndex();
                    InfoChangeTime=0;//InfoChangeTime交換次數歸零
                    return;
                }*/
                //Log.d("Miga", "WiFi_Connect/ROLE:"+ROLE);
                //20180323/26 暫時先註解看看下面的code可不可行  Start
                /*if(ROLE == RoleFlag.NONE.getIndex() || ROLE == RoleFlag.GO.getIndex()) {
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
                            String.valueOf(peercount), GO_mac, String.valueOf(power_level), String.valueOf(grouppeer));

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
                }*/
                //20180323/26 暫時先註解看看下面的code可不可行  End

                //20180323/26 新加入 Start
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
                    DROLE = record.get("DROLE").toString();
                    //GO = record.get("GO").toString();
                    //Log.d("Miga", "WiFi_Connect/Insert data");

                    //20180516 For controller
                    if(ControllerAuto){
                        if(!IsNeighborCollect) {//還沒蒐集過資料才進來，避免重複儲存相同的資料到Neighbor_record
                            Neighbor_set data = new Neighbor_set(SSID, key);
                            if (!Neighbor_record.contains(data)) {
                                Neighbor_record.add(data);
                            }
                            if(NeighborList==""){
                                NeighborList += SSID+"@";
                            }else{
                                if(NeighborList.indexOf(SSID)!=-1) {//有包含
                                }else{
                                    NeighborList += SSID+"@";
                                    //Log.d("Miga","NeighborList: "+NeighborList);
                                }
                            }
                        }
                    }else {//沒有要執行Controller則直接進來這裡；若要執行Controller則不須進來
                        if (!Name.equals(Cluster_Name)) {//只儲存不同Cluster的device資料
                            Step1Data_set data = new Step1Data_set(SSID, key, Name, PEER, MAC, POWER, GroupPEER, DROLE);
                            if (!Collect_record.contains(data)) {
                                Collect_record.add(data);
                            }
                        }
                    }
                }
                if(ControllerAuto){
                    IsNeighborCollect = true;
                    //目的應該只是要print出有收集到哪些data
                    int obj_num = 0;
                    String Collect_contain = "";
                    Neighbor_set tmp;
                    for (int i = 0; i < Neighbor_record.size(); i++) {
                        tmp = (Neighbor_set) Neighbor_record.get(i);
                        Collect_contain = Collect_contain + obj_num + " : " + tmp.toString() + " ";
                        obj_num++;
                    }
                    //Log.d("Miga", "WiFi_Connect/Neighbor_record: " + Collect_contain);
                    NeighborListNum = Neighbor_record.size();
                    Log.d("Miga","WiFi_Connect/NeighborListNum: "+NeighborListNum +", NeighborList: "+NeighborList);
                    return;//資料蒐集結束
                }
                //也加入自己的data
                Step1Data_set self = new Step1Data_set(WiFiApName, GOpasswd, Cluster_Name,
                        String.valueOf(peercount), GO_mac, String.valueOf(power_level), String.valueOf(grouppeer), String.valueOf(ROLE));

                if (!Collect_record.contains(self)) {
                    Collect_record.add(self);
                }

                //20180409 Start For Step2
                if(Step2Auto){
                    if(Before_Step2_RunT >= 2 ){//交換次數少於2次
                        Step2Connection();
                    }
                    return;
                }
                //20180409 End

                if(ROLE == RoleFlag.NONE.getIndex() || ROLE == RoleFlag.GO.getIndex()) {//20180326 只有NONE和GO需去做Step1的排序, GO是為了避免被孤立因此需去做排序, NONE則是為了一開始去組intra group
                    //Collect_record進行排序來選出Group來加入
                    Collections.sort(Collect_record, new Comparator<Step1Data_set>() {
                        public int compare(Step1Data_set o1, Step1Data_set o2) {
                            return o1.compareTo(o2);
                        }
                    });
                    //取出排序第一個的
                    SSID = Collect_record.get(0).getSSID();
                    key = Collect_record.get(0).getkey();
                    Name = Collect_record.get(0).getName();
                    PEER = Collect_record.get(0).getPEER();
                    MAC = Collect_record.get(0).getMAC();
                }
                //目的應該只是要print出有收集到哪些data
                int obj_num = 0;
                String Collect_contain = "";
                Step1Data_set tmp;
                for (int i = 0; i < Collect_record.size(); i++) {
                    tmp = (Step1Data_set) Collect_record.get(i);
                    Collect_contain = Collect_contain + obj_num + " : " + tmp.toString() + " ";
                    obj_num++;
                }
                Log.d("Miga", "WiFi_Connect/Collect records contain (Step1) " + Collect_contain);


                //20180323/26 新加入 End

                if(!IsManual) {//非手動
                    if (ROLE == RoleFlag.NONE.getIndex()) {//還沒檢查並連線過,則進行判斷
                        if (mConnectivityManager != null) {
                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                            if (!mNetworkInfo.isConnected()) {//Wifi還沒連上其他GO,則進行連線
                                if (SSID.equals(WiFiApName)) {
                                    //如果自己是排序第一個的話則不做事,只需等待別人來連線
                                    ROLE = RoleFlag.GO.getIndex();//變為GO
                                    CNTable.put(Cluster_Name,0);//將自己的CN放入CNTable
                                    GO_mac = MAC;
                                    Thread.sleep(1000);
                                    //建立Group
                                    manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                                        @Override
                                        public void onSuccess() {
                                            Isconnect = true;
                                            IsP2Pconnect = true;
                                            Log.d("Miga", "IsP2Pconnect : " + IsP2Pconnect);
                                            s_status = "createGroup time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0)) +  " Round_Num :" + NumRound +"ROLE: "+ROLE;
                                            Log.d("Miga","createGroup time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0)) + " Round_Num :" + NumRound+"ROLE: "+ROLE);
                                        }

                                        @Override
                                        public void onFailure(int error) {
                                            Log.d("Miga", "createGroup onFailure");
                                            STATE = StateFlag.ADD_SERVICE.getIndex();
                                        }
                                    });
                                    STATE = StateFlag.ADD_SERVICE.getIndex();
                                    return;
                                } else {//連上別人
                                    // Try to connect Ap(連上排序第一個or第二個的裝置)
                                    IsConnecting = true;//正在連線中,避免Reconnecting_Wifi繼續執行
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
                                    TryNum = 15;
                                    //20180418註解之後就不會有一直連不上的問題了!!!
                                    /*//檢查我們所要連的GO是否還存在
                                    wifiScanCheck = false;
                                    wifi.startScan();//startScan完畢後，wifi會呼叫SCAN_RESULTS_AVAILABLE_ACTION
                                    long wifiscan_time_start = Calendar.getInstance().getTimeInMillis();
                                    while (wifiScanCheck == false) {//在onCreate時有註冊一個廣播器,專門來徵測wifi scan的結果,wifi.startscan完畢後,wifiScanCheck應該會變為true
                                        Log.d("Miga","wifiScanCheck == false");
                                    }
                                    sleep_time = sleep_time + Calendar.getInstance().getTimeInMillis() - wifiscan_time_start;
                                    Log.d("Miga","wifiScanCheck "+wifiScanCheck);
                                    wifiScanCheck = false;
                                    Log.d("Miga","wifiScanCheck "+wifiScanCheck);
                                    boolean findIsGoExist = false;


                                    Log.d("Miga","wifi_scan_results "+wifi_scan_results.size());
                                    for (int i = 0; i < wifi_scan_results.size(); i++) {//檢查接下來要連上的GO還在不在,wifi_scan_results:會列出掃描到的所有AP
                                        ScanResult sr = wifi_scan_results.get(i);
                                        if (sr.SSID.equals(SSID)) {//去比對每一個掃描到的AP,看是不是我們要連上的GO,若是則將findIsGoExist設為true並跳出for迴圈
                                            findIsGoExist = true;
                                            break;
                                        }
                                    }
                                    Log.d("Miga", "WiFi_Connect/findIsGoExist : " + findIsGoExist);
                                    if (findIsGoExist == false) {//若我們要連的GO不見的話,則回到ADD_SERVICE,重新再收集資料一次.20180307Miga 這裡可能要再想一下後面的流程
                                        Log.d("Miga", "findIsGoExist == false" + findIsGoExist);
                                        STATE = StateFlag.ADD_SERVICE.getIndex();
                                        Log.d("Miga", "STATE=" + STATE);
                                        return;
                                    }
*/
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
                                        Log.d("Miga", "WiFi_Connect/ROLE:" + ROLE + "Cluster_Name:" + Cluster_Name + " wifiIpAddress:" + WiFiIpAddr);
                                        s_status = "state: WiFiIpAddress=" + WiFiIpAddr;
                                        if (!isOpenSWIAThread) {//開啟client傳送wifi ip address thread給GO
                                            if (SendWiFiIpAddr == null) {
                                                SendWiFiIpAddr = new SendWiFiIpAddr();
                                                SendWiFiIpAddr.start();
                                            }
                                            isOpenSWIAThread = true;
                                        }
                                        Thread.sleep(1000);
                                        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                                            @Override
                                            public void onGroupInfoAvailable(WifiP2pGroup group) {
                                                if (group == null) {
                                                    //建立Group
                                                    manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                                                        @Override
                                                        public void onSuccess() {
                                                            Isconnect = true;
                                                            IsP2Pconnect = true;
                                                            Log.d("Miga", "IsP2Pconnect : " + IsP2Pconnect);
                                                            s_status = "createGroup time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0)) +  " Round_Num :" + NumRound +"ROLE: "+ROLE;
                                                            Log.d("Miga","createGroup time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0)) + " Round_Num :" + NumRound+"ROLE: "+ROLE);
                                                        }

                                                        @Override
                                                        public void onFailure(int error) {
                                                            Log.d("Miga", "createGroup onFailure");
                                                            STATE = StateFlag.ADD_SERVICE.getIndex();
                                                        }
                                                    });
                                                }
                                            }
                                        });

                                        //CheckChangeIP(WiFiIpAddr);// Miga Add 20180307. 讓client丟自己的wifi ip addr.給GO檢查,並讓GO的IPTable內有這組ip(為了讓GO進行unicast傳送訊息用)
                                        STATE = StateFlag.ADD_SERVICE.getIndex();
                                        GO_mac = MAC;
                                        //isCheck = true;//檢查完畢
                                    } else {
                                        STATE = StateFlag.ADD_SERVICE.getIndex();//如果wifi interface沒有連上表示可能資訊不夠新, 重add_service重新開始
                                        List<WifiConfiguration> list = wifi.getConfiguredNetworks();
                                        for (WifiConfiguration i : list) {
                                            wifi.removeNetwork(i.networkId);//移除所有之前wifi連線網路的設定
                                            wifi.saveConfiguration();//除存設定
                                        }
                                    }
                                    IsConnecting = false;
                                    return;
                                }
                            }
                        }//End mConnectivityManager != null
                    }//End ROLE == RoleFlag.NONE.getIndex()

                    // GO檢查自己是否被其他裝置連
                    if (ROLE == RoleFlag.GO.getIndex()) {
                        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                            @Override
                            public void onGroupInfoAvailable(WifiP2pGroup group) {
                                if (group != null) {
                                    if(group.getClientList().isEmpty()){
                                        hasclients = false;
                                    }else{
                                        hasclients = true;
                                    }

                                }
                            }
                        });
                        Thread.sleep(1500);
                        if ( hasclients ||peerCount > 1) {//peerCount是計算同個group內裡面有幾個device,若是>1的話表示GO有被其他裝置連(=1表示只有GO自己)
                            detect_run = 0;
                            STATE = StateFlag.ADD_SERVICE.getIndex();
                            return;
                        } else if (detect_run < 3) {//若peerCount=1,且detect_run<5(給GO 5次機會去看看Group內會不會將來有device連上他)
                            detect_run++;
                            STATE = StateFlag.ADD_SERVICE.getIndex();
                            return;
                        }
                        //GO被孤立
                        //利用line1080抓get(0)來檢查
                        if (SSID.equals(WiFiApName)) {//會相等表示line1080抓到的是自己的data(自己將來應該是這群device的GO),因此檢查有沒有被其他裝置連並且檢查是否孤立
                            //Miga20180117 會執行到這邊的原因是因為GO被孤立了,因為他的peerCount不大於0,表示他的cluster內只有自己一個人
                            if (Collect_record.get(1) != null) {//因為自己是GO,但是peerCount又沒有>1,表示說應該是被孤立了,因此將SSID改設為排序第二的device,之後可能會連到該device
                                SSID = Collect_record.get(1).getSSID();//因此這個被孤立的GO//Miga20180117 peerCount應該是計算同個cluster內裡面有幾個device,若是>0的話表示GO有被其他裝置連(因為同cluster內數量>0)
                                key = Collect_record.get(1).getkey();
                                Name = Collect_record.get(1).getName();
                                PEER = Collect_record.get(1).getPEER();
                                MAC = Collect_record.get(1).getMAC();
                            } else {//完全被孤立,附近都沒有裝置可以連,因此回到ADD_SERVICE繼續搜尋
                                detect_run = 0;
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                                return;
                            }

                        }
                        //用WIFI_INTERFACE去聯別人
                        STATE = StateFlag.WAITING.getIndex();
                        s_status = "State: GO choosing peer";
                        Log.d("Miga", "State: GO choosing peer");

                        if (mConnectivityManager != null) {
                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                            if (mNetworkInfo != null) {
                                if (mNetworkInfo.isConnected() == true) {//Wifi interface有連上
                                    wifi.disconnect();//斷掉wifi interface的連線,
                                    Thread.sleep(1000);
                                    sleep_time = sleep_time + 1000;
                                }
                            }
                        }
                        List<WifiConfiguration> list = wifi.getConfiguredNetworks();
                        for (WifiConfiguration i : list) {
                            wifi.removeNetwork(i.networkId);//移除所有之前wifi連線網路的設定
                            wifi.saveConfiguration();//除存設定
                        }
                        //連上別人
                        // Try to connect Ap(連上排序第一個or第二個的裝置)
                        IsConnecting = true;//正在連線中,避免Reconnecting_Wifi繼續執行
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
                        TryNum = 15;
                        //檢查我們所要連的GO是否還存在
                        wifiScanCheck = false;
                        long wifiscan_time_start = Calendar.getInstance().getTimeInMillis();
                        while (wifiScanCheck == false) {//在onCreate時有註冊一個廣播器,專門來徵測wifi scan的結果,wifi.startscan完畢後,wifiScanCheck應該會變為true
                            ;
                        }
                        sleep_time = sleep_time + Calendar.getInstance().getTimeInMillis() - wifiscan_time_start;
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
                            Log.d("Miga", "WiFi_Connect/ROLE:" + ROLE + " Cluster_Name:" + Cluster_Name + " wifiIpAddress:" + WiFiIpAddr);
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
                            IsP2Pconnect = true;
                        } else {
                            STATE = StateFlag.ADD_SERVICE.getIndex();//如果wifi interface沒有連上表示可能資訊不夠新, 重add_service重新開始
                            for (WifiConfiguration i : list) {
                                wifi.removeNetwork(i.networkId);//移除所有之前wifi連線網路的設定
                                wifi.saveConfiguration();//除存設定
                            }
                        }
                        IsConnecting = false;
                        return;
                    }//ROLE == RoleFlag.GO.getIndex()
                }/* 20180409 Start
                  * 由於新增了Step2Connection的function，因此下面的程式碼可以註解
                else{//手動建立連線 //20180403 以下為test,還沒測試成功
                    if( ROLE != RoleFlag.HYBRID.getIndex() && ROLE != RoleFlag.BRIDGE.getIndex()){
                        Log.d("Miga","Become to bridge or hybrid");
                        s_status="State: Become to bridge or hybrid";
                        //become to bridge or hybrid
                        if(Collect_record.size() == 1) {//沒有收集到其他cluster的device
                            Log.d("Miga", "Device doesn't receive data from other devices of the other cluster. ");
                            STATE = StateFlag.ADD_SERVICE.getIndex();
                            return;
                        }

                        STATE = StateFlag.WAITING.getIndex();
                        Collections.sort(Collect_record, new Step2Data_set_Comparator());//Collections根據step2的policy來排序
                        int obj_num2 = 0;
                        String Collect_contain2 = "";
                        Step1Data_set tmp2;
                        for (int i = 0; i < Collect_record.size(); i++) {
                            tmp2 = (Step1Data_set) Collect_record.get(i);
                            Collect_contain2 = Collect_contain2 + obj_num2 + " : " + tmp2.toString() + " ";
                            obj_num2++;
                        }
                        Log.d("Miga", "WiFi_Connect/Collect records contain (Step2) " + Collect_contain2);
                        String a;
                        //取出排序第一個,檢查是不是自己,若是的話則不用去連別人,只須等待別人來連自己
                        SSID = Collect_record.get(0).getSSID();
                        if(SSID.equals(WiFiApName)){
                            Log.d("Miga", "Step 2, I'm the best!");
                            STATE = StateFlag.ADD_SERVICE.getIndex();
                            return;
                        }
                        for (int i = 0; i < Collect_record.size(); i++) {
                            if (!Collect_record.get(i).getROLE().equals("3")) {//對方不是BRIDGE的話則可以連線,若為BRIDGE則在選下一個
                                if (!Collect_record.get(i).getName().equals(Cluster_Name)) {//不相等的話表示這個GO是屬於其他Cluster的,所以device可以連上他然後變成是relay連接兩個不同的cluster
                                    SSID = Collect_record.get(i).getSSID();
                                    key = Collect_record.get(i).getkey();
                                    MAC = Collect_record.get(i).getMAC();
                                    Choose_Cluster_Name = Collect_record.get(i).getName();
                                    break;
                                }
                            }
                        }
                        if(ROLE == RoleFlag.GO.getIndex()){//GO使用wifi去連
                            WifiInterface_Connect(SSID,key,"2");
                            if(!mNetworkInfo.isConnected()){//Wifi沒連上,則重新連
                                STATE = StateFlag.ADD_SERVICE.getIndex();
                                List<WifiConfiguration> list = wifi.getConfiguredNetworks();
                                for (WifiConfiguration i : list) {
                                    wifi.removeNetwork(i.networkId);//移除所有之前wifi連線網路的設定
                                    wifi.saveConfiguration();//除存設定
                                }
                                return;
                            }else{//Wifi成功連線
                                // renew service record information
                                Cluster_Name = Choose_Cluster_Name;//將自己的Cluster_Name更新為新的GO的Cluster_Name //Miga 20180118 將自己的clusterName更新了
                                ROLE = RoleFlag.HYBRID.getIndex();//變為HYBRID
                                WiFiIpAddr = wifiIpAddress();//取得wifi IP address
                                Log.d("Miga", "WiFi_Connect/ROLE:" + ROLE + " Cluster_Name:" + Cluster_Name + " wifiIpAddress:" + WiFiIpAddr);
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
                                IsP2Pconnect = true;
                                s_status = "Connect Group time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0)) + " stay_time : " + Double.toString((sleep_time / 1000.0))
                                        + " Round_Num :" + NumRound;
                                Log.d("Miga", "Connect Group time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0)) + " stay_time : " + Double.toString((sleep_time / 1000.0))
                                        + " Round_Num :" + NumRound);

                            }
                        }else if (ROLE == RoleFlag.CLIENT.getIndex()){//CLIENT使用P2P interface去連
                            // 為了變成relay,需要先把自己的GO解除->讓要用p2p interface去連別人
                            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Miga", "remove group success");
                                }

                                @Override
                                public void onFailure(int reason) {
                                    Log.d("Miga", "remove group fail");
                                }
                            });
                            Thread.sleep(2000);
                            sleep_time = sleep_time + 2;
                            manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Miga", "stopPeerDiscovery onSuccess");
                                    manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                                        @Override
                                        public void onSuccess() {
                                            Log.d("Miga", "discoverPeers onSuccess");
                                        }

                                        @Override
                                        public void onFailure(int reasonCode) {
                                            Log.d("Miga", "discoverPeers onFailure");
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(int reasonCode) {
                                    Log.d("Wang", "stopPeerDiscovery onFailure");
                                }
                            });
                            PreClusterName = Cluster_Name;
                            Thread.sleep(5000);
                            sleep_time = sleep_time + 5;

                            WifiP2pConfig config = new WifiP2pConfig();
                            config.deviceAddress = MAC;//設定接下來relay要連接的device的MAC address
                            config.wps.setup = WpsInfo.PBC;
                            config.groupOwnerIntent = 0;

                            int try_num = 0;
                            //connect_check = false;

                            s_status = "State: Step 2, CLIENT associating GO, enable true:?" + MAC + " remainder #attempt:"
                                    + try_num+ " Cluster_Name " + Choose_Cluster_Name ;
                            Log.d("Miga", "State: Step 2, CLIENT associating GO, enable true:?" + MAC + " remainder #attempt:"
                                    + try_num+ " Cluster_Name " + Choose_Cluster_Name);
                            //p2p interface去連別人
                            manager.connect(channel, config, new ActionListener() {
                                @Override
                                public void onSuccess() {
                                    try {
                                        Thread.sleep(5000);
                                        sleep_time = sleep_time + 5;
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                    ROLE = RoleFlag.BRIDGE.getIndex();//變為BRIDGE
                                    //connect_check = true;
                                    Log.d("Miga", "P2P connect Success " + " " + "Cluseter_Name " + Cluster_Name);
                                    try {
                                        s_status = "Connect Group time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0))
                                                +  " Round_Num :" + NumRound +" peer count : " + ServalDCommand.peerCount();
                                    } catch (ServalDFailureException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }

                                @Override
                                public void onFailure(int reason) {//p2p interface連接失敗
                                    manager.cancelConnect(channel, new ActionListener() {
                                        @Override
                                        public void onSuccess() {}
                                        public void onFailure(int reason) {}
                                    });
                                    Log.d("Miga", "P2P connect failure");
                                }
                            });
                            try_num++;
                            Thread.sleep(5000);
                            sleep_time = sleep_time +5;
                        }
                    }
                }//End ROLE != RoleFlag.HYBRID.getIndex() && ROLE != RoleFlag.BRIDGE.getIndex()
                * 20180409  註解 End
                */
                /*if( ROLE == RoleFlag.CLIENT.getIndex()){
                    Log.d("Miga","Become to bridge or hybrid");
                    s_status="State: Become to bridge or hybrid";
                    //become to bridge or hybrid
                    if(Collect_record.size() == 1) {//沒有收集到其他cluster的device
                        Log.d("Miga", "Client doesn't receive data from other devices of the other cluster. ");
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        return;
                    }
                    if (mNetworkInfo.isConnected() == false) {//wifi interface還沒連上別人(GO)
                        Log.d("Wang", "mNetworkInfo.isConnected() == false");//可能原本有連上,所以ROLE==CLIENT,但之後可能wifi突然斷線了(?)
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        return;
                    }
                    STATE = StateFlag.WAITING.getIndex();
                    Collections.sort(Collect_record, new Step2Data_set_Comparator());//Collections根據step2的policy來排序
                    String a;
                    for (int i = 0; i < Collect_record.size(); i++) {

                        *//*if(Collect_record.get(i).getPEER()) {
                            if (!Collect_record.get(i).getName().equals(Cluster_Name)) {//不相等的話表示這個GO是屬於其他Cluster的,所以device可以連上他然後變成是relay連接兩個不同的cluster
                                MAC = Collect_record.get(i).getMAC();
                                Choose_Cluster_Name = Collect_record.get(i).getName();
                                break;
                            }
                        }*//*
                    }
                }//End ROLE == RoleFlag.CLIENT.getIndex()
*/
            } catch (Exception e) {
                STATE = StateFlag.ADD_SERVICE.getIndex();
                e.printStackTrace();
            }
        }
    }

    private void Step2Connection(){
        String SSID = null;
        String key = null;
        String MAC = null;
        try {
            if (ROLE != RoleFlag.HYBRID.getIndex() && ROLE != RoleFlag.BRIDGE.getIndex()) {
                Log.d("Miga", "Become to bridge or hybrid");
                s_status = "State: Become to bridge or hybrid";
                //become to bridge or hybrid
                if (Collect_record.size() == 1) {//沒有收集到其他cluster的device
                    Log.d("Miga", "Device doesn't receive data from other devices of the other cluster. ");
                    STATE = StateFlag.ADD_SERVICE.getIndex();
                    return;
                }

                STATE = StateFlag.WAITING.getIndex();
                Collections.sort(Collect_record, new Step2Data_set_Comparator());//Collections根據step2的policy來排序
                int obj_num2 = 0;
                String Collect_contain2 = "";
                Step1Data_set tmp2;
                for (int i = 0; i < Collect_record.size(); i++) {
                    tmp2 = (Step1Data_set) Collect_record.get(i);
                    Collect_contain2 = Collect_contain2 + obj_num2 + " : " + tmp2.toString() + " ";
                    obj_num2++;
                }
                Log.d("Miga", "Step2Connection/Collect records contain (Step2) " + Collect_contain2);
                String a;
                //取出排序第一個,檢查是不是自己,若是的話則不用去連別人,只須等待別人來連自己
                SSID = Collect_record.get(0).getSSID();
                if (SSID.equals(WiFiApName)) {
                    Log.d("Miga", "Step 2, I'm the best!");
                    STATE = StateFlag.ADD_SERVICE.getIndex();
                    return;
                }
                for (int i = 0; i < Collect_record.size(); i++) {
                    if (!Collect_record.get(i).getROLE().equals("3")) {//對方不是BRIDGE的話則可以連線,若為BRIDGE則在選下一個
                        if (!Collect_record.get(i).getName().equals(Cluster_Name)) {//不相等的話表示這個GO是屬於其他Cluster的,所以device可以連上他然後變成是relay連接兩個不同的cluster
                            SSID = Collect_record.get(i).getSSID();
                            key = Collect_record.get(i).getkey();
                            MAC = Collect_record.get(i).getMAC();
                            Choose_Cluster_Name = Collect_record.get(i).getName();
                            break;
                        }
                    }
                }
                if (ROLE == RoleFlag.GO.getIndex()) {//GO使用wifi去連
                    WifiInterface_Connect(SSID, key, "2",Choose_Cluster_Name);
                    if (!mNetworkInfo.isConnected()) {//Wifi沒連上
                        if(IsGOConnecting) { //20180429, GO先前有嘗試連線，但是沒有連成功，因此移除CNTable該SSID
                            CNTable.remove(Choose_Cluster_Name);
                            IsGOConnecting = false;
                        }
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        List<WifiConfiguration> list = wifi.getConfiguredNetworks();
                        for (WifiConfiguration i : list) {
                            wifi.removeNetwork(i.networkId);//移除所有之前wifi連線網路的設定
                            wifi.saveConfiguration();//除存設定
                        }
                        return;
                    } else {//Wifi成功連線
                        // renew service record information
                        Cluster_Name = Choose_Cluster_Name;//將自己的Cluster_Name更新為新的GO的Cluster_Name //Miga 20180118 將自己的clusterName更新了
                        //HasClient();//檢查有沒有client
                        //if(hasclients)
                        ROLE = RoleFlag.HYBRID.getIndex();//變為HYBRID
                        //else
                        //    ROLE = RoleFlag.CLIENT.getIndex();//變為CLIENT
                        WiFiIpAddr = wifiIpAddress();//取得wifi IP address
                        Log.d("Miga", "Step2Connection/ROLE:" + ROLE + " Cluster_Name:" + Cluster_Name + " wifiIpAddress:" + WiFiIpAddr);
                        s_status = "state: WiFiIpAddress=" + WiFiIpAddr;
                        //if (!isOpenSWIAThread) {//開啟client傳送wifi ip address thread給GO
                        if (SendWiFiIpAddr == null) {
                            SendWiFiIpAddr = new SendWiFiIpAddr();
                            SendWiFiIpAddr.start();
                        }
                        //    isOpenSWIAThread = true;
                        //}

                        //CheckChangeIP(WiFiIpAddr);// Miga Add 20180307. 讓client丟自己的wifi ip addr.給GO檢查,並讓GO的IPTable內有這組ip(為了讓GO進行unicast傳送訊息用)
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        Isconnect = true;//p2p已有人連上/被連上,目前主要是用來判斷是否可以開始進行Group內peer的計算
                        IsP2Pconnect = true;
                        s_status = "Step2_Connect Group time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - step2_start_time) / 1000.0)) + " stay_time : " + Double.toString((step2_sleep_time / 1000.0))
                                + " Round_Num :" + NumRound;
                        Log.d("Miga", "Step2_Connect Group time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - step2_start_time) / 1000.0)) + " stay_time : " + Double.toString((step2_sleep_time / 1000.0))
                                + " Round_Num :" + NumRound);
                        return;
                    }
                } else if (ROLE == RoleFlag.CLIENT.getIndex()) {//CLIENT使用P2P interface去連
                    String canIconnects="";
                    if (MAC.equals(GO_mac)||SSID.equals(GO_SSID)) {//可能是因為GO_mac和接下來要連接的cluster內的GO是一樣的,所以連了也沒用(因為已經連了，且連了也無法multi group)
                        Log.d("Miga", "MAC.equals(GO_mac)||SSID.equals(GO_SSID)");
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        return;
                    }
                    canIconnects=CanIConnect(Choose_Cluster_Name);
                    if(canIconnects.equals("NO")){//同個group內已經有其他裝置連該CN了
                        Log.d("Miga", "Client can not connect!");
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        return;
                    }else if(canIconnects.equals("NotReceive")){
                        STATE = StateFlag.ADD_SERVICE.getIndex();
                        return;
                    }
                    /*boolean continuerun=false;
                    while (!continuerun){
                        if(canIconnects.equals("NotReceive")){//Client傳送過去的資訊GO沒有收到,因此需再繼續重新傳送,等到GO回復
                            Log.d("Miga", "canIconnects.equals=NotReceive");
                            canIconnects=CanIConnect(Choose_Cluster_Name);
                        }else if (canIconnects.equals("OK")){
                            continuerun=true;
                            break;
                        }else if (canIconnects.equals("NO")){
                            Log.d("Miga", "Client can not connect!");
                            STATE = StateFlag.ADD_SERVICE.getIndex();
                            return;
                        }
                    }*/
                    // 為了變成relay,需要先把自己的GO解除->讓要用p2p interface去連別人
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d("Miga", "remove group success");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.d("Miga", "remove group fail");
                        }
                    });
                    Thread.sleep(2000);
                    step2_sleep_time = step2_sleep_time + 2;
                    manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d("Miga", "stopPeerDiscovery onSuccess");
                            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

                                @Override
                                public void onSuccess() {
                                    Log.d("Miga", "discoverPeers onSuccess");
                                }

                                @Override
                                public void onFailure(int reasonCode) {
                                    Log.d("Miga", "discoverPeers onFailure");
                                }
                            });
                        }

                        @Override
                        public void onFailure(int reasonCode) {
                            Log.d("Wang", "stopPeerDiscovery onFailure");
                        }
                    });
                    PreClusterName = Cluster_Name;
                    Thread.sleep(5000);
                    step2_sleep_time = step2_sleep_time + 5;

                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = MAC;//設定接下來relay要連接的device的MAC address
                    config.wps.setup = WpsInfo.PBC;
                    config.groupOwnerIntent = 0;

                    int try_num = 0;
                    //connect_check = false;
                    IsClientConnecting = true;
                    s_status = "State: Step 2, CLIENT associating GO, enable true:?" + SSID + " remainder #attempt:"
                            + try_num + " Cluster_Name " + Choose_Cluster_Name;
                    Log.d("Miga", "State: Step 2, CLIENT associating GO, enable true:?" + SSID + " remainder #attempt:"
                            + try_num + " Cluster_Name " + Choose_Cluster_Name);
                    //p2p interface去連別人
                    manager.connect(channel, config, new ActionListener() {
                        @Override
                        public void onSuccess() {
                            try {
                                //傳送info給GO讓GO知道我的p2p ip address
                                //20180411 BRIDGE的IP傳送是利用send_peer_count時的unicast傳給49.1時，
                                //等到GO在receive_peer_count接收到BRIDGE傳來的pkt時，可以抓取傳送該pkt的ip address
                                //GO在將之儲存於他的IPTable，讓兩個GROUP可以順利溝通
                                Thread.sleep(5000);
                                step2_sleep_time = step2_sleep_time + 5;
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            ROLE = RoleFlag.BRIDGE.getIndex();//變為BRIDGE
                            Cluster_Name = Choose_Cluster_Name;
                            //connect_check = true;
                            Log.d("Miga", "P2P connect Success " + " " + "Cluseter_Name " + Cluster_Name);
                            try {
                                s_status = "Connect Group time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - step2_start_time) / 1000.0))
                                        + " Round_Num :" + NumRound + " peer count : " + ServalDCommand.peerCount();
                            } catch (ServalDFailureException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onFailure(int reason) {//p2p interface連接失敗
                            String goack=CanIConnect(Choose_Cluster_Name);
                            manager.cancelConnect(channel, new ActionListener() {
                                @Override
                                public void onSuccess() {
                                }

                                public void onFailure(int reason) {
                                }
                            });
                            Log.d("Miga", "P2P connect failure");
                        }
                    });
                    try_num++;
                    Thread.sleep(5000);
                    step2_sleep_time = step2_sleep_time + 5;
                    STATE = StateFlag.ADD_SERVICE.getIndex();
                    return;
                }
            }
        }
        catch (Exception e){
            STATE = StateFlag.ADD_SERVICE.getIndex();
            e.printStackTrace();
            Log.d("Miga","Step2Connection Exception: " + e.toString());
        }
    }

    private void WifiInterface_Connect(String SSID, String key,String StepNum,String Want_Cluster_Name){
        try {
            //用WIFI_INTERFACE去聯別人
            STATE = StateFlag.WAITING.getIndex();
            //GO確認自己能不能連上別人
            if(StepNum.equals("2")) {
                if(!CanGOConnect(Want_Cluster_Name)){//CNTable已經有這個CN了，所以GO不能連線
                    STATE = StateFlag.ADD_SERVICE.getIndex();
                    return;
                }
            }


            s_status = "State: Step "+StepNum+", GO choosing peer";
            Log.d("Miga", "State: Step "+StepNum+", GO choosing peer");

            if (mConnectivityManager != null) {
                mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (mNetworkInfo != null) {
                    if (mNetworkInfo.isConnected() == true) {//Wifi interface有連上
                        wifi.disconnect();//斷掉wifi interface的連線,
                        Thread.sleep(1000);
                        sleep_time = sleep_time + 1000;
                    }
                }
            }
            List<WifiConfiguration> list = wifi.getConfiguredNetworks();
            for (WifiConfiguration i : list) {
                wifi.removeNetwork(i.networkId);//移除所有之前wifi連線網路的設定
                wifi.saveConfiguration();//除存設定
            }
            //連上別人
            // Try to connect Ap(連上排序第一個or第二個的裝置)
            IsConnecting = true;//正在連線中,避免Reconnecting_Wifi繼續執行
            //20180429 start 為了讓GO移除掉沒有成功連線的CNTable
            if(ROLE == RoleFlag.GO.getIndex())
                IsGOConnecting = true;//GO正在嘗試連線
            //20180429 end
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
            TryNum = 15;
            //檢查我們所要連的GO是否還存在
            /*wifiScanCheck = false;
            long wifiscan_time_start = Calendar.getInstance().getTimeInMillis();
            while (wifiScanCheck == false) {//在onCreate時有註冊一個廣播器,專門來徵測wifi scan的結果,wifi.startscan完畢後,wifiScanCheck應該會變為true
                ;
            }
            sleep_time = sleep_time + Calendar.getInstance().getTimeInMillis() - wifiscan_time_start;
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
            }*/

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
            IsConnecting = false;
            return;
        }
        catch (Exception e){

        }
    }

    private void HasClient(){

        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                if (group != null) {
                    if(group.getClientList().isEmpty()){
                        hasclients = false;
                    }else{
                        hasclients = true;
                    }

                }
            }
        });

    }
    //For GO確認自己是否可以進行連線
    private boolean CanGOConnect(String WantConnectCN){
        if(CNTable.containsKey(WantConnectCN)){
            Log.d("Miga","GO Can not connect to: "+WantConnectCN+" ,CNTable = "+CNTable);
            return false;
        }else{
            CNTable.put(WantConnectCN,0);
            Log.d("Miga","GO Can connect to: "+WantConnectCN+" ,Put CNTable = "+CNTable);
            return true;
        }
    }
    //For Client呼叫此function傳送連線info
    private String CanIConnect(String WantConnectCN){
        String message="",recmessage="";
        Iterator iterator;
        MulticastSocket multicsk;//Miga20180313
        DatagramPacket msgPkt;//Miga


        byte[] bcMsg;
        boolean isSuccessSend=false;
        DatagramPacket dgpkt;//unicast
        DatagramSocket dgskt;//unicast
        try{
            bcMsg = new byte[8192];
            dgpkt = new DatagramPacket(bcMsg, bcMsg.length);
            dgskt = new DatagramSocket(IP_port_for_can_connect);
            dgskt.setReuseAddress(true);//For EADDRINUSE (Address already in use) Exception
            if (IsP2Pconnect) {
                if(IsClientConnecting){//Client正在嘗試連線，但是連線失敗了
                    message = WiFiApName + "#" + WiFiIpAddr + "#" + WantConnectCN +"#"+"fail";
                    IsClientConnecting = false;
                }
                else {//Client還沒開始連線過
                    message = WiFiApName + "#" + WiFiIpAddr + "#" + WantConnectCN;
                }
                //multicast
                if (mConnectivityManager != null) {
                    mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                    if (mNetworkInfo.isConnected()) {
                        multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                        multicsk = new MulticastSocket(6792);//6790: for peertable update
                        msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6792);
                        multicsk.send(msgPkt);
                        Log.v("Miga", "CanIConnect multicsk send message:" + message);
                        s_status = "CanIConnect multicsk send message" + message;

                        if (dgpkt != null) {
                            dgskt.receive(dgpkt);
                            recmessage = new String(bcMsg, 0, dgpkt.getLength());
                            //Log.d("Miga","Client get GO's msg:"+recmessage);
                            if (recmessage.equals("OK")) {//recmessage=="IpReceive" ,用==是比較物件, 在這裡字串比較應該用str.equals(str2);比較好
                                Log.d("Miga", "Client get GO's msg: OK");
                                return "OK";
                            } else if (recmessage.equals("NO")) {
                                Log.d("Miga", "Client get GO's msg: NO");
                                return "NO";
                            } else if (recmessage.equals("remove success")){
                                Log.d("Miga", "Client get GO's msg: remove success");
                                return "remove success";
                            }else {
                                Log.d("Miga", "Client get GO's msg: NotReceive");
                                return "NotReceive";
                            }
                        }
                    }
                }
                Log.d("Miga", "Client get GO's msg: NotReceive");
                return "NotReceive";//還沒接收到GO回傳的
            }
        }catch(Exception e){
            e.printStackTrace();
            Log.d("Miga","CanIConnect Exception"+e.toString());
        }
        Log.d("Miga","Client get GO's msg: NotReceive");
        return "NotReceive";
    }
    //For Go確認是否client可以進行連線
    public class CanConnect extends Thread{
        private PrintWriter out;
        private String recMessagetemp,recWantCN, temp, sendbackmessage;
        private String[] recMessage;
        private int i,PreROLE;
        //private MulticastSocket clientSocket;//Miga
        private DatagramPacket msgPkt;//Miga
        private boolean isJoin=false;
        private ServerSocket ss = null;
        private DatagramPacket dgpacket = null;
        private DatagramSocket dgsocket = null;
        public void run() {
            try {
                lMsg_cc = new byte[8192];
                receivedpkt_cc = new DatagramPacket(lMsg_cc, lMsg_cc.length);//接收到的message會存在IMsg//回傳使用 unicast
                dgsocket = new DatagramSocket();
                //Miga add multicast 20180309 (接收使用multicast)
                byte[] buf=new byte[256];

                while(true){
                    if(Step2Auto) {//避免跳Exception
                        //讀取數據
                        msgPkt = null;
                        msgPkt = new DatagramPacket(buf, buf.length);

                        if (msgPkt != null && !msgPkt.equals("")) {
                            recvCCSocket.receive(msgPkt);
                            recMessagetemp = new String(buf, 0, msgPkt.getLength());
                            recMessage = recMessagetemp.split("#");//[0]:WiFiApName(SSID),[1]:傳送過來的IP address [2]: 想連的CN名稱
                            recWantCN = recMessage[2];
                            if (!(recMessage[0].equals(WiFiApName))) {//接收到的不是自己,則可以進行IP判斷
                                //Log.d("Miga", "I got multicast message from:" + recMessagetemp);
                                //s_status = "I got multicast message from:" + recMessagetemp;
                                if(recMessage.length==4){
                                    if(recMessage[3].equals("fail")){
                                        CNTable.remove(Choose_Cluster_Name);
                                        sendbackmessage = "remove success";
                                        s_status ="CNTable remove success"+Choose_Cluster_Name;
                                        Log.d("Miga","CNTable remove success"+Choose_Cluster_Name);
                                    }
                                }else {
                                    if (CNTable.containsKey(recWantCN)) {//已經在CNTable內了，表示GO或是其他member已經要連了
                                        sendbackmessage = "NO";
                                        s_status = "Has in CNTable " + CNTable;
                                        Log.d("Miga", "Client ask, CanConnect/ Has in CNTable: " + CNTable);
                                    } else {
                                        sendbackmessage = "OK";
                                        CNTable.put(recWantCN, 0);
                                        //for test
                                        s_status = "Put in CNTable " + CNTable;
                                        Log.d("Miga", "Client ask, CanConnect/ Put in CNTable: " + CNTable);
                                    }
                                }
                                //unicast 回傳到該wifi ip address,及該port
                                dgpacket = null;//20180520
                                dgpacket = new DatagramPacket(sendbackmessage.getBytes(), sendbackmessage.length(), InetAddress.getByName(recMessage[1]), IP_port_for_can_connect);
                                dgsocket.send(dgpacket);
                                Log.d("Miga", "GO send CanIConnect msg to client: " + recMessage[1] + ", " + sendbackmessage);
                            }

                        }
                    }

                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "CanConnect1 Exception" + e.toString());
            } finally {

            }
        }
    }
    //取得p2p ip address
    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }


    private void discoverService() {
        manager.setDnsSdResponseListeners(channel,
                new WifiP2pManager.DnsSdServiceResponseListener() {
                    @Override
                    public void onDnsSdServiceAvailable(String instanceName,
                                                        String registrationType, WifiP2pDevice srcDevice) { }
                },
                new WifiP2pManager.DnsSdTxtRecordListener() {
                    @Override
                    public void onDnsSdTxtRecordAvailable(
                            String fullDomainName, Map<String, String> re_record,
                            WifiP2pDevice device) {
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
        if(Step2Auto) {
            Before_Step2_RunT += 1;
        }
        //Log.d("Miga", "startRegistration/InfoChangeTime"+InfoChangeTime);
        record_re = new HashMap();
        //int peercount = count_peer();
        //peercount=record_set.size();//蒐集到周遭的info,初始值為0
        peercount = discoverpeernum;//於listener接收到時會做更新
        grouppeer = count_peer()+1;//group內的peer數量(包含自己)
        //Log.d("Miga", "startRegistration/discoverpeernum"+discoverpeernum);
        /*try {
            peerCount = ServalDCommand.peerCount();
        } catch (ServalDFailureException e) {
            e.printStackTrace();
        }*/
        peerCount=grouppeer;//Miga 測試看看是不是會出現在app的Enable Services旁邊的字,20180316測試結果是不會XD
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
        record_re.put("GroupPEER",Integer.toString(grouppeer));//count_peer(): PeerTable內有幾個peer,+1表示group內有幾個peer
        record_re.put("DROLE", Integer.toString(ROLE));
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
                                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d("Leaf0419", "State: discoverPeers onSuccess");
                                        STATE = StateFlag.DISCOVERY_SERVICE.getIndex();
                                    }

                                    @Override
                                    public void onFailure(int error) {
                                    }
                                });
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
                    sleep_time = sleep_time + 1000;
                    /*if(Step2Auto){
                        Log.d("Miga","Step2 Auto true!");
                    }*/
                    if (Auto) {
                        //開啟discovery serivce的listener,來接收其他device的info
                        if(start_time == 0) {//OnCreate時將start_time=0;
                            start_time = Calendar.getInstance().getTimeInMillis();
                            sleep_time = 0;
                            discoverService();
                        }
                        if ((!IsConnecting)&&STATE == StateFlag.ADD_SERVICE.getIndex()) {
                            s_status = "State: advertising service";
                            Log.d("Miga", "State: advertising service");
                            STATE = StateFlag.WAITING.getIndex();
                            long service_time = Calendar.getInstance().getTimeInMillis();
                            startRegistration();
                            sleep_time = sleep_time + Calendar.getInstance().getTimeInMillis() - service_time;
                            // 一定要 sleep 否則無法觸發discovery_service_flag
                            // 造成一直執行 add_service_flag
                            Thread.sleep(2000);
                            sleep_time = sleep_time + 2000;
                            if(Step2Auto)
                                step2_sleep_time = step2_sleep_time + 2000;
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
                            //discoverPeers於20180319加入
                            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                                @Override
                                public void onSuccess() {
                                    Log.d("Miga", "peer discovery success");
                                }
                                @Override
                                public void onFailure(int reasonCode) {
                                    switch(reasonCode){
                                        case WifiP2pManager.ERROR:
                                            Log.d("Miga", "WifiP2pManager.ERROR");
                                            break;
                                        case WifiP2pManager.P2P_UNSUPPORTED:
                                            Log.d("Miga", "WifiP2pManager.P2P_UNSUPPORTED:");
                                            break;
                                        case WifiP2pManager.BUSY:
                                            Log.d("Miga", "WifiP2pManager.BUSY:");
                                            break;
                                    }
                                }
                            });
                            STATE = StateFlag.ADD_SERVICE.getIndex();
                        }//End DISCOVERY_SERVICE
                        if(InfoChangeTime < 3) {
                            int randomnum = randomWithRange(4,8)*1000;
                            Thread.sleep(randomnum);
                            //Log.d("Miga","Thread sleep:"+randomnum);
                            sleep_time = sleep_time + randomnum;
                            if(Step2Auto)
                                step2_sleep_time = step2_sleep_time + randomnum;
                        }else{
                            int randomnum = randomWithRange(8,10)*1000;
                            Thread.sleep(randomnum);
                            //Log.d("Miga","Thread sleep:"+randomnum);
                            sleep_time = sleep_time + randomnum;
                            if(Step2Auto)
                                step2_sleep_time = step2_sleep_time + randomnum;
                        }
                        NumRound++;
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

    public int randomWithRange(int min, int max) {
        int range = (max - min) + 1;
        return (int)(Math.random() * range) + min;
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
    // 20180323 目前GO已可使用unicast來回傳給Client, 若GO有成功收到ip則回傳IpReceive;若沒有則沒辦法回傳
    public class CollectIP_server extends Thread {

        private PrintWriter out;
        private String recMessagetemp,recWiFiIpAddr, temp, sendbackmessage;
        private String[] recMessage;
        private int i,PreROLE;
        //private MulticastSocket clientSocket;//Miga
        private DatagramPacket msgPkt;//Miga
        private boolean isJoin=false;
        private ServerSocket ss = null;
        private DatagramPacket dgpacket = null;
        private DatagramSocket dgsocket = null;


        public void run() {
            try {


                //Miga add multicast 20180309 (接收使用multicast)
                byte[] buf=new byte[256];
                //回傳使用 unicast
                dgsocket = new DatagramSocket();
                while(true){

                    msgPkt=null;
                    //讀取數據
                    msgPkt=new DatagramPacket(buf, buf.length);

                    if(msgPkt!=null || !msgPkt.equals("")){
                        recvSocket.receive(msgPkt);
                        recMessagetemp=new String(buf, 0, msgPkt.getLength());
                        recMessage = recMessagetemp.split("#");//[0]:WiFiApName(SSID),[1]: WifiIP
                        recWiFiIpAddr=recMessage[1];
                        if(!(recMessage[0].equals(WiFiApName))) {//接收到的不是自己,則可以進行IP判斷
                            //Log.d("Miga", "I got multicast message from:" + recMessagetemp);
                            //s_status = "I got multicast message from:" + recMessagetemp;
                            if (IPTable.containsKey(recWiFiIpAddr)) {
                                /*//temp = recWiFiIpAddr;
                                for (i = 2; i < 254; i++) {
                                    temp = "192.168.49." + String.valueOf(i);
                                    if (IPTable.containsKey(temp) == false) break;
                                }
                                IPTable.put(temp, 0);
                                //for test
                                s_status=" IPTABLE " + IPTable;
                                Log.d("Miga", "IPTABLE: " + IPTable);
                                // test end
                                //sendbackmessage = "YES:" + temp;*/
                            } else {
                                IPTable.put(recWiFiIpAddr, 0);
                                //for test
                                s_status=" IpTable " + IPTable;
                                Log.d("Miga", "IpTable: " + IPTable);

                                //unicast 回傳到該wifi ip address,及該port
                                sendbackmessage = "IpReceive"+"#"+WiFiApName;//20180417加入+"#"+WiFiApName，為了讓CLIENT更新GO_SSID
                                dgpacket = new DatagramPacket(sendbackmessage.getBytes(), sendbackmessage.length(), InetAddress.getByName(recWiFiIpAddr),IP_port_for_IPSave);
                                dgsocket.send(dgpacket);

                                Log.d("Miga", "GO send wifi ip msg to client: "+recWiFiIpAddr+", " + sendbackmessage);
                                //180328 Start 避免在此裝置還沒變成GO的情況下,就馬上連到另個device. 這樣這個裝置的client的peer table會接受不到
                                /*if(ROLE == RoleFlag.NONE.getIndex()){
                                    if(mNetworkInfo.isConnected())
                                        ROLE = RoleFlag.HYBRID.getIndex();
                                    else
                                        ROLE = RoleFlag.GO.getIndex();
                                }*/
                                //180328 End
                                PreROLE = ROLE;
                                //180329把上面的NONE加入到下面一起寫
                                if(ROLE == RoleFlag.CLIENT.getIndex() || ROLE == RoleFlag.NONE.getIndex()){//當原本的ROLE是CLIENT時，但是有收到了device傳來的ip.表示現在我這個client有member的存在
                                    if(mNetworkInfo.isConnected()) {//且我的wifi還持續有連上,表示我現在ROLE是Hybrid
                                        ROLE = RoleFlag.HYBRID.getIndex();
                                        IsP2Pconnect = true;
                                    }else {//wifi沒有連上，表示我只有client.因此我是GO
                                        ROLE = RoleFlag.GO.getIndex();
                                        IsP2Pconnect = true;
                                        CNTable.put(Cluster_Name,0);//將自己的CN放入CNTable
                                    }
                                    Log.d("Miga", "CollectIP_server/Role transform ROLE= "+PreROLE+" into : "+ROLE);
                                    //s_status= "CollectIP_server/Role transform ROLE= "+PreROLE+" into : "+ROLE;
                                }


                            }
                        }

                    }

                }
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
                    if (dgsocket != null) {
                        dgsocket.close();
                        Log.d("Miga", "CollectIP_server dgsocket is close");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.d("Miga","CollectIP_Server Exception:"+e);
                }
            }
        }
    }

    // 20180323 將此thread改為迴圈, 若GO都還沒收到ip的話, 會持續傳送ip, 直到接收到IP
    public class SendWiFiIpAddr extends Thread{
        MulticastSocket multicsk;//Miga20180129
        DatagramPacket msgPkt;//Miga
        String message,recmessage="";
        String[] recMessage;
        private byte[] bcMsg;
        boolean isSuccessSend=false;
        DatagramPacket dgpkt;//unicast
        DatagramSocket dgskt;//unicast

        private boolean GO_SSID_update = false;//for GO_SSID update

        public void run() {
            try {
                bcMsg = new byte[8192];
                dgpkt = new DatagramPacket(bcMsg, bcMsg.length);
                dgskt = new DatagramSocket(IP_port_for_IPSave);
                while(!isSuccessSend) {//還沒成功接收,則繼續接收GO回傳的msg
                    //Thread.sleep(20000);
                    dgskt = null; // 20180520 關socket
                    dgskt = new DatagramSocket(IP_port_for_IPSave); // 20180520 關socket
                    multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                    multicsk = new MulticastSocket(6789);
                    message =  WiFiApName+"#" +WiFiIpAddr;
                    msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6789);
                    multicsk.send(msgPkt);
                    Log.v("Miga", "SendWiFiIpAddr multicsk send message:" + message);
                    //s_status = "(Proactive)multicsk send message" + message;
                    //Thread.sleep(5000);

                    if(dgpkt != null){
                        dgskt.receive(dgpkt);
                        dgskt.close(); // 20180520 關socket
                        recmessage = new String(bcMsg, 0 , dgpkt.getLength());

                        recMessage = recmessage.split("#");//[0]:WiFiApName(SSID),[1]: WifiIP
                        //Log.d("Miga","Client get GO's msg:"+recmessage);
                        if(recMessage[0].equals("IpReceive")) {//recmessage=="IpReceive" ,用==是比較物件, 在這裡字串比較應該用str.equals(str2);比較好
                            isSuccessSend = true;
                            Log.d("Miga","Client get GO's msg: IpReceive");

                        }
                        //20180417將GO_SSID移來這裡
                        //讓CLIENT更新GO_SSID
                        if(!GO_SSID_update) {
                            if (ROLE == RoleFlag.CLIENT.getIndex()) {
                                GO_SSID = recMessage[1];//將自己的GO_SSID更新為GO的,這裡是為了Step2不要連上自己的GO
                                Cluster_Name = recMessage[1];//20180427避免再Receive_CN時沒更新到，因此也在這裡再做一次更新
                                Log.d("Miga", "GO_SSID:" + GO_SSID);
                                //s_status ="GO_SSID:" +GO_SSID;
                                GO_SSID_update = true;
                            }
                        }

                    }
                    //避免此裝置先變成了GO但是之後又發送ip給他的GO, 這樣此裝置的ROLE應為HYBRIYD才對
                    manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                        @Override
                        public void onGroupInfoAvailable(WifiP2pGroup group) {
                            if (group != null) {
                                if(!group.getClientList().isEmpty()){
                                    if(ROLE==RoleFlag.GO.getIndex()) {
                                        ROLE = RoleFlag.HYBRID.getIndex();
                                        IsP2Pconnect = true;
                                        Log.d("Miga", "SendWiFiIpAddr/GO->HYBRID");
                                    }
                                }

                            }
                        }
                    });
                    Thread.sleep(1000);

                }
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
                isJoin = true;//已加入multicast group
                try
                {
                    Thread.sleep(1000);
                }
                catch(InterruptedException ex)
                {
                    Thread.currentThread().interrupt();
                }
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
    //加入更新的Cluster Name multicast, 於Initial()呼叫
    public void JoinUpdateCNMultiCst(){

        try {
            //getP2P0();

            recvCNSocket = new MulticastSocket(6791);
            multicgroup = InetAddress.getByName("224.0.0.3"); //客戶客戶端將自己加入到指定的multicast group中,這樣就能夠收到來自該組的消息
            recvCNSocket.joinGroup(new InetSocketAddress(multicgroup, 6791), p2p0);//用p2p0 interface來接收muticast pkt
            Log.d("Miga", "I join CN multicast group success" + multicgroup);

        }catch (Exception e){
            Log.d("Miga", "JoinUpdatePeerMultiCst Exception:" + e);
        }
    }
    //加入確認step2是否可連線的 multicast, 於Initial()呼叫
    public void JoinUpdateCCMultiCst(){

        try {
            recvCCSocket = new MulticastSocket(6792);
            multicgroup = InetAddress.getByName("224.0.0.3"); //客戶客戶端將自己加入到指定的multicast group中,這樣就能夠收到來自該組的消息
            recvCCSocket.joinGroup(new InetSocketAddress(multicgroup, 6792), p2p0);//用p2p0 interface來接收muticast pkt
            Log.d("Miga", "I join CC multicast group success" + multicgroup);

        }catch (Exception e){
            Log.d("Miga", "JoinUpdateCCMultiCst Exception:" + e);
        }
    }
    //加入controller multicast, 於Initial()呼叫
    public void JoinUpdateControllerMultiCst(){

        try {
            recvControllerSocket = new MulticastSocket(6793);
            multicgroup = InetAddress.getByName("224.0.0.3"); //客戶客戶端將自己加入到指定的multicast group中,這樣就能夠收到來自該組的消息
            recvControllerSocket.joinGroup(new InetSocketAddress(multicgroup, 6793), p2p0);//用p2p0 interface來接收muticast pkt
            Log.d("Miga", "I join controller multicast group success" + multicgroup);

        }catch (Exception e){
            Log.d("Miga", "JoinUpdateControllerMultiCst Exception:" + e);
        }
    }
    //加入controller 傳送新的連線info的 multicast, 於Initial()呼叫
    public void JoinControllerNewConnectMultiCst(){

        try {
            recvControllerNewConnectSocket = new MulticastSocket(6795);
            multicgroup = InetAddress.getByName("224.0.0.3"); //客戶客戶端將自己加入到指定的multicast group中,這樣就能夠收到來自該組的消息
            recvControllerNewConnectSocket.joinGroup(new InetSocketAddress(multicgroup, 6795), p2p0);//用p2p0 interface來接收muticast pkt
            Log.d("Miga", "I join controller NewConnect multicast group success" + multicgroup);

        }catch (Exception e){
            Log.d("Miga", "JoinControllerNewConnectMultiCst Exception:" + e);
        }
    }
    // Miga, For multicast ------------End-------------

    //20180314 可成功使用multi, uni接收並relay(GO) pkt出去
    public class Receive_peer_count extends Thread {
        private byte[] lMsg,buf;
        private DatagramPacket receivedp, senddp;
        private DatagramSocket sendds;
        private Iterator iterator;
        private String message, tempkey, recv_ip;
        private String[] temp;
        private MulticastSocket multicsk;//Miga20180312


        private String recMessagetemp,recWiFiIpAddr;
        private String[] recMessage;
        private int i;
        private DatagramPacket msgPkt;//Miga
        private boolean isreceiveformbridge=false;


        public void run() {

            try{
                //20180326將multicast和unicast的socket分成兩個thread來寫，小的thread所接收的data會用global來儲存，再由此thread來處理資料。
                lMsg_pc = new byte[8192];
                receivedpkt_pc = new DatagramPacket(lMsg_pc, lMsg_pc.length);//接收到的message會存在IMsg
                receivedskt_pc = new DatagramSocket(IP_port_for_peer_counting);
                if (t_Receive_peer_count_uni == null) {
                    t_Receive_peer_count_uni = new Receive_peer_count_unicastsocket();
                    t_Receive_peer_count_uni.start();
                }

                if (t_Receive_peer_count_multi == null) {
                    t_Receive_peer_count_multi = new Receive_peer_count_multicastsocket();
                    t_Receive_peer_count_multi.start();
                }

                while(true){//20180518 Controller 開啟時，這個thread就不執行了
                    if(ControllerAuto){
                        Thread.sleep(600000);//sleep 600sec , 10mins
                    }
                    if(RecvMsg_pc!="") {
                        temp = RecvMsg_pc.split("#");//將message之中有#則分開存到tmep陣列裡;message = WiFiApName + "#" + Cluster_Name + "#" + "5"+ "#" +ROLE;
                        if (temp[0] != null && temp[1] != null && temp[2] != null && WiFiApName != null) {
                            if (Newcompare(temp[0], WiFiApName) != 0) {//接收到的data和此裝置的SSID不同; 若A>B則reutrn 1
                                InetAddress P2PIPAddress = receivedpkt_pc.getAddress();
                                recv_ip = P2PIPAddress.toString().split("/")[1];//接收傳這個pkt的ip
                                if(!isreceiveformbridge) {//還沒從bridge接收到ip
                                    if (temp.length == 4) {//表示有temp3
                                        //接收到的info事由BRIDGE傳來的，則將此ip存到自己的IPTable
                                        if (Integer.valueOf(temp[3]) == RoleFlag.BRIDGE.getIndex()) {
                                            //InetAddress P2PIPAddress = receivedpkt_pc.getAddress();
                                            String bridgeip = P2PIPAddress.toString().split("/")[1];//接收BRIDGE的IP
                                            //Log.d("Miga", "Receive ip form BRIDGE: " + bridgeip);
                                            //s_status = "Receive ip form BRIDGE: " + bridgeip;
                                            if (!IPTable.containsKey(bridgeip)) {
                                                IPTable.put(bridgeip, 0);
                                                Log.d("Miga", "IPTable: " + IPTable);
                                                s_status = "IPTable: " + IPTable;
                                                isreceiveformbridge=true;//已經從bridge那邊接收過了，因此不需要再存ip了
                                            }
                                        }
                                    }
                                }
                                //Log.d("Miga", "I got message from: " + RecvMsg_pc);
                                //s_status = "I got message from unicast" + RecvMsg_pc;
                                /*寫在這裡可能會讓GOip不准,可能抓到的是後面才傳來的pkt
                                //讓CLIENT更新GO_SSID
                                if(!GO_SSID_update) {
                                    if (ROLE == RoleFlag.CLIENT.getIndex()) {
                                        InetAddress WiFiIPAddress = receivedpkt_pc.getAddress();
                                        String GOip = WiFiIPAddress.toString().split("/")[1];//接收GO的IP
                                        //Log.d("Miga","GOip:" +GOip);
                                        //s_status ="GOip:" +GOip;
                                        if(GOip.equals("192.168.49.1")) {//傳封包的人是GO
                                            GO_SSID = temp[0];//將自己的GO_SSID更新為GO的,這裡是為了Step2不要連上自己的GO
                                            Log.d("Miga","GO_SSID:" +GO_SSID);
                                            //s_status ="GO_SSID:" +GO_SSID;
                                            GO_SSID_update = true;
                                        }
                                    }
                                }*/
                                // TTL -1
                                try {
                                    if(Integer.valueOf(temp[2].trim())>0) {
                                        temp[2] = String.valueOf(Integer.valueOf(temp[2].trim()) - 1).trim();//經過一個router因此-1
                                    }
                                    //Log.d("Miga","Temp[2]:"+temp[2]);
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Log.d("Miga", "Receive_peer_count Exception : at temp[2] _" + e.toString());
                                    s_status="Receive_peer_count Exception : at temp[2] _" + e.toString();
                                }
                                // update peer table
                                //if (Newcompare(temp[1], Cluster_Name) == 0) {//相同Cluster_Name
                                if(!PeerTable.containsKey(temp[0])) {
                                    PeerTable.put(temp[0], 20);//填入收到data的SSID(WiFiApName)
                                    if (count_peer() + 1 != pre_peer_count) {
                                        pre_peer_count = count_peer() + 1;//更新現在PeerTable內有幾個Peer
                                        s_status = "peer_count time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0))
                                                + " Round_Num :" + NumRound + " peer count : " + (count_peer() + 1) + " PeerTable:" + PeerTable;
                                        Log.d("Miga", "peer_count time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0)) + " stay_time : " + Double.toString((sleep_time / 1000.0))
                                                + " Round_Num :" + NumRound + " peer count : " + (count_peer() + 1) + " PeerTable:" + PeerTable);
                                    }

                                    InetAddress WiFiIPAddress = receivedpkt_pc.getAddress();
                                    String clientip = WiFiIPAddress.toString().split("/")[1];//接收CLIENT的IP
                                    if(!clientip.equals("192.168.49.1")) {//不是GO(GO的不存在CLINET內)
                                        if (!IPTable.containsKey(clientip)) {//避免client在sendWiFiIPAddress沒有成功傳來，因此直接在這邊做IPTable的儲存->SendWiFiIPAddress和Collect_IPServer之後可以拿掉了
                                            IPTable.put(clientip, 0);
                                            Log.d("Miga", "Receive_peer_count/IPTable:" + IPTable);
                                            s_status = "Receive_peer_count/IPTable:" + IPTable;
                                        }
                                    }
                                }
                                //Log.v("Miga", "PeerTable:" + PeerTable);
                                //s_status = "PeerTable:" + PeerTable;
                                //}

                                try {
                                    // relay packet
                                    if (Integer.valueOf(temp[2].trim()) > 0) {
                                        message = temp[0] + "#" + temp[1] + "#" + temp[2].trim();
                                        sendds = null;
                                        sendds = new DatagramSocket();
                                        try {
                                            //避免RELAY回原本傳回去的那個device
                                            //InetAddress WiFiIPAddress = receivedpkt_pc.getAddress();
                                            //String clientip = WiFiIPAddress.toString().split("/")[1];//接收CLIENT的IP
                                            //Log.d("Miga", "clientip: " + clientip);
                                            //s_status = "State : clientip: " +clientip;
                                            // unicast
                                            iterator = IPTable.keySet().iterator();
                                            while (iterator.hasNext()) {
                                                tempkey = iterator.next().toString();
                                                if(!recv_ip.equals(tempkey)) {
                                                    senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                            InetAddress.getByName(tempkey), IP_port_for_peer_counting);
                                                    sendds.send(senddp);
                                                    //Log.d("Miga", "(Relay)Send the message: " + message + " to " + tempkey);
                                                    //s_status = "State : (Relay)Send the message: " + message + " to " + tempkey;
                                                }
                                            }
                                            //for BRIDGE
                                            senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                    InetAddress.getByName("192.168.49.1"), IP_port_for_peer_counting);
                                            sendds.send(senddp);
                                            //if(sendds!=null)
                                            //sendds.close();
                                            //sendds.setRequestProperty("Connection","Close");
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            Log.d("Miga", "Receive_peer_count Exception : at relay pkt (unicast) _" + e.toString());
                                            s_status = "Receive_peer_count Exception : at relay pkt (unicast) _" + e.toString();
                                        }
                                        try {
                                            if (ROLE == RoleFlag.HYBRID.getIndex()|| ROLE == RoleFlag.BRIDGE.getIndex()) {
                                                if (ROLE == RoleFlag.HYBRID.getIndex()) {//20180501 新增HYBRID轉傳給CLIENT的，測試可成功
                                                    // broadcast
                                                    senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                            InetAddress.getByName("192.168.49.255"), IP_port_for_peer_counting);
                                                    sendds.send(senddp);
                                                }
                                                //multicast
                                                if (mConnectivityManager != null) {
                                                    mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                                    if (mNetworkInfo.isConnected()) {
                                                        multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                                                        multicsk = null;// 20180520 關socket
                                                        multicsk = new MulticastSocket(6790);//6790: for peertable update
                                                        msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6790);
                                                        multicsk.send(msgPkt);
                                                        if(multicsk!=null)
                                                            multicsk.close();
                                                        //Log.v("Miga", "multicsk send message:" + message);
                                                        //s_status = "multicsk send message" + message;
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            Log.d("Miga", "Receive_peer_count Exception : at relay pkt (multicast) _" + e.toString());
                                            s_status = "Receive_peer_count Exception : at relay pkt (multicast) _" + e.toString();
                                        }
                                    }
                                    if(multicsk!=null)
                                        multicsk.close();
                                    //if(sendds!=null)
                                        sendds.close();// 20180520 關socket
                                }catch (Exception e){
                                    e.printStackTrace();
                                    Log.d("Miga", "Receive_peer_count Exception : // relay packet _" + e.toString());
                                    s_status = "Receive_peer_count Exception : // relay packet _" + e.toString();
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
                if (recvPeerSocket != null) {
                    recvPeerSocket.close();
                    Log.d("Miga", "Receive_peer_count recvPeerSocket is close");
                }
            }
        }
    }

    public class Receive_peer_count_unicastsocket extends Thread{
        public void run() {
            try {
                while(true){//20180518 Controller 開啟時，這個thread就不執行了
                    if(ControllerAuto){
                        Thread.sleep(600000);//sleep 600sec , 10mins
                    }
                    if (IsP2Pconnect) {
                        if (receivedpkt_pc != null) {
                            //20180411註解下面，因為ROLE==GO時也會用unicast接收
                            //if (ROLE == RoleFlag.CLIENT.getIndex() || ROLE == RoleFlag.HYBRID.getIndex()|| ROLE == RoleFlag.BRIDGE.getIndex()) {
                            //unicast
                            receivedskt_pc.receive(receivedpkt_pc);//把接收到的data存在receivedp.
                            RecvMsg_pc = new String(lMsg_pc, 0, receivedpkt_pc.getLength());//將接收到的IMsg轉換成String型態
                            //Log.d("Miga", "I got message from unicast" + RecvMsg_pc);
                            //s_status = "I got message from unicast" + RecvMsg_pc;
                            //}
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_peer_count_unicastsocket Exception" + e.toString());
            }
        }
    }

    public class Receive_peer_count_multicastsocket extends Thread{
        public void run() {
            try {
                while(true){//20180518 Controller 開啟時，這個thread就不執行了
                    if(ControllerAuto){
                        Thread.sleep(600000);//sleep 600sec , 10mins
                    }
                    if (IsP2Pconnect) {
                        if (receivedpkt_pc != null) {
                            if (ROLE == RoleFlag.GO.getIndex() || ROLE == RoleFlag.HYBRID.getIndex()) {
                                //multicast
                                recvPeerSocket.receive(receivedpkt_pc);//recvPeerSocket_ port:6790
                                RecvMsg_pc = new String(lMsg_pc, 0, receivedpkt_pc.getLength());//將接收到的IMsg轉換成String型態
                                /*if(ROLE == RoleFlag.HYBRID.getIndex()) {
                                    Log.d("Miga", "I got message from multicast" + RecvMsg_pc);
                                    s_status = "I got message from multicast" + RecvMsg_pc;
                                }*/
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_peer_count_multicastsocket Exception" + e.toString());
            }
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
        //Log.d("Miga", "The peer count result is : " + result);
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
                while(true){//20180518 Controller 開啟時，這個thread就不執行了
                    if(ControllerAuto){
                        Thread.sleep(600000);//sleep 600sec , 10mins
                    }
                    if (IsP2Pconnect) {
                        sendds = null;// 20180520 關socket
                        sendds = new DatagramSocket();// 20180520 關socket
                        int randomnum = randomWithRange(1,4)*1000;
                        Thread.sleep(randomnum);
                        //Log.d("Miga","Sleep:"+randomnum);
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
                        if(ROLE == RoleFlag.BRIDGE.getIndex()) {//BRIDGE會多一個ROLE是為了讓另個cluster的知道是BRIDGE傳過去的(做IPTable更新用)
                            //for bridge
                            message = WiFiApName + "#" + Cluster_Name + "#" + "5"+"#"+ROLE;// 0: source SSID 1: cluster name 2: TTL 3:ROLE
                            dp = new DatagramPacket(message.getBytes(), message.length(),
                                    InetAddress.getByName("192.168.49.1"), IP_port_for_peer_counting);
                            sendds.send(dp);//傳給GO
                        }

                        message = WiFiApName + "#" + Cluster_Name + "#" + "5";// 0: source SSID 1: cluster name 2: TTL

                        //multicast
                        if (mConnectivityManager != null) {
                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                            if (mNetworkInfo.isConnected()) {
                                multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                                multicsk = null;// 20180520 關socket
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
                                iterator.remove();
                                //PeerTable.remove(tempkey);//將此SSID移除 //20180327註解這句,以iterator.remove取代,避免跳出ConcurrentModificationException
                                Log.v("Miga", "remove Peer:"+tempkey);
                                s_status = "remove Peer:"+tempkey;
                            }
                        }
                        //Log.v("Miga", "PeerTable:" + PeerTable);
                        //s_status = "PeerTable:" + PeerTable;
                        sendds.close();// 20180520 關socket
                        if(multicsk!=null)
                            multicsk.close();// 20180520 關socket
                    }
                    //Thread.sleep(1000);

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
        }
    }

    public class Send_Cluster_Name extends Thread {
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
                while(true){//20180518 Controller 開啟時，這個thread就不執行了
                    if(ControllerAuto){
                        Thread.sleep(600000);//sleep 600sec , 10mins
                    }
                    if (IsP2Pconnect) {
                        Time_Stamp="123456";//暫定
                        message = WiFiApName + "#" + Cluster_Name + "#" + ROLE + "#" + IsReceiveGoInfo + "#"
                                + Time_Stamp + "#" + "5";
                        sendds = null;// 20180520 關socket
                        sendds = new DatagramSocket();// 20180520 關socket
                        // unicast
                        iterator = IPTable.keySet().iterator();//IPTable的keySet為許多IP所組成
                        while (iterator.hasNext()) {
                            tempkey = iterator.next().toString();
                            dp = new DatagramPacket(message.getBytes(), message.length(),
                                    InetAddress.getByName(tempkey), IP_port_for_cluster_name);
                            sendds.send(dp);//一一傳送給IPTable內的所有IP
                            Log.v("Miga", "I send unicast message:" + message);
                            //s_status="I send unicast message:"+message;

                        }
                        //20180410For BRIDGE,HYBRID傳送CN
                        //multicast
                        if( ROLE == RoleFlag.BRIDGE.getIndex() || ROLE == RoleFlag.HYBRID.getIndex()) {
                            if (mConnectivityManager != null) {
                                mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                if (mNetworkInfo.isConnected()) {
                                    multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                                    multicsk = null;// 20180520 關socket
                                    multicsk = new MulticastSocket(6791);//6790: for CN update
                                    msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6791);
                                    multicsk.send(msgPkt);
                                    //Log.v("Miga", "multicsk send message:" + message);
                                    //s_status = "multicsk send message" + message;
                                }
                            }
                        }
                        sendds.close();// 20180520 關socket
                        if(multicsk!=null)
                            multicsk.close();// 20180520 關socket
                    }
                    int randomnum = randomWithRange(2,4)*1000;
                    Thread.sleep(randomnum);
                }
            } catch (SocketException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_Cluster_Name Socket exception" + e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_Cluster_Name IOException" + e.toString());
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_Cluster_Name InterruptedException" + e.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Send_Cluster_Name other exception" + e.toString());
            } finally {
                if (sendds != null) {
                    sendds.close();
                    Log.d("Miga", "Send_Cluster_Name sendds is close");
                }
            }
        }
    }
    //20180314 可成功使用multi, uni接收並relay(GO) pkt出去
    public class Receive_Cluster_Name extends Thread {
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

        private boolean notnull;
        private int m_length = 0;

        public void run() {

            try{
                //20180326將multicast和unicast的socket分成兩個thread來寫，小的thread所接收的data會用global來儲存，再由此thread來處理資料。
                lMsg_cn = new byte[8192];
                receivedpkt_cn = new DatagramPacket(lMsg_cn, lMsg_cn.length);//接收到的message會存在IMsg
                receivedskt_cn = new DatagramSocket(IP_port_for_cluster_name);
                if (t_Receive_Cluster_Name_uni == null) {
                    t_Receive_Cluster_Name_uni = new Receive_Cluster_Name_unicastsocket();
                    t_Receive_Cluster_Name_uni.start();
                }

                if (t_Receive_Cluster_Name_multi == null) {
                    t_Receive_Cluster_Name_multi = new Receive_Cluster_Name_multicastsocket();
                    t_Receive_Cluster_Name_multi.start();
                }

                while(true){//20180518 Controller 開啟時，這個thread就不執行了
                    if(ControllerAuto){
                        Thread.sleep(600000);//sleep 600sec , 10mins
                    }
                    if (RecvMsg_cn != "" && RecvMsg_cn!=null) {
                        temp = RecvMsg_cn.split("#");//將message之中有#則分開存到tmep陣列裡;message =  WiFiApName + "#" + Cluster_Name + "#" + ROLE + "#" + IsReceiveGoInfo + "#" + Time_Stamp + "#" + "5";
                        m_length = temp.length;
                        notnull=  true;
                        for(int i = 0; i < m_length; i++) {
                            if(temp[i] != null && !temp[i].isEmpty() && !temp[i].equals("null")) {
                            }else {
                                notnull = false;
                                break;
                            }
                        }
                        if(notnull){
                            if (Newcompare(temp[0], WiFiApName) != 0) {//接收到的不是自己的

                                if(!IsInitial){//手動設定的device還沒進CN更新,加入這個主要是讓非手動的device近來更新-> 20180410應該是讓手動的device近來更新
                                    if (ROLE == RoleFlag.CLIENT.getIndex()) {
                                        if (Integer.valueOf(temp[2]) == RoleFlag.GO.getIndex()) {//接收到的是GO傳來的
                                            if (!IsReceiveGoInfo) {//且還沒接收過GO的Info
                                                Cluster_Name = temp[1];
                                                IsReceiveGoInfo = true;//已接收過GO的
                                            }
                                        }
                                    }
                                }//還需要再寫一個自動的device更新CN
                                else{
                                    if (Integer.valueOf(temp[2]) == RoleFlag.HYBRID.getIndex() || Integer.valueOf(temp[2]) == RoleFlag.BRIDGE.getIndex()) {//接收到的是HY,BR傳來的
                                        if (!Cluster_Name.equals(temp[1])) {//傳送過來的CN不相同
                                            Cluster_Name = temp[1];
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                /*lMsg = new byte[8192];
                receivedp = new DatagramPacket(lMsg, lMsg.length);//接收到的message會存在IMsg
                receiveds_cn = null;
                receiveds_cn = new DatagramSocket(IP_port_for_cluster_name);//接收的Socket
                //pre_peer_count=1;
                while(true){
                    if(IsP2Pconnect) {
                        if (receivedp != null) {
                            if (ROLE == RoleFlag.GO.getIndex()) {
                                //multicast
                                recvCNSocket.receive(receivedp);//recvCNSocket port:6791
                                message = new String(lMsg, 0, receivedp.getLength());//將接收到的IMsg轉換成String型態
                                Log.d("Miga", "I got message from multicast" + message);
                                //s_status = "I got message from multicast" + message;
                            } else if (ROLE == RoleFlag.CLIENT.getIndex()) {
                                //unicast
                                receiveds_cn.receive(receivedp);//把接收到的data存在receivedp.
                                message = new String(lMsg, 0, receivedp.getLength());//將接收到的IMsg轉換成String型態
                                //Log.d("Miga", "I got message from unicast" + message);
                                //s_status = "I got message from unicast" + message;
                            }

                            temp = message.split("#");//將message之中有#則分開存到tmep陣列裡;message =  WiFiApName + "#" + Cluster_Name + "#" + ROLE + "#" + IsReceiveGoInfo + "#" + Time_Stamp + "#" + "5";
                            m_length = temp.length;
                            notnull=  true;
                            for(int i = 0; i < m_length; i++) {
                                if(temp[i] != null && !temp[i].isEmpty() && !temp[i].equals("null")) {
                                }else {
                                    notnull = false;
                                    break;
                                }
                            }
                            if(notnull){
                                if (Newcompare(temp[0], WiFiApName) != 0) {//接收到的不是自己的
                                    if(!IsInitial){//手動設定的device還沒進CN更新,加入這個主要是讓非手動的device近來更新
                                        if (ROLE == RoleFlag.CLIENT.getIndex()) {
                                            if (Integer.valueOf(temp[2]) == RoleFlag.GO.getIndex()) {//接收到的是GO傳來的
                                                if (!IsReceiveGoInfo) {//且還沒接收過GO的Info
                                                    Cluster_Name = temp[1];
                                                    IsReceiveGoInfo = true;//已接收過GO的
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }*/


            }catch (SocketException e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_Cluster_Name Socket exception" + e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_Cluster_Name IOException" + e.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_Cluster_Name Exception" + e.toString());
            } finally {
                if (receiveds_cn != null) {
                    receiveds_cn.close();
                    Log.d("Miga", "Receive_Cluster_Name receiveds_cn is close");
                }
                if (sendds != null) {
                    sendds.close();
                    Log.d("Miga", "Receive_Cluster_Name sendds is close");
                }
            }
        }
    }

    public class Receive_Cluster_Name_unicastsocket extends Thread{
        public void run() {
            try {
                while(true){//20180518 Controller 開啟時，這個thread就不執行了
                    if(ControllerAuto){
                        Thread.sleep(600000);//sleep 600sec , 10mins
                    }
                    if (IsP2Pconnect) {
                        if (receivedpkt_cn != null) {
                            if (ROLE == RoleFlag.CLIENT.getIndex() || ROLE == RoleFlag.HYBRID.getIndex() || ROLE == RoleFlag.BRIDGE.getIndex()) {
                                //unicast
                                receivedskt_cn.receive(receivedpkt_cn);//把接收到的data存在receivedp.
                                RecvMsg_cn = new String(lMsg_cn, 0, receivedpkt_cn.getLength());//將接收到的IMsg轉換成String型態
                                //Log.d("Miga", "I got message from unicast" + message);
                                //s_status = "I got message from unicast" + message;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_Cluster_Name_unicastsocket Exception" + e.toString());
            }
        }
    }

    public class Receive_Cluster_Name_multicastsocket extends Thread{
        public void run() {
            try {
                while(true){//20180518 Controller 開啟時，這個thread就不執行了
                    if(ControllerAuto){
                        Thread.sleep(600000);//sleep 600sec , 10mins
                    }
                    if (IsP2Pconnect) {
                        if (receivedpkt_cn != null) {
                            if (ROLE == RoleFlag.GO.getIndex() || ROLE == RoleFlag.HYBRID.getIndex()) {
                                //unicast
                                recvCNSocket.receive(receivedpkt_cn);//把接收到的data存在receivedp.
                                RecvMsg_cn = new String(lMsg_cn, 0, receivedpkt_cn.getLength());//將接收到的IMsg轉換成String型態
                                //Log.d("Miga", "I got message from unicast" + message);
                                //s_status = "I got message from unicast" + message;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_Cluster_Name_multicastsocket Exception" + e.toString());
            }
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

        registerReceiver(receiver_device_change = new BroadcastReceiver() {//註冊用來接收peer discovery的peer數量變化的結果
            @Override
            public void onReceive(Context c, Intent intent) {
                WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                thisDeviceName = device.deviceName;
            }
        }, new IntentFilter(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION ));

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
        CandController_record = new ArrayList<CandidateController_set>();// Miga 20180508
        Neighbor_record = new ArrayList<Neighbor_set>();
        Controller_record = new ArrayList<ControllerData_set>();
        first_round_Controller_record = new ArrayList<ControllerData_set>();
        CandNeighbor_record = new ArrayList<CandidateController_set>();
        second_round_Controller_record = new ArrayList<ControllerData_set>();
        Final_Controller_record = new ArrayList<ControllerData_set>();
        getBatteryCapacity();
        callAsynchronousTask();//Wang 20180427



    }
    //Wang
    public static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }
    //Wang
    public void callAsynchronousTask() {
        final Handler handler = new Handler();
        Timer timer = new Timer();
        TimerTask doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        try {
                            modeChanged();
                            String server_status = ServalDCommand.serverStatus().status;
                            if(server_status.equals("stopped")) {
                                try{
                                    ServalDCommand.serverStart();
                                }catch(ServalDFailureException e){
                                    Log.d("Wang", getStackTrace(e));
                                }
                            }
                            // PerformBackgroundTask this class is the class that extends AsynchTask
                        } catch (Exception e) {

                        }
                    }
                });
            }
        };
        timer.schedule(doAsynchronousTask, 0, 2000); //execute in every 2000 ms
    }
    //Miga for power
    private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctxt, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            power_level = (int)(level*getBatteryCapacity()/100);//20180327 power改用比較剩餘電容量
        }
    };
    //Miga
    public double getBatteryCapacity() {
        Object mPowerProfile_ = null;
        double batteryCapacity = 3000;
        final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";
        try {
            mPowerProfile_ = Class.forName(POWER_PROFILE_CLASS)
                    .getConstructor(Context.class).newInstance(this);
            batteryCapacity = (Double) Class
                    .forName(POWER_PROFILE_CLASS)
                    .getMethod("getAveragePower", java.lang.String.class)
                    .invoke(mPowerProfile_, "battery.capacity");
            //s_status="batteryCapacity:"+batteryCapacity+"mAh";
            //Log.d("Miga","BatteryCapacity: "+batteryCapacity+" mAh");
            //return batteryCapacity;
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("Miga","getBatteryCapacity Exception: "+e);
        }
        return batteryCapacity;
    }
    //Miga for device initial create
    public class  Initial extends Thread{
        String [] tempCN,tempSSID;
        public void run() {
            JoinUpdateIPMultiCst();//加入multicast group,為了讓GO來接收連上他的client向他傳送的ip address(ip用來更新IPTable)
            JoinUpdatePeerMultiCst();//加入multicast group,為了讓所有member來更新peer table
            JoinUpdateCNMultiCst();//加入更新的Cluster Name multicast,為了讓手動建立的role更新CN
            JoinUpdateCCMultiCst();//用來讓Client在Step2傳送欲連線的info給GO
            JoinUpdateControllerMultiCst();//用來進行controller資料的傳輸
            JoinControllerNewConnectMultiCst();//用來讓controller傳送新的連線資訊
            ROLE = RoleFlag.NONE.getIndex();

            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null) {
                        GOpasswd = group.getPassphrase();
                        //20180520為了解決BRIDGE會抓錯的問題，因此將SSID以及CN都改為單純開頭是Android_xxxx的
                        if(group.getNetworkName()!=""||!group.getNetworkName().equals("null")){
                            tempSSID=group.getNetworkName().split("-");
                        }
                        //WiFiApName = group.getNetworkName(); //20180520以前的
                        WiFiApName = tempSSID[2];
                        Cluster_Name = WiFiApName;
                        GO_mac = group.getOwner().deviceAddress.toString();//這裡的GO_mac沒有用，抓到的是自己的mac address，因此加入了GO_SSID
                        GO_SSID = tempSSID[2];
                        //GO_SSID = group.getNetworkName();//用來於Step2判斷是否要連線的是自己的GO //20180520以前的
                        STATE = StateFlag.ADD_SERVICE.getIndex();//1
                        if(!group.getClientList().isEmpty()){
                            ROLE = RoleFlag.GO.getIndex();
                            IsP2Pconnect=true;//p2p已有人連上/被連上,目前主要是用來判斷是否可以開始進行Group內peer的計算
                            IsManual = true;//這個裝置是手動先連線的
                            CNTable.put(Cluster_Name,0);//將自己的CN放入CNTable
                        }

                    }
                }
            });
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (mConnectivityManager != null) {
                mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                if (mNetworkInfo.isConnected() == true && mNetworkInfo != null) {//wifi interface已連上
                    if(ROLE == RoleFlag.NONE.getIndex()){
                        ROLE = RoleFlag.CLIENT.getIndex();
                        Log.d("Miga", " ROLE:" + ROLE);
                    }else if(ROLE == RoleFlag.GO.getIndex()){//是GO又是Client
                        ROLE = RoleFlag.HYBRID.getIndex();
                        Log.d("Miga", " ROLE:" + ROLE);
                    }
                    if(ROLE == RoleFlag.CLIENT.getIndex()){
                        manager.requestConnectionInfo(channel, new WifiP2pManager.ConnectionInfoListener() {//manager.requestConnectionInfo: Request device connection Info
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {//onConnectionInfoAvailable: The requested connection info is available
                                if (info.groupFormed == true) {// groupFormed: Indicates if a p2p group has been successfully formed
                                    ROLE = RoleFlag.BRIDGE.getIndex();//wifi interface已連上,且也有p2p group
                                    IsP2Pconnect=true;//p2p已有人連上/被連上,目前主要是用來判斷是否可以開始進行Group內peer的計算
                                    IsManual = true;//這個裝置是手動先連線的
                                    Log.d("Miga", " ROLE:" + ROLE);
                                }
                            }
                        });
                    }
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group == null) {
                        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d("Miga", "initial createGroup Success");
                                if( ROLE == RoleFlag.CLIENT.getIndex()) {
                                    IsP2Pconnect = true;
                                    IsManual = true;//這個裝置是手動先連線的
                                    WiFiIpAddr = wifiIpAddress();//取得wifi IP address
                                    Log.d("Miga", "Initial/ROLE:" + ROLE + "Cluster_Name:" + Cluster_Name+" wifiIpAddress:"+WiFiIpAddr);
                                    s_status="state: WiFi Ip Address = "+WiFiIpAddr;
                                    if(!isOpenSWIAThread){//開啟client傳送wifi ip address thread給GO
                                        if (SendWiFiIpAddr == null) {
                                            SendWiFiIpAddr = new SendWiFiIpAddr();
                                            SendWiFiIpAddr.start();
                                        }
                                        isOpenSWIAThread=true;
                                    }
                                }
                            }
                            @Override
                            public void onFailure(int error) {
                                Log.d("Miga", "initial createGroup onFailure");
                            }
                        });
                    }
                }
            });

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }


            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null) {
                        GOpasswd = group.getPassphrase();
                        /*20180520 新增 Start*/
                        if(ROLE == RoleFlag.BRIDGE.getIndex()) {
                            WiFiApName = thisDeviceName;
                            //20180520為了解決BRIDGE會抓錯的問題，因此將SSID以及CN都改為單純開頭是Android_xxxx的
                            if(group.getNetworkName()!=""||!group.getNetworkName().equals("null")){
                                tempCN=group.getNetworkName().split("-");
                            }
                            Cluster_Name = tempCN[2];
                            GOpasswd = getDevicePwd(WiFiApName);//Bridge手動設置的密碼要去抓預設儲存好的才抓的到20180520
                            //Cluster_Name = group.getNetworkName();
                        }
                        else {
                            //20180520為了解決BRIDGE會抓錯的問題，因此將SSID以及CN都改為單純開頭是Android_xxxx的
                            if(group.getNetworkName()!=""||!group.getNetworkName().equals("null")){
                                tempSSID=group.getNetworkName().split("-");
                            }
                            WiFiApName = tempSSID[2];
                            Cluster_Name = WiFiApName;
                            //WiFiApName = group.getNetworkName();
                            //Cluster_Name = WiFiApName;
                        }
                        /*20180520 新增 End*/

                        //WiFiApName = group.getNetworkName();//20180520以前
                        //Cluster_Name = WiFiApName;//20180520以前
                        GO_mac = group.getOwner().deviceAddress.toString();//這裡的GO_mac沒有用，抓到的是自己的mac address，因此加入了GO_SSID
                        if(ROLE == RoleFlag.NONE.getIndex()) {//20180426只有NONE需要進來這裡更新GO_SSID，因為CLIENT在sendwifiipaddr已經進行更新了
                            GO_SSID = tempSSID[2];
                            //GO_SSID = group.getNetworkName();//用來於Step2判斷是否要連線的是自己的GO //20180520以前的
                            Log.d("Miga", "GO_SSID:" + GO_SSID);
                            s_status = "GO_SSID: " + GO_SSID;
                        }
                        STATE = StateFlag.ADD_SERVICE.getIndex();//1
                        //20180508加入Candidate Controller
                        CandidateController_set self = new CandidateController_set(WiFiApName, String.valueOf(power_level));
                        CandController_record.add(self);
                    }
                }
            });

            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            /*if (t_Send_Cluster_Name == null) {
                t_Send_Cluster_Name = new Send_Cluster_Name();
                t_Send_Cluster_Name.start();
            }
            if (t_Receive_Cluster_Name == null) {
                t_Receive_Cluster_Name = new Receive_Cluster_Name();
                t_Receive_Cluster_Name.start();
            }*/



            if(ROLE == RoleFlag.NONE.getIndex()) {//20180408加入，若剛開始不是已經設定好ROLE的裝置，則每個人都要移除掉自己的GROUP。這樣在serivce discvoery階段比較可以找到其他人
                manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("Miga", "remove group success");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.d("Miga", "remove group fail");
                    }
                });
            }

            IsInitial=true;

            Log.d("Miga", "State: Initial Complete , SSID : " + WiFiApName + " Cluster_Name : " + Cluster_Name + " ROLE:" + ROLE + " PeerTable:"+ PeerTable);
            s_status = "State: Initial Complete : " + " SSID : " + WiFiApName + " Cluster_Name : " + Cluster_Name + " ROLE:" + ROLE+ " PeerTable:"+ PeerTable;


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

        if (t_Send_Cluster_Name == null) {
            t_Send_Cluster_Name = new Send_Cluster_Name();
            t_Send_Cluster_Name.start();
        }
        if (t_Receive_Cluster_Name == null) {
            t_Receive_Cluster_Name = new Receive_Cluster_Name();
            t_Receive_Cluster_Name.start();
        }

        if(t_CanConnect == null){
            t_CanConnect = new CanConnect();
            t_CanConnect.start();
        }
        //20180518 Controller
        if(t_Conroller_Thread == null){
            t_Conroller_Thread = new Conroller_Thread();
            t_Conroller_Thread.start();
        }

        // </aqua0722>
        new Task().execute(State.On);
        serviceRunning = true;

        peerdiscover();//進行discoverPeers,一開始就先去搜尋附近有誰, Miga add 0226
        //STATE = StateFlag.WAITING.getIndex();
        STATE = StateFlag.ADD_SERVICE.getIndex();
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

    //20180518 For Controller
    public class Conroller_Thread extends Thread{
        private String SSID;
        int obj_num = 0;
        String Collect_contain = "";
        CandidateController_set tmp;
        ControllerData_set tmp_con;
        public void run() {
            try {
                while(true) {
                    if (ControllerAuto) {
                        while(IsControllerOpenFirstRond){//當收到自己新的的info，暫時先讓這個thread不要往下執行20180521
                            Thread.sleep(60000);//sleep 1 mins
                            if(IsNewConnectionComplete){//新的連線已完成，重新繼續執行此thread
                                IsControllerOpenFirstRond = false;
                                IsNewConnectionComplete = false;
                                SSID ="";Collect_contain="";obj_num=0;//初始化，下面要用

                            }
                        }
                        /*if(IsControllerOpenFirstRond){
                            Thread.sleep(60000);
                            while (true){
                                ;
                            }
                        }*/
                        Log.d("Miga","Conroller_Thread open");
                        if (t_Receive_info == null) {
                            t_Receive_info = new Receive_info();
                            t_Receive_info.start();
                            Log.d("Miga","t_Receive_info.start()");
                        }
                        if (t_Send_info == null) {
                            t_Send_info = new Send_info();
                            t_Send_info.start();
                            Log.d("Miga","t_Send_info.start()");
                        }
                        Log.d("Miga","RunNewConnectionTime: "+RunNewConnectionTime);
                        if(RunNewConnectionTime==0)
                            Thread.sleep(10000);//睡10秒，讓裝置之間能夠有充分的時間蒐集同個cluster內的其他裝置的SSID,POWER
                        else
                            Thread.sleep(90000);//sleep 1 mins
                        //進行Controller候選人的排序
                        Collections.sort(CandController_record, new Comparator<CandidateController_set>() {
                            public int compare(CandidateController_set o1, CandidateController_set o2) {
                                return o1.compareTo(o2);
                            }
                        });
                        SSID=CandController_record.get(0).getSSID();//取出排序後的第一個，表示他是被選為controller

                        //print出排序後的data, 檢查用
                        for (int i = 0; i < CandController_record.size(); i++) {
                            tmp = (CandidateController_set) CandController_record.get(i);
                            Collect_contain = Collect_contain + obj_num + " : " + tmp.toString() + " ";
                            obj_num++;
                        }
                        Log.d("Miga", "Conroller_Thread/CandController_record" + Collect_contain);
                        Collect_contain="";obj_num=0;//初始化，下面要用
                        TotaldevicesNum = CandController_record.size();
                        if(SSID.equals(WiFiApName)){//是controller
                            IsController = true;//這個裝置是Controller
                            Log.d("Miga","I'm the controller");

                            while(!IsNeighborCollect){
                                ;//等待蒐集完Neighbor資料才繼續往下做
                            }

                            while(!(TotaldevicesNum==Controller_record.size())){
                                //controller還沒蒐集完所有資料
                            }

                            //controller已蒐集完所有資料
                            Log.d("Miga", "Collect Neighbor data OK!");
                            first_round_Controller_record.clear();//清除前面的，避免重複儲存
                            for(int i = 0 ; i < Controller_record.size(); i++) {
                                first_round_Controller_record.add(Controller_record.get(i));
                            }
                            //進行first round排序，peer, power少的優先
                            Collections.sort(first_round_Controller_record, new Comparator<ControllerData_set>() {
                                public int compare(ControllerData_set o1, ControllerData_set o2) {
                                    return o1.FirstRoundcompareTo(o2);
                                }
                            });

                            //print出排序後的data, 檢查用
                            for (int i = 0; i < first_round_Controller_record.size(); i++) {
                                tmp_con = (ControllerData_set) first_round_Controller_record.get(i);
                                Collect_contain = Collect_contain + obj_num + " : " + tmp_con.toString() + " ";
                                obj_num++;
                            }
                            Log.d("Miga", "Conroller_Thread/first_round_Controller_record" + Collect_contain.trim());
                            Collect_contain="";obj_num=0;

                            IsControllerOpenFirstRond = true;
                            //開始處理每個Device配對問題
                            First_Round();
                        }else{
                            IsControllerOpenFirstRond = true;
                            //開啟用來接收controller要傳送新的連線info的thread
                            if (t_Receive_info_new_connect == null) {
                                t_Receive_info_new_connect = new Receive_info_new_connect();
                                t_Receive_info_new_connect.start();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Conroller_Thread Exception" + e.toString());
            }
        }
    }

    //20180519 大致上已完成，但尚未寫多個cluster串起來之後CN的更新
    public void First_Round (){
        ControllerData_set tmp,tempprint;
        CandidateController_set neighborprint;
        String[] tempNeighbor = null;//,ConnectNeighbor;
        int obj_num = 0;
        String Collect_contain = "",tempNeighbor_canconnect="",oldCN="",ConnectNeighbor="";


        for(int i =0 ; i < first_round_Controller_record.size(); i++){
            tempNeighbor = first_round_Controller_record.get(i).getNeighbor().split("@");
            if(Integer.valueOf(first_round_Controller_record.get(i).NeighborNum) == 1 ){
                if(!IsTheSameCN(tempNeighbor[0],first_round_Controller_record.get(i).getClusterName())) {//CN不相同才能連
                    //Log.d("Miga", "before edit: " + first_round_Controller_record.get(i).toString());
                    oldCN = first_round_Controller_record.get(i).getClusterName();//先把現在要連的這個人的舊有的ClusterName存起來
                    //只有一個鄰居，直接連他
                    tmp = new ControllerData_set(first_round_Controller_record.get(i).getSSID(), first_round_Controller_record.get(i).getNeighbor(),
                            first_round_Controller_record.get(i).getNeighborNum(), first_round_Controller_record.get(i).getPOWER(),
                            GetNeighborCN(tempNeighbor[0]), tempNeighbor[0], "None");
                    first_round_Controller_record.set(i, tmp);
                    //Log.d("Miga", "before edit: " + first_round_Controller_record.get(i).toString());
                    update_Neighbor_data(tempNeighbor[0], "X", "X", "X", "X", "X", "GO");
                    UpdateSameCNNeighbor(oldCN,GetNeighborCN(ConnectNeighbor));//把oldCluster相關的Device的clustername都更新為新的CN
                }
            }else{//鄰居有兩個以上
                for( int j =0; j< tempNeighbor.length; j++){//j是鄰居，鄰居數量若只有一個的話就是length==1
                    if(!IsTheSameCN(tempNeighbor[j],first_round_Controller_record.get(i).getClusterName())){//檢查鄰居的CN是否相同
                        //不相同才能列入考慮連線的
                        //檢查鄰居的電量有沒有比自己高
                        if(NeighborBatterHigher(tempNeighbor[j],first_round_Controller_record.get(i).getPOWER())){
                            //電量比自己高
                            tempNeighbor_canconnect = tempNeighbor_canconnect + tempNeighbor[j]+"@";//這裡就是儲存可以被連線的鄰居候選人
                            //20180520 CandNeighbor_record於NeighborBatterHigher加入了
                        }
                    }
                }
                if(CandNeighbor_record.size()!=0) {//避免執行到電量都比鄰居高的device會跳出Exception (IndexOutOfBoundsException: Invalid index 0, size is 0)
                    Collections.sort(CandNeighbor_record, new Comparator<CandidateController_set>() {
                        public int compare(CandidateController_set o1, CandidateController_set o2) {
                            return o1.compareTo(o2);
                        }
                    });

                    ConnectNeighbor = CandNeighbor_record.get(0).getSSID();//取出排序後的第一個，表示他是被選為最高電量的neighbor

                    //print出排序後的data, 檢查用
                    for (int k = 0; k < CandNeighbor_record.size(); k++) {
                        neighborprint = (CandidateController_set) CandNeighbor_record.get(k);
                        Collect_contain = Collect_contain + obj_num + " : " + neighborprint.toString() + " ";
                        obj_num++;
                    }
                    Log.d("Miga", "First_Round/"+ first_round_Controller_record.get(i).getSSID() + " , CandNeighbor_record: "+ Collect_contain);
                    Collect_contain = "";
                    obj_num = 0;//初始化，下面要用
                }
                //Log.d("Miga",first_round_Controller_record.get(i).getSSID()+" tempNeighbor_canconnect: " +tempNeighbor_canconnect);
                //檢查完鄰居CN以及電量後，去選擇可以連的鄰居
                if(!ConnectNeighbor.equals("")||ConnectNeighbor!=""){//表示有鄰居候選人
                    oldCN = first_round_Controller_record.get(i).getClusterName();//先把現在要連的這個人的舊有的ClusterName存起來
                    //取鄰居最高的那個
                    //ConnectNeighbor = tempNeighbor_canconnect.split("@");
                    //去連最高的
                    tmp = new ControllerData_set(first_round_Controller_record.get(i).getSSID(), first_round_Controller_record.get(i).getNeighbor(),
                            first_round_Controller_record.get(i).getNeighborNum(), first_round_Controller_record.get(i).getPOWER(),
                            GetNeighborCN(ConnectNeighbor), ConnectNeighbor, "None");
                    first_round_Controller_record.set(i, tmp);
                    //Log.d("Miga", "before edit: " + first_round_Controller_record.get(i).toString());
                    //更新鄰居的info
                    update_Neighbor_data(ConnectNeighbor, "X", "X", "X", "X", "X", "GO");
                    UpdateSameCNNeighbor(oldCN,GetNeighborCN(ConnectNeighbor));//把oldCluster相關的Device的clustername都更新為新的CN

                }
            }
            tempNeighbor_canconnect="";
            CandNeighbor_record.clear();//初始化，給下一個device用
            ConnectNeighbor="";//初始化，給下一個device用
        }
        //print出排序後的data, 檢查用
        for (int i = 0; i < first_round_Controller_record.size(); i++) {
            tempprint = (ControllerData_set) first_round_Controller_record.get(i);
            Collect_contain = Collect_contain + obj_num + " : " + tempprint.toString() + " ";
            obj_num++;
        }
        Log.d("Miga", "First_Round/first_round_Controller_record" + Collect_contain);
        //Second Round
        Second_Round();
    }

    public void Second_Round(){

        //把第一階段整理完後的資料丟到第二階段
        second_round_Controller_record.clear();//清除前面的，避免重複儲存
        for(int i = 0 ; i < first_round_Controller_record.size(); i++) {
            second_round_Controller_record.add(first_round_Controller_record.get(i));
        }
        List<String> Each_Cluster_name = new ArrayList<String>();//儲存不同的CN

        for(int i = 0; i< second_round_Controller_record.size(); i ++ ){
            if(!Each_Cluster_name.contains(second_round_Controller_record.get(i).getClusterName())){
                Each_Cluster_name.add(second_round_Controller_record.get(i).getClusterName());
            }
        }
        if(Each_Cluster_name.size()==1) {
            //把第一階段整理完後的資料丟到最後階段
            Final_Controller_record.clear();//清除前面的，避免重複儲存
            for(int i = 0 ; i < second_round_Controller_record.size(); i++) {
                Final_Controller_record.add(second_round_Controller_record.get(i));
            }
            Log.d("Miga", "After Second Round, it only one cluster!");
            IsSecondRoundOver = true;//可以開始進行新的資訊傳送
            //開啟只有Controller才可以使用的thread，是用來讓controller最後傳送新的連線info用
            if (t_Send_info_new_connect == null) {
                t_Send_info_new_connect = new Send_info_new_connect();
                t_Send_info_new_connect.start();
            }
            //這邊先保留controller沒有開啟Receive_info_new_connect
            /*if (t_Receive_info_new_connect == null) {
                t_Receive_info_new_connect = new Receive_info_new_connect();
                t_Receive_info_new_connect.start();
            }*/
        }
        else
            Log.d("Miga","There are many cluster need to combine ...");

        //Each_Cluster_name = null;
    }
    //每個裝置做完一次改變後，也要去更新neighbor的
    public void update_Neighbor_data(String SSID,String Neighbor,String NeighborNum,String POWER,String ClusterName,String WiFiInterface,String P2PInterface){
        ControllerData_set tmp;
        for( int i =0; i<first_round_Controller_record.size();i++){
            if(SSID.equals(first_round_Controller_record.get(i).getSSID())){
                //抓出要更新的
                //只有一個鄰居，直接連他
                //Log.d("Miga","update_Neighbor_data/before edit: "+first_round_Controller_record.get(i).toString());
                //傳入的值若是"X"，則表示不做更新
                tmp = new ControllerData_set((SSID.equals("X"))?first_round_Controller_record.get(i).getSSID():SSID,
                        (Neighbor.equals("X"))?first_round_Controller_record.get(i).getNeighbor():Neighbor,
                        (NeighborNum.equals("X"))?first_round_Controller_record.get(i).getNeighborNum():NeighborNum,
                        (POWER.equals("X"))?first_round_Controller_record.get(i).getPOWER():POWER,
                        (ClusterName.equals("X"))?first_round_Controller_record.get(i).getClusterName():ClusterName,
                        (WiFiInterface.equals("X"))?first_round_Controller_record.get(i).getWiFiInterface():WiFiInterface,
                        (P2PInterface.equals("X"))?first_round_Controller_record.get(i).getP2PInterface():P2PInterface
                );
                first_round_Controller_record.set(i,tmp);
                //Log.d("Miga","update_Neighbor_data/before edit: "+first_round_Controller_record.get(i).toString());
                break;
            }
        }
    }
    //檢查neighbor和自己的CN是不是一樣
    public boolean IsTheSameCN(String NeighborSSID, String myCN){
        for( int i =0; i<first_round_Controller_record.size();i++){
            if(NeighborSSID.equals(first_round_Controller_record.get(i).getSSID())){
                if(myCN.equals(first_round_Controller_record.get(i).getClusterName())){
                    return true;//Neighbor和自己的CN是一樣的
                }
                else
                    return false;//Neighbor和自己的CN不同
            }
        }
        return false;
    }
    //檢查neighbor電量有沒有比我高，有回true
    public boolean NeighborBatterHigher(String NeighborSSID, String myBattery){
        for( int i =0; i<first_round_Controller_record.size();i++){
            if(NeighborSSID.equals(first_round_Controller_record.get(i).getSSID())){
                if(Integer.valueOf(first_round_Controller_record.get(i).getPOWER()) >= Integer.valueOf(myBattery)){
                    CandidateController_set data = new CandidateController_set(NeighborSSID, first_round_Controller_record.get(i).getPOWER());
                    if (!CandNeighbor_record.contains(data)) {//將電量比自己高的Neighbor放入
                        if(!CandNeighbor_record.equals(data))
                            CandNeighbor_record.add(data);
                    }
                    return true;//Neighbor電量比我高
                }
                else
                    return false;//Neighbor電量比我低
            }
        }
        return false;
    }
    //連上其他人之後，更新舊有的相同CN內其他member的CN
    public void UpdateSameCNNeighbor(String OldCN,String NewCN){
        for ( int i = 0; i< first_round_Controller_record.size(); i++){
            if(OldCN.equals(first_round_Controller_record.get(i).getClusterName())){
                update_Neighbor_data(first_round_Controller_record.get(i).getSSID(), "X", "X", "X", NewCN, "X", "X");
            }
        }
    }
    //取得Neighbor的CN
    public String GetNeighborCN(String NeighborSSID){
        for( int i =0; i<first_round_Controller_record.size();i++){
            if(NeighborSSID.equals(first_round_Controller_record.get(i).getSSID())){
                return first_round_Controller_record.get(i).getClusterName();
            }
        }
        return "DontKonw";//沒找到Neighbor
    }

    public class Send_info extends Thread {
        private DatagramPacket dp;
        private DatagramSocket senddsk;
        private Iterator iterator;
        private String message, tempkey;
        MulticastSocket multicsk;//Miga20180313
        DatagramPacket msgPkt;//Miga

        public void run() {
            try{
                senddsk = null;
                senddsk = new DatagramSocket();
                //Log.d("Miga","Send_info onpe");
                while(ControllerAuto) {
                    //Log.d("Miga","Send_info ControllerAuto");
                    if (IsP2Pconnect) {
                        if(IsController){
                            if(IsControllerOpenFirstRond){
                                return;//Controller已開啟first round,因此結束這個thread
                            }
                        }else{
                            if(IsReceiveMyself){
                                return;//不是Controller，當收到自己新的的info,因此結束這個thread
                            }
                        }
                        //Log.d("Miga","Send_info IsP2Pconnect");
                        int randomnum = randomWithRange(2,3)*1000;
                        Thread.sleep(randomnum);
                        //Log.d("Miga","Sleep:"+randomnum);
                        if( ControllerAuto && IsNeighborCollect ){ //要執行controller且已經蒐集完鄰居的資料了
                            message = WiFiApName + "#" + NeighborList + "#" +Integer.toString(NeighborListNum) + "#" + Integer.toString(power_level) + "#" + WiFiApName + "#" + "None"+ "#" + "None";//SSID,Neighbor,Neighbornum,Power,ClusterName,WiFi interface,P2P interface
                        }else if(!IsNeighborCollect) {//還沒蒐集完neighbor就跑舊有info
                            message = WiFiApName + "#" + Cluster_Name + "#" + "5" + "#" + Integer.toString(power_level);// 0: source SSID 1: cluster name 2: TTL 3:電池電量 //電池電量新增於20180508(Controller判斷用)
                        }
                        senddsk = null;// 20180520 關socket
                        senddsk = new DatagramSocket();// 20180520 關socket
                        // unicast
                        iterator = IPTable.keySet().iterator();//IPTable的keySet為許多IP所組成
                        while (iterator.hasNext()) {
                            tempkey = iterator.next().toString();
                            dp = new DatagramPacket(message.getBytes(), message.length(),
                                    InetAddress.getByName(tempkey), IP_port_controller_collect);
                            senddsk.send(dp);//一一傳送給IPTable內的所有IP
                            //Log.v("Miga", "I send unicast message:" + message);
                            //s_status="I send unicast message:"+message;

                        }

                        //for BRIDGE，20180519解決BRIDGE無法傳送給GO的問題
                        dp = new DatagramPacket(message.getBytes(), message.length(),
                                InetAddress.getByName("192.168.49.1"), IP_port_controller_collect);
                        senddsk.send(dp);

                        //multicast
                        if (mConnectivityManager != null) {
                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                            if (mNetworkInfo.isConnected()) {
                                multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                                multicsk = null;// 20180520 關socket
                                multicsk = new MulticastSocket(6793);//6793: for controller
                                msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6793);
                                multicsk.send(msgPkt);
                                //Log.v("Miga", "multicsk send message:" + message);
                                //s_status = "multicsk send message" + message;
                            }
                        }
                        senddsk.close();// 20180520 關socket
                        if(multicsk!=null)
                            multicsk.close();// 20180520 關socket

                    }
                    //Thread.sleep(1000);

                }
            } catch (SocketException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_info Socket exception" + e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_info IOException" + e.toString());
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_info InterruptedException" + e.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Send_info other exception" + e.toString());
            } finally {
                if (senddsk != null) {
                    senddsk.close();
                    Log.d("Miga", "Send_info senddsk is close");
                }
                if(multicsk!=null){
                    multicsk.close();// 20180520 關socket
                    Log.d("Miga", "Send_info multicsk is close");
                }
                if(t_Send_info!=null){
                    t_Send_info = null;
                    Log.d("Miga", "t_Send_info = null");
                }
            }
        }
    }
    /*關閉順序：
    * 1. Receive_info_uni
    * 2. Receive_info_multi
    * 3. Receive_info
    * */
    public class Receive_info extends Thread {
        private byte[] lMsg,buf;
        private DatagramPacket receivedp, senddp;
        private DatagramSocket senddsk;
        private Iterator iterator;
        private String message, tempkey, recv_ip;
        private String[] temp;
        private MulticastSocket multicsk;//Miga20180312


        private String recMessagetemp,recWiFiIpAddr;
        private String[] recMessage;
        private int i;
        private DatagramPacket msgPkt;//Miga
        private boolean isreceiveformbridge=false;

        private boolean isAddMyself = false;


        public void run() {

            try{
                //20180326將multicast和unicast的socket分成兩個thread來寫，小的thread所接收的data會用global來儲存，再由此thread來處理資料。
                lMsg_cinfo = new byte[8192];
                receivedpkt_cinfo = new DatagramPacket(lMsg_cinfo, lMsg_cinfo.length);//接收到的message會存在IMsg
                //receivedskt_cinfo = new DatagramSocket(IP_port_controller_collect);

                if (receivedskt_cinfo == null) {
                    Log.d("Miga", "receivedskt_cinfo == null");
                    receivedskt_cinfo = new DatagramSocket(null);
                    receivedskt_cinfo.setReuseAddress(true);
                    receivedskt_cinfo.setBroadcast(true);
                    receivedskt_cinfo.bind(new InetSocketAddress(IP_port_controller_collect));
                } else {
                    Log.d("Miga", "receivedskt_cinfo != null");
                }

                if (t_Receive_info_uni == null) {
                    t_Receive_info_uni = new Receive_info_uni();
                    t_Receive_info_uni.start();
                }
                if (t_Receive_info_multi == null) {
                    t_Receive_info_multi = new Receive_info_multi();
                    t_Receive_info_multi.start();
                }
                while(ControllerAuto){

                    if(RecvMsg_cinfo!="") {
                        if(IsController){
                            if(IsControllerOpenFirstRond){
                                return;//Controller已開啟first round,因此結束這個thread
                            }
                        }else{
                            if(IsReceiveMyself){
                                if(t_Receive_info_uni==null && t_Receive_info_multi==null)//小的都關完才官大的
                                    return;//不是Controller，當收到自己新的的info,因此結束這個thread
                            }
                        }
                        try {//新的topology改變後，GO需要蒐集Client的IP，才能去做轉傳pkt
                            if (receivedpkt_cinfo != null) {
                                InetAddress WiFiIPAddress = receivedpkt_cinfo.getAddress();
                                //if(RunNewConnectionTime>0)
                                //    Log.d("Miga","receivedpkt_cinfo:"+receivedpkt_cinfo+", WiFiIPAddress:"+WiFiIPAddress);
                                if(WiFiIPAddress!= null) {
                                    String clientip = WiFiIPAddress.toString().split("/")[1];//接收CLIENT的IP
                                    if (!clientip.equals("192.168.49.1")) {//不是GO(GO的不存在CLINET內)
                                        if (!IPTable.containsKey(clientip)) {//避免client在sendWiFiIPAddress沒有成功傳來，因此直接在這邊做IPTable的儲存->SendWiFiIPAddress和Collect_IPServer之後可以拿掉了
                                            IPTable.put(clientip, 0);
                                            Log.d("Miga", "Receive_info/IPTable:" + IPTable);
                                            s_status = "Receive_info/IPTable:" + IPTable;
                                        }
                                    }
                                }
                            }
                        }catch (Exception e){
                            Log.d("Miga","Receive_info/receivedpkt_cinfo.getAddress() Exception: " +e.toString());
                        }
                        //Log.d("Miga","RECE:"+RecvMsg_cinfo);
                        temp = RecvMsg_cinfo.split("#");//將message之中有#則分開存到tmep陣列裡;message = WiFiApName + "#" + Cluster_Name + "#" + "5"+"#"+POWER+ "#" +ROLE;
                        if( ControllerAuto && IsNeighborCollect ){ //要執行controller且已經蒐集完鄰居的資料了
                            if(IsController){
                                //ControllerData_set
                                if(!isAddMyself) {
                                    //也加入自己的data
                                    ControllerData_set self = new ControllerData_set(WiFiApName, NeighborList, Integer.toString(NeighborListNum),
                                            String.valueOf(power_level), WiFiApName, "None", "None");
                                    if (!Controller_record.contains(self)) {
                                        Controller_record.add(self);
                                    }
                                    isAddMyself = true;//已加入自己的 data到controller_record內
                                }
                                if(temp.length > 5) {//用來存Controller的資料
                                    //也加入自己的data
                                    ControllerData_set others = new ControllerData_set(temp[0], temp[1], temp[2],
                                            temp[3], temp[4], temp[5], temp[6]);
                                    if (!Controller_record.contains(others)) {
                                        Controller_record.add(others);
                                    }
                                }
                                //目的應該只是要print出有收集到哪些data
                                int obj_num = 0;
                                String Collect_contain = "";
                                ControllerData_set tmp;
                                for (int i = 0; i < Controller_record.size(); i++) {
                                    tmp = (ControllerData_set) Controller_record.get(i);
                                    Collect_contain = Collect_contain + obj_num + " : " + tmp.toString() + " ";
                                    obj_num++;
                                }
                                Log.d("Miga", "0Receive_info/Controller_record: " + Collect_contain);
                                int randomnum = randomWithRange(2,3)*1000;
                                Thread.sleep(randomnum);
                            }else{//不是Controller的要幫忙Relay packet
                                try {
                                    message = RecvMsg_cinfo;
                                    senddsk = null;
                                    senddsk = new DatagramSocket();
                                    // unicast
                                    iterator = IPTable.keySet().iterator();
                                    while (iterator.hasNext()) {
                                        tempkey = iterator.next().toString();
                                        if (!recv_ip.equals(tempkey)) {
                                            senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                    InetAddress.getByName(tempkey), IP_port_controller_collect);
                                            senddsk.send(senddp);
                                            //Log.d("Miga", "(Relay)Send the message: " + message + " to " + tempkey);
                                            //s_status = "State : (Relay)Send the message: " + message + " to " + tempkey;
                                        }
                                    }
                                    if(ROLE == RoleFlag.BRIDGE.getIndex()) {
                                        //for BRIDGE
                                        senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                InetAddress.getByName("192.168.49.1"), IP_port_controller_collect);
                                        senddsk.send(senddp);
                                    }

                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Log.d("Miga", "0Receive_info Exception : at relay pkt (unicast) :" + e.toString());
                                    s_status = "0Receive_info Exception : at relay pkt (unicast) :" + e.toString();
                                }
                                try {
                                    if (ROLE == RoleFlag.HYBRID.getIndex() || ROLE == RoleFlag.BRIDGE.getIndex()) {
                                        if (ROLE == RoleFlag.HYBRID.getIndex()) {//20180501 新增HYBRID轉傳給CLIENT的，測試可成功
                                            // broadcast
                                            senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                    InetAddress.getByName("192.168.49.255"), IP_port_controller_collect);
                                            senddsk.send(senddp);
                                        }
                                        //multicast
                                        if (mConnectivityManager != null) {
                                            mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                            if (mNetworkInfo.isConnected()) {
                                                multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                                                multicsk = null;
                                                multicsk = new MulticastSocket(6793);//6790: for peertable update
                                                msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6793);
                                                multicsk.send(msgPkt);
                                                multicsk.close();
                                                //Log.v("Miga", "multicsk send message:" + message);
                                                //s_status = "multicsk send message" + message;
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Log.d("Miga", "0Receive_info Exception : at relay pkt (multicast) _" + e.toString());
                                    s_status = "0Receive_info Exception : at relay pkt (multicast) _" + e.toString();
                                }
                                if (senddsk != null)
                                    senddsk.close();// 20180520 關socket
                                if(multicsk!=null)
                                    multicsk.close();// 20180520 關socket
                            }

                        }else if(!IsNeighborCollect) {//在鄰居資料蒐集完之前都是跑舊的
                            if (temp[0] != null && temp[1] != null && temp[2] != null && WiFiApName != null) {
                                if (Newcompare(temp[0], WiFiApName) != 0) {//接收到的data和此裝置的SSID不同; 若A>B則reutrn 1



                                    /*try {
                                        InetAddress P2PIPAddress = receivedpkt_cinfo.getAddress();
                                        if(RunNewConnectionTime>0) {
                                            Log.d("Miga", "I got message from: " + RecvMsg_pc);
                                            s_status = "I got message from unicast" + RecvMsg_pc;
                                            Log.d("Miga", "P2PIPAddress: " + P2PIPAddress);
                                            s_status = "P2PIPAddress: " + P2PIPAddress;
                                        }
                                        recv_ip = P2PIPAddress.toString().split("/")[1];//接收傳這個pkt的ip
                                    }catch (Exception e){
                                        e.printStackTrace();
                                        Log.d("Miga", "Receive_info receivedpkt_cinfo.getAddress() exception" + e.toString());
                                    }*/

                                    // TTL -1
                                    try {
                                        if (Integer.valueOf(temp[2].trim()) > 0) {
                                            temp[2] = String.valueOf(Integer.valueOf(temp[2].trim()) - 1).trim();//經過一個router因此-1
                                        }
                                        //Log.d("Miga","Temp[2]:"+temp[2]);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Log.d("Miga", "Receive_info Exception : at temp[2] _" + e.toString());
                                        s_status = "Receive_info Exception : at temp[2] _" + e.toString();
                                    }

                                    if (!CandidateControllerTable.containsKey(temp[0])) {//CandidateControllerTable留著只是輔助用，用來新增CandController_record的
                                        CandidateControllerTable.put(temp[0], Integer.valueOf(temp[3]));//SSID跟電池電量
                                        CandidateController_set data = new CandidateController_set(temp[0], temp[3]);
                                        if (!CandController_record.contains(data)) {
                                            CandController_record.add(data);
                                        }
                                        //目的應該只是要print出有收集到哪些data
                                        int obj_num = 0;
                                        String Collect_contain = "";
                                        CandidateController_set tmp;
                                        for (int i = 0; i < CandController_record.size(); i++) {
                                            tmp = (CandidateController_set) CandController_record.get(i);
                                            Collect_contain = Collect_contain + obj_num + " : " + tmp.toString() + " ";
                                            obj_num++;
                                        }
                                        Log.d("Miga", "CandController_record" + Collect_contain);
                                        s_status = "CandController_record: " + Collect_contain;
                                        //Log.d("Miga", "CandidateControllerTable: " + CandidateControllerTable);
                                    }

                                    try {
                                        // relay packet
                                        if (Integer.valueOf(temp[2].trim()) > 0) {
                                            message = temp[0] + "#" + temp[1] + "#" + temp[2].trim() + "#" + temp[3];
                                            senddsk = null;
                                            senddsk = new DatagramSocket();
                                            try {
                                                // unicast
                                                iterator = IPTable.keySet().iterator();
                                                while (iterator.hasNext()) {
                                                    tempkey = iterator.next().toString();
                                                    //if (!recv_ip.equals(tempkey)) {
                                                        senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                                InetAddress.getByName(tempkey), IP_port_controller_collect);
                                                        senddsk.send(senddp);
                                                        //Log.d("Miga", "(Relay)Send the message: " + message + " to " + tempkey);
                                                        //s_status = "State : (Relay)Send the message: " + message + " to " + tempkey;
                                                    //}
                                                }
                                                if(ROLE==RoleFlag.BRIDGE.getIndex()) {
                                                    //for BRIDGE
                                                    senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                            InetAddress.getByName("192.168.49.1"), IP_port_controller_collect);
                                                    senddsk.send(senddp);
                                                }

                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                Log.d("Miga", "Receive_info Exception : at relay pkt (unicast) _" + e.toString());
                                                s_status = "Receive_info Exception : at relay pkt (unicast) _" + e.toString();
                                            }
                                            try {
                                                if (ROLE == RoleFlag.HYBRID.getIndex() || ROLE == RoleFlag.BRIDGE.getIndex()) {
                                                    if (ROLE == RoleFlag.HYBRID.getIndex()) {//20180501 新增HYBRID轉傳給CLIENT的，測試可成功
                                                        // broadcast
                                                        senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                                InetAddress.getByName("192.168.49.255"), IP_port_controller_collect);
                                                        senddsk.send(senddp);
                                                    }
                                                    //multicast
                                                    if (mConnectivityManager != null) {
                                                        mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                                        if (mNetworkInfo.isConnected()) {
                                                            multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                                                            multicsk = null;
                                                            multicsk = new MulticastSocket(6793);//6790: for peertable update
                                                            msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6793);
                                                            multicsk.send(msgPkt);
                                                            multicsk.close();
                                                            //Log.v("Miga", "multicsk send message:" + message);
                                                            //s_status = "multicsk send message" + message;
                                                        }
                                                    }
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                                Log.d("Miga", "Receive_info Exception : at relay pkt (multicast) _" + e.toString());
                                                s_status = "Receive_info Exception : at relay pkt (multicast) _" + e.toString();
                                            }
                                        }
                                        if (senddsk != null)
                                            senddsk.close();// 20180520 關socket
                                        if(multicsk!=null)
                                            multicsk.close();// 20180520 關socket
                                        /*if (multicsk != null)
                                            multicsk.close();
                                        if (senddsk != null)
                                            senddsk.close();*/
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Log.d("Miga", "Receive_info Exception : // relay packet _" + e.toString());
                                        s_status = "Receive_info Exception : // relay packet _" + e.toString();
                                    }
                                }
                            }//End if(temp[0]!=null&&temp[1]!=null&&...)
                        }
                    }
                }
            }catch (SocketException e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info Socket exception" + e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info IOException" + e.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info Exception" + e.toString());
            } finally {
                if (senddsk != null) {
                    senddsk.close();
                    Log.d("Miga", "Receive_info sendds is close");
                }
                if(multicsk!=null){
                    multicsk.close();// 20180520 關socket
                    Log.d("Miga", "Receive_info multicsk is close");
                }
                if(t_Receive_info!=null){
                    t_Receive_info = null;
                    Log.d("Miga", "t_Receive_info = null");
                }

            }
        }
    }
    public class Receive_info_uni extends Thread{
        public void run() {
            try {
                while(true){//20180518 Controller 開啟時
                    if(IsController){
                        if(IsControllerOpenFirstRond){
                            return;//Controller已開啟first round,因此結束這個thread
                        }
                    }else{
                        if(IsReceiveMyself){
                            return;//不是Controller，當收到自己新的的info,因此結束這個thread
                        }
                    }
                    if (IsP2Pconnect&&ControllerAuto) {
                        if (receivedpkt_cinfo != null) {
                            //20180411註解下面，因為ROLE==GO時也會用unicast接收
                            //if (ROLE == RoleFlag.CLIENT.getIndex() || ROLE == RoleFlag.HYBRID.getIndex()|| ROLE == RoleFlag.BRIDGE.getIndex()) {
                            //unicast
                            receivedskt_cinfo.receive(receivedpkt_cinfo);//把接收到的data存在receivedp.
                            RecvMsg_cinfo = new String(lMsg_cinfo, 0, receivedpkt_cinfo.getLength());//將接收到的IMsg轉換成String型態
                            //Log.d("Miga", "I got message from unicast" + RecvMsg_cinfo);
                            //s_status = "I got message from unicast" + RecvMsg_cinfo;
                            //}
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info_uni Exception" + e.toString());
            } finally {
                if(receivedskt_cinfo!=null){
                    receivedskt_cinfo.close();
                    receivedskt_cinfo =null;
                    Log.d("Miga", "Receive_info_uni/receivedskt_cinfo.close()");
                }
                if(t_Receive_info_uni != null){
                    t_Receive_info_uni = null;
                    Log.d("Miga", "t_Receive_info_uni = null");
                }
            }
        }
    }
    public class Receive_info_multi extends Thread{
        public void run() {
            try {
                while(true){//20180518 Controller
                    if(IsController){
                        if(IsControllerOpenFirstRond){
                            if(t_Receive_info_uni==null) //uni關了我才關
                                return;//Controller已開啟first round,因此結束這個thread
                        }
                    }else{
                        if(IsReceiveMyself){
                            if(t_Receive_info_uni==null) //uni關了我才關
                                return;//不是Controller，當收到自己新的的info,因此結束這個thread
                        }
                    }
                    if (IsP2Pconnect&&ControllerAuto) {
                        if (receivedpkt_cinfo != null) {
                            if (ROLE == RoleFlag.GO.getIndex() || ROLE == RoleFlag.HYBRID.getIndex()) {
                                //multicast
                                recvControllerSocket.receive(receivedpkt_cinfo);//recvControllerSocket port:6790
                                RecvMsg_cinfo = new String(lMsg_cinfo, 0, receivedpkt_cinfo.getLength());//將接收到的IMsg轉換成String型態
                                /*if(ROLE == RoleFlag.HYBRID.getIndex()) {
                                    Log.d("Miga", "I got message from multicast" + RecvMsg_pc);
                                    s_status = "I got message from multicast" + RecvMsg_pc;
                                }*/
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info_multi Exception" + e.toString());
            } finally {
                if(receivedpkt_cinfo!=null){
                    receivedpkt_cinfo = null;
                    Log.d("Miga", "Receive_info_multi/receivedpkt_cinfo = null");
                }
                if(t_Receive_info_multi != null){
                    t_Receive_info_multi = null;
                    Log.d("Miga", "t_Receive_info_multi = null");
                }
            }
        }
    }


    public class Send_info_new_connect extends Thread{

        private DatagramPacket dp;
        private DatagramSocket senddsk;
        private Iterator iterator;
        private String message, tempkey;
        MulticastSocket multicsk;//Miga20180313
        DatagramPacket msgPkt;//Miga
        int send_time=0;

        public void run(){
            try{
                senddsk = null;
                senddsk = new DatagramSocket();
                //Log.d("Miga","Send_info onpe");
                while(ControllerAuto) {
                    if(IsNewConnection){//當收到自己已開始進行新的連線，則結束著個thread 20180522
                        return;
                    }
                    //Log.d("Miga","Send_info ControllerAuto");
                    if (IsSecondRoundOver) {
                        //Log.d("Miga","Send_info IsP2Pconnect");
                        int randomnum = randomWithRange(2,3)*1000;
                        Thread.sleep(randomnum);
                        //迴圈來傳送每一個device的連線msg
                        for(int i = 0; i < Final_Controller_record.size();i++) {
                            if(!WiFiApName.equals(Final_Controller_record.get(i).getSSID())) {//不是controller的才傳
                                message = Final_Controller_record.get(i).toString_Final();
                                senddsk = null;
                                senddsk = new DatagramSocket();
                                //message = WiFiApName + "#" + NeighborList + "#" +Integer.toString(NeighborListNum) + "#" + Integer.toString(power_level) + "#" + WiFiApName + "#" + "None"+ "#" + "None";//SSID,Neighbor,Neighbornum,Power,ClusterName,WiFi interface,P2P interface

                                // unicast
                                iterator = IPTable.keySet().iterator();//IPTable的keySet為許多IP所組成
                                while (iterator.hasNext()) {
                                    tempkey = iterator.next().toString();
                                    dp = new DatagramPacket(message.getBytes(), message.length(),
                                            InetAddress.getByName(tempkey), IP_port_controller_new_connect);
                                    senddsk.send(dp);//一一傳送給IPTable內的所有IP
                                    //Log.v("Miga", "I send unicast message:" + message);
                                    //s_status="I send unicast message:"+message;

                                }

                                //multicast
                                if (mConnectivityManager != null) {
                                    mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                    if (mNetworkInfo.isConnected()) {
                                        multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                                        multicsk = null;// 20180520 關socket
                                        multicsk = new MulticastSocket(6795);//6795: for controller
                                        msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6795);
                                        multicsk.send(msgPkt);
                                        //Log.v("Miga", "multicsk send message:" + message);
                                        //s_status = "multicsk send message" + message;
                                    }
                                }
                            }else{
                                MyNewConnectInfo = Final_Controller_record.get(i).toString_Final();//Controller取得自己的
                                if(send_time == 3) {
                                    IsReceiveMyself = true;//Controllert傳送5次後，就不在傳送了
                                    //New_Connection_Func();//Controller也開啟新的連線
                                    if(t_New_Connection_Func==null){
                                        t_New_Connection_Func = new New_Connection_Func_t();
                                        t_New_Connection_Func.start();
                                    }
                                }
                            }
                            Thread.sleep(500);
                        }//End for Final_Controller_record.size()
                        send_time += 1 ;
                    }//End IsSecondRoundOver
                    //Thread.sleep(1000);
                    senddsk.close();// 20180520 關socket
                    if(multicsk!=null)
                        multicsk.close();// 20180520 關socket

                }
            } catch (SocketException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_info_new_connect Socket exception" + e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_info_new_connect IOException" + e.toString());
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.d("Miga", "Send_info_new_connect InterruptedException" + e.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Send_info_new_connect other exception" + e.toString());
            } finally {
                if (senddsk != null) {
                    senddsk.close();
                    Log.d("Miga", "Send_info_new_connect senddsk is close");
                }
                if(multicsk!=null){
                    multicsk.close();// 20180520 關socket
                Log.d("Miga", "Send_info_new_connect multicsk is close");
                }
                if(t_Send_info_new_connect!=null){
                    t_Send_info_new_connect = null;
                    Log.d("Miga", "t_Send_info_new_connect = null");
                }
            }
        }
    }
    public class Receive_info_new_connect extends Thread{

        private DatagramPacket senddp;
        private DatagramSocket senddsk;
        private Iterator iterator;
        private String message, tempkey, recv_ip;
        private String[] temp;
        private MulticastSocket multicsk;//Miga20180312
        private DatagramPacket msgPkt;//Miga
        private boolean isAddMyself = false;


        public void run(){
            try{
                //20180326將multicast和unicast的socket分成兩個thread來寫，小的thread所接收的data會用global來儲存，再由此thread來處理資料。
                lMsg_cinfo_nwconnect = new byte[8192];
                receivedpkt_cinfo_nwconnect = new DatagramPacket(lMsg_cinfo_nwconnect, lMsg_cinfo_nwconnect.length);//接收到的message會存在IMsg
                if (receivedskt_cinfo_nwconnect == null) {
                    Log.d("Miga", "receivedskt_cinfo_nwconnect == null");
                    receivedskt_cinfo_nwconnect = new DatagramSocket(null);
                    receivedskt_cinfo_nwconnect.setReuseAddress(true);
                    receivedskt_cinfo_nwconnect.setBroadcast(true);
                    receivedskt_cinfo_nwconnect.bind(new InetSocketAddress(IP_port_controller_new_connect));
                } else {
                    Log.d("Miga", "receivedskt_cinfo_nwconnect != null");
                }
                if (t_Receive_info_uni_new_connect == null) {
                    t_Receive_info_uni_new_connect = new Receive_info_uni_new_connect();
                    t_Receive_info_uni_new_connect.start();
                }
                if (t_Receive_info_multi_new_connect == null) {
                    t_Receive_info_multi_new_connect = new Receive_info_multi_new_connect();
                    t_Receive_info_multi_new_connect.start();
                }
                while(ControllerAuto){
                    //這裡應該不能註解，不然就沒辦法轉傳
                    if(IsNewConnection){//當收到自己已開始進行新的連線，則結束著個thread 20180522
                        if(t_Receive_info_uni_new_connect ==null && t_Receive_info_multi_new_connect == null)//小的都關完才官大的
                            return;//不是Controller，當收到自己新的的info,因此結束這個thread
                    }
                    if(RecvMsg_cinfo_nwconnect!= null) {//RecvMsg_cinfo_nwconnect!="" || RecvMsg_cinfo_nwconnect!= null||(!RecvMsg_cinfo_nwconnect.equals(""))
                        //"".equals(str)
                        if (!("".equals(RecvMsg_cinfo_nwconnect.trim())))
                        {

                            RecvMsg_cinfo_nwconnect = RecvMsg_cinfo_nwconnect.trim();
                            //temp = RecvMsg_cinfo_nwconnect.split("#");//將message之中有#則分開存到tmep陣列裡;message = WiFiApName + "#" + Cluster_Name + "#" + "5"+"#"+POWER+ "#" +ROLE;
                            //RecvMsg_cinfo_nwconnect
                            //Log.d("Miga","Receive_info_new_connect: "+RecvMsg_cinfo_nwconnect);
                            try {
                                // relay packet
                                //message = temp[0] + "#" + temp[1] + "#" + temp[2].trim() + "#" + temp[3];
                                message = RecvMsg_cinfo_nwconnect;
                                temp = message.split("#");
                                if (WiFiApName.equals(temp[0])) {
                                    MyNewConnectInfo = message;//儲存自己的新的連線info
                                    IsReceiveMyself = true;
                                    if (!IsReceiveFromCon) {
                                        Log.d("Miga", "Receive_info_new_connect: I receive myself info!!!!!" + MyNewConnectInfo);
                                        s_status = "Receive_info_new_connect: I receive myself info!!!!!" + MyNewConnectInfo;
                                        IsReceiveFromCon = true;//已經從controller那裡收到資訊
                                        //New_Connection_Func();
                                        if (t_New_Connection_Func == null) {//要把新連線改成thread，否則只要進去func等於就沒辦法幫忙轉傳了
                                            t_New_Connection_Func = new New_Connection_Func_t();
                                            t_New_Connection_Func.start();
                                        }
                                    }
                                    //收到的是自己的，因此不轉傳

                                } else {   //不是自己的連線msg，因此轉傳
                                    senddsk = null;
                                    senddsk = new DatagramSocket();
                                    try {
                                        // unicast
                                        iterator = IPTable.keySet().iterator();
                                        while (iterator.hasNext()) {
                                            tempkey = iterator.next().toString();
                                            senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                    InetAddress.getByName(tempkey), IP_port_controller_new_connect);
                                            senddsk.send(senddp);
                                            //Log.d("Miga", "(Relay)Send the message: " + message + " to " + tempkey);
                                            //s_status = "State : (Relay)Send the message: " + message + " to " + tempkey;

                                        }
                                        if (ROLE == RoleFlag.BRIDGE.getIndex()) {
                                            //for BRIDGE
                                            //for BRIDGE
                                            senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                    InetAddress.getByName("192.168.49.1"), IP_port_controller_new_connect);
                                            senddsk.send(senddp);
                                        }

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Log.d("Miga", "Receive_info_new_connect Exception : at relay pkt (unicast) _" + e.toString());
                                        s_status = "Receive_info_new_connect Exception : at relay pkt (unicast) _" + e.toString();
                                    }
                                    try {
                                        if (ROLE == RoleFlag.HYBRID.getIndex() || ROLE == RoleFlag.BRIDGE.getIndex()) {
                                            if (ROLE == RoleFlag.HYBRID.getIndex()) {//20180501 新增HYBRID轉傳給CLIENT的，測試可成功
                                                // broadcast
                                                senddp = new DatagramPacket(message.getBytes(), message.length(),
                                                        InetAddress.getByName("192.168.49.255"), IP_port_controller_new_connect);
                                                senddsk.send(senddp);
                                            }
                                            //multicast
                                            if (mConnectivityManager != null) {
                                                mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                                                if (mNetworkInfo.isConnected()) {
                                                    multicgroup = InetAddress.getByName("224.0.0.3");//指定multicast要發送的group
                                                    multicsk = null;// 20180520 關socket
                                                    multicsk = new MulticastSocket(6795);//6790: for peertable update
                                                    msgPkt = new DatagramPacket(message.getBytes(), message.length(), multicgroup, 6795);
                                                    multicsk.send(msgPkt);
                                                    //if (multicsk != null)
                                                    multicsk.close();
                                                    //Log.v("Miga", "multicsk send message:" + message);
                                                    //s_status = "multicsk send message" + message;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Log.d("Miga", "Receive_info_new_connect Exception : at relay pkt (multicast) _" + e.toString());
                                        s_status = "Receive_info_new_connect Exception : at relay pkt (multicast) _" + e.toString();
                                    }
                                    if (senddsk != null)
                                        senddsk.close();// 20180520 關socket
                                    if (multicsk != null)
                                        multicsk.close();// 20180520 關socket
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Log.d("Miga", "Receive_info_new_connect Exception : // relay packet _" + e.toString());
                                s_status = "Receive_info_new_connect Exception : // relay packet _" + e.toString();
                            }
                        }
                    }
                }
            }catch (SocketException e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info_new_connect Socket exception" + e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info_new_connect IOException" + e.toString());
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info_new_connect Exception" + e.toString());
            } finally {
                if (receiveds != null) {
                    receiveds.close();
                    Log.d("Miga", "Receive_info_new_connect receiveds is close");
                }
                if (senddsk != null) {
                    senddsk.close();
                    Log.d("Miga", "Receive_info_new_connect sendds is close");
                }
                if(multicsk!=null){
                    multicsk.close();// 20180520 關socket
                    Log.d("Miga", "Receive_info_new_connect multicsk is close");
                }
                if(t_Receive_info_new_connect!=null){
                    t_Receive_info_new_connect = null;
                    Log.d("Miga", "t_Receive_info_new_connect = null");
                }
                lMsg_cinfo_nwconnect=null;
                /*if (recvControllerSocket != null) {
                    recvControllerSocket.close();
                    Log.d("Miga", "Receive_info recvControllerSocket is close");
                }*/
            }
        }
    }
    public class Receive_info_uni_new_connect extends Thread{
        public void run(){
            try {
                while(true){//20180518 Controller 開啟時
                    if(IsNewConnection){
                            return;//不是Controller，當收到自己新的的info,因此結束這個thread
                    }
                    if (IsP2Pconnect&&ControllerAuto) {
                        if (receivedpkt_cinfo_nwconnect != null) {
                            //20180411註解下面，因為ROLE==GO時也會用unicast接收
                            //if (ROLE == RoleFlag.CLIENT.getIndex() || ROLE == RoleFlag.HYBRID.getIndex()|| ROLE == RoleFlag.BRIDGE.getIndex()) {
                            //unicast
                            try {
                                receivedskt_cinfo_nwconnect.setSoTimeout(20000);
                                receivedskt_cinfo_nwconnect.receive(receivedpkt_cinfo_nwconnect);//把接收到的data存在receivedp.
                            }catch (SocketTimeoutException e) {
                                Log.d("Miga","Receive_info_uni_new_connect time out");
                                if (IsNewConnection)
                                    return;
                            }
                            RecvMsg_cinfo_nwconnect = new String(lMsg_cinfo_nwconnect, 0, receivedpkt_cinfo_nwconnect.getLength());//將接收到的IMsg轉換成String型態
                            //Log.d("Miga", "I got message from unicast" + RecvMsg_cinfo);
                            //s_status = "I got message from unicast" + RecvMsg_cinfo;
                            //}
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info_uni_new_connect Exception" + e.toString());
            }finally{
                if(receivedskt_cinfo_nwconnect!=null){
                    receivedskt_cinfo_nwconnect.close();
                    receivedskt_cinfo_nwconnect = null;
                    Log.d("Miga", "Receive_info_uni_new_connect/receivedskt_cinfo_nwconnect.close()");
                }
                if(t_Receive_info_uni_new_connect != null){
                    t_Receive_info_uni_new_connect = null;
                    Log.d("Miga", "t_Receive_info_uni_new_connect = null");
                }
                RecvMsg_cinfo_nwconnect = null;
            }
        }
    }
    public class Receive_info_multi_new_connect extends Thread{
        public void run(){
            try {
                while(true){//20180518 Controller
                    if(IsNewConnection){
                        if(t_Receive_info_uni_new_connect==null) //uni關了我才關
                            return;//不是Controller，當收到自己新的的info,因此結束這個thread
                    }
                    if (IsP2Pconnect&&ControllerAuto) {
                        if (receivedpkt_cinfo_nwconnect != null) {
                            if (ROLE == RoleFlag.GO.getIndex() || ROLE == RoleFlag.HYBRID.getIndex()) {
                                //multicast
                                //recvControllerNewConnectSocket.receive(receivedpkt_cinfo_nwconnect);//recvControllerSocket port:6790
                                try {
                                    recvControllerNewConnectSocket.setSoTimeout(20000);
                                    recvControllerNewConnectSocket.receive(receivedpkt_cinfo_nwconnect);//把接收到的data存在receivedp.
                                }catch (SocketTimeoutException e) {
                                    Log.d("Miga","Receive_info_multi_new_connect time out");
                                    if(IsNewConnection){
                                        if(t_Receive_info_uni_new_connect==null) //uni關了我才關
                                            return;//不是Controller，當收到自己新的的info,因此結束這個thread
                                    }
                                }
                                RecvMsg_cinfo_nwconnect = new String(lMsg_cinfo_nwconnect, 0, receivedpkt_cinfo_nwconnect.getLength());//將接收到的IMsg轉換成String型態
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("Miga", "Receive_info_multi_new_connect Exception" + e.toString());
            }finally {
                if(receivedpkt_cinfo_nwconnect!=null){
                    receivedpkt_cinfo_nwconnect = null;
                    Log.d("Miga", "Receive_info_multi_new_connect/receivedpkt_cinfo_nwconnect = null");
                }
                if(t_Receive_info_multi_new_connect != null){
                    t_Receive_info_multi_new_connect = null;
                    Log.d("Miga", "t_Receive_info_multi_new_connect = null");
                }
                RecvMsg_cinfo_nwconnect = null;
            }
        }
    }
    //取得該裝置的密碼，用來讓其他裝置連線用。這裡會寫這個主要是因為Bridge手動設置之後，因為兩端都連了別人，因此會造成不知道自己的密碼是甚麼的問題。
    public String getDevicePwd(String SSID){
        if(SSID.equals("Android_4a9e"))
            return "Udpseqpr";
        else if(SSID.equals("Android_e9dd"))
            return "6jq1pSN6";
        else if(SSID.equals("Android_9722"))
            return "73EYTubA";
        else
            return "dontkown";


    }

    public class New_Connection_Func_t extends Thread{
        public void run(){
            String [] temp;
            try {
                if (IsReceiveMyself) {
                    if(IsController)
                        Thread.sleep(15000);
                    else
                        Thread.sleep(20000);//非controller才要睡那麼久，因為要幫忙轉傳
                    Log.d("Miga","Start New_connection_func!");
                    IsNewConnection = true;//已開始進行新連線
                    Thread.sleep(10000);
                    //CloseNonCloseThread();
                    //開始進行舊有的連線斷線
                    if (mConnectivityManager != null) {
                        mNetworkInfo = mConnectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                        if (mNetworkInfo.isConnected() == true && mNetworkInfo != null) {//wifi interface已連上
                            wifi.disconnect();//斷掉Wifi連線
                            Log.d("Miga","wifi.disconnect()");

                            Thread.sleep(500);
                        }
                    }
                    //移除所有之前wifi連線網路的設定
                    List<WifiConfiguration> list = wifi.getConfiguredNetworks();
                    for (WifiConfiguration i : list) {
                        wifi.removeNetwork(i.networkId);//移除所有之前wifi連線網路的設定
                        if(!wifi.removeNetwork(i.networkId))
                            wifi.disableNetwork(i.networkId);//Android 6.0無法清除連線，只好先disable
                        wifi.saveConfiguration();//除存設定
                    }
                    Thread.sleep(1000);

                    Log.d("Miga","My connection info:"+MyNewConnectInfo);

                    //處理新的連線資訊
                    temp = MyNewConnectInfo.split("#");//[0] SSID,[1] Neighbor,[2] NeighborNum,[3] POWER,[4] ClusterName,[5] WiFiInterface,[6] P2PInterface
                    //進行更新新的ROLE
                    ROLE = RoleFlag.NONE.getIndex();//先None，為了下面方便判斷用
                    if((!temp[6].equals("GO"))&&(!temp[6].equals("None"))){//不為GO也不為NONE表示要連到別人那裡
                        //GO移除group
                        GOdisconnect();
                        ROLE = RoleFlag.BRIDGE.getIndex();
                    }
                    if(temp[6].equals("GO")){//檢查是否要建立GO
                        CreateP2PGroup();//建立P2P group
                        ROLE = RoleFlag.GO.getIndex();
                    }
                    else if(!temp[6].equals("None")){
                        Connect_P2P(temp[5],temp[4]);
                    }
                    if(!temp[5].equals("None")){//Wifi interface不為None，表示有藥連到其他裝置
                        Connect_WiFi(temp[5],temp[4]);
                        if( ROLE == RoleFlag.NONE.getIndex() )
                            ROLE = RoleFlag.CLIENT.getIndex();
                        else if( ROLE == RoleFlag.GO.getIndex())
                            ROLE = RoleFlag.HYBRID.getIndex();

                    }
                    Thread.sleep(30000);//等30秒(這裡要等的原因是要讓那些可能正在進行wifi連線或是p2p連線的裝置有緩衝時間)
                    VariableInitial();//初始化
                }
            }catch (Exception e){

            }finally {
                if(t_New_Connection_Func!=null){
                    t_New_Connection_Func = null;
                    Log.d("Miga","New_Connection_Func_t = null");
                }
            }

        }
    }
    //GO移除group
    public void GOdisconnect() {
        if (manager != null && channel != null) {
            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                    if (group != null && manager != null && channel != null) {//&& group.isGroupOwner()
                        manager.removeGroup(channel, new ActionListener() {

                            @Override
                            public void onSuccess() {
                                Log.d("Miga", "removeGroup onSuccess -");
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.d("Miga", "removeGroup onFailure -" + reason);
                            }
                        });
                    }
                }
            });
        }
    }

    public void CreateP2PGroup(){
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                if (group == null) {
                    manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            //STATE = StateFlag.ADD_SERVICE.getIndex();
                            Log.d("Miga", "CreateGroup Success!");
                            //Isconnect = true;
                            //s_status = "createGroup time : " + Double.toString(((Calendar.getInstance().getTimeInMillis() - start_time) / 1000.0))  +" stay_time : " +  Double.toString((sleep_time/1000.0))
                            //        +  " Round_Num :" + NumRound;
                        }

                        @Override
                        public void onFailure(int error) {
                            Log.d("Miga", "CreateGroup Failure!");
                        }
                    });
                }else{
                    Log.d("Miga", "Group already exist!");
                }
            }
        });
    }

    public void Connect_WiFi(String ConnectSSID,String NewCN){
        boolean connectSuccess= false;
        int tempWifiID=0;
        try {
            while(!connectSuccess){
                String key = getDevicePwd(ConnectSSID);//取得密碼
                WifiConfiguration wc = new WifiConfiguration();
                s_status = "State: choosing peer done, try to associate with" + ": SSID name: " + ConnectSSID + " , passwd: " + key;
                Log.d("Miga", "State: choosing peer done, try to associate with" + ": SSID name: " + ConnectSSID + " , passwd: " + key);
                wc.SSID = "\"" + getWholeSSID(ConnectSSID) + "\"";
                wc.preSharedKey = "\"" + key + "\"";
                wc.hiddenSSID = true;
                wc.status = WifiConfiguration.Status.ENABLED;
                wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
                wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
                wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
                wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
                wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
                TryNum = 15;

                //使用wifi interface連線,連上GO
                int res = wifi.addNetwork(wc);
                //Log.d("Miga","res:!!!!!!!!!!!!"+res);
                //s_status = "res:"+res;
                if(res == -1){//解決disable後不能重新連線的問題
                    List<WifiConfiguration> list = wifi.getConfiguredNetworks();
                    for (WifiConfiguration i : list){
                        //Log.d("Miga","i:"+i);
                        if( i.SSID.equals(wc.SSID) ){
                            //Log.d("Miga","I enter if");
                            //s_status="I enter if";
                            isWifiConnect = wifi.enableNetwork(i.networkId,  true);
                            tempWifiID = i.networkId;
                            //Log.d("Miga","isWifiConnect:"+isWifiConnect);
                            //s_status="isWifiConnect:"+isWifiConnect;
                        }
                    }
                }else{
                    isWifiConnect = wifi.enableNetwork(res, true);
                }
                //isWifiConnect = wifi.enableNetwork(res, true);//學長的temp
                while (!mNetworkInfo.isConnected() && TryNum > 0) {//wifi interface沒成功連上,開始不斷嘗試連接
                    if(res == -1){//解決disable後不能重新連線的問題
                        isWifiConnect = wifi.enableNetwork(tempWifiID,  true);
                    }else{
                        isWifiConnect = wifi.enableNetwork(res, true);
                    }
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
                    Cluster_Name = NewCN;//將自己的Cluster_Name更新為Controller分配好的CN
                    //ROLE = RoleFlag.CLIENT.getIndex();//變為CLIENT
                    WiFiIpAddr = wifiIpAddress();//取得wifi IP address
                    Log.d("Miga", "Connect_WiFi/ROLE:" + ROLE + "Cluster_Name:" + Cluster_Name + " wifiIpAddress:" + WiFiIpAddr);

                    Thread.sleep(1000);
                    connectSuccess = true;//成功連線，跳出此迴圈

                } else {
                    connectSuccess = false;
                }
            }
        }catch (Exception e){
            Log.d("Miga","Exception: "+e.toString());

        }
    }

    public void Connect_P2P (String ConnectSSID,String NewCN){

        while(!p2p_connect_check) {
            WifiP2pConfig config = new WifiP2pConfig();
            config.deviceAddress = getDeviceMACAddress(ConnectSSID);
            config.wps.setup = WpsInfo.PBC;
            config.groupOwnerIntent = 0;

            int try_num = 0;

            s_status = "State: Relay associating GO, enable true:?" + ConnectSSID + " remainder #attempt:"
                    + try_num + " Cluster_Name " + NewCN;
            Log.d("Miga", "State: Relay associating GO, enable true:?" + ConnectSSID + " remainder #attempt:"
                    + try_num + " Cluster_Name " + NewCN);

            manager.connect(channel, config, new ActionListener() {
                @Override
                public void onSuccess() {
                    try {
                        p2p_connect_check = true;
                        Thread.sleep(5000);
                        sleep_time = sleep_time + 5;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Log.d("Miga", "P2P connect Success ");
                }
                @Override
                public void onFailure(int reason) {
                    manager.cancelConnect(channel, new ActionListener() {
                        @Override
                        public void onSuccess() {
                        }

                        public void onFailure(int reason) {
                        }
                    });
                    Log.d("Miga", "P2P connect failure");
                }
            });
        }
    }

    //取得該裝置的MAC，用來讓其他裝置連線用。
    public String getDeviceMACAddress(String SSID){
        if(SSID.equals("Android_4a9e"))
            return "62:45:cb:1b:e8:77";
        else if(SSID.equals("Android_e9dd"))
            return "62:45:cb:21:78:cf";
        else if(SSID.equals("Android_9722"))
            return "2e:4d:54:30:3f:b7";
        else
            return "dontkown";


    }

    public String getNewRole(String WifiIn,String P2PIn){
        if(WifiIn.equals("None")&&P2PIn.equals("GO")){
            return "GO";
        }else if((!WifiIn.equals("None"))&&P2PIn.equals("GO")){
            //Wifi 有連別人，p2p卻是GO
            return "HYBRID";
        }else if((!WifiIn.equals("None"))&&P2PIn.equals("None")){
            //Wifi 有連別人
            return "CLIENT";
        }else if((!WifiIn.equals("None"))&&(!P2PIn.equals("None"))&&(!P2PIn.equals("GO"))){
            //Wifi 有連別人，p2p有連別人且不是GO
            return "BRIDGE";
        }else{
            return "NONE";
        }
    }
    //Wifi連線時需要完整的網路名稱
    public String getWholeSSID(String SSID){
        if(SSID.equals("Android_4a9e"))
            return "DIRECT-ND-Android_4a9e";
        else if(SSID.equals("Android_e9dd"))
            return "DIRECT-LK-Android_e9dd";
        else if(SSID.equals("Android_9722"))
            return "DIRECT-Wd-Android_9722";
        else
            return "dontkown";
    }

    //建立完新連線之後，先前所用到的參數都要初始化。
    public void VariableInitial(){
        RunNewConnectionTime +=1;
        IsNewConnectionComplete = true;//新的連線已完成
        //IsControllerOpenFirstRond = false;
        IsReceiveMyself = false;
        IsNewConnection = false;
        CandController_record.clear();//候選人清除
        Controller_record.clear();//Controller資料清除
        first_round_Controller_record.clear();
        second_round_Controller_record.clear();
        Final_Controller_record.clear();
        TotaldevicesNum = 0;//總共餐與實驗裝置數量歸零
        IsNeighborCollect = false;//
        IsSecondRoundOver = false;
        IsReceiveFromCon = false;
        CandidateControllerTable.clear();
        CandidateController_set self = new CandidateController_set(WiFiApName, String.valueOf(power_level));
        CandController_record.add(self);
        RecvMsg_cinfo_nwconnect="";
        lMsg_cinfo_nwconnect=null;
        InfoChangeTime = 0;//歸零service discovery交換次數
        NeighborList="";
        Neighbor_record.clear();
        IPTable.clear();//IPTable清除
        //Log.d("Miga", "VariableInitial/IPTable:" + IPTable);
        s_status = "VariableInitial/IPTable:" + IPTable;
    }
}
