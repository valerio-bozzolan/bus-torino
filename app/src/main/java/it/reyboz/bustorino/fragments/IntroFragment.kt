package it.reyboz.bustorino.fragments

import android.content.Context
import android.graphics.*
import android.graphics.text.LineBreaker
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.utils
import it.reyboz.bustorino.util.Permissions
import it.reyboz.bustorino.util.ViewUtils
import java.lang.IllegalStateException


// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val SCREEN_INDEX = "screenindex"

class IntroFragment : Fragment() {
    private var screenIndex = 1
    private lateinit var imageHolder: ImageView
    private lateinit var textView: TextView

    private lateinit var listener: IntroListener
    private lateinit var interactButton: Button

    private val locationRequestResLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ res ->
        //onActivityResult(res: map<String,Boolean>)
        if(res.get(Permissions.LOCATION_PERMISSIONS[0])==true || res.get(Permissions.LOCATION_PERMISSIONS[1])==true)
            setInteractButtonState(ButtonState.LOCATION,false)
    }
    private val notificationsReqLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) setInteractButtonState(ButtonState.NOTIFICATIONS, false)
    }
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
        interactButton = root.findViewById(R.id.permissionButton)

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
                if (Build.VERSION.SDK_INT >= 23) {
                    //only show if running on Android M or above
                    val permGranted = (Permissions.anyLocationPermissionsGranted(requireContext()))
                    setInteractButtonState(ButtonState.LOCATION, !permGranted)
                    interactButton.setOnClickListener {
                        //ask location permission
                        locationRequestResLauncher.launch(Permissions.LOCATION_PERMISSIONS)
                    }
                    interactButton.visibility = View.VISIBLE
                }

            }
            5 ->{
                setImageBitmap(imageHolder, R.drawable.tuto_line_det)
                textView.text = utils.convertHtml(getString(R.string.tutorial_line))
            }
            6-> {
                setImageBitmap(imageHolder,R.drawable.tuto_menu)
                setTextHtmlDescription(R.string.tutorial_menu)
                //this is the cheapest trick ever lol
                if(!Permissions.isNotificationPermissionNeeded()){
                    //no other screen needed
                    val button = root.findViewById<Button>(R.id.closeAllButton)
                    button.visibility = View.VISIBLE
                    button.setOnClickListener {
                        listener.closeIntroduction()
                    }
                }
            }
            /// IMPORTANT: THIS NEEDS TO BE LAST SCREEN, ALWAYS
            7 ->{
                imageHolder.setImageDrawable(ContextCompat.getDrawable(requireContext(),R.drawable.megaphone))
                setTextHtmlDescription(R.string.tutorial_permissions)
                if(Permissions.isNotificationPermissionNeeded()) {
                    val disabled = Permissions.isPermissionGranted(
                        requireContext(),
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )
                    setInteractButtonState(ButtonState.NOTIFICATIONS, !disabled)
                    interactButton.setOnClickListener {
                        if (Permissions.isNotificationPermissionNeeded()) {
                            notificationsReqLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    interactButton.visibility = View.VISIBLE
                }
                //show the other button
                val button = root.findViewById<Button>(R.id.closeAllButton)
                button.setText(R.string.close_tutorial_short)
                button.visibility = View.VISIBLE
                button.setOnClickListener {
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

    private fun setInteractButtonState(state: ButtonState, active: Boolean){
        when(state){
            ButtonState.LOCATION ->
                if(active) interactButton.setText(R.string.grant_location_permission)
                else interactButton.setText(R.string.location_permission_granted)
            ButtonState.NOTIFICATIONS -> if(active)
                interactButton.setText(R.string.grant_notification_permission)
                else interactButton.setText(R.string.notification_permission_granted)
        }
        if(!active) {
            val color = ViewUtils.getColorFromTheme(requireContext(), R.attr.colorAccent)//ContextCompat.getColor(requireContext(), R.style.AppTheme.)
            if(Build.VERSION.SDK_INT >= 29)
                interactButton.background.colorFilter = BlendModeColorFilter(color, BlendMode.MULTIPLY)
            else
                interactButton.background.setColorFilter(
                    color,PorterDuff.Mode.MULTIPLY)
            interactButton.isClickable = false
        }
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
        fun View.disable() {
            background.setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY)
            isClickable = false
        }
        enum class ButtonState{
            LOCATION, NOTIFICATIONS
        }
    }

    interface IntroListener{
        fun closeIntroduction()
    }
}