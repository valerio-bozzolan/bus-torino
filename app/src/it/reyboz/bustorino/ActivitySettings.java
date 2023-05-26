package it.reyboz.bustorino;

import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import it.reyboz.bustorino.fragments.SettingsFragment;

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
        ft.replace(R.id.setting_container,new SettingsFragment());
        ft.commit();
    }

}
/**
 * Interesting thing
 * Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
 *                         .setAction("Action", null).show();
 */
