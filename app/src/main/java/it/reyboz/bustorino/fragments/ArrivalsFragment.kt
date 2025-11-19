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

import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.viewModels
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.reyboz.bustorino.R
import it.reyboz.bustorino.adapters.PalinaAdapter
import it.reyboz.bustorino.adapters.PalinaAdapter.PalinaClickListener
import it.reyboz.bustorino.adapters.RouteOnlyLineAdapter
import it.reyboz.bustorino.backend.*
import it.reyboz.bustorino.backend.DBStatusManager.OnDBUpdateStatusChangeListener
import it.reyboz.bustorino.backend.Passaggio.Source
import it.reyboz.bustorino.data.AppDataProvider
import it.reyboz.bustorino.data.NextGenDB
import it.reyboz.bustorino.data.UserDB
import it.reyboz.bustorino.middleware.AsyncStopFavoriteAction
import it.reyboz.bustorino.util.LinesNameSorter
import it.reyboz.bustorino.viewmodels.ArrivalsViewModel
import java.util.*


class ArrivalsFragment : ResultBaseFragment(), LoaderManager.LoaderCallbacks<Cursor> {
    private var DEBUG_TAG = DEBUG_TAG_ALL
    private lateinit var stopID: String
        //private set
    private var stopName: String? = null
    private var prefs: DBStatusManager? = null
    private var listener: OnDBUpdateStatusChangeListener? = null
    private var justCreated = false
    private var lastUpdatedPalina: Palina? = null
    private var needUpdateOnAttach = false
    private var fetchersChangeRequestPending = false
    private var stopIsInFavorites = false

    //Views
    protected lateinit var addToFavorites: ImageButton
    protected lateinit var timesSourceTextView: TextView
    protected lateinit var messageTextView: TextView
    protected lateinit  var arrivalsRecyclerView: RecyclerView
    private var mListAdapter: PalinaAdapter? = null

    private lateinit var resultsLayout : LinearLayout
    private lateinit var loadingMessageTextView: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var howDoesItWorkTextView: TextView
    private lateinit var hideHintButton: Button


    //private NestedScrollView theScrollView;
    protected lateinit var noArrivalsRecyclerView: RecyclerView
    private var noArrivalsAdapter: RouteOnlyLineAdapter? = null
    private var noArrivalsTitleView: TextView? = null
    private var layoutManager: GridLayoutManager? = null

    //private View canaryEndView;
    private var fetchers: List<ArrivalsFetcher?> =  ArrayList()
    private val arrivalsViewModel : ArrivalsViewModel by viewModels()


    private var reloadOnResume = true

    fun getStopID() = stopID

