/*
	BusTO (middleware)
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
package it.reyboz.bustorino.middleware;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import android.util.Log;

import it.reyboz.bustorino.backend.*;
import it.reyboz.bustorino.data.AppDataProvider;
import it.reyboz.bustorino.data.NextGenDB;
import it.reyboz.bustorino.fragments.FragmentHelper;
import it.reyboz.bustorino.data.NextGenDB.Contract.*;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Calendar;

/**
 * This should be used to download data, but not to display it
 */
public class AsyncDataDownload extends AsyncTask<String,Fetcher.result,Object>{

    private static final String TAG = "BusTO-DataDownload";
    private boolean failedAll = false;

    private final AtomicReference<Fetcher.result> res;
    private final RequestType t;
    private String query;
    WeakReference<FragmentHelper> helperRef;
    private final ArrayList<Thread> otherActivities = new ArrayList<>();
    private final Fetcher[] theFetchers;
    private Context context;


    public AsyncDataDownload(FragmentHelper fh, @NonNull Fetcher[] fetchers, Context context) {
        RequestType type;
        helperRef = new WeakReference<>(fh);
        fh.setLastTaskRef(new WeakReference<>(this));
        res = new AtomicReference<>();
        this.context = context.getApplicationContext();

        theFetchers = fetchers;
        if (theFetchers.length < 1){
            throw new IllegalArgumentException("You have to put at least one Fetcher, idiot!");
        }
        if (theFetchers[0] instanceof ArrivalsFetcher){
            type = RequestType.ARRIVALS;
        } else if (theFetchers[0] instanceof StopsFinderByName){
            type = RequestType.STOPS;
        } else{
            type = null;
        }
        t = type;

    }

    @Override
    protected Object doInBackground(String... params) {
        RecursionHelper<Fetcher> r = new RecursionHelper<>(theFetchers);
        boolean success=false;
        Object result;
        FragmentHelper fh = helperRef.get();
        //If the FragmentHelper is null, that means the activity doesn't exist anymore
        if (fh == null){
            return null;
        }

        //Log.d(TAG,"refresh layout reference is: "+fh.isRefreshLayoutReferenceTrue());
        while(r.valid()) {
            if(this.isCancelled()) {
                return null;
            }
            //get the data from the fetcher
            switch (t){
                case ARRIVALS:
                    ArrivalsFetcher f = (ArrivalsFetcher) r.getAndMoveForward();
                    Log.d(TAG,"Using the ArrivalsFetcher: "+f.getClass());

                    Stop lastSearchedBusStop = fh.getLastSuccessfullySearchedBusStop();
                    Palina p;
                    String stopID;
                    if(params.length>0)
                        stopID=params[0]; //(it's a Palina)
                    else if(lastSearchedBusStop!=null)
                        stopID = lastSearchedBusStop.ID; //(it's a Palina)
                    else {
                        publishProgress(Fetcher.result.QUERY_TOO_SHORT);
                        return null;
                    }
                    //Skip the FiveTAPIFetcher for the Metro Stops because it shows incomprehensible arrival times
                    if(f instanceof FiveTAPIFetcher && Integer.parseInt(stopID)>= 8200)
                        continue;
                    p= f.ReadArrivalTimesAll(stopID,res);
                    publishProgress(res.get());

                    if(f instanceof FiveTAPIFetcher){
                        AtomicReference<Fetcher.result> gres = new AtomicReference<>();
                        List<Route> branches = ((FiveTAPIFetcher) f).getDirectionsForStop(stopID,gres);
                        if(gres.get() == Fetcher.result.OK){
                            p.addInfoFromRoutes(branches);
                            Thread t = new Thread(new BranchInserter(branches, context));
                            t.start();
                            otherActivities.add(t);

                        }
                        //put updated values into Database
                    }

                    if(lastSearchedBusStop != null && res.get()== Fetcher.result.OK) {
                        // check that we don't have the same stop
                        if(lastSearchedBusStop.ID.equals(p.ID)) {
                            // searched and it's the same
                            String sn = lastSearchedBusStop.getStopDisplayName();
                            if(sn != null) {
                                // "merge" Stop over Palina and we're good to go
                                p.mergeNameFrom(lastSearchedBusStop);
                            }
                        }
                    }

                    result = p;
                    //TODO: find a way to avoid overloading the user with toasts
                    break;
                case STOPS:
                    StopsFinderByName finder = (StopsFinderByName) r.getAndMoveForward();

                    List<Stop> resultList= finder.FindByName(params[0], this.res); //it's a List<Stop>
                    Log.d(TAG,"Using the StopFinderByName: "+finder.getClass());
                    query =params[0];
                    result = resultList; //dummy result
                    break;
                default:
                    result = null;
            }
            //find if it went well
            if(res.get()== Fetcher.result.OK) {
                //wait for other threads to finish
                for(Thread t: otherActivities){
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        //do nothing
                    }
                }
                return result;
            }

        }
        //at this point, we are sure that the result has been negative
        failedAll=true;

