/*
	BusTO  - Fragments components
    Copyright (C) 2025 Fabio Mazza

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

import it.reyboz.bustorino.R

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import it.reyboz.bustorino.backend.LivePositionsServiceStatus
import it.reyboz.bustorino.viewmodels.LivePositionsViewModel

class LivePositionsDialogFragment : DialogFragment() {
    private val viewModel: LivePositionsViewModel by activityViewModels()
    private lateinit var providerNameTextView: TextView
    private lateinit var statusMessageTextView: TextView


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        //return super.onCreateDialog(savedInstanceState)
        val builder = AlertDialog.Builder(requireContext())
        val view = layoutInflater.inflate(R.layout.fragment_dialog_buspositions, null)

        providerNameTextView = view.findViewById<TextView>(R.id.providerNameTextView)
        statusMessageTextView = view.findViewById<TextView>(R.id.statusMessageTextView)
        val btnSwitch = view.findViewById<Button>(R.id.btnSwitch)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClose)

        // OBSERVE VIEWMODEL
        /*sharedViewModel.variableTitle.observe(this) {
            tvTitleVariable.text = it
        }

        sharedViewModel.statusMessage.observe(this) {
            tvStatusMessage.text = it
        }



         */

        btnSwitch.setOnClickListener {
            viewModel.switchPositionsSource()
        }

        btnClose.setOnClickListener { dismiss() }

        builder.setView(view)
        val res =  builder.create()
        res.window?.setBackgroundDrawableResource(R.color.card_background)
        return res
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        viewModel.serviceStatus.observe(this) { serviceStatus ->
            val message = when (serviceStatus) {
                LivePositionsServiceStatus.OK -> getString(R.string.live_positions_msg_ok)
                LivePositionsServiceStatus.NO_POSITIONS -> getString(R.string.live_positions_msg_no_positions)
                LivePositionsServiceStatus.ERROR_CONNECTION -> getString(R.string.live_positions_msg_connection_error)
                LivePositionsServiceStatus.ERROR_NETWORK_RESPONSE -> getString(R.string.live_positions_msg_network_error)
                LivePositionsServiceStatus.ERROR_PARSING_RESPONSE -> getString(R.string.live_positions_msg_parsing_error)
                LivePositionsServiceStatus.CONNECTING -> getString(R.string.live_positions_msg_connecting)

            }
            statusMessageTextView.text = message
        }
        viewModel.useMQTTPositionsLiveData.observe(this) { useMQTT ->
            val message = if (useMQTT) {
                getString(R.string.positions_source_mato_short)
            } else getString(R.string.positions_source_gtfsrt_short)
            providerNameTextView.text  = message
        }

        return super.onCreateView(inflater, container, savedInstanceState)
    }
}