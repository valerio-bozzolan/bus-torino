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

import it.reyboz.bustorino.lab.GTTSiteSucker.BusStop;
import it.reyboz.bustorino.lab.MyDB;
import it.reyboz.bustorino.lab.MyDB.DBBusStop;
import it.reyboz.bustorino.lab.adapters.AdapterBusStops;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

public class ActivityFavorites extends ActionBarActivity {
    private ListView favoriteListView;

    private SQLiteDatabase db;

    private EditText bus_stop_name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        getSupportActionBar().setIcon(R.drawable.ic_launcher);

        MyDB mDbHelper = new MyDB(this);
        db = mDbHelper.getWritableDatabase();

        favoriteListView = (ListView) findViewById(R.id.favoriteListView);

        // Back button
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        createFavoriteList();

        db.close();
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

        BusStop busStop = (BusStop) favoriteListView.getItemAtPosition(info.position);

        switch (item.getItemId()) {
            case R.id.action_favourite_entry_delete:
                busStop.setIsFavorite(false);
                MyDB.DBBusStop.addBusStop(db, busStop);
                createFavoriteList();
                return true;
            case R.id.action_rename_bus_stop_username:
                showBusStopUsernameInputDialog(busStop);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    void createFavoriteList() {
        BusStop[] busStops = MyDB.DBBusStop.getFavoriteBusStops(db);

        // If no data is found show a friendly message
        if (busStops.length == 0) {
            favoriteListView.setVisibility(View.INVISIBLE);
            TextView favoriteTipTextView = (TextView) findViewById(R.id.favoriteTipTextView);
            favoriteTipTextView.setVisibility(View.VISIBLE);
        }

        // Show results
        favoriteListView.setAdapter(new AdapterBusStops(this, R.layout.entry_bus_stop, busStops));
        favoriteListView
                .setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        /**
                         * Casting because of Javamerda
                         * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
                         */
                        BusStop busStop = (BusStop) parent.getItemAtPosition(position);

                        Intent intent = new Intent(ActivityFavorites.this,
                                ActivityMain.class);

                        Bundle b = new Bundle();
                        b.putString("bus-stop-ID", busStop.getBusStopID());
                        intent.putExtras(b);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                                | Intent.FLAG_ACTIVITY_NEW_TASK);

                        Log.d("FavoritesActivity", "Tapped on bus stop: "
                                + busStop.getBusStopID());

                        startActivity(intent);

                        finish();
                    }
                });
        registerForContextMenu(favoriteListView);
    }

    private class BusStopUsernameOnClickListener implements DialogInterface.OnClickListener {
        private BusStop busStop;

        BusStopUsernameOnClickListener(BusStop busStop) {
            this.busStop = busStop;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {

        }
    }

    public void showBusStopUsernameInputDialog(BusStop busStop) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = this.getLayoutInflater();
        View renameDialogLayout = inflater.inflate(R.layout.rename_dialog, null);

        bus_stop_name = (EditText) renameDialogLayout.findViewById(R.id.rename_dialog_bus_stop_name);
        bus_stop_name.setText(busStop.getBusStopUsername());
        bus_stop_name.setHint(busStop.getBusStopName());

        builder.setTitle(getString(R.string.dialog_rename_bus_stop_username_title));
        builder.setView(renameDialogLayout);
        builder.setPositiveButton(getString(android.R.string.ok), new BusStopUsernameOnClickListener(busStop) {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String busStopUsername = bus_stop_name.getText().toString();
                if (busStopUsername.length() == 0) {
                    busStopUsername = super.busStop.getBusStopName();
                }

                super.busStop.setBusStopUsername(busStopUsername);
                MyDB.DBBusStop.addBusStop(db, super.busStop);

                createFavoriteList();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setNeutralButton(R.string.dialog_rename_bus_stop_username_reset_button, new BusStopUsernameOnClickListener(busStop) {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                super.busStop.setBusStopUsername(null);
                MyDB.DBBusStop.addBusStop(db, super.busStop, DBBusStop.FORCE_NULL_BUSSTOP_USERNAME);

                createFavoriteList();
            }
        });
        builder.show();
    }
}
