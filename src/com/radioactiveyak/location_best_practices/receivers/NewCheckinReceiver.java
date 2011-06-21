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

import com.radioactiveyak.location_best_practices.PlacesConstants;
import com.radioactiveyak.location_best_practices.services.CheckinNotificationService;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Manifest Receiver that listens for broadcasts announcing a successful checkin.
 * This class starts the CheckinNotification Service that will trigger a notification
 * announcing the successful checkin. We don't want notifications for this app to 
 * be announced while the app is running, so this receiver is disabled whenever the
 * main Activity is visible. 
 */
public class NewCheckinReceiver extends BroadcastReceiver {
  
  protected static String TAG = "NewCheckinReceiver";
  
  /**
   * When a successful checkin is announced, extract the unique ID of the place
   * that's been checked in to, and pass this value to the CheckinNotification Service
   * when you start it.
   */
  @Override
  public void onReceive(Context context, Intent intent) {
    String id = intent.getStringExtra(PlacesConstants.EXTRA_KEY_ID);
    if (id != null) {
      Intent serviceIntent = new Intent(context, CheckinNotificationService.class);
      serviceIntent.putExtra(PlacesConstants.EXTRA_KEY_ID, id);
      context.startService(serviceIntent);
    }
	}
}