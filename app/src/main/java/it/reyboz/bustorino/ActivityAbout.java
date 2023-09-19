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
package it.reyboz.bustorino;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.middleware.BarcodeScanUtils;

public class ActivityAbout extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        Spanned htmlText = utils.convertHtml(getResources().getString(
                R.string.about_history));
        TextView aboutTextView = findViewById(R.id.aboutTextView);
        assert aboutTextView != null;
        aboutTextView.setText(htmlText);
        aboutTextView.setMovementMethod(LinkMovementMethod.getInstance());

        Toolbar mToolbar = findViewById(R.id.default_toolbar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar()!=null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        TextView versionText = findViewById(R.id.versionTextView);
        Log.d("BusTO About", "The version text view is: "+versionText);
        versionText.setText(getResources().getText(R.string.app_version)+": "+BuildConfig.VERSION_NAME);

        Button openTelegramButton = findViewById(R.id.openTelegramButton);
        openTelegramButton.setOnClickListener(view -> {

            Intent trueIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=busto_fdroid"));
            if(BarcodeScanUtils.checkTargetPackageExists(this, trueIntent))
                startActivity(trueIntent);
            else{
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/busto_fdroid"));
                startActivity(intent);
               // Toast.makeText(this, "Install Telegram and retry", Toast.LENGTH_SHORT).show();
            }

        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Respond to the action bar's Up/Home button
        if (item.getItemId() == android.R.id.home) {//NavUtils.navigateUpFromSameTask(this);
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
