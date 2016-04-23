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

import it.reyboz.bustorino.backend.ArrivalsFetcher;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.Palina;

import android.os.AsyncTask;
import android.support.annotation.NonNull;

import java.util.concurrent.atomic.AtomicReference;

public class AsyncArrivalsFetcherAll extends AsyncTask<Void, Void, Palina> {
    private final ArrivalsFetcher af;
    private AtomicReference<Fetcher.result> result;
    private final String stopID;
    private final AsyncArrivalsFetcherAllCallback callback;

    public AsyncArrivalsFetcherAll(@NonNull ArrivalsFetcher af, @NonNull String stopID, @NonNull AsyncArrivalsFetcherAllCallback callback) {
        this.af = af;
        this.stopID = stopID;
        this.callback = callback;
        this.result = new AtomicReference<>();
    }

    @Override protected Palina doInBackground(Void... useless) {
        return af.ReadArrivalTimesAll(this.stopID, this.result);
    }

    @Override protected void onPostExecute(Palina p) {
        if(!this.isCancelled()) { // is this really needed?
            this.callback.call(p, result.get(), this.stopID);
        }
    }

    /**
     * Android doesn't really support JDK 8, so we have to use this hack\workaround.<br>
     * <br>
     * I've already tested 4 different solutions, this is the cleanest-looking so far...<br>
     * <br>
     * The only purpose of this interface is to glue together the AsyncArrivalsFetcherAll and whatver ActivityMain needs to do after obraining results, change parameters liberally if needed.
     */
    public interface AsyncArrivalsFetcherAllCallback {
        void call(Palina p, Fetcher.result res, String stopID);
    }
}