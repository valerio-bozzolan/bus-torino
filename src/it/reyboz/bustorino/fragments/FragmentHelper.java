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


import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.sqlite.SQLiteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.adapters.PalinaAdapter;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.middleware.*;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Helper class to manage the fragments and their needs
 */
public class FragmentHelper {
    GeneralActivity act;
    private Stop lastSuccessfullySearchedBusStop;
    //support for multiple frames
    private int  primaryFrameLayout,secondaryFrameLayout, swipeRefID;
    public static final int NO_FRAME = -3;
    private WeakReference<AsyncDataDownload> lastTaskRef;
    private NextGenDB newDBHelper;
    private boolean shouldHaltAllActivities=false;


    public FragmentHelper(GeneralActivity act, int swipeRefID, int mainFrame) {
        this(act,swipeRefID,mainFrame,NO_FRAME);
    }

    public FragmentHelper(GeneralActivity act, int swipeRefID, int primaryFrameLayout, int secondaryFrameLayout) {
        this.act = act;
        this.swipeRefID = swipeRefID;
        this.primaryFrameLayout = primaryFrameLayout;
        this.secondaryFrameLayout = secondaryFrameLayout;
        newDBHelper = NextGenDB.getInstance(act.getApplicationContext());
    }

    /**
     * Get the last successfully searched bus stop or NULL
     *
     * @return
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

        if(act==null || shouldHaltAllActivities) {
            //SOMETHING WENT VERY WRONG
            return;
        }

        SwipeRefreshLayout srl = (SwipeRefreshLayout) act.findViewById(swipeRefID);
        FragmentManager fm = act.getSupportFragmentManager();

        if(fm.findFragmentById(R.id.resultFrame) instanceof ArrivalsFragment) {
            arrivalsFragment = (ArrivalsFragment) fm.findFragmentById(R.id.resultFrame);
            sameFragment = arrivalsFragment.isFragmentForTheSameStop(p);
        } else
            sameFragment = false;

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
            arrivalsFragment = (ArrivalsFragment) fm.findFragmentById(R.id.resultFrame);
        }

        arrivalsFragment.setListAdapter(new PalinaAdapter(act.getApplicationContext(),p));
        act.hideKeyboard();
        toggleSpinner(false);
    }

    /**
     * Called when you need to display the results of a search of stops
     * @param resultList the List of stops found
     * @param query String queried
     */
    public void createFragmentFor(List<Stop> resultList,String query){
        act.hideKeyboard();
        StopListFragment listfragment = StopListFragment.newInstance(query);
        attachFragmentToContainer(act.getSupportFragmentManager(),listfragment,false,"search_"+query);
        listfragment.setStopList(resultList);
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

    synchronized public int insertBatchDataInNextGenDB(ContentValues[] valuesArr,String tableName){
        if(newDBHelper !=null)
            try {
                return newDBHelper.insertBatchContent(valuesArr, tableName);
            } catch (SQLiteException exc){
                Log.w("DB Batch inserting: ","ERROR Inserting the data batch: ",exc.fillInStackTrace());
                return -2;
            }
        else return -1;
    }

    synchronized public ContentResolver getContentResolver(){
        return act.getContentResolver();
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