        return null;
    }

    @Override
    protected void onProgressUpdate(Fetcher.result... values) {
        FragmentHelper fh = helperRef.get();
        if (fh!=null)
        for (Fetcher.result r : values){
            //TODO: make Toast
            fh.showErrorMessage(r);
        }
        else {
            Log.w(TAG,"We had to show some progress but activity was destroyed");
        }
    }

    @Override
    protected void onPostExecute(Object o) {
        FragmentHelper fh = helperRef.get();

        if(failedAll || o == null || fh == null){
            //everything went bad
            if(fh!=null) fh.toggleSpinner(false);
            cancel(true);
            //TODO: send message here
            return;
        }

        if(isCancelled()) return;

        switch (t){
            case ARRIVALS:
                Palina palina = (Palina) o;
                fh.createOrUpdateStopFragment(palina);
                break;
            case STOPS:
                //this should never be a problem
                List<Stop> stopList = (List<Stop>) o;
                if(query!=null && !isCancelled()) {
                    fh.createFragmentFor(stopList,query);
                } else Log.e(TAG,"QUERY NULL, COULD NOT CREATE FRAGMENT");
                break;
            case DBUPDATE:
                break;
        }
    }

    @Override
    protected void onCancelled() {
        FragmentHelper fh = helperRef.get();
        if (fh!=null) fh.toggleSpinner(false);
    }

    @Override
    protected void onPreExecute() {
        FragmentHelper fh = helperRef.get();
        if (fh!=null) fh.toggleSpinner(true);
    }


    public enum RequestType {
        ARRIVALS,STOPS,DBUPDATE
    }

    public class BranchInserter implements Runnable{
        private final List<Route> routesToInsert;

        private final Context context;
        private final NextGenDB nextGenDB;

        public BranchInserter(List<Route> routesToInsert,@NonNull Context con) {
            this.routesToInsert = routesToInsert;
            this.context = con;
            nextGenDB = new NextGenDB(context);
        }

        @Override
        public void run() {
            ContentValues[] values = new ContentValues[routesToInsert.size()];
            ArrayList<ContentValues> connectionsVals = new ArrayList<>(routesToInsert.size()*4);
            long starttime,endtime;
            for (Route r:routesToInsert){
                //if it has received an interrupt, stop
                if(Thread.interrupted()) return;
                //otherwise, build contentValues
                final ContentValues cv = new ContentValues();
                cv.put(BranchesTable.COL_BRANCHID,r.branchid);
                cv.put(LinesTable.COLUMN_NAME,r.getName());
                cv.put(BranchesTable.COL_DIRECTION,r.destinazione);
                cv.put(BranchesTable.COL_DESCRIPTION,r.description);
                for (int day :r.serviceDays) {
                    switch (day){
                        case Calendar.MONDAY:
                            cv.put(BranchesTable.COL_LUN,1);
                            break;
                        case Calendar.TUESDAY:
                            cv.put(BranchesTable.COL_MAR,1);
                            break;
                        case Calendar.WEDNESDAY:
                            cv.put(BranchesTable.COL_MER,1);
                            break;
                        case Calendar.THURSDAY:
                            cv.put(BranchesTable.COL_GIO,1);
                            break;
                        case Calendar.FRIDAY:
                            cv.put(BranchesTable.COL_VEN,1);
                            break;
                        case Calendar.SATURDAY:
                            cv.put(BranchesTable.COL_SAB,1);
                            break;
                        case Calendar.SUNDAY:
                            cv.put(BranchesTable.COL_DOM,1);
                            break;
                    }
                }
                if(r.type!=null) cv.put(BranchesTable.COL_TYPE, r.type.getCode());
                cv.put(BranchesTable.COL_FESTIVO, r.festivo.getCode());

                values[routesToInsert.indexOf(r)] = cv;
                for(int i=0; i<r.getStopsList().size();i++){
                    String stop = r.getStopsList().get(i);
                    final ContentValues connVal = new ContentValues();
                    connVal.put(ConnectionsTable.COLUMN_STOP_ID,stop);
                    connVal.put(ConnectionsTable.COLUMN_ORDER,i);
                    connVal.put(ConnectionsTable.COLUMN_BRANCH,r.branchid);

                    //add to global connVals
                    connectionsVals.add(connVal);
                }
            }
            starttime = System.currentTimeMillis();
            ContentResolver cr = context.getContentResolver();
            try {
                cr.bulkInsert(Uri.parse("content://" + AppDataProvider.AUTHORITY + "/branches/"), values);
                endtime = System.currentTimeMillis();
                Log.d("DataDownload", "Inserted branches, took " + (endtime - starttime) + " ms");
            } catch (SQLException exc){
                Log.e("AsyncDataDownload","Inserting data: some error happened, aborting the database insert");
                exc.printStackTrace();
                return;
            }


            starttime = System.currentTimeMillis();
            ContentValues[] valArr = connectionsVals.toArray(new ContentValues[0]);
            Log.d("DataDownloadInsert","inserting "+valArr.length+" connections");
            int rows = nextGenDB.insertBatchContent(valArr,ConnectionsTable.TABLE_NAME);
            endtime = System.currentTimeMillis();
            Log.d("DataDownload","Inserted connections found, took "+(endtime-starttime)+" ms, inserted "+rows+" rows");
        }
    }
}
