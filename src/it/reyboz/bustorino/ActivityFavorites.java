/*
	BusTO - Arrival times for Turin public transports.
    Copyright (C) 2014  Valerio Bozzolan

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
package it.reyboz.bustorino;

import androidx.lifecycle.ViewModelProvider;

import android.widget.*;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.adapters.StopAdapter;
import it.reyboz.bustorino.data.FavoritesViewModel;
import it.reyboz.bustorino.data.UserDB;
import it.reyboz.bustorino.middleware.AsyncStopFavoriteAction;

import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.core.app.NavUtils;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import java.util.List;

public class ActivityFavorites extends AppCompatActivity {
    private ListView favoriteListView;
    private SQLiteDatabase userDB;
    private EditText busStopNameText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        // this should be done in onStarted and closed in onStop, but apparently onStarted is never run.
        this.userDB = new UserDB(getApplicationContext()).getWritableDatabase();

        ActionBar ab = getSupportActionBar();
        assert ab != null;
        ab.setIcon(R.drawable.ic_launcher);
        ab.setDisplayHomeAsUpEnabled(true); // Back button

        favoriteListView = (ListView) findViewById(R.id.favoriteListView);
        favoriteListView.setOnItemClickListener((parent, view, position, id) -> {
            /**
             * Casting because of Javamerda
             * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
             */
            Stop busStop = (Stop) parent.getItemAtPosition(position);

            Intent intent = new Intent(ActivityFavorites.this,
                    ActivityMain.class);

            Bundle b = new Bundle();
            // TODO: is passing a serialized object a good idea? Or rather, is it reasonably fast?
            //b.putSerializable("bus-stop-serialized", busStop);
            b.putString("bus-stop-ID", busStop.ID);
            b.putString("bus-stop-display-name", busStop.getStopDisplayName());
            intent.putExtras(b);
            //intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            // Intent.FLAG_ACTIVITY_CLEAR_TASK isn't supported in API < 11 and we're targeting API 7...
            intent.setFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

            startActivity(intent);

            finish();
        });
        registerForContextMenu(favoriteListView);

        FavoritesViewModel model = new ViewModelProvider(this).get(FavoritesViewModel.class);
        model.getFavorites().observe(this, this::showStops);

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.favoriteListView) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_favourites_entry, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
                .getMenuInfo();

        Stop busStop = (Stop) favoriteListView.getItemAtPosition(info.position);

        switch (item.getItemId()) {
            case R.id.action_favourite_entry_delete:
                new AsyncStopFavoriteAction(getApplicationContext(), AsyncStopFavoriteAction.Action.REMOVE,
                        new AsyncStopFavoriteAction.ResultListener() {
                            @Override
                            public void doStuffWithResult(Boolean result) {

                            }
                        }).execute(busStop);

                return true;

            case R.id.action_rename_bus_stop_username:
                showBusStopUsernameInputDialog(busStop);
                return true;
            case R.id.action_view_on_map:
                final String theGeoUrl = busStop.getGeoURL();
                if(theGeoUrl==null){
                    //doesn't have a position
                    Toast.makeText(getApplicationContext(),R.string.cannot_show_on_map_no_position,Toast.LENGTH_SHORT).show();
                    return true;
                }

                // start ActivityMap with these extras in intent
                Intent intent = new Intent(ActivityFavorites.this, ActivityMap.class);
                Bundle b = new Bundle();
                double lat, lon;
                if (busStop.getLatitude()!=null)
                    lat = busStop.getLatitude();
                else lat = 200;
                if (busStop.getLongitude()!=null)
                    lon = busStop.getLongitude();
                else lon = 200;
                b.putDouble("lat", lat);
                b.putDouble("lon",lon);
                b.putString("name", busStop.getStopDefaultName());
                b.putString("ID", busStop.ID);
                intent.putExtras(b);

                startActivity(intent);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }


    void showStops(List<Stop> busStops){
        // If no data is found show a friendly message
        if (busStops.size() == 0) {
            favoriteListView.setVisibility(View.INVISIBLE);
            TextView favoriteTipTextView = (TextView) findViewById(R.id.favoriteTipTextView);
            assert favoriteTipTextView != null;
            favoriteTipTextView.setVisibility(View.VISIBLE);
            ImageView angeryBusImageView = (ImageView) findViewById(R.id.angeryBusImageView);
            angeryBusImageView.setVisibility(View.VISIBLE);
        }
        /* There's a nice method called notifyDataSetChanged() to avoid building the ListView
         * all over again. This method exists in a billion answers on Stack Overflow, but
         * it's nowhere to be seen around here, Android Studio can't find it no matter what.
         * Anyway, it only works from Android 2.3 onward (which is why it refuses to appear, I
         * guess) and requires to modify the list with .add() and .clear() and some other
         * methods, so to update a single stop we need to completely rebuild the list for no
         * reason. It would probably end up as "slow" as throwing away the old ListView and
         * redrwaing everything.
         */

        // Show results
        favoriteListView.setAdapter(new StopAdapter(this, busStops));
    }

    public void showBusStopUsernameInputDialog(final Stop busStop) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = this.getLayoutInflater();
        View renameDialogLayout = inflater.inflate(R.layout.rename_dialog, null);

        busStopNameText = (EditText) renameDialogLayout.findViewById(R.id.rename_dialog_bus_stop_name);
        busStopNameText.setText(busStop.getStopDisplayName());
        busStopNameText.setHint(busStop.getStopDefaultName());

        builder.setTitle(getString(R.string.dialog_rename_bus_stop_username_title));
        builder.setView(renameDialogLayout);
        builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String busStopUsername = busStopNameText.getText().toString();
                String oldUserName = busStop.getStopUserName();

                // changed to none
                if(busStopUsername.length() == 0) {
                    // unless it was already empty, set new
                    if(oldUserName != null) {
                        busStop.setStopUserName(null);
                        //UserDB.updateStop(busStop, userDB);
                        //UserDB.notifyContentProvider(getApplicationContext());
                    }
                } else { // changed to something
                    // something different?
                    if(!busStopUsername.equals(oldUserName)) {
                        busStop.setStopUserName(busStopUsername);
                        //UserDB.updateStop(busStop, userDB);
                        //UserDB.notifyContentProvider(getApplicationContext());
                    }
                }
                new AsyncStopFavoriteAction(getApplicationContext(), AsyncStopFavoriteAction.Action.UPDATE,
                        new AsyncStopFavoriteAction.ResultListener() {
                            @Override
                            public void doStuffWithResult(Boolean result) {
                                //Toast.makeText(getApplicationContext(), R.string.tip_add_favorite, Toast.LENGTH_SHORT).show();
                            }
                        }).execute(busStop);

            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setNeutralButton(R.string.dialog_rename_bus_stop_username_reset_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // delete user name from database
                busStop.setStopUserName(null);
                UserDB.updateStop(busStop, userDB);

            }
        });
        builder.show();
    }

    /**
     * This one runs. onStart instead gets ignored for no reason whatsoever.
     *
     * @see <a href="https://i.stack.imgur.com/SAX9I.png">Android Activity Lifecycle</a>
     */
    @Override
    protected void onStop() {
        super.onStop();
        this.userDB.close();
    }
/*
    private class AsyncGetFavorites extends AsyncTask<Void, Void, List<Stop>> {
        private Context c;
        private SQLiteDatabase userDB;

        AsyncGetFavorites(Context c, SQLiteDatabase userDB) {
            this.c = c;
            this.userDB = userDB;
        }

        @Override
        protected List<Stop> doInBackground(Void... voids) {
            StopsDB stopsDB = new StopsDB(c);
            stopsDB.openIfNeeded();
            List<Stop> busStops = UserDB.getFavorites(this.userDB, stopsDB);
            stopsDB.closeIfNeeded();

            return busStops;
        }

        @Override
        protected void onPostExecute(List<Stop> busStops) {

        }
    }
*/

}
