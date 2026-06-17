package com.example.nearbyplaces;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.ChipGroup;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final String TAG = "NearbyPlaces";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private final Map<Integer, String> categoryMap = new HashMap<>();

    private MapView mapView;
    private IMapController mapController;
    private LocationManager locationManager;

    private double currentLat = 0;
    private double currentLng = 0;
    private boolean locationFound = false;

    private String selectedCategory = "restaurant";
    private int searchRadius = 5000;

    private Spinner spinnerRadius;
    private ProgressBar progressBar;
    private CardView infoCard;
    private TextView tvPlaceName, tvPlaceAddress, tvPlaceRating, tvPlaceDistance, tvPlaceType;
    private TextView tvStatus;

    private MyLocationNewOverlay myLocationOverlay;
    private Marker myLocationMarker;
    private Marker[] placeMarkers;
    private PlaceModel.Element[] currentPlaces;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        initCategoryMap();
        initViews();
        setupMap();
        setupRadiusSpinner();
        setupChipListeners();
        setupButtonListeners();

        requestLocation();
    }

    private void initCategoryMap() {
        categoryMap.put(R.id.chipRestaurant, "restaurant");
        categoryMap.put(R.id.chipHospital, "hospital");
        categoryMap.put(R.id.chipSchool, "school");
        categoryMap.put(R.id.chipPharmacy, "pharmacy");
        categoryMap.put(R.id.chipBank, "bank");
        categoryMap.put(R.id.chipGasStation, "gas_station");
        categoryMap.put(R.id.chipPolice, "police");
        categoryMap.put(R.id.chipCafe, "cafe");
        categoryMap.put(R.id.chipMall, "mall");
        categoryMap.put(R.id.chipMosque, "mosque");
        categoryMap.put(R.id.chipPark, "park");
        categoryMap.put(R.id.chipLibrary, "library");
    }

    private void initViews() {
        mapView = findViewById(R.id.mapView);
        spinnerRadius = findViewById(R.id.spinnerRadius);
        progressBar = findViewById(R.id.progressBar);
        infoCard = findViewById(R.id.infoCard);
        tvPlaceName = findViewById(R.id.tvPlaceName);
        tvPlaceAddress = findViewById(R.id.tvPlaceAddress);
        tvPlaceRating = findViewById(R.id.tvPlaceRating);
        tvPlaceDistance = findViewById(R.id.tvPlaceDistance);
        tvPlaceType = findViewById(R.id.tvPlaceType);
        tvStatus = findViewById(R.id.tvStatus);
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);

        CompassOverlay compassOverlay = new CompassOverlay(this, mapView);
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

        mapController = mapView.getController();
        mapController.setZoom(14.0);

        myLocationOverlay = new MyLocationNewOverlay(
                new GpsMyLocationProvider(this), mapView);
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.setDrawAccuracyEnabled(true);
        mapView.getOverlays().add(myLocationOverlay);
    }

    private void setupRadiusSpinner() {
        List<String> radiusOptions = Arrays.asList(
                "500 m", "1000 m", "2000 m", "3000 m", "5000 m", "8000 m", "10000 m");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, radiusOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRadius.setAdapter(adapter);
        spinnerRadius.setSelection(4);

        spinnerRadius.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: searchRadius = 500; break;
                    case 1: searchRadius = 1000; break;
                    case 2: searchRadius = 2000; break;
                    case 3: searchRadius = 3000; break;
                    case 4: searchRadius = 5000; break;
                    case 5: searchRadius = 8000; break;
                    case 6: searchRadius = 10000; break;
                }
                if (locationFound) searchNearbyPlaces();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                searchRadius = 5000;
            }
        });
    }

    private void setupChipListeners() {
        ChipGroup chipGroup = findViewById(R.id.chipGroup);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!checkedIds.isEmpty()) {
                selectedCategory = categoryMap.getOrDefault(checkedIds.get(0), "restaurant");
                infoCard.setVisibility(View.GONE);
                if (locationFound) searchNearbyPlaces();
            }
        });
    }

    private void setupButtonListeners() {
        findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            if (locationFound) searchNearbyPlaces();
            else {
                Toast.makeText(this, "Waiting for location...", Toast.LENGTH_SHORT).show();
                requestLocation();
            }
        });

        findViewById(R.id.btnMyLocation).setOnClickListener(v -> {
            if (locationFound) {
                mapController.animateTo(new GeoPoint(currentLat, currentLng));
                mapController.setZoom(16.0);
            } else {
                requestLocation();
            }
        });

        mapView.setOnClickListener(v -> infoCard.setVisibility(View.GONE));
    }

    private void requestLocation() {
        if (checkLocationPermission()) {
            startLocationUpdates();
        } else {
            requestLocationPermission();
        }
    }

    private void startLocationUpdates() {
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("🔍 Finding your location...");
        progressBar.setVisibility(View.VISIBLE);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                if (checkLocationPermission()) {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER, 5000, 10, this);
                    Location lastKnown = locationManager.getLastKnownLocation(
                            LocationManager.GPS_PROVIDER);
                    if (lastKnown != null) onLocationChanged(lastKnown);
                }
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                if (checkLocationPermission()) {
                    locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER, 5000, 10, this);
                    Location lastKnown = locationManager.getLastKnownLocation(
                            LocationManager.NETWORK_PROVIDER);
                    if (lastKnown != null && !locationFound) onLocationChanged(lastKnown);
                }
            }

            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    && !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                progressBar.setVisibility(View.GONE);
                tvStatus.setText("⚠️ Please enable location services");
                Toast.makeText(this, "Please enable GPS or Network location.",
                        Toast.LENGTH_LONG).show();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        progressBar.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);

        currentLat = location.getLatitude();
        currentLng = location.getLongitude();
        locationFound = true;

        Log.d(TAG, "Location: " + currentLat + ", " + currentLng);

        GeoPoint currentLocation = new GeoPoint(currentLat, currentLng);
        mapController.animateTo(currentLocation);
        mapController.setZoom(15.0);

        if (myLocationMarker != null) mapView.getOverlays().remove(myLocationMarker);

        myLocationMarker = new Marker(mapView);
        myLocationMarker.setPosition(currentLocation);
        myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        myLocationMarker.setTitle("📍 You are here");
        myLocationMarker.setSnippet(String.format(Locale.getDefault(),
                "Lat: %.6f, Lon: %.6f", currentLat, currentLng));
        myLocationMarker.setIcon(createBlueDotMarker());
        mapView.getOverlays().add(myLocationMarker);
        mapView.invalidate();

        searchNearbyPlaces();
    }

    private Drawable createBlueDotMarker() {
        int size = 60;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint paint = new Paint();
        paint.setColor(0xFF4285F4);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        canvas.drawCircle(size / 2f, size / 2f - 1, size / 3f, paint);

        paint.setColor(Color.WHITE);
        canvas.drawCircle(size / 2f, size / 2f - 1, size / 7f, paint);

        paint.setColor(0xFF1565C0);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawCircle(size / 2f, size / 2f - 1, size / 3f, paint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    // ==================== SEARCH ====================

    private void searchNearbyPlaces() {
        if (!locationFound) {
            Toast.makeText(this, "Waiting for location...", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        tvStatus.setText("🔍 Searching nearby " + getCategoryDisplayName(selectedCategory) + "...");

        OverpassApiHelper.searchNearbyPlaces(
                currentLat, currentLng, searchRadius, selectedCategory,
                new OverpassApiHelper.OnPlacesReceivedListener() {
                    @Override
                    public void onPlacesReceived(List<PlaceModel.Element> places) {
                        progressBar.setVisibility(View.GONE);
                        tvStatus.setVisibility(View.GONE);

                        if (places != null && !places.isEmpty()) {
                            places.sort((p1, p2) ->
                                    Double.compare(p1.getDistance(), p2.getDistance()));

                            currentPlaces = places.toArray(new PlaceModel.Element[0]);
                            addMarkersToMap(places);

                            Toast.makeText(MainActivity.this,
                                    "Found " + places.size() + " " +
                                            getCategoryDisplayName(selectedCategory),
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            clearMarkers();
                            Toast.makeText(MainActivity.this,
                                    "No " + getCategoryDisplayName(selectedCategory) +
                                            " found within " + searchRadius + "m",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        progressBar.setVisibility(View.GONE);
                        tvStatus.setVisibility(View.GONE);
                        Log.e(TAG, "API Error: " + error);
                        Toast.makeText(MainActivity.this,
                                "Error: " + error + "\nCheck internet connection.",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void clearMarkers() {
        if (placeMarkers != null) {
            for (Marker m : placeMarkers) {
                if (m != null) mapView.getOverlays().remove(m);
            }
        }
        mapView.invalidate();
    }

    private void addMarkersToMap(List<PlaceModel.Element> places) {
        clearMarkers();
        placeMarkers = new Marker[places.size()];

        for (int i = 0; i < places.size(); i++) {
            PlaceModel.Element place = places.get(i);
            GeoPoint point = new GeoPoint(place.getLat(), place.getLon());

            float[] results = new float[1];
            Location.distanceBetween(currentLat, currentLng,
                    place.getLat(), place.getLon(), results);
            place.setDistance(results[0]);

            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(place.getName());
            marker.setSnippet(place.getAddress());
            marker.setSubDescription(String.format(Locale.getDefault(), "%.0f m", results[0]));
            marker.setIcon(createCategoryMarker(selectedCategory));
            marker.setPanToView(false);

            final int idx = i;
            marker.setOnMarkerClickListener((m, mv) -> {
                if (idx < currentPlaces.length) {
                    showPlaceInfo(currentPlaces[idx]);
                    mapController.animateTo(m.getPosition());
                }
                return true;
            });

            mapView.getOverlays().add(marker);
            placeMarkers[i] = marker;
        }
        mapView.invalidate();
    }

    private Drawable createCategoryMarker(String category) {
        int size = 56;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int color = getCategoryColor(category);

        Paint paint = new Paint();
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        canvas.drawCircle(size / 2f, size / 2f - 2, size / 3f, paint);

        paint.setColor(Color.WHITE);
        canvas.drawCircle(size / 2f, size / 2f - 2, size / 8f, paint);

        paint.setColor(0xFF333333);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawCircle(size / 2f, size / 2f - 2, size / 3f, paint);

        return new BitmapDrawable(getResources(), bitmap);
    }

    private void showPlaceInfo(PlaceModel.Element place) {
        infoCard.setVisibility(View.VISIBLE);
        tvPlaceName.setText(place.getName());
        tvPlaceAddress.setText(place.getAddress().isEmpty() ?
                "Address not available" : place.getAddress());

        double distance = place.getDistance();
        if (distance < 1000) {
            tvPlaceDistance.setText(String.format(Locale.getDefault(),
                    "📏 %.0f meters away", distance));
        } else {
            tvPlaceDistance.setText(String.format(Locale.getDefault(),
                    "📏 %.1f km away", distance / 1000.0));
        }

        tvPlaceType.setText("Category: " + getCategoryDisplayName(place.getAmenityType()));
        tvPlaceRating.setText(getCategoryEmoji(selectedCategory) + " " +
                capitalizeFirst(place.getAmenityType()));
    }

    private int getCategoryColor(String category) {
        switch (category) {
            case "restaurant": return 0xFFFF4444;
            case "hospital": return 0xFFE91E63;
            case "school": return 0xFF9C27B0;
            case "pharmacy": return 0xFF009688;
            case "bank": return 0xFFFF9800;
            case "gas_station": return 0xFF607D8B;
            case "police": return 0xFF3F51B5;
            case "cafe": return 0xFF795548;
            case "mall": return 0xFF2196F3;
            case "mosque": return 0xFF009688;
            case "park": return 0xFF4CAF50;
            case "library": return 0xFF8D6E63;
            default: return 0xFF78909C;
        }
    }

    private String getCategoryDisplayName(String category) {
        switch (category) {
            case "restaurant": return "Restaurants";
            case "hospital": return "Hospitals";
            case "school": return "Schools";
            case "pharmacy": return "Pharmacies";
            case "bank": return "Banks";
            case "gas_station": return "Gas Stations";
            case "police": return "Police Stations";
            case "cafe": return "Cafes";
            case "mall": return "Malls";
            case "mosque": return "Mosques";
            case "park": return "Parks";
            case "library": return "Libraries";
            default: return category.substring(0, 1).toUpperCase() + category.substring(1);
        }
    }

    private String getCategoryEmoji(String category) {
        switch (category) {
            case "restaurant": return "🍽️";
            case "hospital": return "🏥";
            case "school": return "🏫";
            case "pharmacy": return "💊";
            case "bank": return "🏦";
            case "gas_station": return "⛽";
            case "police": return "🚔";
            case "cafe": return "☕";
            case "mall": return "🛒";
            case "mosque": return "🕌";
            case "park": return "🌳";
            case "library": return "📚";
            default: return "📍";
        }
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // ==================== PERMISSIONS ====================

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this,
                        "Location permission is required to use this app.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(@NonNull String provider) { }

    @Override
    public void onProviderDisabled(@NonNull String provider) { }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        Configuration.getInstance().save(this, getPreferences(MODE_PRIVATE));
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
            } catch (SecurityException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }
}