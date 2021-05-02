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


import android.content.Context;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.util.Log;
import android.widget.Toast;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.data.NextGenDB;
import it.reyboz.bustorino.middleware.*;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Helper class to manage the fragments and their needs
 */
public class FragmentHelper {
    //GeneralActivity act;
    private final FragmentListenerMain listenerMain;
    private final WeakReference<FragmentManager> managerWeakRef;
    private Stop lastSuccessfullySearchedBusStop;
    //support for multiple frames
    private final int secondaryFrameLayout;
    private final int primaryFrameLayout;
    private final Context context;
    public static final int NO_FRAME = -3;
    private static final String DEBUG_TAG = "BusTO FragmHelper";
    private WeakReference<AsyncDataDownload> lastTaskRef;
    private boolean shouldHaltAllActivities=false;


    public FragmentHelper(FragmentListenerMain listener, FragmentManager framan, Context context, int mainFrame) {
        this(listener,framan, context,mainFrame,NO_FRAME);
    }

    public FragmentHelper(FragmentListenerMain listener, FragmentManager fraMan, Context context, int primaryFrameLayout, int secondaryFrameLayout) {
        this.listenerMain = listener;
        this.managerWeakRef = new WeakReference<>(fraMan);
        this.primaryFrameLayout = primaryFrameLayout;
        this.secondaryFrameLayout = secondaryFrameLayout;
        this.context = context.getApplicationContext();
    }

    /**
     * Get the last successfully searched bus stop or NULL
     *
     * @return the stop
     */
    public Stop getLastSuccessfullySearchedBusStop() {
        return lastSuccessfullySearchedBusStop;
    }

    public void setLastSuccessfullySearchedBusStop(Stop stop) {
        this.lastSuccessfullySearchedBusStop = stop;
    }

    public void setLastTaskRef(WeakReference<AsyncDataDownload> lastTaskRef) {
        this.lastTaskRef = lastTaskRef;
    }

    /**
     * Called when you need to create a fragment for a specified Palina
     * @param p the Stop that needs to be displayed
     */
    public void createOrUpdateStopFragment(Palina p){
        boolean sameFragment;
        ArrivalsFragment arrivalsFragment;

        if(managerWeakRef.get()==null || shouldHaltAllActivities) {
            //SOMETHING WENT VERY WRONG
            Log.e(DEBUG_TAG, "We are asked for a new stop but we can't show anything");
            return;
        }

        FragmentManager fm = managerWeakRef.get();

        if(fm.findFragmentById(primaryFrameLayout) instanceof ArrivalsFragment) {
            arrivalsFragment = (ArrivalsFragment) fm.findFragmentById(primaryFrameLayout);
            //Log.d(DEBUG_TAG, "Arrivals are for fragment with same stop?");
            sameFragment = arrivalsFragment.isFragmentForTheSameStop(p);
        } else {
            sameFragment = false;
            Log.d(DEBUG_TAG, "We aren't showing an ArrivalsFragment");

        }
        setLastSuccessfullySearchedBusStop(p);

        if(!sameFragment) {
            //set the String to be displayed on the fragment
            String displayName = p.getStopDisplayName();
            String displayStuff;

            if (displayName != null && displayName.length() > 0) {
                arrivalsFragment = ArrivalsFragment.newInstance(p.ID,displayName);
            } else {
                arrivalsFragment = ArrivalsFragment.newInstance(p.ID);
            }
            attachFragmentToContainer(fm,arrivalsFragment,true,ResultListFragment.getFragmentTag(p));
        } else {
            Log.d("BusTO", "Same bus stop, accessing existing fragment");
            arrivalsFragment = (ArrivalsFragment) fm.findFragmentById(primaryFrameLayout);
        }
        // DO NOT CALL `setListAdapter` ever on arrivals fragment
        arrivalsFragment.updateFragmentData(p);

        listenerMain.hideKeyboard();
        toggleSpinner(false);
    }

    /**
     * Called when you need to display the results of a search of stops
     * @param resultList the List of stops found
     * @param query String queried
     */
    public void createFragmentFor(List<Stop> resultList,String query){
        listenerMain.hideKeyboard();
        StopListFragment listfragment = StopListFragment.newInstance(query);
        if(managerWeakRef.get()==null || shouldHaltAllActivities) {
            //SOMETHING WENT VERY WRONG
            Log.e(DEBUG_TAG, "We are asked for a new stop but we can't show anything");
            return;
        }
        attachFragmentToContainer(managerWeakRef.get(),listfragment,false,"search_"+query);
        listfragment.setStopList(resultList);
        toggleSpinner(false);

    }

    /**
     * Wrapper for toggleSpinner in Activity
     * @param on new status of spinner system
     */
    public void toggleSpinner(boolean on){
        listenerMain.toggleSpinner(on);
    }

    /**
     * Attach a new fragment to a cointainer
     * @param fm the FragmentManager
     * @param fragment the Fragment
     * @param sendToSecondaryFrame needs to be displayed in secondary frame or not
     * @param tag tag for the fragment
     */
    public void attachFragmentToContainer(FragmentManager fm,Fragment fragment, boolean sendToSecondaryFrame, String tag){
        FragmentTransaction ft = fm.beginTransaction();
        if(sendToSecondaryFrame && secondaryFrameLayout!=NO_FRAME)
            ft.replace(secondaryFrameLayout,fragment,tag);
        else ft.replace(primaryFrameLayout,fragment,tag);
        ft.addToBackStack("state_"+tag);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        ft.commit();
        //fm.executePendingTransactions();
    }

    public void setBlockAllActivities(boolean shouldI) {
        this.shouldHaltAllActivities = shouldI;
    }

    public void stopLastRequestIfNeeded(){
        if(lastTaskRef == null) return;
        AsyncDataDownload task = lastTaskRef.get();
        if(task!=null){
            task.cancel(true);
        }
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
                showToastMessage(R.string.network_error, true);
                break;
            case SERVER_ERROR:
                if (utils.isConnected(context)) {
                    showToastMessage(R.string.parsing_error, true);
                } else {
                    showToastMessage(R.string.network_error, true);
                }
            case PARSER_ERROR:
            default:
                showShortToast(R.string.internal_error);
                break;
            case QUERY_TOO_SHORT:
                showShortToast(R.string.query_too_short);
                break;
            case EMPTY_RESULT_SET:
                showShortToast(R.string.no_bus_stop_have_this_name);
                break;
        }
    }

    public void showToastMessage(int messageID, boolean short_lenght) {
        final int length = short_lenght ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG;
        if (context != null)
        Toast.makeText(context, messageID, length).show();
    }
    private void showShortToast(int messageID){
        showToastMessage(messageID, true);
    }

}
