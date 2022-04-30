package it.reyboz.bustorino.backend.gtfs;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;

public final class PolylineParser {
    /**
     * Decode a Google polyline
     * Thanks to https://stackoverflow.com/questions/9341020/how-to-decode-googles-polyline-algorithm
     * @param encodedPolyline the encoded polyline in a string
     * @param initial_capacity for the list
     * @return the list of points correspoding to the polyline
     */
    public static ArrayList<GeoPoint> decodePolyline(String encodedPolyline, int initial_capacity) {
        ArrayList<GeoPoint> points = new ArrayList<>(initial_capacity);
        int truck = 0;
        int carriage_q = 0;
        int longit=0, latit=0;
        boolean is_lat=true;
        for (int x = 0, xx = encodedPolyline.length(); x < xx; ++x) {
            int i = encodedPolyline.charAt(x);
            i -= 63;
            int _5_bits = i << (32 - 5) >>> (32 - 5);
            truck |= _5_bits << carriage_q;
            carriage_q += 5;
            boolean is_last = (i & (1 << 5)) == 0;
            if (is_last) {
                boolean is_negative = (truck & 1) == 1;
                truck >>>= 1;
                if (is_negative) {
                    truck = ~truck;
                }
                if (is_lat){
                    latit += truck;
                    is_lat = false;
                } else{
                    longit += truck;
                    points.add(new GeoPoint((double)latit/1e5,(double)longit/1e5));
                    is_lat=true;
                }
                carriage_q = 0;
                truck = 0;
            }
        }
        return points;
    }
}
