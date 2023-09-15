/*
	BusTO  - Fragments components
    Copyright (C) 2018 Fabio Mazza

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
package it.reyboz.bustorino.fragments;


import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import android.widget.*;
import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.core.widget.NestedScrollView;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.adapters.AdapterClickListener;
import it.reyboz.bustorino.adapters.PalinaAdapter;
import it.reyboz.bustorino.adapters.RouteOnlyLineAdapter;
import it.reyboz.bustorino.backend.ArrivalsFetcher;
import it.reyboz.bustorino.backend.DBStatusManager;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.FiveTNormalizer;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Passaggio;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.data.AppDataProvider;
import it.reyboz.bustorino.data.NextGenDB;
import it.reyboz.bustorino.data.UserDB;
import it.reyboz.bustorino.middleware.AsyncStopFavoriteAction;
import it.reyboz.bustorino.util.LinesNameSorter;
import it.reyboz.bustorino.util.ViewUtils;

import static it.reyboz.bustorino.fragments.ScreenBaseFragment.setOption;

public class ArrivalsFragment extends ResultBaseFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String OPTION_SHOW_LEGEND = "show_legend";
    private final static String KEY_STOP_ID = "stopid";
    private final static String KEY_STOP_NAME = "stopname";
    private final static String DEBUG_TAG_ALL = "BUSTOArrivalsFragment";
    private String DEBUG_TAG = DEBUG_TAG_ALL;
    private final static int loaderFavId = 2;
    private final static int loaderStopId = 1;
    static final String STOP_TITLE = "messageExtra";
    private final static String SOURCES_TEXT="sources_textview_message";

    private @Nullable String stopID,stopName;
    private DBStatusManager prefs;
    private DBStatusManager.OnDBUpdateStatusChangeListener listener;
    private boolean justCreated = false;
    private Palina lastUpdatedPalina = null;
    private boolean needUpdateOnAttach = false;
    private boolean fetchersChangeRequestPending = false;
    private boolean stopIsInFavorites = false;

    //Views
    protected ImageButton addToFavorites;
    protected TextView timesSourceTextView;
    protected TextView messageTextView;
    protected RecyclerView arrivalsRecyclerView;
    private PalinaAdapter mListAdapter = null;

    private TextView howDoesItWorkTextView;
    private Button hideHintButton;


    //private NestedScrollView theScrollView;
    protected RecyclerView noArrivalsRecyclerView;
    private RouteOnlyLineAdapter noArrivalsAdapter;
    private TextView noArrivalsTitleView;
    private GridLayoutManager layoutManager;

    //private View canaryEndView;
    private List<ArrivalsFetcher> fetchers = null; //new ArrayList<>(Arrays.asList(utils.getDefaultArrivalsFetchers()));

    private boolean reloadOnResume = true;

    private final AdapterClickListener<Route> mRouteClickListener = route -> {
            String routeName;

            routeName = FiveTNormalizer.routeInternalToDisplay(route.getNameForDisplay());
            if (routeName == null) {
                routeName = route.getNameForDisplay();
            }
            if(getContext()==null)
                Log.e(DEBUG_TAG, "Touched on a route but Context is null");
            else if (route.destinazione == null || route.destinazione.length() == 0) {
                Toast.makeText(getContext(),
                        getString(R.string.route_towards_unknown, routeName), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(),
                        getString(R.string.route_towards_destination, routeName, route.destinazione), Toast.LENGTH_SHORT).show();
            }

    };

    public static ArrivalsFragment newInstance(String stopID){
        return newInstance(stopID, null);
    }

    public static ArrivalsFragment newInstance(@NonNull String stopID, @Nullable String stopName){
        ArrivalsFragment fragment = new ArrivalsFragment();
        Bundle args = new Bundle();
        args.putString(KEY_STOP_ID,stopID);
        //parameter for ResultListFragmentrequestArrivalsForStopID
        //args.putSerializable(LIST_TYPE,FragmentKind.ARRIVALS);
        if (stopName != null){
            args.putString(KEY_STOP_NAME,stopName);
        }
        fragment.setArguments(args);
        return fragment;
    }

    public static String getFragmentTag(Palina p) {
        return "palina_"+p.ID;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        stopID = getArguments().getString(KEY_STOP_ID);
        DEBUG_TAG = DEBUG_TAG_ALL+" "+stopID;

        //this might really be null
        stopName = getArguments().getString(KEY_STOP_NAME);
        final ArrivalsFragment arrivalsFragment = this;
        listener = new DBStatusManager.OnDBUpdateStatusChangeListener() {
            @Override
            public void onDBStatusChanged(boolean updating) {
                if(!updating){
                    getLoaderManager().restartLoader(loaderFavId,getArguments(),arrivalsFragment);
                } else {
                    final LoaderManager lm = getLoaderManager();
                    lm.destroyLoader(loaderFavId);
                    lm.destroyLoader(loaderStopId);
                }
            }

            @Override
            public boolean defaultStatusValue() {
                return true;
            }
        };
        prefs = new DBStatusManager(getContext().getApplicationContext(),listener);
        justCreated = true;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_arrivals, container, false);
        messageTextView = root.findViewById(R.id.messageTextView);
        addToFavorites = root.findViewById(R.id.addToFavorites);
        // "How does it work part"
        howDoesItWorkTextView = root.findViewById(R.id.howDoesItWorkTextView);
        hideHintButton = root.findViewById(R.id.hideHintButton);
        hideHintButton.setOnClickListener(this::onHideHint);

        //theScrollView = root.findViewById(R.id.arrivalsScrollView);
        // recyclerview holding the arrival times
        arrivalsRecyclerView = root.findViewById(R.id.arrivalsRecyclerView);
        final LinearLayoutManager manager = new LinearLayoutManager(getContext());
        arrivalsRecyclerView.setLayoutManager(manager);
        final DividerItemDecoration mDividerItemDecoration = new DividerItemDecoration(arrivalsRecyclerView.getContext(),
                manager.getOrientation());
        arrivalsRecyclerView.addItemDecoration(mDividerItemDecoration);
        timesSourceTextView = root.findViewById(R.id.timesSourceTextView);
        timesSourceTextView.setOnLongClickListener(view -> {
            if(!fetchersChangeRequestPending){
                rotateFetchers();
                //Show we are changing provider
                timesSourceTextView.setText(R.string.arrival_source_changing);

                mListener.requestArrivalsForStopID(stopID);
                fetchersChangeRequestPending = true;
                return true;
            }
            return false;
        });
        timesSourceTextView.setOnClickListener(view -> {
            Toast.makeText(getContext(), R.string.change_arrivals_source_message, Toast.LENGTH_SHORT)
                    .show();
        });
        //Button
        addToFavorites.setClickable(true);
        addToFavorites.setOnClickListener(v -> {
            // add/remove the stop in the favorites
            toggleLastStopToFavorites();
        });

        String displayName = getArguments().getString(STOP_TITLE);
        if(displayName!=null)
            setTextViewMessage(String.format(
                getString(R.string.passages), displayName));


        String probablemessage = getArguments().getString(MESSAGE_TEXT_VIEW);
        if (probablemessage != null) {
            //Log.d("BusTO fragment " + this.getTag(), "We have a possible message here in the savedInstaceState: " + probablemessage);
            messageTextView.setText(probablemessage);
            messageTextView.setVisibility(View.VISIBLE);
        }
        //no arrivals stuff
        noArrivalsRecyclerView = root.findViewById(R.id.noArrivalsRecyclerView);
        layoutManager = new GridLayoutManager(getContext(),60);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return 12;
            }
        });
        noArrivalsRecyclerView.setLayoutManager(layoutManager);
        noArrivalsTitleView = root.findViewById(R.id.noArrivalsMessageTextView);

        //canaryEndView = root.findViewById(R.id.canaryEndView);

        /*String sourcesTextViewData = getArguments().getString(SOURCES_TEXT);
        if (sourcesTextViewData!=null){
            timesSourceTextView.setText(sourcesTextViewData);
        }*/
        //need to do this when we recreate the fragment but we haven't updated the arrival times
        if (lastUpdatedPalina!=null)
            showArrivalsSources(lastUpdatedPalina);
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        LoaderManager loaderManager  = getLoaderManager();
        Log.d(DEBUG_TAG, "OnResume, justCreated "+justCreated+", lastUpdatedPalina is: "+lastUpdatedPalina);
        /*if(needUpdateOnAttach){
            updateFragmentData(null);
            needUpdateOnAttach=false;
        }*/
        /*if(lastUpdatedPalina!=null){
            updateFragmentData(null);
            showArrivalsSources(lastUpdatedPalina);
        }*/
        mListener.readyGUIfor(FragmentKind.ARRIVALS);

        if (mListAdapter!=null)
            resetListAdapter(mListAdapter);
        if(noArrivalsAdapter!=null){
            noArrivalsRecyclerView.setAdapter(noArrivalsAdapter);

        }

        if(stopID!=null){
            if(!justCreated){
                fetchers = utils.getDefaultArrivalsFetchers(getContext());
                adjustFetchersToSource();

                if (reloadOnResume)
                    mListener.requestArrivalsForStopID(stopID);
            }
            else justCreated = false;
            //start the loader
            if(prefs.isDBUpdating(true)){
                prefs.registerListener();
            } else {
                Log.d(DEBUG_TAG, "Restarting loader for stop");
                loaderManager.restartLoader(loaderFavId, getArguments(), this);
            }
            updateMessage();
        }

        if (ScreenBaseFragment.getOption(requireContext(),OPTION_SHOW_LEGEND, true)) {
            showHints();
        }


    }


    @Override
    public void onStart() {
        super.onStart();
        if (needUpdateOnAttach){
            updateFragmentData(null);
            needUpdateOnAttach = false;
        }
    }

    @Override
    public void onPause() {
        if(listener!=null)
            prefs.unregisterListener();
        super.onPause();
        LoaderManager loaderManager  = getLoaderManager();
        Log.d(DEBUG_TAG, "onPause, have running loaders: "+loaderManager.hasRunningLoaders());
        loaderManager.destroyLoader(loaderFavId);

    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        //get fetchers
        fetchers = utils.getDefaultArrivalsFetchers(context);
    }

    @Nullable
    public String getStopID() {
        return stopID;
    }

    public boolean reloadsOnResume() {
        return reloadOnResume;
    }

    public void setReloadOnResume(boolean reloadOnResume) {
        this.reloadOnResume = reloadOnResume;
    }

    // HINT "HOW TO USE"
    private void showHints() {
        howDoesItWorkTextView.setVisibility(View.VISIBLE);
        hideHintButton.setVisibility(View.VISIBLE);
        //actionHelpMenuItem.setVisible(false);
    }

    private void hideHints() {
        howDoesItWorkTextView.setVisibility(View.GONE);
        hideHintButton.setVisibility(View.GONE);
        //actionHelpMenuItem.setVisible(true);
    }

    public void onHideHint(View v) {
        hideHints();
        setOption(requireContext(),OPTION_SHOW_LEGEND, false);
    }
    /**
     * Give the fetchers
     * @return the list of the fetchers
     */
    public ArrayList<Fetcher> getCurrentFetchers(){
        return new ArrayList<>(this.fetchers);
    }
    public ArrivalsFetcher[] getCurrentFetchersAsArray(){
        ArrivalsFetcher[] arr = new ArrivalsFetcher[fetchers.size()];
        fetchers.toArray(arr);
        return arr;
    }

    private void rotateFetchers(){
        Log.d(DEBUG_TAG, "Rotating fetchers, before: "+fetchers);
        Collections.rotate(fetchers, -1);
        Log.d(DEBUG_TAG, "Rotating fetchers, afterwards: "+fetchers);

    }


    /**
     * Update the UI with the new data
     * @param p the full Palina
     */
    public void updateFragmentData(@Nullable Palina p){
        if (p!=null)
            lastUpdatedPalina = p;

        if (!isAdded()){
            //defer update at next show
            if (p==null)
                Log.w(DEBUG_TAG, "Asked to update the data, but we're not attached and the data is null");
            else needUpdateOnAttach = true;
        } else {

            final PalinaAdapter adapter = new PalinaAdapter(getContext(), lastUpdatedPalina, mRouteClickListener, true);
            showArrivalsSources(lastUpdatedPalina);
            resetListAdapter(adapter);

            final ArrayList<String> routesWithNoPassages = lastUpdatedPalina.getRoutesNamesWithNoPassages();
            Collections.sort(routesWithNoPassages, new LinesNameSorter());
            noArrivalsAdapter = new RouteOnlyLineAdapter(routesWithNoPassages);
            if(noArrivalsRecyclerView!=null){
                noArrivalsRecyclerView.setAdapter(noArrivalsAdapter);
                //hide the views if there are no empty routes
                if(routesWithNoPassages.isEmpty()){
                    noArrivalsRecyclerView.setVisibility(View.GONE);
                    noArrivalsTitleView.setVisibility(View.GONE);
                } else {
                    noArrivalsRecyclerView.setVisibility(View.VISIBLE);
                    noArrivalsTitleView.setVisibility(View.VISIBLE);
                }
            }

            //canaryEndView.setVisibility(View.VISIBLE);
            //check if canaryEndView is visible
            //boolean isCanaryVisibile = ViewUtils.Companion.isViewPartiallyVisibleInScroll(canaryEndView, theScrollView);
            //Log.d(DEBUG_TAG, "Canary view fully visibile: "+isCanaryVisibile);

        }
    }



    /**
     * Set the message of the arrival times source
     * @param p Palina with the arrival times
     */
    protected void showArrivalsSources(Palina p){
        final Passaggio.Source source = p.getPassaggiSourceIfAny();
        if (source == null){
            Log.e(DEBUG_TAG, "NULL SOURCE");
            return;
        }
        String source_txt;
        switch (source){
            case GTTJSON:
                source_txt = getString(R.string.gttjsonfetcher);
                break;
            case FiveTAPI:
                source_txt = getString(R.string.fivetapifetcher);
                break;
            case FiveTScraper:
                source_txt = getString(R.string.fivetscraper);
                break;
            case MatoAPI:
                source_txt = getString(R.string.source_mato);
                break;
            case UNDETERMINED:
                //Don't show the view
                source_txt = getString(R.string.undetermined_source);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + source);
        }
        //
        final boolean updatedFetchers = adjustFetchersToSource(source);
        if(!updatedFetchers)
            Log.w(DEBUG_TAG, "Tried to update the source fetcher but it didn't work");
        final String base_message = getString(R.string.times_source_fmt, source_txt);
        timesSourceTextView.setText(base_message);
        timesSourceTextView.setVisibility(View.VISIBLE);

        if (p.getTotalNumberOfPassages() > 0) {
            timesSourceTextView.setVisibility(View.VISIBLE);
        } else {
            timesSourceTextView.setVisibility(View.INVISIBLE);
        }
        fetchersChangeRequestPending = false;
    }

    protected boolean adjustFetchersToSource(Passaggio.Source source){
        if (source == null) return false;
        int count = 0;
        if (source!= Passaggio.Source.UNDETERMINED)
            while (source != fetchers.get(0).getSourceForFetcher() && count < 200){
                //we need to update the fetcher that is requested
                rotateFetchers();
                count++;
            }
        return count < 200;

    }
    protected boolean adjustFetchersToSource(){
        if (lastUpdatedPalina == null) return false;
        final Passaggio.Source source = lastUpdatedPalina.getPassaggiSourceIfAny();
        return adjustFetchersToSource(source);
    }

    /**
     * Update the message in the fragment
     *
     * It may eventually change the "Add to Favorite" icon
     */
    private void updateMessage(){
        String message = null;
        if (stopName != null && stopID != null && stopName.length() > 0) {
            message = (stopID.concat(" - ").concat(stopName));
        } else if(stopID!=null) {
            message = stopID;
        } else {
            Log.e("ArrivalsFragm"+getTag(),"NO ID FOR THIS FRAGMENT - something went horribly wrong");
        }
        if(message!=null) {
            setTextViewMessage(getString(R.string.passages,message));
        }

        // whatever is the case, update the star icon
        //updateStarIconFromLastBusStop();

    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if(args.getString(KEY_STOP_ID)==null) return null;
        final String stopID = args.getString(KEY_STOP_ID);
        final Uri.Builder builder = AppDataProvider.getUriBuilderToComplete();
        CursorLoader cl;
        switch (id){
            case loaderFavId:
                builder.appendPath("favorites").appendPath(stopID);
                cl = new CursorLoader(getContext(),builder.build(),UserDB.getFavoritesColumnNamesAsArray,null,null,null);

                break;
            case loaderStopId:
                builder.appendPath("stop").appendPath(stopID);
                cl = new CursorLoader(getContext(),builder.build(),new String[]{NextGenDB.Contract.StopsTable.COL_NAME},
                        null,null,null);
                break;
            default:
                return null;
        }
        cl.setUpdateThrottle(500);
        return cl;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        switch (loader.getId()){
            case loaderFavId:
                final int colUserName = data.getColumnIndex(UserDB.getFavoritesColumnNamesAsArray[1]);
                if(data.getCount()>0){
                    // IT'S IN FAVORITES
                    data.moveToFirst();
                    final String probableName = data.getString(colUserName);
                    stopIsInFavorites = true;
                    stopName = probableName;
                    //update the message in the textview
                    updateMessage();

                } else {
                    stopIsInFavorites =false;
                }
                updateStarIcon();

                if(stopName == null){
                    //stop is not inside the favorites and wasn't provided
                    Log.d("ArrivalsFragment"+getTag(),"Stop wasn't in the favorites and has no name, looking in the DB");
                    getLoaderManager().restartLoader(loaderStopId,getArguments(),this);
                }
                break;
            case loaderStopId:
                if(data.getCount()>0){
                    data.moveToFirst();
                    int index = data.getColumnIndex(
                            NextGenDB.Contract.StopsTable.COL_NAME
                    );
                    if (index == -1){
                        Log.e(DEBUG_TAG, "Index is -1, column not present. App may explode now...");
                    }
                    stopName = data.getString(index);
                    updateMessage();
                } else {
                    Log.w("ArrivalsFragment"+getTag(),"Stop is not inside the database... CLOISTER BELL");
                }
        }

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        //NOTHING TO DO
    }
    protected void resetListAdapter(PalinaAdapter adapter) {
        mListAdapter = adapter;
        if (arrivalsRecyclerView != null) {
            arrivalsRecyclerView.setAdapter(adapter);
            arrivalsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Set the message textView
     * @param message the whole message to write in the textView
     */
    public void setTextViewMessage(String message) {
        messageTextView.setText(message);
        messageTextView.setVisibility(View.VISIBLE);
    }

    public void toggleLastStopToFavorites() {

        Stop stop = lastUpdatedPalina;
        if (stop != null) {

            // toggle the status in background
            new AsyncStopFavoriteAction(getContext().getApplicationContext(), AsyncStopFavoriteAction.Action.TOGGLE,
                    v->updateStarIconFromLastBusStop(v)).execute(stop);
        } else {
            // this case have no sense, but just immediately update the favorite icon
            updateStarIconFromLastBusStop(true);
        }
    }
    /**
     * Update the star "Add to favorite" icon
     */
    public void updateStarIconFromLastBusStop(Boolean toggleDone) {
        if (stopIsInFavorites)
            stopIsInFavorites = !toggleDone;
        else stopIsInFavorites = toggleDone;

        updateStarIcon();

        // check if there is a last Stop
        /*
        if (stopID == null) {
            addToFavorites.setVisibility(View.INVISIBLE);
        } else {
            // filled or outline?
            if (isStopInFavorites(stopID)) {
                addToFavorites.setImageResource(R.drawable.ic_star_filled);
            } else {
                addToFavorites.setImageResource(R.drawable.ic_star_outline);
            }

            addToFavorites.setVisibility(View.VISIBLE);
        }
         */
    }

    /**
     * Update the star icon according to `stopIsInFavorites`
     */
    public void updateStarIcon() {

        // no favorites no party!

        // check if there is a last Stop

        if (stopID == null) {
            addToFavorites.setVisibility(View.INVISIBLE);
        } else {
            // filled or outline?
            if (stopIsInFavorites) {
                addToFavorites.setImageResource(R.drawable.ic_star_filled);
            } else {
                addToFavorites.setImageResource(R.drawable.ic_star_outline);
            }

            addToFavorites.setVisibility(View.VISIBLE);
        }


    }

    @Override
    public void onDestroyView() {
        arrivalsRecyclerView = null;
        if(getArguments()!=null) {
            getArguments().putString(SOURCES_TEXT, timesSourceTextView.getText().toString());
            getArguments().putString(MESSAGE_TEXT_VIEW, messageTextView.getText().toString());
        }
        super.onDestroyView();
    }

    public boolean isFragmentForTheSameStop(Palina p) {
        if (getTag() != null)
            return getTag().equals(getFragmentTag(p));
        else return false;
    }
}
