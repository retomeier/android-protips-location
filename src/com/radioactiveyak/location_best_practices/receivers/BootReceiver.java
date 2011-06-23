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

package com.radioactiveyak.location_best_practices.receivers;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;

import com.radioactiveyak.location_best_practices.PlacesConstants;
import com.radioactiveyak.location_best_practices.utils.PlatformSpecificImplementationFactory;
import com.radioactiveyak.location_best_practices.utils.base.LocationUpdateRequester;

/**
 * This Receiver class is designed to listen for system boot.
 * 
 * If the app has been run at least once, the passive location
 * updates should be enabled after a reboot.
 */
public class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    SharedPreferences prefs = context.getSharedPreferences(PlacesConstants.SHARED_PREFERENCE_FILE, Context.MODE_PRIVATE);
  	boolean runOnce = prefs.getBoolean(PlacesConstants.SP_KEY_RUN_ONCE, false);
  	
  	if (runOnce) {
  	  LocationManager locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
  		
        // Instantiate a Location Update Requester class based on the available platform version.
        // This will be used to request location updates.
  	  LocationUpdateRequester locationUpdateRequester = PlatformSpecificImplementationFactory.getLocationUpdateRequester(locationManager); 
   
      // Check the Shared Preferences to see if we are updating location changes.
      boolean followLocationChanges = prefs.getBoolean(PlacesConstants.SP_KEY_FOLLOW_LOCATION_CHANGES, true);
      
      if (followLocationChanges) { 	  
        // Passive location updates from 3rd party apps when the Activity isn't visible.
        Intent passiveIntent = new Intent(context, PassiveLocationChangedReceiver.class);
        PendingIntent locationListenerPassivePendingIntent = PendingIntent.getActivity(context, 0, passiveIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        locationUpdateRequester.requestPassiveLocationUpdates(PlacesConstants.PASSIVE_MAX_TIME, PlacesConstants.PASSIVE_MAX_DISTANCE, locationListenerPassivePendingIntent);
      }
  	}
  }
}