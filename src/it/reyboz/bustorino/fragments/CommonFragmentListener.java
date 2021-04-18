package it.reyboz.bustorino.fragments;

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
}
