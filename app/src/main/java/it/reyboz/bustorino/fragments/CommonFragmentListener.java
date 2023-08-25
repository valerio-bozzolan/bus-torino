package it.reyboz.bustorino.fragments;

import android.location.Location;
import android.view.View;
import it.reyboz.bustorino.backend.Stop;

public interface CommonFragmentListener {


    /**
     * Tell the activity that we need to disable/enable its floatingActionButton
     * @param yes or no
     */
    void showFloatingActionButton(boolean yes);

    /**
     * Sends the message to the activity to adapt the GUI
     * to the fragment that has been attached
     * @param fragmentType the type of fragment attached
     */
    void readyGUIfor(FragmentKind fragmentType);
    /**
     * Houston, we need another fragment!
     *
     * @param ID the Stop ID
     */
    void requestArrivalsForStopID(String ID);

    /**
     * Method to call when we want to hide the keyboard
     */
    void hideKeyboard();

    /**
     * We want to open the map on the specified stop
     * @param stop needs to have location data (latitude, longitude)
     */
    void showMapCenteredOnStop(Stop stop);

    /**
     * We want to show the line in detail for route
     * @param routeGtfsId the route gtfsID (eg, "gtt:10U")
     */
    void showLineOnMap(String routeGtfsId);
}
