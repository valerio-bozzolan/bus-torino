package it.reyboz.bustorino;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import it.reyboz.bustorino.fragments.SettingsFragment;
import it.reyboz.bustorino.middleware.GeneralActivity;

public class ActivitySettings extends GeneralActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        final Toolbar mToolbar = findViewById(R.id.default_toolbar);
        setSupportActionBar(mToolbar);
        ActionBar ab = getSupportActionBar();
        if(ab!=null) {
            //ab.setIcon(R.drawable.ic_launcher);
            ab.setDisplayHomeAsUpEnabled(true);
        } else {
            Log.e("SETTINGS_ACTIV","ACTION BAR IS NULL");
        }

        FragmentManager framan = getSupportFragmentManager();
        FragmentTransaction ft = framan.beginTransaction();
        ft.replace(R.id.setting_container,new SettingsFragment());
        ft.commit();

        ViewCompat.setOnApplyWindowInsetsListener(mToolbar, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            v.setPadding(0, insets.top, 0, 0);

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            return WindowInsetsCompat.CONSUMED;
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.setting_container)
                ,this.applyBottomAndBordersInsetsListener);

    }

}
/*
 * Interesting thing
 * Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
 *                         .setAction("Action", null).show();
 */
