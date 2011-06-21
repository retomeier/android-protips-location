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

package com.radioactiveyak.location_best_practices.content_providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * Content Provider and database for storing checkins which 
 * have not yet successfully been reported to the server.
 * Checkins will be added be added and removed by the checkin Service.
 */
public class QueuedCheckinsContentProvider extends ContentProvider {
  /** The underlying database */
  private SQLiteDatabase checkinsDB;

  private static final String TAG = "QueuedCheckinsContentProvider";
  private static final String DATABASE_NAME = "checkins.db";
  private static final int DATABASE_VERSION = 3;
  private static final String CHECKINS_TABLE = "checkins";

  // TODO Update the columns and SQL Create statement with the data you 
  // TODO will be sending to your online checkin service.
  // Column Names
  public static final String KEY_REFERENCE = "_id";
  public static final String KEY_ID = "id";
  public static final String KEY_TIME_STAMP = "timestamp";

  public static final Uri CONTENT_URI = Uri.parse("content://com.radioactiveyak.provider.checkins/checkins");
  
  //Create the constants used to differentiate between the different URI requests.
  private static final int CHECKINS = 1;
  private static final int CHECKIN_ID = 2;

  //Allocate the UriMatcher object, where a URI ending in 'checkins' will
  //correspond to a request for all earthquakes, and 'checkins' with a trailing '/[Unique ID]' will represent a single earthquake row.
  private static final UriMatcher uriMatcher;
  static {
   uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
   uriMatcher.addURI("com.radioactiveyak.provider.checkins", "checkins", CHECKINS);
   uriMatcher.addURI("com.radioactiveyak.provider.checkins", "checkins/*", CHECKIN_ID);
  }
  
  @Override
  public boolean onCreate() {
    Context context = getContext();
  
    CheckinsDatabaseHelper dbHelper = new CheckinsDatabaseHelper(context, DATABASE_NAME, null, DATABASE_VERSION);
    try {
      checkinsDB = dbHelper.getWritableDatabase();
    } catch (SQLiteException e) {
      checkinsDB = null;
      Log.d(TAG, "Database Opening exception");
    }
    
    return (checkinsDB == null) ? false : true;
  }

  @Override
  public String getType(Uri uri) {
    switch (uriMatcher.match(uri)) {
      case CHECKINS: return "vnd.android.cursor.dir/vnd.radioativeyak.checkin";
      case CHECKIN_ID: return "vnd.android.cursor.item/vnd.radioactiveyak.checkin";
      default: throw new IllegalArgumentException("Unsupported URI: " + uri);
    }
  }

  
  @Override
  public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sort) {
    SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
    qb.setTables(CHECKINS_TABLE);

    // If this is a row query, limit the result set to the passed in row. 
    switch (uriMatcher.match(uri)) {
      case CHECKIN_ID: qb.appendWhere(KEY_REFERENCE + "=" + uri.getPathSegments().get(1));
                     break;
      default      : break;
    }

    // If no sort order is specified sort by date / time
    String orderBy;
    if (TextUtils.isEmpty(sort)) {
      orderBy = KEY_TIME_STAMP + " ASC";
    } else {
      orderBy = sort;
    }

    // Apply the query to the underlying database.
    Cursor c = qb.query(checkinsDB, 
                        projection, 
                        selection, selectionArgs, 
                        null, null, orderBy);

    // Register the contexts ContentResolver to be notified if
    // the cursor result set changes. 
    c.setNotificationUri(getContext().getContentResolver(), uri);
    
    // Return a cursor to the query result.
    return c;
  }

  @Override
  public Uri insert(Uri _uri, ContentValues _initialValues) {
    // Insert the new row, will return the row number if successful.
    long rowID = checkinsDB.insert(CHECKINS_TABLE, "checkin", _initialValues);
          
    // Return a URI to the newly inserted row on success.
    if (rowID > 0) {
      Uri uri = ContentUris.withAppendedId(CONTENT_URI, rowID);
      getContext().getContentResolver().notifyChange(uri, null);
      return uri;
    }
    throw new SQLException("Failed to insert row into " + _uri);
  }

  @Override
  public int delete(Uri uri, String where, String[] whereArgs) {
    int count;
    
    switch (uriMatcher.match(uri)) {
      case CHECKINS:
        count = checkinsDB.delete(CHECKINS_TABLE, where, whereArgs);
        break;

      case CHECKIN_ID:
        String segment = uri.getPathSegments().get(1);
        count = checkinsDB.delete(CHECKINS_TABLE, KEY_REFERENCE + "="
                                    + segment
                                    + (!TextUtils.isEmpty(where) ? " AND (" 
                                    + where + ')' : ""), whereArgs);
        break;

      default: throw new IllegalArgumentException("Unsupported URI: " + uri);
    }

    getContext().getContentResolver().notifyChange(uri, null);
    return count;
  }

  @Override
  public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
    int count;
    switch (uriMatcher.match(uri)) {
      case CHECKINS: count = checkinsDB.update(CHECKINS_TABLE, values, where, whereArgs);
                   break;

      case CHECKIN_ID: String segment = uri.getPathSegments().get(1);
                     count = checkinsDB.update(CHECKINS_TABLE, values, KEY_REFERENCE 
                             + "=" + segment 
                             + (!TextUtils.isEmpty(where) ? " AND (" 
                             + where + ')' : ""), whereArgs);
                     break;

      default: throw new IllegalArgumentException("Unknown URI " + uri);
    }

    getContext().getContentResolver().notifyChange(uri, null);
    return count;
  }
  
  // Helper class for opening, creating, and managing database version control
  private static class CheckinsDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_CREATE =
      "create table " + CHECKINS_TABLE + " (" 
      + KEY_REFERENCE + " TEXT primary key, "
      + KEY_ID + " TEXT, "
      + KEY_TIME_STAMP + " LONG); ";
        
    public CheckinsDatabaseHelper(Context context, String name, CursorFactory factory, int version) {
      super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL(DATABASE_CREATE);           
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                  + newVersion + ", which will destroy all old data");
              
      db.execSQL("DROP TABLE IF EXISTS " + CHECKINS_TABLE);
      onCreate(db);
    }
  }
}