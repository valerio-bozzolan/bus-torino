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
import android.view.View.*
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginEnd
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.gtfs.GtfsUtils
import it.reyboz.bustorino.backend.utils
import it.reyboz.bustorino.data.gtfs.MatoPattern
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.BasicInfoWindow

@SuppressLint("ClickableViewAccessibility")
class BusInfoWindow(map: MapView,
                    private val routeName: String,
                    private val vehicleLabel: String,
                    var pattern: MatoPattern?,
                    val showClose: Boolean,
                    private val touchUp: onTouchUp
    ):
    BasicInfoWindow(R.layout.bus_info_window,map) {

    init {
        mView.setOnTouchListener { view, motionEvent ->
            touchUp.onActionUp(pattern)
            close()
            //mView.performClick()
            true

        }
    }
    constructor(map: MapView, update: LivePositionUpdate, pattern: MatoPattern?, showClose: Boolean, touchUp: onTouchUp, ):
            this(map,
                GtfsUtils.getLineNameFromGtfsID(update.routeID),
                update.vehicle,
                pattern,
                showClose,
                touchUp
                )


    override fun onOpen(item: Any?) {
       // super.onOpen(item)
        val titleView = mView.findViewById<TextView>(R.id.businfo_title)
        val descrView = mView.findViewById<TextView>(R.id.businfo_description)
        val subdescrView = mView.findViewById<TextView>(R.id.businfo_subdescription)

        val iconClose = mView.findViewById<ImageView>(R.id.closeIcon)

        //val nameRoute =  GtfsUtils.getLineNameFromGtfsID(update.lineGtfsId)

        titleView.text = (mView.resources.getString(R.string.line_fill, routeName)
                )
        subdescrView.text = vehicleLabel


        if(pattern!=null){
            descrView.text = pattern!!.headsign
            descrView.visibility = VISIBLE
        } else{
            descrView.visibility = GONE
        }
        if(!showClose){
            iconClose.visibility = GONE
            val ctx = titleView.context
            val layPars = (titleView.layoutParams as ConstraintLayout.LayoutParams).apply {
                marginStart= 0 //utils.convertDipToPixelsInt(ctx, 8.0)//8.dpToPixels()
                topMargin=utils.convertDipToPixelsInt(ctx, 4.0)
                marginEnd=0
                bottomMargin=0
            }
            //titleView.layoutParams = layPars
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
        fun onActionUp(pattern: MatoPattern?)
    }
}