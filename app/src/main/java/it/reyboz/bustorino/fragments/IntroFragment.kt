package it.reyboz.bustorino.fragments

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.text.LineBreaker
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.utils
import java.lang.IllegalStateException


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val SCREEN_INDEX = "screenindex"

/**
 * A simple [Fragment] subclass.
 * Use the [IntroFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class IntroFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var screenIndex = 1
    private lateinit var imageHolder: ImageView
    private lateinit var textView: TextView


    private lateinit var listener: IntroListener
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            screenIndex = it.getInt(SCREEN_INDEX)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context !is IntroListener){
            throw IllegalStateException("Context must implement IntroListener")
        }
        listener = context
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val root=  inflater.inflate(R.layout.fragment_intro, container, false)
        imageHolder = root.findViewById(R.id.image_tutorial)
        textView = root.findViewById(R.id.tutorialTextView)


        when(screenIndex){
            0 -> {
                setImageBitmap(imageHolder, R.drawable.tuto_busto, 300f)
                textView.text = utils.convertHtml(getString(R.string.tutorial_first))
            }

            1->{
                setImageBitmap(imageHolder, R.drawable.tuto_search)
                setTextHtmlDescription(R.string.tutorial_search)
            }
            2 ->{
                setImageBitmap(imageHolder, R.drawable.tuto_arrivals)
                textView.text = utils.convertHtml(getString(R.string.tutorial_arrivals))
            }
            3 ->{
                setImageBitmap(imageHolder, R.drawable.tuto_stops)
                textView.text = utils.convertHtml(getString(R.string.tutorial_stops))
            }
            4 ->{
                setImageBitmap(imageHolder, R.drawable.tuto_map)
                textView.text = utils.convertHtml(getString(R.string.tutorial_map))
            }
            5 ->{
                setImageBitmap(imageHolder, R.drawable.tuto_line_det)
                textView.text = utils.convertHtml(getString(R.string.tutorial_line))
            }
            6-> {
                setImageBitmap(imageHolder,R.drawable.tuto_menu)
                setTextHtmlDescription(R.string.tutorial_menu)
                val closeButton = root.findViewById<Button>(R.id.closeAllButton)
                closeButton.visibility = View.VISIBLE
                closeButton.setOnClickListener {
                    listener.closeIntroduction()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            textView.breakStrategy = LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
        }

        return root
    }

    private fun setTextHtmlDescription(resId: Int){
        textView.text = utils.convertHtml(getString(resId))
    }


    private fun setImageBitmap(imageView: ImageView, resId: Int, maxDpToScale:Float = DP_LIM_IMAGE){
        val bitmap = BitmapFactory.decodeResource(resources,resId)
        /*val limPix = utils.convertDipToPixels(resources, maxDpToScale)
        if (bitmap.width > limPix) {
            val rescFac = limPix/bitmap.width
            val rescaledBitmap = Bitmap.createScaledBitmap(bitmap,(limPix).toInt(), (bitmap.height*rescFac).toInt(),false)
            imageView.setImageBitmap(rescaledBitmap)
        }
        else
        */
        imageView.setImageBitmap(bitmap)

    }

    companion object {

        const val DP_LIM_IMAGE = 1000f
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param index the screen index
         * @return A new instance of fragment IntroFragment.
         */
        @JvmStatic
        fun newInstance(index: Int) =
            IntroFragment().apply {
                arguments = Bundle().apply {
                    putInt(SCREEN_INDEX, index)
                }
            }
        @JvmStatic
        fun makeArguments(index: Int) = Bundle().apply {
            putInt(SCREEN_INDEX, index) }

    }

    interface IntroListener{
        fun closeIntroduction()
    }
}