package com.example.dario_dell.smartphone;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


class ConnManager {

    private BluetoothAdapter bluetoothAdapter;
    private final static String TAG = "ConnectionManager";
    private final static String UUID_STRING = "f6b42a90-79a7-11ea-bc55-0242ac130003";
    private Handler handler; // handler that gets info from Bluetooth service
    private CryptUtils cryptUtils;
    private boolean keyExchangeDone, noisyInputCollected;
    private List<Float> noisyInputX, noisyInputY;
    private long startTime, deltaT1, deltaT2;

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        int MESSAGE_READ = 0;
        int MESSAGE_WRITE = 1;
        int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed.)
    }


    ConnManager() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        handler = new Handler();
        cryptUtils = new CryptUtils();
        keyExchangeDone = false;
        noisyInputCollected = false;
    }
    Intent checkIfEnabled() {
        if (!getBluetoothAdapter().isEnabled()) {
            return new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        }
        return null;

    }
    BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    void connect(BluetoothDevice device) {
        new ConnectThread(device).start();
    }

    void setNoisyInput(List<Float>xVel, List<Float> yVel) {
        this.noisyInputX = new ArrayList<>(xVel);
        this.noisyInputY = new ArrayList<>(yVel);
        initTimers();
        noisyInputCollected = true;
    }

    private void initTimers() {
        startTime = System.currentTimeMillis();
        deltaT1 = 5000;
        deltaT2 = 5000;
    }

    boolean isKeyExchangeDone() {return keyExchangeDone; }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;

        ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString(UUID_STRING));
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                Log.i(TAG, "Connecting...");
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            //manageMyConnectedSocket(mmSocket);
            Log.i(TAG, "Connected.");
            new ConnectedThread(mmSocket).start();
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {

            cryptUtils.setDHParams();
            BigInteger gX = cryptUtils.genKeyPair();
            mmBuffer = gX.toByteArray();
            write(mmBuffer);

            int sizeInBytes = CryptUtils.KEY_SIZE / 8;
            mmBuffer = new byte[sizeInBytes];
            read();
            BigInteger gY = new BigInteger(mmBuffer);
            cryptUtils.computeSessionKey(gY);
            keyExchangeDone = true;

            // Block waiting till noisy input is collected
            while (!noisyInputCollected);

            Log.d(TAG, noisyInputX.size() + " " + noisyInputY.size());
            Log.d(TAG, noisyInputX.get(1) + " " + noisyInputY.get(1));

        }

        private void read() {
            // Keep listening to the InputStream until an exception occurs.

            try {
                // Read from the InputStream.
                int numBytes = mmInStream.read(mmBuffer);
                // Send the obtained bytes to the UI activity.
                Message readMsg = handler.obtainMessage(
                        MessageConstants.MESSAGE_READ, numBytes, -1,
                        mmBuffer);
                readMsg.sendToTarget();
            } catch (IOException e) {
                Log.d(TAG, "Input stream was disconnected", e);
            }

        }

        // Call this from the main activity to send data to the remote device.
        private void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
                Message writtenMsg = handler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        handler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                handler.sendMessage(writeErrorMsg);
            }
        }


        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }


    }
}
