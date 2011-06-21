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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.net.ssl.HttpsURLConnection;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.util.Log;

import com.radioactiveyak.location_best_practices.PlacesConstants;
import com.radioactiveyak.location_best_practices.content_providers.PlaceDetailsContentProvider;

/**
 * Service that queries the underlying web service to retrieve the full
 * details for the specified place / venue.
 * This Service is called by the {@link PlacesUpdateService} to prefetch details
 * for the nearby venues, or by the {@link PlacesActivity} and {@link PlaceDetailsFragment}
 * to retrieve the details for the selected place.
 * TODO Replace the URL and XML parsing to match the details available from your service.
 */
public class PlaceDetailsUpdateService extends IntentService {
  
  protected static String TAG = "PlaceDetailsIntentService";
  
  protected ContentResolver contentResolver;
  protected String[] projection;
  protected SharedPreferences prefs;
  protected ConnectivityManager cm;
  
  public PlaceDetailsUpdateService() {
    super(TAG);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    contentResolver = getContentResolver();
    projection = new String[] {PlaceDetailsContentProvider.KEY_LAST_UPDATE_TIME, PlaceDetailsContentProvider.KEY_FORCE_CACHE};
    cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
    prefs = getSharedPreferences(PlacesConstants.SHARED_PREFERENCE_FILE, Context.MODE_PRIVATE);
  }

  /**
   * {@inheritDoc}
   * Check to see if we already have these details, and if so, whether or not we should update them.
   * Initiates an update where appropriate.
   */
  @Override
  protected void onHandleIntent(Intent intent) {
	// Check if we're running in the foreground, if not, check if
	// we have permission to do background updates.
	boolean backgroundAllowed = cm.getBackgroundDataSetting();
	boolean inBackground = prefs.getBoolean(PlacesConstants.EXTRA_KEY_IN_BACKGROUND, true);
	
	if (!backgroundAllowed && inBackground) return;

    // Extract the identifiers for the place to refresh the detail for.
    String reference = intent.getStringExtra(PlacesConstants.EXTRA_KEY_REFERENCE);
    String id = intent.getStringExtra(PlacesConstants.EXTRA_KEY_ID);
    
    // If this is a forced refresh (typically because the details UI is being displayed
    // then do an update and force this into the cache.
    boolean forceCache = intent.getBooleanExtra(PlacesConstants.EXTRA_KEY_FORCEREFRESH, false);
    boolean doUpdate = id == null || forceCache;
    
    // Check to see if the latency since the last update is sufficient to perform a refresh.
    if (!doUpdate) {
      Uri uri = Uri.withAppendedPath(PlaceDetailsContentProvider.CONTENT_URI, id);
      Cursor cursor = contentResolver.query(uri, projection, null, null, null);
      
      try { 
        doUpdate = true;
        if (cursor.moveToFirst()) {
          if (cursor.getLong(cursor.getColumnIndex(PlaceDetailsContentProvider.KEY_LAST_UPDATE_TIME)) > System.currentTimeMillis()-PlacesConstants.MAX_DETAILS_UPDATE_LATENCY)
            doUpdate = false;
        }
      }
      finally {
        cursor.close();
      }
    }
    
    // Hit the server for an update / refresh.
    if (doUpdate) {
      refreshPlaceDetails(reference, forceCache);
    }
  }
  
