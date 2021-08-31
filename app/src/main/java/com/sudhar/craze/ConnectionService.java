package com.sudhar.craze;


import static android.content.Intent.ACTION_BATTERY_CHANGED;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.sudhar.craze.connectivity.ClientID;
import com.sudhar.craze.connectivity.TCP;
import com.sudhar.craze.connectivity.TCPCommand;
import com.sudhar.craze.connectivity.TCPConListener;
import com.sudhar.craze.connectivity.TCPProtocol;
import com.sudhar.craze.sensors.IMUComplimentaryFilter;
import com.sudhar.craze.serial.SerialProtocol;
import com.sudhar.craze.serial.USBListener;
import com.sudhar.craze.utils.Converters;
import com.sudhar.craze.utils.Tools;

import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;


public class ConnectionService extends Service {
    private static final String TAG = "ConnectionService";
    private static final String ACTION_USB_PERMISSION = "USB-PERMISSION";
    private static final int RGB_DELAY = 41;
    private static final int DEPTH_DELAY = 60;
    private static final String QUALITY_TOKEN = "imageQuality";
    SharedPreferences sharedPreferences;
    public static final int CHANNEL_NO = 1;
    String mHostName;
    int mPort;
    String UsbDeviceName = "None";
    private TCPProtocol tcpProtocol;

    private SerialProtocol serialProtocol;

    private IMUComplimentaryFilter sensorsHelper;
    private float[] sensorData;
    private double heading;
    private int batteryLevel = 0;
    WifiManager wifiManager;
    private int imageQuality = 30;

    TCP CamFeedTcp;
    TCP DepthFeedTcp;

    List<TCPConListener> tcpConListeners = new ArrayList<>();

    List<USBListener> usbListeners = new ArrayList<>();

    private final IBinder localBinder = new ConnectionServiceBinder();

    public class ConnectionServiceBinder extends Binder {

        public ConnectionService getService() {
            return ConnectionService.this;

        }
    }

    long previous = System.currentTimeMillis();
    int g = 0;

    IMUComplimentaryFilter.Listener sensorListener = new IMUComplimentaryFilter.Listener() {
        @Override
        public void onData(float[] data, float headings) {
            sensorData = data;
            heading = headings;

            long current = System.currentTimeMillis();

            if (isTCPConnected() && current - previous >= 41) {   /// ~20hz
                sendTelemetry();

                previous = current;
                Log.d(TAG, "onData: " + g + " time " + current);
            }


        }
    };
    private Mat rgb;

    private Handler rgbCamFeedHandler = new Handler();

    private Runnable RGBrunnable = new Runnable() {
        @Override
        public void run() {

            if (rgb != null) {
                sendRGBFrame(rgb);
            }
            rgbCamFeedHandler.postDelayed(this, RGB_DELAY);


        }
    };

    public void startRGB() {
        rgbCamFeedHandler.postDelayed(RGBrunnable, RGB_DELAY);
    }

    public void stopRGB() {
        rgbCamFeedHandler.removeCallbacks(RGBrunnable);
    }

    public void setRgb(Mat rgb) {
        this.rgb = rgb;
    }


