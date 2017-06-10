package com.golfmarin.cliponcaddie;

/*
        Copyright (C) 2015  Michael Hahn

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;//
import android.content.IntentSender;
import android.content.pm.PackageManager;
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
import android.support.wearable.activity.WearableActivity;
import android.support.wearable.view.BoxInsetLayout;
import android.support.wearable.view.DismissOverlayView;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.ResultCallbacks;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.Calendar;

import static android.media.CamcorderProfile.get;
import static java.lang.Integer.valueOf;

/*
  This activity initializes with the closest course
  then displays distances from current location to the hole placements.
*/

public class HoleActivity extends WearableActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener{


    // Drop the sensor listener and use ambient enter and exit instead
    // SensorEventListener

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
    private ProgressBar progressView;

    private GestureDetectorCompat gestureDetector;

    private GoogleApiClient googleClient;

    // Sensor globals
    private boolean mIsInResolution;
    private SensorManager senSensorManager;
    private Sensor senAccelerometer;

    // Constant to convert nanoseconds to seconds.
    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];

    private float timestamp;

    /*
     * Local broadcasts (future for data layer)
     */
    MessageReceiver messageReceiver;
    IntentFilter messageFilter;

    /*
    * Golf course variables
     */
    private ArrayList<Course> allCourses;
    private Course currentCourse;
    private Course nearbyCourse;
    private ArrayList<Hole> allHoles;
    private Integer currentHoleNum;
    private Hole currentHole;
    private Boolean startup = true;
    private BoxInsetLayout mContainerView;

   private Integer onStoppedDay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hole);
        setAmbientEnabled();
        mContainerView = (BoxInsetLayout) findViewById(R.id.watch_view_stub);


        holeView = (TextView) mContainerView.findViewById(R.id.hole);
        backView = (TextView) mContainerView.findViewById(R.id.back);
        middleView = (TextView) mContainerView.findViewById(R.id.middle);
        frontView = (TextView) mContainerView.findViewById(R.id.front);
        progressView = (ProgressBar) mContainerView.findViewById(R.id.progress_bar);



        // Enable the ambient mode
         setAmbientEnabled();


        // Initialize data model containing all golf courses
        DataModel dm = new DataModel(this);
        allCourses = dm.getCourses();
        Log.v(TAG, "All courses: " + allCourses);

        // Setup a local broadcast receiver
        messageFilter = new IntentFilter(Intent.ACTION_SEND);
        messageReceiver = new MessageReceiver();

        // Set up gesture detector
        gestureDetector = new GestureDetectorCompat(this, new MyGestureListener());

        // Turn off auto brightness
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        // Recreate previously destroyed global variables, if present
        // and not out of date
        if (savedInstanceState != null) {
            //Get date of saved instance (day of year)
            Calendar calendar = Calendar.getInstance();
            Integer dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
            Integer dayOfYearSaved = savedInstanceState.getInt("daysaved");

            // If today, restore current member valuesstate
            if (dayOfYear.equals(dayOfYearSaved)) {
                startup = savedInstanceState.getBoolean("startup");
                currentCourse = savedInstanceState.getParcelable("currentcourse");
                allHoles = savedInstanceState.getParcelableArrayList("allholes");
                currentHole = savedInstanceState.getParcelable("currenthole");
                currentHoleNum = savedInstanceState.getInt("currentholenum");
                nearbyCourse = savedInstanceState.getParcelable("nearbycourse");

                Log.v(TAG, "Restored current course members during recreation of app");
                Log.v(TAG, "Current day of year: " + dayOfYear);
                Log.v(TAG, "Day of year when saved: " + dayOfYearSaved);
                if (startup != null) Log.v(TAG, "startup: " + startup);
                if (currentCourse != null) Log.v(TAG, "currentCourse: " + currentCourse.name);
                if (allHoles != null) Log.v(TAG, "allHoles array is present");
                if (currentHoleNum != null) Log.v(TAG, "currentHoleNum: " + currentHoleNum);
                if (currentHole != null) Log.v(TAG, "currentHole Middle: " + currentHole.middle);
                if (nearbyCourse != null) Log.v(TAG, "nearbyCourse: " + nearbyCourse.name);
            }

        } else {
            // Otherwise, initialize members with default values for the new instance
            // Set startup state
            startup = true;
            currentCourse = null;
            allHoles = null;
            currentHole = null;
            currentHoleNum = 1;
            nearbyCourse = null;
            Log.v(TAG, "Set startup to true and current course variables to null on creation/recreation of app");
        }
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
    }

    // Disconnect from Google Play Services when the Activity is no longer visible
    @Override
    protected void onStop() {
        if ((googleClient != null) && googleClient.isConnected()) {
            googleClient.disconnect();
        }
        // Manage selection of the current golf course after a stop
        Calendar calendar = Calendar.getInstance();
        onStoppedDay = calendar.get(Calendar.DAY_OF_YEAR);
        Log.v(TAG, "onStop executed on day: " + onStoppedDay);
        startup = true;
        super.onStop();
    }

   @Override
   protected void onRestart() {
       super.onRestart();

       Calendar calendar = Calendar.getInstance();
       Integer onRestartDay = calendar.get(Calendar.DAY_OF_YEAR);
       Log.v(TAG, "onRestart executed on day: " + onRestartDay);
       Log.v(TAG, "onStop was executed on day: " + onStoppedDay);

       // Force startup state to true
       startup = true;
       updateGpsView(startup);
    }

    @Override
    protected void onPause() {
        // Unregister listeners
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
        super.onPause();
    }

    @Override
    protected void onResume() {
        // Register a local broadcast receiver, defined below.
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);
        // Force startup state to true
        startup = true;
        updateGpsView(startup);
    }

    /**
     * Save the instance state.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IN_RESOLUTION, mIsInResolution);

        outState.putBoolean("startup",startup);
        outState.putParcelable("currentcourse",currentCourse);
        outState.putParcelableArrayList("allholes",allHoles);
        outState.putInt("currentholenum",currentHoleNum);
        outState.putParcelable("currenthole",currentHole);
        outState.putParcelable("nearbycourse", nearbyCourse);

        Calendar calendar = Calendar.getInstance();
        int dayOfYearSaved = calendar.get(Calendar.DAY_OF_YEAR);
        outState.putInt("daysaved",dayOfYearSaved);

        Log.v(TAG, "Save instance state variables");
        Log.v(TAG, "dayOfYearSaved: " + dayOfYearSaved);
        if(startup != null) Log.v(TAG, "startup: " + startup);

        if (currentCourse != null) {Log.v(TAG, "currentCourse: " + currentCourse.name);}
        if (allHoles != null) Log.v(TAG, "allHoles array is present");
        if (currentHoleNum != null) Log.v(TAG, "currentHoleNum: " + currentHoleNum);
        if (currentHole != null) Log.v(TAG, "currentHole Middle: " + currentHole.middle);
        if (nearbyCourse != null) Log.v(TAG, "nearbyCourse: " + nearbyCourse.name);
    }

    // ******************************
    // Google Connection callbacks
    // ******************************
    @Override
    public void onConnected(Bundle connectionHint) {

        // First check the wearable for onboard GPS
        boolean gpsPresent = getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        if (!gpsPresent) {
            // TODO check for GPS enabled on handheld
        }

        // Register for location services

        LocationRequest locationRequest = LocationRequest.create();
        // Use high accuracy
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Set the update interval to 2 seconds
        locationRequest.setInterval(2000);
        // Set the fastest update interval to 2 seconds
        locationRequest.setFastestInterval(2000);
        // Set the minimum displacement to 2 meters
        locationRequest.setSmallestDisplacement(2);

        // Register listener using the LocationRequest object
        LocationServices.FusedLocationApi.requestLocationUpdates(googleClient, locationRequest, this);

        // Get the last location and invoke the golf course setup procedure
        onLocationChanged(LocationServices.FusedLocationApi.getLastLocation(googleClient));

        // Drop the progress view
        // progressView.setVisibility(View.GONE);
        // Log.v("HoleActivity", "Progress View set to Gone");
    }

    @Override
    public void onConnectionSuspended(int cause) {
        retryConnecting();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

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
     * Location service callback
    */

    @Override
    public void onLocationChanged(Location location) {

        // Wait for a usable location
        if ((location != null) &&
                (location.getAccuracy() < 25.0) &&
                (location.getAccuracy() > 0.0)) {
            // Find closest course, if just starting up
            Log.v(TAG, "Distances updating using " + location.toString());
            if (startup) {
                // Do a search for the closest course and verify that one was found
                ArrayList<Course>  bestCourses  = new ArrayList<Course>();
                bestCourses = getCurrentCourse(location);

                if (bestCourses.size() > 0) {
                    currentCourse = bestCourses.get(0);
                    if (bestCourses.size() > 1)
                        nearbyCourse = bestCourses.get(1);

                    allHoles = currentCourse.holeList;
                    currentHole = allHoles.get(currentHoleNum - 1);
                    startup = false;
                }
            }
        }
        // Refresh the distances to hole placements
        if (!startup) updateDisplay(location);
    }

    // Local broadcast receiver callback to receive messages
    // forwarded by the data layer listener service.
    public class MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            //      TODO Future communications with a handheld
        }
    }

    //************************************
    // Gesture handling
    // Swipe increments or decrements hole number
    // Double tap changes to alternate course
    // Long press to dismiss app
    //************************************

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        this.gestureDetector.onTouchEvent(event);
        return gestureDetector.onTouchEvent(event)||super.onTouchEvent(event);
    }

    class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final String DEBUG_TAG = "Gestures";

        @Override
        public boolean onDown(MotionEvent event) {

            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent event) {

            // Change to alternate course at the same golf club (future)

            return true;
        }

        // Move to next or previous hole

        @Override
        public boolean onFling(MotionEvent event1, MotionEvent event2,
                               float velocityX, float velocityY) {
            if (!startup && currentHoleNum != null) {
                if (event1.getX() < event2.getX()) {
                    // Future, send swipe to handheld
                    // Swipe left (minus)
         //           new SendToDataLayerThread("/swipe", "minus").start();
                    if (currentHoleNum > 1) {
                        currentHoleNum--;
                        currentHole = allHoles.get(currentHoleNum - 1);
                    }
                } else {
                    // Future, send swipe t0 handheld
                    // Swipe right (plus)
         //           new SendToDataLayerThread("/swipe", "plus").start();
                    if (currentHoleNum < allHoles.size()) {
                        currentHoleNum++;
                        currentHole = allHoles.get(currentHoleNum - 1);
                    }
                }
                updateDisplay(LocationServices.FusedLocationApi.getLastLocation(googleClient));
            }
            return true;
        }

        // TODO Future communications with handheld
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

                    } else {
                        // Log an error

                    }
                }
            }
        }
    }

     /******************************
      * Ambient mode callbacks
      * This replaces the earlier motion detection
      *******************************/

     @Override
     public void onEnterAmbient(Bundle ambientDetails) {
         super.onEnterAmbient(ambientDetails);
         Log.v(TAG,"Entered Ambient.");

         // Disable antialias for devices with low-bit ambient
         holeView.getPaint().setAntiAlias(false);
         frontView.getPaint().setAntiAlias(false);
         middleView.getPaint().setAntiAlias(false);
         backView.getPaint().setAntiAlias(false);
     }

      @Override
      public void onExitAmbient() {
          Log.v(TAG, "ExitedAmbient");
          // Restore display
       //   final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
       //   stub.invalidate();

          // Renable antialias for normal display
          holeView.getPaint().setAntiAlias(true);
          frontView.getPaint().setAntiAlias(true);
          middleView.getPaint().setAntiAlias(true);
          backView.getPaint().setAntiAlias(true);

          super.onExitAmbient();
      }

         @Override
         public void onUpdateAmbient() {
             // Update hole distances using current location
             super.onUpdateAmbient();
             Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(googleClient);
             updateDisplay(currentLocation);
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

        // Search for course closest to current location
        Log.v(TAG, "getCurrentCourse started.");

        ArrayList<Course> bestCourses = new ArrayList<Course>();
        float bestYards = 20000;
        float conv = (float) 1.0936133;

        //Search through all courses and find the two closest
        for (Course course : allCourses) {
            // Not all courses have hole locations, skip those
            if (course.holeList != null) {
                Log.v(TAG, "Course: " + course);
                float yards = location.distanceTo(course.getLocation()) * conv;
                if (yards < bestYards) {
                    bestYards = yards;
                    // If this is the first course with holes, just add it to bestCourses
                    if (bestCourses.isEmpty())
                        bestCourses.add(course);

                        // Otherwise, shift the previous best from bestCourses index 0 to 1
                    else {
                        bestCourses.add(1, bestCourses.get(0));
                        bestCourses.add((0), course);
                    }
                }
            }
        }

        Log.v(TAG, "The closest course is: " + bestCourses.get(0));
        Log.v(TAG, "The second closest course is: " + bestCourses.get(1));

        return bestCourses;
        }

    /**
     * Calculates distances to placements and updates the UI
     *
     * @param location Current watch location
     */

    private void updateDisplay(Location location) {
        // float accuracy;
        // accuracy = location.getAccuracy();

        if (location != null && currentHole != null) {

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
            String holeViewText = ("Hole " + currentHole.holeNum);
            holeView.setText(holeViewText);

        }
        progressView.setVisibility(View.GONE);
    }

    private void updateGpsView(boolean startupState) {

        if (startupState) {
            progressView.setVisibility(View.VISIBLE);
            holeView.setText("GPS");
            backView.setText("SYNC");
            middleView.setText("");
            frontView.setText("");
        }
        else progressView.setVisibility(View.GONE);
    }
}


