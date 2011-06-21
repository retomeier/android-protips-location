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
 
** QUICK START GUIDE **

1) Make sure you've downloaded and installed the Android Compatibility Library:
     http://developer.android.com/sdk/compatibility-library.html 
2) Obtain a Google Places API key from:
     http://code.google.com/apis/maps/documentation/places/#Limits
   And assign it to the MY_API_KEY static constant in PlacesConstants.java
3) Obtain a Backup Manager API key from:
     http://code.google.com/android/backup/signup.html
   And assign it to the backup_manager_key value in res/values/strings.xml

** About this Project **
 
Project Home Page:
http://code.google.com/p/android-protips-location/

Maintained by:
Reto Meier
  http://www.twitter.com/retomeier 
  http://blog.radioactiveyak.com
 
Uses the Google Places API to mimic the core functionality of apps that use
your current location to provide a list of nearby points of interest, allow you
to drill down into the details, and then checkin / rate/ review.
 
It is an open-source reference implementation of a location-based app that 
incorporates several tips, tricks, best practices, and cheats for creating 
high quality apps.

Particular attention has been paid to reducing the time between opening an app
and seeing an up-to-date list of nearby venues and providing a reasonable level
of offline support.

The code implements all of the location best-practices for reducing latency and
battery consumption as detailed in my Google I/O 2011 session, 
Android Protips: Advanced Topics for Expert Android Developers:
http://www.google.com/events/io/2011/sessions/android-protips-advanced-topics-for-expert-android-app-developers.html

