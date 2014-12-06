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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class GTTSiteSucker {

	public static String arrivalTimesByLineQuery(String busStop) {
		return "http://www.5t.torino.it/5t/trasporto/arrival-times-byline.jsp?action=getTransitsByLine&shortName="
				+ busStop;
	}

	public static String busLinesByQuery(String busLineName)
			throws UnsupportedEncodingException {
		return "http://m.gtt.to.it/m/it/arrivi.jsp?n="
				+ URLEncoder.encode(busLineName, "UTF-8");
	}

	/**
	 * Helps comparing times
	 *
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
			if (hh == time.getHH()) {
				return mm >= time.getMM();
			} else if (hh > time.getHH() || hh == 0 && time.getHH() == 23) {
				return true;
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
			if (parts.length != 2) {
				return null;
			}
			try {
				hh = Integer.valueOf(parts[0]);
				mm = Integer.valueOf(parts[1]);
			} catch (NumberFormatException e) {
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
	public static class BusLine {

		private int busLineID;
		private String busLineName;

		private ArrayList<TimePassage> timesPassages;

		public BusLine() {
			timesPassages = new ArrayList<TimePassage>();
		}

		public void addTimePassage(String time, boolean isInRealTime) {
			timesPassages.add(new TimePassage(time, isInRealTime));
		}

		public ArrayList<TimePassage> getTimePassages() {
			return timesPassages;
		}

		public String getTimePassagesString() {
			if (timesPassages.size() == 0) {
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

		public int getBusLineID() {
			return busLineID;
		}

		public void setBusLineID(int busLineID) {
			this.busLineID = busLineID;
		}

		public String toString() {
			return getTimePassagesString();
		}

		public Time getFirstPassageComparableTime() {
			if (timesPassages.size() == 0) {
				return null;
			}
			return timesPassages.get(0).getComparableTime();
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
		private BusLine[] busLines;

		BusStop(Integer busStopID, String busStopName, BusLine[] busLines) {
			this.busStopID = busStopID;
			this.busStopName = busStopName;
			this.busLines = busLines;
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

		public BusLine[] getBusLines() {
			return busLines;
		}

		public void orderBusLinesByFirstArrival() {
			for (int i = 0; i < busLines.length - 1; i++) {
				for (int j = i + 1; j < busLines.length; j++) {
					BusLine a = busLines[i];
					BusLine b = busLines[j];
					Time aTime = a.getFirstPassageComparableTime();
					Time bTime = b.getFirstPassageComparableTime();
					if (aTime != null && bTime != null
							&& aTime.isMajorThan(bTime)) {
						BusLine tmp = a;
						busLines[i] = b;
						busLines[j] = tmp;
					}
				}
			}
		}
	}

	/**
	 * API/Workaround to get all informations from the 5T website Return a
	 * BusStop object. BusStop.busStopName can be null.
	 *
	 * @param html
	 * @author Valerio Bozzolan
	 * @return BusStop
	 */
	public static BusStop getBusStopSuckingHTML(SQLiteDatabase db, String html) {
		if (html == null) {
			return null;
		}

		ArrayList<BusLine> busLines = new ArrayList<BusLine>();
		Document doc = Jsoup.parse(html);

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
		if (busStopID == null) {
			Log.e("GTTSiteSucker", "Parse error: busStopID is null!");
			return null;
		}

		Elements trs = doc.getElementsByTag("tr");
		for (Element tr : trs) {
			BusLine busLine = new BusLine();

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
					busLine.setBusLineID(Integer.parseInt(busLineID));
					busLine.setBusLineName(busLineName);
					isBusLineIDFound = true;

					// Associate the bus line to the bus stop
					long inserted = MyDB.BusLine.addBusLine(db,
							busLine.getBusLineID(), busLine.getBusLineName());
					Log.d("GTTSiteSucker", "Last inserted busLineID: "
							+ inserted);
					MyDB.BusStopServeLine.addBusStopServeLine(db, busStopID,
							busLine.getBusLineID());

					continue;
				}

				// Look for "<i>*</i>" (aka "prodotto surgelato")
				boolean isFrozenProduct = !td.select("i").isEmpty();
				if (isFrozenProduct) {
					td.select("i").remove();
					tdContent = td.html();
				}

				busLine.addTimePassage(tdContent, isFrozenProduct);
			}
			busLines.add(busLine);
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
				(BusLine[]) busLines.toArray(new BusLine[] {}));
	}

	/**
	 * API/Workaround to get all informations from the 5T website Return a
	 * BusStop object. BusStop.busStopName can be null.
	 *
	 * @param html
	 * @author Valerio Bozzolan
	 * @return BusStop
	 */
	public static BusStop[] getBusStopsSuckingHTML(SQLiteDatabase db,
			String html) {
		if (html == null) {
			return null;
		}

		ArrayList<BusStop> busStops = new ArrayList<BusStop>();
		Document doc = Jsoup.parse(html);

		String title = doc.title();
		if (title.equals("&nbsp;Elenco fermate")) {
			// Find bus stops
			Elements lis = doc.getElementsByTag("li");
			for (Element li : lis) {
				String liContent = li.html();
				if (liContent.isEmpty()) {
					continue;
				}
				Element a;
				String href;
				try {
					a = li.getElementsByTag("a").first();
					href = a.attr("href");
				} catch (NullPointerException e) {
					continue;
				}

				// Sucking bus stop ID (e.g.: 1254)
				Integer busStopID = null;
				Matcher matcherStationNumber = Pattern.compile("([0-9]+)")
						.matcher(liContent);
				if (matcherStationNumber.find()) {
					busStopID = Integer.parseInt(matcherStationNumber.group(1));
				}
				if (busStopID == null) {
					Log.e("GTTSiteSucker", "Parse error: busStopID is null!");
					return null;
				}

				// Sucking bus stop name (e.g.: POZZO STRADA)
				String busStopName = null;
				Matcher matcherStationName = Pattern.compile(
						"[0-9]* - (.+)<br />").matcher(liContent);
				if (matcherStationName.find()) {
					busStopName = matcherStationName.group(1);
				}

				Log.d("GTTSiteSucker", "busStopID: " + busStopID);
				Log.d("GTTSiteSucker", "busStopName:" + busStopName);

				busStops.add(new BusStop(busStopID, busStopName, null));
			}
		} else if (title.equals("Arrivi in fermata")) {
			return null;
		} else {
			return null;
		}

		return busStops.toArray((new BusStop[] {}));
	}
}
