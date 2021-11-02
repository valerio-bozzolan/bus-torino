/*
	BusTO - Arrival times for Turin public transports.
    Copyright (C) 2014  Valerio Bozzolan

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

package it.reyboz.bustorino.backend;

import android.content.Context;
import androidx.annotation.Nullable;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public abstract class networkTools {
    public static String getDOM(final URL url, final AtomicReference<Fetcher.Result> res) {
        //Log.d("asyncwget", "Catching URL in background: " + uri[0]);
        HttpURLConnection urlConnection;
        StringBuilder result = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch(IOException e) {
            res.set(Fetcher.Result.SERVER_ERROR);
            return null;
        }

        try {
            InputStream in = new BufferedInputStream(
                    urlConnection.getInputStream());
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in));
            result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        } catch (Exception e) {
            //Log.e("asyncwget", e.getMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }

        if (result == null) {
            res.set(Fetcher.Result.SERVER_ERROR);
            return null;
        }

        res.set(Fetcher.Result.PARSER_ERROR); // will be set to "OK" later, this is a safety net in case StringBuilder returns null, the website returns an HTTP 204 or something like that.
        return result.toString();
    }

    public static Fetcher.Result saveFileInCache(File outputFile, URL url) {
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            //e.printStackTrace();
            return Fetcher.Result.CONNECTION_ERROR;
        }
        urlConnection.setConnectTimeout(4000);
        urlConnection.setReadTimeout(50 * 1000);
        System.out.println("Last modified: "+new Date(urlConnection.getLastModified()));

        Log.d("BusTO net Tools", "Download file "+url);
        try (InputStream inputStream = urlConnection.getInputStream()) {
            //File outputFile = new File(con.getFilesDir(), fileName);
            //BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            FileOutputStream outputStream = new FileOutputStream(outputFile);
            byte buffer[] = new byte[16384];
            boolean inProgress = true;
            while(inProgress){
                int numread = inputStream.read(buffer);
                inProgress = (numread > 0);
                if(inProgress) outputStream.write(buffer, 0, numread);
            }
            outputStream.close();
            //while (bufferedInputStream.available())
        } catch (IOException e) {
            e.printStackTrace();
            try {
                final Fetcher.Result  res;
                if(urlConnection.getResponseCode()==404)
                    res= Fetcher.Result.SERVER_ERROR_404;
                else if(urlConnection.getResponseCode()!=200)
                    res= Fetcher.Result.SERVER_ERROR;
                else res= Fetcher.Result.PARSER_ERROR;
                urlConnection.disconnect();
                return res;
            } catch (IOException ioException) {
                ioException.printStackTrace();
                urlConnection.disconnect();
                return Fetcher.Result.PARSER_ERROR;
            }
        }
        urlConnection.disconnect();
        return Fetcher.Result.OK;
    }

    @Nullable
    public static Date checkLastModificationDate(URL url, AtomicReference<Fetcher.Result> res) {
        HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            //e.printStackTrace();
            res.set(Fetcher.Result.CONNECTION_ERROR);
            return null;
        }
        urlConnection.setConnectTimeout(4000);
        urlConnection.setReadTimeout(4 * 1000);
        System.out.println("Last modified: "+new Date(urlConnection.getLastModified()));

        Log.d("BusTO net Tools", "Download file "+url);
        final Date theDate = new Date(urlConnection.getLastModified());

        try {
            if(urlConnection.getResponseCode()==404)
                res.set(Fetcher.Result.SERVER_ERROR_404);
            else if(urlConnection.getResponseCode()!=200)
                res.set(Fetcher.Result.SERVER_ERROR);
        } catch (IOException e) {
            e.printStackTrace();
            res.set(Fetcher.Result.PARSER_ERROR);
        }
        urlConnection.disconnect();
        //theDate.getTime()
        return theDate;
    }
    @Nullable
    static String queryURL(URL url, AtomicReference<Fetcher.Result> res){
        return queryURL(url,res,null);
    }
    @Nullable
    static String queryURL(URL url, AtomicReference<Fetcher.Result> res, Map<String,String> headers) {
        HttpURLConnection urlConnection;
        InputStream in;
        String s;

        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch(IOException e) {
            //e.printStackTrace();
            res.set(Fetcher.Result.SERVER_ERROR); // even when offline, urlConnection works fine. WHY.
            return null;
        }

        // TODO: make this configurable?
        urlConnection.setConnectTimeout(3000);
        urlConnection.setReadTimeout(10000);
        if(headers!= null){
            for(String key : headers.keySet()){
                urlConnection.setRequestProperty(key,headers.get(key));
            }
        }
        res.set(Fetcher.Result.SERVER_ERROR); // will be set to OK later

        try {
            in = urlConnection.getInputStream();
        } catch (Exception e) {
            try {
                if(urlConnection.getResponseCode()==404)
                    res.set(Fetcher.Result.SERVER_ERROR_404);
            } catch (IOException e2) {
                e2.printStackTrace();
            }
            return null;

        }
        //s = streamToString(in);
        try {
            final long startTime = System.currentTimeMillis();
            s = parseStreamToString(in);
            final long endtime = System.currentTimeMillis();
            Log.d("NetworkTools-queryURL","reading response took "+(endtime-startTime)+" millisec");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }


        try {
            in.close();
        } catch(IOException ignored) {
            //ignored.printStackTrace();
        }

        try {
            urlConnection.disconnect();
        } catch(Exception ignored) {
            //ignored.printStackTrace();
        }

        if(s.length() == 0) {
            Log.w("NET TOOLS", "string is empty");
            return null;
        } else {
            //Log.d("NET TOOLS", s);
            return s;
        }
    }

    // https://stackoverflow.com/a/5445161
    static String streamToString(InputStream is) {
        Scanner s = new Scanner(is, "UTF-8").useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    /**
     * New method, maybe faster, to read inputStream
     * also see https://stackoverflow.com/a/5445161
     * @param is what to read
     * @return the String Read
     * @throws IOException from the InputStreamReader
     */
    static String parseStreamToString(InputStream is) throws IOException{
        final int bufferSize = 1024;
        final char[] buffer = new char[bufferSize];
        final StringBuilder out = new StringBuilder();
        InputStreamReader in = new InputStreamReader(is, "UTF-8");
        int rsz= in.read(buffer, 0, buffer.length);
        while( rsz >0) {
            out.append(buffer, 0, rsz);
            rsz = in.read(buffer, 0, buffer.length);
        }
        return out.toString();

    }

    static int failsafeParseInt(String str) {
        try {
            return Integer.parseInt(str);
        } catch(NumberFormatException e) {
            return 0;
        }
    }
}
