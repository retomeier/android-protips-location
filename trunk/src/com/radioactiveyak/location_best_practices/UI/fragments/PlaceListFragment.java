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

import com.radioactiveyak.location_best_practices.PlacesConstants;
import com.radioactiveyak.location_best_practices.UI.PlaceActivity;
import com.radioactiveyak.location_best_practices.content_providers.PlacesContentProvider;
import com.radioactiveyak.location_best_practices.services.PlaceDetailsUpdateService;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.ListView;

// TODO Update this UI to show a better list of available venues. This could include
// TODO pictures, direction, more detailed text, etc. You will likely want to define
// TODO your own List Item Layout. 

/**
 * UI Fragment to show a list of venues near to the users current location.
 */
public class PlaceListFragment extends ListFragment implements LoaderCallbacks<Cursor> {
  
  protected Cursor cursor = null;
  protected SimpleCursorAdapter adapter;
  protected PlaceActivity activity;
  
  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
        
    activity = (PlaceActivity)getActivity();
    
    // Create a new SimpleCursorAdapter that displays the name of each nearby
    // venue and the current distance to it.
    adapter = new SimpleCursorAdapter(
            activity,
            android.R.layout.two_line_list_item,
            cursor,                                              
            new String[] {PlacesContentProvider.KEY_NAME, PlacesContentProvider.KEY_DISTANCE},           
            new int[] {android.R.id.text1, android.R.id.text2},
            0);
    // Allocate the adapter to the List displayed within this fragment.
    setListAdapter(adapter);
    
    // Populate the adapter / list using a Cursor Loader. 
    getLoaderManager().initLoader(0, null, this);
  }
  
  /**
   * {@inheritDoc}
   * When a venue is clicked, fetch the details from your server and display the detail page.
   */
  @Override
  public void onListItemClick(ListView l, View v, int position, long theid) {
    super.onListItemClick(l, v, position, theid);
    
    // Find the ID and Reference of the selected venue.
    // These are needed to perform a lookup in our cache and the Google Places API server respectively.
    Cursor c = adapter.getCursor();
    c.moveToPosition(position);
    String reference = c.getString(c.getColumnIndex(PlacesContentProvider.KEY_REFERENCE));
    String id = c.getString(c.getColumnIndex(PlacesContentProvider.KEY_ID));
    
    // Initiate a lookup of the venue details usign the PlacesDetailsUpdateService.
    // Because this is a user initiated action (rather than a prefetch) we request
    // that the Service force a refresh.
    Intent serviceIntent = new Intent(activity, PlaceDetailsUpdateService.class);
    serviceIntent.putExtra(PlacesConstants.EXTRA_KEY_REFERENCE, reference);
    serviceIntent.putExtra(PlacesConstants.EXTRA_KEY_ID, id);
    serviceIntent.putExtra(PlacesConstants.EXTRA_KEY_FORCEREFRESH, true);        
    activity.startService(serviceIntent);
    
    // Request the parent Activity display the venue detail UI.
    activity.selectDetail(reference, id);
  }
     
  /**
   * {@inheritDoc}
   * This loader will return the ID, Reference, Name, and Distance of all the venues
   * currently stored in the {@link PlacesContentProvider}.
   */
  public Loader<Cursor> onCreateLoader(int id, Bundle args) {
    String[] projection = new String[] {PlacesContentProvider.KEY_ID,PlacesContentProvider.KEY_NAME, PlacesContentProvider.KEY_DISTANCE, PlacesContentProvider.KEY_REFERENCE};
    
    return new CursorLoader(activity, PlacesContentProvider.CONTENT_URI, 
        projection, null, null, null);
  }

  /**
   * {@inheritDoc}
   * When the loading has completed, assign the cursor to the adapter / list.
   */
  public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
    adapter.swapCursor(data);
  }

  /**
   * {@inheritDoc}
   */
  public void onLoaderReset(Loader<Cursor> loader) {
    adapter.swapCursor(null);
  }
}