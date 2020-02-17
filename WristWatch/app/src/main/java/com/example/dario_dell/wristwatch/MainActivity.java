package com.example.dario_dell.wristwatch;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;

public class MainActivity extends WearableActivity implements SensorEventListener {

    //view components
    private TextView instructionsTxtView, waitTxtView, goTxtView;
    private Button startBtn;

    private SensorManager senSensorManager;
    private Sensor senAccelerometer;

    //needed for changing the button functionality
    private boolean isStart = true;
    private boolean justStarted = true;
    private String folder_name = "/Accelerometer_Data/";

    //needed for providing unique names to the files
    private int filename_counter = 0;

    //along with date and counter, they provide unique names to the files
    private final String filename_separator = "_";
    private final String filename_suffix = "watch_sample";
    private final String filename_format = ".csv";
    String text_separator = ",";

    // resulting string to write into a CSV file
    // Init: CSV file header
    String to_write = "timestamp,x_acc,y_acc,z_acc\n";

    //variables needed for detecting the Shaking Gesture
    private long lastUpdate = 0;
    private float x, y, z = 0;
    private long curTime = 0;

    //sensitivity for detecting the Shaking Gesture
    private static final int SHAKE_THRESHOLD = 0;
    /* In order to have samples at 100Hz frequency.
    This way (according to the Nyquist-Shannon theorem)
    no actual information is lost in the sampling process
    when reconstructing the 50Hz signal.
    Change it if you need a different frequency */
    private static final int TIME_INTERVAL = 10;
    private final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        instructionsTxtView = findViewById(R.id.instructions);
        waitTxtView = findViewById(R.id.wait);
        goTxtView = findViewById(R.id.go);


        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL);
        }

        startBtn = findViewById(R.id.startBtn);
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                justStarted = false;
                if (isStart){
                    instructionsTxtView.setVisibility(View.INVISIBLE);
                    waitTxtView.setVisibility(View.VISIBLE);
                    startBtn.setText("STOP");
                    startBtn.setEnabled(false);

                    //countdown before starting to register sensor data
                    new CountDownTimer(1000, 1) {
                        public void onTick(long millisUntilFinished) {
                        }

                        @Override
                        public void onFinish() {
                            waitTxtView.setVisibility(View.INVISIBLE);
                            goTxtView.setVisibility(View.VISIBLE);
                            senSensorManager.registerListener(MainActivity.this,
                                    senAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
                            startBtn.setEnabled(true);
                        }
                    }.start();

                } else {
                    //View update
                    senSensorManager.unregisterListener(MainActivity.this);
                    startBtn.setText("START");
                    instructionsTxtView.setVisibility(View.VISIBLE);
                    waitTxtView.setVisibility(View.INVISIBLE);
                    goTxtView.setVisibility(View.INVISIBLE);

                    // Write to file after tapping the "Stop" button
                    writeToFile();
                    // Reinitialize the output string
                    to_write = "timestamp,x_acc,y_acc,z_acc\n";

                }
                isStart = !isStart;
            }
        });
    }

    //unregister the sensor when the application hibernates
    protected void onPause() {
        super.onPause();
        //senSensorManager.unregisterListener(this);
    }

    //register the sensor again when the application resumes
    protected void onResume() {
        super.onResume();
        /*if (!justStarted)
            senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);*/
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        Sensor mySensor = sensorEvent.sensor;

        if (mySensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION/* Sensor.TYPE_ACCELEROMETER */) {

            // Acceleration minus Gx on the x-axis
            x = sensorEvent.values[0];
            // Acceleration minus Gy on the y-axis
            y = sensorEvent.values[1];
            // Acceleration minus Gz on the z-axis
            z = sensorEvent.values[2];
            curTime = System.currentTimeMillis();

            /* check whether more than TIME_INTERVAL msec
               (e.g: 10 msec) have passed since the last
               time onSensorChanged was invoked */
            if ((curTime - lastUpdate) >= TIME_INTERVAL) {
                long diffTime = curTime - lastUpdate;
                lastUpdate = curTime;

                to_write += System.currentTimeMillis() + text_separator +
                            x + text_separator +
                            y + text_separator +
                            z + "\n";
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    //Method for writing to file after tapping the "Stop" button
    private void writeToFile() {
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
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
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

}
