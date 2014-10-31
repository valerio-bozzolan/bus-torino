package it.reyboz.bustorino;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.AsyncTask;
import android.util.Log;

public class AsyncWget extends AsyncTask<String, String, String> {
	protected String doInBackground(String... uri) {
		Log.d("async-action", "Async in backgroundo: " + uri[0]);
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
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			urlConnection.disconnect();
		}
		return result.toString();
	}

	protected void onProgressUpdate(Integer... progress) {

	}

	protected void onPostExecute(String result) {
		super.onPostExecute(result);
	}
}
