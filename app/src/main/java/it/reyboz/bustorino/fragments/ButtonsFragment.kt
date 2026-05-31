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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import it.reyboz.bustorino.ActivityAbout
import it.reyboz.bustorino.ActivitySettings
import it.reyboz.bustorino.R
import it.reyboz.bustorino.adapters.RecyclerViewMargin

/**
 * A simple [Fragment] subclass.
 * Use the [ButtonsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ButtonsFragment : BarcodeFragment() {

    //private lateinit var gridLayout: GridLayout

    private lateinit var recyclerView: RecyclerView

    private var listener: CommonFragmentListener? = null
    private lateinit var items: List<CardMenuItem>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {

        }
    }
    private val marginHoriz = 30
    private val margin = 11

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root = inflater.inflate(R.layout.fragment_buttons, container, false)

        // this is the actual list of the buttons
        items = listOf(
            CardMenuItem(CardAction.NEARBY, getString(R.string.near_me_title), R.drawable.compass_3_fill),
            CardMenuItem(CardAction.MAP, getString(R.string.map), R.drawable.map),
            CardMenuItem(CardAction.FAVORITES_STOPS, getString(R.string.action_favorites), R.drawable.ic_star_filled_white),
            CardMenuItem(CardAction.LINES, getString(R.string.lines), R.drawable.ic_moving_emph),
            CardMenuItem(CardAction.SETTINGS, getString(R.string.action_settings), R.drawable.ic_baseline_settings_24),
            CardMenuItem(CardAction.QR_SCAN, getString(R.string.scan_qr_code_stop),
                R.drawable.qr_code_scan),
            CardMenuItem(CardAction.INFO,
                getString(R.string.action_about), R.drawable.ic_baseline_info_24
                ),

            )

        recyclerView = root.findViewById(R.id.buttonsRecyclerView)

        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.layoutManager = gridLayoutManager

        recyclerView.adapter = ActionsCardAdapter(items, this::onCardClicked)
        val margins = RecyclerViewMargin.makeMarginsDip(requireContext(), margin, 2)
        recyclerView.addItemDecoration(margins)


        return root
    }


    private fun onCardClicked(item: CardMenuItem) {
        Log.d(DEBUG_TAG, "onCardClicked - item: ${item}, listener: ${listener}")
        // reagisci al tap
        val list = listener
        if(list  == null){
            Log.w(DEBUG_TAG, "onCardClicked - listener is null")
        } else
            when(item.action) {
                CardAction.NEARBY -> {
                        list.openNearbyStopsFragment()
                }
                CardAction.MAP -> { list.showMapCenteredOnStop(null)}
                CardAction.LINES -> { list.openLinesFragment();}
                CardAction.SETTINGS -> { startActivity(Intent(requireContext(), ActivitySettings::class.java)) }
                CardAction.FAVORITES_STOPS -> { list.openFavoritesFragment() }
                CardAction.QR_SCAN -> {
                    launchBarcodeScan()
                }
                CardAction.INFO ->{
                   startActivity(Intent(requireContext(), ActivityAbout::class.java))
                }
        }
    }

    override fun onQrScanSuccess(busIDToSearch: String) {
        listener?.let {
            it.requestArrivalsForStopID(busIDToSearch)
        } ?: Log.d(DEBUG_TAG, "onQrScanSuccess - listener is null")
    }

    override fun getBaseViewForSnackBar(): View? {
        return null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is CommonFragmentListener) {
            listener = context
            Log.d(DEBUG_TAG, "onAttach")
        } else{
            throw RuntimeException("$context must implement CommonFragmentListener")
        }
    }

    override fun onDetach() {
        listener = null
        Log.d(DEBUG_TAG, "onDetach")
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
        listener?.readyGUIfor(FragmentKind.HOME_BUTTONS)
        if(listener is FragmentListenerMain){
            val ll = listener as FragmentListenerMain
            ll.enableRefreshLayout(false)
        }
    }

    companion object {
        /**
         * @return A new instance of fragment ButtonsFragment.
         */
        @JvmStatic
        fun newInstance() =
            ButtonsFragment().apply {
                arguments = Bundle().apply {
                }
            }
        const val DEBUG_TAG = "BusTO-ButtonsFragment"


        const val FRAGMENT_TAG = "HomeButtonsFragment"
    }
    data class CardMenuItem(
        val action: CardAction,
        val label: String,
        val iconRes: Int
    )
    enum class CardAction {
        NEARBY, MAP, FAVORITES_STOPS, LINES, SETTINGS, QR_SCAN, INFO
    }
}

class ActionsCardAdapter(val actions: List<ButtonsFragment.CardMenuItem>,
                         val listener: (ButtonsFragment.CardMenuItem) -> Unit) :
    RecyclerView.Adapter<ActionsCardAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card_button_home, parent, false)
    /*
        // Altezza match_parent per uniformare le card della stessa riga
        view.layoutParams = RecyclerView.LayoutParams(
            RecyclerView.LayoutParams.MATCH_PARENT,
            RecyclerView.LayoutParams.MATCH_PARENT
        )
     */

        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        val item = actions[position]

        holder.imgView.setImageResource(item.iconRes)
        holder.textView.text = item.label

        holder.cardView.setOnClickListener { listener(item) }
    }

    override fun getItemCount() = actions.size


    inner class ViewHolder(val view: View): RecyclerView.ViewHolder(view) {
        val textView = view.findViewById<TextView>(R.id.cardLabel)
        val imgView: ImageView = view.findViewById(R.id.cardIcon)
        val cardView: MaterialCardView = view.findViewById(R.id.buttonCardView)
    }
}