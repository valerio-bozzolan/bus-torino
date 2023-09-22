package it.reyboz.bustorino.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import it.reyboz.bustorino.R
import it.reyboz.bustorino.data.gtfs.GtfsRoute
import java.lang.ref.WeakReference

class RouteAdapter(val routes: List<GtfsRoute>,
                   click: ItemClicker,
                   private val layoutId: Int = R.layout.entry_line_num_descr) :
    RecyclerView.Adapter<RouteAdapter.ViewHolder>()
{
        val clickreference: WeakReference<ItemClicker>
        init {
            clickreference = WeakReference(click)
        }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val descrptionTextView: TextView
        val nameTextView : TextView
        val innerCardView : CardView?
        init {
            // Define click listener for the ViewHolder's View
            nameTextView = view.findViewById(R.id.lineShortNameTextView)
            descrptionTextView = view.findViewById(R.id.lineDirectionTextView)
            innerCardView = view.findViewById(R.id.innerCardView)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutId, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount() = routes.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        val route = routes[position]
        holder.nameTextView.text = route.shortName
        holder.descrptionTextView.text = route.longName

        holder.itemView.setOnClickListener{
            clickreference.get()?.onRouteItemClicked(route)
        }
    }

    fun interface ItemClicker{
        fun onRouteItemClicked(gtfsRoute: GtfsRoute)
    }
}