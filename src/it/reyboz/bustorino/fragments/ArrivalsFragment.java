/*
	BusTO  - Fragments components
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


import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.widget.TextView;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.DBStatusManager;
import it.reyboz.bustorino.middleware.AppDataProvider;
import it.reyboz.bustorino.middleware.NextGenDB;
import it.reyboz.bustorino.middleware.UserDB;

public class ArrivalsFragment extends ResultListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private final static String KEY_STOP_ID = "stopid";
    private final static String KEY_STOP_NAME = "stopname";
    private final static String DEBUG_TAG = "BUSTOArrivalsFragment";
    private final static int loaderFavId = 2;
    private final static int loaderStopId = 1;
    private @Nullable String stopID,stopName;
    private TextView messageTextView;
    private DBStatusManager prefs;
    private DBStatusManager.OnDBUpdateStatusChangeListener listener;
    private boolean justCreated = false;

    public static ArrivalsFragment newInstance(String stopID){
        Bundle args = new Bundle();
        args.putString(KEY_STOP_ID,stopID);
        ArrivalsFragment fragment = new ArrivalsFragment();
        //parameter for ResultListFragment
        args.putSerializable(LIST_TYPE,FragmentKind.ARRIVALS);
        fragment.setArguments(args);
        return fragment;
    }
    public static ArrivalsFragment newInstance(String stopID,String stopName){
        ArrivalsFragment fragment = newInstance(stopID);
        Bundle args = fragment.getArguments();
        args.putString(KEY_STOP_NAME,stopName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        stopID = getArguments().getString(KEY_STOP_ID);
        //this might really be null
        stopName = getArguments().getString(KEY_STOP_NAME);
        final ArrivalsFragment f = this;
        listener = new DBStatusManager.OnDBUpdateStatusChangeListener() {
            @Override
            public void onDBStatusChanged(boolean updating) {
                if(!updating){
                    getLoaderManager().restartLoader(loaderFavId,getArguments(),f);
                } else {
                    final LoaderManager lm = getLoaderManager();
                    lm.destroyLoader(loaderFavId);
                    lm.destroyLoader(loaderStopId);
                }
            }

            @Override
            public boolean defaultStatusValue() {
                return true;
            }
        };
        prefs = new DBStatusManager(getContext().getApplicationContext(),listener);
        justCreated = true;

    }

    @Override
    public void onResume() {
        super.onResume();
        LoaderManager loaderManager  = getLoaderManager();

        if(stopID!=null){
            //refresh the arrivals
            if(!justCreated)
                mListener.createFragmentForStop(stopID);
            else justCreated = false;
            //start the loader
            if(prefs.isDBUpdating(true)){
                prefs.registerListener();
            } else {
                loaderManager.restartLoader(loaderFavId, getArguments(), this);
            }
            updateMessage();
        }
    }

    @Nullable
    public String getStopID() {
        return stopID;
    }

    /**
     * Update the message in the fragment
     *
     * It may eventually change the "Add to Favorite" icon
     */
    private void updateMessage(){
        String message = null;
        if (stopName != null && stopID != null && stopName.length() > 0) {
            message = (stopID.concat(" - ").concat(stopName));
        } else if(stopID!=null) {
            message = stopID;
        } else {
            Log.e("ArrivalsFragm"+getTag(),"NO ID FOR THIS FRAGMENT - something went horribly wrong");
        }
        if(message!=null) {
            setTextViewMessage(getString(R.string.passages,message));

            /**
             * If the Bus Stop is already in the favorites, change somehow the UX
             *
             * TODO https://gitpull.it/T18
             */
            if(isStopInFavorites(stopID)) {
                // TODO fill the favorite star and remove this log
                Log.d("ArrivalsFragm"+getTag(), "Bus stop IS in the favorites");
            } else {
                // TODO do not fill the favorite star and remove this log
                Log.d("ArrivalsFragm"+getTag(), "Bus stop IS NOT in the favorites");
            }
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(args.getString(KEY_STOP_ID)==null) return null;
        final String stopID = args.getString(KEY_STOP_ID);
        final Uri.Builder builder = AppDataProvider.getUriBuilderToComplete();
        CursorLoader cl;
        switch (id){
            case loaderFavId:
                builder.appendPath("favorites").appendPath(stopID);
                cl = new CursorLoader(getContext(),builder.build(),UserDB.getFavoritesColumnNamesAsArray,null,null,null);

                break;
            case loaderStopId:
                builder.appendPath("stop").appendPath(stopID);
                cl = new CursorLoader(getContext(),builder.build(),new String[]{NextGenDB.Contract.StopsTable.COL_NAME},
                        null,null,null);
                break;
            default:
                return null;
        }
        cl.setUpdateThrottle(500);
        return cl;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        switch (loader.getId()){
            case loaderFavId:
                final int colUserName = data.getColumnIndex(UserDB.getFavoritesColumnNamesAsArray[1]);
                if(data.getCount()>0){
                    data.moveToFirst();
                    final String probableName = data.getString(colUserName);
                    if(probableName!=null && !probableName.isEmpty()){
                    stopName = probableName;
                    updateMessage();
                    }
                }
                if(stopName == null){
                    //stop is not inside the favorites and wasn't provided
                    Log.d("ArrivalsFragment"+getTag(),"Stop wasn't in the favorites and has no name, looking in the DB");
                    getLoaderManager().restartLoader(loaderStopId,getArguments(),this);
                }
                break;
            case loaderStopId:
                if(data.getCount()>0){
                    data.moveToFirst();
                    stopName = data.getString(data.getColumnIndex(
                            NextGenDB.Contract.StopsTable.COL_NAME
                    ));
                    updateMessage();
                } else {
                    Log.w("ArrivalsFragment"+getTag(),"Stop is not inside the database... CLOISTER BELL");
                }
        }

    }

    @Override
    public void onPause() {
        if(listener!=null)
            prefs.unregisterListener();
        super.onPause();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        //NOTHING TO DO
    }
}
