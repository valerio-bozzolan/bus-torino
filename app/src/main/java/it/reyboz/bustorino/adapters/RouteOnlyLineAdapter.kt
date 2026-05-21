package it.reyboz.bustorino.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.FiveTNormalizer

class RouteOnlyLineAdapter (val routeNames: List<String>,
                            onItemClick: OnClick?) :
    RecyclerView.Adapter<RouteOnlyLineAdapter.ViewHolder>() {


    private val clickreference = onItemClick


    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder)
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView  = view.findViewById(R.id.routeBallID)
        val cardView: CardView = view.findViewById(R.id.headerCardView)

    }

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
        // SHOW "STAR" as "ST"
        viewHolder.textView.text = FiveTNormalizer.filterFullStarName(routeNames[position])
        viewHolder.cardView.setOnClickListener{
            clickreference?.onItemClick(position, routeNames[position])
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = routeNames.size

    fun interface OnClick{
        fun onItemClick(index: Int, name: String)
    }
}
