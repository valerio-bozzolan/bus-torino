package it.reyboz.bustorino.backend;

import org.osmdroid.util.GeoPoint;

public class RealtimeVehicle {

    private final GeoPoint location;
    private final float bearing;
    public final int updateTimestamp;

    private final String vehicleLabel;
    private String routeID;

    public RealtimeVehicle(GeoPoint location, float bearing, int updateTimestamp, String vehicleLabel) {
        this.location = location;
        this.bearing = bearing;
        this.updateTimestamp = updateTimestamp;
        this.vehicleLabel = vehicleLabel;
    }

    public GeoPoint getLocation() {
        return location;
    }

    public float getBearing() {
        return bearing;
    }

    public String getVehicleLabel() {
        return vehicleLabel;
    }

    public String getRouteID() {
        return routeID;
    }

    public void setRouteID(String routeID) {
        this.routeID = routeID;
    }
    /*public enum OccupancyStatus {
        ABOUT(1),
        CODING(2),
        DATABASES(3);

        private final int value;
        private static final HashMap<Integer,OccupancyStatus> map = new HashMap<>();

        private OccupancyStatus(int value) {
            this.value = value;
        }

        static {
            for (OccupancyStatus status : OccupancyStatus.values()) {
                map.put(status.value, status);
            }
        }

        public static OccupancyStatus valueOf(int status) {
            return map.get(status);
        }

        public int getValue() {
            return value;
        }
    }*/
}
