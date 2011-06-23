/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.radioactiveyak.location_best_practices.UI;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.radioactiveyak.location_best_practices.PlacesConstants;
import com.radioactiveyak.location_best_practices.R;
import com.radioactiveyak.location_best_practices.UI.fragments.CheckinFragment;
import com.radioactiveyak.location_best_practices.UI.fragments.PlaceDetailFragment;
import com.radioactiveyak.location_best_practices.UI.fragments.PlaceListFragment;
import com.radioactiveyak.location_best_practices.receivers.LocationChangedReceiver;
import com.radioactiveyak.location_best_practices.receivers.NewCheckinReceiver;
import com.radioactiveyak.location_best_practices.receivers.PassiveLocationChangedReceiver;
import com.radioactiveyak.location_best_practices.services.EclairPlacesUpdateService;
import com.radioactiveyak.location_best_practices.services.PlacesUpdateService;
import com.radioactiveyak.location_best_practices.utils.PlatformSpecificImplementationFactory;
import com.radioactiveyak.location_best_practices.utils.base.ILastLocationFinder;
import com.radioactiveyak.location_best_practices.utils.base.LocationUpdateRequester;
import com.radioactiveyak.location_best_practices.utils.base.SharedPreferenceSaver;

/**
 * Main application Activity. Used for all application UI, including pre/post Honeycomb 
 * and tablet / phone variations using different layouts. The {@link PlaceDetailFragment},
 * {@link PlaceListFragment}, and {@link CheckinFragment} are all managed from within this Activity.
 * 
 * Because the list, detail, and checkin functionality is encapsulated within their respective
 * Fragments, this Activity serves as a container for each fragment, and the coordinator of their
 * interaction. This Activity also manages the menu, listens for checkins, and manages location updates when the 
 * Application is active.
 * 
 * TODO Update the menu and functionality to support your enhanced UI
 * TODO and service-specific features and requirements
 */
public class PlaceActivity extends FragmentActivity {

  protected static String TAG = "PlaceActivity";

  // TODO (RETO) Add "refreshing" icons when stuff is blank or refreshing.

  protected PackageManager packageManager;
  protected NotificationManager notificationManager;
  protected LocationManager locationManager;
  
//  protected boolean followLocationChanges = true;
  protected SharedPreferences prefs;
  protected Editor prefsEditor;
  protected SharedPreferenceSaver sharedPreferenceSaver;

  protected Criteria criteria;
  protected ILastLocationFinder lastLocationFinder;
  protected LocationUpdateRequester locationUpdateRequester;
  protected PendingIntent locationListenerPendingIntent;
  protected PendingIntent locationListenerPassivePendingIntent;
 
  protected PlaceListFragment placeListFragment;
  protected CheckinFragment checkinFragment;
  protected PlaceDetailFragment placeDetailFragment;
  
  protected IntentFilter newCheckinFilter;
  protected ComponentName newCheckinReceiverName;


  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    // Inflate the layout
    setContentView(R.layout.main);
    
    // Get a handle to the Fragments
    placeListFragment = (PlaceListFragment)getSupportFragmentManager().findFragmentById(R.id.list_fragment);
    checkinFragment = (CheckinFragment)getSupportFragmentManager().findFragmentById(R.id.checkin_fragment);
    
    // Get references to the managers
    packageManager = getPackageManager();
    notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
    
    // Get a reference to the Shared Preferences and a Shared Preference Editor.
    prefs = getSharedPreferences(PlacesConstants.SHARED_PREFERENCE_FILE, Context.MODE_PRIVATE);
    prefsEditor = prefs.edit();  
    
    // Instantiate a SharedPreferenceSaver class based on the available platform version.
    // This will be used to save shared preferences
    sharedPreferenceSaver = PlatformSpecificImplementationFactory.getSharedPreferenceSaver(this);
           
    // Save that we've been run once.
    prefsEditor.putBoolean(PlacesConstants.SP_KEY_RUN_ONCE, true);
    sharedPreferenceSaver.savePreferences(prefsEditor, false);
    
    // Specify the Criteria to use when requesting location updates while the application is Active
    criteria = new Criteria();
    if (PlacesConstants.USE_GPS_WHEN_ACTIVITY_VISIBLE)
      criteria.setAccuracy(Criteria.ACCURACY_FINE);
    else
      criteria.setPowerRequirement(Criteria.POWER_LOW);
    
