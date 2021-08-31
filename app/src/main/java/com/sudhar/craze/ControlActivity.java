package com.sudhar.craze;


import static org.opencv.imgproc.Imgproc.resize;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import com.sudhar.craze.connectivity.TCPCommand;
import com.sudhar.craze.connectivity.TCPConListener;
import com.sudhar.craze.depth.Estimator;
import com.sudhar.craze.depth.TensorflowDepthEstimator;
import com.sudhar.craze.serial.SerialProtocol;
import com.sudhar.craze.serial.USBListener;
import com.sudhar.craze.utils.Converters;
import com.sudhar.craze.utils.Tools;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCamera2View;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ControlActivity extends org.opencv.android.CameraActivity {

    private static final String TAG = "MainActivity";


    private static final int INPUT_SIZE_WIDTH = 640;
    private static final int INPUT_SIZE_HEIGHT = 480;

    private Executor executor = Executors.newSingleThreadExecutor();

    String SERVER_IP;
    int SERVER_PORT;
    InetAddress address;

    ConnectionService boundService;
    boolean isBound = false;
    Intent broadcastIntent;

    Button btnConnect, usbBtn;
    TextView tcpStat, usbStat;
    private JavaCamera2View mOpenCvCameraView;


    Rect2d roi;
    Rect2d found;

    float x_scale = 0f;
    float y_scale = 0f;


    Size frameSize;
    double horizontalFOV;
    double verticalFOV;
    double totalPixels;
    double perPixelDegH;
    double perPixelDegV;
    Point predicted;


    UsbManager mUsbManager;


    //TF-L
    ByteBuffer input = null;
    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;
    private Estimator estimator;
    private static final String MODEL_PATH = "nyu.tflite";
    private static final boolean QUANT = false;
    private boolean loaded = false;


    @Override
    protected void onStart() {
        super.onStart();
        bindService();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_control);

        btnConnect = findViewById(R.id.btnConnect);
        usbBtn = findViewById(R.id.usbBtn);
        tcpStat = findViewById(R.id.tcpStat);
        usbStat = findViewById(R.id.usbStat);


        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);


        initTensorFlowAndLoadModel();
        usbConnection();


        usbBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isMyServiceRunning(ConnectionService.class) && isBound) {

                    if (boundService.isUSBConnected()) {
                        boundService.disconnectUSB();
                    } else {
                        boundService.connectUSB();
                    }

                }
            }
        });

        if (!OpenCVLoader.initDebug())
            Log.e("OpenCv", "Unable to load OpenCV");
        else
            Log.d("OpenCv", "OpenCV loaded");

        mOpenCvCameraView = findViewById(R.id.CameraView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(cvCameraViewListener);


        x_scale = 3.0f;
        y_scale = 2.25f;

        frameSize = new Size(INPUT_SIZE_WIDTH, INPUT_SIZE_HEIGHT);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            mOpenCvCameraView.setPortrait(true);
            x_scale = 1.6875f;
            y_scale = 2.25f;


        }

        if (!isMyServiceRunning(ConnectionService.class)) btnConnect.setEnabled(false);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isMyServiceRunning(ConnectionService.class) && isBound) {

                    if (boundService.isTCPConnected()) {
                        boundService.disconnectTCP();
                    } else {
                        btnConnect.setText("Connecting...");
                        boundService.connectTCP(boundService.getmHostName(), boundService.getmPort());
                    }
                }
            }
        });


        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        float fovs[] = Tools.calculateFOV(manager);
        assert fovs != null;
        horizontalFOV = Math.toDegrees(fovs[0]);
        verticalFOV = Math.toDegrees(fovs[1]);

    }


    private Handler depthCamFeedHandler = new Handler();

    private static final int DEPTH_DELAY = 100;
    private Runnable depthrunnable = new Runnable() {
        @Override
        public void run() {

            if (boundService.isDEPTHCamReady()) {


                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap bitmap = Bitmap.createBitmap(INPUT_SIZE_WIDTH, INPUT_SIZE_HEIGHT, Bitmap.Config.ARGB_8888);


                        Utils.matToBitmap(F640X480, bitmap);
//                        saveImage(bitmap);

                        Bitmap outBitmap = estimator.estimateDepth(bitmap);
//                        saveImage(outBitmap);

                        Mat mat = new Mat(INPUT_SIZE_WIDTH / 2, INPUT_SIZE_HEIGHT / 2, CvType.CV_8UC1);
                        Utils.bitmapToMat(outBitmap, mat, false);

                        Mat outMat = new Mat();
                        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGRA2BGR);

                        Size sz2 = new Size(1080, 1080);
                        resize(mat, outMat, sz2);
                        Imgproc.applyColorMap(outMat, outMat, Imgproc.COLORMAP_MAGMA);


                        Mat depth = new Mat();
                        Size sz23 = new Size(INPUT_SIZE_WIDTH, INPUT_SIZE_HEIGHT);
                        resize(outMat, depth, sz23);
                        boundService.sendDepthFrame(depth);
                    }
                });
            }
            depthCamFeedHandler.postDelayed(this, DEPTH_DELAY);


        }
    };

    public void startDepth() {
        depthCamFeedHandler.postDelayed(depthrunnable, DEPTH_DELAY);
    }

    public void stopDepth() {
        depthCamFeedHandler.removeCallbacks(depthrunnable);
    }

    public void setDepth(Mat rgb) {
        this.rgb = rgb;
    }


    private void initTensorFlowAndLoadModel() {
        input = ByteBuffer.allocateDirect(4 * BATCH_SIZE * INPUT_SIZE_WIDTH * INPUT_SIZE_HEIGHT * PIXEL_SIZE);
        input.order(ByteOrder.nativeOrder());

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    estimator = TensorflowDepthEstimator.create(
                            getAssets(),
                            MODEL_PATH,
                            QUANT);

                    loaded = true;
                } catch (final Exception e) {
                    loaded = false;
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }


    Mat rgb;
    public Mat F640X480 = null;
    public Mat Temp = null;
    public Mat toSend = null;
    Size sendingSize = null;

    CameraBridgeViewBase.CvCameraViewListener2 cvCameraViewListener = new CameraBridgeViewBase.CvCameraViewListener2() {
        @Override
        public void onCameraViewStarted(int width, int height) {
            found = new Rect2d();
            totalPixels = Math.sqrt((width * width) + (height * height));
            perPixelDegH = horizontalFOV / totalPixels;
            perPixelDegV = verticalFOV / totalPixels;
            F640X480 = new Mat();
            toSend = new Mat();
            Temp = new Mat();
            predicted = new Point();
            sendingSize = new Size();


        }


        @Override
        public void onCameraViewStopped() {

        }

        @Override
        public Mat onCameraFrame(final CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

            rgb = inputFrame.rgba();
            Imgproc.cvtColor(rgb, Temp, Imgproc.COLOR_BGR2RGB);
            resize(rgb, F640X480, frameSize);


            Point centerPoint = new Point(rgb.cols() / 2, rgb.rows() / 2);


            if (boundService.isRGBCamReady()) {
                resize(rgb, toSend, sendingSize);
                Imgproc.cvtColor(toSend, Temp, Imgproc.COLOR_BGR2RGB);
                boundService.setRgb(Temp);

//                boundService.sendRGBFrame(toSend);

            }

            Imgproc.circle(rgb, centerPoint, 5, new Scalar(255, 0, 0), 3);


            return rgb;
        }
    };

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();

                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    private void bindService() {
        broadcastIntent = new Intent(getApplicationContext(), ConnectionService.class);
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
//            btnSend.setEnabled(true);
            isBound = true;
            btnConnect.setEnabled(true);

            if (boundService.isTCPConnected()) {
                setTCPStat(boundService.getmHostName(), boundService.getmPort());
            } else {
                setTCPNotConnected();
            }

            if (boundService.isUSBConnected()) {
                setUSBStat(boundService.getUsbDeviceName());
            } else {
                setUSBNotConnected();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            boundService = null;
        }
    };


    void setTCPStat(String host, int port) {
        String stat = String.format(Locale.ENGLISH, "Connected to tcp://%s:%d", host, port);
        tcpStat.setText(stat);
    }

    void setUSBStat(String name) {
        String stat = String.format(Locale.ENGLISH, "USB device:%s", name);
        usbStat.setText(stat);
    }

    void setTCPNotConnected() {
        String stat = "Connected to :Not Connected";
        tcpStat.setText(stat);
    }

    void setUSBNotConnected() {
        String stat = String.format(Locale.ENGLISH, "USB device: Not Connected");
        usbStat.setText(stat);
    }


    TCPConListener tcpConListener = new TCPConListener() {
        @Override
        public void onConnect(String name, int port) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnConnect.setText("Disconnect");
                    setTCPStat(boundService.getmHostName(), boundService.getmPort());
                }
            });

        }

        @Override
        public void onError(String error) {
            Toast.makeText(boundService, "TCP Error :" + error, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDataReceived(TCPCommand tcpCommand, byte[] bytes) {

            Log.d(TAG, "onDataReceived: ");

            switch (tcpCommand) {
                case START_CAM_FEED:
                    int[] widthXheight = Converters.ByteArray2IntArray(bytes);
                    sendingSize = new Size(widthXheight[0], widthXheight[1]);
                    boundService.connectCamFeed();
                    break;
                case STOP_CAM_FEED:
                    boundService.disconnectCamFeed();
                    break;
                case START_DEPTH_CAM_FEED:
                    boundService.connectDepthFeed();
                    startDepth();
                    break;

                case STOP_DEPTH_CAM_FEED:
                    boundService.disconnectDepthFeed();
                    stopDepth();
                    break;


            }
        }

        @Override
        public void onDisconnect() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    btnConnect.setText("Re-connect");
                    boundService.disconnectUSB();
                    setTCPNotConnected();
                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);

                    startActivity(intent);
                }
            });
        }
    };

    @Override
    protected void onStop() {
        super.onStop();
        boundService.removeTCPListener(tcpConListener);
    }


    USBListener usbListener = new USBListener() {
        @Override
        public void onConnect(String name, int baudrate) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    usbStat.setText("connected");
                    setUSBStat(boundService.getUsbDeviceName());
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
                    setUSBNotConnected();
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
//                    etUsb.setText(device.getProductName());
                }
            }
        }
    };

    private void showDevices() {
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            //            mUsbManager.requestPermission(device, mPermissionIntent);
            //your code
            Log.d(TAG, "showDevices: ");
            Log.d("usb", "name: " + device.getDeviceName() + ", " + "ID: " + device.getDeviceId());

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();


        if (boundService.isRGBConnected()) {
            boundService.disconnectCamFeed();
        }


        stopAndUnbindService();


        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        unregisterReceiver(mUsbDetachReceiver);
        unregisterReceiver(mUsbAttachReceiver);

    }


    @Override
    protected void onResume() {
        super.onResume();
        OpenCVLoader.initDebug();

        mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);

    }

    @Override
    protected void onPause() {
        // unregister listener
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
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