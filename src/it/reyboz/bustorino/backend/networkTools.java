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

import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public abstract class networkTools {
    static String getDOM(final URL url, final AtomicReference<Fetcher.result> res) {
        //Log.d("asyncwget", "Catching URL in background: " + uri[0]);
        HttpURLConnection urlConnection;
        StringBuilder result = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch(IOException e) {
            res.set(Fetcher.result.SERVER_ERROR);
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
            res.set(Fetcher.result.SERVER_ERROR);
            return null;
        }

        res.set(Fetcher.result.PARSER_ERROR); // will be set to "OK" later, this is a safety net in case StringBuilder returns null, the website returns an HTTP 204 or something like that.
        return result.toString();
    }
    @Nullable
    static String queryURL(URL url, AtomicReference<Fetcher.result> res){
        return queryURL(url,res,null);
    }
    @Nullable
    static String queryURL(URL url, AtomicReference<Fetcher.result> res, Map<String,String> headers) {
        HttpURLConnection urlConnection;
        InputStream in;
        String s;

        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch(IOException e) {
            res.set(Fetcher.result.SERVER_ERROR); // even when offline, urlConnection works fine. WHY.
            return null;
        }

        // TODO: make this configurable?
        urlConnection.setConnectTimeout(5000);
        urlConnection.setReadTimeout(10000);
        if(headers!= null){
            for(String key : headers.keySet()){
                urlConnection.setRequestProperty(key,headers.get(key));
            }
        }
        res.set(Fetcher.result.SERVER_ERROR); // will be set to OK later

        try {
            in = urlConnection.getInputStream();
        } catch (Exception e) {
            try {
                if(urlConnection.getResponseCode()==404)
                    res.set(Fetcher.result.SERVER_ERROR_404);
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
        } catch(Exception ignored) {}

        try {
            urlConnection.disconnect();
        } catch(Exception ignored) {}

        if(s.length() == 0) {
            return null;
        } else {
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
