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
package it.reyboz.bustorino;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.FragmentTransaction;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.fragments.*;
import it.reyboz.bustorino.middleware.GeneralActivity;

public class ActivityExperiments extends GeneralActivity implements CommonFragmentListener {

    final static String DEBUG_TAG = "ExperimentsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiments);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setIcon(R.drawable.ic_launcher);
        }
        if (savedInstanceState==null) {
            getSupportFragmentManager().beginTransaction()
                    .setReorderingAllowed(true)

                   /* .add(R.id.fragment_container_view, LinesDetailFragment.class,

                            LinesDetailFragment.Companion.makeArgs("gtt:4U"))

                    */
                    //.add(R.id.fragment_container_view, LinesGridShowingFragment.class, null)
                    //.add(R.id.fragment_container_view, IntroFragment.class, IntroFragment.makeArguments(0))
                    //.commit();

                    //.add(R.id.fragment_container_view, LinesDetailFragment.class,
                    //        LinesDetailFragment.Companion.makeArgs("gtt:4U"))
                    .add(R.id.fragment_container_view, TestSavingFragment.class, null)
                    .commit();
        }
    }

    @Override
    public void showFloatingActionButton(boolean yes) {
        Log.d(DEBUG_TAG, "Asked to show the action button");
    }

    @Override
    public void readyGUIfor(FragmentKind fragmentType) {
        Log.d(DEBUG_TAG, "Asked to prepare the GUI for fragmentType "+fragmentType);
    }

    @Override
    public void requestArrivalsForStopID(String ID) {

    }

    @Override
    public void showMapCenteredOnStop(Stop stop) {

    }
    @Override
    public void showLineOnMap(String routeGtfsId){

        readyGUIfor(FragmentKind.LINES);
        FragmentTransaction tr = getSupportFragmentManager().beginTransaction();
        tr.replace(R.id.fragment_container_view, LinesDetailFragment.class,
                LinesDetailFragment.Companion.makeArgs(routeGtfsId));
        tr.addToBackStack("LineonMap-"+routeGtfsId);
        tr.commit();


    }
}