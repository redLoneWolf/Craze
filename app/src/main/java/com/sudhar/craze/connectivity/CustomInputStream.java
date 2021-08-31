package com.sudhar.craze.connectivity;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class CustomInputStream {
    InputStream inputStream;

    Socket socket;

    public interface Listener {
        void onReceived(byte[] bytes);

        void onDisconnect();
    }

    Listener listener;

    public CustomInputStream(Socket socket, Listener listener) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.listener = listener;

    }


    Runnable receiverRunnable = new Runnable() {
        @Override
        public void run() {

            byte[] buffer = new byte[64];


            while (true) {

                if (socket != null) {
                    if (socket.isConnected() && !socket.isClosed()) {
                        try {
                            if (inputStream.available() > 0) {
                                inputStream.read(buffer);
                                listener.onReceived(buffer);
                            }

                        } catch (IOException e) {
                            e.printStackTrace();
                            break;

                        }
                    } else {
                        listener.onDisconnect();
                        break;
                    }
                }


            }
        }
    };


    public void close() throws IOException {
        inputStream.close();
    }

    public void init() {
        Thread thread = new Thread(receiverRunnable);
        thread.start();


    }
}
