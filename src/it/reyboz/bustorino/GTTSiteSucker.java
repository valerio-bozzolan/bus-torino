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
		return "http://www.5t.torino.it/5t/trasporto/arrival-times-byline.jsp?action=getTransitsByLine&shortName="
				+ busStop;
	}

	/**
	 * A time passage is a time with the answer of: Is this in real time?
	 * 
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
	 * Passages (at a bus line)
	 * (like line numbers)
	 * 
	 * @author boz
	 */
	public static class PassagesBusLine {

		private int busLineID;
		private String busLineName;

		private ArrayList<TimePassage> timesPassages;

		public PassagesBusLine() {
			timesPassages = new ArrayList<TimePassage>();
		}

		public void addTimePassage(String time, boolean isInRealTime) {
			timesPassages.add(new TimePassage(time, isInRealTime));
		}

		public ArrayList<TimePassage> getTimePassages() {
			return timesPassages;
		}

		public String getTimePassagesString() {
			String out = "";
			for (TimePassage timePassage : timesPassages) {
				out += timePassage.getTime()
						+ (timePassage.isInRealTime() ? "*" : "") + " ";
			}
			if (timesPassages.size() == 0) {
				out = ":(";
			}
			return out;
		}

		public String getBusLineName() {
			return busLineName;
		}

		public void setBusLineName(String busLineName) {
			this.busLineName = busLineName;
		}

		public int getLineID() {
			return busLineID;
		}

		public void setBusLineID(int busLineID) {
			this.busLineID = busLineID;
		}

		public String toString() {
			return getTimePassagesString();
		}
	}

	/**
	 * A BusStop has information on the bus stop (bus stop name) and of all the
	 * buses that pass through it
	 * 
	 * @author boz
	 */
	public static class BusStop {
		private Integer busStopID; // Es: 1254 (always Integer)
		private String busStopName; // Es: MARCONI
		private PassagesBusLine[] arrivalsAtBusStop;

		BusStop(Integer busStopID, String busStopName,
				PassagesBusLine[] arrivalsAtBusStop) {
			this.busStopID = busStopID;
			this.busStopName = busStopName;
			this.arrivalsAtBusStop = arrivalsAtBusStop;
		}

		public void setGTTBusStopName(String busStopName) {
			this.busStopName = busStopName;
		}

		public String getBusStopName() {
			return busStopName;
		}

		public Integer getBusStopID() {
			return busStopID;
		}

		public void setBusStopName(String busStopName) {
			this.busStopName = busStopName;
		}

		public PassagesBusLine[] getPassagesBusLine() {
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
		ArrayList<PassagesBusLine> arrivalsAtBusStop = new ArrayList<PassagesBusLine>();
		Document doc = Jsoup.parse(html);
		for (Element tr : doc.getElementsByTag("tr")) {
			PassagesBusLine passagesBusLine = new PassagesBusLine();

			boolean codLineaGTTfound = false;

			for (Element td : tr.children()) {
				String tdContent = td.html();
				if (tdContent.isEmpty()) {
					continue;
				}

				if (!codLineaGTTfound) {
					Element tdURL = td.select("a").first();
					String busLineName = tdURL.html();
					String busLineID = "";
					Matcher matcher = Pattern.compile("([0-9])+").matcher(
							tdURL.attr("href"));
					if (matcher.find()) {
						busLineID = matcher.group();
					}
					passagesBusLine.setBusLineID(Integer
							.parseInt(busLineID));
					passagesBusLine.setBusLineName(busLineName);
					codLineaGTTfound = true;
					continue;
				}

				// Look for "<i>*</i>" (aka "prodotto surgelato")
				boolean frozenProduct = !td.select("i").isEmpty();
				if (frozenProduct) {
					td.select("i").remove();
					tdContent = td.html();
				}

				passagesBusLine.addTimePassage(tdContent, frozenProduct);
			}
			arrivalsAtBusStop.add(passagesBusLine);
		}

		// Sucking bus stop info
		String busStopInfo = null;
		String busStopName = null;
		Integer busStopID = null;
		Element tagStationInfo = doc.select("span").first();
		if (tagStationInfo != null) {
			busStopInfo = tagStationInfo.html();
			Log.d("it.reyboz", "stationInfo:" + busStopInfo);

			// Sucking station number (e.g.: 1254)
			Matcher matcherStationNumber = Pattern.compile("([0-9]+)").matcher(
					busStopInfo);
			if (matcherStationNumber.find()) {
				busStopID = Integer.parseInt(matcherStationNumber.group(1));
			}
			Log.d("it.reyboz", "stationNumber:" + busStopID);

			// Sucking station name (e.g.: POZZO STRADA)
			Matcher matcherStationName = Pattern.compile("&nbsp;(.+)").matcher(
					busStopInfo);
			if (matcherStationName.find()) {
				busStopName = matcherStationName.group(1);
			}
			Log.d("it.reyboz", "stationName:" + busStopName);
		}

		return new BusStop(busStopID, busStopName,
				(PassagesBusLine[]) arrivalsAtBusStop
						.toArray(new PassagesBusLine[] {}));
	}
}
