package com.sudhar.craze.connectivity;

public interface TCPConListener {
    public void onConnect(String Host, int Port);

    public void onError(String error);

    public void onDataReceived(TCPCommand tcpCommand, byte[] bytes);

    public void onDisconnect();
}
