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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import android.util.Log;
import android.widget.Toast;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.middleware.*;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Helper class to manage the fragments and their needs
 */
public class FragmentHelper {
    //GeneralActivity act;
    private final FragmentListenerMain mainFragment;
    private final WeakReference<FragmentManager> managerWeakRef;
    private Stop lastSuccessfullySearchedBusStop;
    //support for multiple frames
    private final int secondaryFrameLayout;
    private final int primaryFrameLayout;
    private final Context context;
    public static final int NO_FRAME = -3;
    private static final String DEBUG_TAG = "BusTO FragmHelper";
    private final StopSearcher stopSearcher;

    //this is for deciding whether to popMain Fragment stack with children
    private final LinkedBlockingDeque<Boolean> popMainQueueOnMainFragStack = new LinkedBlockingDeque<>();


    public FragmentHelper(FragmentListenerMain listener, FragmentManager framan, Context context, int mainFrame) {
        this(listener,framan, context,mainFrame,NO_FRAME);
    }

    public FragmentHelper(FragmentListenerMain listener, FragmentManager fraMan, Context context, int primaryFrameLayout, int secondaryFrameLayout) {
        this.mainFragment = listener;
        this.managerWeakRef = new WeakReference<>(fraMan);
        this.primaryFrameLayout = primaryFrameLayout;
        this.secondaryFrameLayout = secondaryFrameLayout;
        this.context = context.getApplicationContext();
        stopSearcher = new StopSearcher(this);
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

    public boolean needToPopMainStackOnBack(){
        if(popMainQueueOnMainFragStack.isEmpty()){
            return true;
        }
        return popMainQueueOnMainFragStack.pop();
    }
    public void setMainFragmentManagerTransition(boolean popMain){
        Log.d(DEBUG_TAG, "Adding child fragment pop for main screen: " + popMain);
        popMainQueueOnMainFragStack.addFirst(popMain);
    }
    /**
     * Called when you need to create a fragment for a specified Palina
     * @param p the Stop that needs to be displayed
     */
    public void showArrivalsFragmentForStop(@NonNull Palina p, boolean addToBackStack){
        boolean sameFragment = false;
        ArrivalsFragment arrivalsFragment = null;
        final FragmentManager fm = managerWeakRef.get();
        if(fm == null) return;

        if(fm.findFragmentById(primaryFrameLayout) instanceof ArrivalsFragment frag) {
            sameFragment = frag.isFragmentForTheSameStop(p);
            if(sameFragment) {
                arrivalsFragment = frag;
                Log.d("BusTO", "Same bus stop, accessing existing fragment");

            }
        }

        if(!sameFragment) {
            // get old fragment
            var frag = fm.findFragmentByTag(ArrivalsFragment.getFragmentTag(p));
            if(frag instanceof ArrivalsFragment) {
                attachFragmentToContainer(fm, frag, null, true, addToBackStack);
                arrivalsFragment = (ArrivalsFragment) frag;
            } else { // create new fragment
                //set the String to be displayed on the fragment
                String displayName = p.getStopDisplayName();
                if (displayName != null && !displayName.isEmpty()) {
                    arrivalsFragment = ArrivalsFragment.newInstance(p.ID, displayName);
                } else {
                    arrivalsFragment = ArrivalsFragment.newInstance(p.ID);
                }
                String probableTag = ArrivalsFragment.getFragmentTag(p);
                attachFragmentToContainer(fm, arrivalsFragment, probableTag, true, addToBackStack);
            }
        }
        setLastSuccessfullySearchedBusStop(p);
        // update the data only if I have information about the passaggi
        if(p.getTotalNumberOfPassages() > 0)
            arrivalsFragment.updateFragmentData(p);
        // enable fragment auto refresh
        arrivalsFragment.setReloadOnResume(true);

        mainFragment.hideKeyboard();
        toggleSpinner(false);
    }

    /**
     * Called when you need to display the results of a search of stops
     * @param resultList the List of stops found
     * @param query String queried
     */
    public void createStopListFragment(List<Stop> resultList, String query, boolean addToBackStack){
        mainFragment.hideKeyboard();
        StopListFragment listfragment = StopListFragment.newInstance(query);
        if(managerWeakRef.get()==null) {
            //SOMETHING WENT VERY WRONG
            Log.e(DEBUG_TAG, "We are asked for a new stop but we can't show anything");
            return;
        }
        attachFragmentToContainer(managerWeakRef.get(),
                listfragment, "search_"+query, false, addToBackStack);
        //DO NOT DO THE SAME ON THE ARRIVALS (the call goes through MainActivity)
        setMainFragmentManagerTransition(false);
        listfragment.setStopList(resultList);
        //listenerMain.readyGUIfor(FragmentKind.STOPS);
        toggleSpinner(false);

    }

    /**
     * Wrapper for toggleSpinner in Activity
     * @param on new status of spinner system
     */
    public void toggleSpinner(boolean on){
        mainFragment.toggleSpinner(on);
    }

    /**
     * Attach a new fragment to the appropriate container
     * @param fm the FragmentManager
     * @param fragment the Fragment
     * @param tagAttach attach tag (can be null, the fragment's own tag has preference)
     * @param addToBackStack if the transaction is to be added to the stack
     * @param toSecondaryFrame if the fragment goes to the secondary frame
     */
    protected void attachFragmentToContainer(FragmentManager fm, Fragment fragment, @Nullable String tagAttach, boolean toSecondaryFrame, boolean addToBackStack){

        FragmentTransaction ft = fm.beginTransaction();
        int frameID;
        if(toSecondaryFrame && secondaryFrameLayout!=NO_FRAME)
            frameID = secondaryFrameLayout;
        else
            frameID = primaryFrameLayout;
        var tag = fragment.getTag();
        if(tag == null) tag = tagAttach;
        // there is only one case
        //switch (pars.transaction){
        //    case REPLACE:
        ft.replace(frameID,fragment,tag);
        //}
        if (addToBackStack)
            ft.addToBackStack("state_"+tag);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
        ft.commit();
        //fm.executePendingTransactions();
    }

    public void stopLastRequestIfNeeded(){
        /*if(lastTaskRef == null) return;
        AsyncTask task = lastTaskRef.get();
        if(task!=null){
            task.cancel(interruptIfRunning);
        }

         */
        stopSearcher.cancelLastRequest();
    }
    public void requestStopSearch(String query){
        stopSearcher.cancelLastRequest();
        stopSearcher.runRequest(query, new StopsFinderByName[]{new GTTStopsFetcher(), new FiveTStopsFetcher()}); // run with the default fetchers
    }

    /**
     * Wrapper to show the errors/status that happened
     * @param res result from Fetcher
     */
    public void showErrorMessage(Fetcher.Result res, SearchRequestType type){
        //TODO: implement a common set of errors for all fragments
        if (res==null){
            Log.e(DEBUG_TAG, "Asked to show result with null result");
            return;
        }
        Log.d(DEBUG_TAG, "Showing result for "+res);
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
                if (type == SearchRequestType.STOPS)
                    showShortToast(R.string.no_bus_stop_have_this_name);
                else if(type == SearchRequestType.ARRIVALS){
                    showShortToast(R.string.no_arrivals_stop);
                }
                break;
            case NOT_FOUND:
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
    /*
    // 18/05/2026: Commenting, do not remove, might be useful later
    enum Transaction{
        REPLACE,
    }
    private static final class AttachParameters {
        String tag;
        boolean attachToSecondaryFrame;
        Transaction transaction;
        boolean addToBackStack;

        public AttachParameters(String tag, boolean attachToSecondaryFrame, Transaction transaction, boolean addToBackStack) {
            this.tag = tag;
            this.attachToSecondaryFrame = attachToSecondaryFrame;
            this.transaction = transaction;
            this.addToBackStack = addToBackStack;
        }

        public AttachParameters(String tag, boolean attachToSecondaryFrame, boolean addToBackStack) {
            this.tag = tag;
            this.attachToSecondaryFrame = attachToSecondaryFrame;
            this.addToBackStack = addToBackStack;
            this.transaction = Transaction.REPLACE;
        }
    }

     */
}
