package it.reyboz.bustorino.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import it.reyboz.bustorino.R


/**
 * A simple [Fragment] subclass.
 * Use the [CrashFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CrashFragment : Fragment() {
    private lateinit var crashButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root= inflater.inflate(R.layout.fragment_crash, container, false)

        crashButton = root.findViewById(R.id.buttonCrash)
        crashButton.setOnClickListener {

            Toast.makeText(requireContext(), "App goes bye bye", Toast.LENGTH_SHORT).show()
            throw IllegalArgumentException("Nobody expects the Spanish Inquisition!")
        }
        return root
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *

         * @return A new instance of fragment CrashFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance() =
            CrashFragment().apply {

            }
    }
}