  /**
   * Request details for this place from the underlying web Service.
   * TODO Replace the URL and XML parsing with whatever is necessary for your service.
   * @param reference Reference
   * @param forceCache Force Cache
   */
  protected void refreshPlaceDetails(String reference, boolean forceCache) {    
	  URL url;
	  try {
	    // TODO Replace with your web service URL schema.
	    String placesFeed = PlacesConstants.PLACES_DETAIL_BASE_URI + reference + PlacesConstants.PLACES_API_KEY;
	    
	    // Make the web query.
	    url = new URL(placesFeed);     
	    URLConnection connection = url.openConnection();
	    HttpsURLConnection httpConnection = (HttpsURLConnection)connection; 
	    int responseCode = httpConnection.getResponseCode(); 

	    if (responseCode == HttpURLConnection.HTTP_OK) { 
	      InputStream in = httpConnection.getInputStream(); 
	          
	      // Extract the details from the returned feed.
	      XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();

        xpp.setInput(in, null);
        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
         if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("result")) {
           eventType = xpp.next();
           String id = "";
           String name = "";
           String vicinity = "";
           String types = "";
           String locationLat = "";
           String locationLng = "";
           String viewport = "";
           String icon = "";
           String phone = "";
           String address = "";
           float rating = 0;
           String placeurl = "";
           
           while (!(eventType == XmlPullParser.END_TAG && xpp.getName().equals("result"))) {
             if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("name"))
               name = xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("vicinity"))
               vicinity = xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("type"))
               types = types == "" ? xpp.nextText() : types + " " + xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("lat"))
               locationLat = xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("lng"))
               locationLng = xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("icon"))
               icon = xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("id"))
               id = xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("formatted_phone_number"))
               phone = xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("formatted_address"))
               address = xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("url"))
               placeurl = xpp.nextText();
             else if (eventType == XmlPullParser.START_TAG && xpp.getName().equals("rating"))
               rating = Float.parseFloat(xpp.nextText());
           
             eventType = xpp.next();
           }
           Location placeLocation = new Location("XML");
           try {
             placeLocation.setLatitude(Double.valueOf(locationLat));
             placeLocation.setLongitude(Double.valueOf(locationLng));
           }
           catch (NumberFormatException e) {
             Log.d(TAG, e.getMessage());
           }
           // Add the new place to the Content Provider
           addPlaceDetail(id, name, vicinity, types, placeLocation, viewport, icon, reference, phone, address, rating, placeurl, forceCache);
         }
         eventType = xpp.next();
        }
	    }
	    else
        Log.e(TAG, responseCode + ": " + httpConnection.getResponseMessage());
	    
	  } catch (MalformedURLException e) {
	    Log.e(TAG, e.getMessage());
	  } catch (IOException e) {
	    Log.e(TAG, e.getMessage());
	  } catch (XmlPullParserException e) { 
      Log.e(TAG, e.getMessage());
	  }
	  finally {
	  }
	}
  
  /**
   * Add details for a place / venue to the {@link PlaceDetailsContentProvider}.
   * TODO Update this with the details corresponding to what's available from your server.
   * @param id Unique identifier
   * @param name Name
   * @param vicinity Vicinity
   * @param types Types
   * @param location Location
   * @param viewport Viewport
   * @param icon Icon
   * @param reference Reference
   * @param phone Phone
   * @param address Address
   * @param rating Rating
   * @param url Url
   * @param forceCache Save to the persistent cache (do not delete)
   * @return Place detail has been updates or added
   */
  protected boolean addPlaceDetail(String id, String name, String vicinity, String types, Location location, String viewport, String icon, String reference, String phone, String address, float rating, String url, boolean forceCache) {    
    
    // Construct the new row.
    ContentValues values = new ContentValues();
    values.put(PlaceDetailsContentProvider.KEY_ID, id);  
    values.put(PlaceDetailsContentProvider.KEY_NAME, name);
    double lat = location.getLatitude();
    double lng = location.getLongitude();
    values.put(PlaceDetailsContentProvider.KEY_LOCATION_LAT, lat);
    values.put(PlaceDetailsContentProvider.KEY_LOCATION_LNG, lng);
    values.put(PlaceDetailsContentProvider.KEY_VICINITY, vicinity);
    values.put(PlaceDetailsContentProvider.KEY_TYPES, types);
    values.put(PlaceDetailsContentProvider.KEY_VIEWPORT, viewport);
    values.put(PlaceDetailsContentProvider.KEY_ICON, icon);
    values.put(PlaceDetailsContentProvider.KEY_REFERENCE, reference);
    values.put(PlaceDetailsContentProvider.KEY_PHONE, phone);
    values.put(PlaceDetailsContentProvider.KEY_ADDRESS, address);
    values.put(PlaceDetailsContentProvider.KEY_RATING, rating);
    values.put(PlaceDetailsContentProvider.KEY_URL, url);
    values.put(PlaceDetailsContentProvider.KEY_LAST_UPDATE_TIME, System.currentTimeMillis());
    values.put(PlaceDetailsContentProvider.KEY_FORCE_CACHE, forceCache);
    
    // Update the existing listing, or add a new listing.
    String where = PlaceDetailsContentProvider.KEY_ID + " = '" + id + "'";
    try {
      if (contentResolver.update(PlaceDetailsContentProvider.CONTENT_URI, values, where, null) == 0) {
        if (contentResolver.insert(PlaceDetailsContentProvider.CONTENT_URI, values) != null) 
          return true;
        return true;
      } 
    }
    catch (Exception ex) { 
      Log.e(TAG, "Adding Detail for " + name + " failed.");
    }
    
    return false;
  }
}