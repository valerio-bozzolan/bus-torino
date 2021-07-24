package it.reyboz.bustorino.data;


import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import it.reyboz.bustorino.BuildConfig;
import it.reyboz.bustorino.backend.Stop;

public class FavoritesLiveData extends LiveData<List<Stop>> implements CustomAsyncQueryHandler.AsyncQueryListener {
    private static final String TAG = "FavoritesLiveData";
    private final boolean notifyChangesDescendants;


    @NonNull
    private final Context mContext;

    @NonNull
    private final FavoritesLiveData.ForceLoadContentObserver mObserver;
    private final CustomAsyncQueryHandler queryHandler;


    private final Uri FAVORITES_URI = AppDataProvider.getUriBuilderToComplete().appendPath(
                AppDataProvider.FAVORITES).build();


    private final int FAV_TOKEN = 23, STOPS_TOKEN_BASE=220;


    @Nullable
    private List<Stop> stopsFromFavorites, stopsDone;

    private boolean isQueryRunning = false;
    private int stopNeededCount = 0;

    public FavoritesLiveData(@NonNull Context context, boolean notifyDescendantsChanges) {
        super();
        mContext = context.getApplicationContext();
        mObserver = new FavoritesLiveData.ForceLoadContentObserver();
        notifyChangesDescendants = notifyDescendantsChanges;
        queryHandler = new CustomAsyncQueryHandler(mContext.getContentResolver(),this);

    }

    private void loadData() {
        loadData(false);
    }
    private static Uri.Builder getStopsBuilder(){
        return AppDataProvider.getUriBuilderToComplete().appendPath("stop");

    }

    private void loadData(boolean forceQuery) {
        Log.d(TAG, "loadData()");

        if (!forceQuery){
            if (getValue()!= null){
                //Data already loaded
                return;
            }
        }
        if (isQueryRunning){
            //we are waiting for data, we will get an update soon
            return;
        }

        isQueryRunning = true;
        queryHandler.startQuery(FAV_TOKEN,null, FAVORITES_URI, UserDB.getFavoritesColumnNamesAsArray, null, null, null);


    }

    @Override
    protected void onActive() {
        //Log.d(TAG, "onActive()");
        loadData();
    }

    /**
     * Clear the data for the cursor
     */
    public void onClear(){

        ContentResolver resolver = mContext.getContentResolver();
        resolver.unregisterContentObserver(mObserver);

    }


    @Override
    protected void setValue(List<Stop> stops) {
        //Log.d("BusTO-FavoritesLiveData","Setting the new values for the stops, have "+
        //       stops.size()+" stops");

        ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(FAVORITES_URI, notifyChangesDescendants,mObserver);

        super.setValue(stops);
    }

    @Override
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (token == FAV_TOKEN) {
            stopsFromFavorites = UserDB.getFavoritesFromCursor(cursor, UserDB.getFavoritesColumnNamesAsArray);
            cursor.close();
            //reset counters
            stopNeededCount = stopsFromFavorites.size();
            stopsDone = new ArrayList<>();
            if(stopsFromFavorites.size() == 0){
                //we don't need to call the other query
                setValue(stopsDone);
            } else
                for (int i = 0; i < stopsFromFavorites.size(); i++) {
                    Stop s = stopsFromFavorites.get(i);
                    queryHandler.startQuery(STOPS_TOKEN_BASE + i, null,
                            getStopsBuilder().appendPath(s.ID).build(),
                            NextGenDB.QUERY_COLUMN_stops_all, null, null, null);
            }



        } else if(token >= STOPS_TOKEN_BASE){
            final int index = token - STOPS_TOKEN_BASE;
            assert stopsFromFavorites != null;
            Stop stopUpdate = stopsFromFavorites.get(index);
            Stop finalStop;

            List<Stop> result = Arrays.asList(NextGenDB.getStopsFromCursorAllFields(cursor));
            cursor.close();
            if (result.size() < 1){
                // stop is not in the DB
                finalStop = stopUpdate;
            } else {
                finalStop = result.get(0);
                if (BuildConfig.DEBUG && !(finalStop.ID.equals(stopUpdate.ID))) {
                    throw new AssertionError("Assertion failed");
                }
                finalStop.setStopUserName(stopUpdate.getStopUserName());
            }
            if (stopsDone!=null)
                stopsDone.add(finalStop);

            stopNeededCount--;
            if (stopNeededCount == 0) {
                // we have finished the queries
                isQueryRunning = false;
                Collections.sort(stopsDone);

                setValue(stopsDone);
            }

        }
    }


    /**
     * Content Observer that forces reload of cursor when data changes
     * On different thread (new Handler)
     */
    public final class ForceLoadContentObserver
            extends ContentObserver {

        public ForceLoadContentObserver() {
            super(new Handler());
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "ForceLoadContentObserver.onChange()");
            loadData(true);
        }

    }


}

