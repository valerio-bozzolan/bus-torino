package it.reyboz.bustorino.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.android.volley.Response
import com.google.transit.realtime.GtfsRealtime
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.NetworkVolleyManager
import it.reyboz.bustorino.backend.gtfs.GtfsRealtimeRequest

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [TestRealtimeGtfsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class TestRealtimeGtfsFragment : Fragment() {

    private lateinit var buttonLaunch: Button
    private lateinit var messageTextView: TextView

    private val requestListener = object: GtfsRealtimeRequest.Companion.RequestListener{
        override fun onResponse(response: GtfsRealtime.FeedMessage?) {
            if (response == null) return

            if (response.entityCount == 0) {
                messageTextView.text = "No entities in the message"
                return
            }
            messageTextView.text = "Entity message 0: ${response.getEntity(0)}"
        }

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
        messageTextView = rootView.findViewById(R.id.gtfsMessageTextView)

        buttonLaunch.setOnClickListener {

            context?.let {cont->
                val req = GtfsRealtimeRequest(GtfsRealtimeRequest.URL_POSITION,
                    Response.ErrorListener { Toast.makeText(cont, "Error: ${it.message}",Toast.LENGTH_SHORT) },
                    requestListener
                )
                NetworkVolleyManager.getInstance(cont).addToRequestQueue(req)
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