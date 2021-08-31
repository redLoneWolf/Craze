package com.sudhar.craze.depth;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class TensorflowDepthEstimator implements Estimator {
    private static final String TAG = "TensorflowDepthEstimato";
    private static final int BATCH_SIZE = 1;
    private static final int PIXEL_SIZE = 3;

    private static final int IMAGE_MEAN = 128;
    private static final float IMAGE_STD = 128.0f;


    private Interpreter interpreter;
    private GpuDelegate delegate;
    private static int inputWidthSize = 640, inputHeightSize = 480;
    private boolean quant;


    public static Estimator create(AssetManager assetManager, String modelPath, boolean quant) throws IOException {
        TensorflowDepthEstimator depthEstimator = new TensorflowDepthEstimator();
//        depthEstimator.delegate = new GpuDelegate();
//        Interpreter.Options options = (new Interpreter.Options()).addDelegate(depthEstimator.delegate);
//        depthEstimator.interpreter = new Interpreter(depthEstimator.loadModelFile(assetManager, modelPath), options);
//        depthEstimator.quant = quant;
//        return depthEstimator;

        Interpreter.Options options = new Interpreter.Options();
        CompatibilityList compatList = new CompatibilityList();

        if (compatList.isDelegateSupportedOnThisDevice()) {
            // if the device has a supported GPU, add the GPU delegate
            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
            options.addDelegate(gpuDelegate);
//            options.setNumThreads(4);
            depthEstimator.delegate = gpuDelegate;

        } else {
            // if the GPU is not supported, run on 4 threads
            options.setNumThreads(4);
        }

        depthEstimator.interpreter = new Interpreter(depthEstimator.loadModelFile(assetManager, modelPath), options);
        depthEstimator.quant = quant;

        return depthEstimator;
    }

    @Override
    public Bitmap estimateDepth(Bitmap bitmap) {
        int outWidth = inputWidthSize / 2;
        int outHeight = inputHeightSize / 2;
        ByteBuffer inByteBuffer = convertBitmapToByteBuffer(bitmap);
        ByteBuffer outByteBuffer = ByteBuffer.allocateDirect(outWidth * outHeight * 4);

        outByteBuffer.order(ByteOrder.nativeOrder());
        try {
            interpreter.run(inByteBuffer, outByteBuffer);
        } catch (Exception e) {
            e.printStackTrace();
        }


//        byte[] data = new byte[outByteBuffer.capacity()];
//        ((ByteBuffer) outByteBuffer.duplicate().clear()).get(data);
//        Mat mat = new Mat(480, 640, CvType.CV_8UC4);
//        mat.put(0, 0, data);
//
//        Bitmap bitmap1  =
//        bitmap =  convertByteBufferToBitmap(outByteBuffer,outWidth,outHeight);
//        return  outByteBuffer;
        return convertByteBufferToBitmap(outByteBuffer, outWidth, outHeight);
//        return getOutputImage(outByteBuffer);
    }

    @Override
    public void close() {
        interpreter.close();
        delegate.close();
        interpreter = null;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Converts ByteBuffer with depth map to the Bitmap
     *
     * @param byteBuffer Output ByteBuffer from Interpreter.run
     * @param imgWidth   Model output image width
     * @param imgHeight  Model output image height
     * @return grayscale Bitmap of depth map
     */
    public static Bitmap convertByteBufferToBitmap(ByteBuffer byteBuffer, int imgWidth, int imgHeight) {
        byteBuffer.rewind();
        byteBuffer.order(ByteOrder.nativeOrder());
        Bitmap bitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.ARGB_8888);
        float minValue = byteBuffer.getFloat();
        float maxValue = minValue;

        //Estimate max and min depth value for normalization
        for (int i = 0; i < imgWidth * imgHeight - 1; i++) {
            float pixelDepth = byteBuffer.getFloat();
            if (pixelDepth > maxValue) {
                maxValue = pixelDepth;
            }
            if (pixelDepth < minValue) {
                minValue = pixelDepth;
            }
        }
        byteBuffer.rewind();

        //Normalize to 0-255 grayscale
        int[] pixels = new int[imgWidth * imgHeight];
        float interval = maxValue - minValue;
        for (int i = 0; i < imgWidth * imgHeight; i++) {
            int normalizedPixelDepth = Math.round((byteBuffer.getFloat() - minValue) / interval * 256);
            pixels[i] = Color.argb(255, normalizedPixelDepth, normalizedPixelDepth, normalizedPixelDepth);

        }
        bitmap.setPixels(pixels, 0, imgWidth, 0, 0, imgWidth, imgHeight);
        return bitmap;
    }

    public static ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * BATCH_SIZE * inputWidthSize * inputHeightSize * PIXEL_SIZE);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] bitmapValues = new int[inputWidthSize * inputHeightSize];
        bitmap.getPixels(bitmapValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        //normalized to -1 to 1
        for (int i = 0; i < inputWidthSize; ++i) {
            for (int j = 0; j < inputHeightSize; ++j) {
                final int val = bitmapValues[pixel++];
                byteBuffer.putFloat((((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat((((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                byteBuffer.putFloat((((val) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            }
        }
        return byteBuffer;
    }

    private Bitmap getOutputImage(ByteBuffer output, int outputWidth, int outputHeight) {
        output.rewind();

//        int outputWidth = 384;
//        int outputHeight = 384;
        Bitmap bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[outputWidth * outputHeight];
        for (int i = 0; i < outputWidth * outputHeight; i++) {
            int a = 0xFF;

            float r = output.getFloat() * 255.0f;
            float g = output.getFloat() * 255.0f;
            float b = output.getFloat() * 255.0f;

            pixels[i] = a << 24 | ((int) r << 16) | ((int) g << 8) | (int) b;
        }
        bitmap.setPixels(pixels, 0, outputWidth, 0, 0, outputWidth, outputHeight);
        return bitmap;
    }
}