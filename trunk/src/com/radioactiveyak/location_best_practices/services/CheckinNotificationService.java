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

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

import com.radioactiveyak.location_best_practices.PlacesConstants;
import com.radioactiveyak.location_best_practices.R;
import com.radioactiveyak.location_best_practices.UI.PlaceActivity;
import com.radioactiveyak.location_best_practices.content_providers.PlaceDetailsContentProvider;

/**
 * Service that handles background checkin notifications.
 * This Service will be started by the {@link NewCheckinReceiver}
 * when the Application isn't visible and trigger a Notification
 * telling the user that they have been checked in to a venue.
 * This typically happens if an earlier checkin has failed 
 * (due to lack of connectivity, server error, etc.).
 * 
 * If your app lets users post reviews / ratings / etc. This 
 * Service can be used to notify them once they have been successfully
 * posted.
 * 
 * TODO Update the Notification to display a richer payload.
 * TODO Create a variation of this Notification for Honeycomb+ devices. 
 */
public class CheckinNotificationService extends IntentService {
  
  protected static String TAG = "CheckinNotificationService";
  
  protected ContentResolver contentResolver;
  protected NotificationManager notificationManager;
  protected String[] projection;
  
  public CheckinNotificationService() {
    super(TAG);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    contentResolver = getContentResolver();
    notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    
    projection = new String[] {PlaceDetailsContentProvider.KEY_ID, PlaceDetailsContentProvider.KEY_NAME};
  }

  /**
   * {@inheritDoc}
   * Extract the name of the venue based on the ID specified in the broadcast Checkin Intent
   * and use it to display a Notification. 
   */
  @Override
  protected void onHandleIntent(Intent intent) {
    String id = intent.getStringExtra(PlacesConstants.EXTRA_KEY_ID);
    
    // Query the PlaceDetailsContentProvider for the specified venue.
    Uri uri = Uri.withAppendedPath(PlaceDetailsContentProvider.CONTENT_URI, id);
    Cursor cursor = contentResolver.query(uri, projection, null, null, null);
    
    if (cursor.moveToFirst()) {
      // Construct a Pending Intent for the Notification. This will ensure that when
      // the notification is clicked, the application will open and the venue we've
      // checked in to will be displayed.
      Intent contentIntent = new Intent(this, PlaceActivity.class);
      contentIntent.putExtra(PlacesConstants.EXTRA_KEY_ID, id);
      PendingIntent contentPendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);
      
      // Construct the notification.
      String checkinText = getResources().getText(R.string.checkin_text).toString();
      String placeName = cursor.getString(cursor.getColumnIndex(PlaceDetailsContentProvider.KEY_NAME));
      String tickerText = checkinText + placeName;
      Notification notification = new Notification(R.drawable.icon, tickerText, System.currentTimeMillis());
      notification.setLatestEventInfo(this, checkinText, placeName, contentPendingIntent);
      
      // Trigger the notification.
      notificationManager.notify(PlacesConstants.CHECKIN_NOTIFICATION, notification);
    }
  }
}