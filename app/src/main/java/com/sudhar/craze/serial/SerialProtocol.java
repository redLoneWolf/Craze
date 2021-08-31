package com.sudhar.craze.serial;

import android.content.Context;
import android.util.Log;

import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SerialProtocol extends USB {

    public static final int PREAMBLE_POSITION = 0;
    public static final int SIZE_POSITION = 1;
    public static final int COMMAND_POSITION = 2;
    public static final int DATA_OFFSET = 3;
    /*
     *  |Preamble|Size|Command|Data
     *       $M
     *
     *
     * */

    public interface Listener {
        void onDataReceived(USBCommand usbCommand, byte[] data);
    }


    private static final String TAG = "SerialProtocol";


    public static final char PREAMBLE = '$';

    private ByteBuffer byteBuffer;
    private boolean handshake = false;
    private boolean gotPreamble = false;
    private boolean gotSize = false;
    private boolean gotCommand = false;
    private int size = 0;

    private USBCommand currentCommand;
    SerialInputOutputManager.Listener inputListener = new SerialInputOutputManager.Listener() {
        @Override
        public void onNewData(byte[] data) {
            evaluate(data);
        }

        @Override
        public void onRunError(Exception e) {
            getUsbListener().onError(e.getMessage());
        }
    };


    public SerialProtocol(Context context) {
        super(context);
        setListener(inputListener);   // dont change order

        byteBuffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
    }


    public enum USBCommand {

        HANDSHAKE(101),
        BYE(102),
        USB_ERROR(104),
        SET_MOTORS(103),
        GET_MOTORS(105),
        LED(106),
        RECEIVED(100),
        MOTOR(107),
        FLOAT_TEST(109),
        INVALID(110);

        private int value;

        USBCommand(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }


        static USBCommand fromValue(int value) {

            for (USBCommand usbcommand : USBCommand.values()) {
                if (usbcommand.getValue() == value) {
                    return usbcommand;
                } else {
                    return USBCommand.INVALID;
                }
            }
            return USBCommand.INVALID;
        }

    }


    public void sendPacket(USBCommand usbCommand, byte[] data) {
        sendPreamble();


        sendSize(data.length);
        sendCommand(usbCommand);

        sendBytes(data, 100);
    }


    static byte[] i8(int[] datas) {

        ByteBuffer buf = ByteBuffer.allocate(datas.length).order(ByteOrder.LITTLE_ENDIAN);
        for (int data : datas) {
            buf.put((byte) data);
        }
        return buf.array();
    }

    static byte[] i16(int[] datas) {
        ByteBuffer buf = ByteBuffer.allocate(2 * datas.length).order(ByteOrder.LITTLE_ENDIAN);
        for (int data : datas) {
            buf.putShort((short) data);
        }
        return buf.array();
    }

    static byte[] i32(int[] datas) {

        ByteBuffer buf = ByteBuffer.allocate(4 * datas.length).order(ByteOrder.LITTLE_ENDIAN);
        for (int data : datas) {
            buf.putInt(data);
        }
        return buf.array();
    }

    void sendPreamble() {
        sendBytes(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put((byte) PREAMBLE).array(), 100);
    }

    void sendSize(int size) {
        sendBytes(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put((byte) size).array(), 100);
    }

    void sendCommand(USBCommand usbCommand) {
        byte com = (byte) usbCommand.getValue();

        sendBytes(ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN).put(com).array(), 100);
    }


    void writeMotor(short[] in) {

        ByteBuffer byteBuffer = ByteBuffer.allocate(in.length * 4).order(ByteOrder.LITTLE_ENDIAN);

        for (short value : in) {
            byteBuffer.putShort(value);
        }

        sendPacket(USBCommand.SET_MOTORS, byteBuffer.array());
    }

    void writeLed(byte value) {
        sendPacket(USBCommand.LED, new byte[]{value});
    }

    @Override
    public void connect() {
        super.connect();
        if (isConnected()) {
            handshake();
        }

    }

    void handshake() {
        byte[] dummy = new byte[1];
        dummy[0] = 123;
        sendPacket(USBCommand.HANDSHAKE, dummy);

    }

    public boolean isHandshake() {
        return handshake;
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

        if (gotPreamble && byteBuffer.position() >= SIZE_POSITION) {  // Check for size of data
            gotSize = true;
            size = byteBuffer.get(SIZE_POSITION);
        }

        if (gotSize && byteBuffer.position() >= COMMAND_POSITION) { // check for handshake
            gotCommand = true;
            byte commandValue = byteBuffer.get(COMMAND_POSITION);
            currentCommand = USBCommand.fromValue(commandValue);
            if (currentCommand == USBCommand.HANDSHAKE) {
                handshake = true;
            } else {
                Log.d(TAG, "evaluate: " + commandValue);
            }

        }

        if (gotCommand && byteBuffer.position() >= DATA_OFFSET + size) {
            byteBuffer.position(3);
            byte[] data = new byte[size];
            byteBuffer.get(data, 0, size);
            getUsbListener().onDataReceived(currentCommand, data);
            byteBuffer.clear();
        }

        if (byteBuffer.position() == 64) {
            byteBuffer.rewind();
        }
    }


}
