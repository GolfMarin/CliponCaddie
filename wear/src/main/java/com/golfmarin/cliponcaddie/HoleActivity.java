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
import android.support.wearable.view.DismissOverlayView;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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

import java.io.IOException;
import java.util.ArrayList;

/*
  This activity initializes with the closest course
  then displays distances from current location to the hole placements.
*/

public class HoleActivity extends WearableActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener
         {

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

    private DismissOverlayView dismissOverlayView;

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
    private ArrayList<Hole> allHoles;
    private Integer currentHoleNum;
    private Hole currentHole;
    private Boolean startup = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_hole);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                holeView = (TextView) stub.findViewById(R.id.hole);
                backView = (TextView) stub.findViewById(R.id.back);
                middleView = (TextView) stub.findViewById(R.id.middle);
                frontView = (TextView) stub.findViewById(R.id.front);

            }
        });

        // Enable the ambient mode
        setAmbientEnabled();

        // Initialize data model containing all golf courses
        DataModel dm = new DataModel(this);
        allCourses = dm.getCourses();

        // Setup a local broadcast receiver
        messageFilter = new IntentFilter(Intent.ACTION_SEND);
        messageReceiver = new MessageReceiver();

        // Set up gesture detector
        gestureDetector = new GestureDetectorCompat(this, new MyGestureListener());

        // Set up dismiss overlay view
        dismissOverlayView = (DismissOverlayView) findViewById(R.id.dismiss_overlay);
        dismissOverlayView.setIntroText(R.string.dismiss_intro);
        dismissOverlayView.showIntroIfNecessary();



        // Turn off auto brightness
        Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
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
        // Must be done to manage selection of the current golf course
        startup = true;
        super.onStop();
    }

    @Override
    protected void onPause() {
        // Unregister listeners
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver);
//        senSensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        // Register a local broadcast receiver, defined below.
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);

        // Register the sensor manager
//        senSensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_UI);
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
        // Find closest course, if just starting up and location is valid
        if (((startup == true) && location != null)) {
            currentCourse = (getCurrentCourse(location)).get(0);
            Log.i(TAG, "Current course: " + currentCourse.name);
            if (currentCourse == null) return;
            else {
                startup = false;
                allHoles = currentCourse.holeList;
                currentHoleNum = 1;
                currentHole = allHoles.get(0);
            }
        }
        // Refresh the distances to hole placements
        if ((location != null) && (location.getAccuracy() < 25.0) && (location.getAccuracy() > 0.0)) {
            updateDisplay(location);
        }
    }

    // Local broadcast receiver callback to receive messages
    // forwarded by the data layer listener service.
    public class MessageReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(Intent.ACTION_SEND)) {

                Bundle b = intent.getBundleExtra("distances");
                if (b != null) {
                }
            }
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

        // Display dismiss overlay to close this app

        @Override
        public void onLongPress(MotionEvent event) {

            dismissOverlayView.show();
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

                } else {
                    // Log an error

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
         /*
         WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());
         try {
             wallpaperManager.setResource(R.raw.green_outline);
             Log.v(TAG,"Wallpaper set.");
         }
         catch (IOException e) {
             Log.v(TAG, "Wallpaper manger failed");
         }
         */
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
          final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
          stub.invalidate();
/*
          WallpaperManager wallpaperManager = WallpaperManager.getInstance(getApplicationContext());
          try {
              wallpaperManager.setResource(R.raw.greenback);
              Log.v(TAG,"Wallpaper set.");
          }
          catch (IOException e) {
              Log.v(TAG, "Wallpaper manger failed");
          }

*/
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
             Location currentLocation = LocationServices.FusedLocationApi.getLastLocation(googleClient);
             Log.v(TAG, "Distances updating using" + currentLocation.toString());
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

                }
                // Some clubs have multiple courses
                else if (yards == bestYards) {
                    bestCourses.add(course);

                }
            }

/*
            if (course.name.equals("Golf Course Name")) {
                bestCourses.clear();
                bestCourses.add(course);
            }
*/
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
            holeView.setText("Hole " + currentHole.holeNum);
        }
    }
}


