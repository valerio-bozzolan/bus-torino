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
package it.reyboz.bustorino.lab;

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

/**
 * «BusTO, because sucking happens»
 * 
 * @author boz
 */
public class GTTSiteSucker {

	/**
	 * 
	 * @deprecated
	 */
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
	 * Execute regexes.
	 * 
	 * @param needle Regex
	 * @param haystack Entire string
	 * @return Matched string
	 */
	public static String grep(String needle, String haystack) {
		String matched = null;
		Matcher matcher = Pattern.compile(
				needle).matcher(haystack);
		if (matcher.find()) {
			matched = matcher.group(1);
		}
		return matched;
	}

	/**
	 * Javammerda! Lasciami null senza suicidarti!
	 */
	public static Integer string2Integer(String str) {
		try {
			return Integer.parseInt(str);
		} catch(NumberFormatException e) {
			return null;
		}
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

		private Integer busLineID;
		private String busLineName;

		private ArrayList<TimePassage> timesPassages;

		public BusLine() {
			this(null, null);
		}

		public BusLine(Integer busLineID) {
			this(busLineID, null);
		}

		public BusLine(String busLineName) {
			this(null, busLineName);
		}

		public BusLine(Integer busLineID, String busLineName) {
			this.busLineID = busLineID;
			this.busLineName = busLineName;
			timesPassages = new ArrayList<TimePassage>();
		}

		public void addTimePassage(String time, boolean isInRealTime) {
			timesPassages.add(new TimePassage(time, isInRealTime));
		}

		public ArrayList<TimePassage> getTimePassages() {
			return timesPassages;
		}

		public String getTimePassagesString() {
			if (timesPassages == null || timesPassages.size() == 0) {
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

		public Integer getBusLineID() {
			return busLineID;
		}

		public void setBusLineID(Integer busLineID) {
			this.busLineID = busLineID;
		}

		public String toString() {
			return getTimePassagesString();
		}

		public Time getFirstPassageComparableTime() {
			if(timesPassages == null || timesPassages.size() == 0) {
				return null;
			}
			return timesPassages.get(0).getComparableTime();
		}

		/**
		 * Help sorting
		 *
		 * @param busLineName
		 * @return boolean -1/0/1 if this is minor/equal/major than busLineName arg (First 0-9, than A-Z)
		 */
		public boolean isMajorThan(String busLineName) {
			final String regex = "^([0-9]+)";
			Integer nThis = string2Integer(grep(regex, this.busLineName));
			Integer nOther = string2Integer(grep(regex, busLineName));
			//Log.d("GTTSiteSucker", "\"" + this.busLineName + "\" (" + nThis + ") | \"" + busLineName + "\" (" + nOther + ")");
			if(nThis != null) {
				if(nOther != null) {
					int res = nThis.compareTo(nOther);
					if(res != 0) {
						return res > 0; // 0-9 <=> 0-9
					}
				}
				return false; // 0-9 < A-Z 
			}
			if(nOther != null) {
				return true; // A-Z > 0-9
			}
			return this.busLineName.compareTo(busLineName) > 0; // A-Z <=> A-Z || 0-9 == 0-9
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
        private String latitude;
        private String longitude;
		private BusLine[] busLines;

		BusStop(Integer busStopID, String busStopName, BusLine[] busLines) {
			this(busStopID, busStopName, busLines, null, null);
		}

        BusStop(Integer busStopID, String busStopName, BusLine[] busLines, String latitude, String longitude) {
            this.busStopID = busStopID;
            this.busStopName = busStopName;
            this.busLines = busLines;
            this.latitude = latitude;
            this.longitude = longitude;
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

		public void orderBusLinesByName() {
			for (int i = 0; i < busLines.length - 1; i++) {
				for (int j = i + 1; j < busLines.length; j++) {
					BusLine a = busLines[i];
					BusLine b = busLines[j];
					// Log.d("GTTSiteSucker", "Comparing " + a.getBusLineName() + (a.isMajorThan(b.getBusLineName()) ? " major than " : " minor or equal than ") + b.getBusLineName());
					if (a.isMajorThan(b.getBusLineName())) {
						BusLine tmp = a;
						busLines[i] = b;
						busLines[j] = tmp;
					}
				}
			}
		}

        public void setCoordinate(String latitude, String longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getLatitude() {
            return latitude;
        }

        public String getLongitude() {
            return longitude;
        }

		/**
		 * @return Bus line list
		 */
		public String toString() {
			String busLineNames = "";
			for(int i=0; i<busLines.length; i++) {
				if (busLineNames != "") {
					busLineNames += "\n";
				}
				busLineNames += busLines[i].getBusLineName();
			}
			return busLineNames;
		}
	}

	/**
	 * API/Workaround to get all informations from the 5T website.
	 * Return a BusStop array.
	 * 
	 * @param html DOM of busLinesByQuery
	 * @author Valerio Bozzolan
	 * @return BusStop[] Bus stops
	 */
	public static BusStop[] getBusStopsSuckingHTML(SQLiteDatabase db,
			String html) {
		if (html == null) {
			return null;
		}

		ArrayList<BusStop> busStops = new ArrayList<BusStop>();

		Document doc = Jsoup.parse(html);
		String title = doc.title().trim();
		if (title.substring(1).equals("Elenco fermate")) {

			// Find bus stops
			Elements lis = doc.getElementsByTag("li");
			for (Element li : lis) {
				String liContent = li.html();
				if (liContent.isEmpty()) {
					continue;
				}

				// Sucking bus stop ID (e.g.: 1254)
				String href;
				try {
					href = li.getElementsByTag("a").first().attr("href");
				} catch (NullPointerException e) {
					Log.e("GTTSiteSucker",
							"Parse error: busStopID can't be found!");
					continue;
				}
				Integer busStopID = string2Integer(grep("([0-9]+)", href));
				if (busStopID == null) {
					Log.e("GTTSiteSucker", "Parse error: busStopID is null!");
					continue;
				}

				// Sucking bus stop name (e.g.: POZZO STRADA)
				String busStopName = grep("- (.+)<br", liContent);
				if(busStopName == null) {
					Log.e("GTTSiteSucker", "Parse error: busStopName is null!");
					continue;
				}

				// Sucking bus lines
				ArrayList<BusLine> busLines = new ArrayList<BusLine>();
				Element span = null;
				try {
					span = li.getElementsByTag("span").last();
				} catch (NullPointerException e) {
					Log.d("GTTSiteSucker", "Can't retrieve span element");
				}
				if(span == null) {
					Log.d("GTTSiteSucker", "Span element is null!" + li.html());
					continue;
				}
				String spanContent = grep("Linee: (.+)", span.html());
				if (spanContent != null) {
					String[] busLineNames = spanContent.split(", ");
					for (int i = 0; i < busLineNames.length; i++) {
						busLines.add(new BusLine(busLineNames[i]));
					}
				}

				Log.d("GTTSiteSucker", "busStopID: " + busStopID);
				Log.d("GTTSiteSucker", "busStopName:" + busStopName);
				Log.d("GTTSiteSucker", "busLines: " + busLines.size());

				busStops.add(new BusStop(busStopID, busStopName, busLines
						.toArray((new BusLine[] {}))));
			}
		} else if (title.equals("Arrivi in fermata")) {
			// Find bus lines

			String p = null;
			try {
				p = doc.getElementsByTag("p").first().html();
			} catch (NullPointerException e) {
				Log.e("GTTSiteSucker",
						"Parse error: busStopID and busStopName can't be found!");
			}
			if(p == null) {
				return null;
			}

			// Sucking bus stop ID (e.g. 1254)
			Integer busStopID = string2Integer(grep("([0-9]+) -", p)); // arrivi.jsp?n=(1254)
			if (busStopID == null) {
				Log.e("GTTSiteSucker", "Parse error: busStopID is null: " + p);
				return null;
			}

			// Sucking bus stop name (e.g.: POZZO STRADA)
			String busStopName = grep("- (.+)<br", p);
			if(busStopName == null) {
				Log.e("GTTSiteSucker", "Parse error: busStopName is null!");
				return null;
			}
			
			// Sucking bus lines
			ArrayList<BusLine> busLines = new ArrayList<BusLine>();
			Elements lis = doc.getElementsByTag("li");
			for (Element li : lis) {
				String liContent = li.html();
				if (liContent.isEmpty()) {
					continue;
				}

				// Sucking bus line name (e.g.: 17 /)
				String h3 = null;
				try {
					h3 = li.getElementsByTag("h3").first().html();
				} catch (NullPointerException e) {
					Log.e("GTTSiteSucker",
							"Parse error: busLineName can't be found!");
				}
				if(h3 == null) {
					continue;
				}
				String busLineName = grep("Linea&nbsp;(.+)", h3);
				if(busLineName == null) {
					Log.e("GTTSiteSucker", "Parse error: busLineName is null: " + h3);
					continue;
				}

				BusLine busLine = new BusLine(busLineName);

				// Sucking bus passages
				Elements spans = li.parent().select("li > span");
				for (Element span : spans) {
					String spanContent = span.html();
					if (spanContent.isEmpty()) {
						continue;
					}

					String busLinePassage = grep("([0-9]+:[0-9]+)", spanContent);
					if(busLinePassage == null) {
						Log.e("GTTSiteSucker", "Parse error: busLinePassage is null: " + spanContent);
						continue;
					}

					String busLinePassageInRealTime = grep("(span)", spanContent);
					busLine.addTimePassage(busLinePassage, busLinePassageInRealTime == null ? false : true);
				}

				busLines.add(busLine);
			}

			return new BusStop[] {new BusStop(busStopID, busStopName, busLines
					.toArray((new BusLine[] {})))};
		} else {
			Log.e("GTTSiteSucker", "Title does not recognized: «" + title + "»");
			return null;
		}

		return busStops.toArray((new BusStop[] {}));
	}

    /**
     * Return geographical infos about a bus stop
     * @return BusStop
     */
    public BusStop getBusStopInfos(SQLiteDatabase db,
                                   String json) {
        // Yes, it's JSON, but I don't want to import org.json

        String line = null;
        while((line=bufReader.readLine()) != null) {
            Integer busStopID = String2Integer(grep("shortName[\s]*:[\s]*\"([0-9]+)\"", line));

            if(line=bufReader.readLine()) == null) {
                return false;
            }

            String busStopName = grep("name[\s]*:[\s]*\"(.+)\"", line);

            if(line=bufReader.readLine()) == null) {
                return false;
            }

            String latitude = grep("lat[\s]*:[\s]*(.+),", line);

            if(line=bufReader.readLine()) == null) {
                return false;
            }

            String longitude = grep("lon[\s]*:[\s]*(.+),", line);

            return new BusStop(busStopID, busStopName, null, latitude, longitude);
        }
    }
}
