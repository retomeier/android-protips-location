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

package com.radioactiveyak.location_best_practices.UI.fragments;

// TODO Create a richer UI to display places Details. This should include images,
// TODO ratings, reviews, other people checked in here, etc.

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.radioactiveyak.location_best_practices.PlacesConstants;
import com.radioactiveyak.location_best_practices.R;
import com.radioactiveyak.location_best_practices.content_providers.PlaceDetailsContentProvider;
import com.radioactiveyak.location_best_practices.services.PlaceCheckinService;
import com.radioactiveyak.location_best_practices.services.PlaceDetailsUpdateService;

/**
 * UI Fragment to display the details for a selected venue.
 */
public class PlaceDetailFragment extends Fragment implements LoaderCallbacks<Cursor> {
  
  /**
   * Factory that produces a new {@link PlaceDetailFragment} populated with
   * details corresponding to the reference / ID of the venue passed in.
   * @param reference Venue Reference
   * @param id Venue Unique ID
   * @return {@link PlaceDetailFragment}
   */
  public static PlaceDetailFragment newInstance(String reference, String id) {
    PlaceDetailFragment f = new PlaceDetailFragment();

    // Supply reference and ID inputs as arguments.
    Bundle args = new Bundle();
    args.putString(PlacesConstants.ARGUMENTS_KEY_REFERENCE, reference);
    args.putString(PlacesConstants.ARGUMENTS_KEY_ID, id);
    f.setArguments(args);
    
    return f;
  }
  
  protected static String TAG = "PlaceDetailFragment";
  
  protected String placeReference = null;
  protected String placeId = null;
  
  protected Handler handler = new Handler();
  protected Activity activity;
  protected TextView nameTextView;
  protected TextView phoneTextView;
  protected TextView addressTextView;
  protected TextView ratingTextView;
  protected TextView urlTextView;
  protected Button checkinButton;
  protected TextView checkedInText;
  
