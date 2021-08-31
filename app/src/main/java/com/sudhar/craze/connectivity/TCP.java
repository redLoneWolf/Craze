package com.sudhar.craze.connectivity;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.sudhar.craze.utils.Converters;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TCP {

    private static final String TAG = "TCP";

    String mHostName;
    int mPort;
    Socket socket;
    InetSocketAddress address;
    OutputStream outputStream;
    CustomInputStream customInputStream;

    TCPConListener tcpConListener;
    boolean needInputStream = true;
    private HandlerThread handlerThread;
    private Handler threadHandler;
    private ClientID clientID;
    private boolean isIdSent = false;
    public static final char ID_REQ_PREAMBLE = '?';

    public interface InputStreamListener {
        void onData(byte[] bytes);
    }

    String handlerName = "Main";
    List<InputStreamListener> inputStreamListeners;


    public TCP(String mHostName, int mPort, String handlerName, ClientID clientID) {
        this.mHostName = mHostName;
        this.mPort = mPort;
        this.handlerName = handlerName;

        this.inputStreamListeners = new ArrayList<>();
        this.clientID = clientID;

    }

    public TCP(String mHostName, int mPort, String handlerName, ClientID clientID, boolean needInputStream) {

//        this.mHostName = mHostName;
//        this.mPort = mPort;
//        handlerThread = new HandlerThread(handlerName);
//        handlerThread.start();
//        threadHandler = new Handler(handlerThread.getLooper());

        this(mHostName, mPort, handlerName, clientID);
        this.needInputStream = needInputStream;


    }


    CustomInputStream.Listener customInputListener = new CustomInputStream.Listener() {
        @Override
        public void onReceived(byte[] bytes) {

            if (bytes[0] == (byte) ID_REQ_PREAMBLE) {
                sendID();
            } else {
                notifyAllListeners(bytes);
            }

        }

        @Override
        public void onDisconnect() {
            tcpConListener.onDisconnect();
        }
    };


    public void connect(String HostName, int Port) {
        this.mHostName = HostName;
        this.mPort = Port;

        connect();
    }

    public void connect() {
        handlerThread = new HandlerThread(handlerName);
        handlerThread.start();
        threadHandler = new Handler(handlerThread.getLooper());
        threadHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Attempting to connect.... ");
                    Log.d(TAG, "run: " + mHostName + "  " + mPort);
                    address = new InetSocketAddress(mHostName, mPort);
                    socket = new Socket();


                    socket.connect(address, 1000);
                    outputStream = socket.getOutputStream();

                    outputStream.flush();

//                    if(needInputStream){
                    customInputStream = new CustomInputStream(socket, customInputListener);

                    customInputStream.init();
//                    }

                    if (socket.isConnected()) {
                        tcpConListener.onConnect(getHostName(), getPort());
                    }
                    isIdSent = false;

                } catch (SocketTimeoutException e) {
                    e.printStackTrace();
                    tcpConListener.onError(e.getMessage());
                    Log.d(TAG, "connect:failed " + e.getLocalizedMessage());
                } catch (SocketException | UnknownHostException e) {
                    e.printStackTrace();
                    tcpConListener.onError(e.getMessage());
                    Log.d(TAG, "connect:failed " + e.getLocalizedMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                    tcpConListener.onError(e.getMessage());
                    Log.d(TAG, "connect:failed " + e.getLocalizedMessage());
                }
            }
        });

    }

    public boolean isIdSent() {
        return isIdSent;
    }

    public void sendID() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(1 + 1);
        byteBuffer.put((byte) ID_REQ_PREAMBLE);
        byteBuffer.put((byte) clientID.getValue());

        sendBytes(byteBuffer.array());
        isIdSent = true;
    }


    public void sendBytes(float[] myFloatArray) {
        byte[] myByteArray = Converters.FloatArray2ByteArrayWithLengthPrefix(myFloatArray, myFloatArray.length);
        sendBytes(myByteArray, 0, myFloatArray.length);
    }


    public void sendBytes(byte[] myByteArray) {
        sendBytes(myByteArray, 0, myByteArray.length);
    }

    public void sendBytes(final byte[] myByteArray, final int start, final int len) {

        threadHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    outputStream.write(myByteArray, start, len);
                    Log.d(TAG, "run: " + len);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    public void disconnect() {

        if (socket != null) {
            if (socket.isConnected() && !socket.isClosed()) {

                try {
                    outputStream.flush();

                    outputStream.close();
                    if (customInputStream != null) {
                        customInputStream.close();
                    }

                    socket.close();
                    tcpConListener.onDisconnect();
                    threadHandler.removeCallbacks(null);
                    handlerThread.quitSafely();
                    Log.d(TAG, "disconnect: " + socket.isConnected());
                } catch (IOException e) {
                    Log.e(TAG, "disconnect: ", e);

                }
            }
        }
    }

    public void setTcpConListener(TCPConListener tcpConListener) {
        this.tcpConListener = tcpConListener;
    }


    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public String getHostName() {
        return mHostName;
    }

    public int getPort() {
        return mPort;
    }


    void addListener(InputStreamListener inputStreamListener) {
        inputStreamListeners.add(inputStreamListener);
    }

    void removeListener(InputStreamListener inputStreamListener) {
        inputStreamListeners.remove(inputStreamListener);
    }

    void removeAllListeners() {
        inputStreamListeners.clear();
    }


    private void notifyAllListeners(byte[] bytes) {
        for (InputStreamListener inputStreamListener : inputStreamListeners) {
            inputStreamListener.onData(bytes);
        }
    }
}
