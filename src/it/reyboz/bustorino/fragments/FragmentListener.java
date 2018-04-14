/*
	BusTO  - Fragments components
    Copyright (C) 2018 Fabio Mazza

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
package it.reyboz.bustorino.fragments;

import it.reyboz.bustorino.backend.Palina;

public interface FragmentListener {
    void toggleSpinner(boolean state);
    /**
     * Sends the message to the activity to adapt the GUI
     * to the fragment that has been attached
     * @param fragmentType the type of fragment attached
     */
    void readyGUIfor(String fragmentType);
    /**
     * Houston, we need another fragment!
     *
     * @param ID the Stop ID
     */
    void createFragmentForStop(String ID);

    void addLastStopToFavorites();

    /**
     * Tell the activity that we need to disable/enable its floatingActionButton
     * @param yes or no
     */
    void showFloatingActionButton(boolean yes);

    /**
     * Tell activity that we need to enable/disable the refreshLayout
     * @param yes or no
     */
    void enableRefreshLayout(boolean yes);
}
