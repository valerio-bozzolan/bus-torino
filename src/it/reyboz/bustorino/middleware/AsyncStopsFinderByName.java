/*
	BusTO (backend components)
    Copyright (C) 2016 Ludovico Pavesi

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
import android.support.annotation.NonNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import it.reyboz.bustorino.backend.ArrivalsFetcher;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.StopsDBInterface;
import it.reyboz.bustorino.backend.StopsFinderByName;

public class AsyncStopsFinderByName extends AsyncTask<Void, Void, List<Stop>> {
    private final StopsFinderByName sf;
    private AtomicReference<Fetcher.result> result;
    private final StopsDB db;
    private final String query;
    private final AsyncStopsFinderByNameCallback callback;

    /**
     * Run a StopsFinderByName in a background thread
     *
     * @param sf some concrete implementation of StopsFinderByName
     * @param query the string to search for
     * @param callback callback (you don't say)
     * @param sdb StopsDB
     */
    public AsyncStopsFinderByName(@NonNull StopsFinderByName sf, @NonNull String query, @NonNull AsyncStopsFinderByNameCallback callback, @NonNull StopsDB sdb) {
        this.sf = sf;
        this.query = query;
        this.callback = callback;
        this.result = new AtomicReference<>();
        this.db = sdb;
    }

    @Override protected List<Stop> doInBackground(Void... useless) {
        this.db.openIfNeeded();
        List<Stop> ret = sf.FindByName(this.query, this.db, this.result);
        this.db.closeIfNeeded();
        return ret;
    }

    @Override protected void onPostExecute(List<Stop> stops) {
        if(!this.isCancelled()) {
            this.callback.call(stops, result.get(), this.query);
        }
    }

    public interface AsyncStopsFinderByNameCallback {
        void call(List<Stop> stops, Fetcher.result res, String query);
    }
}