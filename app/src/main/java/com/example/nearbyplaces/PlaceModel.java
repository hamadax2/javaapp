package com.example.nearbyplaces;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public class PlaceModel {

    @SerializedName("elements")
    private List<Element> elements;

    public List<Element> getElements() {
        return elements;
    }

    public void setElements(List<Element> elements) {
        this.elements = elements;
    }

    public static class Element {
        @SerializedName("type")
        private String type;

        @SerializedName("id")
        private long id;

        @SerializedName("lat")
        private double lat;

        @SerializedName("lon")
        private double lon;

        @SerializedName("tags")
        private Map<String, String> tags;

        @SerializedName("center")
        private Center center;

        private transient double distance;

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }

        public double getLat() {
            if (center != null) return center.getLat();
            return lat;
        }
        public void setLat(double lat) { this.lat = lat; }

        public double getLon() {
            if (center != null) return center.getLon();
            return lon;
        }
        public void setLon(double lon) { this.lon = lon; }

        public Map<String, String> getTags() { return tags; }
        public void setTags(Map<String, String> tags) { this.tags = tags; }

        public String getName() {
            if (tags == null) return "Unknown";
            return tags.getOrDefault("name", "Unknown");
        }

        public String getAddress() {
            if (tags == null) return "";
            String addr = tags.getOrDefault("addr:full", "");
            if (addr.isEmpty()) {
                String street = tags.getOrDefault("addr:street", "");
                String hnr = tags.getOrDefault("addr:housenumber", "");
                String city = tags.getOrDefault("addr:city", "");
                if (!hnr.isEmpty() && !street.isEmpty()) {
                    addr = hnr + " " + street;
                    if (!city.isEmpty()) addr += ", " + city;
                } else if (!hnr.isEmpty()) {
                    addr = hnr;
                } else if (!street.isEmpty()) {
                    addr = street;
                }
                if (addr.isEmpty()) addr = tags.getOrDefault("display_name", "");
            }
            return addr;
        }

        public String getAmenityType() {
            if (tags == null) return "";
            String amenity = tags.getOrDefault("amenity", "");
            if (amenity.isEmpty()) {
                amenity = tags.getOrDefault("shop", "");
            }
            if (amenity.isEmpty()) {
                amenity = tags.getOrDefault("tourism", "");
            }
            if (amenity.isEmpty()) {
                amenity = tags.getOrDefault("leisure", "");
            }
            return amenity;
        }

        public double getDistance() { return distance; }
        public void setDistance(double distance) { this.distance = distance; }
    }

    public static class Center {
        @SerializedName("lat")
        private double lat;

        @SerializedName("lon")
        private double lon;

        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }

        public double getLon() { return lon; }
        public void setLon(double lon) { this.lon = lon; }
    }
}