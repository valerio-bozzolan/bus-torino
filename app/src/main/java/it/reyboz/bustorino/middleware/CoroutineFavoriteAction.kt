package it.reyboz.bustorino.middleware

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import it.reyboz.bustorino.R
import it.reyboz.bustorino.backend.Stop
import it.reyboz.bustorino.data.UserDB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CoroutineFavoriteAction(
    context: Context,
    private var action: Action,
    private val listener: ResultListener
) {
    private val context = context.applicationContext

    enum class Action { ADD, REMOVE, TOGGLE, UPDATE }

    fun interface ResultListener {
        fun doStuffWithResult(result: Boolean)
    }

    fun execute(stop: Stop) {
        CoroutineScope(Dispatchers.IO).launch {
            val result = doInBackground(stop)
            withContext(Dispatchers.Main) {
                onPostExecute(result)
            }
        }
    }

    private fun doInBackground(stop: Stop): Boolean {
        val userDB = UserDB.getInstance(context)

        if (action == Action.TOGGLE) {
            action = if (userDB.isStopInFavorites(stop.ID)) Action.REMOVE else Action.ADD
        }

        return when (action) {
            Action.ADD    -> userDB.addOrUpdateStop(stop)
            Action.UPDATE -> userDB.updateStop(stop)
            Action.REMOVE -> userDB.deleteStop(stop)
            Action.TOGGLE -> false // irraggiungibile, ma richiesto da when exhaustive
        }
    }

    private fun onPostExecute(result: Boolean) {
        if (result) {
            UserDB.notifyContentProvider(context)
            when (action) {
                Action.ADD    -> Toast.makeText(context, R.string.added_in_favorites, Toast.LENGTH_SHORT).show()
                Action.REMOVE -> Toast.makeText(context, R.string.removed_from_favorites, Toast.LENGTH_SHORT).show()
                else          -> Unit
            }
        } else {
            Toast.makeText(context, R.string.cant_add_to_favorites, Toast.LENGTH_SHORT).show()
        }
        listener.doStuffWithResult(result)
        Log.d("BusTO FavoritesAction", "Action $action completed")
    }
}