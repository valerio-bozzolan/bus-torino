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
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.widget.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import it.reyboz.bustorino.R;
import it.reyboz.bustorino.backend.FiveTNormalizer;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import it.reyboz.bustorino.data.UserDB;

/**
 *  This is a generalized fragment that can be used both for
 *
 *
 */
public class ResultListFragment extends Fragment{
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    static final String LIST_TYPE = "list-type";
    protected static final String LIST_STATE = "list_state";


    protected static final String MESSAGE_TEXT_VIEW = "message_text_view";
    private FragmentKind adapterKind;

    protected FragmentListenerMain mListener;
    protected TextView messageTextView;
    protected ListView resultsListView;


    private ListAdapter mListAdapter = null;
    boolean listShown;
    private Parcelable mListInstanceState = null;

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
    public static ResultListFragment newInstance(FragmentKind listType, String eventualStopTitle) {
        ResultListFragment fragment = new ResultListFragment();
        Bundle args = new Bundle();
        args.putSerializable(LIST_TYPE, listType);
        if (eventualStopTitle != null) {
            args.putString(ArrivalsFragment.STOP_TITLE, eventualStopTitle);
        }
        fragment.setArguments(args);
        return fragment;
    }

    public static ResultListFragment newInstance(FragmentKind listType) {
        return newInstance(listType, null);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            adapterKind = (FragmentKind) getArguments().getSerializable(LIST_TYPE);
        }
    }

    /**
     * Check if the last Bus Stop is in the favorites
     * @return true if it iss
     */
    public boolean isStopInFavorites(String busStopId) {
        boolean found = false;

        // no stop no party
        if(busStopId != null) {
            SQLiteDatabase userDB = new UserDB(getContext()).getReadableDatabase();
            found = UserDB.isStopInFavorites(userDB, busStopId);
        }

        return found;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_list_view, container, false);
        messageTextView = (TextView) root.findViewById(R.id.messageTextView);
        if (adapterKind != null) {
            resultsListView = (ListView) root.findViewById(R.id.resultsListView);
            switch (adapterKind) {
                case STOPS:
                    resultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            /*
                             * Casting because of Javamerda
                             * @url http://stackoverflow.com/questions/30549485/androids-list-view-parameterized-type-in-adapterview-onitemclicklistener
                             */
                            Stop busStop = (Stop) parent.getItemAtPosition(position);
                            mListener.requestArrivalsForStopID(busStop.ID);
                        }
                    });

                    // set the textviewMessage
                    setTextViewMessage(getString(R.string.results));
                    break;
                case ARRIVALS:
                    resultsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            String routeName;

                            Route r = (Route) parent.getItemAtPosition(position);
                            routeName = FiveTNormalizer.routeInternalToDisplay(r.getNameForDisplay());
                            if (routeName == null) {
                                routeName = r.getNameForDisplay();
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
                    String displayName = getArguments().getString(ArrivalsFragment.STOP_TITLE);
                    setTextViewMessage(String.format(
                            getString(R.string.passages), displayName));
                    break;
                default:
                    throw new IllegalStateException("Argument passed was not of a supported type");
            }

            String probablemessage = getArguments().getString(MESSAGE_TEXT_VIEW);
            if (probablemessage != null) {
                //Log.d("BusTO fragment " + this.getTag(), "We have a possible message here in the savedInstaceState: " + probablemessage);
                messageTextView.setText(probablemessage);
                messageTextView.setVisibility(View.VISIBLE);
            }

        } else
            Log.d(getString(R.string.list_fragment_debug), "No content root for fragment");
        return root;
    }

    public boolean isFragmentForTheSameStop(Palina p) {
        return adapterKind.equals(FragmentKind.ARRIVALS) && getTag().equals(getFragmentTag(p));
    }

    public static String getFragmentTag(Palina p) {
        return "palina_"+p.ID;
    }


    @Override
    public void onResume() {
        super.onResume();
        //Log.d(getString(R.string.list_fragment_debug),"Fragment restored, saved listAdapter is "+(mListAdapter));
        if (mListAdapter != null) {

            ListAdapter adapter = mListAdapter;
            mListAdapter = null;
            resetListAdapter(adapter);
        }
        if (mListInstanceState != null) {
            Log.d("resultsListView", "trying to restore instance state");
            resultsListView.onRestoreInstanceState(mListInstanceState);
        }
        switch (adapterKind) {
            case ARRIVALS:
                resultsListView.setOnScrollListener(new CommonScrollListener(mListener, true));
                mListener.showFloatingActionButton(true);
                break;
            case STOPS:
                resultsListView.setOnScrollListener(new CommonScrollListener(mListener, false));
                break;
            default:
                //NONE
        }
        mListener.readyGUIfor(adapterKind);

    }


    @Override
    public void onPause() {
        if (adapterKind.equals(FragmentKind.ARRIVALS)) {
            SwipeRefreshLayout reflay = getActivity().findViewById(R.id.listRefreshLayout);
            reflay.setEnabled(false);
            Log.d("BusTO Fragment " + this.getTag(), "RefreshLayout disabled");
        }
        super.onPause();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof FragmentListenerMain) {
            mListener = (FragmentListenerMain) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement ResultFragmentListener");
        }

    }


    @Override
    public void onDetach() {
        mListener.showFloatingActionButton(false);
        mListener = null;
        super.onDetach();
    }


    @Override
    public void onDestroyView() {
        resultsListView = null;
        //Log.d(getString(R.string.list_fragment_debug), "called onDestroyView");
        getArguments().putString(MESSAGE_TEXT_VIEW, messageTextView.getText().toString());
        super.onDestroyView();
    }


    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        Log.d("ResultListFragment", "onViewStateRestored");
        if (savedInstanceState != null) {
            mListInstanceState = savedInstanceState.getParcelable(LIST_STATE);
            Log.d("ResultListFragment", "listInstanceStatePresent :" + mListInstanceState);
        }
    }

    protected void resetListAdapter(ListAdapter adapter) {
        boolean hadAdapter = mListAdapter != null;
        mListAdapter = adapter;
        if (resultsListView != null) {
            resultsListView.setAdapter(adapter);
            resultsListView.setVisibility(View.VISIBLE);
        }
    }
    public void setNewListAdapter(ListAdapter adapter){
        resetListAdapter(adapter);
    }

    /**
     * Set the message textView
     * @param message the whole message to write in the textView
     */
    public void setTextViewMessage(String message) {
        messageTextView.setText(message);
        messageTextView.setVisibility(View.VISIBLE);
    }
}