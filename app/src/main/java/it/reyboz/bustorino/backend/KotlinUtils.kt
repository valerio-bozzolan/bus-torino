package it.reyboz.bustorino.backend

import android.content.Context
import android.content.res.Configuration

object KotlinUtils {

    @JvmStatic
    fun isDarkTheme(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }
}