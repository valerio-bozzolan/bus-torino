package it.reyboz.bustorino;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import android.util.Log;

public class GTTSiteSucker {

	public static class TimePassage {
		private String time;
		private boolean isInRealTime;

		public TimePassage(String time, boolean isInRealTime) {
			this.time = time;
			this.isInRealTime = isInRealTime;
		}

		public String getTime() {
			return time;
		}

		public boolean isInRealTime() {
			return isInRealTime;
		}
	}

	public static class ArrivalsAtBusStop {
		private int codLineaGTT;
		private int lineaGTT;
		private ArrayList<TimePassage> timesPassages;

		public ArrivalsAtBusStop() {
			timesPassages = new ArrayList<TimePassage>();
		}

		public void setCodLineaGTT(int codLineaGTT) {
			this.codLineaGTT = codLineaGTT;
		}

		public void setLineaGTT(int lineaGTT) {
			this.lineaGTT = lineaGTT;
		}

		public void addTimePassage(String time, boolean isInRealTime) {
			timesPassages.add(new TimePassage(time, isInRealTime));
		}

		public int getCodLineaGTT() {
			return codLineaGTT;
		}

		public int getLineaGTT() {
			return lineaGTT;
		}

		public ArrayList<TimePassage> getTimePassages() {
			return timesPassages;
		}

		public String toString() {
			return codLineaGTT + " " + lineaGTT;
		}
	}

	public static String arrivalTimesByLineQuery(String busStop) {
		return "http://www.5t.torino.it/5t/trasporto/arrival-times-byline.jsp?action=getTransitsByLine&shortName=" + busStop;
	}
	
	/**
	 * Workaround to get all informations from the 5T website
	 * 
	 * @param html
	 * @author Valerio Bozzolan
	 */
	public static ArrivalsAtBusStop[] arrivalTimesBylineHTMLSucker(String html) {
		ArrayList<ArrivalsAtBusStop> arrivalsAtBusStop = new ArrayList<ArrivalsAtBusStop>();
		Document doc = Jsoup.parse(html);
		for (Element tr : doc.getElementsByTag("tr")) {
			ArrivalsAtBusStop arrivalAtBusStop = new ArrivalsAtBusStop();

			boolean codLineaGTTfound = false;
			for (Element td : tr.children()) {
				String tdContent = td.html();
				if (tdContent.isEmpty()) {
					continue;
				}

				if (!codLineaGTTfound) {
					Element tdURL = td.select("a").first();
					String lineaGTT = tdURL.html();
					String codLineaGTT = "";
					Matcher matcher = Pattern.compile("([0-9])+").matcher(
							tdURL.attr("href"));
					if (matcher.find()) {
						codLineaGTT = matcher.group();
					}
					arrivalAtBusStop.setLineaGTT(Integer.parseInt(lineaGTT));
					arrivalAtBusStop.setCodLineaGTT(Integer
							.parseInt(codLineaGTT));
					codLineaGTTfound = true;
					continue;
				}
				
				// Look for "<i>*</i>"
				boolean frozenProduct = !td.select("i").isEmpty();
				if(frozenProduct) {
					td.select("i").remove();
					tdContent = td.html();
				}

				Log.d("miao", "Prodotto surgelato: " + ((frozenProduct) ? "Yeppa" : "Nopely nope"));
				
				arrivalAtBusStop.addTimePassage(tdContent, frozenProduct);
				Log.d("miao td", "td: " + tdContent);
			}
			arrivalsAtBusStop.add(arrivalAtBusStop);
		}
		return (ArrivalsAtBusStop[]) arrivalsAtBusStop
				.toArray(new ArrivalsAtBusStop[] {});
	}
}
