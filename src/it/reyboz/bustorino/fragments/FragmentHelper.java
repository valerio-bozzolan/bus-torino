/*
	BusTO (fragments)
    Copyright (C) 2018 Fabio Mazza

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.reyboz.bustorino.fragments;


import android.database.sqlite.SQLiteDatabase;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.middleware.*;

import java.util.List;

/**
 * Helper class to manage the fragments and their needs
 */
public class FragmentHelper {
    GeneralActivity act;
    private Stop lastSuccessfullySearchedBusStop;
    private int  primaryFrameLayout,secondaryFrameLayout, swipeRefID;
    public static final int NO_FRAME = -3;
    UserDB userDB;
    StopsDB stopsDB;

    public FragmentHelper(GeneralActivity act, int swipeRefID, int mainFrame) {
        this(act,swipeRefID,mainFrame,NO_FRAME);
    }

    public FragmentHelper(GeneralActivity act, int swipeRefID, int primaryFrameLayout, int secondaryFrameLayout) {
        this.act = act;
        this.swipeRefID = swipeRefID;
        this.primaryFrameLayout = primaryFrameLayout;
        this.secondaryFrameLayout = secondaryFrameLayout;
        stopsDB = new StopsDB(act);
        userDB = new UserDB(act);
    }

    public Stop getLastSuccessfullySearchedBusStop() {
        return lastSuccessfullySearchedBusStop;
    }

    public void setLastSuccessfullySearchedBusStop(Stop stop) {
        this.lastSuccessfullySearchedBusStop = stop;
    }

    /**
     * Called when you need to create a fragment for a specified Palina
     * @param p the Stop that needs to be displayed
     */
    public void createOrUpdateStopFragment(Palina p){
        boolean refreshing;
        ResultListFragment listFragment;

        if(act==null) {
            //SOMETHING WENT VERY WRONG
            return;
        }

        SwipeRefreshLayout srl = (SwipeRefreshLayout) act.findViewById(swipeRefID);
        FragmentManager fm = act.getSupportFragmentManager();
        //DON'T UNDERSTAND WHY BUT IT SAYS IT'S NULL REFERENCE
        if(srl.isRefreshing())
            refreshing=true;
        else if(fm.findFragmentById(R.id.resultFrame) instanceof ResultListFragment) {
            listFragment = (ResultListFragment) fm.findFragmentById(R.id.resultFrame);
            refreshing = listFragment.isFragmentForTheSameStop(p);
        } else
            refreshing = false;

        setLastSuccessfullySearchedBusStop(p);

        if(!refreshing) {
            //set the String to be displayed on the fragment
            String displayName = p.getStopDisplayName();
            String displayStuff;

            if (displayName != null && displayName.length() > 0) {
                displayStuff = p.ID.concat(" - ").concat(displayName);
            } else {
                displayStuff = p.ID;
            }
            listFragment = ResultListFragment.newInstance(ResultListFragment.TYPE_LINES,displayStuff);
            attachFragmentToContainer(fm,listFragment,true,ResultListFragment.getFragmentTag(p));
        } else {
            Log.d("BusTO", "Same bus stop, accessing existing fragment");
            listFragment = (ResultListFragment) fm.findFragmentById(R.id.resultFrame);
        }

        listFragment.setListAdapter(new PalinaAdapter(act.getApplicationContext(),p));
        act.hideKeyboard();
        if (act instanceof FragmentListener) ((FragmentListener) act).readyGUIfor(ResultListFragment.TYPE_LINES);

        toggleSpinner(false);
    }

    /**
     * Called when you need to display the results of a search of stops
     * @param resultList the List of stops found
     * @param query String queried
     */
    public void createFragmentFor(List<Stop> resultList,String query){
        act.hideKeyboard();
        ResultListFragment listfragment = ResultListFragment.newInstance(ResultListFragment.TYPE_STOPS);
        attachFragmentToContainer(act.getSupportFragmentManager(),listfragment,false,"search_"+query);
        if (act instanceof FragmentListener) ((FragmentListener) act).readyGUIfor(ResultListFragment.TYPE_STOPS);
        listfragment.setListAdapter(new StopAdapter(act.getApplicationContext(), resultList));
        toggleSpinner(false);

    }

    /**
     * Wrapper for toggleSpinner in Activity
     * @param on new status of spinner system
     */
    public void toggleSpinner(boolean on){
        if (act instanceof FragmentListener)
            ((FragmentListener) act).toggleSpinner(on);
        else {
            SwipeRefreshLayout srl = (SwipeRefreshLayout) act.findViewById(swipeRefID);
            srl.setRefreshing(false);
        }
    }

    /**
     * Attach a new fragment to a cointainer
     * @param fm the FragmentManager
     * @param fragment the Fragment
     * @param sendToSecondaryFrame needs to be displayed in secondary frame or not
     * @param tag tag for the fragment
     */
    private void attachFragmentToContainer(FragmentManager fm,Fragment fragment, boolean sendToSecondaryFrame, String tag){
        FragmentTransaction ft = fm.beginTransaction();
        if(sendToSecondaryFrame && secondaryFrameLayout!=NO_FRAME)
            ft.replace(secondaryFrameLayout,fragment,tag);
        else ft.replace(primaryFrameLayout,fragment,tag);
        ft.addToBackStack("state_"+tag);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        ft.commit();
        //fm.executePendingTransactions();
    }
    /*
        Set of methods to "wrap" database operations
     */
    //Find a way to open databases
    public void openStopsDB(){
        stopsDB.openIfNeeded();
    }
    public String getLocationFromDB(Stop p){
        return stopsDB.getLocationFromID(p.ID);
    }
    public List<String> getStopRoutesFromDB(String stopID){
        return stopsDB.getRoutesByStop(stopID);
    }
    public void closeDBIfNeeded(){
        stopsDB.closeIfNeeded();
    }
    public String getStopNamefromDB(String stopID){
        return stopsDB.getNameFromID(stopID);
    }
    /**
     * Wrapper to show the errors/status that happened
     * @param res result from Fetcher
     */
    public void showErrorMessage(Fetcher.result res){
        //TODO: implement a common set of errors for all fragments
        switch (res){
            case OK:
                break;
            case CLIENT_OFFLINE:
                act.showMessage(R.string.network_error);
                break;
            case SERVER_ERROR:
                if (act.isConnected()) {
                    act.showMessage(R.string.parsing_error);
                } else {
                    act.showMessage(R.string.network_error);
                }
            case PARSER_ERROR:
            default:
                act.showMessage(R.string.internal_error);
                break;
            case QUERY_TOO_SHORT:
                act.showMessage(R.string.query_too_short);
                break;
            case EMPTY_RESULT_SET:
                act.showMessage(R.string.no_bus_stop_have_this_name);
                break;
        }
    }

}
