package com.sudhar.craze.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.util.Log;
import android.util.SizeF;

import androidx.annotation.RequiresApi;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Tools {
    private static final String TAG = "Tools";


    public static String[] concatArrays(String[] src1, String[] src2) {
        if (src1 == null) {
            throw new IllegalArgumentException("src1 is required.");
        }
        if (src2 == null) {
            throw new IllegalArgumentException("src2 is required.");
        }

        String[] result = new String[src1.length + src2.length];

        System.arraycopy(src1, 0, result, 0, src1.length);
        System.arraycopy(src2, 0, result, src1.length, src2.length);

        return result;
    }

    public static float[] concatArrays(float[] src1, float[] src2) {
        if (src1 == null) {
            throw new IllegalArgumentException("src1 is required.");
        }
        if (src2 == null) {
            throw new IllegalArgumentException("src2 is required.");
        }

        float[] result = new float[src1.length + src2.length];

        System.arraycopy(src1, 0, result, 0, src1.length);
        System.arraycopy(src2, 0, result, src1.length, src2.length);

        return result;
    }

    public static int[] concatArrays(int[] src1, int[] src2) {
        if (src1 == null) {
            throw new IllegalArgumentException("src1 is required.");
        }
        if (src2 == null) {
            throw new IllegalArgumentException("src2 is required.");
        }

        int[] result = new int[src1.length + src2.length];

        System.arraycopy(src1, 0, result, 0, src1.length);
        System.arraycopy(src2, 0, result, src1.length, src2.length);

        return result;
    }

    public static byte[] concatArrays(byte[] src1, byte[] src2) {
        if (src1 == null) {
            throw new IllegalArgumentException("src1 is required.");
        }
        if (src2 == null) {
            throw new IllegalArgumentException("src2 is required.");
        }

        byte[] result = new byte[src1.length + src2.length];

        System.arraycopy(src1, 0, result, 0, src1.length);
        System.arraycopy(src2, 0, result, src1.length, src2.length);

        return result;
    }

    public static String getPath(String file, Context context) {
        AssetManager assetManager = context.getAssets();

        BufferedInputStream inputStream = null;
        try {
            // Read data from assets.
            inputStream = new BufferedInputStream(assetManager.open(file));
            byte[] data = new byte[inputStream.available()];
            inputStream.read(data);
            inputStream.close();

            // Create copy file in storage.
            File outFile = new File(context.getFilesDir(), file);
            FileOutputStream os = new FileOutputStream(outFile);
            os.write(data);
            os.close();
            // Return a path to file which may be read in common way.
            return outFile.getAbsolutePath();
        } catch (IOException ex) {
            Log.i(TAG, "Failed to upload a file");
        }
        return "";
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public static float[] calculateFOV(CameraManager cManager) {
        try {
            for (final String cameraId : cManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cManager.getCameraCharacteristics(cameraId);

                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
                    float[] maxFocus = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    SizeF size = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    float w = size.getWidth();
                    float h = size.getHeight();
                    float horizontalAngle = (float) (2 * Math.atan(w / (maxFocus[0] * 2)));
                    float verticalAngle = (float) (2 * Math.atan(h / (maxFocus[0] * 2)));
                    return new float[]{horizontalAngle, verticalAngle};

                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();

        }
        return null;
    }

    public static int ensureRange(double value, int min, int max) {
        return (int) Math.min(Math.max(value, min), max);
    }


}
