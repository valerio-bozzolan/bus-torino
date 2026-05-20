/*
	BusTO  - UI components
    Copyright (C) 2026 Fabio Mazza

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
package it.reyboz.bustorino.adapters;

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import it.reyboz.bustorino.BuildConfig;
import it.reyboz.bustorino.backend.utils;


// based on the answer at https://stackoverflow.com/questions/37507937/margin-between-items-in-recycler-view-android

/**
 * Recycler view margin setter for the elements. If you call "addExternal", it will use the same margins on bordering elements
 * towards the border (i.e., applying the margin on top for the first row, on right for the last columns, etc.)
 */
public class RecyclerViewMargin extends RecyclerView.ItemDecoration {

    private final int margin;
    private final int columns;

    private boolean addExternal = false;
    private static final String DEBUG_TAG = "BusTO-RecViewMargin";
    /**
     * constructor
     * @param marginPx desirable margin size in px between the views in the recyclerView
     * @param numColumns number of numColumns of the RecyclerView
     */
    public RecyclerViewMargin(@IntRange(from=0)int marginPx , @IntRange(from=0) int numColumns ) {
        this.margin = marginPx;
        this.columns=numColumns;

    }
    public static RecyclerViewMargin makeMarginsDip(@NonNull Context context,
                                                    @IntRange(from=0)int marginDip ,
                                                    @IntRange(from=0) int numColumns) {
        return new RecyclerViewMargin(utils.convertDipToPixelInt(context, marginDip), numColumns);
    }

    public RecyclerViewMargin addExternal(){
        addExternal = true;
        return this;
    }

    /**
     * Set different margins for the items inside the recyclerView: no top margin for the first row
     * and no left margin for the first column.
     */
    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        var adapter = parent.getAdapter();
        int nrows = adapter!=null ? (int)Math.ceil( (double) adapter.getItemCount() / columns) : -2;
        int position = parent.getChildLayoutPosition(view);
        if(BuildConfig.DEBUG)
            Log.d(DEBUG_TAG, "getItemOffsets: position = " + position);
        var sb = new StringBuilder();
        //set right margin to all
        if(position % columns != columns-1){
            outRect.right = margin;
            sb.append("right ");
        }
        int row =  (int)((double) position / columns) ;
        if(nrows == -2 || row < nrows-1){
            outRect.bottom = margin;
            sb.append("bottom ");
        }
        /*
        //set right margin to all
                    outRect.right = margin;

        //set bottom margin to all
        outRect.bottom = margin;
        //we only add top margin to the first row


         */
        if(addExternal){
            if (position <columns) {
                outRect.top = margin;
                sb.append("top ");
            }
            //add left margin only to the first column
            if(position%columns==0){
                outRect.left = margin;
                sb.append("left ");
            }
            if(position%columns==columns-1){
                outRect.right = margin;
                sb.append("right ");
            }
        }
        if(BuildConfig.DEBUG)
            Log.d(DEBUG_TAG, "margins put: " + sb.toString());

    }
}

/* leftover code from my poor trial

var adapter = parent.getAdapter();
        int nrows = adapter!=null ? (int)Math.ceil( (double) adapter.getItemCount() / columns) : -2;
        int position = parent.getChildLayoutPosition(view);
        Log.d(DEBUG_TAG, "getItemOffsets: position = " + position);
        var sb = new StringBuilder();
        //set right margin to all
        if(position % columns != columns-1){
            outRect.right = margin;
            sb.append("right ");
        }
        if(position % columns != 0){
            outRect.left = margin;
            sb.append("left ");
        }
        if (position >= columns){
            outRect.top = margin;
            sb.append("top ");
        }
        int row =  (int)((double) position / columns) ;
        if(nrows == -2 || row < nrows-1){
            outRect.bottom = margin;
            sb.append("bottom ");
        }
        Log.d(DEBUG_TAG, "margins put: " + sb.toString());
 */