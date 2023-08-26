package it.reyboz.bustorino.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import it.reyboz.bustorino.R
import it.reyboz.bustorino.adapters.RouteAdapter
import it.reyboz.bustorino.backend.utils
import it.reyboz.bustorino.data.gtfs.GtfsRoute
import it.reyboz.bustorino.middleware.AutoFitGridLayoutManager
import it.reyboz.bustorino.util.LinesNameSorter
import it.reyboz.bustorino.util.ViewUtils
import it.reyboz.bustorino.viewmodels.LinesGridShowingViewModel


class LinesGridShowingFragment : ScreenBaseFragment() {



    private val viewModel: LinesGridShowingViewModel by viewModels()
    //private lateinit var gridLayoutManager: AutoFitGridLayoutManager

    private lateinit var urbanRecyclerView: RecyclerView
    private lateinit var extraurbanRecyclerView: RecyclerView
    private lateinit var touristRecyclerView: RecyclerView

    private lateinit var urbanLinesTitle: TextView
    private lateinit var extrurbanLinesTitle: TextView
    private lateinit var touristLinesTitle: TextView


    private var routesByAgency = HashMap<String, ArrayList<GtfsRoute>>()
        /*hashMapOf(
        AG_URBAN to ArrayList<GtfsRoute>(),
        AG_EXTRAURB to ArrayList(),
        AG_TOUR to ArrayList()
    )*/

    private lateinit var fragmentListener: CommonFragmentListener

    private val linesNameSorter = LinesNameSorter()
    private val linesComparator = Comparator<GtfsRoute> { a,b ->
        return@Comparator linesNameSorter.compare(a.shortName, b.shortName)
    }

    private val routeClickListener = RouteAdapter.onItemClick {
        fragmentListener.showLineOnMap(it.gtfsId)
    }
    private val arrows = HashMap<String, ImageView>()
    private val durations = HashMap<String, Long>()
    private var openRecyclerView = "AG_URBAN"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =  inflater.inflate(R.layout.fragment_lines_grid, container, false)

        urbanRecyclerView = rootView.findViewById(R.id.urbanLinesRecyclerView)
        extraurbanRecyclerView = rootView.findViewById(R.id.extraurbanLinesRecyclerView)
        touristRecyclerView = rootView.findViewById(R.id.touristLinesRecyclerView)

        urbanLinesTitle = rootView.findViewById(R.id.urbanLinesTitleView)
        extrurbanLinesTitle = rootView.findViewById(R.id.extraurbanLinesTitleView)
        touristLinesTitle = rootView.findViewById(R.id.touristLinesTitleView)

        arrows[AG_URBAN] = rootView.findViewById(R.id.arrowUrb)
        arrows[AG_TOUR] = rootView.findViewById(R.id.arrowTourist)
        arrows[AG_EXTRAURB] = rootView.findViewById(R.id.arrowExtraurban)
        //show urban expanded by default

        val recViews = listOf(urbanRecyclerView, extraurbanRecyclerView,  touristRecyclerView)
        for (recyView in recViews) {
            val gridLayoutManager = AutoFitGridLayoutManager(
                requireContext().applicationContext,
                (utils.convertDipToPixels(context, COLUMN_WIDTH_DP.toFloat())).toInt()
            )
            recyView.layoutManager = gridLayoutManager
        }

        viewModel.routesLiveData.observe(viewLifecycleOwner){
            //routesList = ArrayList(it)
            //routesList.sortWith(linesComparator)
            routesByAgency.clear()

            for(route in it){
                val agency = route.agencyID
                if(!routesByAgency.containsKey(agency)){
                    routesByAgency[agency] = ArrayList()
                }
                routesByAgency[agency]?.add(route)

            }


            //val adapter = RouteOnlyLineAdapter(routesByAgency.map { route-> route.shortName })
            //zip agencies and recyclerviews
            Companion.AGENCIES.zip(recViews) { ag, recView  ->
                routesByAgency[ag]?.let { routeList ->
                    routeList.sortWith(linesComparator)
                    //val adapter = RouteOnlyLineAdapter(it.map { rt -> rt.shortName })
                    val adapter = RouteAdapter(routeList,routeClickListener)
                    recView.adapter = adapter
                    durations[ag] = if(routeList.size < 20) ViewUtils.DEF_DURATION else 1000
                }
            }

        }

        //onClicks
        urbanLinesTitle.setOnClickListener {
            if(openRecyclerView!=""&& openRecyclerView!= AG_URBAN){
                openCloseRecyclerView(openRecyclerView)
                openCloseRecyclerView(AG_URBAN)
            }
        }
        extrurbanLinesTitle.setOnClickListener {
            if(openRecyclerView!=""&& openRecyclerView!= AG_EXTRAURB){
                openCloseRecyclerView(openRecyclerView)
                openCloseRecyclerView(AG_EXTRAURB)

            }
        }
        touristLinesTitle.setOnClickListener {
            if(openRecyclerView!="" && openRecyclerView!= AG_TOUR) {
                openCloseRecyclerView(openRecyclerView)
                openCloseRecyclerView(AG_TOUR)
            }
        }

