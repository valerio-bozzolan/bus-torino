package it.reyboz.bustorino.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.Palina

class RouteOnlyLineAdapter (val routeNames: List<String>) :
    RecyclerView.Adapter<RouteOnlyLineAdapter.ViewHolder>() {

    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView

        init {
            // Define click listener for the ViewHolder's View
            textView = view.findViewById(R.id.routeBallID)
        }
    }
    constructor(palina: Palina, showOnlyEmpty: Boolean): this(palina.routesNamesWithNoPassages)

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.round_line_header, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.textView.text = routeNames[position]
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = routeNames.size

}
