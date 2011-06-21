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

package com.radioactiveyak.location_best_practices.services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.radioactiveyak.location_best_practices.PlacesConstants;
import com.radioactiveyak.location_best_practices.content_providers.QueuedCheckinsContentProvider;
import com.radioactiveyak.location_best_practices.receivers.ConnectivityChangedReceiver;
import com.radioactiveyak.location_best_practices.utils.PlatformSpecificImplementationFactory;
import com.radioactiveyak.location_best_practices.utils.base.SharedPreferenceSaver;

/**
 * Service that notifies the underlying web service to Checkin to the specified venue.
 * TODO Replace or augment with a Service that performs ratings / reviews / etc.
 */
public class PlaceCheckinService extends IntentService {
  
  protected static String TAG = "PlaceCheckinService";
  
  protected ContentResolver contentResolver;
  protected ConnectivityManager cm;
  protected AlarmManager alarmManager;
  protected PendingIntent retryQueuedCheckinsPendingIntent;
  protected SharedPreferences sharedPreferences;
  protected Editor sharedPreferencesEditor;
  protected SharedPreferenceSaver sharedPreferenceSaver;
  
  public PlaceCheckinService() {
    super(TAG);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    contentResolver = getContentResolver();
    cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
    alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
    
    sharedPreferences = getSharedPreferences(PlacesConstants.SHARED_PREFERENCE_FILE, Context.MODE_PRIVATE);
    sharedPreferencesEditor = sharedPreferences.edit();
    sharedPreferenceSaver = PlatformSpecificImplementationFactory.getSharedPreferenceSaver(this);
    
    Intent retryIntent = new Intent(PlacesConstants.RETRY_QUEUED_CHECKINS_ACTION);
    retryQueuedCheckinsPendingIntent = PendingIntent.getBroadcast(this, 0, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  /**
   * {@inheritDoc}
   * Perform a checkin the specified venue. If the checkin fails, add it to the queue and
   * set an alarm to retry.
   * 
   * Query the checkin queue to see if there are pending checkins to be retried.
   */
  @Override
  protected void onHandleIntent(Intent intent) {
    // Retrieve the details for the checkin to perform.
    String reference = intent.getStringExtra(PlacesConstants.EXTRA_KEY_REFERENCE);
    String id = intent.getStringExtra(PlacesConstants.EXTRA_KEY_ID);
    long timeStamp = intent.getLongExtra(PlacesConstants.EXTRA_KEY_TIME_STAMP, 0);

  	// Check if we're running in the foreground, if not, check if
  	// we have permission to do background updates.
  	boolean backgroundAllowed = cm.getBackgroundDataSetting();
  	boolean inBackground = sharedPreferences.getBoolean(PlacesConstants.EXTRA_KEY_IN_BACKGROUND, true);
  	
  	if (reference != null && !backgroundAllowed && inBackground) {
      addToQueue(timeStamp, reference, id);
  	  return;
  	}
        
    // Check to see if we are connected to a data network.
    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();

    // If we're not connected then disable the retry Alarm, enable the Connectivity Changed Receiver
    // and add the new checkin directly to the queue. The Connectivity Changed Receiver will listen
    // for when we connect to a network and start this service to retry the checkins.
    if (!isConnected) {
      // No connection so no point triggering an alarm to retry until we're connected.
      alarmManager.cancel(retryQueuedCheckinsPendingIntent);
      
      // Enable the Connectivity Changed Receiver to listen for connection to a network
      // so we can commit the pending checkins.
      PackageManager pm = getPackageManager();
      ComponentName connectivityReceiver = new ComponentName(this, ConnectivityChangedReceiver.class);
      pm.setComponentEnabledSetting(connectivityReceiver,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 
        PackageManager.DONT_KILL_APP);
      
      // Add this checkin to the queue.
      addToQueue(timeStamp, reference, id);
    }
    else { 
      // Execute the checkin. If it fails, add it to the retry queue.
      if (reference != null) {
        if (!checkin(timeStamp, reference, id))
          addToQueue(timeStamp, reference, id);
      }
    
      // Retry the queued checkins.
      ArrayList<String> successfulCheckins = new ArrayList<String>();
      Cursor queuedCheckins = contentResolver.query(QueuedCheckinsContentProvider.CONTENT_URI, null, null, null, null);
      try {
        // Retry each checkin.
        while (queuedCheckins.moveToNext()) {
          long queuedTimeStamp =  queuedCheckins.getLong(queuedCheckins.getColumnIndex(QueuedCheckinsContentProvider.KEY_TIME_STAMP));
          String queuedReference =  queuedCheckins.getString(queuedCheckins.getColumnIndex(QueuedCheckinsContentProvider.KEY_REFERENCE));
          String queuedId =  queuedCheckins.getString(queuedCheckins.getColumnIndex(QueuedCheckinsContentProvider.KEY_ID));
          if (queuedReference == null || checkin(queuedTimeStamp, queuedReference, queuedId))
            successfulCheckins.add(queuedReference);
        }
       
        // Delete the queued checkins that were successful.
        if (successfulCheckins.size() > 0) {
          StringBuilder sb = new StringBuilder("("+QueuedCheckinsContentProvider.KEY_REFERENCE + "='" + successfulCheckins.get(0) + "'");
          for (int i = 1; i < successfulCheckins.size(); i++)
            sb.append(" OR " + QueuedCheckinsContentProvider.KEY_REFERENCE + " = '" + successfulCheckins.get(i) + "'");
          sb.append(")");
          int deleteCount = contentResolver.delete(QueuedCheckinsContentProvider.CONTENT_URI, sb.toString(), null);
          Log.d(TAG, "Deleted: " + deleteCount);
        }
        
        // If there are still queued checkins then set a non-waking alarm to retry them.
        queuedCheckins.requery();
        if (queuedCheckins.getCount() > 0) {
          long triggerAtTime = System.currentTimeMillis() + PlacesConstants.CHECKIN_RETRY_INTERVAL;
          alarmManager.set(AlarmManager.ELAPSED_REALTIME, triggerAtTime, retryQueuedCheckinsPendingIntent);
        }
        else
          alarmManager.cancel(retryQueuedCheckinsPendingIntent);
      }
      finally {
        queuedCheckins.close(); 
      }
    }
  }
   
  /**
   * Performs a checkin for the specified venue at the specified time.
   * TODO Add additional checkin / ratings / review details as appropriate for your service.
   * @param timeStamp Checkin timestamp
   * @param reference Checkin venue reference
   * @param id Checkin venue unique identifier
   * @return Successfully checked in
   */
  protected boolean checkin(long timeStamp, String reference, String id) {
    if (reference != null) {
      try {
        // Construct the URI required to perform a checkin.
        // TODO Replace this with the checkin URI for your own web service.
        URI uri = new URI(PlacesConstants.PLACES_CHECKIN_URI + PlacesConstants.PLACES_API_KEY);
        
        // Construct the payload
        // TODO Replace with your own payload
        StringBuilder postData = new StringBuilder("<CheckInRequest>\n");
        postData.append("  <reference>");
        postData.append(URLEncoder.encode(reference, "UTF-8"));
        postData.append("</reference>\n");
        postData.append("</CheckInRequest>");
        
        // Construct and execute the HTTP Post 
        HttpClient httpClient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(uri);
        httppost.setEntity(new StringEntity(postData.toString()));
        HttpResponse response = httpClient.execute(httppost);
    
        // If the post was successful, check if this is the newest checkin, and if so save it and broadcast
        // an Intent to notify the application.
        if (response.getStatusLine().getReasonPhrase().equals(PlacesConstants.PLACES_CHECKIN_OK_STATUS)) {
          long lastCheckin = sharedPreferences.getLong(PlacesConstants.SP_KEY_LAST_CHECKIN_TIMESTAMP, 0);
          if (timeStamp > lastCheckin) {   
            sharedPreferencesEditor.putLong(PlacesConstants.SP_KEY_LAST_CHECKIN_TIMESTAMP, timeStamp);
            sharedPreferencesEditor.putString(PlacesConstants.SP_KEY_LAST_CHECKIN_ID, id);
            sharedPreferenceSaver.savePreferences(sharedPreferencesEditor, false);
            Intent intent = new Intent(PlacesConstants.NEW_CHECKIN_ACTION);
            intent.putExtra(PlacesConstants.EXTRA_KEY_ID, id);
            sendBroadcast(intent);
          }
          return true;
        }
        // If the checkin fails return false.
        else
          return false;
      } catch (ClientProtocolException e) {
        Log.e(TAG, e.getMessage());
      } catch (IOException e) {
        Log.e(TAG, e.getMessage());
      } catch (URISyntaxException e) {
        Log.e(TAG, e.getMessage());
      }
    }
    return false;
  }
  
  /**
   * Adds a checkin to the {@link QueuedCheckinsContentProvider} to be retried.
   * @param timeStamp Checkin timestamp
   * @param reference Checkin venue reference
   * @param id Checkin venue unique identifier
   * @return Successfully added to the queue
   */
  protected boolean addToQueue(long timeStamp, String reference, String id) {
    // Construct the new / updated row.
    ContentValues values = new ContentValues();
    values.put(QueuedCheckinsContentProvider.KEY_REFERENCE, reference); 
    values.put(QueuedCheckinsContentProvider.KEY_ID, id);
    values.put(QueuedCheckinsContentProvider.KEY_TIME_STAMP, timeStamp);
    
    String where = QueuedCheckinsContentProvider.KEY_REFERENCE + " = '" + reference + "'";

    // Update the existing checkin for this venue or add the venue to the queue.
    try {
      if (contentResolver.update(QueuedCheckinsContentProvider.CONTENT_URI, values, where, null) == 0) {
        if (contentResolver.insert(QueuedCheckinsContentProvider.CONTENT_URI, values) != null) 
          return true;
        return true;
      } 
    }
    catch (Exception ex) { 
      Log.e(TAG, "Queuing checkin for " + reference + " failed.");
    }
    
    return false;
  }
}