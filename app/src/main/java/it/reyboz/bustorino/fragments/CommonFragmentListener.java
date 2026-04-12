package it.reyboz.bustorino.fragments;

import android.os.Bundle;
import androidx.annotation.Nullable;
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
     * We want to show the line in detail for route coming from a stop
     * @param routeGtfsId the route gtfsID (eg, "gtt:10U")
     */
    void openLineFromStop(String routeGtfsId, @Nullable String fromStopID);

    /**
     * Open the line screen on the line, from a live vehicle (optional pattern)
     * @param routeGtfsId the route gtfsID (eg, "gtt:10U")
     * @param optionalPatternId the pattern name (can be null)
     * @param args extra arguments given as Bundle
     */
    void openLineFromVehicle(String routeGtfsId, @Nullable String optionalPatternId, @Nullable Bundle args);
}
