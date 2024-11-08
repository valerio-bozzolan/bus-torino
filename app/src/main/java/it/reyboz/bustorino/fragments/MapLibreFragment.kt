package it.reyboz.bustorino.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import it.reyboz.bustorino.R
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [MapLibreFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MapLibreFragment : Fragment(), OnMapReadyCallback {
    // TODO: Rename and change types of parameters
    //private var param1: String? = null
    //private var param2: String? = null
    // Declare a variable for MapView
    private lateinit var mapView: MapView
    protected var map: MapLibreMap? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

         */
        MapLibre.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val rootView =  inflater.inflate(R.layout.fragment_map_libre, container, false)



        // Init layout view

        // Init the MapView
        mapView = rootView.findViewById(R.id.libreMapView)
        mapView.getMapAsync(this) //{ //map ->
            //map.setStyle("https://demotiles.maplibre.org/style.json") }

        return rootView
    }

    override fun onMapReady(mapReady: MapLibreMap) {
        this.map = mapReady
        mapReady.cameraPosition = CameraPosition.Builder().target(LatLng(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON)).zoom(9.0).build()
        activity?.run {
            //TODO: copy from TransportR
            val mapStyle = makeStyleUrl("jawg-terrain")
            /*if (mapStyle != null && mapReady.style?.uri != mapStyle) {
                mapReady.setStyle(mapStyle, ::onMapStyleLoaded) //callback
            }
             */
            mapReady.setStyle(mapStyle)
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    companion object {
        private const val DEFAULT_CENTER_LAT = 45.0708
        private const val DEFAULT_CENTER_LON = 7.6858
        private const val POSITION_FOUND_ZOOM = 18.3
        private const val ACCESS_TOKEN="KxO8lF4U3kiO63m0c7lzqDCDrMUVg1OA2JVzRXxxmYSyjugr1xpe4W4Db5rFNvbQ"
        const val NO_POSITION_ZOOM = 17.1
        private const val MAPLIBRE_URL = "https://api.jawg.io/styles/"
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment MapLibreFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MapLibreFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
        private fun makeStyleUrl(style: String = "jawg-streets") =
            "${MAPLIBRE_URL+ style}.json?access-token=${ACCESS_TOKEN}"
    }
}