    private val palinaClickListener: PalinaClickListener = object : PalinaClickListener {
        override fun showRouteFullDirection(route: Route) {
            var routeName: String?
            Log.d(DEBUG_TAG, "Make toast for line " + route.name)


            routeName = FiveTNormalizer.routeInternalToDisplay(route.name)
            if (routeName == null) {
                routeName = route.displayCode
            }
            if (context == null) Log.e(DEBUG_TAG, "Touched on a route but Context is null")
            else if (route.destinazione == null || route.destinazione.length == 0) {
                Toast.makeText(
                    context,
                    getString(R.string.route_towards_unknown, routeName), Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    context,
                    getString(R.string.route_towards_destination, routeName, route.destinazione), Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun requestShowingRoute(route: Route) {
            Log.d(
                DEBUG_TAG, """Need to show line for route: gtfsID ${route.gtfsId} name ${route.name}"""
            )
            if (route.gtfsId != null) {
                mListener.showLineOnMap(route.gtfsId, stopID)
            } else {
                val gtfsID = FiveTNormalizer.getGtfsRouteID(route)
                Log.d(DEBUG_TAG, "GtfsID for route is: $gtfsID")
                mListener.showLineOnMap(gtfsID, stopID)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        stopID = requireArguments().getString(KEY_STOP_ID) ?: ""
        DEBUG_TAG = DEBUG_TAG_ALL + " " + stopID

        //this might really be null
        stopName = requireArguments().getString(KEY_STOP_NAME)
        val arrivalsFragment = this
        listener = object : OnDBUpdateStatusChangeListener {
            override fun onDBStatusChanged(updating: Boolean) {
                if (!updating) {
                    loaderManager.restartLoader(
                        loaderFavId,
                        arguments, arrivalsFragment
                    )
                } else {
                    val lm = loaderManager
                    lm.destroyLoader(loaderFavId)
                    lm.destroyLoader(loaderStopId)
                }
            }

            override fun defaultStatusValue(): Boolean {
                return true
            }
        }
        prefs = DBStatusManager(requireContext().applicationContext, listener)
        justCreated = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_arrivals, container, false)
        messageTextView = root.findViewById(R.id.messageTextView)
        addToFavorites = root.findViewById(R.id.addToFavorites)
        // "How does it work part"
        howDoesItWorkTextView = root.findViewById(R.id.howDoesItWorkTextView)
        hideHintButton = root.findViewById(R.id.hideHintButton)
        //TODO: Hide this layout at the beginning, show it later
        resultsLayout  = root.findViewById(R.id.resultsLayout)
        loadingMessageTextView = root.findViewById(R.id.loadingMessageTextView)
        progressBar = root.findViewById(R.id.circularProgressBar)

        hideHintButton.setOnClickListener { v: View? -> this.onHideHint(v) }

        //theScrollView = root.findViewById(R.id.arrivalsScrollView);
        // recyclerview holding the arrival times
        arrivalsRecyclerView = root.findViewById(R.id.arrivalsRecyclerView)
        val manager = LinearLayoutManager(context)
        arrivalsRecyclerView.setLayoutManager(manager)
        val mDividerItemDecoration = DividerItemDecoration(
            arrivalsRecyclerView.context,
            manager.orientation
        )
        arrivalsRecyclerView.addItemDecoration(mDividerItemDecoration)
        timesSourceTextView = root.findViewById(R.id.timesSourceTextView)
        timesSourceTextView.setOnLongClickListener { view: View? ->
            if (!fetchersChangeRequestPending) {
                rotateFetchers()
                //Show we are changing provider
                timesSourceTextView.setText(R.string.arrival_source_changing)

                //mListener.requestArrivalsForStopID(stopID)
                requestArrivalsForTheFragment()
                fetchersChangeRequestPending = true
                return@setOnLongClickListener true
            }
            false
        }
        timesSourceTextView.setOnClickListener(View.OnClickListener { view: View? ->
            Toast.makeText(
                context, R.string.change_arrivals_source_message, Toast.LENGTH_SHORT
            )
                .show()
        })
        //Button
        addToFavorites.setClickable(true)
        addToFavorites.setOnClickListener(View.OnClickListener { v: View? ->
            // add/remove the stop in the favorites
            toggleLastStopToFavorites()
        })

        val displayName = requireArguments().getString(STOP_TITLE)
        if (displayName != null) setTextViewMessage(
            String.format(
                getString(R.string.passages), displayName
            )
        )


        val probablemessage = requireArguments().getString(MESSAGE_TEXT_VIEW)
        if (probablemessage != null) {
            //Log.d("BusTO fragment " + this.getTag(), "We have a possible message here in the savedInstaceState: " + probablemessage);
            messageTextView.setText(probablemessage)
            messageTextView.setVisibility(View.VISIBLE)
        }
        //no arrivals stuff
        noArrivalsRecyclerView = root.findViewById(R.id.noArrivalsRecyclerView)
        layoutManager = GridLayoutManager(context, 60)
        layoutManager!!.spanSizeLookup = object : SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return 12
            }
        }
        noArrivalsRecyclerView.setLayoutManager(layoutManager)
        noArrivalsTitleView = root.findViewById(R.id.noArrivalsMessageTextView)

        //canaryEndView = root.findViewById(R.id.canaryEndView);

        /*String sourcesTextViewData = getArguments().getString(SOURCES_TEXT);
        if (sourcesTextViewData!=null){
            timesSourceTextView.setText(sourcesTextViewData);
        }*/
        //need to do this when we recreate the fragment but we haven't updated the arrival times
        lastUpdatedPalina?.let { showArrivalsSources(it) }
        /*if (lastUpdatedPalina?.queryAllRoutes() != null && lastUpdatedPalina!!.queryAllRoutes()!!.size >0){
            showArrivalsSources(lastUpdatedPalina!!)
        } else{
            Log.d(DEBUG_TAG, "No routes names")
        }

         */



        arrivalsViewModel.palinaLiveData.observe(viewLifecycleOwner){
            mListener.toggleSpinner(false)
            if(arrivalsViewModel.resultLiveData.value==Fetcher.Result.OK){
                //the result is true
                changeUIFirstSearchActive(false)
                updateFragmentData(it)
            } else{
                progressBar.visibility=View.INVISIBLE
                // Avoid showing this ugly message if we have found the stop, clearly it exists but GTT doesn't provide arrival times
                if (stopName==null)
                    loadingMessageTextView.text = getString(R.string.no_bus_stop_have_this_name)
                else
                    loadingMessageTextView.text = getString(R.string.no_arrivals_stop)
            }

        }

        arrivalsViewModel.sourcesLiveData.observe(viewLifecycleOwner){
            Log.d(DEBUG_TAG, "Using arrivals source: $it")
            val srcString = getDisplayArrivalsSource(it,requireContext())
            loadingMessageTextView.text = getString(R.string.searching_arrivals_fmt, srcString)
        }

        arrivalsViewModel.resultLiveData.observe(viewLifecycleOwner){res ->
            when (res) {
                Fetcher.Result.OK -> {}
                Fetcher.Result.CLIENT_OFFLINE -> showToastMessage(R.string.network_error, true)
                Fetcher.Result.SERVER_ERROR -> {
                    if (utils.isConnected(context)) {
                        showToastMessage(R.string.parsing_error, true)
                    } else {
                        showToastMessage(R.string.network_error, true)
                    }
                    showToastMessage(R.string.internal_error,true)
                }

                Fetcher.Result.PARSER_ERROR -> showShortToast(R.string.internal_error)
                Fetcher.Result.QUERY_TOO_SHORT -> showShortToast(R.string.query_too_short)
                Fetcher.Result.EMPTY_RESULT_SET -> showShortToast(R.string.no_arrivals_stop)

                Fetcher.Result.NOT_FOUND -> showShortToast(R.string.no_bus_stop_have_this_name)
                else -> showShortToast(R.string.internal_error)
            }
        }
        return root
    }


    private fun showShortToast(id: Int) = showToastMessage(id,true)


    private fun changeUIFirstSearchActive(yes: Boolean){
        if(yes){
            resultsLayout.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
            loadingMessageTextView.visibility = View.VISIBLE
        } else{
            resultsLayout.visibility = View.VISIBLE
            progressBar.visibility = View.GONE
            loadingMessageTextView.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        val loaderManager = loaderManager
        Log.d(DEBUG_TAG, "OnResume, justCreated $justCreated, lastUpdatedPalina is: $lastUpdatedPalina")
        /*if(needUpdateOnAttach){
            updateFragmentData(null);
            needUpdateOnAttach=false;
        }*/
        /*if(lastUpdatedPalina!=null){
            updateFragmentData(null);
            showArrivalsSources(lastUpdatedPalina);
        }*/
        mListener.readyGUIfor(FragmentKind.ARRIVALS)

        //fix bug when the list adapter is null
        mListAdapter?.let { resetListAdapter(it) }
        if (noArrivalsAdapter != null) {
            noArrivalsRecyclerView.adapter = noArrivalsAdapter
        }

        if (stopID.isNotEmpty()) {
            if (!justCreated) {
                fetchers = utils.getDefaultArrivalsFetchers(context)
                adjustFetchersToSource()

                if (reloadOnResume) requestArrivalsForTheFragment() //mListener.requestArrivalsForStopID(stopID)
            } else {
                //start first search
                requestArrivalsForTheFragment()
                changeUIFirstSearchActive(true)
                justCreated = false
            }
            //start the loader
            if (prefs!!.isDBUpdating(true)) {
                prefs!!.registerListener()
            } else {
                Log.d(DEBUG_TAG, "Restarting loader for stop")
                loaderManager.restartLoader(
                    loaderFavId,
                    arguments, this
                )
            }
            updateMessage()
        }

        if (ScreenBaseFragment.getOption(requireContext(), OPTION_SHOW_LEGEND, true)) {
            showHints()
        }
    }


    override fun onStart() {
        super.onStart()
        if (needUpdateOnAttach) {
            updateFragmentData(null)
            needUpdateOnAttach = false
        }
    }

    override fun onPause() {
        if (listener != null) prefs!!.unregisterListener()
        super.onPause()
        val loaderManager = loaderManager
        Log.d(DEBUG_TAG, "onPause, have running loaders: " + loaderManager.hasRunningLoaders())
        loaderManager.destroyLoader(loaderFavId)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        //get fetchers
        fetchers = utils.getDefaultArrivalsFetchers(context)
    }

    fun reloadsOnResume(): Boolean {
        return reloadOnResume
    }

    fun setReloadOnResume(reloadOnResume: Boolean) {
        this.reloadOnResume = reloadOnResume
    }

    // HINT "HOW TO USE"
    private fun showHints() {
        howDoesItWorkTextView.visibility = View.VISIBLE
        hideHintButton.visibility = View.VISIBLE
        //actionHelpMenuItem.setVisible(false);
    }

    private fun hideHints() {
        howDoesItWorkTextView.visibility = View.GONE
        hideHintButton.visibility = View.GONE
        //actionHelpMenuItem.setVisible(true);
    }

    fun onHideHint(v: View?) {
        hideHints()
        setOption(requireContext(), OPTION_SHOW_LEGEND, false)
    }

    /*val currentFetchersAsArray: Array<ArrivalsFetcher?>
        get() {
            val arr = arrayOfNulls<ArrivalsFetcher>(fetchers!!.size)
            fetchers!!.toArray<ArrivalsFetcher>(arr)
            return arr
        }

     */

    fun getCurrentFetchersAsArray(): Array<out ArrivalsFetcher?> {
        val r= fetchers.toTypedArray()
            //?: emptyArray<ArrivalsFetcher>()
        return r
    }

    private fun rotateFetchers() {
        Log.d(DEBUG_TAG, "Rotating fetchers, before: $fetchers")
        fetchers?.let { Collections.rotate(it, -1) }
        Log.d(DEBUG_TAG, "Rotating fetchers, afterwards: $fetchers")
    }


    /**
     * Update the UI with the new data
     * @param p the full Palina
     */
    fun updateFragmentData(p: Palina?) {
        if (p != null) lastUpdatedPalina = p

        if (!isAdded) {
            //defer update at next show
            if (p == null) Log.w(DEBUG_TAG, "Asked to update the data, but we're not attached and the data is null")
            else needUpdateOnAttach = true
        } else {
            val adapter = PalinaAdapter(context, lastUpdatedPalina, palinaClickListener, true)
            showArrivalsSources(lastUpdatedPalina!!)
            resetListAdapter(adapter)

            val routesWithNoPassages = lastUpdatedPalina!!.routesNamesWithNoPassages
            if (routesWithNoPassages.isEmpty()) {
                //hide the views if there are no empty routes
                noArrivalsRecyclerView!!.visibility = View.GONE
                noArrivalsTitleView!!.visibility = View.GONE
            } else {
                Collections.sort(routesWithNoPassages, LinesNameSorter())
                noArrivalsAdapter = RouteOnlyLineAdapter(routesWithNoPassages, null)
                noArrivalsRecyclerView!!.adapter = noArrivalsAdapter

                noArrivalsRecyclerView!!.visibility = View.VISIBLE
                noArrivalsTitleView!!.visibility = View.VISIBLE
            }


            //canaryEndView.setVisibility(View.VISIBLE);
            //check if canaryEndView is visible
            //boolean isCanaryVisibile = ViewUtils.Companion.isViewPartiallyVisibleInScroll(canaryEndView, theScrollView);
            //Log.d(DEBUG_TAG, "Canary view fully visibile: "+isCanaryVisibile);
        }
    }




    /**
     * Set the message of the arrival times source
     * @param p Palina with the arrival times
     */
    protected fun showArrivalsSources(p: Palina) {
        val source = p.passaggiSourceIfAny
        val source_txt = getDisplayArrivalsSource(source, requireContext())
        //
        val updatedFetchers = adjustFetchersToSource(source)
        if (!updatedFetchers) Log.w(DEBUG_TAG, "Tried to update the source fetcher but it didn't work")
        val base_message = getString(R.string.times_source_fmt, source_txt)
        timesSourceTextView.text = base_message
        timesSourceTextView.visibility = View.VISIBLE

        if (p.totalNumberOfPassages > 0) {
            timesSourceTextView.visibility = View.VISIBLE
        } else {
            timesSourceTextView.visibility = View.INVISIBLE
        }
        fetchersChangeRequestPending = false
    }

    protected fun adjustFetchersToSource(source: Source?): Boolean {
        if (source == null) return false
        var count = 0
        if (source != Source.UNDETERMINED) while (source != fetchers[0]!!.sourceForFetcher && count < 200) {
            //we need to update the fetcher that is requested
            rotateFetchers()
            count++
        }
        return count < 200
    }

    protected fun adjustFetchersToSource(): Boolean {
        if (lastUpdatedPalina == null) return false
        val source = lastUpdatedPalina!!.passaggiSourceIfAny
        return adjustFetchersToSource(source)
    }

    /**
     * Update the message in the fragment
     *
     * It may eventually change the "Add to Favorite" icon
     */
    private fun updateMessage() {
        var message = ""
        if (stopName != null && !stopName!!.isEmpty()) {
            message = ("$stopID - $stopName")
        } else if (stopID != null) {
            message = stopID
        } else {
            Log.e("ArrivalsFragm$tag", "NO ID FOR THIS FRAGMENT - something went horribly wrong")
        }
        if (message.isNotEmpty()) {
            setTextViewMessage(getString(R.string.passages, message))
        }

        // whatever is the case, update the star icon
        //updateStarIconFromLastBusStop();
    }

    override fun onCreateLoader(id: Int, p1: Bundle?): Loader<Cursor> {
        val args = arguments
        //if (args?.getString(KEY_STOP_ID) == null) throw
        val stopID = args?.getString(KEY_STOP_ID) ?: ""
        val builder = AppDataProvider.getUriBuilderToComplete()
        val cl: CursorLoader
        when (id) {
            loaderFavId -> {
                builder.appendPath("favorites").appendPath(stopID)
                cl = CursorLoader(requireContext(), builder.build(), UserDB.getFavoritesColumnNamesAsArray, null, null, null)
            }

            loaderStopId -> {
                builder.appendPath("stop").appendPath(stopID)
                cl = CursorLoader(
                    requireContext(), builder.build(), arrayOf(NextGenDB.Contract.StopsTable.COL_NAME),
                    null, null, null
                )
            }

            else -> {
                cl = CursorLoader(requireContext(), builder.build(), null, null,null,null)
                Log.d(DEBUG_TAG, "This is probably going to crash")
            }
        }
        cl.setUpdateThrottle(500)
        return cl
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor) {
        when (loader.id) {
            loaderFavId -> {
                val colUserName = data.getColumnIndex(UserDB.getFavoritesColumnNamesAsArray[1])
                if (data.count > 0) {
                    // IT'S IN FAVORITES
                    data.moveToFirst()
                    val probableName = data.getString(colUserName)
                    stopIsInFavorites = true
                    if (probableName != null && !probableName.isEmpty()) stopName = probableName //set the stop

                    //update the message in the textview
                    updateMessage()
                } else {
                    stopIsInFavorites = false
                }
                updateStarIcon()

                if (stopName == null) {
                    //stop is not inside the favorites and wasn't provided
                    Log.d("ArrivalsFragment$tag", "Stop wasn't in the favorites and has no name, looking in the DB")
                    loaderManager.restartLoader(
                        loaderStopId,
                        arguments, this
                    )
                }
            }

            loaderStopId -> if (data.count > 0) {
                data.moveToFirst()
                val index = data.getColumnIndex(
                    NextGenDB.Contract.StopsTable.COL_NAME
                )
                if (index == -1) {
                    Log.e(DEBUG_TAG, "Index is -1, column not present. App may explode now...")
                }
                stopName = data.getString(index)
                updateMessage()
            } else {
                Log.w("ArrivalsFragment$tag", "Stop is not inside the database... CLOISTER BELL")
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        //NOTHING TO DO
    }

    protected fun resetListAdapter(adapter: PalinaAdapter) {
        mListAdapter = adapter
        arrivalsRecyclerView.adapter = adapter
        arrivalsRecyclerView.visibility = View.VISIBLE
    }

    /**
     * Set the message textView
     * @param message the whole message to write in the textView
     */
    fun setTextViewMessage(message: String?) {
        messageTextView.text = message
        messageTextView.visibility = View.VISIBLE
    }

    fun toggleLastStopToFavorites() {
        val stop: Stop? = lastUpdatedPalina
        if (stop != null) {
            // toggle the status in background

            AsyncStopFavoriteAction(
                requireContext().applicationContext, AsyncStopFavoriteAction.Action.TOGGLE
            ) { v: Boolean -> updateStarIconFromLastBusStop(v) }.execute(stop)
        } else {
            // this case have no sense, but just immediately update the favorite icon
            updateStarIconFromLastBusStop(true)
        }
    }

    /**
     * Update the star "Add to favorite" icon
     */
    fun updateStarIconFromLastBusStop(toggleDone: Boolean) {
        stopIsInFavorites = if (stopIsInFavorites) !toggleDone
        else toggleDone

        updateStarIcon()

        // check if there is a last Stop
        /*
        if (stopID == null) {
            addToFavorites.setVisibility(View.INVISIBLE);
        } else {
            // filled or outline?
            if (isStopInFavorites(stopID)) {
                addToFavorites.setImageResource(R.drawable.ic_star_filled);
            } else {
                addToFavorites.setImageResource(R.drawable.ic_star_outline);
            }

            addToFavorites.setVisibility(View.VISIBLE);
        }
         */
    }

    /**
     * Update the star icon according to `stopIsInFavorites`
     */
    fun updateStarIcon() {
        // no favorites no party!

        // check if there is a last Stop

        if (stopID.isEmpty()) {
            addToFavorites.visibility = View.INVISIBLE
        } else {
            // filled or outline?
            if (stopIsInFavorites) {
                addToFavorites.setImageResource(R.drawable.ic_star_filled)
            } else {
                addToFavorites.setImageResource(R.drawable.ic_star_outline)
            }

            addToFavorites.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        //arrivalsRecyclerView = null
        if (arguments != null) {
            requireArguments().putString(SOURCES_TEXT, timesSourceTextView.text.toString())
            requireArguments().putString(MESSAGE_TEXT_VIEW, messageTextView.text.toString())
        }
        super.onDestroyView()
    }

    override fun getBaseViewForSnackBar(): View? {
        return null
    }

    fun isFragmentForTheSameStop(p: Palina): Boolean {
        return if (tag != null) tag == getFragmentTag(p)
        else false
    }


    /**
     * Request arrivals in the fragment
     */
    fun requestArrivalsForTheFragment(){

        // Run with previous fetchers
        //fragment.getCurrentFetchers().toArray()
        //AsyncArrivalsSearcher(, getCurrentFetchersAsArray(), context).execute(stopID)
        context?.let {
            mListener.toggleSpinner(true)
            val fetcherSources = fetchers.map { f-> f?.sourceForFetcher?.name ?: "" }
            //val workRequest = ArrivalsWorker.buildWorkRequest(stopID, fetcherSources.toTypedArray())
            //val workManager = WorkManager.getInstance(it)

            //workManager.enqueueUniqueWork(getArrivalsWorkID(stopID), ExistingWorkPolicy.REPLACE, workRequest)

            arrivalsViewModel.requestArrivalsForStop(stopID,fetcherSources.toTypedArray())

            //prepareGUIForArrivals();
            //new AsyncArrivalsSearcher(fragmentHelper,fetchers, getContext()).execute(ID);
            Log.d(DEBUG_TAG, "Started search for arrivals of stop $stopID")
        }
    }

    companion object {
        private const val OPTION_SHOW_LEGEND = "show_legend"
        private const val KEY_STOP_ID = "stopid"
        private const val KEY_STOP_NAME = "stopname"
        private const val DEBUG_TAG_ALL = "BUSTOArrivalsFragment"
        private const val loaderFavId = 2
        private const val loaderStopId = 1
        const val STOP_TITLE: String = "messageExtra"
        private const val SOURCES_TEXT = "sources_textview_message"

        @JvmStatic
        @JvmOverloads
        fun newInstance(stopID: String, stopName: String? = null): ArrivalsFragment {
            val fragment = ArrivalsFragment()
            val args = Bundle()
            args.putString(KEY_STOP_ID, stopID)
            //parameter for ResultListFragmentrequestArrivalsForStopID
            //args.putSerializable(LIST_TYPE,FragmentKind.ARRIVALS);
            if (stopName != null) {
                args.putString(KEY_STOP_NAME, stopName)
            }
            fragment.arguments = args
            return fragment
        }

        @JvmStatic
        fun getFragmentTag(p: Palina): String {
            return "palina_" + p.ID
        }

        @JvmStatic
        fun getArrivalsWorkID(stopID: String) = "arrivals_search_$stopID"

        @JvmStatic
        fun getDisplayArrivalsSource(source: Source, context: Context): String{
            return when (source) {
                Source.GTTJSON -> context.getString(R.string.gttjsonfetcher)
                Source.FiveTAPI -> context.getString(R.string.fivetapifetcher)
                Source.FiveTScraper -> context.getString(R.string.fivetscraper)
                Source.MatoAPI -> context.getString(R.string.source_mato)
                Source.UNDETERMINED ->                 //Don't show the view
                    context.getString(R.string.undetermined_source)

            }
        }
    }
}
