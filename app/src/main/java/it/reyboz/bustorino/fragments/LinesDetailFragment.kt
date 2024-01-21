/*
	BusTO  - Fragments components
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
package it.reyboz.bustorino.fragments

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.reyboz.bustorino.R
import it.reyboz.bustorino.adapters.NameCapitalize
import it.reyboz.bustorino.adapters.StopAdapterListener
import it.reyboz.bustorino.adapters.StopRecyclerAdapter
import it.reyboz.bustorino.backend.FiveTNormalizer
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.backend.gtfs.GtfsUtils
import it.reyboz.bustorino.backend.gtfs.LivePositionUpdate
import it.reyboz.bustorino.backend.gtfs.PolylineParser
import it.reyboz.bustorino.backend.utils
import it.reyboz.bustorino.data.MatoTripsDownloadWorker
import it.reyboz.bustorino.data.PreferencesHolder
import it.reyboz.bustorino.data.gtfs.MatoPattern
import it.reyboz.bustorino.data.gtfs.MatoPatternWithStops
import it.reyboz.bustorino.data.gtfs.TripAndPatternWithStops
import it.reyboz.bustorino.map.*
import it.reyboz.bustorino.map.CustomInfoWindow.TouchResponder
import it.reyboz.bustorino.viewmodels.LinesViewModel
import it.reyboz.bustorino.viewmodels.LivePositionsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.advancedpolyline.MonochromaticPaintList


class LinesDetailFragment() : ScreenBaseFragment() {

    private var lineID = ""
    private lateinit var patternsSpinner: Spinner
    private var patternsAdapter: ArrayAdapter<String>? = null

    //private var patternsSpinnerState: Parcelable? = null

    private lateinit var currentPatterns: List<MatoPatternWithStops>

    private lateinit var map: MapView
    private var viewingPattern: MatoPatternWithStops? = null

    private val viewModel: LinesViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()
    private var firstInit = true
    private var pausedFragment = false
    private lateinit var switchButton: ImageButton

    private var favoritesButton: ImageButton? = null
    private var isLineInFavorite = false
    private var appContext: Context? = null
    private val lineSharedPrefMonitor = SharedPreferences.OnSharedPreferenceChangeListener { pref, keychanged ->
        if(keychanged!=PreferencesHolder.PREF_FAVORITE_LINES || lineID.isEmpty()) return@OnSharedPreferenceChangeListener
        val newFavorites = pref.getStringSet(PreferencesHolder.PREF_FAVORITE_LINES, HashSet())
        newFavorites?.let {favorites->
            isLineInFavorite = favorites.contains(lineID)
            //if the button has been intialized, change the icon accordingly
            favoritesButton?.let { button->
                if(isLineInFavorite) {
                    button.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_star_filled, null))
                    appContext?.let {  Toast.makeText(it,R.string.favorites_line_add,Toast.LENGTH_SHORT).show()}
                } else {
                    button.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_star_outline, null))
                    appContext?.let {Toast.makeText(it,R.string.favorites_line_remove,Toast.LENGTH_SHORT).show()}
                }


            }
        }
    }

    private lateinit var stopsRecyclerView: RecyclerView
    private lateinit var descripTextView: TextView
    //adapter for recyclerView
    private val stopAdapterListener= object : StopAdapterListener {
        override fun onTappedStop(stop: Stop?) {

            if(viewModel.shouldShowMessage) {
                Toast.makeText(context, R.string.long_press_stop_4_options, Toast.LENGTH_SHORT).show()
                viewModel.shouldShowMessage=false
            }
            stop?.let {
                fragmentListener.requestArrivalsForStopID(it.ID)
            }
            if(stop == null){
                Log.e(DEBUG_TAG,"Passed wrong stop")
            }
            if(fragmentListener == null){
                Log.e(DEBUG_TAG, "Fragment listener is null")
            }
        }

        override fun onLongPressOnStop(stop: Stop?): Boolean {
            TODO("Not yet implemented")
        }

    }


    private var polyline: Polyline? = null
    //private var stopPosList = ArrayList<GeoPoint>()

    private lateinit var stopsOverlay: FolderOverlay
    private lateinit var locationOverlay: LocationOverlay
    //fragment actions
    private lateinit var fragmentListener: CommonFragmentListener

    private val stopTouchResponder = TouchResponder { stopID, stopName ->
        Log.d(DEBUG_TAG, "Asked to show arrivals for stop ID: $stopID")
        fragmentListener.requestArrivalsForStopID(stopID)
    }
    private var showOnTopOfLine = true
    private var recyclerInitDone = false

    private var useMQTTPositions = true

    //position of live markers
    private val busPositionMarkersByTrip = HashMap<String,Marker>()
    private var busPositionsOverlay = FolderOverlay()

    private val tripMarkersAnimators = HashMap<String, ObjectAnimator>()

    private val liveBusViewModel: LivePositionsViewModel by viewModels()
    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_lines_detail, container, false)
        lineID = requireArguments().getString(LINEID_KEY, "")
        switchButton = rootView.findViewById(R.id.switchImageButton)
        favoritesButton = rootView.findViewById(R.id.favoritesButton)
        stopsRecyclerView = rootView.findViewById(R.id.patternStopsRecyclerView)
        descripTextView = rootView.findViewById(R.id.lineDescripTextView)
        descripTextView.visibility = View.INVISIBLE

        val titleTextView = rootView.findViewById<TextView>(R.id.titleTextView)
        titleTextView.text = getString(R.string.line)+" "+FiveTNormalizer.fixShortNameForDisplay(
            GtfsUtils.getLineNameFromGtfsID(lineID), true)

        favoritesButton?.isClickable = true
        favoritesButton?.setOnClickListener {
            if(lineID.isNotEmpty())
                PreferencesHolder.addOrRemoveLineToFavorites(requireContext(),lineID,!isLineInFavorite)
        }
        val preferences = PreferencesHolder.getMainSharedPreferences(requireContext())
        val favorites = preferences.getStringSet(PreferencesHolder.PREF_FAVORITE_LINES, HashSet())
        if(favorites!=null && favorites.contains(lineID)){
            favoritesButton?.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.ic_star_filled, null))
            isLineInFavorite = true
        }
        appContext = requireContext().applicationContext
        preferences.registerOnSharedPreferenceChangeListener(lineSharedPrefMonitor)

        patternsSpinner = rootView.findViewById(R.id.patternsSpinner)
        patternsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
        patternsSpinner.adapter = patternsAdapter

        initializeMap(rootView)

        initializeRecyclerView()

        switchButton.setOnClickListener{
            if(map.visibility == View.VISIBLE){
                map.visibility = View.GONE
                stopsRecyclerView.visibility = View.VISIBLE

                viewModel.setMapShowing(false)
                liveBusViewModel.stopMatoUpdates()
                switchButton.setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_map_white_30))
            } else{
                stopsRecyclerView.visibility = View.GONE
                map.visibility = View.VISIBLE
                viewModel.setMapShowing(true)
                if(useMQTTPositions)
                    liveBusViewModel.requestMatoPosUpdates(lineID)
                else
                    liveBusViewModel.requestGTFSUpdates()
                switchButton.setImageDrawable(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_list_30))
            }
        }
        viewModel.setRouteIDQuery(lineID)

        val keySourcePositions = getString(R.string.pref_positions_source)
        useMQTTPositions = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(keySourcePositions, "mqtt").contentEquals("mqtt")

        viewModel.patternsWithStopsByRouteLiveData.observe(viewLifecycleOwner){
            patterns -> savePatternsToShow(patterns)
        }
        /*
            We have the pattern and the stops here, time to display them
         */
        viewModel.stopsForPatternLiveData.observe(viewLifecycleOwner) { stops ->
            if(map.visibility ==View.VISIBLE)
                showPatternWithStopsOnMap(stops)
            else{
                if(stopsRecyclerView.visibility==View.VISIBLE)
                    showStopsAsList(stops)
            }
        }
        viewModel.gtfsRoute.observe(viewLifecycleOwner){route->
            if(route == null){
                //need to close the fragment
                activity?.supportFragmentManager?.popBackStack()
                return@observe
            }
             descripTextView.text = route.longName
            descripTextView.visibility = View.VISIBLE
        }
        if(pausedFragment && viewModel.selectedPatternLiveData.value!=null){
            val patt = viewModel.selectedPatternLiveData.value!!
            Log.d(DEBUG_TAG, "Recreating views on resume, setting pattern: ${patt.pattern.code}")
            showPattern(patt)
            pausedFragment = false
        }

        Log.d(DEBUG_TAG,"Data ${viewModel.stopsForPatternLiveData.value}")

        //listeners
        patternsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                val patternWithStops = currentPatterns.get(position)
                //viewModel.setPatternToDisplay(patternWithStops)
                setPatternAndReqStops(patternWithStops)

                Log.d(DEBUG_TAG, "item Selected, cleaning bus markers")
                if(map?.visibility == View.VISIBLE) {
                    busPositionsOverlay.closeAllInfoWindows()
                    busPositionsOverlay.items.clear()
                    busPositionMarkersByTrip.clear()

                    stopAnimations()
                    tripMarkersAnimators.clear()
                    liveBusViewModel.retriggerPositionUpdate()
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }


        //live bus positions
        liveBusViewModel.updatesWithTripAndPatterns.observe(viewLifecycleOwner){
            if(map.visibility == View.GONE || viewingPattern ==null){
                //DO NOTHING
                return@observe
            }
            val filtdLineID = GtfsUtils.stripGtfsPrefix(lineID)
            //filter buses with direction, show those only with the same direction
            val outmap = HashMap<String, Pair<LivePositionUpdate, TripAndPatternWithStops?>>()
            val currentPattern = viewingPattern!!.pattern
            val numUpds = it.entries.size
            Log.d(DEBUG_TAG, "Got $numUpds updates, current pattern is: ${currentPattern.name}, directionID: ${currentPattern.directionId}")
            val patternsDirections = HashMap<String,Int>()
            for((tripId, pair) in it.entries){
                //remove trips with wrong line ideas
                if(pair.first.routeID!=filtdLineID)
                    continue

                if(pair.second!=null && pair.second?.pattern !=null){
                    val dir = pair.second?.pattern?.directionId
                    if(dir !=null && dir == currentPattern.directionId){
                        outmap[tripId] = pair
                    }
                    patternsDirections.set(tripId,if (dir!=null) dir else -10)
                } else{
                    outmap[tripId] = pair
                    //Log.d(DEBUG_TAG, "No pattern for tripID: $tripId")
                    patternsDirections[tripId] = -10
                }
            }
            Log.d(DEBUG_TAG, " Filtered updates are ${outmap.keys.size}") // Original updates directs: $patternsDirections\n
            updateBusPositionsInMap(outmap)
            //if not using MQTT positions
            if(!useMQTTPositions){
                liveBusViewModel.requestDelayedGTFSUpdates(2000)
            }
        }

        //download missing tripIDs
        liveBusViewModel.tripsGtfsIDsToQuery.observe(viewLifecycleOwner){
            //gtfsPosViewModel.downloadTripsFromMato(dat);
            MatoTripsDownloadWorker.downloadTripsFromMato(
                it, requireContext().applicationContext,
                "BusTO-MatoTripDownload"
            )
        }


        return rootView
    }

    private fun initializeMap(rootView : View){
        val ctx = requireContext().applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))

        map = rootView.findViewById(R.id.lineMap)
        map.let {
            it.setTileSource(TileSourceFactory.MAPNIK)

            locationOverlay = LocationOverlay.createLocationOverlay(true, it, requireContext(), object : LocationOverlay.OverlayCallbacks{
                override fun onDisableFollowMyLocation() {
                    Log.d(DEBUG_TAG, "Follow location disabled")
                }

                override fun onEnableFollowMyLocation() {
                    Log.d(DEBUG_TAG, "Follow location enabled")
                }

            })
            locationOverlay.disableFollowLocation()

            stopsOverlay = FolderOverlay()
            busPositionsOverlay =  FolderOverlay()


            //map.setTilesScaledToDpi(true);
            //map.setTilesScaledToDpi(true);
            it.setFlingEnabled(true)
            it.setUseDataConnection(true)

            // add ability to zoom with 2 fingers
            it.setMultiTouchControls(true)
            it.minZoomLevel = 12.0

            //map controller setup
            val mapController = it.controller
            var zoom = 12.0
            var centerMap = GeoPoint(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON)
            if(mapViewModel.currentLat.value!=MapViewModel.INVALID) {
                Log.d(DEBUG_TAG, "mapViewModel posi: ${mapViewModel.currentLat.value}, ${mapViewModel.currentLong.value}"+
                        " zoom ${mapViewModel.currentZoom.value}")
                zoom = mapViewModel.currentZoom.value!!
                centerMap = GeoPoint(mapViewModel.currentLat.value!!, mapViewModel.currentLong.value!!)
                /*viewLifecycleOwner.lifecycleScope.launch {
                    delay(100)
                    Log.d(DEBUG_TAG, "zooming back to point")
                    controller.animateTo(GeoPoint(mapViewModel.currentLat.value!!, mapViewModel.currentLong.value!!),
                        mapViewModel.currentZoom.value!!,null,null)
                    //controller.setCenter(GeoPoint(mapViewModel.currentLat.value!!, mapViewModel.currentLong.value!!))
                    //controller.setZoom(mapViewModel.currentZoom.value!!)

                 */

                }
            mapController.setZoom(zoom)
            mapController.setCenter(centerMap)
            Log.d(DEBUG_TAG, "Initializing map, first init $firstInit")
            //map.invalidate()

            it.overlayManager.add(stopsOverlay)
            it.overlayManager.add(locationOverlay)
            it.overlayManager.add(busPositionsOverlay)

            zoomToCurrentPattern()
            firstInit = false

        }


    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is CommonFragmentListener){
            fragmentListener = context
        } else throw RuntimeException("$context must implement CommonFragmentListener")

    }


    private fun stopAnimations(){
        for(anim in tripMarkersAnimators.values){
            anim.cancel()
        }
    }

    private fun savePatternsToShow(patterns: List<MatoPatternWithStops>){
        val patternsSorter = Comparator{ p1: MatoPatternWithStops, p2: MatoPatternWithStops ->
            if(p1.pattern.directionId != p2.pattern.directionId)
                return@Comparator p1.pattern.directionId - p2.pattern.directionId
            else
                return@Comparator -1*(p1.stopsIndices.size - p2.stopsIndices.size)

        }
        currentPatterns = patterns.sortedWith(patternsSorter)

        patternsAdapter?.let {
            it.clear()
            it.addAll(currentPatterns.map { p->"${p.pattern.directionId} - ${p.pattern.headsign}" })
            it.notifyDataSetChanged()
        }
        viewingPattern?.let {
            showPattern(it)
        }

    }

    /**
     * Called when the position of the spinner is updated
     */
    private fun setPatternAndReqStops(patternWithStops: MatoPatternWithStops){
        Log.d(DEBUG_TAG, "Requesting stops for pattern ${patternWithStops.pattern.code}")
        viewModel.selectedPatternLiveData.value = patternWithStops
        viewModel.currentPatternStops.value =  patternWithStops.stopsIndices.sortedBy { i-> i.order }
        viewingPattern = patternWithStops

        viewModel.requestStopsForPatternWithStops(patternWithStops)
    }
    private fun showPattern(patternWs: MatoPatternWithStops){
        Log.d(DEBUG_TAG, "Finding pattern to show: ${patternWs.pattern.code}")
        var pos = -2
        val code = patternWs.pattern.code.trim()
        for(k in currentPatterns.indices){
            if(currentPatterns[k].pattern.code.trim() == code){
                pos = k
                break
            }
        }
        Log.d(DEBUG_TAG, "Found pattern $code in position: $pos")
        if(pos>=0)
            patternsSpinner.setSelection(pos)
        //set pattern
        setPatternAndReqStops(patternWs)
    }

    private fun zoomToCurrentPattern(){
        var pointsList: List<GeoPoint>
        if(viewingPattern==null) {
            Log.e(DEBUG_TAG, "asked to zoom to pattern but current viewing pattern is null")
            if(polyline!=null)
            pointsList = polyline!!.actualPoints
            else {
                Log.d(DEBUG_TAG, "The polyline is null")
                return
            }
        }else{
            val pattern = viewingPattern!!.pattern

            pointsList = PolylineParser.decodePolyline(pattern.patternGeometryPoly, pattern.patternGeometryLength)
        }

        var maxLat = -4000.0
        var minLat = -4000.0
        var minLong = -4000.0
        var maxLong = -4000.0
        for (p in pointsList){
            // get max latitude
            if(maxLat == -4000.0)
                maxLat = p.latitude
            else if (maxLat < p.latitude) maxLat = p.latitude
            // find min latitude
            if (minLat == -4000.0)
                minLat = p.latitude
            else if (minLat > p.latitude) minLat = p.latitude
            if(maxLong == -4000.0 || maxLong < p.longitude )
                maxLong = p.longitude
            if (minLong == -4000.0 || minLong > p.longitude)
                minLong = p.longitude
        }

        val del = 0.008
        //map.controller.c
        Log.d(DEBUG_TAG, "Setting limits of bounding box of line: $minLat -> $maxLat, $minLong -> $maxLong")
        map.zoomToBoundingBox(BoundingBox(maxLat+del, maxLong+del, minLat-del, minLong-del), false)
    }

    private fun showPatternWithStopsOnMap(stops: List<Stop>){
        Log.d(DEBUG_TAG, "Got the stops: ${stops.map { s->s.gtfsID }}}")
        if(viewingPattern==null || map == null) return

        val pattern = viewingPattern!!.pattern

        val pointsList = PolylineParser.decodePolyline(pattern.patternGeometryPoly, pattern.patternGeometryLength)

        var maxLat = -4000.0
        var minLat = -4000.0
        var minLong = -4000.0
        var maxLong = -4000.0
        for (p in pointsList){
            // get max latitude
            if(maxLat == -4000.0)
                maxLat = p.latitude
            else if (maxLat < p.latitude) maxLat = p.latitude
            // find min latitude
            if (minLat == -4000.0)
                minLat = p.latitude
            else if (minLat > p.latitude) minLat = p.latitude
            if(maxLong == -4000.0 || maxLong < p.longitude )
                maxLong = p.longitude
            if (minLong == -4000.0 || minLong > p.longitude)
                minLong = p.longitude
        }
        //val polyLine=Polyline(map)
        //polyLine.setPoints(pointsList)
        //save points
        if(map.overlayManager.contains(polyline)){
            map.overlayManager.remove(polyline)
        }
        polyline = Polyline(map, false)
        polyline!!.setPoints(pointsList)
        //polyline.color = ContextCompat.getColor(context!!,R.color.brown_vd)
        polyline!!.infoWindow = null
        val paint = Paint()
        paint.color = ContextCompat.getColor(requireContext(),R.color.line_drawn_poly)
        paint.isAntiAlias = true
        paint.strokeWidth = 13f

        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        polyline!!.outlinePaintLists.add(MonochromaticPaintList(paint))

        map.overlayManager.add(0,polyline!!)

        stopsOverlay.closeAllInfoWindows()
        stopsOverlay.items.clear()
        val stopIcon = ContextCompat.getDrawable(requireContext(), R.drawable.ball)

        for(s in stops){
            val gp = if (showOnTopOfLine)
                findOptimalPosition(s,pointsList)
            else GeoPoint(s.latitude!!,s.longitude!!)

            val marker = MarkerUtils.makeMarker(
                gp, s.ID, s.stopDefaultName,
                s.routesThatStopHereToString(),
                map,stopTouchResponder, stopIcon,
                R.layout.linedetail_stop_infowindow,
                R.color.line_drawn_poly
            )
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            stopsOverlay.add(marker)
        }
        //POINTS LIST IS NOT IN ORDER ANY MORE
        //if(!map.overlayManager.contains(stopsOverlay)){
        //    map.overlayManager.add(stopsOverlay)
        //}
        polyline!!.setOnClickListener(Polyline.OnClickListener { polyline, mapView, eventPos ->
            Log.d(DEBUG_TAG, "clicked")
            true
        })

        //map.controller.zoomToB//#animateTo(pointsList[0])
        val del = 0.008
        map.zoomToBoundingBox(BoundingBox(maxLat+del, maxLong+del, minLat-del, minLong-del), true)
        //map.invalidate()
    }

    private fun initializeRecyclerView(){
        val llManager = LinearLayoutManager(context)
        llManager.orientation = LinearLayoutManager.VERTICAL

        stopsRecyclerView.layoutManager = llManager
    }
    private fun showStopsAsList(stops: List<Stop>){

        Log.d(DEBUG_TAG, "Setting stops from: "+viewModel.currentPatternStops.value)
        val orderBy = viewModel.currentPatternStops.value!!.withIndex().associate{it.value.stopGtfsId to it.index}
        val stopsSorted = stops.sortedBy { s -> orderBy[s.gtfsID] }
        val numStops = stopsSorted.size
        Log.d(DEBUG_TAG, "RecyclerView adapter is: ${stopsRecyclerView.adapter}")

        val setNewAdapter = true
        if(setNewAdapter){
            stopsRecyclerView.adapter = StopRecyclerAdapter(
                stopsSorted, stopAdapterListener, StopRecyclerAdapter.Use.LINES,
                NameCapitalize.FIRST
            )

        }



    }


    /**
     * Remove bus marker from overlay associated with tripID
     */
    private fun removeBusMarker(tripID: String){
        if(!busPositionMarkersByTrip.containsKey(tripID)){
            Log.e(DEBUG_TAG, "Asked to remove veh with tripID $tripID but it's supposedly not shown")
            return
        }
        val marker = busPositionMarkersByTrip[tripID]
        busPositionsOverlay.remove(marker)
        busPositionMarkersByTrip.remove(tripID)

        val animator = tripMarkersAnimators[tripID]
        animator?.let{
            it.cancel()
            tripMarkersAnimators.remove(tripID)
        }

    }

    private fun showPatternWithStop(patternId: String){
        //var index = 0
        Log.d(DEBUG_TAG, "Showing pattern with code $patternId ")
        for (i in currentPatterns.indices){
            val pattStop = currentPatterns[i]
            if(pattStop.pattern.code == patternId){
                Log.d(DEBUG_TAG, "Pattern found in position $i")
                //setPatternAndReqStops(pattStop)
                patternsSpinner.setSelection(i)
                break
            }
        }
    }

    /**
     * draw the position of the buses in the map. Copied from MapFragment
     */
    private fun updateBusPositionsInMap(tripsPatterns: java.util.HashMap<String, Pair<LivePositionUpdate, TripAndPatternWithStops?>>
                                        ) {
        //Log.d(MapFragment.DEBUG_TAG, "Updating positions of the buses")
        //if(busPositionsOverlay == null) busPositionsOverlay = new FolderOverlay();
        val noPatternsTrips = ArrayList<String>()
        for (tripID in tripsPatterns.keys) {
            val (update, tripWithPatternStops) = tripsPatterns[tripID] ?: continue

            var marker: Marker? = null
            //check if Marker is already created
            if (busPositionMarkersByTrip.containsKey(tripID)) {

                //check if the trip direction ID is the same, if not remove
                if(tripWithPatternStops?.pattern != null &&
                    tripWithPatternStops.pattern.directionId != viewingPattern?.pattern?.directionId){
                            removeBusMarker(tripID)

                } else {
                    //need to change the position of the marker
                    marker = busPositionMarkersByTrip.get(tripID)!!
                    BusPositionUtils.updateBusPositionMarker(map, marker, update, tripMarkersAnimators, false)
                    // Set the pattern to add the info
                    if (marker.infoWindow != null && marker.infoWindow is BusInfoWindow) {
                        val window = marker.infoWindow as BusInfoWindow
                        if (window.pattern == null && tripWithPatternStops != null) {
                            //Log.d(DEBUG_TAG, "Update pattern for trip: "+tripID);
                            window.setPatternAndDraw(tripWithPatternStops.pattern)
                        }
                    }
                }
            } else {
                //marker is not there, need to make it
                //if (mapView == null) Log.e(MapFragment.DEBUG_TAG, "Creating marker with null map, things will explode")
                marker = Marker(map)

                //String route = GtfsUtils.getLineNameFromGtfsID(update.getRouteID());
                val mdraw = ResourcesCompat.getDrawable(getResources(), R.drawable.map_bus_position_icon, null)!!
                //mdraw.setBounds(0,0,28,28);

                marker.icon = mdraw
                var markerPattern: MatoPattern? = null
                if (tripWithPatternStops != null) {
                    if (tripWithPatternStops.pattern != null)
                        markerPattern = tripWithPatternStops.pattern
                }
                marker.infoWindow = BusInfoWindow(map, update, markerPattern, true) {
                    // set pattern to show
                    if(it!=null)
                        showPatternWithStop(it.code)
                }
                //marker.infoWindow as BusInfoWindow
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                BusPositionUtils.updateBusPositionMarker(map,marker, update, tripMarkersAnimators,true)
                // the overlay is null when it's not attached yet?
                // cannot recreate it because it becomes null very soon
                // if(busPositionsOverlay == null) busPositionsOverlay = new FolderOverlay();
                //save the marker
                if (busPositionsOverlay != null) {
                    busPositionsOverlay.add(marker)
                    busPositionMarkersByTrip.put(tripID, marker)
                }
            }
        }
        if (noPatternsTrips.size > 0) {
            Log.i(DEBUG_TAG, "These trips have no matching pattern: $noPatternsTrips")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(DEBUG_TAG, "Resetting paused from onResume")
        pausedFragment = false

        val keySourcePositions = getString(R.string.pref_positions_source)
        useMQTTPositions = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(keySourcePositions, "mqtt").contentEquals("mqtt")

        //separate paths
        if(useMQTTPositions)
            liveBusViewModel.requestMatoPosUpdates(GtfsUtils.getLineNameFromGtfsID(lineID))
        else
            liveBusViewModel.requestGTFSUpdates()


        if(mapViewModel.currentLat.value!=MapViewModel.INVALID) {
            Log.d(DEBUG_TAG, "mapViewModel posi: ${mapViewModel.currentLat.value}, ${mapViewModel.currentLong.value}"+
                    " zoom ${mapViewModel.currentZoom.value}")
            val controller = map.controller
            viewLifecycleOwner.lifecycleScope.launch {
                delay(100)
                Log.d(DEBUG_TAG, "zooming back to point")
                controller.animateTo(GeoPoint(mapViewModel.currentLat.value!!, mapViewModel.currentLong.value!!),
                    mapViewModel.currentZoom.value!!,null,null)
                //controller.setCenter(GeoPoint(mapViewModel.currentLat.value!!, mapViewModel.currentLong.value!!))
                //controller.setZoom(mapViewModel.currentZoom.value!!)
            }

            //controller.setZoom()
        }
        //initialize GUI here
        fragmentListener.readyGUIfor(FragmentKind.LINES)

    }

    override fun onPause() {
        super.onPause()
        liveBusViewModel.stopMatoUpdates()
        pausedFragment = true
        //save map
        val center = map.mapCenter
        mapViewModel.currentLat.value = center.latitude
        mapViewModel.currentLong.value = center.longitude
        mapViewModel.currentZoom.value = map.zoomLevel.toDouble()
    }

    override fun getBaseViewForSnackBar(): View? {
        return null
    }

    companion object {
        private const val LINEID_KEY="lineID"
        fun newInstance() = LinesDetailFragment()
        const val DEBUG_TAG="LinesDetailFragment"

        fun makeArgs(lineID: String): Bundle{
            val b = Bundle()
            b.putString(LINEID_KEY, lineID)
            return b
        }
        @JvmStatic
        private fun findOptimalPosition(stop: Stop, pointsList: MutableList<GeoPoint>): GeoPoint{
            if(stop.latitude==null || stop.longitude ==null|| pointsList.isEmpty())
                throw IllegalArgumentException()
            val sLat = stop.latitude!!
            val sLong = stop.longitude!!
            if(pointsList.size < 2)
                return  pointsList[0]
            pointsList.sortBy { utils.measuredistanceBetween(sLat, sLong, it.latitude, it.longitude) }

            val p1 = pointsList[0]
            val p2 = pointsList[1]
            if (p1.longitude == p2.longitude){
                //Log.e(DEBUG_TAG, "Same longitude")
                return GeoPoint(sLat, p1.longitude)
            } else if (p1.latitude == p2.latitude){
                //Log.d(DEBUG_TAG, "Same latitude")
                return GeoPoint(p2.latitude,sLong)
            }

            val m = (p1.latitude - p2.latitude) / (p1.longitude - p2.longitude)
            val minv = (p1.longitude-p2.longitude)/(p1.latitude - p2.latitude)
            val cR = p1.latitude - p1.longitude * m

            val longNew = (minv * sLong + sLat -cR ) / (m+minv)
            val latNew = (m*longNew + cR)
            //Log.d(DEBUG_TAG,"Stop ${stop.ID} old pos: ($sLat, $sLong), new pos ($latNew,$longNew)")
            return GeoPoint(latNew,longNew)
        }

        private const val DEFAULT_CENTER_LAT = 45.12
        private const val DEFAULT_CENTER_LON = 7.6858
    }
}