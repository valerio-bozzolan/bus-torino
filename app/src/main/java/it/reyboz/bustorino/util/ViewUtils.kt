package it.reyboz.bustorino.util

import android.R
import android.content.Context
import android.content.res.Resources.Theme
import android.graphics.Rect
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.Transformation
import androidx.annotation.ColorInt
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

        //from https://stackoverflow.com/questions/4946295/android-expand-collapse-animation
        fun expand(v: View,duration: Long, layoutHeight: Int = WindowManager.LayoutParams.WRAP_CONTENT) {
            val matchParentMeasureSpec =
                View.MeasureSpec.makeMeasureSpec((v.parent as View).width, View.MeasureSpec.EXACTLY)
            val wrapContentMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            v.measure(matchParentMeasureSpec, wrapContentMeasureSpec)
            val targetHeight = v.measuredHeight

            // Older versions of android (pre API 21) cancel animations for views with a height of 0.
            v.layoutParams.height = 1
            v.visibility = View.VISIBLE
            val a: Animation = object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                    v.layoutParams.height =
                        if (interpolatedTime == 1f) layoutHeight
                        else (targetHeight * interpolatedTime).toInt()
                    v.requestLayout()
                }

                override fun willChangeBounds(): Boolean {
                    return true
                }
            }

            // Expansion speed of 1dp/ms
            if(duration == DEF_DURATION)
                a.duration = (targetHeight / v.context.resources.displayMetrics.density).toInt().toLong()
            else
                a.duration = duration
            v.startAnimation(a)
        }

        fun collapse(v: View, duration: Long): Animation {
            val initialHeight = v.measuredHeight
            val a: Animation = object : Animation() {
                override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
                    if (interpolatedTime == 1f) {
                        v.visibility = View.GONE
                    } else {
                        v.layoutParams.height = initialHeight - (initialHeight * interpolatedTime).toInt()
                        v.requestLayout()
                    }
                }

                override fun willChangeBounds(): Boolean {
                    return true
                }
            }

            // Collapse speed of 1dp/ms
            if (duration == DEF_DURATION)
                a.duration = (initialHeight / v.context.resources.displayMetrics.density).toInt().toLong()
            else
                a.duration = duration
            v.startAnimation(a)
            return a
        }

        const val DEF_DURATION: Long = -2

        fun getColorFromTheme(context: Context, resId: Int): Int {
            val typedValue = TypedValue()
            val theme: Theme = context.getTheme()
            theme.resolveAttribute(resId, typedValue, true)
            val color = typedValue.data
            return color
        }

    }
}