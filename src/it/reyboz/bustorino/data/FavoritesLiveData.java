package it.reyboz.bustorino.data;

import android.annotation.SuppressLint;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContentResolverCompat;
import androidx.core.os.CancellationSignal;
import androidx.core.os.OperationCanceledException;
import androidx.lifecycle.LiveData;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;

import it.reyboz.bustorino.data.NextGenDB.Contract.*;

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


    private final int FAV_TOKEN = 23, STOPS_TOKEN_BASE=90;


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
        Log.d(TAG, "onActive()");
        loadData();
    }

    @Override
    protected void onInactive() {
        Log.d(TAG, "onInactive()");

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

        ContentResolver resolver = mContext.getContentResolver();
        resolver.registerContentObserver(FAVORITES_URI, notifyChangesDescendants,mObserver);

        super.setValue(stops);
    }

    @Override
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (token == FAV_TOKEN) {
            stopsFromFavorites = UserDB.getFavoritesFromCursor(cursor, UserDB.getFavoritesColumnNamesAsArray);
            cursor.close();

            for (int i = 0; i < stopsFromFavorites.size(); i++) {
                Stop s = stopsFromFavorites.get(i);
                queryHandler.startQuery(STOPS_TOKEN_BASE + i, null, getStopsBuilder().appendPath(s.ID).build(),
                        NextGenDB.QUERY_COLUMN_stops_all, null, null, null);
            }
            stopNeededCount = stopsFromFavorites.size();
            stopsDone = new ArrayList<>();


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
            } else{
                finalStop = result.get(0);
                assert  (finalStop.ID.equals(stopUpdate.ID));
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

