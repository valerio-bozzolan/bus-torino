package it.reyboz.bustorino;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.util.Log;

public class GTTSiteSucker {

	public static String arrivalTimesByLineQuery(String busStop) {
		return "http://www.5t.torino.it/5t/trasporto/arrival-times-byline.jsp?action=getTransitsByLine&shortName="
				+ busStop;
	}

	/**
	 * Helps comparing times
	 * @author boz
	 */
	public static class Time {
		private int mm;
		private int hh;
		
		public Time(int hh, int mm) {
			this.hh = hh;
			this.mm = mm;
		}

		public boolean isMajorThan(Time time) {
			if(hh >= time.getHH()) {
				return mm >= time.getHH();
			}
			return false;
		}

		public int getHH() {
			return hh;
		}

		public int getMM() {
			return mm;
		}
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

		/**
		 * @return null if can't be converted into a time
		 */
		public Time getComparableTime() {
			int hh, mm;
			String[] parts = time.split(":");
			if(parts.length != 2) {
				return null;
			}
			try {
				hh = Integer.valueOf(parts[0]);
				mm = Integer.valueOf(parts[1]);
			} catch(NumberFormatException e) {
				return null;
			}
			return new Time(hh, mm);
		}
	}

	/**
	 * Passages (at a bus line) (like line numbers)
	 * 
	 * @author boz
	 */
	public static class BusLinePassages {

		private int busLineID;
		private String busLineName;

		private ArrayList<TimePassage> timesPassages;

		public BusLinePassages() {
			timesPassages = new ArrayList<TimePassage>();
		}

		public void addTimePassage(String time, boolean isInRealTime) {
			timesPassages.add(new TimePassage(time, isInRealTime));
		}

		public ArrayList<TimePassage> getTimePassages() {
			return timesPassages;
		}

		public String getTimePassagesString() {
			if(timesPassages.size() == 0) {
				return null;
			}
			String out = "";
			for (TimePassage timePassage : timesPassages) {
				out += timePassage.getTime()
						+ (timePassage.isInRealTime() ? "*" : " ") + " ";
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
		private BusLinePassages[] arrivalsAtBusStop;

		BusStop(Integer busStopID, String busStopName,
				BusLinePassages[] arrivalsAtBusStop) {
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

		public BusLinePassages[] getPassagesBusLine() {
			return arrivalsAtBusStop;
		}
	}

	/**
	 * API/Workaround to get all informations from the 5T website
	 * Return a BusStop object.
	 * BusStop.busStopName can be null.
	 * 
	 * @param html
	 * @author Valerio Bozzolan
	 * @return BusStop
	 */
	public static BusStop getBusStopSuckingHTML(String html) {
		if(html == null) {
			return null;
		}

		ArrayList<BusLinePassages> arrivalsAtBusStop = new ArrayList<BusLinePassages>();
		Document doc = Jsoup.parse(html);

		Elements trs = doc.getElementsByTag("tr");
		for (Element tr : trs) {
			BusLinePassages busLinePassages = new BusLinePassages();

			boolean isBusLineIDFound = false;

			Elements tds = tr.children();
			for (Element td : tds) {
				String tdContent = td.html();
				if (tdContent.isEmpty()) {
					continue;
				}

				if (!isBusLineIDFound) {
					Element tdURL = td.select("a").first();
					String busLineName = tdURL.html();
					String busLineID = "";
					Matcher matcher = Pattern.compile("([0-9])+").matcher(
							tdURL.attr("href"));
					if (matcher.find()) {
						busLineID = matcher.group();
					}
					busLinePassages.setBusLineID(Integer.parseInt(busLineID));
					busLinePassages.setBusLineName(busLineName);
					isBusLineIDFound = true;
					continue;
				}

				// Look for "<i>*</i>" (aka "prodotto surgelato")
				boolean isFrozenProduct = !td.select("i").isEmpty();
				if (isFrozenProduct) {
					td.select("i").remove();
					tdContent = td.html();
				}

				busLinePassages.addTimePassage(tdContent, isFrozenProduct);
			}
			arrivalsAtBusStop.add(busLinePassages);
		}

		// Sucking bus stop infos
		String busStopInfo = null;
		Element tagStationInfo = doc.select("span").first();
		if (tagStationInfo == null) {
			Log.e("GTTSiteSucker", "Parse error: tagStationInfo is null!");
			return null;
		}
		busStopInfo = tagStationInfo.html();

		// Sucking busStopID (e.g.: 1254)
		Integer busStopID = null;
		Matcher matcherStationNumber = Pattern.compile("([0-9]+)").matcher(
				busStopInfo);
		if (matcherStationNumber.find()) {
			busStopID = Integer.parseInt(matcherStationNumber.group(1));
		}
		if(busStopID == null) {
			Log.e("GTTSiteSucker", "Parse error: busStopID is null!");
			return null;
		}

		// Sucking busStopName (e.g.: POZZO STRADA)
		String busStopName = null;
		Matcher matcherStationName = Pattern.compile("&nbsp;(.+)").matcher(
				busStopInfo);
		if (matcherStationName.find()) {
			busStopName = matcherStationName.group(1);
		}

		Log.d("GTTSiteSucker", "busStopInfo: " + busStopInfo);
		Log.d("GTTSiteSucker", "busStopID: " + busStopID);
		Log.d("GTTSiteSucker", "busStopName:" + busStopName);

		return new BusStop(busStopID, busStopName,
				(BusLinePassages[]) arrivalsAtBusStop
						.toArray(new BusLinePassages[] {}));
	}
}
