/*
	BusTO - Data components
    Copyright (C) 2021 Fabio Mazza

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
package it.reyboz.bustorino;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import it.reyboz.bustorino.backend.Fetcher;
import it.reyboz.bustorino.backend.gtfs.GtfsDataParser;
import it.reyboz.bustorino.backend.networkTools;
import it.reyboz.bustorino.backend.utils;
import it.reyboz.bustorino.data.gtfs.GtfsDatabase;
import it.reyboz.bustorino.data.gtfs.StaticGtfsDao;
import it.reyboz.bustorino.middleware.GeneralActivity;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class ActivityExperiments extends GeneralActivity {

    ExecutorService executorService;
    final static String DEBUG_TAG = "ExperimentsGTFS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_experiments);
        Button deleteButton = findViewById(R.id.deleteButton);
        if(deleteButton!=null)
            deleteButton.setOnClickListener(view -> {
                File saveFile = new File(getFilesDir(), "gtfs_data.zip");
                if(!saveFile.isDirectory() && saveFile.exists()){
                    //delete the file
                    if(saveFile.delete())
                        Toast.makeText(this, "Gtfs zip deleted", Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(this, "Cannot delete gtfs zip", Toast.LENGTH_SHORT).show();
                } else
                    Toast.makeText(this, "Gtfs data zip not present", Toast.LENGTH_SHORT).show();
            });

        Button cleanDBButton = findViewById(R.id.deleteDbButton);
        if(cleanDBButton!=null)
            cleanDBButton.setOnClickListener(this::deleteDatabase);

        executorService = Executors.newFixedThreadPool(2);
    }

    public void runExp(View v){

        final Context appContext = v.getContext().getApplicationContext();

        Runnable run = new Runnable() {
            @Override
            public void run() {

            AtomicReference<Fetcher.Result> res = new AtomicReference<>();
            //List<String> files = GtfsDataParser.readFilesList(res);
            Date updateDate = GtfsDataParser.getLastGTFSUpdateDate(res);
            Log.w(
                    "ExperimentGTFS", "Last update date is " + updateDate//utils.joinList(files, "\n")
            );
            //Toast.makeText(v.getContext(), "Gtfs data already downloaded", Toast.LENGTH_SHORT).show();
                StaticGtfsDao dao = GtfsDatabase.Companion.getGtfsDatabase(appContext).gtfsDao();
                Log.d(DEBUG_TAG, String.valueOf(dao));
                dao.deleteAllStopTimes();


            File saveFile = new File(getFilesDir(), "gtfs_data.zip");
            if (!saveFile.isDirectory() && saveFile.exists()) {
                Log.w(DEBUG_TAG, "Zip exists: " + saveFile);


                try (ZipFile zipFile = new ZipFile(saveFile)) {
                    //ZipInputStream stream = new ZipInputStream(fileStream);
                    // now iterate through each item in the stream. The get next
                    // entry call will return a ZipEntry for each file in the
                    // stream
                    Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    ZipEntry entry;
                    String line;
                    //final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
                    HashSet<ZipEntry> readLater = new HashSet<>();
                    while(entries.hasMoreElements()){
                        entry = entries.nextElement();
                        //String tableName = entry.getName().split("\\.")[0].trim();
                        if(entry.getName().trim().equals("stop_times.txt")) {
                            readLater.add(entry);
                            continue;
                        }
                        GtfsDataParser.readGtfsZipEntry(entry, zipFile, v.getContext().getApplicationContext());
                    }
                    for(ZipEntry laterEntry: readLater){
                        GtfsDataParser.readGtfsZipEntry(laterEntry, zipFile, v.getContext().getApplicationContext());
                    }
                    //Toast.makeText(appContext, "D", Toast.LENGTH_SHORT).show();
                    /*
                    while ((entry = stream.getNextEntry()) != null) {
                        String s = String.format(Locale.ENGLISH, "Entry: %s len %d added",
                                entry.getName(),
                                entry.getSize()
                        );
                        if(entry.getName().contains("stop_times.")){
                            //skip and do later

                        }
                        //Toast.makeText(v.getContext(), "File: " + entry.getName(), Toast.LENGTH_SHORT).show();
                        Log.d(DEBUG_TAG, s);
                        //read data in table
                        final String tableName = entry.getName().split("\\.")[0].trim();



                        // Once we get the entry from the stream, the stream is
                        // positioned read to read the raw data, and we keep
                        // reading until read returns 0 or less.
                        //result.add(entry.getName());
                    }
                    stream.close();
                     */
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //saveFile.delete();
            } else
                try {
                    //Toast.makeText(v.getContext(), "Downloading gtfs data", Toast.LENGTH_SHORT).show();

                    networkTools.saveFileInCache(saveFile, new URL(GtfsDataParser.GTFS_ADDRESS));
                    Log.w(DEBUG_TAG, "File saved");
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }

            }
        };

        Toast.makeText(this, "Launching, no result will show", Toast.LENGTH_SHORT).show();
        //Looper looper = new Looper(true);
        //Handler handler = new Handler();
        //handler.post(run);
        executorService.execute(run);


    }

    public void deleteDatabase(View v){
        final Context con = getApplicationContext().getApplicationContext();
        Toast.makeText(this, "Deleting GTFS DB contents, wait a few seconds", Toast.LENGTH_SHORT).show();
        Runnable deleteDB = new Runnable() {
            @Override
            public void run() {
                StaticGtfsDao dao = GtfsDatabase.Companion.getGtfsDatabase(con).gtfsDao();
                Log.d(DEBUG_TAG, String.valueOf(dao));
                dao.deleteAllStopTimes();
                dao.deleteAllTrips();
                dao.deleteAllRoutes();
                dao.deleteAllStops();
                dao.deleteAllServices();
                Log.d(DEBUG_TAG, "Deleted stuff");
            }
        };

        executorService.execute(deleteDB);
    }
}