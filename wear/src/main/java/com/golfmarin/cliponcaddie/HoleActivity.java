package com.golfmarin.cliponcaddie;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;//
import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GestureDetectorCompat;
import android.support.wearable.view.DismissOverlayView;
import android.support.wearable.view.WatchViewStub;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;


import android.util.Log;

import java.util.ArrayList;

import static android.util.FloatMath.cos;
import static android.util.FloatMath.sin;
import static android.util.FloatMath.sqrt;

public class HoleActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        View.OnLongClickListener,
        SensorEventListener {

    private static final String TAG = "HoleActivity";

    private static final String KEY_IN_RESOLUTION = "is_in_resolution";

    /**
     * Request code for auto Google Play Services error resolution.
     */
    protected static final int REQUEST_CODE_RESOLUTION = 1;

    private TextView holeView;
    private TextView backView;
    private TextView middleView;
    private TextView frontView;

    private GestureDetectorCompat gestureDetector;

    private GoogleApiClient googleClient;
    /**
     * Determines if the client is in a resolution state, and
     * waiting for resolution intent to return.
     */
    private boolean mIsInResolution;

    // Sensor globals
    private SensorManager senSensorManager;
    private Sensor senAccelerometer;

    // Create a constant to convert nanoseconds to seconds.
    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];

    private float timestamp;

    /**
     * Local broadcasts (future for data layer)
     */
    MessageReceiver messageReceiver;
    IntentFilter messageFilter;

    private ArrayList<Course> allCourses;
    private Course currentCourse;
    private ArrayList<Hole> allHoles;
    private Integer currentHoleNum;
    private Hole currentHole;
    private Boolean startup = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "Wearable hole activity started");

        setContentView(R.layout.activity_hole);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                holeView = (TextView) stub.findViewById(R.id.hole);
                backView = (TextView) stub.findViewById(R.id.back);
                middleView = (TextView) stub.findViewById(R.id.middle);
                frontView = (TextView) stub.findViewById(R.id.front);

                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        });

        // Initialize data model containing all golf courses
        DataModel dm = new DataModel(this);
        allCourses = dm.getCourses();
        Log.i(TAG, "All courses: " + allCourses);

        // Setup a local broadcast receiver
        messageFilter = new IntentFilter(Intent.ACTION_SEND);
        messageReceiver = new MessageReceiver();

        // Set up gesture detector
        gestureDetector = new GestureDetectorCompat(this, new MyGestureListener());

        // Set up sensor listener
        senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        // Disable screen timeout, the sensors will handle dimming
        // Settings.System.putString(getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, "-1");

        // Turn off auto brightness
        int brightnessMode = 0;
        Log.i(TAG, "Ready to set brightness mode.");
        //try {
        //   brightnessMode = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
        //   Log.i(TAG, "Screen brightness mode: " + brightnessMode);
        //   if (brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
        //       Log.i(TAG, "Found automatic screen brightness.");
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        //}
        //catch(Settings.SettingNotFoundException e) {
        //    Log.i(TAG, "Screen brightness setting threw an exception");
        //}

    }

    // *************************
    // App lifecycle callbacks
    // **************************

    // Connect to location services and data layer when the Activity starts
    @Override
    protected void onStart() {
        super.onStart();

        // Create a Google API client for the data layer and location services
        if (googleClient == null) {
            googleClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        // Connect the Google API client when the Activity becomes visible
        googleClient.connect();
        Log.i(TAG, "Connecting watch google client.");
    }

    // Disconnect from Google Play Services when the Activity is no longer visible
    @Override
    protected void onStop() {
        if ((googleClient != null) && googleClient.isConnected()) {
            googleClient.disconnect();
        }
        // Must be done to manage selection of the current golf course
        startup = true;
        super.onStop();
    }

    @Override
    protected void onPause() {
        // Unregister listeners
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        senSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        // Register a local broadcast receiver, defined below.
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);

        // Register the sensor manager
        senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_UI);
    }

    /**
     * Save the resolution state.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_RESOLUTION, mIsInResolution);
    }

    // ******************************
    // Google Connection callbacks
    // ******************************
    @Override
    public void onConnected(Bundle connectionHint) {

        Log.i(TAG, "Connected to google services");
        // Register for location services

        // Create the LocationRequest object
        LocationRequest locationRequest = LocationRequest.create();
        // Use high accuracy
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 2 seconds
        locationRequest.setInterval(2);
        // Set the fastest update interval to 2 seconds
        locationRequest.setFastestInterval(2);
        // Set the minimum displacement
        locationRequest.setSmallestDisplacement(2);

        // Register listener using the LocationRequest object
        LocationServices.FusedLocationApi.requestLocationUpdates(googleClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.i(TAG, "GoogleApiClient connection suspended");
        retryConnecting();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "GoogleApiClient connection failed: " + connectionResult.toString());
        if (!connectionResult.hasResolution()) {
            // Show a localized error dialog.
            GooglePlayServicesUtil.getErrorDialog(
                    connectionResult.getErrorCode(), this, 0, new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            retryConnecting();
                        }
                    }).show();
            return;
        }
        // If there is an existing resolution error being displayed or a resolution
        // activity has started before, do nothing and wait for resolution
        // progress to be completed.
        if (mIsInResolution) {
            return;
        }
        mIsInResolution = true;
        try {
            connectionResult.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
            retryConnecting();
        }

    }

    /**
     * Handle Google Play Services resolution callbacks.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CODE_RESOLUTION:
                retryConnecting();
                break;
        }
    }

    private void retryConnecting() {
        mIsInResolution = false;
        if (!googleClient.isConnecting()) {
            googleClient.connect();
        }
    }

    /**
     * ******************************
     * Location service callback
     *
     * @param location *********************************
     */

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "Location Changed for wearable.");
        // Find closest course, if just starting up and location is valid
        if (((startup == true) && location != null)) {
            currentCourse = (getCurrentCourse(location)).get(0);
            if (currentCourse == null) return;
            else {
                startup = false;
                allHoles = currentCourse.holeList;
                currentHoleNum = 1;
                currentHole = allHoles.get(0);
                Log.i(TAG, "Local course successfully initialized: " + currentCourse.name);
                Log.i(TAG, "Current hole location: " + "Location");
            }
        }
        // Refresh the distances to hole placements
        if ((location != null) && (location.getAccuracy() < 25.0) && (location.getAccuracy() > 0.0)) {
            updateDisplay(location);
        }
    }

    // Long click closes the wearable app
    public boolean onLongClick(View v) {
        //    dismissOverlayView.show();
        return true;
    }

    // Local broadcast receiver callback to receive messages
    // forwarded by the data layer listener service.
    public class MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(Intent.ACTION_SEND)) {

                Bundle b = intent.getBundleExtra("distances");
                if (b != null) {
                    Log.i(TAG, "Wearable device, message received on data layer.");
                }
            }
        }
    }

    //************************************
    // Gesture handling
    // Swipe increments or decrements hole number
    // Double tap changes to alternate course
    // Long press to dismiss app (later)
    //************************************

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.i(TAG, "onTouchEvent override entered");
        this.gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public boolean onDown(MotionEvent event) {
            Log.i(TAG, "onDown: " + event.toString());
            return true;
        }

        @Override
        public void onLongPress(MotionEvent event) {
            Log.i(TAG, "onLongClick: " + event.toString());
            //        dismissOverlayView.show();
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {
            Log.i(TAG, "onDoubleTap: " + event.toString());
            // Change to alternate course at same golf club
            /*
            if (currentCourses.get(1) != null) {
                ArrayList<Course> newCurrentCourses = new ArrayList<Course>{};
                newCurrentCourses.add(currentcourses.get(1));
                newCurrentCourses.add(currentCourses.get(0));
            }
            */
            return true;
        }

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            Log.i(TAG, "onFling: " + event1.toString() + event2.toString());

            if (event1.getX() < event2.getX()) {
                // Swipe left (minus)
                new SendToDataLayerThread("/swipe", "minus").start();
                if (currentHoleNum > 1) {
                    currentHoleNum--;
                    currentHole = allHoles.get(currentHoleNum - 1);
                }
            } else {
                // Swipe right (plus)
                new SendToDataLayerThread("/swipe", "plus").start();
                if (currentHoleNum < allHoles.size()) {
                    currentHoleNum++;
                    currentHole = allHoles.get(currentHoleNum - 1);
                }
            }
            updateDisplay(LocationServices.FusedLocationApi.getLastLocation(googleClient));
            return true;
        }
    }


    class SendToDataLayerThread extends Thread {
        String path;
        String message;

        // Constructor to send a message to the data layer
        SendToDataLayerThread(String p, String msg) {
            path = p;
            message = msg;
        }

        public void run() {
            NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(googleClient).await();
            for (Node node : nodes.getNodes()) {
                MessageApi.SendMessageResult result = Wearable.MessageApi.sendMessage(googleClient, node.getId(), path, message.getBytes()).await();
                if (result.getStatus().isSuccess()) {
                    Log.i(TAG, "Message: {" + message + "} sent to: " + node.getDisplayName());
                } else {
                    // Log an error
                    Log.i(TAG, "ERROR: failed to send Message");
                }
            }
        }
    }

    /*****************************
     * Course and hole methods
     ******************************/

    /**
     * Scans through list of courses to find the one close to the current location.
     *
     * @param location current gps location
     * @return Courses closest to current location
     */
    private ArrayList<Course> getCurrentCourse(Location location) {

        // Search for course(s) closest to current location

        ArrayList<Course> bestCourses = new ArrayList<Course>();
        float bestYards = 20000;
        float conv = (float) 1.0936133;

        for (Course course : allCourses) {

            // Not all courses have hole locations, skip those

            if (course.holeList != null) {
                float yards = location.distanceTo(course.getLocation()) * conv;
                if (yards < bestYards) {
                    bestYards = yards;
                    bestCourses.clear();
                    bestCourses.add(course);
                    Log.i(TAG, "Found closer course. Yards: " + yards + ", Course: " + course.name);
                }
                // Some clubs have multiple courses
                else if (yards == bestYards) {
                    bestCourses.add(course);
                    Log.i(TAG, "Found club with multiple courses");
                }
            }

/*
            if (course.name.equals("Emerald Hills Golf Course")) {
                bestCourses.clear();
                bestCourses.add(course);
                Log.i(TAG, "Matched course: " + course.name);
            }
*/

            Log.i(TAG, "Course name: " + course.name);
        }

        startup = false;
        return bestCourses;
    }

    /**
     * Calculates distances to placements and updates the UI
     *
     * @param location Current watch location
     */

    private void updateDisplay(Location location) {
        float accuracy;
        accuracy = location.getAccuracy();
        Log.i(TAG, "Location accuracy on wearable:" + accuracy);

        float conv = (float) 1.0936133;
        float yards = location.distanceTo(currentHole.getLocation("front")) * conv;
        String front = String.valueOf((int) yards);
        frontView.setText(front);

        yards = location.distanceTo(currentHole.getLocation("middle")) * conv;
        String middle = String.valueOf((int) yards);
        middleView.setText(middle);

        yards = location.distanceTo(currentHole.getLocation("back")) * conv;
        String back = String.valueOf((int) yards);
        backView.setText(back);

        // Keep the hole number display current
        holeView.setText("Hole " + currentHole.holeNum);
    }

    /**
     * Handle Sensor callbacks
     */

    private Handler displayHandler = new Handler();

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        // The light sensor returns a single value.
        // Many sensors return 3 values, one for each axis.
        // float lux = event.values[0];
        // Do something with this sensor value.

        // This timestep's delta rotation to be multiplied by the current rotation
        // after computing it from the gyro sample data.

        if (timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * NS2S;
            // Axis of the rotation sample, not normalized yet.
            float axisX = event.values[0];
            float axisY = event.values[1];
            float axisZ = event.values[2];

            // Calculate the angular speed of the sample
            float omegaMagnitude = sqrt(axisX * axisX + axisY * axisY + axisZ * axisZ);

            Log.i(TAG, "omegaMagnitude = " + omegaMagnitude);

            // Normalize the rotation vector if it's big enough to get the axis
            // (that is, EPSILON should represent your maximum allowable margin of error)
            //   if (omegaMagnitude > EPSILON) {
            axisX /= omegaMagnitude;
            axisY /= omegaMagnitude;
            axisZ /= omegaMagnitude;

            Log.i(TAG, "axisX, axisY, axisZ: " + axisX + ", " + axisY + ", " + axisZ);
            //   }
            if ((axisZ < .7) ) {
                Log.i(TAG, "Dim brightness");
                Settings.System.putString(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, "0");
                //WindowManager.LayoutParams lp = this.getWindow().getAttributes();
                //    lp.screenBrightness =0.0f;
                //    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;
                requireRotate = 0;

            } else if ((axisZ > .9) && (requireRotate == 0))  {
                Log.i(TAG, "Restore brightness");
                Settings.System.putString(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, "255");

                //    WindowManager.LayoutParams lp = this.getWindow().getAttributes();
                //    lp.screenBrightness = (WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE);

                // Start a handler to implement the maximum time for normal brightness
                displayHandler.postDelayed(displayTimeout, 4000);
                requireRotate = 1;
            }

            // Integrate around this axis with the angular speed by the timestep
            // in order to get a delta rotation from this sample over the timestep
            // We will convert this axis-angle representation of the delta rotation
            // into a quaternion before turning it into the rotation matrix.
            float thetaOverTwo = omegaMagnitude * dT / 2.0f;
            float sinThetaOverTwo = sin(thetaOverTwo);
            float cosThetaOverTwo = cos(thetaOverTwo);
            deltaRotationVector[0] = sinThetaOverTwo * axisX;
            deltaRotationVector[1] = sinThetaOverTwo * axisY;
            deltaRotationVector[2] = sinThetaOverTwo * axisZ;
            deltaRotationVector[3] = cosThetaOverTwo;
        }
        timestamp = event.timestamp;
        float[] deltaRotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaRotationMatrix, deltaRotationVector);

    }

    // Dim the display after a timeout
    // Set flag that requires wrist rotation to restore brightness

    int requireRotate = 0;

    protected Runnable displayTimeout = new Runnable() {
        @Override
        public void run() {
            Settings.System.putString(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, "0");
            requireRotate = 1;
        }
    };
}


