package com.sudhar.craze.connectivity;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class TCPProtocol extends TCP {
    public static final int PREAMBLE_POSITION = 0;
    public static final int SIZE_POSITION = 1;
    public static final int COMMAND_POSITION = SIZE_POSITION + 4; // because here size in integer
    public static final int DATA_OFFSET = COMMAND_POSITION + 1;
    private static final String TAG = "TCPProtocol";
    public static final char PREAMBLE = '$';

    private ByteBuffer byteBuffer;
    private boolean handshake = false;
    private boolean gotPreamble = false;
    private boolean gotSize = false;
    private boolean gotCommand = false;
    private ByteBuffer TXBuffer;

    private int size = 0;

    private TCPCommand currentCommand;
    private TCPConListener tcpConListener;

    InputStreamListener inputStreamListener = new InputStreamListener() {
        @Override
        public void onData(byte[] bytes) {
            evaluate(bytes);
        }
    };


    public TCPProtocol(String mHostName, int mPort, String threadName, ClientID clientID) {
        super(mHostName, mPort, threadName, clientID);
        super.addListener(inputStreamListener);
        byteBuffer = ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN);
        TXBuffer = ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN);
    }

    void evaluate(byte[] in) {
        byteBuffer.put(in);

        if (byteBuffer.get(PREAMBLE_POSITION) == (byte) PREAMBLE) {  //Check for Preamble
            gotPreamble = true;
        } else {
            Log.d(TAG, "onNewData: intruder");
            byteBuffer.clear();
            return;
        }

        if (!gotSize && gotPreamble && byteBuffer.position() > SIZE_POSITION) {  // Check for size of data
            gotSize = true;
            size = byteBuffer.getInt(SIZE_POSITION);
        }

        if (!gotCommand && gotSize && byteBuffer.position() >= COMMAND_POSITION) {  // Check for Command
            gotCommand = true;
            int commandValue = byteBuffer.get(COMMAND_POSITION) & 0xFF;
            if (byteBuffer.get(COMMAND_POSITION) == TCPCommand.HANDSHAKE.getValue()) {
                handshake = true;
                currentCommand = TCPCommand.HANDSHAKE;
            } else {
                for (TCPCommand tcpCommand : TCPCommand.values()) {
                    if (tcpCommand.getValue() == commandValue) {
                        currentCommand = tcpCommand;
                        break;
                    }
                }
            }

        }

        Log.d(TAG, "evaluate: " + byteBuffer.position());
        if (gotCommand && byteBuffer.position() >= DATA_OFFSET + size) {
            byteBuffer.position(DATA_OFFSET);
            byte[] data = new byte[size];
            byteBuffer.get(data, 0, size);
            Log.d(TAG, "evaluate: " + currentCommand + " b : " + Arrays.toString(data));
            onCommand(currentCommand, data);

            byteBuffer.clear();
            gotCommand = false;
            gotSize = false;
            gotPreamble = false;
        }

        if (byteBuffer.position() == 128) {
            byteBuffer.rewind();
        }

    }

    void onCommand(TCPCommand tcpCommand, byte[] data) {
        switch (tcpCommand) {
            case DISCONNECT:
                disconnect();
                break;
        }
        tcpConListener.onDataReceived(currentCommand, data);
    }

    public void sendPacket(TCPCommand tcpCommand, byte[] data) {
        TXBuffer.clear();
        addPreamble();
        addSize(data.length);
        Log.d(TAG, "sendPacket: " + data.length);
        addCommand(tcpCommand);
        addData(data);
        sendBytes(TXBuffer.array(), 0, data.length + 4 + 1 + 1);

//        sendPreamble();
//        sendSize(data.length);
//        sendCommand(tcpCommand);
//        sendData(data);

    }

    public void sendPacket(TCPCommand tcpCommand) {
        TXBuffer.clear();
        addPreamble();
        addSize(0);
        addCommand(tcpCommand);
        sendBytes(TXBuffer.array(), 0, 4 + 1 + 1);
    }


    void sendPreamble() {
        sendBytes(ByteBuffer.allocate(1).order(ByteOrder.BIG_ENDIAN).put((byte) PREAMBLE).array());
    }

    void sendSize(int size) {
        sendBytes(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(size).array());
    }

    void sendCommand(TCPCommand tcpCommand) {
        sendBytes(ByteBuffer.allocate(1).order(ByteOrder.BIG_ENDIAN).put((byte) tcpCommand.getValue()).array());
    }

    void sendData(byte[] data) {
        sendBytes(data);
    }

    void addPreamble() {
        TXBuffer.put((byte) PREAMBLE);
    }

    void addSize(int size) {
        TXBuffer.putInt(size);
    }

    void addCommand(TCPCommand tcpCommand) {

        TXBuffer.put((byte) tcpCommand.getValue());
    }

    void addData(byte[] data) {
        TXBuffer.put(data);
    }

    @Override
    public void setTcpConListener(TCPConListener tcpConListener) {
        this.tcpConListener = tcpConListener;
        super.setTcpConListener(this.tcpConListener);
    }
}
