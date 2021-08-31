package com.sudhar.craze.serial;


public interface USBListener {
    public void onConnect(String name, int baudrate);

    public void onError(String error);

    public void onDataReceived(SerialProtocol.USBCommand usbCommand, byte[] bytes);

    public void onDisconnect();
}

