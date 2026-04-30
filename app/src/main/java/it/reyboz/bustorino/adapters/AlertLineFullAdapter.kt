package it.reyboz.bustorino.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.reyboz.bustorino.R
import it.reyboz.bustorino.data.gtfs.AlertWithDetails
import it.reyboz.bustorino.data.gtfs.GtfsAlertsTranslation

class AlertLineFullAdapter(val alerts: List<AlertWithDetails>,
    val locale: String
    ) :RecyclerView.Adapter<AlertLineFullAdapter.ViewHolder>() {


    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val v = LayoutInflater.from(parent.context).inflate(LAYOUT_ID, parent, false)

        return ViewHolder(v)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val al = alerts[position]

        var til = al.translations.filter { it.field == GtfsAlertsTranslation.FIELD_HEADER && it.language == locale }
        var text = if(til.isEmpty()) "404" else til[0].text
        holder.titleTextView.text = text

        til = al.translations.filter { it.field == GtfsAlertsTranslation.FIELD_DESCRIPTION && it.language == locale }
        text = if(til.isEmpty()) "404" else til[0].text
        holder.bodyTextView.text = text

    }

    override fun getItemCount(): Int {
        return alerts.size
    }


    class ViewHolder(view: View) : RecyclerView.ViewHolder(view){
            val titleTextView: TextView = view.findViewById(R.id.messageTitleTextView)
            val bodyTextView: TextView = view.findViewById(R.id.messageBodyTextView)

    }
    companion object{
        private val LAYOUT_ID = R.layout.entry_alert_line_adapter
    }
}