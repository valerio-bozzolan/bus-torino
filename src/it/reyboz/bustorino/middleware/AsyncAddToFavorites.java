/*
	BusTO  - Middleware components
    Copyright (C) 2016 Fabio Mazza

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
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.widget.Toast;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.middleware.UserDB;

public class AsyncAddToFavorites extends AsyncTask<Stop, Void, Boolean> {
    private Context c;
    public AsyncAddToFavorites(Context c) {
        this.c = c.getApplicationContext();
    }

    @Override
    protected Boolean doInBackground(Stop... stops) {
        boolean result;
        if(stops[0]!=null) {
            UserDB userDatabase = new UserDB(c);
            SQLiteDatabase db = userDatabase.getWritableDatabase();
            result = UserDB.addOrUpdateStop(stops[0], db);
            db.close();
        }
        else result = false;
        return result;
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if(result) {
            Toast.makeText(this.c, R.string.added_in_favorites, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this.c, R.string.cant_add_to_favorites, Toast.LENGTH_SHORT).show();
        }
    }
}