    public void setImageQuality(int imageQuality) {
        this.imageQuality = imageQuality;
        sharedPreferences.edit().putInt(QUALITY_TOKEN, imageQuality).apply();
        Converters.setQuality(imageQuality);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }


    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }


    void init() {
        tcpProtocol = new TCPProtocol(mHostName, mPort, "ServiceThread", ClientID.MAIN);
        tcpProtocol.setTcpConListener(tcpConListener);


        serialProtocol = new SerialProtocol(getApplicationContext());
        serialProtocol.setUsbListener(usbListener);

        sensorsHelper = new IMUComplimentaryFilter(getApplicationContext(), sensorListener);
        sensorsHelper.init();


        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);


        registerReceiver(batteryLevelReceiver, new IntentFilter(ACTION_BATTERY_CHANGED));


    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mHostName = intent.getStringExtra("IP");
        mPort = intent.getIntExtra("PORT", 1234);
        init();
        startNotification("Not Connected");
        sharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        setImageQuality(sharedPreferences.getInt(QUALITY_TOKEN, 30));


        return START_NOT_STICKY;
    }


    public boolean isTCPConnected() {
        return tcpProtocol != null && tcpProtocol.isConnected();
    }


    void connectTCP(String HostName, int Port) {
        if (tcpProtocol != null) {
            tcpProtocol.connect(HostName, Port);
        }
    }

    void disconnectTCP() {
        tcpProtocol.sendPacket(TCPCommand.DISCONNECT);
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Do something after 5s = 5000ms
                if (tcpProtocol != null && tcpProtocol.isConnected()) {
                    tcpProtocol.disconnect();
                }

                disconnectCamFeed();
                disconnectDepthFeed();
            }
        }, 1000);


    }

    void sendPacket(TCPCommand tcpCommand, byte[] data) {
        tcpProtocol.sendPacket(tcpCommand, data);
    }

    void sendTCPBytes(byte[] data) {
        tcpProtocol.sendBytes(data, 0, data.length);
    }


    void connectUSB() {
        if (serialProtocol != null) {

            if (serialProtocol.getAvailableDrivers().size() > 0 && !serialProtocol.isHandshake()) {
                serialProtocol.connect();
            } else {
                Log.d(TAG, "connectUSB: already connected");
            }

        }
    }

    public boolean isUSBConnected() {
        return serialProtocol != null && serialProtocol.isConnected();
    }


    void disconnectUSB() {
        if (serialProtocol != null) {
            serialProtocol.disconnect();
        }
    }

    void sendPacketUSB(SerialProtocol.USBCommand usbCommand, byte[] data) {
        serialProtocol.sendPacket(usbCommand, data);
    }


    void connectCamFeed() {
        CamFeedTcp = new TCP(getmHostName(), getmPort(), "CamThread", ClientID.CAM, false);
        CamFeedTcp.setTcpConListener(new TCPConListener() {
            @Override
            public void onConnect(String Host, int Port) {
                startRGB();
            }

            @Override
            public void onError(String error) {
                updateNotification("RGB Cam Error :" + error);
                for (TCPConListener tcpConListener : tcpConListeners) {
                    tcpConListener.onError("RGB Cam Error :" + error);
                }
            }

            @Override
            public void onDataReceived(TCPCommand tcpCommand, byte[] bytes) {

            }

            @Override
            public void onDisconnect() {
                stopRGB();
            }
        });
        CamFeedTcp.connect();

    }

    void disconnectCamFeed() {
        if (isRGBConnected()) {
            stopRGB();
            CamFeedTcp.disconnect();

        }
    }

    boolean isRGBConnected() {
        return CamFeedTcp != null && CamFeedTcp.isConnected();
    }

    boolean isRGBCamReady() {
        return isRGBConnected() && CamFeedTcp.isIdSent();
    }


    void sendRGBFrame(Mat mat) {
        if (CamFeedTcp.isConnected()) {
            CamFeedTcp.sendBytes(Converters.CVMatToPacket(mat));
        }
    }


    void connectDepthFeed() {


        DepthFeedTcp = new TCP(getmHostName(), getmPort(), "Depth", ClientID.DEPTH, false);

        DepthFeedTcp.setTcpConListener(new TCPConListener() {
            @Override
            public void onConnect(String Host, int Port) {

            }

            @Override
            public void onError(String error) {
                updateNotification("Depth Cam Error :" + error);
                for (TCPConListener tcpConListener : tcpConListeners) {
                    tcpConListener.onError("Depth Cam Error :" + error);
                }
            }

            @Override
            public void onDataReceived(TCPCommand tcpCommand, byte[] bytes) {

            }

            @Override
            public void onDisconnect() {

            }
        });

        DepthFeedTcp.connect();


    }

    void disconnectDepthFeed() {
        if (isDepthConnected()) {
            DepthFeedTcp.disconnect();
        }
    }

    boolean isDepthConnected() {
        return DepthFeedTcp != null && DepthFeedTcp.isConnected();
    }

    boolean isDEPTHCamReady() {
        return isDepthConnected() && DepthFeedTcp.isIdSent();
    }

    void sendDepthFrame(Mat mat) {
        if (DepthFeedTcp.isConnected()) {
            DepthFeedTcp.sendBytes(Converters.CVMatToPacket(mat));
        }
    }

    private Notification getNotification(String status) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            return new NotificationCompat.Builder(this, App.CHANNEL_1_ID)
                    .setContentTitle("TCP service")
                    .setContentText(status)
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setContentIntent(pendingIntent)
                    .build();
        } else {
            return new NotificationCompat.Builder(this, App.CHANNEL_1_ID)
                    .setContentTitle("TCP service")
                    .setContentText(status)
                    .setSmallIcon(R.drawable.ic_launcher_background)
                    .setContentIntent(pendingIntent)
                    .build();
        }

    }

    private void startNotification(String status) {
        startForeground(1, getNotification(status));
    }

    private void updateNotification(String status) {
        Notification notification = getNotification(status);

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(CHANNEL_NO, notification);
    }

    public String getmHostName() {
        return mHostName;
    }

    public int getmPort() {
        return mPort;
    }

    @Override
    public void onDestroy() {

        disconnectTCP();

        disconnectCamFeed();
        disconnectDepthFeed();
        unregisterReceiver(batteryLevelReceiver);

        if (sensorsHelper != null) {
            sensorsHelper.onStop();
        }
        stopSelf();

        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
    }

    BroadcastReceiver batteryLevelReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {


            int rawlevel = intent.getIntExtra("level", -1);
            int scale = intent.getIntExtra("scale", -1);
            int level = -1;
            if (rawlevel >= 0 && scale > 0) {
                level = (rawlevel * 100) / scale;
            }
            batteryLevel = level;

        }
    };


    TCPConListener tcpConListener = new TCPConListener() {
        @Override
        public void onConnect(String Host, int Port) {
            mHostName = Host;
            mPort = Port;

            updateNotification("Connected to " + Host + ":" + Port);
            for (TCPConListener tcpConListener : tcpConListeners) {
                tcpConListener.onConnect(Host, Port);
            }
        }

        @Override
        public void onError(String error) {
            updateNotification("Error :" + error);
            for (TCPConListener tcpConListener : tcpConListeners) {
                tcpConListener.onError(error);
            }
        }

        @Override
        public void onDataReceived(TCPCommand tcpCommand, byte[] bytes) {
            runCommand(tcpCommand, bytes);
            for (TCPConListener tcpConListener : tcpConListeners) {
                tcpConListener.onDataReceived(tcpCommand, bytes);
            }

        }

        @Override
        public void onDisconnect() {
            updateNotification("Disconnected");
            for (TCPConListener tcpConListener : tcpConListeners) {
                tcpConListener.onDisconnect();
            }
        }
    };


    public String getUsbDeviceName() {
        return UsbDeviceName;
    }

    USBListener usbListener = new USBListener() {
        @Override
        public void onConnect(String name, int baudrate) {

            UsbDeviceName = name;
            for (USBListener usbListener : usbListeners) {
                usbListener.onConnect(name, baudrate);
            }
        }

        @Override
        public void onError(String error) {
            for (USBListener usbListener : usbListeners) {
                usbListener.onError(error);
            }
        }

        @Override
        public void onDataReceived(SerialProtocol.USBCommand usbCommand, byte[] bytes) {
            for (USBListener usbListener : usbListeners) {
                usbListener.onDataReceived(usbCommand, bytes);
            }
        }

        @Override
        public void onDisconnect() {
            for (USBListener usbListener : usbListeners) {
                usbListener.onDisconnect();
            }
        }
    };

    boolean s = false;
    long pr;
    int i = 0;

    void sendTelemetry() {


//        if(!s){
//            s=true;
//            pr = System.currentTimeMillis();
//        }
//        long c = System.currentTimeMillis();
//        if(c - pr >=1000){
//           Log.d(TAG, "sendTelemetry: 1");
//           return;
//        }else {
//            Log.d(TAG, "sendTelemetry: "+i);
//            i++;
//        }

        byte[] data;
        ByteBuffer byteBuffer = ByteBuffer.allocate(8 + 4);
        byteBuffer.putDouble(heading);
        byteBuffer.putInt(batteryLevel);
        byte[] sensorDataInBytes = Converters.FloatArray2ByteArray(sensorData);
        data = Tools.concatArrays(sensorDataInBytes, byteBuffer.array());

        tcpProtocol.sendPacket(TCPCommand.TELEMETRY_DATA, data);

    }


    void runCommand(TCPCommand tcpCommand, byte[] data) {
        switch (tcpCommand) {
            case HANDSHAKE:
                // TODO: 4/9/2021 Handshake
//                Toast.makeText(this, "Handshake Done", Toast.LENGTH_SHORT).show();
                break;

            case INVALID:
                // TODO: 4/9/2021 Error
//                Toast.makeText(this, "Error:runCommand", Toast.LENGTH_SHORT).show();
                break;

            case USB_CONNECT:
                connectUSB();
                break;

            case USB_DISCONNECT:
                disconnectUSB();
                break;

            case WRITE_MOTORS:
                if (data != null && serialProtocol.isConnected()) {
                    writeMotors(data);
                }
                break;

            case START_TELEMETRY:

                sensorsHelper.initListeners();
                break;

            case STOP_TELEMETRY:
                sensorsHelper.onStop();
                break;

            case START_WAY_POINT:

//                processWayPoints(data);
                break;

            case STOP_WAY_POINT:
                Log.d(TAG, "runCommand: stop waypoint");
//                if(rcRunnable.isRunning()){
//                    rcRunnable.terminate();
//                }
                disconnectUSB();
                break;
            case DISCONNECT:
                if (tcpProtocol.isConnected()) {
                    tcpProtocol.disconnect();
                }

        }


    }


    void writeMotors(int Fleft, int Fright, int Rleft2, int Rright) {

//        byte[] out =  new byte[]{(byte)left1,(byte)right1,(byte)left2,(byte)right2};

        short[] shorts = Converters.getShortFromInt(new int[]{Fleft, Fright, Rleft2, Rright});
        byte[] out = Converters.ShortArrayToByteArray(shorts, ByteOrder.LITTLE_ENDIAN);

        sendPacketUSB(SerialProtocol.USBCommand.MOTOR, out);
    }


    void writeMotors(byte[] data) {
        int[] ins = Converters.ByteArray2IntArray(data);
        short[] shorts = Converters.getShortFromInt(ins);
        byte[] out = Converters.ShortArrayToByteArray(shorts, ByteOrder.LITTLE_ENDIAN);

        sendPacketUSB(SerialProtocol.USBCommand.MOTOR, out);
        Log.d(TAG, "runCommand: ");
    }


    void addTCPListener(TCPConListener tcpConListener) {
        tcpConListeners.add(tcpConListener);
    }

    void removeTCPListener(TCPConListener tcpConListener) {
        tcpConListeners.remove(tcpConListener);
    }

    void removeAllTCPListeners() {
        tcpConListeners.clear();
    }

    private void notifyAllTCPListeners(TCPCommand tcpCommand, byte[] bytes) {
        for (TCPConListener tcpConListener : tcpConListeners) {
            tcpConListener.onDataReceived(tcpCommand, bytes);
        }
    }

    void addUSBListener(USBListener usbListener) {
        usbListeners.add(usbListener);
    }

    void removeUSBListener(USBListener usbListener) {
        usbListeners.remove(usbListener);
    }

    void removeAllUSBListeners() {
        usbListeners.clear();
    }

    private void notifyAllUSBListeners(SerialProtocol.USBCommand usbCommand, byte[] bytes) {
        for (USBListener usbListener : usbListeners) {
            usbListener.onDataReceived(usbCommand, bytes);
        }
    }
}
