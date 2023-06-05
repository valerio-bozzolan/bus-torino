/*
	BusTO - Map components
    Copyright (C) 2023 Fabio Mazza

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
package it.reyboz.bustorino.map

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.View.*
import android.widget.TextView
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.gtfs.GtfsPositionUpdate
import it.reyboz.bustorino.backend.gtfs.GtfsUtils
import it.reyboz.bustorino.data.gtfs.GtfsTrip
import it.reyboz.bustorino.data.gtfs.MatoPattern
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow

@SuppressLint("ClickableViewAccessibility")
class BusInfoWindow(map: MapView,
                    val update: GtfsPositionUpdate,
                    var pattern: MatoPattern?,
                    private val touchUp: onTouchUp):
    BasicInfoWindow(R.layout.bus_info_window,map) {

    init {
        mView.setOnTouchListener { view, motionEvent ->
            touchUp.onActionUp()
            close()
            //mView.performClick()
            true

        }
    }

    override fun onOpen(item: Any?) {
       // super.onOpen(item)
        val titleView = mView.findViewById<TextView>(R.id.businfo_title)
        val descrView = mView.findViewById<TextView>(R.id.businfo_description)
        val subdescrView = mView.findViewById<TextView>(R.id.businfo_subdescription)

        val nameRoute =  GtfsUtils.getLineNameFromGtfsID(update.routeID)
        titleView.text = (mView.resources.getString(R.string.line_fill, nameRoute)
                )
        subdescrView.text = update.vehicleInfo.label


        if(pattern!=null){
            descrView.text = pattern!!.headsign
            descrView.visibility = VISIBLE
        } else{
            descrView.visibility = GONE
        }
    }
    fun setPatternAndDraw(pattern: MatoPattern?){
        if(pattern==null){
            return
        }
        this.pattern = pattern
        if(isOpen){
            onOpen(pattern)
        }
    }

    fun interface onTouchUp{
        fun onActionUp()
    }
}