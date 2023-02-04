package it.reyboz.bustorino.fragments

import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.gtfs.PolylineParser
import it.reyboz.bustorino.data.gtfs.MatoPatternWithStops
import it.reyboz.bustorino.data.gtfs.PatternStop
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

class LinesDetailFragment() : Fragment() {

    private lateinit var lineID: String


    private lateinit var patternsSpinner: Spinner
    private var patternsAdapter: ArrayAdapter<String>? = null

    private var patternsSpinnerState: Parcelable? = null

    private lateinit var currentPatterns: List<MatoPatternWithStops>
    private lateinit var gtfsStopsForCurrentPattern: List<PatternStop>

    private lateinit var map: MapView
    private lateinit var viewingPattern: MatoPatternWithStops

    private lateinit var viewModel: LinesViewModel

    private var polyline = Polyline();

    companion object {
        private const val LINEID_KEY="lineID"
        fun newInstance() = LinesDetailFragment()
        const val DEBUG_TAG="LinesDetailFragment"

        fun makeArgs(lineID: String): Bundle{
            val b = Bundle()
            b.putString(LINEID_KEY, lineID)
            return b
        }
        private const val DEFAULT_CENTER_LAT = 45.0708
        private const val DEFAULT_CENTER_LON = 7.6858
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_lines_detail, container, false)
        lineID = requireArguments().getString(LINEID_KEY, "")

        patternsSpinner = rootView.findViewById(R.id.patternsSpinner)
        patternsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
        patternsSpinner.adapter = patternsAdapter

        map = rootView.findViewById(R.id.lineMap)
        map.setTileSource(TileSourceFactory.MAPNIK)
        //map.setTilesScaledToDpi(true);
        //map.setTilesScaledToDpi(true);
        map.setFlingEnabled(true)

        // add ability to zoom with 2 fingers
        map.setMultiTouchControls(true)
        map.minZoomLevel = 14.0

        //map controller setup
        val mapController = map.controller
        mapController.setZoom(14.0)
        mapController.setCenter(GeoPoint(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON))
        map.invalidate()

        viewModel.patternsWithStopsByRouteLiveData.observe(viewLifecycleOwner){
            patterns -> savePatternsToShow(patterns)
        }


        /*
            We have the pattern and the stops here, time to display them
         */
        viewModel.stopsForPatternLiveData.observe(viewLifecycleOwner) { stops ->
            Log.d(DEBUG_TAG, "Got the stops: ${stops.map { s->s.gtfsID }}}")

            val pattern = viewingPattern.pattern

            val pointsList = PolylineParser.decodePolyline(pattern.patternGeometryPoly, pattern.patternGeometryLength)
            //val polyLine=Polyline(map)
            //polyLine.setPoints(pointsList)
            if(map.overlayManager.contains(polyline)){
                map.overlayManager.remove(polyline)
            }
            polyline = Polyline(map)
            polyline.setPoints(pointsList)

            map.overlayManager.add(polyline)
            //map.controller.animateTo(pointsList[0])
        }

        viewModel.setRouteIDQuery(lineID)

        Log.d(DEBUG_TAG,"Data ${viewModel.stopsForPatternLiveData.value}")

        //listeners
        patternsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                val patternWithStops = currentPatterns.get(position)
                setPatternAndReqStops(patternWithStops)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }


        return rootView
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(LinesViewModel::class.java)
    }

    private fun savePatternsToShow(patterns: List<MatoPatternWithStops>){
        currentPatterns = patterns.sortedBy { p-> p.pattern.code }

        patternsAdapter?.let {
            it.clear()
            it.addAll(currentPatterns.map { p->"${p.pattern.directionId} - ${p.pattern.headsign}" })
            it.notifyDataSetChanged()
        }

        val  pos = patternsSpinner.selectedItemPosition
        //might be possible that the selectedItem is different (larger than list size)
        if(pos!= AdapterView.INVALID_POSITION && pos >= 0 && (pos < currentPatterns.size)){
            val p = currentPatterns[pos]
            Log.d(LinesFragment.DEBUG_TAG, "Setting patterns with pos $pos and p gtfsID ${p.pattern.code}")
            setPatternAndReqStops(currentPatterns[pos])
        }
        Log.d(DEBUG_TAG, "Patterns changed")

    }

    private fun setPatternAndReqStops(patternWithStops: MatoPatternWithStops){
        Log.d(DEBUG_TAG, "Requesting stops for pattern ${patternWithStops.pattern.code}")
        gtfsStopsForCurrentPattern = patternWithStops.stopsIndices.sortedBy { i-> i.order }
        viewingPattern = patternWithStops

        viewModel.requestStopsForPatternWithStops(patternWithStops)
    }


}