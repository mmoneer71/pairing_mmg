package com.example.dario_dell.wristwatch;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;



class ConnManager {


    private final static String TAG = "ConnectionManager";
    private final static String UUID_STRING = "f6b42a90-79a7-11ea-bc55-0242ac130003";
    private final static String NAME = "DP-MMG";


    private BluetoothAdapter bluetoothAdapter;
    private Handler handler; // handler that gets info from Bluetooth service
    private CryptUtils cryptUtils;
    private boolean keyExchangeDone, noisyInputCollected;
    private List<Float> noisyInputX, noisyInputY;
    private long startTime, deltaT1, deltaT2;
    private String uniqueID;


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
        uniqueID = UUID.randomUUID().toString();
    }
    Intent checkIfEnabled() {
        if (!getBluetoothAdapter().isEnabled()) {
            return new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        }
        return null;

    }
    private BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    void setNoisyInput(List<Float>xAcc, List<Float> yAcc) {
        this.noisyInputX = new ArrayList<>(xAcc);
        this.noisyInputY = new ArrayList<>(yAcc);
        initTimers();
        noisyInputCollected = true;
    }

    private void initTimers() {
        startTime = System.currentTimeMillis();
        deltaT1 = 5000;
        deltaT2 = 5000;
    }

    boolean isKeyExchangeDone() {return keyExchangeDone; }

    void accept() {
        new AcceptThread().start();
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;
        AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, UUID.fromString(UUID_STRING));
            } catch (IOException e) {
                Log.e(TAG, "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket;
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    Log.i(TAG, "Waiting for request...");
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                    break;
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    //manageMyConnectedSocket(socket);

                    Log.i(TAG, "Connected.");
                    new ConnectedThread(socket).start();
                    break;
                    /*try {
                        mmServerSocket.close();
                        break;
                    } catch (IOException e) {
                        Log.e(TAG, "Socket's close() method failed", e);
                        break;
                    }*/
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
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


            // Initiate DH exchange
            cryptUtils.setDHParams();

            BigInteger gX = cryptUtils.genKeyPair();

            int sizeInBytes = CryptUtils.KEY_SIZE / 8;
            mmBuffer = new byte[sizeInBytes];

            read();
            BigInteger gY = new BigInteger(mmBuffer);


            mmBuffer = gX.toByteArray();
            write(mmBuffer);


            // Generate Session Key
            cryptUtils.computeSessionKey(gY);
            keyExchangeDone = true;

            // Block waiting till noisy input is collected
            while (!noisyInputCollected);

            // Read other's device commitment
            mmBuffer = new byte[CryptUtils.HASH_SIZE / 8];
            read();

            String otherCommitment = Base64.getEncoder().encodeToString(mmBuffer);

            // Generate commitment
            byte[] myCommitment = cryptUtils.genCommitment(uniqueID,
                    noisyInputX,
                    noisyInputY);
            write(myCommitment);

            // Read other device's commitment opening size
            mmBuffer = new byte[4];
            read();
            int otherCommitmentOpeningSize = ByteBuffer.wrap(mmBuffer).getInt();

            // Commitment Opening
            byte[] myCommitmentOpening = cryptUtils.openCommitment();

            // Send size of the commitment first, since it is variable and
            // the bluetooth socket read requires the buffer size beforehand
            byte[] myCommitmentOpeningSize = ByteBuffer.allocate(Integer.SIZE / 8).putInt(myCommitmentOpening.length).array();

            write(myCommitmentOpeningSize);

            // Read other device's commitment opening
            mmBuffer = new byte[otherCommitmentOpeningSize];
            read();

            // Send commitment opening
            write(myCommitmentOpening);

            // Decrypt and verify other device's commitment opening
            if (!cryptUtils.verifyCommitment(mmBuffer, otherCommitment, uniqueID)) {
                Log.e(TAG, "Commitment verification failed! Aborting.");
                return;
            }

            Log.i(TAG, "Commitment verification succeeded.");

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
