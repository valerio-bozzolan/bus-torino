/*
	BusTO - Data components
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
package it.reyboz.bustorino.data;

import android.database.sqlite.SQLiteDatabase;
import it.reyboz.bustorino.backend.Result;
import it.reyboz.bustorino.backend.Stop;

import java.util.List;
import java.util.concurrent.Executor;

public class OldDataRepository {

    private final Executor executor;
    private final NextGenDB nextGenDB;

    public OldDataRepository(Executor executor, final NextGenDB nextGenDB) {
        this.executor = executor;
        this.nextGenDB = nextGenDB;
    }

    public void requestStopsWithGtfsIDs(final List<String> gtfsIDs,
                                        final Callback<List<Stop>> callback){
        executor.execute(() -> {

            try {
                //final NextGenDB dbHelper = new NextGenDB(context);
                final SQLiteDatabase db = nextGenDB.getReadableDatabase();

                final List<Stop> stops = NextGenDB.queryAllStopsWithGtfsIDs(db, gtfsIDs);
                //Result<List<Stop>> result = Result.success;

                callback.onComplete(Result.success(stops));
            } catch (Exception e){
                callback.onComplete(Result.failure(e));
            }
        });
    }

    public interface Callback<T>{
        void onComplete(Result<T> result);
    }
}
