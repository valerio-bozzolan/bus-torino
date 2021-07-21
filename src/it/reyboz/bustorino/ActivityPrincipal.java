package it.reyboz.bustorino;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.data.DBUpdateWorker;
import it.reyboz.bustorino.data.DatabaseUpdate;
import it.reyboz.bustorino.fragments.FavoritesFragment;
import it.reyboz.bustorino.fragments.FragmentKind;
import it.reyboz.bustorino.fragments.FragmentListenerMain;
import it.reyboz.bustorino.fragments.MainScreenFragment;
import it.reyboz.bustorino.fragments.MapFragment;
import it.reyboz.bustorino.middleware.GeneralActivity;

import static it.reyboz.bustorino.backend.utils.getBusStopIDFromUri;
import static it.reyboz.bustorino.backend.utils.openIceweasel;

public class ActivityPrincipal extends GeneralActivity implements FragmentListenerMain {
    private DrawerLayout mDrawer;
    private NavigationView mNavView;
    private ActionBarDrawerToggle drawerToggle;
    private final static String DEBUG_TAG="BusTO Act Principal";

    private final static String TAG_FAVORITES="favorites_frag";
    private Snackbar snackbar;

    private boolean showingMainFragmentFromOther = false;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_principal);
        final SharedPreferences theShPr = getMainSharedPreferences();


        Toolbar mToolbar = findViewById(R.id.default_toolbar);
        setSupportActionBar(mToolbar);
        if (getSupportActionBar()!=null)
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        else Log.w(DEBUG_TAG, "NO ACTION BAR");

        mToolbar.setOnMenuItemClickListener(new ToolbarItemClickListener(this));


        mDrawer = findViewById(R.id.drawer_layout);
        drawerToggle = setupDrawerToggle(mToolbar);

        // Setup toggle to display hamburger icon with nice animation
        drawerToggle.setDrawerIndicatorEnabled(true);

        drawerToggle.syncState();
        mDrawer.addDrawerListener(drawerToggle);

        mDrawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                hideKeyboard();
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {

            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });


        mNavView = findViewById(R.id.nvView);

        setupDrawerContent(mNavView);
        /*View header = mNavView.getHeaderView(0);

        */
        //mNavView.getMenu().findItem(R.id.versionFooter).

        /// LEGACY CODE
        //---------------------------- START INTENT CHECK QUEUE ------------------------------------

        // Intercept calls from URL intent
        boolean tryedFromIntent = false;

        String busStopID = null;
        Uri data = getIntent().getData();
        if (data != null) {
            busStopID = getBusStopIDFromUri(data);
            tryedFromIntent = true;
        }

        // Intercept calls from other activities
        if (!tryedFromIntent) {
            Bundle b = getIntent().getExtras();
            if (b != null) {
                busStopID = b.getString("bus-stop-ID");

                /**
                 * I'm not very sure if you are coming from an Intent.
                 * Some launchers work in strange ways.
                 */
                tryedFromIntent = busStopID != null;
            }
        }

        //---------------------------- END INTENT CHECK QUEUE --------------------------------------

        if (busStopID == null) {
            // Show keyboard if can't start from intent
            // JUST DON'T
            // showKeyboard();

            // You haven't obtained anything... from an intent?
            if (tryedFromIntent) {

                // This shows a luser warning
                Toast.makeText(getApplicationContext(),
                        R.string.insert_bus_stop_number_error, Toast.LENGTH_SHORT).show();
            }
        } else {
            // If you are here an intent has worked successfully
            //setBusStopSearchByIDEditText(busStopID);

            requestArrivalsForStopID(busStopID);
        }
        //Try (hopefully) database update
        PeriodicWorkRequest wr = new PeriodicWorkRequest.Builder(DBUpdateWorker.class, 1, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .build();
        final WorkManager workManager = WorkManager.getInstance(this);

        final int version = theShPr.getInt(DatabaseUpdate.DB_VERSION_KEY, -10);
        if (version >= 0)
            workManager.enqueueUniquePeriodicWork(DBUpdateWorker.DEBUG_TAG,
                    ExistingPeriodicWorkPolicy.KEEP, wr);
        else workManager.enqueueUniquePeriodicWork(DBUpdateWorker.DEBUG_TAG,
                ExistingPeriodicWorkPolicy.REPLACE, wr);
        /*
        Set database update
         */
        workManager.getWorkInfosForUniqueWorkLiveData(DBUpdateWorker.DEBUG_TAG)
                .observe(this, workInfoList -> {
                    // If there are no matching work info, do nothing
                    if (workInfoList == null || workInfoList.isEmpty()) {
                        return;
                    }
                    Log.d(DEBUG_TAG, "WorkerInfo: "+workInfoList);

                    boolean showProgress = false;
                    for (WorkInfo workInfo : workInfoList) {
                        if (workInfo.getState() == WorkInfo.State.RUNNING) {
                            showProgress = true;
                            break;
                        }
                    }

                    if (showProgress) {
                        createDefaultSnackbar();
                    } else {
                        if(snackbar!=null) {
                            snackbar.dismiss();
                            snackbar = null;
                        }
                    }

                });
        // show the main fragment
        showMainFragment();


    }
    private ActionBarDrawerToggle setupDrawerToggle(Toolbar toolbar) {
        // NOTE: Make sure you pass in a valid toolbar reference.  ActionBarDrawToggle() does not require it
        // and will not render the hamburger icon without it.
        return new ActionBarDrawerToggle(this, mDrawer, toolbar, R.string.drawer_open,  R.string.drawer_close);

    }

    /**
     * Setup drawer actions
     * @param navigationView the navigation view on which to set the callbacks
     */
    private void setupDrawerContent(NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(
                menuItem -> {
                    if (menuItem.getItemId() == R.id.drawer_action_settings) {
                        Log.d("MAINBusTO", "Pressed button preferences");
                        closeDrawerIfOpen();
                        startActivity(new Intent(ActivityPrincipal.this, ActivitySettings.class));
                        return true;
                    } else if(menuItem.getItemId() == R.id.nav_favorites_item){
                        closeDrawerIfOpen();
                        //get Fragment
                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                        FavoritesFragment fragment = FavoritesFragment.newInstance();
                        ft.replace(R.id.mainActContentFrame,fragment, TAG_FAVORITES);
                        ft.addToBackStack(null);
                        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
                        ft.commit();
                        return true;
                    } else if(menuItem.getItemId() == R.id.nav_arrivals){
                        closeDrawerIfOpen();
                        showMainFragment();
                        return true;
                    } else if(menuItem.getItemId() == R.id.nav_map_item){
                        closeDrawerIfOpen();
                        final String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
                        int result = askForPermissionIfNeeded(permission, STORAGE_PERMISSION_REQ);
                        switch (result) {
                            case PERMISSION_OK:
                                createAndShowMapFragment(null);
                                break;
                            case PERMISSION_ASKING:
                                permissionDoneRunnables.put(permission,
                                        () -> createAndShowMapFragment(null));
                                break;
                            case PERMISSION_NEG_CANNOT_ASK:
                                String storage_perm = getString(R.string.storage_permission);
                                String text = getString(R.string.too_many_permission_asks,  storage_perm);
                                Toast.makeText(getApplicationContext(),text, Toast.LENGTH_LONG).show();
                        }
                        return true;
                    }
                    //selectDrawerItem(menuItem);
                    Log.d(DEBUG_TAG, "pressed item "+menuItem.toString());

                    return true;

                });

    }

    private void closeDrawerIfOpen(){
        if (mDrawer.isDrawerOpen(GravityCompat.START))
            mDrawer.closeDrawer(GravityCompat.START);
    }


    // `onPostCreate` called when activity start-up is complete after `onStart()`
    // NOTE 1: Make sure to override the method with only a single `Bundle` argument
    // Note 2: Make sure you implement the correct `onPostCreate(Bundle savedInstanceState)` method.
    // There are 2 signatures and only `onPostCreate(Bundle state)` shows the hamburger icon.
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Pass any configuration change to the drawer toggles
        drawerToggle.onConfigurationChanged(newConfig);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.extra_menu_items, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode==STORAGE_PERMISSION_REQ){
            final String storagePerm = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(DEBUG_TAG, "Permissions check: " + Arrays.toString(permissions));

                if (permissionDoneRunnables.containsKey(storagePerm)) {
                    Runnable toRun = permissionDoneRunnables.get(storagePerm);
                    if (toRun != null)
                        toRun.run();
                    permissionDoneRunnables.remove(storagePerm);
                }
            } else {
                //permission denied
                showToastMessage(R.string.permission_storage_maps_msg, false);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        int[] cases = {R.id.nav_arrivals, R.id.nav_favorites_item};
        Log.d(DEBUG_TAG, "Item pressed");


        if (item.getItemId() == android.R.id.home) {
            mDrawer.openDrawer(GravityCompat.START);
            return true;
        }

        if (drawerToggle.onOptionsItemSelected(item)) {

            return true;

        }
        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onBackPressed() {
        boolean foundFragment = false;
        Fragment shownFrag = getSupportFragmentManager().findFragmentById(R.id.mainActContentFrame);
        if (mDrawer.isDrawerOpen(GravityCompat.START))
            mDrawer.closeDrawer(GravityCompat.START);
        else if(shownFrag != null && shownFrag.isVisible() && shownFrag.getChildFragmentManager().getBackStackEntryCount() > 0){
            //if we have been asked to show a stop from another fragment, we should go back even in the main
            shownFrag.getChildFragmentManager().popBackStackImmediate();
            if(showingMainFragmentFromOther && getSupportFragmentManager().getBackStackEntryCount() > 0){
                getSupportFragmentManager().popBackStack();
            }
        }
        else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
        }
        else
            super.onBackPressed();
    }

    private void createDefaultSnackbar() {
        if (snackbar == null) {
            snackbar = Snackbar.make(findViewById(R.id.searchButton), R.string.database_update_message, Snackbar.LENGTH_INDEFINITE);
        }
        snackbar.show();
    }

    private MainScreenFragment createAndShowMainFragment(){
        FragmentManager fraMan = getSupportFragmentManager();

        MainScreenFragment fragment = MainScreenFragment.newInstance();

        FragmentTransaction transaction = fraMan.beginTransaction();
        transaction.replace(R.id.mainActContentFrame, fragment, MainScreenFragment.FRAGMENT_TAG);
        transaction.commit();
        return fragment;
    }

    /**
     * Show the fragment by adding it to the backstack
     * @param fraMan the fragmentManager
     * @param fragment the fragment
     */
    private static void showMainFragment(FragmentManager fraMan, MainScreenFragment fragment){
        fraMan.beginTransaction().replace(R.id.mainActContentFrame, fragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                /*.setCustomAnimations(
                        R.anim.slide_in,  // enter
                        R.anim.fade_out,  // exit
                        R.anim.fade_in,   // popEnter
                        R.anim.slide_out  // popExit
                )*/
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }

    private MainScreenFragment showMainFragment(){
        FragmentManager fraMan = getSupportFragmentManager();
        Fragment fragment = fraMan.findFragmentByTag(MainScreenFragment.FRAGMENT_TAG);
        MainScreenFragment mainScreenFragment = null;
        if (fragment==null | !(fragment instanceof MainScreenFragment)){
            mainScreenFragment = createAndShowMainFragment();
        }
        else if(!fragment.isVisible()){


            mainScreenFragment = (MainScreenFragment) fragment;
            showMainFragment(fraMan, mainScreenFragment);
            Log.d(DEBUG_TAG, "Found the main fragment");
        } else{
            mainScreenFragment = (MainScreenFragment) fragment;
        }
        return mainScreenFragment;
    }
    @Nullable
    private MainScreenFragment getMainFragmentIfVisible(){
        FragmentManager fraMan = getSupportFragmentManager();
        Fragment fragment = fraMan.findFragmentByTag(MainScreenFragment.FRAGMENT_TAG);
        if (fragment!= null && fragment.isVisible()) return (MainScreenFragment) fragment;
        else return null;
    }


    @Override
    public void showFloatingActionButton(boolean yes) {
        //TODO
    }
    /*
    public void setDrawerSelectedItem(String fragmentTag){
        switch (fragmentTag){
            case MainScreenFragment.FRAGMENT_TAG:
                mNavView.setCheckedItem(R.id.nav_arrivals);
                break;
            case MapFragment.FRAGMENT_TAG:

                break;

            case FavoritesFragment.FRAGMENT_TAG:
                mNavView.setCheckedItem(R.id.nav_favorites_item);
                break;
        }
    }*/

    @Override
    public void readyGUIfor(FragmentKind fragmentType) {
        MainScreenFragment probableFragment = getMainFragmentIfVisible();
        if (probableFragment!=null){
            probableFragment.readyGUIfor(fragmentType);
        }
        int titleResId;
        switch (fragmentType){
            case MAP:
                mNavView.setCheckedItem(R.id.nav_map_item);
                titleResId = R.string.map;
                break;
            case FAVORITES:
                mNavView.setCheckedItem(R.id.nav_favorites_item);
                titleResId = R.string.nav_favorites_text;
                break;
            case ARRIVALS:
                titleResId = R.string.nav_arrivals_text;
                mNavView.setCheckedItem(R.id.nav_arrivals);
                break;
            case STOPS:
                titleResId = R.string.stop_search_view_title;
                mNavView.setCheckedItem(R.id.nav_arrivals);
                break;
            case MAIN_SCREEN_FRAGMENT:
            case NEARBY_STOPS:
            case NEARBY_ARRIVALS:
                titleResId=R.string.app_name_full;
                mNavView.setCheckedItem(R.id.nav_arrivals);
                break;
            default:
                titleResId = 0;
        }
        if(getSupportActionBar()!=null && titleResId!=0)
            getSupportActionBar().setTitle(titleResId);
    }

    @Override
    public void requestArrivalsForStopID(String ID) {
        //register if the request came from the main fragment or not
        MainScreenFragment probableFragment = getMainFragmentIfVisible();
        showingMainFragmentFromOther = (probableFragment==null);

        if (showingMainFragmentFromOther){
            FragmentManager fraMan = getSupportFragmentManager();
            Fragment fragment = fraMan.findFragmentByTag(MainScreenFragment.FRAGMENT_TAG);
            if(fragment!=null){
                //the fragment is there but not shown
                probableFragment = (MainScreenFragment) fragment;
                // set the flag
                probableFragment.setSuppressArrivalsReload(true);
                showMainFragment(fraMan, probableFragment);
            } else {
                // we have no fragment
                probableFragment = createAndShowMainFragment();
            }
        }
        probableFragment.requestArrivalsForStopID(ID);
        mNavView.setCheckedItem(R.id.nav_arrivals);
    }

    @Override
    public void toggleSpinner(boolean state) {
        MainScreenFragment probableFragment = getMainFragmentIfVisible();
        if (probableFragment!=null){
            probableFragment.toggleSpinner(state);
        }
    }

    @Override
    public void enableRefreshLayout(boolean yes) {
        MainScreenFragment probableFragment = getMainFragmentIfVisible();
        if (probableFragment!=null){
            probableFragment.enableRefreshLayout(yes);
        }
    }

    //Map Fragment stuff
    void createAndShowMapFragment(@Nullable Stop stop){
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        MapFragment fragment = stop == null? MapFragment.getInstance(): MapFragment.getInstance(stop);
        ft.replace(R.id.mainActContentFrame, fragment, MapFragment.FRAGMENT_TAG);
        ft.addToBackStack(null);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }

    class ToolbarItemClickListener implements Toolbar.OnMenuItemClickListener{
        private final Context activityContext;

        public ToolbarItemClickListener(Context activityContext) {
            this.activityContext = activityContext;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_about:
                    startActivity(new Intent(ActivityPrincipal.this, ActivityAbout.class));
                    return true;
                case R.id.action_hack:
                    openIceweasel(getString(R.string.hack_url), activityContext);
                    return true;
                case R.id.action_source:
                    openIceweasel("https://gitpull.it/source/libre-busto/", activityContext);
                    return true;
                case R.id.action_licence:
                    openIceweasel("https://www.gnu.org/licenses/gpl-3.0.html", activityContext);
                    return true;
                default:
            }
            return false;
        }
    }
}
