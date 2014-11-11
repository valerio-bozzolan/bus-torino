package it.reyboz.bustorino;

import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.os.Bundle;
import android.widget.TextView;

public class AboutActivity extends ActionBarActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_about);
		Spanned htmlText = Html.fromHtml(getResources().getString(
				R.string.about_history));
		TextView aboutTextView = (TextView) findViewById(R.id.aboutTextView);
		aboutTextView.setText(htmlText);
		aboutTextView.setMovementMethod(LinkMovementMethod.getInstance());
	}
	/*
	 * @Override public boolean onCreateOptionsMenu(Menu menu) { // Inflate the
	 * menu; this adds items to the action bar if it is present. //
	 * getMenuInflater().inflate(R.menu.about, menu); return true; }
	 */
	/*
	 * @Override public boolean onOptionsItemSelected(MenuItem item) { // Handle
	 * action bar item clicks here. The action bar will // automatically handle
	 * clicks on the Home/Up button, so long // as you specify a parent activity
	 * in AndroidManifest.xml. //int id = item.getItemId(); //if (id ==
	 * R.id.action_settings) { // return true; //} return
	 * super.onOptionsItemSelected(item); }
	 */
}
