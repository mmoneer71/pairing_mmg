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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;


class ConnManager {

    private final static String TAG = "ConnectionManager";
    private final static String UUID_STRING = "f6b42a90-79a7-11ea-bc55-0242ac130003";
    private final static byte[] ACK_MSG = "ack".getBytes();
    private final static byte[] INPUT_COLLECTED_MSG = "done".getBytes();
    private final static int MAX_BUFFER_SIZE = 900;
    private final static int DELTA_T1 = 400;
    private final static int DELTA_T2 = 900;

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler; // handler that gets info from Bluetooth service
    private CryptUtils cryptUtils;
    private boolean keyExchangeDone, pairingComplete, pairingStatus;
    private List<Float> noisyInputX, noisyInputY, decryptedNoisyInputX, decryptedNoisyInputY;
    private String uniqueID;
    private final Message noisyInputMsg;

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        int MESSAGE_READ = 0;
        int MESSAGE_WRITE = 1;
        int MESSAGE_TOAST = 2;

        // ... (Add other message types here as needed.)
    }


    ConnManager(Message noisyInputMsg) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        handler = new Handler();
        cryptUtils = new CryptUtils();
        keyExchangeDone = false;
        pairingComplete = false;
        uniqueID = UUID.randomUUID().toString();
        decryptedNoisyInputX = new ArrayList<>();
        decryptedNoisyInputY = new ArrayList<>();
        this.noisyInputMsg = noisyInputMsg;
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
    }

    boolean isKeyExchangeDone() {return keyExchangeDone; }

    boolean isPairingComplete() {return pairingComplete; }

    boolean getPairingResult() {return pairingStatus; }

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

            // Wait till noisy input is collected
            synchronized (noisyInputMsg) {
                try {
                    noisyInputMsg.wait();
                }
                catch(InterruptedException e) {
                    Log.e(TAG, "Error while waiting for noisy Input to be collected.", e);
                }
            }

            Log.i(TAG, "Noisy input collected successfully.");

            // Notify wristwatch that input is collected
            write(INPUT_COLLECTED_MSG);

            // Wait for ack message to be received
            mmBuffer = new byte[ACK_MSG.length];
            read();

            // Set start timer
            long startTime = System.currentTimeMillis();
            // Generate commitment
            byte[] myCommitment = cryptUtils.genCommitment(uniqueID,
                    noisyInputX,
                    noisyInputY);

            write(myCommitment);

            // Read other's device commitment
            mmBuffer = new byte[CryptUtils.HASH_SIZE / 8];
            read();
            String otherCommitment = Base64.getEncoder().encodeToString(mmBuffer);

            if (System.currentTimeMillis() - startTime > DELTA_T1) {
                Log.e(TAG, "Time violation on delta1");
                pairingStatus = false;
                pairingComplete = true;
                return;
            }
            Log.i(TAG, "3rd phase finished after: " + (System.currentTimeMillis() - startTime));

            while (System.currentTimeMillis() - startTime < DELTA_T1 + DELTA_T2);

            // Commitment Opening
            byte[] myCommitmentOpening = cryptUtils.openCommitment();

            // Send size of the commitment first, since it is variable and
            // the bluetooth socket read requires the buffer size beforehand
            byte[] myCommitmentOpeningSize = ByteBuffer.allocate(Integer.SIZE / 8).putInt(myCommitmentOpening.length).array();

            write(myCommitmentOpeningSize);

            // Read other device's commitment opening size
            mmBuffer = new byte[Integer.SIZE / 8];
            read();

            int otherCommitmentOpeningSize = ByteBuffer.wrap(mmBuffer).getInt();

            // Send commitment opening
            writeCommitmentOpening(myCommitmentOpening);

            // Read other device's commitment opening
            byte[] otherCommitmentOpening = readCommitmentOpening(otherCommitmentOpeningSize);

            // Decrypt and verify other device's commitment opening
            if (!cryptUtils.verifyCommitment(otherCommitmentOpening, otherCommitment, uniqueID)) {
                Log.e(TAG, "Commitment verification failed! Aborting.");
                return;
            }

            Log.i(TAG, "Commitment verification succeeded.");

            cryptUtils.getDecryptedNoisyInput(decryptedNoisyInputX, decryptedNoisyInputY);

            pairingStatus = MatchingAlgo.pair(decryptedNoisyInputX,
                    decryptedNoisyInputY,
                    noisyInputX,
                    noisyInputY);

            pairingComplete = true;
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

        private byte[] readCommitmentOpening(int commitmentSize) {
            byte[] receivedCommitment = null;
            int remainingBytes = commitmentSize;
            while (remainingBytes > 0) {
                int bufferSize = (remainingBytes < MAX_BUFFER_SIZE) ? remainingBytes : MAX_BUFFER_SIZE;
                mmBuffer = new byte[bufferSize];
                read();
                if (receivedCommitment == null) {
                    receivedCommitment = mmBuffer.clone();
                }
                else {
                    receivedCommitment = cryptUtils.mergeArrays(receivedCommitment, mmBuffer);
                }
                remainingBytes -= bufferSize;
                write(ACK_MSG);
            }
            return receivedCommitment;
        }


        private void writeCommitmentOpening(byte[] commitment) {
            int remainingBytes = commitment.length, startIndex = 0;
            while (remainingBytes > 0) {
                int bufferSize = (remainingBytes < MAX_BUFFER_SIZE) ? remainingBytes : MAX_BUFFER_SIZE;
                byte[] dataToSend = Arrays.copyOfRange(commitment, startIndex, startIndex + bufferSize);
                write(dataToSend);
                startIndex += bufferSize;
                remainingBytes -= bufferSize;
                mmBuffer = new byte[ACK_MSG.length];
                read();

            }
        }

    }
}
