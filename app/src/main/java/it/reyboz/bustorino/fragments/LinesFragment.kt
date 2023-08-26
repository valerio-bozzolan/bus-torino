/*
	BusTO  - Fragments components
    Copyright (C) 2022 Fabio Mazza

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

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.*
import android.widget.*
import android.widget.AdapterView.INVALID_POSITION
import android.widget.AdapterView.OnItemSelectedListener
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.reyboz.bustorino.R
import it.reyboz.bustorino.adapters.NameCapitalize
import it.reyboz.bustorino.adapters.StopAdapterListener
import it.reyboz.bustorino.adapters.StopRecyclerAdapter
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.gtfs.GtfsRoute
import it.reyboz.bustorino.data.gtfs.MatoPatternWithStops
import it.reyboz.bustorino.util.LinesNameSorter
import it.reyboz.bustorino.util.PatternWithStopsSorter
import it.reyboz.bustorino.viewmodels.LinesViewModel

class LinesFragment : ScreenBaseFragment() {

    companion object {
        fun newInstance(){
            LinesFragment()
        }
        private const val DEBUG_TAG="BusTO-LinesFragment"
        const val FRAGMENT_TAG="LinesFragment"

        val patternStopsComparator = PatternWithStopsSorter()
    }


    private lateinit var viewModel: LinesViewModel

    private lateinit var linesSpinner: Spinner
    private lateinit var patternsSpinner: Spinner

    private lateinit var currentRoutes: List<GtfsRoute>
    private lateinit var selectedPatterns: List<MatoPatternWithStops>

    private lateinit var routeDescriptionTextView: TextView
    private lateinit var stopsRecyclerView: RecyclerView

    private var linesAdapter: ArrayAdapter<String>? = null
    private var patternsAdapter: ArrayAdapter<String>? = null
    private var mListener: CommonFragmentListener? = null

    private val linesNameSorter = LinesNameSorter()
    private val linesComparator = Comparator<GtfsRoute> { a,b ->
        return@Comparator linesNameSorter.compare(a.shortName, b.shortName)
    }
    private var firstClick = true
    private var recyclerViewState:Parcelable? = null
    private var patternsSpinnerState:Parcelable? = null

    private val adapterListener = object : StopAdapterListener {
        override fun onTappedStop(stop: Stop?) {
            //var r = ""
            //stop?.let { r= it.stopDisplayName.toString() }
            if(viewModel.shouldShowMessage) {
                Toast.makeText(context, R.string.long_press_stop_4_options, Toast.LENGTH_SHORT).show()
                viewModel.shouldShowMessage=false
            }
            stop?.let {
                mListener?.requestArrivalsForStopID(it.ID)
            }
            if(stop == null){
                Log.e(DEBUG_TAG,"Passed wrong stop")
            }
            if(mListener == null){
                Log.e(DEBUG_TAG, "Listener is null")
            }
        }

        override fun onLongPressOnStop(stop: Stop?): Boolean {
            Log.d(DEBUG_TAG, "LongPressOnStop")
            return true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        Log.d(DEBUG_TAG, "saveInstanceState bundle: $outState")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView =  inflater.inflate(R.layout.fragment_lines, container, false)

        linesSpinner = rootView.findViewById(R.id.linesSpinner)
        patternsSpinner = rootView.findViewById(R.id.patternsSpinner)


        routeDescriptionTextView = rootView.findViewById(R.id.routeDescriptionTextView)
        stopsRecyclerView = rootView.findViewById(R.id.patternStopsRecyclerView)

        val llManager = LinearLayoutManager(context)
        llManager.orientation = LinearLayoutManager.VERTICAL

        stopsRecyclerView.layoutManager = llManager
        //allow the context menu to be opened
        registerForContextMenu(stopsRecyclerView)
        Log.d(DEBUG_TAG, "Called onCreateView for LinesFragment")
        Log.d(DEBUG_TAG, "OnCreateView, selected line spinner pos: ${linesSpinner.selectedItemPosition}")
        Log.d(DEBUG_TAG, "OnCreateView, selected patterns spinner pos: ${patternsSpinner.selectedItemPosition}")

        //set requests
        viewModel.routesGTTLiveData.observe(viewLifecycleOwner) {
            setRoutes(it)
        }

        viewModel.patternsWithStopsByRouteLiveData.observe(viewLifecycleOwner){
                patterns ->
            run {
                selectedPatterns = patterns.sortedBy { p-> p.pattern.code }
                //patterns. //sortedBy {-1*it.stopsIndices.size}// "${p.pattern.directionId} - ${p.pattern.headsign}" }
                patternsAdapter?.let {
                    it.clear()
                    it.addAll(selectedPatterns.map { p->"${p.pattern.directionId} - ${p.pattern.headsign}" })
                    it.notifyDataSetChanged()
                }
                viewModel.selectedPatternLiveData.value?.let {
                   setSelectedPattern(it)
                }

                val  pos = patternsSpinner.selectedItemPosition
                //might be possible that the selectedItem is different (larger than list size)
                if(pos!= INVALID_POSITION && pos >= 0 && (pos < selectedPatterns.size)){
                    val p = selectedPatterns[pos]
                    Log.d(DEBUG_TAG, "Setting patterns with pos $pos and p gtfsID ${p.pattern.code}")
                    setPatternAndReqStops(selectedPatterns[pos])
                }

            }
        }

        viewModel.stopsForPatternLiveData.observe(viewLifecycleOwner){stops->
            Log.d("BusTO-LinesFragment", "Setting stops from DB")
            setCurrentStops(stops)
        }

        if(context!=null) {
            patternsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
            patternsSpinner.adapter = patternsAdapter
            linesAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
            linesSpinner.adapter = linesAdapter

            if (linesSpinner.onItemSelectedListener != null){
                Log.d(DEBUG_TAG, "linesSpinner listener != null")
            }
            //listener
            linesSpinner.onItemSelectedListener = object: OnItemSelectedListener{
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    val selRoute = currentRoutes.get(pos)

                    routeDescriptionTextView.text = selRoute.longName
                    val oldRoute = viewModel.getRouteIDQueried()
                    val resetSpinner = (oldRoute != null) && (oldRoute.trim() != selRoute.gtfsId.trim())
                    Log.d(DEBUG_TAG, "Selected route: ${selRoute.gtfsId}, reset spinner: $resetSpinner, oldRoute: $oldRoute")
                    //launch query for this gtfsID
                    viewModel.setRouteIDQuery(selRoute.gtfsId)
                    //reset spinner position
                    if(resetSpinner) patternsSpinner.setSelection(0)

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {

                }
            }

            patternsSpinner.onItemSelectedListener = object : OnItemSelectedListener{
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                    val patternWithStops = selectedPatterns.get(position)
                    //
                    setPatternAndReqStops(patternWithStops)
                    //viewModel.currentPositionInPatterns.value = position

                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }
        }

        return rootView
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is CommonFragmentListener)
            mListener = context
        else throw RuntimeException(context.toString()
                + " must implement CommonFragmentListener")
    }

    override fun onResume() {
        super.onResume()
        mListener?.readyGUIfor(FragmentKind.LINES)

        Log.d(DEBUG_TAG, "Resuming lines fragment")
        //Log.d(DEBUG_TAG, "OnResume, selected line spinner pos: ${linesSpinner.selectedItemPosition}")
        //Log.d(DEBUG_TAG, "OnResume, selected patterns spinner pos: ${patternsSpinner.selectedItemPosition}")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this).get(LinesViewModel::class.java)
        Log.d(DEBUG_TAG, "Fragment onCreate")

    }

    override fun getBaseViewForSnackBar(): View? {
        return null
    }

    private fun setSelectedPattern(patternWs: MatoPatternWithStops){
        Log.d(DEBUG_TAG, "Finding pattern to show: ${patternWs.pattern.code}")
        var pos = -2
        val code = patternWs.pattern.code.trim()
        for(k in selectedPatterns.indices){
            if(selectedPatterns[k].pattern.code.trim() == code){
                pos = k
                break
            }
        }
        Log.d(DEBUG_TAG, "Found pattern $code in position: $pos")
        if(pos>=0){
            patternsSpinner.setSelection(pos)
        }
    }

    private fun setRoutes(routes: List<GtfsRoute>){
        Log.d(DEBUG_TAG, "Resetting routes")
        currentRoutes = routes.sortedWith<GtfsRoute>(linesComparator)

        if (linesAdapter!=null){

            var selGtfsRoute = viewModel.getRouteIDQueried()
            var selRouteIdx = 0
            if(selGtfsRoute == null){
                selGtfsRoute =""
            }
            Log.d(DEBUG_TAG, "Setting routes, selected route gtfsID: $selGtfsRoute")
            val adapter = linesAdapter!!

            if (adapter.isEmpty) {
                Log.d(DEBUG_TAG, "Lines adapter is empty")
            }
            else{
                adapter.clear()

            }
            adapter.addAll(currentRoutes.map { r -> r.shortName })
            adapter.notifyDataSetChanged()
            for(j in currentRoutes.indices){
                val route = currentRoutes[j]
                if (route.gtfsId == selGtfsRoute) {
                    selRouteIdx = j
                    Log.d(DEBUG_TAG, "Route $selGtfsRoute has index $j")
                }
            }
            linesSpinner.setSelection(selRouteIdx)
            //
        }
        /*
        linesAdapter?.clear()
        linesAdapter?.addAll(currentRoutes.map { r -> r.shortName })
        linesAdapter?.notifyDataSetChanged()
         */
    }

    private fun setCurrentStops(stops: List<Stop>){

        Log.d(DEBUG_TAG, "Setting stops from: "+viewModel.currentPatternStops.value)
        val orderBy = viewModel.currentPatternStops.value!!.withIndex().associate{it.value.stopGtfsId to it.index}
        val stopsSorted = stops.sortedBy { s -> orderBy[s.gtfsID] }
        val numStops = stopsSorted.size
        Log.d(DEBUG_TAG, "RecyclerView adapter is: ${stopsRecyclerView.adapter}")

        var setNewAdapter = true
        if(stopsRecyclerView.adapter is StopRecyclerAdapter){
            val adapter = stopsRecyclerView.adapter as StopRecyclerAdapter
            if(adapter.stops.size == stopsSorted.size && (adapter.stops.get(0).gtfsID == stopsSorted.get(0).gtfsID)
                && (adapter.stops.get(numStops-1).gtfsID == stopsSorted.get(numStops-1).gtfsID)
            ){
                Log.d(DEBUG_TAG, "Found same stops on recyclerview")
                setNewAdapter = false
            }
            /*else {
                Log.d(DEBUG_TAG, "Found adapter on recyclerview, but not the same stops")
                adapter.stops = stopsSorted
                adapter.notifyDataSetChanged()
            }*/

        }
        if(setNewAdapter){
            stopsRecyclerView.adapter = StopRecyclerAdapter(
                stopsSorted, adapterListener, StopRecyclerAdapter.Use.LINES,
                NameCapitalize.FIRST
            )
        }



    }

    private fun setPatternAndReqStops(patternWithStops: MatoPatternWithStops){
        Log.d(DEBUG_TAG, "Requesting stops for pattern ${patternWithStops.pattern.code}")
        //currentPatternStops = patternWithStops.stopsIndices.sortedBy { i-> i.order }
        viewModel.currentPatternStops.value =  patternWithStops.stopsIndices.sortedBy { i-> i.order }
        viewModel.selectedPatternLiveData.value = patternWithStops
        viewModel.requestStopsForPatternWithStops(patternWithStops)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        Log.d("BusTO-LinesFragment", "Creating context menu ")


        if (v.id == R.id.patternStopsRecyclerView) {
            // if we aren't attached to activity, return null
            if (activity == null) return
            val inflater = requireActivity().menuInflater
            inflater.inflate(R.menu.menu_line_item, menu)

        }
    }


    override fun onContextItemSelected(item: MenuItem): Boolean {

        if (stopsRecyclerView.getAdapter() !is StopRecyclerAdapter) return false
        val adapter =stopsRecyclerView.adapter as StopRecyclerAdapter
        val stop = adapter.stops.get(adapter.getPosition())

        val acId = item.itemId
        if(acId == R.id.action_view_on_map){
            // view on the map
            if ((stop.latitude == null) or (stop.longitude == null) or (mListener == null) ) {
                Toast.makeText(context, R.string.cannot_show_on_map_no_position, Toast.LENGTH_SHORT).show()
                return true
            }
            mListener!!.showMapCenteredOnStop(stop)
            return true
        } else if (acId == R.id.action_show_arrivals){
            mListener?.requestArrivalsForStopID(stop.ID)
            return true
        }
        return false
    }

    override fun onStop() {
        super.onStop()
        Log.d(DEBUG_TAG, "Fragment stopped")

        recyclerViewState = stopsRecyclerView.layoutManager?.onSaveInstanceState()
        patternsSpinnerState = patternsSpinner.onSaveInstanceState()
    }


    override fun onStart() {
        super.onStart()

        Log.d(DEBUG_TAG, "OnStart, selected line spinner pos: ${linesSpinner.selectedItemPosition}")
        Log.d(DEBUG_TAG, "OnStart, selected patterns spinner pos: ${patternsSpinner.selectedItemPosition}")

        if (recyclerViewState!=null){
            stopsRecyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
        }
        if(patternsSpinnerState!=null){
            patternsSpinner.onRestoreInstanceState(patternsSpinnerState)
        }
    }
    /*
    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(DEBUG_TAG, "Fragment view destroyed")

    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(DEBUG_TAG, "Fragment destroyed")
    }
    */

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        Log.d(DEBUG_TAG, "OnViewStateRes, bundled saveinstancestate: $savedInstanceState")
        Log.d(DEBUG_TAG, "OnViewStateRes, selected line spinner pos: ${linesSpinner.selectedItemPosition}")
        Log.d(DEBUG_TAG, "OnViewStateRes, selected patterns spinner pos: ${patternsSpinner.selectedItemPosition}")
    }

}