package it.reyboz.bustorino.fragments

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.viewModels
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.mato.MQTTMatoClient
import it.reyboz.bustorino.viewmodels.LivePositionsViewModel


/**
 * A simple [Fragment] subclass.
 * Use the [TestRealtimeGtfsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class TestRealtimeGtfsFragment : Fragment() {

    private lateinit var buttonLaunch: Button
    private lateinit var messageTextView: TextView

    private var subscribed = false
    private lateinit var mqttMatoClient: MQTTMatoClient

    private lateinit var lineEditText: EditText

    private val mqttViewModel: LivePositionsViewModel by viewModels()

    /*private val requestListener = object: GtfsRtPositionsRequest.Companion.RequestListener{
        override fun onResponse(response: ArrayList<GtfsPositionUpdate>?) {
            if (response == null) return

            if (response.size == 0) {
                messageTextView.text = "No entities in the message"
                return
            }
            val position = response[0]
            //position.
            messageTextView.text = "Entity message 0: ${position}"
        }


    }
     */

    private val listener = MQTTMatoClient.Companion.MQTTMatoListener{

        messageTextView.text = "Update: ${it}"
        Log.d("BUSTO-TestMQTT", "Received update $it")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        val rootView= inflater.inflate(R.layout.fragment_test_realtime_gtfs, container, false)

        buttonLaunch = rootView.findViewById(R.id.btn_download_data)
        buttonLaunch.text="Start"
        messageTextView = rootView.findViewById(R.id.gtfsMessageTextView)
        lineEditText = rootView.findViewById(R.id.lineEditText)

        mqttViewModel.updatesWithTripAndPatterns.observe(viewLifecycleOwner){
            val upds = it.entries.map { it.value.first }
            messageTextView.text = "$upds"
        }

        buttonLaunch.setOnClickListener {

            context?.let {cont->
                /*val req = GtfsRtPositionsRequest(
                    Response.ErrorListener { Toast.makeText(cont, "Error: ${it.message}",Toast.LENGTH_SHORT) },
                    requestListener
                )
                NetworkVolleyManager.getInstance(cont).addToRequestQueue(req)

                 */
                subscribed = if(subscribed){
                    //mqttMatoClient.desubscribe(listener)
                    mqttViewModel.stopMatoUpdates()
                    buttonLaunch.text="Start"
                    false
                } else{
                    //mqttMatoClient.startAndSubscribe(lineEditText.text.trim().toString(), listener)
                    mqttViewModel.requestMatoPosUpdates(lineEditText.text.trim().toString())
                    buttonLaunch.text="Stop"
                    true
                }

            }



        }
        return rootView
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment TestRealtimeGtfsFragment.
         */
        @JvmStatic
        fun newInstance() =
            TestRealtimeGtfsFragment().apply {
            }
    }
}