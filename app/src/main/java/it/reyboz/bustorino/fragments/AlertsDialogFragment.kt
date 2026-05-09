/*
	BusTO  - Fragments components
    Copyright (C) 2026 Fabio Mazza

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

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import it.reyboz.bustorino.R
import it.reyboz.bustorino.adapters.AlertLineFullAdapter
import it.reyboz.bustorino.backend.gtfs.GtfsUtils
import it.reyboz.bustorino.data.GtfsAlertDBDownloadWorker
import it.reyboz.bustorino.data.gtfs.AlertWithDetails
import it.reyboz.bustorino.data.gtfs.GtfsAlertsTranslation
import it.reyboz.bustorino.viewmodels.ServiceAlertsViewModel
import java.util.Locale
import kotlin.getValue
import kotlin.collections.HashMap


class AlertsDialogFragment(private val gtfsLineShow: String) : DialogFragment() {

    private lateinit var titleTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var statusCardView: CardView
    private lateinit var recyclerView: RecyclerView
    private val alertsViewModel: ServiceAlertsViewModel by activityViewModels()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(DEBUG_TAG, "created DialogFragment for line ${gtfsLineShow}")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_dialog_alerts_line, container, false)

        titleTextView = root.findViewById<TextView>(R.id.titleTextView)
        titleTextView.setText(getString(R.string.alert_line_fill,GtfsUtils.lineNameDisplayFromGtfsID(gtfsLineShow)))
        recyclerView =  root.findViewById(R.id.alertsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        messageTextView = root.findViewById(R.id.alertMessageTextView)
        statusCardView = root.findViewById(R.id.statusCard)
        alertsViewModel.alertsByRouteLiveData.observe(viewLifecycleOwner){ alerts ->
            showAlerts(alerts)
        }

        val btnClose = root.findViewById<ImageButton>(R.id.btnClose)
        btnClose.setOnClickListener {
            dismiss()
        }

        val btnRefresh = root.findViewById<ImageButton>(R.id.btnRefresh)
        btnRefresh.setOnClickListener {
            val name = "manualUpdateAlerts"
            val req = GtfsAlertDBDownloadWorker.makeOneTimeRequest("manualUpdate$gtfsLineShow")
            WorkManager.getInstance(requireContext()).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP,req)
            Toast.makeText(context, R.string.checking_alerts_update, Toast.LENGTH_SHORT).show()
        }
        return root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun showAlerts(alerts: List<AlertWithDetails>) {
        val currentLang = Locale.getDefault().language
        val ms =  "language : $currentLang"
        val langs_msg = HashMap<String, Int>()
        for (a in alerts) {
            for (tr in a.translations){
                if(tr.field == GtfsAlertsTranslation.FIELD_HEADER){
                    tr.language?.let{
                        if(langs_msg.containsKey(it)){
                            langs_msg[it] = langs_msg[it]!! + 1
                        } else{
                            langs_msg[it] = 1
                        }
                    }
                    //found the title, stop
                    break
                }
            }
        }
        Log.d(DEBUG_TAG, "Lang $currentLang, alerts: $langs_msg, of lang: ${langs_msg[currentLang]}")
        val msgInLang = langs_msg[currentLang]?: 0
        val langShow = if (msgInLang > 0){
            currentLang
        } else if("en" in langs_msg.keys){
            "en"
        } else{
            "it"
        } // if there are no messages with "it", then it's over
        val count = langs_msg[langShow] ?: 0
        if (count == 0){
            messageTextView.text = "ERROR: NO ALERTS TO SHOW"
            statusCardView.visibility = View.VISIBLE
        } else if(msgInLang == 0){
            val msgShow = if(langShow == "en") getString(R.string.english) else getString(R.string.italian)
            messageTextView.text = getString(R.string.no_alerts_in_your_language_fill, msgShow)
            statusCardView.visibility = View.VISIBLE
        }
        // put them in the adapter
        if(count>0){
            recyclerView.adapter = AlertLineFullAdapter(alerts, langShow)
        }
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param gtfsLine Line To show.
         * @return A new instance of fragment LineAlertsDialogFragment.
         */
        @JvmStatic
        fun newInstance(gtfsLine: String) =
            AlertsDialogFragment(gtfsLine)

        private const val GTFS_LINE_ARG = "gtfsLine"

        private const val DEBUG_TAG = "BusTO-AlertsDialog"
    }
}