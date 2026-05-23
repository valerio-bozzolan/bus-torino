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
package it.reyboz.bustorino.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.util.Pair
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import it.reyboz.bustorino.BuildConfig
import it.reyboz.bustorino.R
import it.reyboz.bustorino.adapters.ArrivalsStopAdapter
import it.reyboz.bustorino.adapters.SquareStopAdapter
import it.reyboz.bustorino.backend.*
import it.reyboz.bustorino.data.DatabaseUpdate
import it.reyboz.bustorino.fragments.NearbyArrivalsDownloader.ArrivalsListener
import it.reyboz.bustorino.middleware.AutoFitGridLayoutManager
import it.reyboz.bustorino.middleware.FusedNativeLocationProvider
import it.reyboz.bustorino.middleware.FusedNativeLocationProvider.LocationUpdateListener
import it.reyboz.bustorino.util.Permissions.Companion.anyLocationPermissionsGranted
import it.reyboz.bustorino.util.Permissions.Companion.bothLocationPermissionsGranted
import it.reyboz.bustorino.util.StopSorterByDistance
import it.reyboz.bustorino.viewmodels.NearbyStopsViewModel
import java.util.*
import kotlin.math.min

class NearbyStopsFragment : ScreenBaseFragment() {
    override fun getBaseViewForSnackBar(): View? {
        return null
    }

    enum class FragType(val num: Int) {
        STOPS(1), ARRIVALS(2);

        companion object {
            @JvmStatic
            fun fromNum(i: Int): FragType {
                when (i) {
                    1 -> return STOPS
                    2 -> return ARRIVALS
                    else -> throw IllegalArgumentException("type not recognized")
                }
            }
        }
    }

    private enum class LocationShowingStatus {
        SEARCHING, FIRST_FIX, DISABLED, NO_PERMISSION
    }

    private var mListener: FragmentListenerMain? = null

    private var fragment_type = FragType.STOPS

    private lateinit var gridRecyclerView: RecyclerView

    private var dataAdapter: SquareStopAdapter? = null
    private var gridLayoutManager: AutoFitGridLayoutManager? = null
    private var lastPosition: GPSPoint? = null
    private var circlingProgressBar: ProgressBar? = null
    private lateinit var flatProgressBar: ProgressBar

    //protected SharedPreferences globalSharedPref;
    //private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private var messageTextView: TextView? = null
    private var titleTextView: TextView? = null
    private var loadingTextView: TextView? = null
    private var scrollListener: CommonScrollListener? = null
    private var switchButton: AppCompatButton? = null
    private var firstLocForStops = true
    private var firstLocForArrivals = true
    private var stopsMaxDistance = -3
    private var stopsMinNumber = -1

    //These are useful for the case of nearby arrivals
    private var arrivalsManager: NearbyArrivalsDownloader? = null
    private var arrivalsStopAdapter: ArrivalsStopAdapter? = null

    private var currentNearbyStops = ArrayList<Stop>()

    private var showingStatus = LocationShowingStatus.NO_PERMISSION
    private var isLocationEnabled = false

    private val locationUpdateListener: LocationUpdateListener = object : LocationUpdateListener {
        override fun onLocationUpdate(location: Location) {
            updateLocationViewModel(location)
        }

        override fun onFusedStatusChanged(isEnabled: Boolean) {
            Log.d(DEBUG_TAG, "Location provider is enabled: " + isEnabled)
            isLocationEnabled = isEnabled
            if (isEnabled) {
                setShowingStatus(LocationShowingStatus.SEARCHING)
            } else {
                setShowingStatus(LocationShowingStatus.DISABLED)
            }
        }
    }
    private val locationOptionsArrivals = FusedNativeLocationProvider.Options(5 * 1000L, 50f)
    private val locationOptionsStops = FusedNativeLocationProvider.Options(1000L, 5f)


