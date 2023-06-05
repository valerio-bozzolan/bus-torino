package it.reyboz.bustorino.util

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import kotlin.math.abs


class DrawableUtils {
    companion object {
        fun getScaledDrawableResources(
            resources: Resources, @DrawableRes id: Int, @DimenRes width: Int, @DimenRes height: Int): Drawable {
            val w = resources.getDimension(width).toInt()
            val h = resources.getDimension(height).toInt()
            return scaledDrawable(resources,id, w, h)
        }

        fun scaledDrawable(resources: Resources, @DrawableRes id: Int, width: Int, height: Int): Drawable {
            val bmp = BitmapFactory.decodeResource(resources, id)
            val bmpScaled = Bitmap.createScaledBitmap(bmp, width, height, false)
            return BitmapDrawable(resources, bmpScaled)
        }
        /*fun writeOnDrawable(resources: Resources, drawableId: Int, colorId: Int, text: String?, textSize: Float, theme: Resources.Theme?): BitmapDrawable? {
            //val bm = BitmapFactory.decodeResource(resources, drawableId).copy(Bitmap.Config.ARGB_8888, true)
            val paint = Paint()
            paint.style = Paint.Style.FILL
            paint.color = colorId
            paint.textSize = textSize
            paint.textAlign = Paint.Align.CENTER

            //get the drawable instead of bitmap
            val drawable = ResourcesCompat.getDrawable(resources,drawableId,theme)
            if (drawable==null){
                Log.e("BusTO:DrawableRes","DRAWABLE IS NULL")
                return null
            }
            Log.d("BusTO:DrawableRes","getResources drawable ${drawable.bounds}")
            val canvas = Canvas()
            drawable.draw(canvas)
            val bounds = drawable.bounds
            canvas.drawText(text!!, (abs(bounds.right - bounds.left)/2).toFloat(), (abs(bounds.top - bounds.bottom) /2).toFloat(), paint)
            return BitmapDrawable(resources,bm)
        }

         */
    }
}