/*
	BusTO - Arrival times for Turin public transports.
    Copyright (C) 2014  Valerio Bozzolan

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
package it.reyboz.bustorino

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import it.reyboz.bustorino.backend.utils
import it.reyboz.bustorino.middleware.BarcodeScanUtils

class ActivityAbout : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        //TOP TEXT VIEW
        val topTextView = checkNotNull(findViewById<TextView>(R.id.topTextView))
        val telegramTextView= checkNotNull(findViewById<TextView>(R.id.telegramTextView))
        val howTextView = checkNotNull(findViewById<TextView>(R.id.howDoesItWorkTextView))
        val bottomTextView= checkNotNull(findViewById<TextView>(R.id.bottomAboutTextView))

        val textviews = arrayOf(topTextView, telegramTextView,howTextView,bottomTextView)
        val stringsForTextViews  = arrayOf(R.string.about_history_top, R.string.about_channel, R.string.about_how, R.string.about_history_bottom)

        for (i in 0 until 4) {
            val htmlText = utils.convertHtml(
                resources.getString(
                    stringsForTextViews[i]
                )
            )
            //val aboutTextView = checkNotNull(findViewById<TextView>(R.id.aboutTextView))
            val textView = textviews[i]
            textView.text = htmlText
            textView.movementMethod = LinkMovementMethod.getInstance()
        }

        /*Toolbar mToolbar = findViewById(R.id.default_toolbar);
        setSupportActionBar(mToolbar);

         */
        val mToolbar = findViewById<Toolbar>(R.id.default_toolbar)
        setSupportActionBar(mToolbar)

        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val versionTextView = findViewById<TextView>(R.id.versionTextView)
        Log.d("BusTO About", "The version text view is: $versionTextView")
        versionTextView.text = resources.getText(R.string.app_version).toString() + ": " + BuildConfig.VERSION_NAME

        val openTelegramButton = findViewById<Button>(R.id.openTelegramButton)
        openTelegramButton.setOnClickListener { view: View? ->
            val trueIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=busto_fdroid"))
            if (BarcodeScanUtils.checkTargetPackageExists(this, trueIntent)) startActivity(trueIntent)
            else {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/busto_fdroid"))
                startActivity(intent)
                // Toast.makeText(this, "Install Telegram and retry", Toast.LENGTH_SHORT).show();
            }
        }
        val openSourceButton = findViewById<Button>(R.id.openSourceButton)
        openSourceButton.setOnClickListener { view: View? ->
            utils.openIceweasel(utils.SOURCE_CODE_URL, this)
        }
        val contributeButton = findViewById<Button>(R.id.openContributeButton)
        contributeButton.setOnClickListener { view: View? ->
            utils.openIceweasel(getString(R.string.hack_url), this)
        }
        val openTutorialButton = findViewById<Button>(R.id.openTutorialButton)
        openTutorialButton.setOnClickListener {
            startIntroductionActivity()
        }

        // handle the device "insets"
        ViewCompat.setOnApplyWindowInsetsListener(versionTextView,
            OnApplyWindowInsetsListener { v: View?, windowInsets: WindowInsetsCompat? ->
                val insets = windowInsets!!.getInsets(WindowInsetsCompat.Type.systemBars())
                // Apply the insets as a margin to the view. This solution sets only the
                // bottom, left, and right dimensions, but you can apply whichever insets are
                // appropriate to your layout. You can also update the view padding if that's
                // more appropriate.
                val mlp = v!!.layoutParams as MarginLayoutParams
                mlp.leftMargin = insets.left
                mlp.bottomMargin = insets.bottom
                mlp.rightMargin = insets.right
                v.layoutParams = mlp

                WindowInsetsCompat.CONSUMED
            })

        ViewCompat.setOnApplyWindowInsetsListener(mToolbar) { v: View?, windowInsets: WindowInsetsCompat? ->
            val insets = windowInsets!!.getInsets(WindowInsetsCompat.Type.systemBars())
            v?.updatePadding(top=insets.top)
            windowInsets
        }
    }

    fun startIntroductionActivity() {
        val intent = Intent(this, ActivityIntro::class.java)
        intent.putExtra(ActivityIntro.RESTART_MAIN, false)
        startActivity(intent)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Respond to the action bar's Up/Home button
        if (item.itemId == android.R.id.home) { //NavUtils.navigateUpFromSameTask(this);
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
