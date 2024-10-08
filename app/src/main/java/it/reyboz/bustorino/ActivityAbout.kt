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
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
        if (supportActionBar != null) supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val versionText = findViewById<TextView>(R.id.versionTextView)
        Log.d("BusTO About", "The version text view is: $versionText")
        versionText.text = resources.getText(R.string.app_version).toString() + ": " + BuildConfig.VERSION_NAME

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
