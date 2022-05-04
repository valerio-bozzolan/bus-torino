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
import it.reyboz.bustorino.data.gtfs.PatternStop
import it.reyboz.bustorino.util.LinesNameSorter
import it.reyboz.bustorino.util.PatternWithStopsSorter

class LinesFragment : ScreenBaseFragment() {

    companion object {
        fun newInstance(){
            val fragment = LinesFragment()
        }
        const val DEBUG_TAG="BusTO-LinesFragment"
        const val FRAGMENT_TAG="LinesFragment"

        val patternStopsComparator = PatternWithStopsSorter()
    }


    private lateinit var viewModel: LinesViewModel

    private lateinit var linesSpinner: Spinner
    private lateinit var patternsSpinner: Spinner

    private lateinit var currentRoutes: List<GtfsRoute>
    private lateinit var currentPatterns: List<MatoPatternWithStops>
    private lateinit var currentPatternStops: List<PatternStop>

    private lateinit var routeDescriptionTextView: TextView
    private lateinit var stopsRecyclerView: RecyclerView

    private var linesAdapter: ArrayAdapter<String>? = null
    private var patternsAdapter: ArrayAdapter<String>? = null
    private var mListener: CommonFragmentListener? = null

    private val linesNameSorter = LinesNameSorter()
    private val linesComparator = Comparator<GtfsRoute> { a,b ->
        return@Comparator linesNameSorter.compare(a.shortName, b.shortName)
    }
    private var firstClick = true;

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

        if(context!=null) {
            patternsAdapter = ArrayAdapter(context!!, android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
            patternsSpinner.adapter = patternsAdapter
            linesAdapter = ArrayAdapter(context!!, android.R.layout.simple_spinner_dropdown_item, ArrayList<String>())
            linesSpinner.adapter = linesAdapter


            linesSpinner.onItemSelectedListener = object: OnItemSelectedListener{
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    val selRoute = currentRoutes.get(pos)

                    routeDescriptionTextView.text = selRoute.longName
                    val oldRoute = viewModel.getRouteIDQueried()
                    val resetSpinner = (oldRoute != null) && (oldRoute.trim() != selRoute.gtfsId.trim())
                    Log.d(DEBUG_TAG, "Selected route: ${selRoute.gtfsId}, reset spinner: $resetSpinner")
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
                    val patternWithStops = currentPatterns.get(position)
                    //
                    setPatternAndReqStops(patternWithStops)

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
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(LinesViewModel::class.java)

        //val lines = viewModel.();
        viewModel.routesGTTLiveData.observe(this) {
            setRoutes(it)
        }

        viewModel.patternsWithStopsByRouteLiveData.observe(this){
            patterns ->
            run {
                currentPatterns = patterns.sortedBy { p-> p.pattern.code }
                        //patterns. //sortedBy {-1*it.stopsIndices.size}// "${p.pattern.directionId} - ${p.pattern.headsign}" }
                patternsAdapter?.let {
                    it.clear()
                    it.addAll(currentPatterns.map { p->"${p.pattern.directionId} - ${p.pattern.headsign}" })
                    it.notifyDataSetChanged()
                }

                val  pos = patternsSpinner.selectedItemPosition
                //might be possible that the selectedItem is different (larger than list size)
                if(pos!= INVALID_POSITION && pos >= 0 && (pos < currentPatterns.size)){
                    setPatternAndReqStops(currentPatterns[pos])
                }

            }
        }

        viewModel.stopsForPatternLiveData.observe(this){stops->
            Log.d("BusTO-LinesFragment", "Setting stops from DB")
            setCurrentStops(stops)
        }

    }

    override fun getBaseViewForSnackBar(): View? {
        return null
    }

    private fun setRoutes(routes: List<GtfsRoute>){
        currentRoutes = routes.sortedWith<GtfsRoute>(linesComparator)

        linesAdapter?.clear()
        linesAdapter?.addAll(currentRoutes.map { r -> r.shortName })
        linesAdapter?.notifyDataSetChanged()
    }

    private fun setCurrentStops(stops: List<Stop>){

        val orderBy = currentPatternStops.withIndex().associate{it.value.stopGtfsId to it.index}
        val stopsSorted = stops.sortedBy { s -> orderBy[s.gtfsID] }
        val numStops = stopsSorted.size
        Log.d(DEBUG_TAG, "RecyclerView adapter is: ${stopsRecyclerView.adapter}")

        var setNewAdapter = true;
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
        currentPatternStops = patternWithStops.stopsIndices.sortedBy { i-> i.order }

        viewModel.requestStopsForPatternWithStops(patternWithStops)
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        Log.d("BusTO-LinesFragment", "Creating context menu ")


        if (v.id == R.id.patternStopsRecyclerView) {
            // if we aren't attached to activity, return null
            if (activity == null) return
            val inflater = activity!!.menuInflater
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
            mListener?.requestArrivalsForStopID(stop.ID);
            return true
        }
        return false
    }

}