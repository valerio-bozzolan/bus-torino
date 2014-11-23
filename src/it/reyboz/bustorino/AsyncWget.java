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
package it.reyboz.bustorino;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.AsyncTask;
import android.util.Log;

public class AsyncWget extends AsyncTask<String, String, String> {
	protected Exception exceptions;

	protected String doInBackground(String... uri) {
		exceptions = null;
		Log.d("AsyncWget", "Catching URL in background: " + uri[0]);
		HttpURLConnection urlConnection = null;
		StringBuilder result = null;
		try {
			URL url = new URL(uri[0]);
			urlConnection = (HttpURLConnection) url.openConnection();
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
			// TODO Auto-generated catch block
			Log.e("AsyncWget", e.getMessage());
			exceptions = e;
		} finally {
			urlConnection.disconnect();
		}

		if (result == null) {
			return null;
		}
		return result.toString();
	}

	protected void onProgressUpdate(Integer... progress) {

	}

	protected void onPostExecute(String result) {
		super.onPostExecute(result);
	}

	protected void onCancelled() {
		this.cancel(true);
	}

	protected void onError() {

	}
}
