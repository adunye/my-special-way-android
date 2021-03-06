package org.myspecialway.android.waywithcompas;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.IAOrientationListener;
import com.indooratlas.android.sdk.IAOrientationRequest;
import com.indooratlas.android.sdk.IARegion;
import com.indooratlas.android.sdk.resources.IAFloorPlan;
import com.indooratlas.android.sdk.resources.IALatLng;
import com.indooratlas.android.sdk.resources.IALocationListenerSupport;
import com.indooratlas.android.sdk.resources.IAResourceManager;
import com.indooratlas.android.sdk.resources.IAResult;
import com.indooratlas.android.sdk.resources.IAResultCallback;
import com.indooratlas.android.sdk.resources.IATask;
import com.indooratlas.android.wayfinding.IARoutingLeg;
import com.indooratlas.android.wayfinding.IARoutingPoint;
import com.indooratlas.android.wayfinding.IAWayfinder;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import org.myspecialway.android.R;
import org.myspecialway.android.SdkExample;

import java.io.InputStream;
import java.util.Arrays;

import uk.co.appoly.arcorelocation.utils.LocationUtils;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.Manifest.permission.CHANGE_WIFI_STATE;

@SdkExample(description = R.string.example_wayfinding_with_compass_description)
public class WayWithCompassOverlayActivity extends FragmentActivity implements LocationListener,
        GoogleMap.OnMapClickListener, OnMapReadyCallback {
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 42;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private static final String TAG = "IndoorAtlasExample";

    /* used to decide when bitmap should be downscaled */
    private static final int MAX_DIMENSION = 2048;
    private static final String ROUTE_TAG = "IARoute";

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private Circle mCircle;
    private IARegion mOverlayFloorPlan = null;
    private GroundOverlay mGroundOverlay = null;
    private IALocationManager mIALocationManager;
    private IAResourceManager mResourceManager;
    private IATask<IAFloorPlan> mFetchFloorPlanTask;
    private Target mLoadTarget;
    private boolean mCameraPositionNeedsUpdating = true; // update on first location
    private boolean mShowIndoorLocation = false;

    private IAWayfinder mWayfinder;
    private LatLng mLocation;

    private LatLng mDestination;
    private Marker mDestinationMarker;

    private Polyline mPath;
    private Polyline mPathCurrent;
    private IARoutingLeg[] mCurrentRoute;

    private Integer mFloor;

//    private Compass compass;

    private float currentAzimuth;
    private double heading;
    private double routeBearing;
    private double course;

    private ImageView arrowView;
    private TextView iaLocationView;
    private TextView locationView;
    private TextView headingView;
    private TextView bearingView;
    private TextView routeBearingView;
    private TextView routeView;

    String[] neededPermissions = {
            CHANGE_WIFI_STATE,
            ACCESS_WIFI_STATE,
            ACCESS_COARSE_LOCATION,
            ACCESS_FINE_LOCATION
    };
    private Button startButton;
    private TextView traceIdView;

    private void showLocationCircle(LatLng center, double accuracyRadius) {
        if (mCircle == null) {
            // location can received before map is initialized, ignoring those updates
            if (mMap != null) {
                mCircle = mMap.addCircle(new CircleOptions()
                        .center(center)
                        .radius(accuracyRadius)
                        .fillColor(0x801681FB)
                        .strokeColor(0x800A78DD)
                        .zIndex(1.0f)
                        .visible(true)
                        .strokeWidth(5.0f));
            }
        } else {
            // move existing markers position to received location
            mCircle.setCenter(center);
            mCircle.setRadius(accuracyRadius);
        }
    }

    /**
     * Listener that handles location change events.
     */
    private IALocationListener mListener = new IALocationListenerSupport() {

        /**
         * Location changed, move marker and camera position.
         */
        @Override
        public void onLocationChanged(IALocation location) {

            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            float bearing = location.getBearing();
            float accuracy = location.getAccuracy();

            Log.d(TAG, "new IALocation received with coordinates: " + ", " + latitude
                    + "," + longitude + ", accuracy: " + accuracy + ", bearing: " + bearing);

            if (mMap == null) {
                // location received before map is initialized, ignoring update here
                return;
            }

            //collect location info

            final LatLng center = new LatLng(latitude, longitude);

            mFloor = location.getFloorLevel();
            mLocation = new LatLng(latitude, longitude);
            if (mWayfinder != null) {
                mWayfinder.setLocation(mLocation.latitude, mLocation.longitude, mFloor);
            }
            updateRoute();

            if (mShowIndoorLocation) {
                showLocationCircle(center, accuracy);
            }

            // our camera position needs updating if location has significantly changed
            if (mCameraPositionNeedsUpdating) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 17.5f));
                mCameraPositionNeedsUpdating = false;
            }

            routeBearing = 0;
            if (mCurrentRoute != null && mCurrentRoute.length > 0){
                IARoutingPoint point = getNearestValidPointOnRoute(location, mCurrentRoute, accuracy);
                if (point != null){
                    routeBearing = LocationUtils.bearing(latitude, longitude, point.getLatitude(), point.getLongitude());
                }
            }
            String msg = "bearing: " + routeBearing + ", dif : " +(routeBearing - heading) + ", accuracy: " + accuracy;
            Log.d(TAG, msg);
            bearingView.setText("bearing: " + location.getBearing());
            routeBearingView.setText("route bearing: " + routeBearing);
            iaLocationView.setText("iaLocation: [lat:" + location.getLatitude() + ", lon:" + location.getLongitude() + ", acc:" + location.getAccuracy());
            String traceId = mIALocationManager.getExtraInfo().traceId == null ? "null " : mIALocationManager.getExtraInfo().traceId;
            traceIdView.setText("traceId: " + traceId);
            Log.d(TAG, traceId);
        }
    };

    private IAOrientationListener mOrientation = new IAOrientationListener() {
        @Override
        public void onHeadingChanged(long timestamp, double heading) {
            WayWithCompassOverlayActivity.this.heading = heading;
            headingView.setText(getString(R.string.text_heading, heading));
            adjustArrow((float) (heading - course));
        }

        @Override
        public void onOrientationChange(long timestamp, double[] orientation) {
        }
    };

    private IARoutingPoint getNearestValidPointOnRoute(IALocation location, IARoutingLeg[] route, float accuracy) {
        double distanceThreshold = 3*accuracy;
        double distanceDeviation = 1.5;
        double bearingThreshold = 5.0;
        for (IARoutingLeg leg : route){
            IARoutingPoint end = leg.getEnd();
            double latitude = end.getLatitude();
            double longitude = end.getLongitude();
            float[] result = new float[3];
            Location.distanceBetween(location.getLatitude(), location.getLongitude(), latitude, longitude, result);
            if (result[0] < distanceThreshold){
                continue;
            }
            if (leg.getLength() <= distanceDeviation){
                continue;
            }
            double bearing = LocationUtils.bearing(location.getLatitude(), location.getLongitude(), end.getLatitude(), end.getLongitude());
            if (Math.abs(360 - bearing - heading) > bearingThreshold){
                return leg.getBegin();
            }
            return leg.getEnd();
        }
        return null;
    }

    /**
     * Listener that changes overlay if needed
     */
    private IARegion.Listener mRegionListener = new IARegion.Listener() {
        @Override
        public void onEnterRegion(IARegion region) {
            if (region.getType() == IARegion.TYPE_FLOOR_PLAN) {
                final String newId = region.getId();
                // Are we entering a new floor plan or coming back the floor plan we just left?
                if (mGroundOverlay == null || !region.equals(mOverlayFloorPlan)) {
                    mCameraPositionNeedsUpdating = true; // entering new fp, need to move camera
                    if (mGroundOverlay != null) {
                        mGroundOverlay.remove();
                        mGroundOverlay = null;
                    }
                    mOverlayFloorPlan = region; // overlay will be this (unless error in loading)
                    fetchFloorPlan(newId);
                } else {
                    mGroundOverlay.setTransparency(0.0f);
                }

                mShowIndoorLocation = true;
                showInfo("Showing IndoorAtlas SDK\'s location output");
            }
            showInfo("Enter " + (region.getType() == IARegion.TYPE_VENUE
                    ? "VENUE "
                    : "FLOOR_PLAN ") + region.getId());
        }

        @Override
        public void onExitRegion(IARegion region) {
            if (mGroundOverlay != null) {
                // Indicate we left this floor plan but leave it there for reference
                // If we enter another floor plan, this one will be removed and another one loaded
                mGroundOverlay.setTransparency(0.5f);
            }

            mShowIndoorLocation = false;
            showInfo("Exit " + (region.getType() == IARegion.TYPE_VENUE
                    ? "VENUE "
                    : "FLOOR_PLAN ") + region.getId());
        }

    };

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startListeningPlatformLocations();
                }
                break;
            }

            case REQUEST_CAMERA_PERMISSION:{
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    // close the app
                    Toast.makeText(this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!mShowIndoorLocation) {
            Log.d(TAG, "new LocationService location received with coordinates: " + location.getLatitude()
                    + "," + location.getLongitude() + ", accuracy: " + location.getAccuracy() + ", bearing: " + location.getBearing());

            showLocationCircle(
                    new LatLng(location.getLatitude(), location.getLongitude()),
                    location.getAccuracy());
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps_compass);

        // prevent the screen going to sleep while app is on foreground
        findViewById(android.R.id.content).setKeepScreenOn(true);

        iaLocationView = findViewById(R.id.ia_location);
        locationView = findViewById(R.id.location);
        headingView = findViewById(R.id.heading);
        bearingView = findViewById(R.id.bearing);
        routeBearingView = findViewById(R.id.routeBearing);
        routeView = findViewById(R.id.route);
        traceIdView = findViewById(R.id.traceId);

        // instantiate IALocationManager and IAResourceManager
        mIALocationManager = IALocationManager.create(this);
        mResourceManager = IAResourceManager.create(this);

        // Request GPS locations
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, neededPermissions, MY_PERMISSION_ACCESS_FINE_LOCATION);
            return;
        }

        startListeningPlatformLocations();

        String graphJSON = loadGraphJSON();
        if (graphJSON == null) {
            Toast.makeText(this, "Could not find wayfinding_graph.json from raw " +
                    "resources folder. Cannot do wayfinding.", Toast.LENGTH_LONG).show();
        } else {
            mWayfinder = IAWayfinder.create(this, graphJSON);
        }

        arrowView = findViewById(R.id.main_image_hands);

        startButton = findViewById(R.id.camera);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCameraActivity();
            }
        });
    }

    private void startCameraActivity() {
        Intent cameraIntent = new Intent(this, WayWithCompassAndCameraActivity.class);
        cameraIntent.putExtra("START_PONT", mLocation);
        cameraIntent.putExtra("DESTINATION_PONT", mDestination);
        cameraIntent.putExtra("FLOOR", mFloor);
        startActivity(cameraIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // remember to clean up after ourselves
        mIALocationManager.destroy();
        if (mWayfinder != null) {
            mWayfinder.close();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMapAsync(this);
        }
     }

    @Override
    protected void onPause() {
        super.onPause();
        // unregister location & region changes
        mIALocationManager.removeLocationUpdates(mListener);
        mIALocationManager.registerRegionListener(mRegionListener);

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * Sets bitmap of floor plan as ground overlay on Google Maps
     */
    private void setupGroundOverlay(IAFloorPlan floorPlan, Bitmap bitmap) {

        if (mGroundOverlay != null) {
            mGroundOverlay.remove();
        }

        if (mMap != null) {
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
            IALatLng iaLatLng = floorPlan.getCenter();
            LatLng center = new LatLng(iaLatLng.latitude, iaLatLng.longitude);
            GroundOverlayOptions fpOverlay = new GroundOverlayOptions()
                    .image(bitmapDescriptor)
                    .zIndex(0.0f)
                    .position(center, floorPlan.getWidthMeters(), floorPlan.getHeightMeters())
                    .bearing(floorPlan.getBearing());

            mGroundOverlay = mMap.addGroundOverlay(fpOverlay);
        }
    }

    /**
     * Download floor plan using Picasso library.
     */
    private void fetchFloorPlanBitmap(final IAFloorPlan floorPlan) {

        final String url = floorPlan.getUrl();

        if (mLoadTarget == null) {
            mLoadTarget = new Target() {

                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    Log.d(TAG, "onBitmap loaded with dimensions: " + bitmap.getWidth() + "x"
                            + bitmap.getHeight());
                    setupGroundOverlay(floorPlan, bitmap);
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {
                    // N/A
                }

                @Override
                public void onBitmapFailed(Drawable placeHolderDraweble) {
                    showInfo("Failed to load bitmap");
                    mOverlayFloorPlan = null;
                }
            };
        }

        RequestCreator request = Picasso.with(this).load(url);

        final int bitmapWidth = floorPlan.getBitmapWidth();
        final int bitmapHeight = floorPlan.getBitmapHeight();

        if (bitmapHeight > MAX_DIMENSION) {
            request.resize(0, MAX_DIMENSION);
        } else if (bitmapWidth > MAX_DIMENSION) {
            request.resize(MAX_DIMENSION, 0);
        }

        request.into(mLoadTarget);
    }


    /**
     * Fetches floor plan data from IndoorAtlas server.
     */
    private void fetchFloorPlan(String id) {

        // if there is already running task, cancel it
        cancelPendingNetworkCalls();

        final IATask<IAFloorPlan> task = mResourceManager.fetchFloorPlanWithId(id);

        task.setCallback(new IAResultCallback<IAFloorPlan>() {

            @Override
            public void onResult(IAResult<IAFloorPlan> result) {

                if (result.isSuccess() && result.getResult() != null) {
                    // retrieve bitmap for this floor plan metadata
                    fetchFloorPlanBitmap(result.getResult());
                } else {
                    // ignore errors if this task was already canceled
                    if (!task.isCancelled()) {
                        // do something with error
                        showInfo("Loading floor plan failed: " + result.getError());
                        mOverlayFloorPlan = null;
                    }
                }
            }
        }, Looper.getMainLooper()); // deliver callbacks using main looper

        // keep reference to task so that it can be canceled if needed
        mFetchFloorPlanTask = task;

    }

    /**
     * Helper method to cancel current task if any.
     */
    private void cancelPendingNetworkCalls() {
        if (mFetchFloorPlanTask != null && !mFetchFloorPlanTask.isCancelled()) {
            mFetchFloorPlanTask.cancel();
        }
    }

    private void showInfo(String text) {
        final Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), text,
                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.button_close, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                snackbar.dismiss();
            }
        });
        snackbar.show();
    }

    private void startListeningPlatformLocations() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
        }
    }

    /**
     * Load "wayfinding_graph.json" from raw resources folder of the app module
     * @return
     */
    private String loadGraphJSON() {
        try {
            Resources res = getResources();
            int resourceIdentifier = res.getIdentifier("wayfinding_graph", "raw", this.getPackageName());
            InputStream in_s = res.openRawResource(resourceIdentifier);

            byte[] b = new byte[in_s.available()];
            in_s.read(b);
            return new String(b);
        } catch (Exception e) {
            Log.e(TAG, "Could not find wayfinding_graph.json from raw resources folder");
            return null;
        }

    }

    @Override
    public void onMapClick(LatLng point) {
        if (mFloor == null) {
            return;
        }
        if (mMap != null) {

            mDestination = point;
            if (mDestinationMarker == null) {
                mDestinationMarker = mMap.addMarker(new MarkerOptions()
                        .position(point)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            } else {
                mDestinationMarker.setPosition(point);
            }
            if (mWayfinder != null) {
                mWayfinder.setDestination(point.latitude, point.longitude, mFloor);
            }
            Log.d(TAG, "Set destination: (" + mDestination.latitude + ", " +
                    mDestination.longitude + "), floor=" + mFloor);

            updateRoute();
            printRoute();
        }
    }

    private void updateRoute() {
        if (mLocation == null || mDestination == null || mWayfinder == null) {
            return;
        }
        Log.d(TAG, "Updating the wayfinding route");

        mCurrentRoute = mWayfinder.getRoute();
        if (mCurrentRoute == null || mCurrentRoute.length == 0) {
            // Wrong credentials or invalid wayfinding graph
            return;
        }


        IARoutingPoint start = mCurrentRoute[0].getBegin();
        IARoutingPoint dest = mCurrentRoute[mCurrentRoute.length - 1].getEnd();
        course = LocationUtils.bearing(start.getLatitude(), start.getLongitude(), dest.getLatitude(), dest.getLongitude());
        if (mPath != null) {
            // Remove old path if any
            clearOldPath();
        }
//        routeView.setText("route: " + mCurrentRoute.length);
        visualizeRoute(mCurrentRoute);
    }

    private void printRoute() {
        if (mCurrentRoute == null || mCurrentRoute.length == 0 ){
            return;
        }

        StringBuffer buffer = new StringBuffer();
        buffer.append("Route: length: " + mCurrentRoute.length + "\n");
        for (IARoutingLeg leg : mCurrentRoute){
            double bearing = LocationUtils.bearing(leg.getBegin().getLatitude(), leg.getBegin().getLongitude(), leg.getEnd().getLatitude(), leg.getEnd().getLongitude());
            double dif = bearing - heading;
            buffer.append("len:" + leg.getLength() + ", dir: " + leg.getDirection() + ", bearing: " + bearing + ", dif:" + dif + "\n");
        }
        Log.d(ROUTE_TAG, buffer.toString());
//        routeView.setText(buffer.toString());
    }

    /**
     * Clear the visualizations for the wayfinding paths
     */
    private void clearOldPath() {
        mPath.remove();
        mPathCurrent.remove();
    }

    /**
     * Visualize the IndoorAtlas Wayfinding path on top of the Google Maps.
     * @param legs Array of IARoutingLeg objects returned from IAWayfinder.getRoute()
     */
    private void visualizeRoute(IARoutingLeg[] legs) {
        // optCurrent will contain the wayfinding path in the current floor and opt will contain the
        // whole path, including parts in other floors.
        PolylineOptions opt = new PolylineOptions();
        PolylineOptions optCurrent = new PolylineOptions();

        for (IARoutingLeg leg : legs) {
            opt.add(new LatLng(leg.getBegin().getLatitude(), leg.getBegin().getLongitude()));
            if (leg.getBegin().getFloor() == mFloor && leg.getEnd().getFloor() == mFloor) {
                optCurrent.add(
                        new LatLng(leg.getBegin().getLatitude(), leg.getBegin().getLongitude()));
                optCurrent.add(
                        new LatLng(leg.getEnd().getLatitude(), leg.getEnd().getLongitude()));
            }
        }
        optCurrent.color(Color.RED);
        if (legs.length > 0) {
            IARoutingLeg leg = legs[legs.length - 1];
            opt.add(new LatLng(leg.getEnd().getLatitude(), leg.getEnd().getLongitude()));
        }
        // Here wayfinding path in different floor than current location is visualized in blue and
        // path in current floor is visualized in red
        mPath = mMap.addPolyline(opt);
        mPathCurrent = mMap.addPolyline(optCurrent);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Request GPS locations
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, neededPermissions, MY_PERMISSION_ACCESS_FINE_LOCATION);
            return;
        }
        mMap.setMyLocationEnabled(false);
        mMap.setOnMapClickListener(this);
        // start receiving location updates & monitor region changes
        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mListener);
        mIALocationManager.registerOrientationListener(new IAOrientationRequest(1.0, 1.0), mOrientation);
        mIALocationManager.registerRegionListener(mRegionListener);

    }

    private void adjustArrow(float azimuth) {
        Log.d(TAG, "will set rotation from " + currentAzimuth + " to "
                + azimuth);

        Animation an = new RotateAnimation(-currentAzimuth, -azimuth,
                Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
                0.5f);
        currentAzimuth = azimuth;

        an.setDuration(500);
        an.setRepeatCount(0);
        an.setFillAfter(true);

        arrowView.startAnimation(an);
    }
}
