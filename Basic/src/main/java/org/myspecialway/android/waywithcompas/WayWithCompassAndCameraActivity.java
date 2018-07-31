package org.myspecialway.android.waywithcompas;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
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
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.PolylineOptions;
import com.indooratlas.android.sdk.IAExtraInfo;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.IAOrientationListener;
import com.indooratlas.android.sdk.IAOrientationRequest;
import com.indooratlas.android.sdk.IARegion;
import com.indooratlas.android.sdk.resources.IALocationListenerSupport;
import com.indooratlas.android.sdk.resources.IAResourceManager;
import com.indooratlas.android.wayfinding.IARoutingLeg;
import com.indooratlas.android.wayfinding.IARoutingPoint;
import com.indooratlas.android.wayfinding.IAWayfinder;
import com.squareup.picasso.Target;

import org.myspecialway.android.R;

import java.io.InputStream;
import java.util.Arrays;

import uk.co.appoly.arcorelocation.utils.LocationUtils;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.Manifest.permission.CHANGE_WIFI_STATE;

public class WayWithCompassAndCameraActivity extends FragmentActivity implements LocationListener{
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 42;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private static final String TAG = "WayWithCameraAndCompass";

    /* used to decide when bitmap should be downscaled */
    private static final int MAX_DIMENSION = 2048;
    private static final String ROUTE_TAG = "IARoute";

    private IALocationManager mIALocationManager;
    private IAResourceManager mResourceManager;
    private Target mLoadTarget;
    private boolean mShowIndoorLocation = false;

    private IAWayfinder mWayfinder;
    private LatLng mLocation;

    private LatLng mDestination;
    private Marker mDestinationMarker;

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


    //Camera preview
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    protected CameraDevice cameraDevice;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;

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

            Log.d(TAG, "new IALocation received with coordinates: " + latitude
                    + "," + longitude + ", accuracy: " + accuracy + ", bearing: " + bearing);

            //collect location info

            final LatLng center = new LatLng(latitude, longitude);

            mFloor = location.getFloorLevel();
            mLocation = new LatLng(latitude, longitude);
            if (mWayfinder != null) {
                mWayfinder.setLocation(mLocation.latitude, mLocation.longitude, mFloor);
            }
            updateRoute();

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
//            adjustArrow((float) (routeBearing - heading));
        }
    };

    private IAOrientationListener mOrientation = new IAOrientationListener() {
        @Override
        public void onHeadingChanged(long timestamp, double heading) {
            WayWithCompassAndCameraActivity.this.heading = heading;
            headingView.setText(getString(R.string.text_heading, heading));
            adjustArrow((float) (heading - course));
//            adjustArrow((float) (heading - routeBearing));
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
//                // Are we entering a new floor plan or coming back the floor plan we just left?
//                if (mGroundOverlay == null || !region.equals(mOverlayFloorPlan)) {
//                    mCameraPositionNeedsUpdating = true; // entering new fp, need to move camera
//                    if (mGroundOverlay != null) {
//                        mGroundOverlay.remove();
//                        mGroundOverlay = null;
//                    }
//                    mOverlayFloorPlan = region; // overlay will be this (unless error in loading)
//                    fetchFloorPlan(newId);
//                } else {
//                    mGroundOverlay.setTransparency(0.0f);
//                }

//                mShowIndoorLocation = true;
                showInfo("Showing IndoorAtlas SDK\'s location output");
            }
            showInfo("Enter " + (region.getType() == IARegion.TYPE_VENUE
                    ? "VENUE "
                    : "FLOOR_PLAN ") + region.getId());
        }

        @Override
        public void onExitRegion(IARegion region) {
//            if (mGroundOverlay != null) {
//                // Indicate we left this floor plan but leave it there for reference
//                // If we enter another floor plan, this one will be removed and another one loaded
//                mGroundOverlay.setTransparency(0.5f);
//            }
//
//            mShowIndoorLocation = false;
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
        setContentView(R.layout.activity_way_camera);

        Intent intent = getIntent();
        mLocation = intent.getParcelableExtra("START_PONT");
        mDestination = intent.getParcelableExtra("DESTINATION_PONT");
        mFloor = intent.getParcelableExtra("FLOOR");

        // prevent the screen going to sleep while app is on foreground
        findViewById(android.R.id.content).setKeepScreenOn(true);

        iaLocationView = findViewById(R.id.ia_location);
        locationView = findViewById(R.id.location);
        headingView = findViewById(R.id.heading);
        bearingView = findViewById(R.id.bearing);
        routeBearingView = findViewById(R.id.routeBearing);
        routeView = findViewById(R.id.route);

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

        //Camera Preview
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);

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

        if (mDestination == null) {
        }
        if (mWayfinder != null && mLocation != null && mDestination != null && mFloor != null) {
            mWayfinder.setDestination(mDestination.latitude, mDestination.longitude, mFloor);
            Log.d(TAG, "Set destination: (" + mDestination.latitude + ", " + mDestination.longitude + "), floor=" + mFloor);
            updateRoute();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }

        IAExtraInfo extraInfo = mIALocationManager.getExtraInfo();
        Log.d(TAG, "ExtraInfo: traceId: " + extraInfo.traceId == null ? "null" : extraInfo.traceId);

        // start receiving location updates & monitor region changes
        mIALocationManager.requestLocationUpdates(IALocationRequest.create(), mListener);
        mIALocationManager.registerOrientationListener(new IAOrientationRequest(1.0, 1.0), mOrientation);
        mIALocationManager.registerRegionListener(mRegionListener);
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();

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
     * Helper method to cancel current task if any.
     */
    private void cancelPendingNetworkCalls() {
//        if (mFetchFloorPlanTask != null && !mFetchFloorPlanTask.isCancelled()) {
//            mFetchFloorPlanTask.cancel();
//        }
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


//        IARoutingPoint start = mCurrentRoute[0].getBegin();
//        IARoutingPoint dest = mCurrentRoute[mCurrentRoute.length - 1].getEnd();
        course = LocationUtils.bearing(mLocation.latitude, mLocation.longitude, mDestination.latitude, mDestination.longitude);
//        routeView.setText("route: " + mCurrentRoute.length);
//        visualizeRoute(mCurrentRoute);
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

    //Camera Preview
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(WayWithCompassAndCameraActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        try {
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e(TAG, "openCamera X");
    }
    protected void updatePreview() {
        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

}
