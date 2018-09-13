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
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.middleware.AppDataProvider;
import it.reyboz.bustorino.middleware.NextGenDB.Contract.StopsTable;
import it.reyboz.bustorino.adapters.StopAdapter;

import java.util.Arrays;
import java.util.List;

public class StopListFragment extends ResultListFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private List<Stop> stopList;
    private StopAdapter mListAdapter;
    private static final String[] dataProjection={StopsTable.COL_LINES_STOPPING,StopsTable.COL_PLACE,StopsTable.COL_TYPE,StopsTable.COL_LOCATION};
    private static final String KEY_STOP_ID = "stopID";
    private static final String WORDS_SEARCHED= "query";
    private static final int EXTRA_ID=160;

    private String searchedWords;
    public StopListFragment(){
        //required empty constructor
    }

    public static StopListFragment newInstance(String searchQuery) {

        Bundle args = new Bundle();
        //TODO: search stops inside the DB
        args.putString(WORDS_SEARCHED,searchQuery);
        StopListFragment fragment = new StopListFragment();
        args.putSerializable(LIST_TYPE,FragmentKind.STOPS);
        fragment.setArguments(args);
        return fragment;
    }

    public void setStopList(List<Stop> stopList){
        this.stopList = stopList;

    }


    @Override
    public void onResume() {
        super.onResume();
        LoaderManager loaderManager  = getLoaderManager();
        if(stopList!=null) {
            mListAdapter = new StopAdapter(getContext(),stopList);
            setListAdapter(mListAdapter);
            for (int i = 0; i < stopList.size(); i++) {
                final Bundle b = new Bundle();
                b.putString(KEY_STOP_ID, stopList.get(i).ID);
                loaderManager.restartLoader(i, b, this);
            }

        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        searchedWords = getArguments().getString(WORDS_SEARCHED);

    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        //The id will be the position of the element in the list

        Uri.Builder builder = new Uri.Builder();
        String stopID = args.getString(KEY_STOP_ID);
        //Log.d("StopListLoader","Creating loader for stop "+stopID+" in position: "+id);
        if(stopID!=null) {
            builder.scheme("content").authority(AppDataProvider.AUTHORITY)
                    .appendPath("stop").appendPath(stopID);
             CursorLoader cursorLoader = new CursorLoader(getContext(),builder.build(),dataProjection,null,null,null);
            return cursorLoader;
        } else return null;

    }


    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        //check that we have valid data
        if(data==null) return;
        final int numRows = data.getCount();
        final int elementIdx = loader.getId();

        if (numRows==0) {
            Log.w(this.getClass().getName(),"No  info for stop in position "+elementIdx);
            return;
        } else if(numRows>1){
            Log.d("StopLoading","we have "+numRows+" rows, should only have 1. Taking the first...");
        }
        final int linesIndex = data.getColumnIndex(StopsTable.COL_LINES_STOPPING);
        data.moveToFirst();
        Stop stopToModify = stopList.get(elementIdx);
        final String linesStopping = data.getString(linesIndex);
        stopToModify.setRoutesThatStopHere(Arrays.asList(linesStopping.split(",")));
        try {
            final String possibleLocation = data.getString(data.getColumnIndexOrThrow(StopsTable.COL_LOCATION));

            if (stopToModify.location == null && possibleLocation != null && !possibleLocation.isEmpty() && !possibleLocation.equals("_")) {
                stopToModify.location = possibleLocation;
            }
            if (stopToModify.type == null) {
                stopToModify.type = Route.Type.fromCode(data.getInt(data.getColumnIndex(StopsTable.COL_TYPE)));
            }
        }catch (IllegalArgumentException arg){
            if(arg.getMessage().contains("'location' does not exist")) Log.w("StopLoading","stop with no location found");
        }
        //Log.d("StopListFragmentLoader","Finished parsing data for stop in position "+elementIdx);
        mListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        loader.abandon();
    }
}
