package it.reyboz.bustorino.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.transit.realtime.GtfsRealtime
import it.reyboz.bustorino.R
import it.reyboz.bustorino.viewmodels.ServiceAlertsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone


/**
 * A simple [Fragment] subclass.
 * Use the [AlertsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AlertsFragment : ScreenBaseFragment() {

    private val alertsViewModel: ServiceAlertsViewModel by activityViewModels()

    private lateinit var textView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            //param1 = it.getString(ARG_PARAM1)
            //param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_alerts, container, false)
        textView = root.findViewById(R.id.simpleTextView)

        alertsViewModel.allAlertsLiveData.observe(viewLifecycleOwner, { alerts ->
            val sb = StringBuilder()
            val unixTimestamp = (System.currentTimeMillis() / 1000)
            for (x in alerts) {
               sb.append(x.longPrint())
                sb.append("----- Alert active: ").append(x.isActive(unixTimestamp)).append("\n\n")
            }

            textView.text = sb.toString()
        })


        alertsViewModel.setStopFilter("472")
        /*alertsViewModel.alertsForStop.observe(viewLifecycleOwner){
            Log.d(DEBUG_TAG, "Got ${it.size} alerts")
            it?.let {
                showAlerts(it)
            }
        }

         */
        /*
        alertsViewModel.alertsByRouteLiveData.observe(viewLifecycleOwner) { map ->
            Log.d(DEBUG_TAG, "Alerts for routes: ${map.keys}")
            val keys = map.keys
            if(keys.isNotEmpty()){
                val sb = StringBuilder()
                for (key in keys.sorted()) {
                    sb.append(" ======== Route: $key =======").append("\n")
                    sb.append(makeAlertListText(map[key]!!)).append("\n")
                    Log.d(DEBUG_TAG, "Route: $key len: ${map[key]!!.size}")
                }

                textView.text = sb.toString()
            }

        }

         */
        return root
    }

    override fun getBaseViewForSnackBar(): View? {
        TODO("Not yet implemented")
    }

    private fun makeAlertListText(alerts: List<GtfsRealtime.Alert>) : String{
        val sb = StringBuilder()
        for (al in alerts) {
            sb.append("=========== Alert ===========\n")
            sb.append("Title:\n")
            for (t in al.headerText.translationList) {
                sb.append(t.language).append(": ").append(t.text).append("\n")
            }
            sb.append("Description:\n")
            val transl = al.descriptionText.translationList
            for (t in transl) {
                sb.append(t.language).append(": ").append(t.text).append("\n")
            }
            val infE = al.informedEntityList
            sb.append("--- Active periods count: ${al.activePeriodCount}\n")
            val timeActive = al.getActivePeriod(0)
            sb.append("Start: ").append(getTimeStampToString(timeActive.start)).append(" ")
            sb.append("End: ").append(getTimeStampToString(timeActive.end)).append("\n")
            sb.append("--- Cause:\n")
            sb.append(al.cause.name).append("\n")
            sb.append("--- Informed entities:\n")
            for (e in infE) {
                if(e.hasTrip()){
                    sb.append("Trip: ${e.trip.tripId} for route ${e.trip.routeId}, ")
                } else{
                    sb.append("No Trip, ")
                }
                sb.append("Stop: ${e.stopId}, Route: ${e.routeId}\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment AlertsFragment.
         */
        @JvmStatic
        fun newInstance() =
            AlertsFragment().apply {
                arguments = Bundle().apply {
                    //putString(ARG_PARAM1, param1)
                    //putString(ARG_PARAM2, param2)
                }
            }

        fun getTimeStampToString(timestamp: Long): String? {
            val date = Date(timestamp*1000)

            val sdf= SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.timeZone = TimeZone.getTimeZone("Europe/Rome")

            return sdf.format(date)
        }

        private const val DEBUG_TAG = "BusTO-AlertsFragment"
    }
}