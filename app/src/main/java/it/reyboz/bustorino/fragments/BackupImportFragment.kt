package it.reyboz.bustorino.fragments

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import de.siegmar.fastcsv.reader.CsvReader
import de.siegmar.fastcsv.writer.CsvWriter
import it.reyboz.bustorino.R
import it.reyboz.bustorino.data.PreferencesHolder
import it.reyboz.bustorino.data.UserDB
import it.reyboz.bustorino.util.ImportExport
import java.io.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream


/**
 * A simple [Fragment] subclass.
 * Use the [BackupImportFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class BackupImportFragment : Fragment() {

    private val saveFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.also { uri ->
                    writeDataZip(uri)
                }
            }
        }

    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (!(loadFavorites|| loadPreferences)){
                Toast.makeText(context, R.string.message_check_at_least_one, Toast.LENGTH_SHORT).show()
            }
            else if (result.resultCode == Activity.RESULT_OK) {

                result.data?.data?.also { uri ->

                    loadZipData(uri,loadFavorites, loadPreferences)
                }
            }
        }


    private lateinit var saveButton: Button
    private var loadFavorites = true
    private var loadPreferences = true
    private lateinit var checkFavorites: CheckBox
    private lateinit var checkPreferences: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }*/
    }



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val rootview= inflater.inflate(R.layout.fragment_test_saving, container, false)

        saveButton = rootview.findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            startFileSaveIntent()
        }
        checkFavorites = rootview.findViewById(R.id.favoritesCheckBox)
        checkFavorites.setOnCheckedChangeListener { _, isChecked ->
            loadFavorites = isChecked

        }
        checkPreferences = rootview.findViewById(R.id.preferencesCheckBox)
        checkPreferences.setOnCheckedChangeListener { _, isChecked ->
            loadPreferences = isChecked

        }
        val readFavoritesButton = rootview.findViewById<Button>(R.id.loadDataButton)
        readFavoritesButton.setOnClickListener {
            startOpenCSVIntent()
        }


        return rootview
    }
    private fun startFileSaveIntent() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val day_string = getCurrentDateString()
            type = "application/zip" //"text/csv" // Set MIME type to CSV
            putExtra(Intent.EXTRA_TITLE, "busto_data_${day_string}.zip") // Default file name
            // Optionally, specify a URI for the directory that should be opened in
            // the system file picker before your app creates the document.
            //putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }
        saveFileLauncher.launch(intent)
    }

    private fun writeCsv(uri: Uri){
        val context = context ?: return
        val contentResolver = context.contentResolver
        contentResolver.openOutputStream(uri)?.use {
            OutputStreamWriter(it).use {wr->
                val csvWriter = CsvWriter.builder().build(wr)
                val userDB = UserDB(context)
                userDB.writeFavoritesToCsv(csvWriter)
                csvWriter.close()
                Toast.makeText(context, R.string.saved_data, Toast.LENGTH_SHORT).show()
            }

        }
    }
    private fun writeStringToZipOS(string: String, zipOutputStream: ZipOutputStream){
        val bais = ByteArrayInputStream(string.toByteArray())
        val bytes = ByteArray(1024)
        var length: Int
        while ((bais.read(bytes).also { length = it }) >= 0) {
            zipOutputStream.write(bytes, 0, length)
        }
    }

    private fun readFileToString(zipInputStream: ZipInputStream): String{
        val buffer = ByteArrayOutputStream()
        val data = ByteArray(1024)
        var count: Int
        // Read the content of the specific entry into the buffer
        while (zipInputStream.read(data, 0, data.size).also { count = it } != -1) {
            buffer.write(data, 0, count)
        }
        // Convert the buffer's content to a string
        return buffer.toString()
    }

    private fun writeDataZip(uri: Uri){
        val context = context ?: return
        val contentResolver = context.contentResolver
        contentResolver.openOutputStream(uri)?.use {outs ->
            val bof = BufferedOutputStream(outs)
            val zipOutputStream = ZipOutputStream(bof)

            //write main preferences
            zipOutputStream.putNextEntry(ZipEntry(MAIN_PREF_NAME))
            var sharedPrefs = PreferencesHolder.getMainSharedPreferences(context)
            var jsonPref = ImportExport.writeSharedPreferencesIntoJSON(sharedPrefs)
            writeStringToZipOS(jsonPref.toString(2), zipOutputStream)
            zipOutputStream.closeEntry()

            zipOutputStream.putNextEntry(ZipEntry(APP_PREF_NAME))
            sharedPrefs = PreferencesHolder.getAppPreferences(context)
            jsonPref = ImportExport.writeSharedPreferencesIntoJSON(sharedPrefs)
            writeStringToZipOS(jsonPref.toString(2), zipOutputStream)
            zipOutputStream.closeEntry()
            //add CSV
            zipOutputStream.putNextEntry(ZipEntry(FAVORITES_NAME))
            val outWriter = OutputStreamWriter(zipOutputStream)
            val csvWriter = CsvWriter.builder().build(outWriter)
            val userDB = UserDB(context)
            userDB.writeFavoritesToCsv(csvWriter)
            outWriter.flush()
            zipOutputStream.closeEntry()

            zipOutputStream.close()

            Toast.makeText(context, R.string.saved_data, Toast.LENGTH_SHORT).show()
        }
    }
    private fun loadZipData(uri: Uri, loadFavorites: Boolean, loadPreferences: Boolean){
        val context = context ?: return
        val contentResolver = context.contentResolver
        contentResolver.openInputStream(uri)?.use {ins->
            ZipInputStream(ins).use {zipstream->
                var entry: ZipEntry? = zipstream.nextEntry

                while (entry != null) {
                    Log.d("testSavingFragment", "read file: ${entry.name}")
                    when (entry.name){
                        FAVORITES_NAME -> if (loadFavorites) {

                            val reader = InputStreamReader(zipstream)
                            val csvReader = CsvReader.builder().build(reader)

                            val userDB =  UserDB(context)
                            val updated = userDB.insertRowsFromCSV(csvReader)

                            userDB.close()
                            //csvReader.close()
                        }
                        APP_PREF_NAME -> if(loadPreferences){
                            val jsonString = readFileToString(zipstream)
                            try {
                                val pref = PreferencesHolder.getAppPreferences(context)
                                ImportExport.importJsonToSharedPreferences(pref, jsonString, null, Regex("osmdroid\\."))
                            } catch (e: Exception){
                                Log.e(DEBUG_TAG, "Cannot read app preferences from file")
                                e.printStackTrace()
                            }
                        }
                        //Main preferences contains the lines favorites
                        MAIN_PREF_NAME -> if(loadFavorites){
                            val jsonString = readFileToString(zipstream)
                            try {
                                val pref = PreferencesHolder.getMainSharedPreferences(context)
                                //In the future, if we move the favorite lines to a different file,
                                // We should check here if the key is in the jsonObject, and copy it to the other file
                                ImportExport.importJsonToSharedPreferences(pref, jsonString, PreferencesHolder.IGNORE_KEYS_LOAD_MAIN, null,
                                    PreferencesHolder.KEYS_MERGE_SET)
                            } catch (e: Exception){
                                Log.e(DEBUG_TAG, "Cannot read main preferences from file")
                                e.printStackTrace()
                            }
                        }
                    }
                    //load new entry
                    entry = zipstream.nextEntry
                }

            }
            Toast.makeText(context, R.string.data_imported_backup, Toast.LENGTH_SHORT).show()
        }

    }

    ///OPEN CSV
    private fun startOpenCSVIntent(){
        val intent= Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip" // Set MIME type
            // Optionally, specify a URI for the directory that should be opened in
            // the system file picker before your app creates the document.
            //putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }
        openFileLauncher.launch(intent)
    }

    private fun importCSVIntoFavorites(uri: Uri){
        val context = context ?: return
        val contentResolver = context.contentResolver
        contentResolver.openInputStream(uri)?.use {
            InputStreamReader(it).use { stream ->
                val csvReader = CsvReader.builder().build(stream)

                val userDB =  UserDB(context)
                val updated = userDB.insertRowsFromCSV(csvReader)
                Toast.makeText(context, "Read $updated favorites", Toast.LENGTH_SHORT).show()
                userDB.close()
                csvReader.close()
            }
        }
    }

    companion object {

        const val FILE_SAVE = "favorites.csv"
        const val DEBUG_TAG ="BusTO-TestSave"

        const val FAVORITES_NAME = "favorites.csv"
        const val MAIN_PREF_NAME = "preferences_main.json"
        const val APP_PREF_NAME = "preferences_app.json"

        @JvmStatic
        fun newInstance() =
            BackupImportFragment().apply {
                arguments = Bundle() /*.apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
                */
            }

        fun getCurrentDateString(): String{

            val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ITALY)
            val date = Date()
            return  dateFormat.format(date)
        }
    }
}