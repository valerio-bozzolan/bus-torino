package it.reyboz.bustorino.fragments;

import it.reyboz.bustorino.backend.Palina;

public interface FragmentListener {
    void toggleSpinner(boolean state);
    /**
     * Sends the message to the activity to adapt the GUI
     * to the fragment that has been attached
     * @param fragmentType the type of fragment attached
     */
    void readyGUIfor(String fragmentType);
}