        return rootView
    }

    private fun openCloseRecyclerView(agency: String){
        val recyclerView = when(agency){
            AG_TOUR -> touristRecyclerView
            AG_EXTRAURB -> extraurbanRecyclerView
            AG_URBAN -> urbanRecyclerView
            else -> throw IllegalArgumentException("$DEBUG_TAG: Agency Invalid")
        }
        val expandedLiveData = when(agency){
            AG_TOUR -> viewModel.isTouristExpanded
            AG_URBAN -> viewModel.isUrbanExpanded
            AG_EXTRAURB -> viewModel.isExtraUrbanExpanded
            else -> throw IllegalArgumentException("$DEBUG_TAG: Agency Invalid")
        }
        val duration = durations[agency]
        val arrow = arrows[agency]
        val durArrow = if(duration == null || duration==ViewUtils.DEF_DURATION) 500 else duration
        if(duration!=null&&arrow!=null)
            when (recyclerView.visibility){
                View.GONE -> {
                    Log.d(DEBUG_TAG, "Open recyclerview $agency")
                    //val a =ViewUtils.expand(recyclerView, duration, 0)
                    recyclerView.visibility = View.VISIBLE
                    expandedLiveData.value = true
                    Log.d(DEBUG_TAG, "Arrow for $agency has rotation: ${arrow.rotation}")

                    setOpen(arrow, true)
                    //arrow.startAnimation(rotateArrow(true,durArrow))
                    openRecyclerView = agency

                }
                View.VISIBLE -> {
                    Log.d(DEBUG_TAG, "Close recyclerview $agency")
                    //ViewUtils.collapse(recyclerView, duration)
                    recyclerView.visibility = View.GONE
                    expandedLiveData.value = false
                    //arrow.rotation = 90f
                    Log.d(DEBUG_TAG, "Arrow for $agency has rotation ${arrow.rotation} pre-rotate")
                    setOpen(arrow, false)
                    //arrow.startAnimation(rotateArrow(false,durArrow))
                    openRecyclerView = ""
                }
                View.INVISIBLE -> {
                    TODO()
                }
            }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is CommonFragmentListener){
            fragmentListener = context
        } else throw RuntimeException("$context must implement CommonFragmentListener")

    }

    override fun getBaseViewForSnackBar(): View? {
        return null
    }

    override fun onResume() {
        super.onResume()
        viewModel.isUrbanExpanded.value?.let {
            if(it) {
                urbanRecyclerView.visibility = View.VISIBLE
                arrows[AG_URBAN]?.rotation= 90f
                openRecyclerView = AG_URBAN
                Log.d(DEBUG_TAG, "RecyclerView gtt:U is expanded")
            }
            else {
                urbanRecyclerView.visibility = View.GONE
                arrows[AG_URBAN]?.rotation= 0f
            }
        }
        viewModel.isTouristExpanded.value?.let {
            val recview = touristRecyclerView
            if(it) {
                recview.visibility = View.VISIBLE
                arrows[AG_TOUR]?.rotation=90f
                openRecyclerView = AG_TOUR
            } else {
                recview.visibility = View.GONE
                arrows[AG_TOUR]?.rotation= 0f
            }
        }
        viewModel.isExtraUrbanExpanded.value?.let {
            val recview = extraurbanRecyclerView
            if(it) {
                openRecyclerView = AG_EXTRAURB
                recview.visibility = View.VISIBLE
                arrows[AG_EXTRAURB]?.rotation=90f
            } else {
                recview.visibility = View.GONE
                arrows[AG_EXTRAURB]?.rotation=0f
            }
        }
        fragmentListener.readyGUIfor(FragmentKind.LINES)

    }


    companion object {
        private const val COLUMN_WIDTH_DP=200
        private const val AG_URBAN = "gtt:U"
        private const val AG_EXTRAURB ="gtt:E"
        private const val AG_TOUR ="gtt:T"
        private const val DEBUG_TAG ="BusTO-LinesGridFragment"

        const val FRAGMENT_TAG = "LinesGridShowingFragment"

        private val AGENCIES = listOf(AG_URBAN, AG_EXTRAURB, AG_TOUR)
        fun newInstance() = LinesGridShowingFragment()

        @JvmStatic
        fun setOpen(imageView: ImageView, value: Boolean){
            if(value)
                imageView.rotation = 90f
            else
                imageView.rotation  = 0f
        }
        @JvmStatic
        fun rotateArrow(toOpen: Boolean, duration: Long):  RotateAnimation{
            val start = if (toOpen) 0f else 90f
            val stop = if(toOpen) 90f else 0f
            Log.d(DEBUG_TAG, "Rotate arrow from $start to $stop")
            val rotate = RotateAnimation(start, stop, Animation.RELATIVE_TO_SELF,
                0.5f, Animation.RELATIVE_TO_SELF, 0.5f)
            rotate.duration = duration
            rotate.interpolator = LinearInterpolator()
            //rotate.fillAfter = true
            rotate.fillBefore = false
            return rotate
        }
    }

}