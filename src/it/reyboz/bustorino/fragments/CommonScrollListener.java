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

import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.widget.AbsListView;

import java.lang.ref.WeakReference;

public class CommonScrollListener extends RecyclerView.OnScrollListener implements AbsListView.OnScrollListener{

    WeakReference<FragmentListener> listenerWeakReference;
    //enable swipeRefreshLayout when scrolling down or not
    boolean enableRefreshLayout;
    int lastvisibleitem;

    public CommonScrollListener(FragmentListener lis,boolean enableRefreshLayout){
        listenerWeakReference = new WeakReference<>(lis);
        this.enableRefreshLayout = enableRefreshLayout;
    }
    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        /*
         * This seems to be a totally useless method
         */
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        FragmentListener listener = listenerWeakReference.get();
        if(listener==null){
            //can't do anything, sorry
            Log.i(this.getClass().getName(),"called onScroll but FragmentListener is null");
            return;
        }
        if (firstVisibleItem!=0) {
            if (lastvisibleitem < firstVisibleItem) {
                Log.i("Busto", "Scrolling DOWN");
                listener.showFloatingActionButton(false);
                //lastScrollUp = true;
            } else if (lastvisibleitem > firstVisibleItem) {
                Log.i("Busto", "Scrolling UP");
                listener.showFloatingActionButton(true);
                //lastScrollUp =  false;
            }
            lastvisibleitem = firstVisibleItem;
        }
        if(enableRefreshLayout){
            boolean enable = false;
            if(view != null && view.getChildCount() > 0){
                // check if the first item of the list is visible
                boolean firstItemVisible = view.getFirstVisiblePosition() == 0;
                // check if the top of the first item is visible
                boolean topOfFirstItemVisible = view.getChildAt(0).getTop() == 0;
                // enabling or disabling the refresh layout
                enable = firstItemVisible && topOfFirstItemVisible;
            }
            listener.enableRefreshLayout(enable);
            //Log.d(getString(R.string.list_fragment_debug),"onScroll active, first item visible: "+firstVisibleItem+", refreshlayout enabled: "+enable);
        }}
}
