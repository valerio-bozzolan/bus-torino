package it.reyboz.bustorino;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import android.util.Log;

public class GTTSiteSucker {

	public static String arrivalTimesByLineQuery(String busStop) {
		return "http://www.5t.torino.it/5t/trasporto/arrival-times-byline.jsp?action=getTransitsByLine&shortName=" + busStop;
	}

	/**
	 * A time passage is a time with the answer of: Is this in real time?
	 * @author boz
	 */
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

	/**
	 * An ArrivalsAtBusStop has informations of the buses that pass through it (like line numbers)
	 * @author boz
	 */
	public static class ArrivalsAtBusStop {
		private int codLineaGTT;
		private String lineaGTT;
		private ArrayList<TimePassage> timesPassages;

		public ArrivalsAtBusStop() {
			timesPassages = new ArrayList<TimePassage>();
		}

		public void setCodLineaGTT(int codLineaGTT) {
			this.codLineaGTT = codLineaGTT;
		}

		public void setLineaGTT(String lineaGTT) {
			this.lineaGTT = lineaGTT;
		}

		public void addTimePassage(String time, boolean isInRealTime) {
			timesPassages.add(new TimePassage(time, isInRealTime));
		}

		public int getCodLineaGTT() {
			return codLineaGTT;
		}

		public String getLineaGTT() {
			return lineaGTT;
		}

		public ArrayList<TimePassage> getTimePassages() {
			return timesPassages;
		}

		public String toString() {
			return codLineaGTT + " " + lineaGTT;
		}
		
		public String getTimePassagesString() {
			String out = "";
			for(TimePassage timePassage:timesPassages) {
				out += timePassage.getTime() + (timePassage.isInRealTime() ? "*" : "") + " ";
			}
			if(timesPassages.size() == 0) {
				out = ":(";
			}
			return out;
		}
	}

	/**
	 * A BusStop has information on the bus stop (bus stop name) and of all the buses that pass through it
	 * @author boz
	 */
	public static class BusStop {
		private String stationName;
		private ArrivalsAtBusStop[] arrivalsAtBusStop;
		
		BusStop(String stationName, ArrivalsAtBusStop[] arrivalsAtBusStop) {
			this.stationName = stationName;
			this.arrivalsAtBusStop = arrivalsAtBusStop;
		}

		public void setNomeUmano(String stationName) {
			this.stationName = stationName;
		}

		public String getStationName() {
			return stationName;
		}

		public ArrivalsAtBusStop[] getArrivalsAtBusStop() {
			return arrivalsAtBusStop;
		}
	}
	
	/**
	 * API/Workaround to get all informations from the 5T website
	 * 
	 * @param html
	 * @author Valerio Bozzolan
	 * @return BusStop
	 */
	public static BusStop arrivalTimesBylineHTMLSucker(String html) {
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
					arrivalAtBusStop.setLineaGTT(lineaGTT);
					arrivalAtBusStop.setCodLineaGTT(Integer
							.parseInt(codLineaGTT));
					codLineaGTTfound = true;
					continue;
				}
				
				// Look for "<i>*</i>" (aka "prodotto surgelato")
				boolean frozenProduct = !td.select("i").isEmpty();
				if(frozenProduct) {
					td.select("i").remove();
					tdContent = td.html();
				}
				
				arrivalAtBusStop.addTimePassage(tdContent, frozenProduct);
			}
			arrivalsAtBusStop.add(arrivalAtBusStop);
		}

		// Sucking bus station name (e.g.: POZZO STRADA)
		String nomeUmano = "";
		Element tagNomeUmano = doc.select("span").first();
		if(tagNomeUmano != null) {
			nomeUmano = tagNomeUmano.html();
		}
		Matcher matcher = Pattern.compile("nbsp;(.*)").matcher(
				nomeUmano);
		if (matcher.find()) {
			nomeUmano = matcher.group(1);
			Log.d("miao", "nome umano:" + nomeUmano);
		}

		return new BusStop(nomeUmano, (ArrivalsAtBusStop[]) arrivalsAtBusStop.toArray(new ArrivalsAtBusStop[] {}));
	}
}
