package org.myspecialway.android.ar.wikitude;

import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.resources.IALocationListenerSupport;
import com.wikitude.architect.ArchitectView;

import org.myspecialway.android.R;
import org.myspecialway.android.SdkExample;
import org.myspecialway.android.ar.wikitude.utils.LocationProvider;

/**
 * This Activity is (almost) the least amount of code required to use the
 * basic functionality for Geo AR.
 * <p>
 * This Activity needs Manifest.permission.ACCESS_FINE_LOCATION permissions
 * in addition to the required permissions of the SimpleArActivity.
 */
@SdkExample(description = R.string.example_ar_wikitude_description, title = R.string.example_ar_wikitude_title)
public class SimpleGeoArActivity extends SimpleArActivity /*implements LocationListener,*/ {

    private static final String TAG = "SimpleGeoArActivity";
    /**
     * Very basic location provider to enable location updates.
     * Please note that this approach is very minimal and we recommend to implement a more
     * advanced location provider for your app. (see https://developer.android.com/training/location/index.html)
     */
//    private LocationProvider locationProvider;

    /**
     * Error callback of the LocationProvider, noProvidersEnabled is called when neither location over GPS nor
     * location over the network are enabled by the device.
     */
    private final LocationProvider.ErrorCallback errorCallback = new LocationProvider.ErrorCallback() {
        @Override
        public void noProvidersEnabled() {
            Toast.makeText(SimpleGeoArActivity.this, R.string.no_location_provider, Toast.LENGTH_LONG).show();
        }
    };

    /**
     * The ArchitectView.SensorAccuracyChangeListener notifies of changes in the accuracy of the compass.
     * This can be used to notify the user that the sensors need to be recalibrated.
     * <p>
     * This listener has to be registered after onCreate and unregistered before onDestroy in the ArchitectView.
     */
    private final ArchitectView.SensorAccuracyChangeListener sensorAccuracyChangeListener = new ArchitectView.SensorAccuracyChangeListener() {
        @Override
        public void onCompassAccuracyChanged(int accuracy) {
            if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) { // UNRELIABLE = 0, LOW = 1, MEDIUM = 2, HIGH = 3
                Toast.makeText(SimpleGeoArActivity.this, R.string.compass_accuracy_low, Toast.LENGTH_LONG).show();
            }
        }
    };
    private IALocationManager mIALocationManager;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIALocationManager = IALocationManager.create(this);
//        locationProvider = new LocationProvider(this, this, errorCallback);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mIALocationManager.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        locationProvider.onResume();
        /*
         * The SensorAccuracyChangeListener has to be registered to the Architect view after ArchitectView.onCreate.
         * There may be more than one SensorAccuracyChangeListener.
         */
        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mListener);
        architectView.registerSensorAccuracyChangeListener(sensorAccuracyChangeListener);
    }

    @Override
    protected void onPause() {
//        locationProvider.onPause();
        super.onPause();
        if (mIALocationManager != null) {
            mIALocationManager.removeLocationUpdates(mListener);
        }
        // The SensorAccuracyChangeListener has to be unregistered from the Architect view before ArchitectView.onDestroy.
        architectView.unregisterSensorAccuracyChangeListener(sensorAccuracyChangeListener);
    }

    /**
     * The ArchitectView has to be notified when the location of the device
     * changed in order to accurately display the Augmentations for Geo AR.
     * <p>
     * The ArchitectView has two methods which can be used to pass the Location,
     * it should be chosen by whether an altitude is available or not.
     */
//    @Override
//    public void onLocationChanged(Location location) {
//        Log.d(TAG, "Location: " + location);
//        float accuracy = location.hasAccuracy() ? location.getAccuracy() : 1000;
//        if (location.hasAltitude()) {
//            architectView.setLocation(location.getLatitude(), location.getLongitude(), location.getAltitude(), accuracy);
//        } else {
//            architectView.setLocation(location.getLatitude(), location.getLongitude(), accuracy);
//        }
//    }

//    /**
//     * The very basic LocationProvider setup of this sample app does not handle the following callbacks
//     * to keep the sample app as small as possible. They should be used to handle changes in a production app.
//     */
//    @Override
//    public void onStatusChanged(String provider, int status, Bundle extras) {
//    }

//    @Override
//    public void onProviderEnabled(String provider) {
//    }
//
//    @Override
//    public void onProviderDisabled(String provider) {
//    }

//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        locationProvider.onRequestPermissionsResult(requestCode, permissions, grantResults);
//    }

    /**
     * Listener that handles location change events.
     */
    private IALocationListener mListener = new IALocationListenerSupport() {

        /**
         * Location changed, move marker and camera position.
         */
        @Override
        public void onLocationChanged(IALocation location) {

            Log.d(TAG, "new IAlocation received with coordinates: " + location.getLatitude()
                    + "," + location.getLongitude());

            if (architectView == null) {
                // location received before map is initialized, ignoring update here
                return;
            }

            float accuracy = location.toLocation().hasAccuracy() ? location.getAccuracy() : 1000;
            if (location.toLocation().hasAltitude()) {
                architectView.setLocation(location.getLatitude(), location.getLongitude(), location.getAltitude(), accuracy);
            } else {
                architectView.setLocation(location.getLatitude(), location.getLongitude(), accuracy);
            }
        }
    };
}
