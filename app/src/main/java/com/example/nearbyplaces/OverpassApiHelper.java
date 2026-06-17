package com.example.nearbyplaces;

import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class OverpassApiHelper {

    private static final String TAG = "OverpassAPI";
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    private static final String[] RESTAURANT_AMENITIES = {"restaurant", "fast_food"};
    private static final String[] HOSPITAL_AMENITIES = {"hospital", "clinic", "doctors", "dentist"};
    private static final String[] SCHOOL_AMENITIES = {"school", "kindergarten", "university", "college"};
    private static final String[] BANK_AMENITIES = {"bank", "atm", "bureau_de_change"};
    private static final String[] GAS_AMENITIES = {"fuel"};
    private static final String[] POLICE_AMENITIES = {"police"};
    private static final String[] MALL_AMENITIES = {"supermarket", "department_store", "shopping_mall"};
    private static final String[] CAFE_AMENITIES = {"cafe"};
    private static final String[] PHARMACY_AMENITIES = {"pharmacy"};
    private static final String[] MOSQUE_AMENITIES = {"mosque", "place_of_worship"};
    private static final String[] PARK_AMENITIES = {"park", "garden"};
    private static final String[] LIBRARY_AMENITIES = {"library"};

    public interface OnPlacesReceivedListener {
        void onPlacesReceived(List<PlaceModel.Element> places);
        void onError(String error);
    }

    public static void searchNearbyPlaces(double latitude, double longitude, int radius,
                                           String category, OnPlacesReceivedListener listener) {
        String query = buildOverpassQuery(latitude, longitude, radius, category);
        Log.d(TAG, "Searching: " + category + " near " + latitude + "," + longitude);
        new FetchPlacesTask(listener).execute(query);
    }

    private static String buildOverpassQuery(double lat, double lon, int radius, String category) {
        String[] amenities = getAmenitiesForCategory(category);

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("[out:json][timeout:25];\n(");

        for (int i = 0; i < amenities.length; i++) {
            if (i > 0) queryBuilder.append(";\n");
            queryBuilder.append("  node[\"amenity\"=\"")
                    .append(amenities[i])
                    .append("\"](around:")
                    .append(radius)
                    .append(",")
                    .append(lat)
                    .append(",")
                    .append(lon)
                    .append(");");
        }

        for (int i = 0; i < amenities.length; i++) {
            queryBuilder.append("\n  way[\"amenity\"=\"")
                    .append(amenities[i])
                    .append("\"](around:")
                    .append(radius)
                    .append(",")
                    .append(lat)
                    .append(",")
                    .append(lon)
                    .append(");");
        }

        queryBuilder.append("\n);\nout center;");

        try {
            return OVERPASS_URL + "?data=" + URLEncoder.encode(queryBuilder.toString(), "UTF-8");
        } catch (Exception e) {
            return OVERPASS_URL + "?data=" + queryBuilder.toString();
        }
    }

    private static String[] getAmenitiesForCategory(String category) {
        switch (category) {
            case "restaurant": return RESTAURANT_AMENITIES;
            case "hospital": return HOSPITAL_AMENITIES;
            case "school": return SCHOOL_AMENITIES;
            case "pharmacy": return PHARMACY_AMENITIES;
            case "bank": return BANK_AMENITIES;
            case "gas_station": return GAS_AMENITIES;
            case "police": return POLICE_AMENITIES;
            case "cafe": return CAFE_AMENITIES;
            case "mall": return MALL_AMENITIES;
            case "mosque": return MOSQUE_AMENITIES;
            case "park": return PARK_AMENITIES;
            case "library": return LIBRARY_AMENITIES;
            default: return new String[]{category};
        }
    }

    private static class FetchPlacesTask extends AsyncTask<String, Void, List<PlaceModel.Element>> {

        private final WeakReference<OnPlacesReceivedListener> listenerRef;
        private String errorMessage;

        FetchPlacesTask(OnPlacesReceivedListener listener) {
            this.listenerRef = new WeakReference<>(listener);
        }

        @Override
        protected List<PlaceModel.Element> doInBackground(String... strings) {
            String urlString = strings[0];
            HttpURLConnection urlConnection = null;
            List<PlaceModel.Element> places = new ArrayList<>();

            try {
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.setConnectTimeout(15000);
                urlConnection.setReadTimeout(30000);
                urlConnection.setDoInput(true);

                int responseCode = urlConnection.getResponseCode();
                Log.d(TAG, "Response code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = urlConnection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    reader.close();
                    inputStream.close();

                    Gson gson = new Gson();
                    Type placeModelType = new TypeToken<PlaceModel>() {}.getType();
                    PlaceModel placeModel = gson.fromJson(response.toString(), placeModelType);

                    if (placeModel != null && placeModel.getElements() != null) {
                        places = placeModel.getElements();
                    }

                    Log.d(TAG, "Fetched " + places.size() + " places");
                } else {
                    errorMessage = "HTTP Error: " + responseCode;
                    Log.e(TAG, errorMessage);
                }

            } catch (Exception e) {
                errorMessage = "Network error: " + e.getMessage();
                Log.e(TAG, errorMessage, e);
            } finally {
                if (urlConnection != null) urlConnection.disconnect();
            }

            return places;
        }

        @Override
        protected void onPostExecute(List<PlaceModel.Element> places) {
            OnPlacesReceivedListener listener = listenerRef.get();
            if (listener != null) {
                if (errorMessage == null) {
                    listener.onPlacesReceived(places);
                } else {
                    listener.onError(errorMessage);
                }
            }
        }
    }
}