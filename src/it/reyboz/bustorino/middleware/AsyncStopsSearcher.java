/*
	BusTO (middleware)
    Copyright (C) 2021 Fabio Mazza

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

import android.os.AsyncTask;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopsFinderByName;
import it.reyboz.bustorino.fragments.FragmentHelper;

public class AsyncStopsSearcher extends AsyncTask<String, Fetcher.Result, List<Stop>> {

    private static final String TAG = "BusTO-StopsSearcher";
    private static final String DEBUG_TAG = TAG;
    private final StopsFinderByName[] fetchers;
    private final AtomicReference<Fetcher.Result> res;

    private WeakReference<FragmentHelper> helperWR;

    private String theQuery;

    public AsyncStopsSearcher(FragmentHelper fh, StopsFinderByName[] fetchers) {
        this.fetchers = fetchers;
        if (fetchers.length < 1){
            throw new IllegalArgumentException("You have to put at least one Fetcher, idiot!");
        }

        this.res = new AtomicReference<>();
        this.helperWR = new WeakReference<>(fh);
        fh.setLastTaskRef(this);

    }

    @Override
    protected List<Stop> doInBackground(String... strings) {
        RecursionHelper<StopsFinderByName> r = new RecursionHelper<>(fetchers);
        if (helperWR.get()==null || strings.length == 0)
            return null;
        Log.d(DEBUG_TAG,"Running with query "+strings[0]);

        ArrayList<Fetcher.Result> results = new ArrayList<>();
        List<Stop> resultsList;
        while (r.valid()){
            if (this.isCancelled()) return null;

            final StopsFinderByName finder = r.getAndMoveForward();
            theQuery = strings[0].trim();
            resultsList = finder.FindByName(theQuery, res);
            Log.d(DEBUG_TAG, "Result: "+res.get()+", "+resultsList.size()+" stops");

            if (res.get()== Fetcher.Result.OK){
                return resultsList;
            }
            results.add(res.get());
        }
        boolean emptyResults = true;
        for (Fetcher.Result re: results){
            if (!re.equals(Fetcher.Result.EMPTY_RESULT_SET)) {
                emptyResults = false;
                break;
            }
        }
        if(emptyResults){
            publishProgress(Fetcher.Result.EMPTY_RESULT_SET);
        }
        return new ArrayList<>();
    }

    @Override
    protected void onProgressUpdate(Fetcher.Result... values) {
        FragmentHelper fh = helperWR.get();
        if (fh!=null)
            for (Fetcher.Result r : values){
                fh.showErrorMessage(r, SearchRequestType.STOPS);
            }
        else {
            Log.w(TAG,"We had to show some progress but activity was destroyed");
        }
    }
    @Override
    protected void onCancelled() {
        FragmentHelper fh = helperWR.get();
        if (fh!=null) fh.toggleSpinner(false);
    }

    @Override
    protected void onPreExecute() {
        FragmentHelper fh = helperWR.get();
        if (fh!=null) fh.toggleSpinner(true);
    }

    @Override
    protected void onPostExecute(List<Stop> stops) {
        final FragmentHelper fh = helperWR.get();

        if (stops==null || fh==null || theQuery==null) {
            if (fh!=null) fh.toggleSpinner(false);
            cancel(true);
            return;
        }
        if(isCancelled()){
            fh.toggleSpinner(false);
            return;
        }
        fh.createStopListFragment(stops, theQuery, true);


    }
}
