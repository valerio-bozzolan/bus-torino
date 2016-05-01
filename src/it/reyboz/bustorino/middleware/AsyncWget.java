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

import android.content.Context;
import android.os.AsyncTask;
import android.widget.Toast;

import it.reyboz.bustorino.R;

/**
 * Call this.execute() right after the constructor. Or in the constructor itself, if you're
 * overriding it (also call super() constructor in that case).
 */
public abstract class AsyncWget<FetcherKind> extends AsyncTask<Void, Integer, Void> {
    protected RecursionHelper<FetcherKind> r;
    /**
     * True: every fetcher failed in some way
     * False: at least one fetcher succeeded and loop was terminated
     */
    protected boolean failedAll = false;
    /**
     * True: recursion\loop has terminated naturally
     * False: task has been cancelled
     */
    protected boolean terminated = false;

    protected final Context c;

    /**
     * Try every fetcher until you get something usable.<br>
     */
    public AsyncWget(Context c, RecursionHelper<FetcherKind> r) {
        r.reset();

        this.r = r;
        this.c = c;
    }

    @Override
    protected Void doInBackground(Void... useless) {
        while(r.valid()) {
            if(this.isCancelled()) {
                return null;
            }

            if(this.tryFetcher(r.getAndMoveForward())) {
                this.terminated = true;
                break;
            }
        }

        // everything failed?
        if(!this.terminated) {
            // well, natural termination was reached, so set "terminated" to true...
            this.terminated = true;
            this.failedAll = true;
        }

        return null;
    }

    /**
     * Override this!
     *
     * @param f fetcher
     * @return true if succeeded and want to terminate, false if failed and want to try next fetcher
     */
    abstract protected boolean tryFetcher(FetcherKind f);

    /**
     * Shows a toast.
     *
     * @param toastz resources from R, containing a message
     */
    @Override
    protected void onProgressUpdate(Integer... toastz) {
        for(int i: toastz) {
            Toast.makeText(this.c, i, Toast.LENGTH_SHORT).show();
        }
    }

}