package it.reyboz.bustorino;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import it.reyboz.bustorino.fragments.PreferFragment;

public class ActivitySettings extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        ActionBar ab = getSupportActionBar();
        if(ab!=null) {
            ab.setIcon(R.drawable.ic_launcher);
            ab.setDisplayHomeAsUpEnabled(true);
        } else {
            Log.e("SETTINGS_ACTIV","ACTION BAR IS NULL");
        }

        FragmentManager framan = getSupportFragmentManager();
        FragmentTransaction ft = framan.beginTransaction();
        ft.add(R.id.setting_container,new PreferFragment());
        ft.commitNow();
    }

}
/**
 * Interesting thing
 * Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
 *                         .setAction("Action", null).show();
 */
