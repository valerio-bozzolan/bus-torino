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
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.data.AppDataProvider;
import it.reyboz.bustorino.data.UserDB;

/**
 * Handler to add or remove or toggle a Stop in your favorites
 */
public class AsyncStopFavoriteAction extends AsyncTask<Stop, Void, Boolean> {
    private final Context context;
    private final Uri FAVORITES_URI = AppDataProvider.getUriBuilderToComplete().appendPath(
            AppDataProvider.FAVORITES).build();

    /**
     * Kind of actions available
     */
    public enum Action { ADD, REMOVE, TOGGLE , UPDATE};

    /**
     * Action chosen
     *
     * Note that TOGGLE is not converted to ADD or REMOVE.
     */
    private Action action;

    // extra stuff to do after we've done it
    private ResultListener listener;
    /**
     * Constructor
     *
     * @param context
     * @param action
     */
    public AsyncStopFavoriteAction(Context context, Action action, ResultListener listener) {
        this.context = context.getApplicationContext();
        this.action = action;
        this.listener = listener;
    }

    @Override
    protected Boolean doInBackground(Stop... stops) {
        boolean result = false;

        Stop stop = stops[0];

        // check if the request has sense
        if(stop != null) {

            // get a writable database
            UserDB userDatabase = new UserDB(context);
            SQLiteDatabase db = userDatabase.getWritableDatabase();

            // eventually toggle the status
            if(Action.TOGGLE.equals(action)) {
                if(UserDB.isStopInFavorites(db, stop.ID)) {
                    action = Action.REMOVE;
                } else {
                    action = Action.ADD;
                }
            }

            // at this point the action is just ADD or REMOVE

            // add or remove?
            if(Action.ADD.equals(action)) {
                // add
                result = UserDB.addOrUpdateStop(stop, db);
            } else if (Action.UPDATE.equals(action)){

                result = UserDB.updateStop(stop, db);
            } else {
                // remove
                result = UserDB.deleteStop(stop, db);
            }

            // please sir, close the door
            db.close();
        }

        return result;
    }

    /**
     * Callback fired when everything was done
     *
     * @param result
     */
    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);

        if(result) {
            UserDB.notifyContentProvider(context);
            // at this point the action should be just ADD or REMOVE
            if(Action.ADD.equals(action)) {
                // now added
                Toast.makeText(this.context, R.string.added_in_favorites, Toast.LENGTH_SHORT).show();
            } else if (Action.REMOVE.equals(action)) {
                // now removed
                Toast.makeText(this.context, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show();
            }
        } else {
            // wtf
            Toast.makeText(this.context, R.string.cant_add_to_favorites, Toast.LENGTH_SHORT).show();
        }
        listener.doStuffWithResult(result);
        Log.d("BusTO FavoritesAction", "Action "+action+" completed");
    }

    public interface ResultListener{
        /**
         * Do what you need to to update the UI with the result
         * @param result true if the action is done
         */
        void doStuffWithResult(Boolean result);
    }

}
