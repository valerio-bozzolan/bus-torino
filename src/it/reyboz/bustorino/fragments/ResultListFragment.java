/*
	BusTO  - Fragments components
    Copyright (C) 2016 Fabio Mazza

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

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.*;
import android.support.design.widget.FloatingActionButton;
import it.reyboz.bustorino.ActivityMain;
import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.FiveTNormalizer;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.middleware.AsyncAddToFavorites;

/**
 *  This is a generalized fragment that can be used both for
 *
 *
 */
public class ResultListFragment extends Fragment {
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String LIST_TYPE = "list-type";
    public static final String TYPE_LINES ="lines";
    public static final String TYPE_STOPS = "fermate";

    private static final String MESSAGE_TEXT_VIEW ="message_text_view";
    private String adapterType;


    private ResultFragmentListener mListener;
    private TextView messageTextView;

    FloatingActionButton fabutton;
    private ListView resultsListView;
    ListAdapter mListAdapter = null;
    boolean listShown;

    public ResultListFragment() {
        // Required empty public constructor
    }
    public ListView getResultsListView() {
        return resultsListView;
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param listType whether the list is used for STOPS or LINES (Orari)
     * @return A new instance of fragment ResultListFragment.
     */
    public static ResultListFragment newInstance(String listType) {
        ResultListFragment fragment = new ResultListFragment();
        Bundle args = new Bundle();
        args.putString(LIST_TYPE, listType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            adapterType = getArguments().getString(LIST_TYPE);
        }
    }



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        hideMyFAB mInterface = new hideMyFAB() {
            @Override
            public void showFloatingActionButton(boolean show) {
                if (fabutton!=null){
                    if(show) fabutton.show();
                    else fabutton.hide();
                }
            }
        };
        View root = inflater.inflate(R.layout.fragment_list_view, container, false);
        messageTextView = (TextView) root.findViewById(R.id.messageTextView);
        if(adapterType!=null) {
            resultsListView = (ListView) root.findViewById(R.id.resultsListView);
            if(adapterType.equals(TYPE_STOPS)) {
                resultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    /*
                     * Casting because of Javamerda
                     * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
                     */
                        Stop busStop = (Stop) parent.getItemAtPosition(position);
                        mListener.createFragmentForStop(busStop.ID);
                    }
                });
                resultsListView.setOnScrollListener(new ListFragmentScrollListener(false,mInterface));
            }
            else if(adapterType.equals(TYPE_LINES)) {
                resultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                        String routeName;

                        Route r = (Route) parent.getItemAtPosition(position);
                        routeName = FiveTNormalizer.routeInternalToDisplay(r.name);
                        if (routeName == null) {
                            routeName = r.name;
                        }
                        if (r.destinazione == null || r.destinazione.length() == 0) {
                            Toast.makeText(getContext(),
                                    getString(R.string.route_towards_unknown, routeName), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(),
                                    getString(R.string.route_towards_destination, routeName, r.destinazione), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                resultsListView.setOnScrollListener(new ListFragmentScrollListener(true,mInterface));
            }
            String  probablemessage = getArguments().getString(MESSAGE_TEXT_VIEW);
            if(probablemessage!=null) {
                //Log.d("BusTO fragment " + this.getTag(), "We have a possible message here in the savedInstaceState: " + probablemessage);
                messageTextView.setText(probablemessage);
                messageTextView.setVisibility(View.VISIBLE);
            }

        } else
            Log.d(getString(R.string.list_fragment_debug), "No content root for fragment");
        return root;
    }


    @Override
    public void onResume() {
        super.onResume();
        //Log.d(getString(R.string.list_fragment_debug),"Fragment restored, saved listAdapter is "+(mListAdapter));
        if(mListAdapter!=null){

            ListAdapter adapter = mListAdapter;
            mListAdapter = null;
            setListAdapter(adapter);
        }
    }

    @Override
    public void onPause() {
        if(adapterType.equals(TYPE_LINES)) {
            SwipeRefreshLayout reflay = (SwipeRefreshLayout) getActivity().findViewById(R.id.listRefreshLayout);
            reflay.setEnabled(false);
            Log.d("BusTO Fragment " + this.getTag(), "RefreshLayout disabled");
        }
        super.onPause();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof ResultFragmentListener) {
            mListener = (ResultFragmentListener) context;
            fabutton = (FloatingActionButton) getActivity().findViewById(R.id.floatingActionButton);
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement ResultFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        mListener = null;
        if(fabutton!=null)
            fabutton.show();
        super.onDetach();
    }


    @Override
    public void onDestroyView() {
        resultsListView = null;
        //Log.d(getString(R.string.list_fragment_debug), "called onDestroyView");
        getArguments().putString(MESSAGE_TEXT_VIEW, messageTextView.getText().toString());
        super.onDestroyView();
    }

    public void setListAdapter(ListAdapter adapter) {
        boolean hadAdapter = mListAdapter != null;
        mListAdapter = adapter;
        if (resultsListView != null) {
            resultsListView.setAdapter(adapter);
            resultsListView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * This method attaches the FloatingActionButton to the current ListView
     */
    /* TODO REWORKING
    public void attachFABToListView(){
        if(resultsListView!=null)
            switch(adapterType){
                case TYPE_LINES:
                    fabutton.attachToListView(resultsListView, null, new ListFragmentScrollListener());
                    break;
                default:
                    fabutton.attachToListView(resultsListView);
                    break;
            }
    }
    */
    public void setTextViewMessage(String message){
        messageTextView.setText(message);
        switch (adapterType){
            case TYPE_LINES:
                final ActivityMain activ = (ActivityMain) getActivity();
                messageTextView.setClickable(true);
                messageTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new AsyncAddToFavorites(getContext()).execute(activ.getLastSuccessfullySearchedBusStop());
                    }
                });
                break;
            case TYPE_STOPS:
                messageTextView.setClickable(false);
                break;
        }

        messageTextView.setVisibility(View.VISIBLE);
    }

    /**
     * This interface is useful for communicating with the activity
     * The name has been automatically generated (do not blame me)
     */
    public interface ResultFragmentListener {
        /**
         * Houston, we need another fragment!
         * @param ID the Stop ID
         */
        void createFragmentForStop(String ID);
    }

    /**
     * Classe per gestire gli scroll
     *
     */
    class ListFragmentScrollListener implements AbsListView.OnScrollListener{
        boolean enableRefreshLayout,lastScrollUp=false;
        int mLastFirstVisibleItem;
        hideMyFAB fabInterface;
        public ListFragmentScrollListener(boolean enableRefreshLayout, hideMyFAB iface){
            this.enableRefreshLayout = enableRefreshLayout;
            this.fabInterface = iface;
        }
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            /*
             * This seems to be a totally useless method
             */
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (firstVisibleItem!=0) {
                if (mLastFirstVisibleItem < firstVisibleItem) {
                    Log.i("Busto", "Scrolling DOWN");
                    fabInterface.showFloatingActionButton(false);
                    //lastScrollUp = true;
                } else if (mLastFirstVisibleItem > firstVisibleItem) {
                    Log.i("Busto", "Scrolling UP");
                    fabInterface.showFloatingActionButton(true);
                    //lastScrollUp =  false;
                }
                mLastFirstVisibleItem = firstVisibleItem;
            }
            if(enableRefreshLayout){
            SwipeRefreshLayout refreshLayout = (SwipeRefreshLayout) getActivity().findViewById(R.id.listRefreshLayout);
            boolean enable = false;
            if(view != null && view.getChildCount() > 0){
                // check if the first item of the list is visible
                boolean firstItemVisible = view.getFirstVisiblePosition() == 0;
                // check if the top of the first item is visible
                boolean topOfFirstItemVisible = view.getChildAt(0).getTop() == 0;
                // enabling or disabling the refresh layout
                enable = firstItemVisible && topOfFirstItemVisible;
            }
            refreshLayout.setEnabled(enable);
            Log.d(getString(R.string.list_fragment_debug),"onScroll active, first item visible: "+firstVisibleItem+", refreshlayout enabled: "+enable);
        }}
    }

}
interface hideMyFAB {
    void showFloatingActionButton(boolean show);
}
