package com.example.dario_dell.wristwatch;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends WearableActivity implements AccelerationSensorObserver, LinearAccelerationSensorObserver, Runnable{

    //view components
    private TextView instructionsTxtView;

    private String folder_name = "/Accelerometer_Data/";

    //needed for providing unique names to the files
    private int filename_counter = 0;

    //along with date and counter, they provide unique names to the files
    private final String filename_separator = "_";
    private final String filename_suffix = "watch_sample";
    private final String filename_format = ".csv";

    // resulting string to write into a CSV file
    // Init: CSV file header
    String header = "seq_number,timestamp,x_acc,y_acc,z_acc,x_lin_acc,y_lin_acc,z_lin_acc\n";

    //variables needed for detecting the Shaking Gesture
    private float[] acceleration = new float[3];
    // https://stackoverflow.com/questions/15158769/android-acceleration-direction
    private float[] linearAcceleration = new float[3];
    private boolean isStable = false, countDownStarted = false;


    private final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL = 1;
    private final int REQUEST_ENABLE_BT = 1;
    private long logTime = 0;
    private int generation = 0;
    private boolean logData = false, drawingEnded = false, drawingStarted = false, pairingStarted = false;
    private boolean pairingDone = false;
    private Handler handler;
    // Output log
    private String log;
    private int sampleCount = 0;
    private float noiseThreshold = 0.2f;


    private AccelerationSensor accelerationSensor;
    private GravitySensor gravitySensor;
    private GyroscopeSensor gyroscopeSensor;
    private MagneticSensor magneticSensor;
    private LinearAccelerationSensor linearAccelerationSensor;

    private ConnManager connManager;
    List<Float> x_lin_acc, y_lin_acc;
    private final Message noisyInputMsg = new Message();



    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instructionsTxtView = findViewById(R.id.instructions);

        accelerationSensor = new AccelerationSensor(this);
        linearAccelerationSensor = new LinearAccelerationSensor();
        gravitySensor = new GravitySensor(this);
        gyroscopeSensor = new GyroscopeSensor(this);
        magneticSensor = new MagneticSensor(this);


        handler = new Handler();
        log = "";

        checkExternalWritePermission();

        connManager = new ConnManager(noisyInputMsg);
        initBluetooth();

        x_lin_acc = new ArrayList<>();
        y_lin_acc = new ArrayList<>();

    }

    //unregister the sensor when the application hibernates
    protected void onPause() {
        super.onPause();
        accelerationSensor.removeAccelerationObserver(this);
        accelerationSensor.removeAccelerationObserver(linearAccelerationSensor);
        gravitySensor.removeGravityObserver(linearAccelerationSensor);
        gyroscopeSensor.removeGyroscopeObserver(linearAccelerationSensor);
        magneticSensor.removeMagneticObserver(linearAccelerationSensor);
        linearAccelerationSensor.removeLinearAccelerationObserver(this);
        handler.removeCallbacks(this);
        //senSensorManager.unregisterListener(this);
    }

    //register the sensor again when the application resumes
    protected void onResume() {
        super.onResume();
        handler.post(this);
        accelerationSensor.registerAccelerationObserver(this);
        accelerationSensor
                .registerAccelerationObserver(linearAccelerationSensor);
        gravitySensor.registerGravityObserver(linearAccelerationSensor);
        gyroscopeSensor.registerGyroscopeObserver(linearAccelerationSensor);
        magneticSensor.registerMagneticObserver(linearAccelerationSensor);

        linearAccelerationSensor.registerLinearAccelerationObserver(this);
        /*if (!justStarted)
            senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);*/
    }


    @Override
    public void onAccelerationSensorChanged(float[] acceleration, long timeStamp) {
        // Get a local copy of the sensor values
        System.arraycopy(acceleration, 0, this.acceleration, 0,
                acceleration.length);
    }

    @Override
    public void onLinearAccelerationSensorChanged(float[] linearAcceleration,
                                                  long timeStamp) {
        // Get a local copy of the sensor values
        System.arraycopy(linearAcceleration, 0, this.linearAcceleration, 0,
                linearAcceleration.length);


    }

    @Override
    public void run() {
        handler.postDelayed(this, 20);
        if (!isStable) {
            checkStability();
        }
        else if (!countDownStarted) {
            startCountDown();
        }
        if (drawingEnded && !pairingStarted) {
            initPairing();
        }
        logData();
        detectOnset();
        detectEnding();
        checkPairingProgress();
    }

    private void checkExternalWritePermission() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL);
        }

    }


    private void checkStability() {
        if (!linearAccelerationSensor.getFusionSystemStability() ||
                !connManager.isKeyExchangeDone()) {
            Log.d("Calibration", "Still calibrating...");
            return;
        }
        isStable = true;

    }

    private void detectOnset() {
        if (logData && !drawingStarted) {
            if (linearAcceleration[0] < -noiseThreshold || linearAcceleration[0] > noiseThreshold
                    || linearAcceleration[1] < -noiseThreshold || linearAcceleration[1] > noiseThreshold) {
                ++sampleCount;
            } else {
                sampleCount = 0;
            }

            if (sampleCount == 10) {
                drawingStarted = true;
            }
        }
    }

    private void detectEnding() {
        if (drawingStarted && !drawingEnded) {
            if (linearAcceleration[0] >= -noiseThreshold && linearAcceleration[0] <= noiseThreshold
                    && linearAcceleration[1] >= -noiseThreshold && linearAcceleration[1] <= noiseThreshold) {
                ++sampleCount;
            } else {
                sampleCount = 0;
            }

            if (sampleCount == 20) {
                drawingEnded = true;
                instructionsTxtView.setText(R.string.pairing);
            }
        }
    }

    private void startCountDown() {
        new CountDownTimer(3000, 1000) {

            public void onTick(long millisUntilFinished) {
                long timeLeft = (millisUntilFinished + 100) / 1000;
                instructionsTxtView.setText(String.format(getString(R.string.wait), timeLeft));
            }

            public void onFinish() {
                instructionsTxtView.setText(R.string.drawing);
                logData = true;
            }
        }.start();
        countDownStarted = true;
    }

    private void initPairing() {
        connManager.setNoisyInput(x_lin_acc, y_lin_acc);

        synchronized (noisyInputMsg) {
            noisyInputMsg.notify();
        }

        writeToFile();
        log = "";
        logData = false;
        x_lin_acc.clear();
        y_lin_acc.clear();
        pairingStarted = true;
    }

    private void checkPairingProgress() {
        if (connManager.isPairingComplete()) {
            boolean pairingResult = connManager.getPairingResult();
            if (pairingResult) {
                instructionsTxtView.setText(R.string.success);
            }
            else {
                instructionsTxtView.setText(R.string.failure);
            }
            pairingDone = true;
            handler.removeCallbacks(this);
        }
    }

    private void logData() {
        if (logData) {
            if (generation == 0) {
                logTime = System.currentTimeMillis();
            }


            x_lin_acc.add(linearAcceleration[0]);
            y_lin_acc.add(linearAcceleration[1]);

            log += generation++ + ",";
            log += System.currentTimeMillis() - logTime + ",";

            log += acceleration[0] + ",";
            log += acceleration[1] + ",";
            log += acceleration[2] + ",";
            log += linearAcceleration[0] + ",";
            log += linearAcceleration[1] + ",";
            log += linearAcceleration[2];

            //Log.d("Generation", String.valueOf(generation));
            //Log.d("Fused Linear Acceleration", linearAcceleration[0] + " " + linearAcceleration[1]  + " " + linearAcceleration[2]);
            log += System.getProperty("line.separator");
        }
    }



    //Method for writing to file after tapping the "Stop" button
    private void writeToFile() {
        String to_write = header + log;
        if (MainActivity.this.isExternalStorageWritable()){
            File path = new File (Environment.getExternalStoragePublicDirectory(
                    Environment.MEDIA_SHARED), folder_name);

            FileOutputStream stream = null;
            try {
                boolean r = path.mkdirs();
                if (!r && !path.exists())
                    throw new FileNotFoundException("DIRECTORY NOT CREATED!");

                File file = new File(path, generateSequencedFilename(path));
                if (!file.createNewFile() && !file.exists())
                    throw new FileNotFoundException("FILE NOT CREATED!");
                Log.d("MFILE",file.exists()+ " "+ file.toString());

                stream = new FileOutputStream(file);
                if (to_write.length()==0)
                    throw new FileNotFoundException("STRING TOO SMALL!");
                stream.write(to_write.getBytes());
                stream.flush();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //generates sequenced unique filenames
    private String generateSequencedFilename(File path){
        String result = LocalDate.now().toString() + filename_separator
                + generateFilenameCounter(path) + filename_separator
                + filename_suffix + filename_format;
        return result;
    }

    private static double calculateMagnitude(float x, float y, float z){
        return Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
    }

    /* Checks if external storage is available for read and write */
    /* Taken from the Android official documentation */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    private int generateFilenameCounter(File path) {
        File folder = new File(path.toString());
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles.length > 0) {
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    String filename = listOfFiles[i].getName();
                    String[] filename_parts = filename.split(filename_separator);

                    // String example: 2018-06-07_2_watch_sample
                    int sequence_number = Integer.parseInt(
                            filename_parts[filename_parts.length - 3]);

                    filename_counter = Math.max(sequence_number, filename_counter);
                }
            }
        }
        return ++filename_counter;
    }


    private void initBluetooth() {
        // Register for broadcasts when a device is discovered.
        Intent enableBtIntent = connManager.checkIfEnabled();
        if (enableBtIntent != null) {
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        connManager.accept();
    }
}