    // Setup the location update Pending Intents
    Intent activeIntent = new Intent(this, LocationChangedReceiver.class);
    locationListenerPendingIntent = PendingIntent.getBroadcast(this, 0, activeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    Intent passiveIntent = new Intent(this, PassiveLocationChangedReceiver.class);
    locationListenerPassivePendingIntent = PendingIntent.getBroadcast(this, 0, passiveIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    // Instantiate a LastLocationFinder class.
    // This will be used to find the last known location when the application starts.
    lastLocationFinder = PlatformSpecificImplementationFactory.getLastLocationFinder(this);
    lastLocationFinder.setChangedLocationListener(oneShotLastLocationUpdateListener);
    
    // Instantiate a Location Update Requester class based on the available platform version.
    // This will be used to request location updates.
    locationUpdateRequester = PlatformSpecificImplementationFactory.getLocationUpdateRequester(locationManager);
    
    // Create an Intent Filter to listen for checkins
    newCheckinReceiverName = new ComponentName(this, NewCheckinReceiver.class);
    newCheckinFilter = new IntentFilter(PlacesConstants.NEW_CHECKIN_ACTION);
    
    // Check to see if an Place ID has been specified in the launch Intent.
    // If so, we should display the details for the specified venue.
    if (getIntent().hasExtra(PlacesConstants.EXTRA_KEY_ID)) {
      Intent intent = getIntent();
      String key = intent.getStringExtra(PlacesConstants.EXTRA_KEY_ID);
      if (key != null) {
        selectDetail(null, key);
        // Remove the ID from the Intent (so that a resume doesn't reselect).
        intent.removeExtra(PlacesConstants.EXTRA_KEY_ID);
        setIntent(intent);
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    // Commit shared preference that says we're in the foreground.
    prefsEditor.putBoolean(PlacesConstants.EXTRA_KEY_IN_BACKGROUND, false);
    sharedPreferenceSaver.savePreferences(prefsEditor, false);
    
    // Disable the Manifest Checkin Receiver when the Activity is visible.
    // The Manifest Checkin Receiver is designed to run only when the Application
    // isn't active to notify the user of pending checkins that have succeeded 
    // (usually through a Notification). 
    // When the Activity is visible we capture checkins through the checkinReceiver.
    packageManager.setComponentEnabledSetting(newCheckinReceiverName,
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 
        PackageManager.DONT_KILL_APP);
    
    // Register the checkinReceiver to listen for checkins while the Activity is visible.
    registerReceiver(checkinReceiver, newCheckinFilter);
    
    // Cancel notifications.
    notificationManager.cancel(PlacesConstants.CHECKIN_NOTIFICATION);
    
    // Update the CheckinFragment with the last checkin.
    updateCheckinFragment(prefs.getString(PlacesConstants.SP_KEY_LAST_CHECKIN_ID, null));
    
    // Get the last known location (and optionally request location updates) and
    // update the place list.
    boolean followLocationChanges = prefs.getBoolean(PlacesConstants.SP_KEY_FOLLOW_LOCATION_CHANGES, true);
    getLocationAndUpdatePlaces(followLocationChanges);
  }
  
  @Override
  protected void onPause() {
	// Commit shared preference that says we're in the background.
    prefsEditor.putBoolean(PlacesConstants.EXTRA_KEY_IN_BACKGROUND, true);
    sharedPreferenceSaver.savePreferences(prefsEditor, false);
	    
    // Enable the Manifest Checkin Receiver when the Activity isn't active.
    // The Manifest Checkin Receiver is designed to run only when the Application
    // isn't active to notify the user of pending checkins that have succeeded 
    // (usually through a Notification). 
    packageManager.setComponentEnabledSetting(newCheckinReceiverName,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 
        PackageManager.DONT_KILL_APP);
    
    // Unregister the checkinReceiver when the Activity is inactive.
    unregisterReceiver(checkinReceiver);
    
    // Stop listening for location updates when the Activity is inactive.
    disableLocationUpdates();
    
    super.onPause();
  }

  /**
   * Find the last known location (using a {@link LastLocationFinder}) and updates the
   * place list accordingly.
   * @param updateWhenLocationChanges Request location updates
   */
  protected void getLocationAndUpdatePlaces(boolean updateWhenLocationChanges) {
    // This isn't directly affecting the UI, so put it on a worker thread.
    AsyncTask<Void, Void, Void> findLastLocationTask = new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        // Find the last known location, specifying a required accuracy of within the min distance between updates
        // and a required latency of the minimum time required between updates.
        Location lastKnownLocation = lastLocationFinder.getLastBestLocation(PlacesConstants.MAX_DISTANCE, 
            System.currentTimeMillis()-PlacesConstants.MAX_TIME);
        
        // Update the place list based on the last known location within a defined radius.
        // Note that this is *not* a forced update. The Place List Service has settings to
        // determine how frequently the underlying web service should be pinged. This function  
        // is called everytime the Activity becomes active, so we don't want to flood the server
        // unless the location has changed or a minimum latency or distance has been covered.
        // TODO Modify the search radius based on user settings?
        updatePlaces(lastKnownLocation, PlacesConstants.DEFAULT_RADIUS, false);
        return null;
      }
    };
    findLastLocationTask.execute();
    
    // If we have requested location updates, turn them on here.
    toggleUpdatesWhenLocationChanges(updateWhenLocationChanges);
  }

  /**
   * Choose if we should receive location updates. 
   * @param updateWhenLocationChanges Request location updates
   */
  protected void toggleUpdatesWhenLocationChanges(boolean updateWhenLocationChanges) {   
    // Save the location update status in shared preferences
    prefsEditor.putBoolean(PlacesConstants.SP_KEY_FOLLOW_LOCATION_CHANGES, updateWhenLocationChanges);
    sharedPreferenceSaver.savePreferences(prefsEditor, true);

    // Start or stop listening for location changes
    if (updateWhenLocationChanges)
      requestLocationUpdates();
    else 
      disableLocationUpdates();
  }
  
  /**
   * Start listening for location updates.
   */
  protected void requestLocationUpdates() {
    // Normal updates while activity is visible.
    locationUpdateRequester.requestLocationUpdates(PlacesConstants.MAX_TIME, PlacesConstants.MAX_DISTANCE, criteria, locationListenerPendingIntent);

    // Passive location updates from 3rd party apps when the Activity isn't visible.
    locationUpdateRequester.requestPassiveLocationUpdates(PlacesConstants.PASSIVE_MAX_TIME, PlacesConstants.PASSIVE_MAX_DISTANCE, locationListenerPassivePendingIntent);
    
    // Register a receiver that listens for when the provider I'm using has been disabled. 
    IntentFilter intentFilter = new IntentFilter(PlacesConstants.ACTIVE_LOCATION_UPDATE_PROVIDER_DISABLED);
    registerReceiver(locProviderDisabledReceiver, intentFilter);

    // Register a receiver that listens for when a better provider than I'm using becomes available.
    String bestProvider = locationManager.getBestProvider(criteria, false);
    String bestAvailableProvider = locationManager.getBestProvider(criteria, true);
    if (bestProvider != null && !bestProvider.equals(bestAvailableProvider)) {
      locationManager.requestLocationUpdates(bestProvider, 0, 0, bestInactiveLocationProviderListener, getMainLooper());
    }
  }
  
  /**
   * Stop listening for location updates
   */
  protected void disableLocationUpdates() {
    unregisterReceiver(locProviderDisabledReceiver);
    locationManager.removeUpdates(locationListenerPendingIntent);
    locationManager.removeUpdates(bestInactiveLocationProviderListener);
    if (isFinishing())
      lastLocationFinder.cancel();
    if (PlacesConstants.DISABLE_PASSIVE_LOCATION_WHEN_USER_EXIT && isFinishing())
      locationManager.removeUpdates(locationListenerPassivePendingIntent);
  }
  
  /**
   * One-off location listener that receives updates from the {@link LastLocationFinder}.
   * This is triggered where the last known location is outside the bounds of our maximum
   * distance and latency.
   */
  protected LocationListener oneShotLastLocationUpdateListener = new LocationListener() {
    public void onLocationChanged(Location l) {
      updatePlaces(l, PlacesConstants.DEFAULT_RADIUS, true);
    }
   
    public void onProviderDisabled(String provider) {}
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    public void onProviderEnabled(String provider) {}
  };
  
  /**
   * If the best Location Provider (usually GPS) is not available when we request location
   * updates, this listener will be notified if / when it becomes available. It calls 
   * requestLocationUpdates to re-register the location listeners using the better Location
   * Provider.
   */
  protected LocationListener bestInactiveLocationProviderListener = new LocationListener() {
    public void onLocationChanged(Location l) {}
    public void onProviderDisabled(String provider) {}
    public void onStatusChanged(String provider, int status, Bundle extras) {}
    public void onProviderEnabled(String provider) {
      // Re-register the location listeners using the better Location Provider.
      requestLocationUpdates();
    }
  };
  
  /**
   * If the Location Provider we're using to receive location updates is disabled while the 
   * app is running, this Receiver will be notified, allowing us to re-register our Location
   * Receivers using the best available Location Provider is still available.
   */
  protected BroadcastReceiver locProviderDisabledReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      boolean providerDisabled = !intent.getBooleanExtra(LocationManager.KEY_PROVIDER_ENABLED, false);
      // Re-register the location listeners using the best available Location Provider.
      if (providerDisabled)
        requestLocationUpdates();
    }
  };
  
  /**
   * Update the list of nearby places centered on the specified Location, within the specified radius.
   * This will start the {@link PlacesUpdateService} that will poll the underlying web service.
   * @param location Location
   * @param radius Radius (meters)
   * @param forceRefresh Force Refresh
   */
  protected void updatePlaces(Location location, int radius, boolean forceRefresh) {
    if (location != null) {
      Log.d(TAG, "Updating place list.");
      // Start the PlacesUpdateService. Note that we use an action rather than specifying the 
      // class directly. That's because we have different variations of the Service for different
      // platform versions.
      Intent updateServiceIntent = new Intent(this, PlacesConstants.SUPPORTS_ECLAIR ? EclairPlacesUpdateService.class : PlacesUpdateService.class);
      updateServiceIntent.putExtra(PlacesConstants.EXTRA_KEY_LOCATION, location);
      updateServiceIntent.putExtra(PlacesConstants.EXTRA_KEY_RADIUS, radius);
      updateServiceIntent.putExtra(PlacesConstants.EXTRA_KEY_FORCEREFRESH, forceRefresh);
      startService(updateServiceIntent);
    }
    else
      Log.d(TAG, "Updating place list for: No Previous Location Found");
  }
  
  /**
   * Updates (or displays) the venue detail Fragment when a venue is selected
   * (normally by clicking a place on the Place List.
   * @param reference Place Reference
   * @param id Place Identifier
   */
  public void selectDetail(String reference, String id) {
    // If the layout includes a single "main fragment container" then
    // we want to hide the List Fragment and display the Detail Fragment.
    // A back-button click should reverse this operation.
    // This is the phone-portrait mode.
    if (findViewById(R.id.main_fragment_container) != null) {
      placeDetailFragment = PlaceDetailFragment.newInstance(reference, id);
      FragmentManager fragmentManager = getSupportFragmentManager();
      FragmentTransaction ft = fragmentManager.beginTransaction();
      ft.addToBackStack(null);
      if (checkinFragment != null)
        ft.hide(checkinFragment);
      ft.hide(placeListFragment);
      ft.replace(R.id.main_fragment_container, placeDetailFragment);
      ft.show(placeDetailFragment);
      ft.commit();       
    // Otherwise the Detail Fragment is already visible and we can
    // Simply replace the previous Fragment with a new one for the
    // selected Place.
    } else {
      placeDetailFragment = PlaceDetailFragment.newInstance(reference, id);
      FragmentManager fragmentManager = getSupportFragmentManager();
      FragmentTransaction ft = fragmentManager.beginTransaction();
      ft.disallowAddToBackStack();
      ft.replace(R.id.detail_fragment_container, placeDetailFragment);
      ft.commit(); 
    }
  }
 
  /**
   * Receiver that listens for checkins when the Activity is visible.
   * It should update the Checkin Fragment with the details for the
   * last venue checked in to.
   */
  protected BroadcastReceiver checkinReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      String id = intent.getStringExtra(PlacesConstants.EXTRA_KEY_ID);
      if (id != null)
        updateCheckinFragment(id);
    }
  };
  
  /**
   * Request that the {@link CheckinFragment} UI be updated
   * with the details corresponding to the specified ID.
   * @param id Place Identifier
   */
  public void updateCheckinFragment(String id) {
    if (id != null) {
      checkinFragment.setPlaceId(id);
      
      if (placeDetailFragment != null) 
        placeDetailFragment.checkedIn(id);
    }
  }
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.main_menu, menu);
      return true;
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      // Handle item selection
      switch (item.getItemId()) {
      case R.id.refresh:
    	  // Request the last known location.
    	  Location lastKnownLocation = lastLocationFinder.getLastBestLocation(PlacesConstants.MAX_DISTANCE, 
    	            System.currentTimeMillis()-PlacesConstants.MAX_TIME);
    	  // Force an update
          updatePlaces(lastKnownLocation, PlacesConstants.DEFAULT_RADIUS, true);
          return true;
      default:
          return super.onOptionsItemSelected(item);
      }
  }
}