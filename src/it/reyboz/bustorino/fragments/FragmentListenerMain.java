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

import it.reyboz.bustorino.backend.Stop;

public interface FragmentListenerMain extends CommonFragmentListener {

    void toggleSpinner(boolean state);


    /*
        Unused method
     * Add the last successfully searched stop to the favorites
     */

    //void toggleLastStopToFavorites();


    /**
     * Tell activity that we need to enable/disable the refreshLayout
     * @param yes or no
     */
    void enableRefreshLayout(boolean yes);
}