  public PlaceDetailFragment() {
    super();
  }
  
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState); 
    activity = getActivity();
        
    // Query the PlacesDetails Content Provider using a Loader to find
    // the details for the selected venue.
    if (placeId != null)
      getLoaderManager().initLoader(0, null, this);
    
    // Query the Shared Preferences to find the ID of the last venue checked in to.
    SharedPreferences sp = activity.getSharedPreferences(PlacesConstants.SHARED_PREFERENCE_FILE, Context.MODE_PRIVATE);
    String lastCheckin = sp.getString(PlacesConstants.SP_KEY_LAST_CHECKIN_ID, null);
    if (lastCheckin != null )
      checkedIn(lastCheckin);
  }
  
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    
    View view = inflater.inflate(R.layout.place_detail, container, false);
    nameTextView = (TextView)view.findViewById(R.id.detail_name);
    phoneTextView = (TextView)view.findViewById(R.id.detail_phone);
    addressTextView = (TextView)view.findViewById(R.id.detail_address);
    ratingTextView = (TextView)view.findViewById(R.id.detail_rating);
    urlTextView = (TextView)view.findViewById(R.id.detail_url);
    checkinButton = (Button)view.findViewById(R.id.checkin_button);
    checkedInText = (TextView)view.findViewById(R.id.detail_checkin_text);
    
    checkinButton.setOnClickListener(checkinButtonOnClickListener);
    
    if (getArguments() != null) {
      placeReference = getArguments().getString(PlacesConstants.ARGUMENTS_KEY_REFERENCE);
      placeId = getArguments().getString(PlacesConstants.ARGUMENTS_KEY_ID);
    }
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
    
    // Always refresh the details on resume, but don't force
    // a refresh to minimize the network usage. Forced updates
    // are unnecessary as we force an update when a venue
    // is selected in the Place List Activity.
    if (placeReference != null && placeId != null)
      updatePlace(placeReference, placeId, false);
  }
  
  /**
   * Start the {@link PlaceDetailsUpdateService} to refresh the details for the 
   * selected venue.
   * @param reference Reference
   * @param id Unique Identifier
   * @param forceUpdate Force an update
   */
  protected void updatePlace(String reference, String id, boolean forceUpdate) {
    if (placeReference != null && placeId != null) {
      // Start the PlaceDetailsUpdate Service to query the server for details
      // on the specified venue. A "forced update" will ignore the caching latency 
      // rules and query the server.
      Intent updateServiceIntent = new Intent(activity, PlaceDetailsUpdateService.class);
      updateServiceIntent.putExtra(PlacesConstants.EXTRA_KEY_REFERENCE, reference);
      updateServiceIntent.putExtra(PlacesConstants.EXTRA_KEY_ID, id);
      updateServiceIntent.putExtra(PlacesConstants.EXTRA_KEY_FORCEREFRESH, forceUpdate);
      activity.startService(updateServiceIntent);
    }
  }
  
  /**
   * {@inheritDoc}
   * Query the {@link PlaceDetailsContentProvider} for the Phone, Address, Rating, Reference, and Url
   * of the selected venue. 
   * TODO Expand the projection to include any other details you are recording in the Place Detail Content Provider.
   */
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    String[] projection = new String[] {PlaceDetailsContentProvider.KEY_NAME, 
      PlaceDetailsContentProvider.KEY_PHONE, 
      PlaceDetailsContentProvider.KEY_ADDRESS, 
      PlaceDetailsContentProvider.KEY_RATING, 
      PlaceDetailsContentProvider.KEY_REFERENCE, 
      PlaceDetailsContentProvider.KEY_URL};
    
    String selection = PlaceDetailsContentProvider.KEY_ID + "='" + placeId + "'";
    
    return new CursorLoader(activity, PlaceDetailsContentProvider.CONTENT_URI, 
        projection, selection, null, null);
  }
  
  /**
   * {@inheritDoc}
   * When the Loader has completed, schedule an update of the Fragment UI on the main application thread.
   */
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    if (data.moveToFirst()) {
      final String name = data.getString(data.getColumnIndex(PlaceDetailsContentProvider.KEY_NAME));
      final String phone = data.getString(data.getColumnIndex(PlaceDetailsContentProvider.KEY_PHONE));
      final String address = data.getString(data.getColumnIndex(PlaceDetailsContentProvider.KEY_ADDRESS));
      final String rating = data.getString(data.getColumnIndex(PlaceDetailsContentProvider.KEY_RATING));
      final String url = data.getString(data.getColumnIndex(PlaceDetailsContentProvider.KEY_URL));

      // If we don't have a place reference passed in, we need to look it up and update our details
      // accordingly.
      if (placeReference == null) {
        placeReference = data.getString(data.getColumnIndex(PlaceDetailsContentProvider.KEY_REFERENCE));
        updatePlace(placeReference, placeId, true);
      }
      
      handler.post(new Runnable () {
        public void run() {
          nameTextView.setText(name);
          phoneTextView.setText(phone);
          addressTextView.setText(address);
          ratingTextView.setText(rating);
          urlTextView.setText(url);
        }        
      });
    }
  }
  
  /**
   * {@inheritDoc}
   */
  public void onLoaderReset(Loader<Cursor> loader) {
    handler.post(new Runnable () {
      public void run() {
        nameTextView.setText("");
        phoneTextView.setText("");
        addressTextView.setText("");
        ratingTextView.setText("");
        urlTextView.setText("");
      }
    });
  }
  
  /**
   * When the Checkin Button is clicked start the {@link PlaceCheckinService} to checkin.
   */
  protected OnClickListener checkinButtonOnClickListener = new OnClickListener() {
    public void onClick(View view) {
      // TODO Pass in additional parameters to your checkin / rating / review service as appropriate
      // TODO In some cases you may prefer to open a new Activity with checkin details before initiating the Service.
      Intent checkinServiceIntent = new Intent(getActivity(), PlaceCheckinService.class);
      checkinServiceIntent.putExtra(PlacesConstants.EXTRA_KEY_REFERENCE, placeReference);
      checkinServiceIntent.putExtra(PlacesConstants.EXTRA_KEY_ID, placeId);
      checkinServiceIntent.putExtra(PlacesConstants.EXTRA_KEY_TIME_STAMP, System.currentTimeMillis());
      getActivity().startService(checkinServiceIntent);
    }
  }; 
  
  /**
   * Checks to see if the currently displayed venue is the last place checked in to.
   * IF it is, it disables the checkin button and update the UI accordingly.
   * @param id Checked-in place ID
   */
  public void checkedIn(String id) {
    if (placeId == null)
      Log.e(TAG, "Place ID = null");
    boolean checkedIn = id != null && placeId != null && placeId.equals(id);
    checkinButton.setEnabled(!checkedIn);
    checkedInText.setVisibility(checkedIn ? View.VISIBLE : View.INVISIBLE);
  }
}