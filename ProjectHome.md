A template app that uses the Google Places API to demonstrate the core functionality of apps that use your current location to provide a list of nearby points of interest, allow you to drill down into the details, and then checkin / rate/ review.  It incorporates the tips, tricks, best practices, and cheats I know for creating high quality apps.

Particular attention has been paid to reducing the time between opening an app and seeing an up-to-date list of nearby venues and providing a reasonable level of offline support.

The code implements all of the location best-practices for reducing latency and battery consumption as detailed in my Google I/O 2011 session, [Android Protips: Advanced Topics for Expert Android Developers](http://www.google.com/events/io/2011/sessions/android-protips-advanced-topics-for-expert-android-app-developers.html).

You can learn more about the decisions involved in this [deep dive into location-based services](http://android-developers.blogspot.com/2011/06/deep-dive-into-location.html) post on the Android Developer Blog.

**Quick Start Guide**
(see the `Readme.txt` for more details)
  * Make sure you've downloaded and installed the [Android Compatibility Library](http://developer.android.com/sdk/compatibility-library.html).
  * Obtain a [Google Places API key](http://code.google.com/apis/maps/documentation/places/#Limits) and assign it to the `MY_API_KEY` static constant in `PlacesConstants.java`.
  * Obtain a [Backup Manager API key](http://code.google.com/android/backup/signup.html) and assign it to the `backup_manager_key` value in `res/values/strings.xml`.

**Implemented Requirements**
  * Display list of nearby locations.
  * Allow drill-down to display place details for any selected place.
  * Support some form of user feedback for a location (Checkins).
  * Offline support for all features (place list, details, and checkins).
  * Must support Android 1.6+.
  * Must be optimized for tablets and use the best APIs available for any given platform version.

**Protips Covered in this Project**
  * Using and supporting the latest APIs and hardware.
  * Using Interfaces for backwards compatibility.
  * Using Intents to receive location updates.
  * Monitoring inactive Location Providers for a better option
  * Monitoring the Location Providers in use in case they’re disabled
  * Using the Passive Location Provider
  * Using Intents to passively receive location updates when your app isn't active
  * Monitoring device state to vary refresh rate
  * Toggling your manifest Receivers at runtime
  * Monitoring state change broadcasts
  * Backing up Shared Preferences to the Cloud using the Backup Manager
  * Making everything asynchronous. No exceptions.
  * Using Intent Services
  * Using the Loader and Cursor Loader
  * Using Strict Mode

Using the code? [Let me know](http://www.twitter.com/retomeier)!