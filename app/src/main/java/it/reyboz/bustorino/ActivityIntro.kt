package it.reyboz.bustorino

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import it.reyboz.bustorino.data.PreferencesHolder
import it.reyboz.bustorino.fragments.IntroFragment

class ActivityIntro : AppCompatActivity(), IntroFragment.IntroListener {

    private lateinit var viewPager : ViewPager2
    private lateinit var btnForward: ImageButton
    private lateinit var btnBackward: ImageButton
    private lateinit var closeBottomButton: ImageButton

    private var restartMain = true



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro)

        viewPager = findViewById(R.id.viewPager)
        btnBackward = findViewById(R.id.btnPrevious)
        btnForward = findViewById(R.id.btnNext)
        closeBottomButton = findViewById(R.id.btnCompactClose)

        val extras = intent.extras
        if(extras!=null){
            restartMain = extras.getBoolean(RESTART_MAIN)
        }


        val adapter = IntroPagerAdapter(this)
        viewPager.adapter = adapter

        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        val tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            Log.d(DEBUG_TAG, "tabview on position $pos")

        }
        tabLayoutMediator.attach()


        btnForward.setOnClickListener {
            viewPager.setCurrentItem(viewPager.currentItem+1,true)
        }
        btnBackward.setOnClickListener {
            viewPager.setCurrentItem(viewPager.currentItem-1, true)
        }
        /*closeBottomButton.setOnClickListener {
            closeIntroduction()
        }

         */

        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                if(position == 0){
                    btnBackward.visibility = View.INVISIBLE
                } else{
                    btnBackward.visibility = View.VISIBLE
                }
                if(position == NUM_ITEMS-1){
                    btnForward.visibility = View.INVISIBLE
                    closeBottomButton.visibility = View.VISIBLE
                }else if(position == NUM_ITEMS-2){
                    if(closeBottomButton.visibility == View.VISIBLE) {
                        closeBottomButton.visibility = View.INVISIBLE
                        btnForward.visibility = View.VISIBLE
                    }
                    //btnForward.setImageDrawable(ResourcesCompat.getDrawable(resources, R.drawable.arrow_forward_white, null))
                    //btnForward.setBackgroundColor(ResourcesCompat.getColor(resources,R.attr.colorAccent, theme))
                    /*val
                    GET THE COLOR VALUE OF THE THEMER
                    colo = TypedValue()
                    theme.resolveAttribute(R.attr.colorAccent,colo, true)
                    btnForward.backgroundTintList  //(colo.data)

                     */
                }
            }


        })

        closeBottomButton.setOnClickListener {
            closeIntroduction()
        }
    }



    /**
     * A simple pager adapter that represents 5 ScreenSlidePageFragment objects, in
     * sequence.
     */
    private inner class IntroPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = NUM_ITEMS

        override fun createFragment(position: Int): Fragment = IntroFragment.newInstance(position)
    }

    companion object{
        const private val DEBUG_TAG = "BusTO-IntroActivity"
        const val RESTART_MAIN = "restartMainActivity"

        const val NUM_ITEMS = 7
    }

    override fun closeIntroduction() {
        if(restartMain) startActivity(Intent(this, ActivityPrincipal::class.java))
        val pref = PreferencesHolder.getMainSharedPreferences(this)
        val editor = pref.edit()
        editor.putBoolean(PreferencesHolder.PREF_INTRO_ACTIVITY_RUN, true)
        //use commit so we don't "lose" info
        editor.commit()
        finish()
    }

}