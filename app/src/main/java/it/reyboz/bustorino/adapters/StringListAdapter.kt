package it.reyboz.bustorino.adapters


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StringListAdapter(private val stringList: List<String>) :
    RecyclerView.Adapter<StringListAdapter.StringViewHolder>() {

    // ViewHolder class to hold the TextView for each item
    class StringViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StringViewHolder {
        // Inflate the layout for each item in the RecyclerView (simple_list_item_1)
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return StringViewHolder(view)
    }

    override fun onBindViewHolder(holder: StringViewHolder, position: Int) {
        // Bind the string from stringList at this position to the TextView
        holder.textView.text = stringList[position]
    }

    override fun getItemCount(): Int = stringList.size

}