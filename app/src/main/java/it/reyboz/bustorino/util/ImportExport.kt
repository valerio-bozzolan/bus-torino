package it.reyboz.bustorino.util

import android.content.SharedPreferences
import android.util.Log
import it.reyboz.bustorino.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.*


class ImportExport {

    companion object{

        const val TAG = "BusTO-Saving";

        /**
         * Serialize all preferences into an output stream
         * @param os OutputStream to write to
         * @return True iff successful
         */
        fun serialize(os: OutputStream, sharedPreferences: SharedPreferences): Boolean {
            var oos: ObjectOutputStream? = null
            try {
                oos = ObjectOutputStream(os)
                oos.writeObject(sharedPreferences.all)
                oos.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error serializing preferences", if (BuildConfig.DEBUG) e else null)
                return false
            } finally {
                //Utils.closeQuietly(oos, os)
                oos?.close()
            }
            return true
        }

        /**
         * Read all preferences from an input stream.
         * then deserializes the options present in the given stream.
         * If the given object contains an unknown class, the deserialization is aborted and the underlying
         * preferences are not changed by this method
         * @param `is` Input stream to load the preferences from
         * @return True iff the new values were successfully written to persistent storage
         *
         * @throws IllegalArgumentException
         */
        fun deserialize(inputs: InputStream, preferences: SharedPreferences): Boolean {
            var ois: ObjectInputStream? = null
            var map: Map<String?, Any>? = null
            try {
                ois = ObjectInputStream(inputs)
                map = ois.readObject() as Map<String?,Any>?
            } catch (e: IOException) {
                Log.e(TAG, "Error deserializing preferences", if (BuildConfig.DEBUG) e else null)
                return false
            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "Error deserializing preferences", if (BuildConfig.DEBUG) e else null)
                return false
            } finally {
                //Utils.closeQuietly(ois, inputs)
                ois?.close()
            }

            val editor: SharedPreferences.Editor = preferences.edit()
            //editor.clear()

            for ((key, value) in map!!) {
                // Unfortunately, the editor only provides typed setters
                if (value is Boolean) {
                    editor.putBoolean(key, (value as Boolean))
                } else if (value is String) {
                    editor.putString(key, value as String)
                } else if (value is Int) {
                    editor.putInt(key, value as Int)
                } else if (value is Float) {
                    editor.putFloat(key, value as Float)
                } else if (value is Long) {
                    editor.putLong(key, (value as Long))
                } else if (value is Set<*>) {
                    val setvalue = value as Set<*>
                    if (setvalue.iterator().next() is String)
                        editor.putStringSet(key, value as Set<String?>)
                } else {
                    throw IllegalArgumentException("Type " + value.javaClass.name + " is unknown")
                }
            }
            return editor.commit()
        }
        fun importJsonToSharedPreferences(sharedPreferences: SharedPreferences,
                                          allJsonAsString: String,
                                          ignoreKeys: Set<String>?,
                                          ignoreKeyRegex: Regex?): Int{
            return importJsonToSharedPreferences(sharedPreferences, allJsonAsString,
                ignoreKeys, ignoreKeyRegex, HashSet())
        }

        fun importJsonToSharedPreferences(sharedPreferences: SharedPreferences,
                                          allJsonAsString: String,
                                          ignoreKeys: Set<String>?,
                                          ignoreKeyRegex: Regex?,
                                          mergeSetKeys: Set<String>
                                          ): Int {
            // Parse JSON
            val jsonObject = JSONObject(allJsonAsString)


            try {
                // Write to SharedPreferences
                val editor = sharedPreferences.edit()
                jsonObject.keys().forEach { key ->
                    if (ignoreKeys?.contains(key)==true || ignoreKeyRegex?.containsMatchIn(key)==true)
                        //do nothing
                        return@forEach
                    val value = jsonObject.opt(key)
                    when (value) {
                        is Boolean -> editor.putBoolean(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Float -> editor.putFloat(key, value)
                        is String -> editor.putString(key, value)
                        is JSONArray -> { // Handle arrays
                            val set = mutableSetOf<String>()
                            if (mergeSetKeys.contains(key))
                                sharedPreferences.getStringSet(key, mutableSetOf())?.let { set.addAll(it) }
                            for (i in 0 until value.length()) {
                                set.add(value.optString(i))
                            }
                            editor.putStringSet(key, set)
                        }
                        // Handle other types as needed
                    }
                }
                editor.apply()
            } catch (e: Exception){
                Log.e(TAG, "Cannot write sharedPreferences")
                e.printStackTrace()
                return -1
            }
            return 0
        }

        fun writeSharedPreferencesIntoJSON(sharedPreferences: SharedPreferences): JSONObject{
            val allEntries: Map<String?, *> = sharedPreferences.all


            // Convert to JSON
            val json = JSONObject()
            for ((key, value1) in allEntries) {
                val value = value1!!
                if (value is Set<*>) {
                    // Convert StringSet to JSONArray
                    val jsonArray = JSONArray(value)
                    if (key != null) {
                        json.put(key, jsonArray)
                    }
                } else if (key != null) {
                    json.put(key, value)

                }
            }
            return json
        }
    }
}