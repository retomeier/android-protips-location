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

import android.app.Activity;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.radioactiveyak.location_best_practices.PlacesConstants;
import com.radioactiveyak.location_best_practices.R;
import com.radioactiveyak.location_best_practices.content_providers.PlaceDetailsContentProvider;

// TODO Update this UI with details related to your checkin. That might include point / rewards
// TODO Or incentives to review or rate the establishment.

/**
 * UI Fragment to display which venue we are currently checked in to.
 */
public class CheckinFragment extends Fragment implements LoaderCallbacks<Cursor> {
  
  /**
   * Factory that return a new instance of the {@link CheckinFragment} 
   * populated with the details corresponding to the passed in venue
   * identifier.
   * @param id Identifier of the venue checked in to
   * @return A new CheckinFragment
   */
  public static CheckinFragment newInstance(String id) {
    CheckinFragment f = new CheckinFragment();

    // Supply id input as an argument.
    Bundle args = new Bundle();
    args.putString(PlacesConstants.ARGUMENTS_KEY_ID, id);
    f.setArguments(args);
    
    return f;
  }
  
  protected String placeId = null;
  protected Handler handler = new Handler();
  protected Activity activity;
  protected TextView checkPlaceNameTextView;
  
  public CheckinFragment() {
    super();
  }
  
  /**
   * Change the venue checked in to.
   * @param id Identifier of the venue checked in to
   */
  public void setPlaceId(String id) {
    // Update the place ID and restart the loader to update the UI.
    placeId = id; 
    if (placeId != null)
      getLoaderManager().restartLoader(0, null, this);
  }
  
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState); 
    activity = getActivity();
  
    // Populate the UI by initiating the loader to retrieve the 
    // details of the venue from the underlying Place Content Provider.
    if (placeId != null)
      getLoaderManager().initLoader(0, null, this);
  }
  
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {    
    View view = inflater.inflate(R.layout.checkin_box, container, false);
    checkPlaceNameTextView = (TextView)view.findViewById(R.id.checkin_place_name);
    
    if (getArguments() != null)
      placeId = getArguments().getString(PlacesConstants.ARGUMENTS_KEY_ID);
    
    return view;
  }

  @Override
  public void onResume() {
    super.onResume();
  }
  
  /**
   * {@inheritDoc}
   * This loader queries the {@link PlaceContentProvider} to extract the name of
   * the venue that has been checked in to.
   */
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    String[] projection = new String[] {PlaceDetailsContentProvider.KEY_NAME};
    
    String selection = PlaceDetailsContentProvider.KEY_ID + "='" + placeId + "'";
    
    return new CursorLoader(activity, PlaceDetailsContentProvider.CONTENT_URI, 
        projection, selection, null, null);
  }
  
  /**
   * {@inheritDoc}
   * When this load has finished, update the UI with the name of the venue.
   */
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    if (data.moveToFirst()) {
      final String venueName = data.getString(data.getColumnIndex(PlaceDetailsContentProvider.KEY_NAME));
      handler.post(new Runnable () {
        public void run() {
            checkPlaceNameTextView.setText(venueName);
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
        checkPlaceNameTextView.setText("");
      }
    });
  }
}