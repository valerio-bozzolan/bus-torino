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

import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.middleware.StopAdapter;
import it.reyboz.bustorino.middleware.UserDB;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
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

import java.util.List;

public class ActivityFavorites extends AppCompatActivity {
    private ListView favoriteListView;

    private EditText bus_stop_name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        ActionBar ab = getSupportActionBar();
        assert ab != null;
        ab.setIcon(R.drawable.ic_launcher);
        ab.setDisplayHomeAsUpEnabled(true); // Back button

        favoriteListView = (ListView) findViewById(R.id.favoriteListView);

        createFavoriteList();
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
                // TODO: implement
                //busStop.setIsFavorite(false);
                //UserDB.DBBusStop.addBusStop(db, busStop);
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
        UserDB userDB = new UserDB(getApplicationContext());
        SQLiteDatabase db = userDB.getReadableDatabase();
        List<Stop> busStops = UserDB.getFavorites(db);
        // TODO: close connection

        // If no data is found show a friendly message
        if (busStops.size() == 0) {
            favoriteListView.setVisibility(View.INVISIBLE);
            TextView favoriteTipTextView = (TextView) findViewById(R.id.favoriteTipTextView);
            assert favoriteTipTextView != null;
            favoriteTipTextView.setVisibility(View.VISIBLE);
        }

        // Show results
        favoriteListView.setAdapter(new StopAdapter(this, busStops));
        favoriteListView
                .setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        /**
                         * Casting because of Javamerda
                         * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
                         */
                        Stop busStop = (Stop) parent.getItemAtPosition(position);

                        Intent intent = new Intent(ActivityFavorites.this,
                                ActivityMain.class);

                        Bundle b = new Bundle();
                        b.putString("bus-stop-ID", busStop.ID);
                        intent.putExtras(b);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK
                                | Intent.FLAG_ACTIVITY_NEW_TASK);

                        startActivity(intent);

                        finish();
                    }
                });
        registerForContextMenu(favoriteListView);
    }

    private class BusStopUsernameOnClickListener implements DialogInterface.OnClickListener {
        private Stop busStop;

        BusStopUsernameOnClickListener(Stop busStop) {
            this.busStop = busStop;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {

        }
    }

    public void showBusStopUsernameInputDialog(Stop busStop) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = this.getLayoutInflater();
        View renameDialogLayout = inflater.inflate(R.layout.rename_dialog, null);

        bus_stop_name = (EditText) renameDialogLayout.findViewById(R.id.rename_dialog_bus_stop_name);
        bus_stop_name.setText(busStop.getStopName());
        // TODO: default name as hint
        //bus_stop_name.setHint(busStop.getBusStopName());

        builder.setTitle(getString(R.string.dialog_rename_bus_stop_username_title));
        builder.setView(renameDialogLayout);
        builder.setPositiveButton(getString(android.R.string.ok), new BusStopUsernameOnClickListener(busStop) {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String busStopUsername = bus_stop_name.getText().toString();
                if (busStopUsername.length() == 0) {
                    busStopUsername = null;
                }

                //super.busStop.setBusStopUsername(busStopUsername);
                // TODO: implement
                //UserDB.DBBusStop.addBusStop(db, super.busStop, DBBusStop.FORCE_NULL_BUSSTOP_USERNAME);

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
                //super.busStop.setBusStopUsername(null);
                // TODO: implement
                //UserDB.DBBusStop.addBusStop(db, super.busStop, DBBusStop.FORCE_NULL_BUSSTOP_USERNAME);

                createFavoriteList();
            }
        });
        builder.show();
    }
}
