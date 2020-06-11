package it.reyboz.bustorino;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.map.CustomInfoWindow;
import it.reyboz.bustorino.middleware.StopsDB;

public class ActivityMap extends AppCompatActivity {

    private static MapView map = null;
    public Context ctx;

    @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB)
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //handle permissions first, before map is created. not depicted here

        //load/initialize the osmdroid configuration, this can be done
        ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        //setting this before the layout is inflated is a good idea
        //it 'should' ensure that the map has a writable location for the map cache, even without permissions
        //if no tiles are displayed, you can try overriding the cache path using Configuration.getInstance().setCachePath
        //see also StorageUtils
        //note, the load method also sets the HTTP User Agent to your application's package name, abusing osm's tile servers will get you banned based on this string

        //inflate and create the map
        setContentView(R.layout.activity_map);

        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);

        // add ability to zoom with 2 fingers
        map.setMultiTouchControls(true);

        // take the parameters if it's called from other Activities
        Bundle b = getIntent().getExtras();
        GeoPoint marker = null;
        String name = null;
        String ID = null;
        if(b != null) {
            double lat = b.getDouble("lat");
            double lon = b.getDouble("lon");
            marker = new GeoPoint(lat, lon);
            name = b.getString("name");
            ID = b.getString("ID");
        }
        startMap(marker, name, ID);

        // on drag and zoom reload the markers
        map.addMapListener(new DelayedMapListener(new MapListener() {

            @Override
            public boolean onScroll(ScrollEvent paramScrollEvent) {
                loadMarkers();
                return true;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                loadMarkers();
                return true;
            }

        }));
    }

    public void startMap(GeoPoint marker, String name, String ID) {

        // move the map on the marker position or on a default view point: Turin, Piazza Castello
        // and set the start zoom
        IMapController mapController = map.getController();
        GeoPoint startPoint;
        if (marker != null) {
            startPoint = marker;
            mapController.setZoom(20.0);
        } else {
            startPoint = new GeoPoint(45.0708, 7.6858);
            mapController.setZoom(18.0);
        }
        // set the minimum zoom level
        map.setMinZoomLevel(15.5);
        mapController.setCenter(startPoint);

        loadMarkers();
        if (marker != null) {
            // make a marker with the info window open for the searched marker
            makeMarker(startPoint, name , ID, true);
        }

    }

    public void makeMarker(GeoPoint geoPoint, String stopName, String ID, boolean isStartMarker) {

        // add a marker
        Marker marker = new Marker(map);

        // set custom info window as info window
        CustomInfoWindow popup = new CustomInfoWindow(map, ID, stopName);
        marker.setInfoWindow(popup);

        // make the marker clickable
        marker.setOnMarkerClickListener((thisMarker, mapView) -> {
            if (thisMarker.isInfoWindowOpen()) {
                // on second click

                // create an intent with these extras
                Intent intent = new Intent(ActivityMap.this, ActivityMain.class);
                Bundle b = new Bundle();
                b.putString("bus-stop-ID", ID);
                b.putString("bus-stop-display-name", stopName);
                intent.putExtras(b);
                intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

                // start ActivityMain with the previous intent
                startActivity(intent);
            } else {
                // on first click

                // hide all opened info window
                InfoWindow.closeAllInfoWindowsOn(map);
                // show this particular info window
                thisMarker.showInfoWindow();
                // move the map to its position
                map.getController().animateTo(thisMarker.getPosition());
            }

            return true;
        });

        // set its position
        marker.setPosition(geoPoint);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        // display the marker
        map.getOverlays().add(marker);
        // add to it an icon
        marker.setIcon(getResources().getDrawable(R.drawable.bus_marker));
        // add to it a title
        marker.setTitle(stopName);

        // show popup info window of the searched marker
        if (isStartMarker) {
            marker.showInfoWindow();
        }

    }

    public void loadMarkers() {

        // get rid of the previous markers
        map.getOverlays().clear();

        // get the top, bottom, left and right screen's coordinate
        BoundingBox bb = map.getBoundingBox();
        double latFrom = bb.getLatSouth();
        double latTo = bb.getLatNorth();
        double lngFrom = bb.getLonWest();
        double lngTo = bb.getLonEast();

        // get the stops located in those coordinates
        StopsDB stopsDB = new StopsDB(ctx);
        stopsDB.openIfNeeded();
        Stop[] stops = stopsDB.queryAllInsideMapView(latFrom, latTo, lngFrom, lngTo);
        stopsDB.closeIfNeeded();

        // add new markers of those stops
        for (Stop stop : stops) {
            GeoPoint marker = new GeoPoint(stop.getLatitude(), stop.getLongitude());
            makeMarker(marker, stop.getStopDefaultName(), stop.ID, false);
        }

    }

    public void onResume(){
        super.onResume();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    public void onPause(){
        super.onPause();
        //this will refresh the osmdroid configuration on resuming.
        //if you make changes to the configuration, use
        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //Configuration.getInstance().save(this, prefs);
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }
}