package com.sudhar.craze.serial;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;

public class USB {
    private static final String TAG = "USB";
    private Context context;
    private SerialInputOutputManager serialInputOutputManager;
    private SerialInputOutputManager.Listener listener;
    private UsbManager usbManager;
    private List<UsbSerialDriver> availableDrivers;
    private UsbSerialPort usbSerialPort;
    private USBListener usbListener;
    private UsbDeviceConnection connection;

    public static final String ACTION_USB_PERMISSION = "USB-PERMISSION";
    private static final int WRITE_WAIT_MILLIS = 100;

    public List<UsbSerialDriver> getAvailableDrivers() {
        return availableDrivers;
    }

    public USB(Context context) {
        this.context = context;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
            Toast.makeText(context, "no usb", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!usbManager.hasPermission(availableDrivers.get(0).getDevice())) {
            PendingIntent mPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(availableDrivers.get(0).getDevice(), mPendingIntent);
        }
    }

    public void connect() {
        UsbSerialDriver usbSerialDriver = availableDrivers.get(0);

        connectUsb(usbSerialDriver);
    }

    void connectUsb(UsbSerialDriver usbSerialDriver) {

        UsbDevice device = usbSerialDriver.getDevice();
        connection = usbManager.openDevice(device);
        if (connection == null) {
            PendingIntent mPendingIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
            usbManager.requestPermission(availableDrivers.get(0).getDevice(), mPendingIntent);
            return;
        }

        usbSerialPort = usbSerialDriver.getPorts().get(0); // Most devices have just one port (port 0)
        try {
            usbSerialPort.open(connection);

            usbSerialPort.setParameters(9600, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            usbListener.onConnect(device.getProductName(), 9600);
        } catch (IOException e) {
            e.printStackTrace();
            usbListener.onError(e.getMessage());
        }

        serialInputOutputManager = new SerialInputOutputManager(usbSerialPort, listener);
        Executors.newSingleThreadExecutor().submit(this.serialInputOutputManager);
    }

    public void setListener(SerialInputOutputManager.Listener listener) {
        this.listener = listener;
    }

    public void disconnect() {
        if (connection != null) {
            if (usbSerialPort.isOpen()) {
                try {
                    serialInputOutputManager.stop();
                    usbSerialPort.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
            connection.close();

        }
    }

    public boolean isConnected() {

        return connection != null && usbSerialPort.isOpen() && usbSerialPort != null;
    }

    void sendBytes(byte[] data, int timeout) {
        try {

            usbSerialPort.write(data, timeout);
        } catch (IOException e) {
            e.printStackTrace();
            usbListener.onError(e.getMessage());
        }


    }

    public void setUsbListener(USBListener usbListener) {
        this.usbListener = usbListener;
    }

    public USBListener getUsbListener() {
        return usbListener;
    }
}
