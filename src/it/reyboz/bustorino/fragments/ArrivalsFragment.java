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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.adapters.PalinaAdapter;
import it.reyboz.bustorino.backend.DBStatusManager;
import it.reyboz.bustorino.backend.FiveTNormalizer;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.middleware.AppDataProvider;
import it.reyboz.bustorino.middleware.NextGenDB;
import it.reyboz.bustorino.middleware.UserDB;

public class ArrivalsFragment extends ResultListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private final static String KEY_STOP_ID = "stopid";
    private final static String KEY_STOP_NAME = "stopname";
    private final static String DEBUG_TAG = "BUSTOArrivalsFragment";
    private final static int loaderFavId = 2;
    private final static int loaderStopId = 1;
    static final String STOP_TITLE = "messageExtra";

    private @Nullable String stopID,stopName;
    private DBStatusManager prefs;
    private DBStatusManager.OnDBUpdateStatusChangeListener listener;
    private boolean justCreated = false;
    private Palina lastUpdatedPalina = null;

    //Views
    protected ImageButton addToFavorites;


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
        final ArrivalsFragment arrivalsFragment = this;
        listener = new DBStatusManager.OnDBUpdateStatusChangeListener() {
            @Override
            public void onDBStatusChanged(boolean updating) {
                if(!updating){
                    getLoaderManager().restartLoader(loaderFavId,getArguments(),arrivalsFragment);
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_arrivals, container, false);
        messageTextView = (TextView) root.findViewById(R.id.messageTextView);
        addToFavorites = (ImageButton) root.findViewById(R.id.addToFavorites);
        resultsListView = (ListView) root.findViewById(R.id.resultsListView);
        //Button
        addToFavorites.setClickable(true);
        addToFavorites.setOnClickListener(v -> {
            // add/remove the stop in the favorites
            mListener.toggleLastStopToFavorites();
        });

        resultsListView.setOnItemClickListener((parent, view, position, id) -> {
            String routeName;

            Route r = (Route) parent.getItemAtPosition(position);
            routeName = FiveTNormalizer.routeInternalToDisplay(r.getNameForDisplay());
            if (routeName == null) {
                routeName = r.getNameForDisplay();
            }
            if (r.destinazione == null || r.destinazione.length() == 0) {
                Toast.makeText(getContext(),
                        getString(R.string.route_towards_unknown, routeName), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(),
                        getString(R.string.route_towards_destination, routeName, r.destinazione), Toast.LENGTH_SHORT).show();
            }
        });
        String displayName = getArguments().getString(STOP_TITLE);
        setTextViewMessage(String.format(
                getString(R.string.passages), displayName));


        String probablemessage = getArguments().getString(MESSAGE_TEXT_VIEW);
        if (probablemessage != null) {
            //Log.d("BusTO fragment " + this.getTag(), "We have a possible message here in the savedInstaceState: " + probablemessage);
            messageTextView.setText(probablemessage);
            messageTextView.setVisibility(View.VISIBLE);
        }
        return root;
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


    public void updateFragmentData(Palina p, PalinaAdapter adapter){

        super.resetListAdapter(adapter);
    }

    @Override
    public void setNewListAdapter(ListAdapter adapter) {
        throw new UnsupportedOperationException();
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
        }

        // whatever is the case, update the star icon
        mListener.updateStarIconFromLastBusStop();
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
