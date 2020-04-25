package com.example.dario_dell.smartphone;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ViewWasTouchedListener, Runnable {

    private TextView velocity_x_txtView, velocity_y_txtView,
            maxVelocity_x_txtView, maxVelocity_y_txtView, velocity_magnitude_txtView, pleaseWaitTxtView;
    private DrawingView drawing;
    private ProgressBar progressBar;

    //needed for providing unique names to the files
    private int filename_counter = 0;

    //along with date and counter, they provide unique names to the files
    private final String filename_separator = "_";
    private final String filename_suffix = "smartphone_sample";
    private final String filename_format = ".csv";
    private String folder_name = "/Drawing_Data/";

    // Request code for external write permission
    private final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL = 1;
    private final static String TAG = "SMARTPHONE_APP";


    private ConnManager connManager;
    private final int REQUEST_ENABLE_BT = 1;
    private final static String SMARTWATCH_NAME = "TicWatch Pro 0306";

    // resulting string to write into a CSV file
    // Init: CSV file header
    String header = "seq_number,timestamp,x,y,x_velocity,y_velocity,x_velocity_filtered,y_velocity_filtered\n";
    String text_separator = ",";

    private VelocityTracker velocityTracker = null;
    private float max_velocity_x, max_velocity_y;

    // needed for inch to meter conversion
    DisplayMetrics metrics = null;
    private final float inchToMeterRatio = (float) 39.3701;

    private long logTime = 0;
    private int generation = 0;

    private MeanFilter meanFilterVelocity;
    private float[] position = new float[2], velocity = new float[2], filtered_velocity = new float[2];
    private Handler handler;
    private boolean logData = false;
    private String log = "";
    List<Float> x_velocity, y_velocity;
    private boolean isInitialized = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        velocity_x_txtView = findViewById(R.id.velocityx);
        velocity_y_txtView = findViewById(R.id.velocityy);
        maxVelocity_x_txtView = findViewById(R.id.maxvelocityx);
        maxVelocity_y_txtView = findViewById(R.id.maxvelocityy);
        velocity_magnitude_txtView = findViewById(R.id.velocity_magnitude);
        progressBar = findViewById(R.id.indeterminateBar);
        pleaseWaitTxtView = findViewById(R.id.pleaseWait);
        drawing = findViewById(R.id.drawing);
        drawing.setWasTouchedListener(this);
        resetView();


        handler = new Handler();
        handler.post(this);
        meanFilterVelocity = new MeanFilter();
        meanFilterVelocity.setWindowSize(10);
        x_velocity = new ArrayList<>();
        y_velocity = new ArrayList<>();

        connManager = new ConnManager();
        checkExternalWritePermission();
        initBluetooth();
        initProgressBar("", false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }



    @Override
    public void run() {
        handler.postDelayed(this, 20);
        if (!isInitialized) {
            checkInitProgress();
        }

        logData();
    }

    private void checkInitProgress() {
        if (connManager.isKeyExchangeDone()) {
            progressBar.setVisibility(View.GONE);
            pleaseWaitTxtView.setVisibility(View.GONE);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            isInitialized = true;
        }
    }

    private boolean checkPairingProgress() {
        return connManager.isPairingComplete();
    }

    private void initProgressBar(String text, boolean setText) {
        progressBar.setVisibility(View.VISIBLE);
        pleaseWaitTxtView.setVisibility(View.VISIBLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        if (setText) {
            pleaseWaitTxtView.setText(text);
        }
    }

    private void logData() {
        if (logData) {
            if (generation == 0) {
                logTime = System.currentTimeMillis();
            }

            x_velocity.add(filtered_velocity[0]);
            // Multiply velocity by -1 since +ve and -ve axes
            // are the opposite of the ones on the watch
            y_velocity.add(filtered_velocity[1] * -1);

            log += generation++ + ",";
            log += System.currentTimeMillis() - logTime + ",";

            log += position[0] + ",";
            log += position[1] + ",";
            log += velocity[0] + ",";
            log += velocity[1] + ",";
            log += filtered_velocity[0] + ",";
            log += filtered_velocity[1];
            log += System.getProperty("line.separator");
        }
    }

    private void checkExternalWritePermission() {
        if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL);
        }
    }

    private void clear() {
        velocityTracker.recycle();
        velocityTracker = null;
        connManager.setNoisyInput(x_velocity, y_velocity);
        writeToFile();
        //initProgressBar("Pairing in progress, please wait", true);
        generation = 0;
        log = "";
        logData = false;
        x_velocity.clear();
        y_velocity.clear();
        resetView();

        while (!checkPairingProgress());


        boolean pairingResult = connManager.getPairingResult();
        Toast pairingToast;
        if (pairingResult) {
            pairingToast = Toast.makeText(getApplicationContext(), "Pairing successful!", Toast.LENGTH_LONG);
            pairingToast.show();
        }
        else {
            pairingToast = Toast.makeText(getApplicationContext(), "Pairing failed.", Toast.LENGTH_LONG);
            pairingToast.show();
        }
    }

    @Override
    //callback for updating the TextViews
    public void onViewTouched(float x, float y, MotionEvent event) {

        int action = event.getActionMasked();
        metrics = getResources().getDisplayMetrics();
        //Log.d(TAG, "dpi: " + metrics.xdpi*metrics.ydpi);
        //System.out.println(metrics.xdpi*metrics.ydpi);
        //Log.d(TAG, "x-dpi:" + metrics.xdpi);
        //Log.d(TAG, "y-dpi:" + metrics.ydpi);

        switch (action){
            //A pressed gesture has started, the motion contains the initial starting location.
            case MotionEvent.ACTION_DOWN:
                if (velocityTracker == null)
                    velocityTracker = VelocityTracker.obtain();
                else
                    velocityTracker.clear();

                //Add a user's movement to the tracker.
                // This call should be done for the initial ACTION_DOWN,
                // the following ACTION_MOVE events that you receive,
                // and the final ACTION_UP.
                velocityTracker.addMovement(event);
                max_velocity_x = 0;
                max_velocity_y = 0;
                logData = true;

                resetView();
                break;

            case MotionEvent.ACTION_MOVE:
                velocityTracker.addMovement(event);
                //pixels/msec - - - change the parameter if you need different units
                velocityTracker.computeCurrentVelocity(1000);

                float velocity_x = velocityTracker.getXVelocity();
                float velocity_y = velocityTracker.getYVelocity();

                if (velocity_x > max_velocity_x)
                    max_velocity_x = velocity_x;

                if (velocity_y > max_velocity_y)
                    max_velocity_y = velocity_y;

                double magnitude = calculateMagnitude(velocity_x,velocity_y);

                position[0] = x/metrics.xdpi/inchToMeterRatio;
                position[1] = y/metrics.ydpi/inchToMeterRatio;

                velocity[0] = toMeterPerSecondsConversion(velocity_x, metrics.xdpi);
                velocity[1] = toMeterPerSecondsConversion(velocity_y, metrics.ydpi);

                filtered_velocity = meanFilterVelocity.filterFloat(velocity);

                updateView(velocity_x, velocity_y, max_velocity_x, max_velocity_y, magnitude);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocityTracker.addMovement(event);
                clear();
                break;
        }
        // Calling invalidate will cause the onDraw method to execute
        this.findViewById(android.R.id.content).invalidate();
    }


    protected static double calculateMagnitude(float x, float y){
        return Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2));
    }


    private void updateView(float velocity_x, float velocity_y,
                            float max_velocity_x, float max_velocity_y, double velocity_magnitude){
        velocity_x_txtView.setText("X-velocity: " + Float.toString(velocity_x));
        velocity_y_txtView.setText("Y-velocity: " + Float.toString(velocity_y));
        maxVelocity_x_txtView.setText("max. X-velocity: " + Float.toString(max_velocity_x));
        maxVelocity_y_txtView.setText("max. Y-velocity: " + Float.toString(max_velocity_y));
        velocity_magnitude_txtView.setText("Velocity magnitude: " + Double.toString(velocity_magnitude));
    }


    private void resetView(){

        updateView(0,0,0,0, 0);
    }


    private void resetXYVelocityAndMagnitudeViews(float max_velocity_x, float max_velocity_y,
                                                  double velocity_magnitude){
        updateView(0,0, max_velocity_x, max_velocity_y, 0);
    }


    //generates sequenced unique filenames
    private String generateSequencedFilename(File path){
        return LocalDate.now().toString() + filename_separator
                + generateFilenameCounter(path) + filename_separator
                + filename_suffix + filename_format;
    }


    /* Checks if external storage is available for read and write */
    /* Taken from the Android official documentation */
    private boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }


    //Method for writing to file after removing the finger form the screen
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
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private int generateFilenameCounter(File path) {
        File folder = new File(path.toString());
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles.length > 0) {
            for (int i = 0; i < listOfFiles.length; i++) {
                if (listOfFiles[i].isFile()) {
                    String filename = listOfFiles[i].getName();
                    String[] filename_parts = filename.split(filename_separator);

                    // String example: 2018-06-07_2_smartphone_sample
                    int sequence_number = Integer.parseInt(
                            filename_parts[filename_parts.length - 3]);

                    filename_counter = Math.max(sequence_number, filename_counter);
                }
            }
        }
        return ++filename_counter;
    }

    // velocityTracker returns pixels/msecs => need to convert to m/sec
    private float toMeterPerSecondsConversion (float velocity, float dpi){
        //max hand speed should be around 65 m/s (Needs to be double-checked)
        //return velocity*inchToMeterRatio/dpi;   // wrong conversion imo
        return velocity/dpi/inchToMeterRatio;   // old wrong version as per Dario
    }

    private void initBluetooth() {
        // Register for broadcasts when a device is discovered.
        Intent enableBtIntent = connManager.checkIfEnabled();
        if (enableBtIntent != null) {
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        Set<BluetoothDevice> pairedDevices = connManager.getBluetoothAdapter().getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals(SMARTWATCH_NAME)) {
                    Log.d(TAG, "smartwatch found");
                    connManager.connect(device);
                }
            }
        }

    }


}