package it.reyboz.bustorino.util

import android.graphics.Rect
import android.util.Log

import android.view.View
import androidx.core.widget.NestedScrollView


class ViewUtils {

    companion object{
        const val DEBUG_TAG="BusTO:ViewUtils"
        fun isViewFullyVisibleInScroll(view: View, scrollView: NestedScrollView): Boolean {
            val scrollBounds = Rect()
            scrollView.getDrawingRect(scrollBounds)
            val top = view.y
            val bottom = top + view.height
            Log.d(DEBUG_TAG, "Scroll bounds are $scrollBounds, top:${view.y}, bottom $bottom")
            return (scrollBounds.top < top && scrollBounds.bottom > bottom)
        }
        fun isViewPartiallyVisibleInScroll(view: View, scrollView: NestedScrollView): Boolean{
            val scrollBounds = Rect()
            scrollView.getHitRect(scrollBounds)
            Log.d(DEBUG_TAG, "Scroll bounds are $scrollBounds")
            if (view.getLocalVisibleRect(scrollBounds)) {
               return true
            } else {
                return false
            }
        }
    }
}