package com.sudhar.craze.utils;

import android.graphics.Bitmap;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;


public class Converters {

    private static int quality = 30;
    private static final String TAG = "Converters";

    public static void setQuality(int quality) {
        Converters.quality = quality;
    }
    static MatOfByte matOfByte;
    static  MatOfInt matOfInt;

    public static void init(){
       matOfByte = new MatOfByte();
       matOfInt = new  MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality);
    }

    static float[] getRandomFloat(int size) {
        float[] randomFloats = new float[size];
        Random rd = new Random(); // creating Random object

        for (int i = 0; i < randomFloats.length; i++) {
            randomFloats[i] = rd.nextFloat(); // storing random integers in an array
            // printing each array element
        }
        return randomFloats;
    }

    static int[] getRandomInt(int size) {
        int[] randomFloats = new int[size];
        Random rd = new Random(); // creating Random object

        for (int i = 0; i < randomFloats.length; i++) {
            randomFloats[i] = rd.nextInt(); // storing random integers in an array
            // printing each array element
        }
        return randomFloats;
    }


    public static byte[] FloatArray2ByteArray(float[] values) {
        ByteBuffer buffer = ByteBuffer.allocate((4 * values.length));


        for (float value : values) {
            buffer.putFloat(value);
        }

        return buffer.array();
    }

    public static byte[] IntArray2ByteArray(int[] values) {
        ByteBuffer buffer = ByteBuffer.allocate((4 * values.length) + 4);


        for (int value : values) {
            buffer.putInt(value);
        }

        return buffer.array();
    }

    public static byte[] FloatArray2ByteArrayWithLengthPrefix(float[] values, int size) {
        ByteBuffer buffer = ByteBuffer.allocate((4 * values.length) + 4);

        buffer.putInt((4 * values.length) + 4);
        // TODO: 3/5/2021 need better protocol for size transmitting
        for (float value : values) {
            buffer.putFloat(value);
        }

        return buffer.array();
    }

    public static byte[] IntArray2ByteArrayWithLengthPrefix(int[] values, int size) {
        ByteBuffer buffer = ByteBuffer.allocate((4 * values.length) + 4);
        // TODO: 3/5/2021 need better protocol for size transmitting
        buffer.putInt(size);
        for (int value : values) {
            buffer.putInt(value);
        }

        return buffer.array();
    }

    public static float[] ByteArray2FloatArray(byte[] values, int size) {
        float[] floats = new float[size];
        ByteBuffer buffer = ByteBuffer.wrap(values);

        buffer.asFloatBuffer().get(floats);
//        Log.d(TAG, "DecodeByteArray2FloatArray: "+floats.length);
        return floats;
    }

    public static int[] ByteArray2IntArray(byte[] values) {
        int[] ints = new int[values.length / 4];
        ByteBuffer buffer = ByteBuffer.wrap(values);
        buffer.asIntBuffer().get(ints);
        return ints;
    }

    public static int ByteArray2Int(byte[] values) {
        return ByteBuffer.wrap(values).getInt();
    }


    public static byte[] Int2ByteArray(int values) {
        ByteBuffer buffer = ByteBuffer.allocate(4).putInt(values);
        return buffer.array();
    }

    public static float[] ByteArray2FloatArrayWithSizePrefix(byte[] values) {
        ByteBuffer buffer = ByteBuffer.wrap(values);
        int length = buffer.getInt();
        float[] floats = new float[length];
        buffer.asFloatBuffer().get(floats);
        return floats;
    }

    public static int[] ByteArray2IntArrayWithSizePrefix(byte[] values) {
        ByteBuffer buffer = ByteBuffer.wrap(values);
        int length = buffer.getInt();
        int[] ints = new int[length];
        buffer.asIntBuffer().get(ints);
        return ints;
    }

    public static byte[] getByteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        bitmap.recycle();
        return stream.toByteArray();
    }


    public static byte[] FloatArray2ByteArray(float[] values, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * values.length).order(byteOrder);


        for (float value : values) {
            buffer.putFloat(value);
        }

        return buffer.array();
    }

    public static byte[] IntArray2ByteArray(int[] values, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.allocate(4 * values.length).order(byteOrder);


        for (int value : values) {
            buffer.putInt(value);
        }

        return buffer.array();
    }

    public static byte[] FloatArray2ByteArrayWithLengthPrefix(float[] values, int size, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.allocate((4 * values.length) + 4).order(byteOrder);

        buffer.putInt((4 * values.length) + 4);
        // TODO: 3/5/2021 need better protocol for size transmitting
        for (float value : values) {
            buffer.putFloat(value);
        }

        return buffer.array();
    }

    public static byte[] IntArray2ByteArrayWithLengthPrefix(int[] values, int size, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.allocate((4 * values.length) + 4).order(byteOrder);
        // TODO: 3/5/2021 need better protocol for size transmitting
        buffer.putInt(size);
        for (int value : values) {
            buffer.putInt(value);
        }

        return buffer.array();
    }

    public static float[] ByteArray2FloatArray(byte[] values, int size, ByteOrder byteOrder) {
        float[] floats = new float[size];
        ByteBuffer buffer = ByteBuffer.wrap(values).order(byteOrder);

        buffer.asFloatBuffer().get(floats);
//        Log.d(TAG, "DecodeByteArray2FloatArray: "+floats.length);
        return floats;
    }

    public static int[] ByteArray2IntArray(byte[] values, int size, ByteOrder byteOrder) {
        int[] ints = new int[size];
        ByteBuffer buffer = ByteBuffer.wrap(values).order(byteOrder);

        buffer.asIntBuffer().get(ints);
//        Log.d(TAG, "DecodeByteArray2FloatArray: "+ints.length);
        return ints;
    }


    public static int ByteArray2Int(byte[] values, ByteOrder byteOrder) {
        return ByteBuffer.wrap(values).order(byteOrder).getInt();
    }


    public static float[] ByteArray2FloatArrayWithSizePrefix(byte[] values, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.wrap(values).order(byteOrder);
        int length = buffer.getInt();
        float[] floats = new float[length];
        buffer.asFloatBuffer().get(floats);
        return floats;
    }

    public static int[] ByteArray2IntArrayWithSizePrefix(byte[] values, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.wrap(values).order(byteOrder);
        int length = buffer.getInt();
        int[] ints = new int[length];
        buffer.asIntBuffer().get(ints);
        return ints;
    }

    public static double[] ByteArrayToDoubleArray(byte[] data) {
        double[] doubles = new double[data.length / 8];

        ByteBuffer byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.asDoubleBuffer().get(doubles);

        return doubles;
    }


    public static byte[] CVMatToPacket(Mat mat) {
        Imgcodecs.imencode(".jpg", mat, matOfByte, matOfInt);
        int size = matOfByte.toArray().length;
        ByteBuffer byteBuffer = ByteBuffer.allocate(size + 4 + 1);
        byteBuffer.put((byte) '$');
        byteBuffer.putInt(size);
        byteBuffer.put(matOfByte.toArray());
        byte[] packet  = byteBuffer.array();
        byteBuffer.clear();
        mat.release();
        return packet;
    }

    public static byte[] get0To255FromInt(int[] ints) {

        byte[] out = new byte[ints.length];

        for (int i = 0; i < ints.length; i++) {
            out[i] = (byte) ints[i];
        }

        return out;
    }

    public static int[] getIntFrom0To255(byte[] bytes) {

        int[] out = new int[bytes.length];

        for (int i = 0; i < bytes.length; i++) {
            out[i] = bytes[i] & 0xFF;
        }

        return out;
    }

    public static short[] getShortFromInt(int[] ints) {
        short[] out = new short[ints.length];

        for (int i = 0; i < ints.length; i++) {
            out[i] = (short) ints[i];
        }

        return out;
    }

    public static byte[] ShortArrayToByteArray(short[] shorts, ByteOrder byteOrder) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(2 * shorts.length).order(byteOrder);

        for (short sh : shorts) {
            byteBuffer.putShort(sh);
        }

        return byteBuffer.array();
    }


}
