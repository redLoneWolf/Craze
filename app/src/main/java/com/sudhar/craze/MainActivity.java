package com.sudhar.craze;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.material.slider.Slider;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.sudhar.craze.connectivity.TCPCommand;
import com.sudhar.craze.connectivity.TCPConListener;
import com.sudhar.craze.serial.SerialProtocol;
import com.sudhar.craze.serial.USBListener;

import java.net.InetAddress;
import java.util.List;

public class MainActivity extends org.opencv.android.CameraActivity {

    private static final String TAG = "MainActivity";

    EditText etIP, etPort;
    private static final int INPUT_SIZE_WIDTH = 640;
    private static final int INPUT_SIZE_HEIGHT = 480;

    Button usbBtn;
    EditText etUsb;
    String SERVER_IP;
    int SERVER_PORT;
    InetAddress address;

    ConnectionService boundService;
    boolean isBound = false;
    Intent broadcastIntent;

    Button btnConnect;
    Slider qualitySlider;

    private List<UsbSerialDriver> availableDrivers;


    UsbManager mUsbManager;


    @Override
    protected void onStart() {
        super.onStart();
        startAndBindService();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        this.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);


        etIP = findViewById(R.id.etIP);
        etPort = findViewById(R.id.etPort);

        btnConnect = findViewById(R.id.btnConnect);
        etUsb = findViewById(R.id.usbName);
        qualitySlider = findViewById(R.id.slider);


        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        usbConnection();

//        usbBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (isMyServiceRunning(ConnectionService.class) && isBound) {
//
//                    if(boundService.isUSBConnected()){
//                        boundService.disconnectUSB();
//                    }else {
//
//                        boundService.connectUSB();
//                    }
//
//                }
//            }
//        });


        qualitySlider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                if (fromUser) {
                    if (isMyServiceRunning(ConnectionService.class) && isBound) {
                        boundService.setImageQuality((int) value);
                    }
                }
            }
        });


        if (!isMyServiceRunning(ConnectionService.class)) btnConnect.setEnabled(false);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                if (isMyServiceRunning(ConnectionService.class) && isBound) {
                    if (boundService.isTCPConnected()) {
                        boundService.disconnectTCP();

                    } else {

                        if (etIP.getText().toString().isEmpty() && etPort.getText().toString().isEmpty()) {
                            return;
                        }

                        SERVER_IP = etIP.getText().toString().trim();
                        SERVER_PORT = Integer.parseInt(etPort.getText().toString().trim());
                        btnConnect.setText("Connecting...");
                        boundService.connectTCP(SERVER_IP, SERVER_PORT);


                    }
                }

            }
        });


    }


    private void startAndBindService() {
        broadcastIntent = new Intent(getApplicationContext(), ConnectionService.class);
        startService(broadcastIntent);
        bindService(broadcastIntent, serviceConnection, BIND_AUTO_CREATE);

    }

    private void stopAndUnbindService() {
        if (isMyServiceRunning(ConnectionService.class)) {
            unbindService(serviceConnection);
            stopService(broadcastIntent);
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ConnectionService.ConnectionServiceBinder binderBridge = (ConnectionService.ConnectionServiceBinder) service;
            boundService = binderBridge.getService();
            boundService.addTCPListener(tcpConListener);
            boundService.addUSBListener(usbListener);

            isBound = true;
            btnConnect.setEnabled(true);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            boundService = null;
        }
    };


    @Override
    protected void onStop() {
        super.onStop();
        boundService.removeTCPListener(tcpConListener);
    }

    TCPConListener tcpConListener = new TCPConListener() {
        @Override
        public void onConnect(String name, int baudrate) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnConnect.setText("Disconnect");
                    Intent intent = new Intent(getApplicationContext(), ControlActivity.class);

                    startActivity(intent);

                }
            });

        }

        @Override
        public void onError(String error) {
            Toast.makeText(boundService, "TCP Error :" + error, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDataReceived(TCPCommand tcpCommand, byte[] bytes) {

        }

        @Override
        public void onDisconnect() {
            btnConnect.setText("Connect");
        }
    };


    USBListener usbListener = new USBListener() {
        @Override
        public void onConnect(String name, int baudrate) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    etUsb.setText(name);
                }
            });

        }

        @Override
        public void onError(String error) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(boundService, "USB Error :" + error, Toast.LENGTH_SHORT).show();
                }
            });

        }

        @Override
        public void onDataReceived(SerialProtocol.USBCommand usbCommand, final byte[] bytes) {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    tvMessages.append("USB :"+ String.valueOf(Tools.ByteArray2Int(bytes,ByteOrder.LITTLE_ENDIAN))+ System.getProperty("line.separator"));
//                }
//            });
        }

        @Override
        public void onDisconnect() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnConnect.setText("No Device");
                }
            });

        }
    };


    private void usbConnection() {
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(mUsbAttachReceiver, filter);
        filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbDetachReceiver, filter);

        showDevices();
    }

    BroadcastReceiver mUsbAttachReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {

                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {

                    showDevices();
                }


            }
        }
    };

    BroadcastReceiver mUsbDetachReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    // call your method that cleans up and closes communication with the device

                    showDevices();
                }
            }
        }
    };

    private void showDevices() {

        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(mUsbManager);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (availableDrivers.size() > 0) {
                    UsbDevice device = availableDrivers.get(0).getDevice();
                    etUsb.setText(device.getProductName());
                } else {
                    etUsb.setText("No USB Attached");
                }
            }
        });

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        stopAndUnbindService();
        unbindService(serviceConnection);
        unregisterReceiver(mUsbDetachReceiver);
        unregisterReceiver(mUsbAttachReceiver);

    }


    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}