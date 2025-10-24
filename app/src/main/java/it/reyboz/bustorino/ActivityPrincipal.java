/*
	BusTO - Arrival times for Turin public transport.
    Copyright (C) 2021 Fabio Mazza

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

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.*;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.data.DBUpdateWorker;
import it.reyboz.bustorino.data.DatabaseUpdate;
import it.reyboz.bustorino.data.PreferencesHolder;
import it.reyboz.bustorino.data.gtfs.GtfsDatabase;
import it.reyboz.bustorino.fragments.*;
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
    private boolean onCreateComplete = false;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(DEBUG_TAG, "onCreate, savedInstanceState is: "+savedInstanceState);
        setContentView(R.layout.activity_principal);
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
         */
        boolean showingArrivalsFromIntent = false;

        final Toolbar mToolbar = findViewById(R.id.default_toolbar);
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
            Log.d(DEBUG_TAG, "Opening Intent: busStopID: "+busStopID);
            tryedFromIntent = true;
        }

        // Intercept calls from other activities
        if (!tryedFromIntent) {
            Bundle b = getIntent().getExtras();
            if (b != null) {
                busStopID = b.getString("bus-stop-ID");

                /*
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
            //Log.d(DEBUG_TAG, "Requesting arrivals for stop "+busStopID+" from intent");
            requestArrivalsForStopID(busStopID); //this shows the fragment, too
            showingArrivalsFromIntent = true;
        }
        //database check
        GtfsDatabase gtfsDB = GtfsDatabase.Companion.getGtfsDatabase(this);

        final int db_version = gtfsDB.getOpenHelper().getReadableDatabase().getVersion();
        boolean dataUpdateRequested = false;
        final SharedPreferences theShPr = getMainSharedPreferences();

        final int old_version = PreferencesHolder.getGtfsDBVersion(theShPr);
        Log.d(DEBUG_TAG, "GTFS Database: old version is "+old_version+ ", new version is "+db_version);
        if (old_version < db_version){
            //decide update conditions in the future
            if(old_version < 2 && db_version >= 2) {
                dataUpdateRequested = true;
                DatabaseUpdate.requestDBUpdateWithWork(this, true, true);
            }
            PreferencesHolder.setGtfsDBVersion(theShPr, db_version);
        }
        //Try (hopefully) database update
        if(!dataUpdateRequested)
            DatabaseUpdate.requestDBUpdateWithWork(this, false, false);

        /*
        Watch for database update
         */
        final WorkManager workManager = WorkManager.getInstance(this);
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
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.mainActContentFrame);
        Log.d(DEBUG_TAG, "OnCreate the fragment is "+f);
        String vl = PreferenceManager.getDefaultSharedPreferences(this).getString(SettingsFragment.PREF_KEY_STARTUP_SCREEN, "");
        //if (vl.length() == 0 || vl.equals("arrivals")) {
        //    showMainFragment();
        Log.d(DEBUG_TAG, "The default screen to open is: "+vl);
        if (showingArrivalsFromIntent){
            //do nothing but exclude a case
        }else if (savedInstanceState==null) {
            //we are not restarting the activity from nothing
            if (vl.equals("map")) {
                requestMapFragment(false);
            } else if (vl.equals("favorites")) {
                checkAndShowFavoritesFragment(getSupportFragmentManager(), false);
            } else if (vl.equals("lines")) {
                showLinesFragment(getSupportFragmentManager(), false, null);
            } else {
                showMainFragment(false);
            }
        }
        onCreateComplete = true;

        //last but not least, set the good default values
        setDefaultSettingsValuesWhenMissing();
        // handle the device "insets"
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootRelativeLayout), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Apply the insets as a margin to the view. This solution sets only the
            // bottom, left, and right dimensions, but you can apply whichever insets are
            // appropriate to your layout. You can also update the view padding if that's
            // more appropriate.
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.leftMargin = insets.left;
            mlp.bottomMargin = insets.bottom;
            mlp.rightMargin = insets.right;
            v.setLayoutParams(mlp);
            //set for toolbar
            //mlp = (ViewGroup.MarginLayoutParams) mToolbar.getLayoutParams();
            //mlp.topMargin = insets.top;
            //mToolbar.setLayoutParams(mlp);
            mToolbar.setPadding(0, insets.top, 0, 0);

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            return WindowInsetsCompat.CONSUMED;
        });

        /*
        ViewCompat.setOnApplyWindowInsetsListener(mToolbar, (v, windowInsets) -> {
            Insets statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            // Apply the insets as a margin to the view.
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.topMargin = statusBarInsets.top;
            v.setLayoutParams(mlp);
            v.setPadding(0, statusBarInsets.top, 0, 0);

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            return WindowInsetsCompat.CONSUMED;
        });

         */
        //to properly handle IME
        WindowInsetsControllerCompat insetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());

        if (insetsController != null) {
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
        }


        //check if first run activity (IntroActivity) has been started once or not
        boolean hasIntroRun = theShPr.getBoolean(PreferencesHolder.PREF_INTRO_ACTIVITY_RUN,false);
        if(!hasIntroRun){
            startIntroductionActivity();
        }
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
                        checkAndShowFavoritesFragment(getSupportFragmentManager(), true);
                        return true;
                    } else if(menuItem.getItemId() == R.id.nav_arrivals){
                        closeDrawerIfOpen();
                        showMainFragment(true);
                        return true;
                    } else if(menuItem.getItemId() == R.id.nav_map_item){
                        closeDrawerIfOpen();
                        requestMapFragment(true);
                        return true;
                    } else if (menuItem.getItemId() == R.id.nav_lines_item) {
                        closeDrawerIfOpen();
                        showLinesFragment(getSupportFragmentManager(), true,null);
                        return true;
                    } else if(menuItem.getItemId() ==  R.id.drawer_action_info) {
                        closeDrawerIfOpen();
                        startActivity(new Intent(ActivityPrincipal.this, ActivityAbout.class));
                        return true;
                    }
                    //selectDrawerItem(menuItem);
                    Log.d(DEBUG_TAG, "pressed item "+menuItem);

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
        getMenuInflater().inflate(R.menu.principal_menu, menu);
        MenuItem experimentsMenuItem = menu.findItem(R.id.action_experiments);
        SharedPreferences shPr = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean exper_On = shPr.getBoolean(getString(R.string.pref_key_experimental), false);
        experimentsMenuItem.setVisible(exper_On);
        return super.onCreateOptionsMenu(menu);
    }

    //requesting permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode==STORAGE_PERMISSION_REQ){
            final String storagePerm = Manifest.permission.WRITE_EXTERNAL_STORAGE;
            if (permissionDoneRunnables.containsKey(storagePerm)) {
                Runnable toRun = permissionDoneRunnables.get(storagePerm);
                if (toRun != null)
                    toRun.run();
                permissionDoneRunnables.remove(storagePerm);
            }

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
            if(shownFrag instanceof MainScreenFragment){
                //we have to stop the arrivals reload
                ((MainScreenFragment) shownFrag).cancelReloadArrivalsIfNeeded();
            }
            shownFrag.getChildFragmentManager().popBackStack();
            if(showingMainFragmentFromOther && getSupportFragmentManager().getBackStackEntryCount() > 0){
                getSupportFragmentManager().popBackStack();
                Log.d(DEBUG_TAG, "Popping main back stack also");
            }
        }
        else if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStack();
            Log.d(DEBUG_TAG, "Popping main frame backstack for fragments");
        }
        else
            super.onBackPressed();
    }

    /**
     * Create and show the SnackBar with the message
     */
    private void createDefaultSnackbar() {

        View baseView = null;
        boolean showSnackbar = true;
        final Fragment frag = getSupportFragmentManager().findFragmentById(R.id.mainActContentFrame);
        if (frag instanceof ScreenBaseFragment){
            baseView = ((ScreenBaseFragment) frag).getBaseViewForSnackBar();
            showSnackbar = ((ScreenBaseFragment) frag).showSnackbarOnDBUpdate();
        }
        if (baseView == null) baseView = findViewById(R.id.mainActContentFrame);
        //if (baseView == null) Log.e(DEBUG_TAG, "baseView null for default snackbar, probably exploding now");
        if (baseView !=null && showSnackbar) {
            this.snackbar = Snackbar.make(baseView, R.string.database_update_msg_inapp, Snackbar.LENGTH_INDEFINITE);
            if (frag instanceof ScreenBaseFragment){
                ((ScreenBaseFragment) frag).setSnackbarPropertiesBeforeShowing(this.snackbar);
            }
            this.snackbar.show();

        } else{
            Log.e(DEBUG_TAG, "Asked to show the snackbar but the baseView is null");
        }
    }

    /**
     * Show the fragment by adding it to the backstack
     * @param fraMan the fragmentManager
     * @param fragment the fragment
     */
    private static void showMainFragment(FragmentManager fraMan, MainScreenFragment fragment, boolean addToBackStack){
        FragmentTransaction ft  = fraMan.beginTransaction()
                .replace(R.id.mainActContentFrame, fragment, MainScreenFragment.FRAGMENT_TAG)
                .setReorderingAllowed(false)
                /*.setCustomAnimations(
                        R.anim.slide_in,  // enter
                        R.anim.fade_out,  // exit
                        R.anim.fade_in,   // popEnter
                        R.anim.slide_out  // popExit
                )*/
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        if (addToBackStack)  ft.addToBackStack(null);
        ft.commit();
    }
    /**
     * Show the fragment by adding it to the backstack
     * @param fraMan the fragmentManager
     * @param arguments args for the fragment
     */
    private static void createShowMainFragment(FragmentManager fraMan,@Nullable Bundle arguments, boolean addToBackStack){
        FragmentTransaction ft  = fraMan.beginTransaction()
                .replace(R.id.mainActContentFrame, MainScreenFragment.class, arguments, MainScreenFragment.FRAGMENT_TAG)
                .setReorderingAllowed(false)
                /*.setCustomAnimations(
                        R.anim.slide_in,  // enter
                        R.anim.fade_out,  // exit
                        R.anim.fade_in,   // popEnter
                        R.anim.slide_out  // popExit
                )*/
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        if (addToBackStack)  ft.addToBackStack(null);
        ft.commit();
    }

    private void requestMapFragment(final boolean allowReturn){
        // starting from Android 11, we don't need to have the STORAGE permission anymore for the map cache

        /*if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            //nothing to do
            Log.d(DEBUG_TAG, "Build codes allow the showing of the map");
            createAndShowMapFragment(null, allowReturn);
            return;
        }
        final String permission = Manifest.permission.WRITE_EXTERNAL_STORAGE;
        int result = askForPermissionIfNeeded(permission, STORAGE_PERMISSION_REQ);
        Log.d(DEBUG_TAG, "Permission for storage: "+result);
        switch (result) {
            case PERMISSION_OK:
                createAndShowMapFragment(null, allowReturn);
                break;
            case PERMISSION_ASKING:
                permissionDoneRunnables.put(permission,
                        () -> createAndShowMapFragment(null, allowReturn));
                break;
            case PERMISSION_NEG_CANNOT_ASK:
                String storage_perm = getString(R.string.storage_permission);
                String text = getString(R.string.too_many_permission_asks,  storage_perm);
                Toast.makeText(getApplicationContext(),text, Toast.LENGTH_LONG).show();
        }

         */
        //The permissions are handled in the MapLibreFragment instead
        createAndShowMapFragment(null, allowReturn);
    }

    private static void checkAndShowFavoritesFragment(FragmentManager fragmentManager,  boolean addToBackStack){
        FragmentTransaction ft = fragmentManager.beginTransaction();
        Fragment fragment = fragmentManager.findFragmentByTag(TAG_FAVORITES);
        if(fragment!=null){
            ft.replace(R.id.mainActContentFrame, fragment, TAG_FAVORITES);
        }else{
            //use new method
            ft.replace(R.id.mainActContentFrame,FavoritesFragment.class,null,TAG_FAVORITES);
        }
        if (addToBackStack)
            ft.addToBackStack("favorites_main");
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .setReorderingAllowed(false);
        ft.commit();
    }

    private static void showLinesFragment(@NonNull FragmentManager fragmentManager,  boolean addToBackStack, @Nullable Bundle fragArgs){
        FragmentTransaction ft = fragmentManager.beginTransaction();
        Fragment f = fragmentManager.findFragmentByTag(LinesGridShowingFragment.FRAGMENT_TAG);
        if(f!=null){
            ft.replace(R.id.mainActContentFrame, f, LinesGridShowingFragment.FRAGMENT_TAG);
        }else{
            //use new method
            ft.replace(R.id.mainActContentFrame,LinesGridShowingFragment.class,fragArgs,
                    LinesGridShowingFragment.FRAGMENT_TAG);
        }
        if (addToBackStack)
            ft.addToBackStack("linesGrid");
        ft.setReorderingAllowed(true)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }

    private void showMainFragment(boolean addToBackStack){
        FragmentManager fraMan = getSupportFragmentManager();
        Fragment fragment = fraMan.findFragmentByTag(MainScreenFragment.FRAGMENT_TAG);
        final MainScreenFragment mainScreenFragment;
        if (fragment==null | !(fragment instanceof MainScreenFragment)){
            createShowMainFragment(fraMan, null, addToBackStack);
        }
        else if(!fragment.isVisible()){


            mainScreenFragment = (MainScreenFragment) fragment;
            showMainFragment(fraMan, mainScreenFragment, addToBackStack);
            Log.d(DEBUG_TAG, "Found the main fragment");
        } else{
            mainScreenFragment = (MainScreenFragment) fragment;
        }
        //return mainScreenFragment;
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
        MainScreenFragment mainFragmentIfVisible = getMainFragmentIfVisible();
        if (mainFragmentIfVisible!=null){
            mainFragmentIfVisible.readyGUIfor(fragmentType);
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
            case LINES:
                titleResId=R.string.lines;
                mNavView.setCheckedItem(R.id.nav_lines_item);
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
            Log.d(DEBUG_TAG, "Requested main fragment, not visible. Search by TAG returned: "+fragment);
            if(fragment!=null){
                //the fragment is there but not shown
                probableFragment = (MainScreenFragment) fragment;
                // set the flag
                probableFragment.setSuppressArrivalsReload(true);
                showMainFragment(fraMan, probableFragment, true);
                probableFragment.requestArrivalsForStopID(ID);
            } else {
                // we have no fragment
                final Bundle args = new Bundle();
                args.putString(MainScreenFragment.PENDING_STOP_SEARCH, ID);
                //if onCreate is complete, then we are not asking for the first showing fragment
                boolean addtobackstack = onCreateComplete;
                createShowMainFragment(fraMan, args ,addtobackstack);
            }
        } else {
            //the MainScreeFragment is shown, nothing to do
            probableFragment.requestArrivalsForStopID(ID);
        }

        mNavView.setCheckedItem(R.id.nav_arrivals);
    }
    @Override
    public void showLineOnMap(String routeGtfsId, @Nullable String stopIDFrom){

        readyGUIfor(FragmentKind.LINES);

        FragmentTransaction tr = getSupportFragmentManager().beginTransaction();
        tr.replace(R.id.mainActContentFrame, LinesDetailFragment.class,
                LinesDetailFragment.Companion.makeArgs(routeGtfsId, stopIDFrom));
        tr.addToBackStack("LineonMap-"+routeGtfsId);
        tr.commit();


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


    @Override
    public void showMapCenteredOnStop(Stop stop) {
        createAndShowMapFragment(stop, true);
    }

    //Map Fragment stuff
    void createAndShowMapFragment(@Nullable Stop stop, boolean addToBackStack){
        final FragmentManager fm = getSupportFragmentManager();
        final FragmentTransaction ft = fm.beginTransaction();
        final MapLibreFragment fragment = MapLibreFragment.newInstance(stop);
        ft.replace(R.id.mainActContentFrame, fragment, MapLibreFragment.FRAGMENT_TAG);
        if (addToBackStack) ft.addToBackStack(null);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }

    void startIntroductionActivity(){
        Intent intent = new Intent(ActivityPrincipal.this, ActivityIntro.class);
        intent.putExtra(ActivityIntro.RESTART_MAIN, false);
        startActivity(intent);
    }

    class ToolbarItemClickListener implements Toolbar.OnMenuItemClickListener{
        private final Context activityContext;

        public ToolbarItemClickListener(Context activityContext) {
            this.activityContext = activityContext;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            final int id = item.getItemId();
            if(id == R.id.action_about){
                startActivity(new Intent(ActivityPrincipal.this, ActivityAbout.class));
                return true;
            } else if (id == R.id.action_hack) {
                openIceweasel(getString(R.string.hack_url), activityContext);
                return true;
            } else if (id == R.id.action_source){
                openIceweasel("https://gitpull.it/source/libre-busto/", activityContext);
                return true;
            } else if (id == R.id.action_licence){
                openIceweasel("https://www.gnu.org/licenses/gpl-3.0.html", activityContext);
                return true;
            } else if (id == R.id.action_experiments) {
                startActivity(new Intent(ActivityPrincipal.this, ActivityExperiments.class));
                return true;
            } else if (id == R.id.action_tutorial) {
                startIntroductionActivity();
                return true;
            }

            return false;
        }
    }

    /**
     * Adjust setting to match the default ones
     */
    private void setDefaultSettingsValuesWhenMissing(){
        SharedPreferences mainSharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = mainSharedPref.edit();
        //Main fragment to show
        String screen = mainSharedPref.getString(SettingsFragment.PREF_KEY_STARTUP_SCREEN, "");
        boolean edit = false;
        if (screen.isEmpty()){
            editor.putString(SettingsFragment.PREF_KEY_STARTUP_SCREEN, "arrivals");
            edit=true;
        }
        //Fetchers
        final Set<String> setSelected = mainSharedPref.getStringSet(SettingsFragment.KEY_ARRIVALS_FETCHERS_USE, new HashSet<>());
        if (setSelected.isEmpty()){
            String[] defaultVals = getResources().getStringArray(R.array.arrivals_sources_values_default);
            editor.putStringSet(SettingsFragment.KEY_ARRIVALS_FETCHERS_USE, utils.convertArrayToSet(defaultVals));
            edit=true;
        }
        //Live bus positions
        final String keySourcePositions=getString(R.string.pref_positions_source);
        final String positionsSource = mainSharedPref.getString(keySourcePositions, "");
        if(positionsSource.isEmpty()){
            String[] defaultVals = getResources().getStringArray(R.array.positions_source_values);
            editor.putString(keySourcePositions, defaultVals[0]);
            edit=true;
        }
        //Map style
        final String mapStylePref = mainSharedPref.getString(SettingsFragment.LIBREMAP_STYLE_PREF_KEY, "");
        if(mapStylePref.isEmpty()){
            final String[] defaultVals = getResources().getStringArray(R.array.map_style_pref_values);
            editor.putString(SettingsFragment.LIBREMAP_STYLE_PREF_KEY, defaultVals[0]);
            edit=true;
        }
        if (edit){
            editor.commit();
        }


    }
}