    /*
    TODO: we do not request the permission in this fragment, only showing it when we have the location. Request position if this changes.
    private final ActivityResultLauncher<String[]> permissionsResultLauncher = getPositionRequestLauncher(
            granted ->{

            }
    );
     */
    private var locationProvider: FusedNativeLocationProvider? = null


    /*private val arrivalsListener: ArrivalsListener = object : ArrivalsListener {
        override fun setProgress(completedRequests: Int, pendingRequests: Int) {
            if (pendingRequests == 0) {
                flatProgressBar.setIndeterminate(true)
                flatProgressBar.setVisibility(View.GONE)
            } else {
                flatProgressBar.setIndeterminate(false)
                flatProgressBar.progress = completedRequests
            }
        }

        /*override fun onAllRequestsCancelled() {
            if (flatProgressBar != null) flatProgressBar!!.setVisibility(View.GONE)
        }

         */

        override fun showCompletedArrivals(completedPalinas: ArrayList<Palina>) {
            showArrivalsInRecycler(completedPalinas)
        }
    }

     */

    //ViewModel
    private val viewModel : NearbyStopsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let{

            setFragmentType(FragType.fromNum(it.getInt(FRAGMENT_TYPE_KEY)))
        }
        //locManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        //fragmentLocationListener = new FragmentLocationListener();
        if (getContext() != null) {
            //globalSharedPref = getContext().getSharedPreferences(getString(R.string.mainSharedPreferences), Context.MODE_PRIVATE);
            //globalSharedPref.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        }

        //NearbyArrivalsDownloader nearbyArrivalsDownloader = new NearbyArrivalsDownloader(getContext().getApplicationContext(), arrivalsListener);
        locationProvider = FusedNativeLocationProvider(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        if (getContext() == null) throw RuntimeException()
        val root = inflater.inflate(R.layout.fragment_nearby_stops, container, false)
        gridRecyclerView = root.findViewById<RecyclerView>(R.id.stopGridRecyclerView)
        gridLayoutManager = AutoFitGridLayoutManager(
            requireContext().getApplicationContext(),
            utils.convertDipToPixels(getContext(), COLUMN_WIDTH_DP.toFloat()).toInt()
        )
        gridRecyclerView.setLayoutManager(gridLayoutManager)
        gridRecyclerView.setHasFixedSize(false)
        circlingProgressBar = root.findViewById<ProgressBar>(R.id.circularProgressBar)
        flatProgressBar = root.findViewById(R.id.horizontalProgressBar)
        messageTextView = root.findViewById<TextView>(R.id.messageTextView)
        titleTextView = root.findViewById<TextView>(R.id.titleTextView)
        loadingTextView = root.findViewById<TextView>(R.id.positionLoadingTextView)
        switchButton = root.findViewById<AppCompatButton>(R.id.switchButton)

        scrollListener = CommonScrollListener(mListener, false)
        switchButton!!.setOnClickListener(View.OnClickListener { v: View? -> switchFragmentType() })
        if (BuildConfig.DEBUG) Log.d(DEBUG_TAG, "onCreateView")

        val appContext = requireContext().applicationContext
        DatabaseUpdate.watchUpdateWorkStatus(context, this){ workInfos ->
                if (workInfos.isEmpty()) {
                    viewModel.setDBUpdateRunning(false)
                    return@watchUpdateWorkStatus
                }

                val wi = workInfos.get(0)
                if (wi.state == WorkInfo.State.RUNNING && locationProvider!!.isRunning()) {
                    locationProvider!!.stopUpdates()
                    viewModel.setDBUpdateRunning(true)
                } else {
                    //start the request
                    if (bothLocationPermissionsGranted(requireContext())) {
                        if (!locationProvider!!.isRunning()) {
                            startLocationUpdatesByType()
                        }
                    } else {
                        setShowingStatus(LocationShowingStatus.NO_PERMISSION)
                    }

                    viewModel.setDBUpdateRunning(false)
                    //actually restart request
                }
        }


        if (anyLocationPermissionsGranted(appContext)) {
            setShowingStatus(LocationShowingStatus.SEARCHING)
        } else {
            setShowingStatus(LocationShowingStatus.NO_PERMISSION)
        }
        //add location listener
        locationProvider!!.addListener(locationUpdateListener)

        return root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        gridRecyclerView.setVisibility(View.INVISIBLE)
        gridRecyclerView.addOnScrollListener(scrollListener!!)
        mListener?.readyGUIfor(FragmentKind.NEARBY_STOPS)

        //observe the livedata
        viewModel.stopsAtDistance.observe(getViewLifecycleOwner()) {stops ->
            Log.d(DEBUG_TAG, "Received " + stops.size + " stops nearby")
            var distance = viewModel.distanceMtLiveData.getValue()
            if (distance == null) {
                distance = 40
            }
            if ((stops.size < stopsMinNumber && distance <= stopsMaxDistance)) {
                viewModel.setDistance(distance + 40)
                //viewModel.requestStopsAtDistance(distance, true);
                //Log.d(DEBUG_TAG, "Doubling distance now!");
                return@observe  // THIS WORKS AS AN `else`
            }
            if (!stops.isEmpty()) {
                currentNearbyStops = stops
                showStopsInViews(currentNearbyStops, lastPosition)
            }
        }

        viewModel.downloadingArrivals.observe(viewLifecycleOwner){ running ->
            if(!running) flatProgressBar.visibility = View.GONE
            else flatProgressBar.visibility = View.VISIBLE
        }
        viewModel.progressPerc.observe(viewLifecycleOwner){ progress ->
            flatProgressBar.isIndeterminate = false
            flatProgressBar.progress = progress
            flatProgressBar.max = 100

            if (progress<100){
                flatProgressBar.visibility = View.VISIBLE
            }

        }

        viewModel.arrivalsDecoupled.observe(viewLifecycleOwner){ stoprouteList ->
            if (getContext() == null) {
                Log.e(DEBUG_TAG, "Trying to show arrivals in Recycler but we're not attached")
                return@observe
            }
            if (firstLocForArrivals) {
                arrivalsStopAdapter = ArrivalsStopAdapter(stoprouteList, mListener, getContext(), lastPosition!!)
                gridRecyclerView.setAdapter(arrivalsStopAdapter)
                firstLocForArrivals = false
            } else {
                arrivalsStopAdapter!!.setRoutesPairListAndPosition(stoprouteList)
            }

            //arrivalsStopAdapter.notifyDataSetChanged();
            showRecyclerHidingLoadMessage()
            if (mListener != null) mListener!!.readyGUIfor(FragmentKind.NEARBY_ARRIVALS)
        }
    }


    /**
     * Internal bit used to start location updates
     */
    private fun startLocationUpdatesByType() {
        when (fragment_type) {
            FragType.STOPS -> locationProvider!!.startUpdates(locationOptionsStops)
            FragType.ARRIVALS -> locationProvider!!.startUpdates(locationOptionsArrivals)
        }
    }


    /**
     * Use this method to set the fragment type
     * @param type the type, TYPE_ARRIVALS or TYPE_STOPS
     */
    private fun setFragmentType(type: FragType) {
        val isChanged = fragment_type != type
        this.fragment_type = type
        /*switch(type){
            case ARRIVALS:
                TIME_INTERVAL_REQUESTS = 5*1000;
                break;
            case STOPS:
                TIME_INTERVAL_REQUESTS = 1000;

        }

         */
        if (isChanged) {
            startLocationUpdatesByType()
            setShowingStatus(LocationShowingStatus.SEARCHING)
        }
    }

    /**
     * Set the location in the view model if it is good
     * @param location new location
     */
    private fun updateLocationViewModel(location: Location, accuracy: Float = 150f) {
        if (location.getAccuracy() < accuracy) {
            lastPosition = GPSPoint(location.getLatitude(), location.getLongitude())
            //viewModel.requestStopsAtDistance(location.getLatitude(), location.getLongitude(), distance, true);
            viewModel.setLastLocation(location)
        }
    }

    private fun setShowingStatus(newStatus: LocationShowingStatus) {
        var newStatus = newStatus
        if (BuildConfig.DEBUG) Log.d(DEBUG_TAG, "Asked to set showing status : $newStatus")
        if (newStatus == showingStatus) {
            return
        }
        if (!isLocationEnabled && newStatus != LocationShowingStatus.NO_PERMISSION) {
            Log.d(DEBUG_TAG, "asked to show status: $newStatus but the position is disabled")
            newStatus = LocationShowingStatus.DISABLED
        }

        when (newStatus) {
            LocationShowingStatus.FIRST_FIX -> {
                circlingProgressBar!!.setVisibility(View.GONE)
                loadingTextView!!.setVisibility(View.GONE)
                gridRecyclerView.setVisibility(View.VISIBLE)
                messageTextView!!.setVisibility(View.GONE)
            }

            LocationShowingStatus.NO_PERMISSION -> {
                circlingProgressBar!!.setVisibility(View.GONE)
                loadingTextView!!.setVisibility(View.GONE)
                messageTextView!!.setText(R.string.enable_position_message_nearby)
                messageTextView!!.setVisibility(View.VISIBLE)
            }

            LocationShowingStatus.DISABLED -> {
                //if (showingStatus== LocationShowingStatus.SEARCHING){
                circlingProgressBar!!.setVisibility(View.GONE)
                loadingTextView!!.setVisibility(View.GONE)
                //}
                messageTextView!!.setText(R.string.enable_location_message)
                messageTextView!!.setVisibility(View.VISIBLE)
            }

            LocationShowingStatus.SEARCHING -> {
                circlingProgressBar!!.setVisibility(View.VISIBLE)
                loadingTextView!!.setVisibility(View.VISIBLE)
                gridRecyclerView.setVisibility(View.GONE)
                messageTextView!!.setVisibility(View.GONE)
            }
        }
        showingStatus = newStatus
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentListenerMain) {
            mListener = context as FragmentListenerMain
        } else {
            throw RuntimeException(
                context
                    .toString() + " must implement OnFragmentInteractionListener"
            )
        }
        Log.d(DEBUG_TAG, "OnAttach called")
        //viewModel = ViewModelProvider(this).get<NearbyStopsViewModel?>(NearbyStopsViewModel::class.java)
    }

    override fun onPause() {
        super.onPause()

        //gridRecyclerView.setAdapter(null)
        Log.d(DEBUG_TAG, "On paused called")

        locationProvider!!.stopUpdates()
    }

    override fun onResume() {
        super.onResume()
        //fix view if we were showing the stops or the arrivals
        prepareForFragmentType()
        when (fragment_type) {
            FragType.STOPS -> if (dataAdapter != null) {
                //gridRecyclerView.setAdapter(dataAdapter);
                circlingProgressBar!!.setVisibility(View.GONE)
                loadingTextView!!.setVisibility(View.GONE)
            }

            FragType.ARRIVALS -> if (arrivalsStopAdapter != null) {
                //gridRecyclerView.setAdapter(arrivalsStopAdapter);
                circlingProgressBar!!.setVisibility(View.GONE)
                loadingTextView!!.setVisibility(View.GONE)
            }
        }

        mListener!!.enableRefreshLayout(false)
        Log.d(DEBUG_TAG, "OnResume called")
        if (getContext() == null) {
            Log.e(DEBUG_TAG, "NULL CONTEXT, everything is going to crash now")
            stopsMinNumber = 5
            stopsMaxDistance = 600
            return
        }
        //Re-read preferences
        val shpr = PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext())
        //For some reason, they are all saved as strings
        stopsMaxDistance = shpr.getInt(getString(R.string.pref_key_radius_recents), 600)
        var isMinStopInt = true
        try {
            stopsMinNumber = shpr.getInt(getString(R.string.pref_key_num_recents), 5)
        } catch (ex: ClassCastException) {
            isMinStopInt = false
        }
        if (!isMinStopInt) try {
            stopsMinNumber = shpr.getString(getString(R.string.pref_key_num_recents), "5")!!.toInt()
        } catch (ex: NumberFormatException) {
            stopsMinNumber = 5
        }
        if (BuildConfig.DEBUG) Log.d(
            DEBUG_TAG,
            "Max distance for stops: $stopsMaxDistance, Min number of stops: $stopsMinNumber"
        )

        if (!locationProvider!!.isRunning()) {
            startLocationUpdatesByType()
        }
    }




    override fun onDetach() {
        super.onDetach()
        mListener = null
        if (arrivalsManager != null) arrivalsManager!!.cancelAllRequests()
    }

    /**
     * Display the stops, or run new set of requests for arrivals
     */
    private fun showStopsInViews(stops: ArrayList<Stop>, location: GPSPoint?) {
        if (stops.isEmpty()) {
            setNoStopsLayout()
            return
        }
        if (location == null) {
            // we could do something better, but it's better to do this for now
            return
        }

        /*var minDistance = Double.POSITIVE_INFINITY
        for (s in stops) {
            minDistance = min(minDistance, s.getDistanceFromLocation(location.getLatitude(), location.getLongitude()))
        }

         */


        //quick trial to hopefully always get the stops in the correct order
        Collections.sort<Stop?>(stops, StopSorterByDistance(location))
        when (fragment_type) {
            FragType.STOPS -> showStopsInRecycler(stops)
            FragType.ARRIVALS -> {
                //don't do anything if we're not attached
                /*context?.let{
                    if (arrivalsManager == null) arrivalsManager =
                        NearbyArrivalsDownloader(it.applicationContext, arrivalsListener)
                    arrivalsManager!!.requestArrivalsForStops(stops)
                }

                 */
                viewModel.requestArrivalsForStops(stops)
            }
        }
    }

    /**
     * To enable targeting from the Button
     */
    fun switchFragmentType(v: View?) {
        switchFragmentType()
    }

    /**
     * Call when you need to switch the type of fragment
     */
    private fun switchFragmentType() {
        when (fragment_type) {
            FragType.ARRIVALS -> setFragmentType(FragType.STOPS)
            FragType.STOPS -> setFragmentType(FragType.ARRIVALS)
            else -> {}
        }
        prepareForFragmentType()
        //locManager.removeLocationRequestFor(fragmentLocationListener);
        //locManager.addLocationRequestFor(fragmentLocationListener);
        if (lastPosition != null) {
            // we have at least one fix on the position
            showStopsInViews(currentNearbyStops, lastPosition)
        }
    }

    /**
     * Prepare the views for the set fragment type
     */
    private fun prepareForFragmentType() {
        if (fragment_type == FragType.STOPS) {
            switchButton!!.setText(getString(R.string.show_arrivals))
            titleTextView!!.setText(getString(R.string.nearby_stops_message))
            if (arrivalsManager != null) arrivalsManager!!.cancelAllRequests()
            if (dataAdapter != null) gridRecyclerView!!.setAdapter(dataAdapter)
        } else if (fragment_type == FragType.ARRIVALS) {
            titleTextView!!.setText(getString(R.string.nearby_arrivals_message))
            switchButton!!.setText(getString(R.string.show_stops))
            if (arrivalsStopAdapter != null) gridRecyclerView!!.setAdapter(arrivalsStopAdapter)
        }
    }

    //useful methods
    /**//// GUI METHODS //////// */
    private fun showStopsInRecycler(stops: MutableList<Stop>?) {
        if (firstLocForStops) {
            dataAdapter = SquareStopAdapter(stops, mListener, lastPosition)
            gridRecyclerView!!.setAdapter(dataAdapter)
            firstLocForStops = false
        } else {
            dataAdapter!!.setStops(stops)
            dataAdapter!!.setUserPosition(lastPosition)
        }
        dataAdapter!!.notifyDataSetChanged()

        //showRecyclerHidingLoadMessage();
        if (gridRecyclerView!!.getVisibility() != View.VISIBLE) {
            circlingProgressBar!!.setVisibility(View.GONE)
            loadingTextView!!.setVisibility(View.GONE)
            gridRecyclerView!!.setVisibility(View.VISIBLE)
        }
        messageTextView!!.setVisibility(View.GONE)

        if (mListener != null) mListener!!.readyGUIfor(FragmentKind.NEARBY_STOPS)
    }

    private fun showArrivalsInRecycler(routesPairList: List<Pair<Stop, Route>>) {


    }

    private fun setNoStopsLayout() {
        messageTextView!!.setVisibility(View.VISIBLE)
        messageTextView!!.setText(R.string.no_stops_nearby)
        circlingProgressBar!!.setVisibility(View.GONE)
        loadingTextView!!.setVisibility(View.GONE)
    }

    /**
     * Does exactly what is says on the tin
     */
    private fun showRecyclerHidingLoadMessage() {
        if (gridRecyclerView.getVisibility() != View.VISIBLE) {
            circlingProgressBar!!.setVisibility(View.GONE)
            loadingTextView!!.setVisibility(View.GONE)
            gridRecyclerView.setVisibility(View.VISIBLE)
        }
        messageTextView!!.setVisibility(View.GONE)
    } /*
     * Local locationListener, to use for the GPS
     */
    /*
    class FragmentLocationListener implements LocationListenerCompat {

        private long lastUpdateTime = -1;
        public boolean isRegistered = false;

        @Override
        public void onLocationChanged(@NonNull Location location) {
            if(viewModel==null){
                return;
            }
            if(location.getAccuracy()<200) {

               lastPosition = new GPSPoint(location.getLatitude(), location.getLongitude());
               //viewModel.requestStopsAtDistance(location.getLatitude(), location.getLongitude(), distance, true);
                viewModel.setLastLocation(location);
            }
            lastUpdateTime = System.currentTimeMillis();
            //Log.d("BusTO:NearPositListen","can start request for stops: "+ !dbUpdateRunning);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            Log.d(DEBUG_TAG, "Location provider "+provider+" enabled");
            if(provider.equals(LocationManager.GPS_PROVIDER)){
                setShowingStatus(LocationShowingStatus.SEARCHING);
            }
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            Log.d(DEBUG_TAG, "Location provider "+provider+" disabled");
            if(provider.equals(LocationManager.GPS_PROVIDER)) {
               setShowingStatus(LocationShowingStatus.DISABLED);
            }
        }

        @Override
        public void onStatusChanged(@NonNull String provider, int status, @Nullable Bundle extras) {
            LocationListenerCompat.super.onStatusChanged(provider, status, extras);
        }
    }

     */

    companion object {
        private const val DEBUG_TAG = "NearbyStopsFragment"
        private const val FRAGMENT_TYPE_KEY = "FragmentType"
        const val FRAGMENT_TAG: String = "NearbyStopsFrag"

        const val COLUMN_WIDTH_DP: Int = 250


        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         * @return A new instance of fragment NearbyStopsFragment.
         */
        @JvmStatic
        fun newInstance(type: FragType): NearbyStopsFragment {
            //if(fragmentType != TYPE_STOPS && fragmentType != TYPE_ARRIVALS )
            //    throw new IllegalArgumentException("WRONG KIND OF FRAGMENT USED");
            val fragment = NearbyStopsFragment()
            val args = Bundle(1)
            args.putInt(FRAGMENT_TYPE_KEY, type.num)
            fragment.setArguments(args)
            return fragment
        }
    }
}
