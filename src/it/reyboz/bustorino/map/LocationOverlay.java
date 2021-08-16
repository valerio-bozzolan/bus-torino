/*
	BusTO - Map components
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
package it.reyboz.bustorino.map;


import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

public class LocationOverlay extends MyLocationNewOverlay {

    final OverlayCallbacks callbacks;

    public LocationOverlay(MapView mapView, OverlayCallbacks callbacks) {
        super(mapView);
        this.callbacks = callbacks;
    }

    public LocationOverlay(IMyLocationProvider myLocationProvider, MapView mapView, OverlayCallbacks callbacks) {
        super(myLocationProvider, mapView);
        this.callbacks = callbacks;
    }

    @Override
    public void enableFollowLocation() {
        super.enableFollowLocation();
        callbacks.onEnableFollowMyLocation();
    }

    @Override
    public void disableFollowLocation() {

        super.disableFollowLocation();
        callbacks.onDisableFollowMyLocation();
    }

    public interface OverlayCallbacks{
        /**
         * Called right after disableFollowMyLocation
         */
        void onDisableFollowMyLocation();

        /**
         * Called right after enableFollowMyLocation
         */
        void onEnableFollowMyLocation();
    }
}
