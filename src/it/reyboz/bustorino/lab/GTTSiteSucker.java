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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
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
 * @author Valerio Bozzolan
 */
public class GTTSiteSucker {

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

		public String toString() {
			return time
					+ (isInRealTime ? "*" : " ");
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
        private String busLineUsername;
        private Integer busLineType;

		private ArrayList<TimePassage> timesPassages = new ArrayList<TimePassage>();

		public BusLine(Integer busLineID, String busLineName) {
			this.busLineID = busLineID;
			this.busLineName = busLineName;
		}

		public Integer getBusLineID() {
			return busLineID;
		}

		public void setBusLineID(Integer busLineID) {
			this.busLineID = busLineID;
		}

		public String getBusLineName() {
			return busLineName;
		}

		public void setBusLineName(String busLineName) {
			this.busLineName = busLineName;
		}

        public String getBusLineUsername() {
            return busLineUsername;
        }

        public void setBusLineUsername(String busLineUsername) {
            this.busLineUsername = busLineUsername;
        }

        public Integer getBusLineType() {
            return busLineType;
        }

        public void setBusLineType(Integer busLineType) {
            this.busLineType = busLineType;
        }

        public void addTimePassage(String time, boolean isInRealTime) {
			timesPassages.add(new TimePassage(time, isInRealTime));
		}

		public TimePassage[] getTimePassages() {
			return timesPassages.toArray(new TimePassage[timesPassages.size()]);
		}

		public String getTimePassagesString() {
			if (timesPassages.size() == 0) {
				return null;
			}
			String out = "";
			for (TimePassage timePassage : timesPassages) {
				if(! out.equals("")) {
					out += " ";
				}
				out += timePassage.toString();
			}
			return out;
		}

		public Time getFirstPassageComparableTime() {
			if(timesPassages == null || timesPassages.size() == 0) {
				return null;
			}
			return timesPassages.get(0).getComparableTime();
		}

		public String toString() {
			return busLineName + " (" + busLineID + ")";
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
		private String busStopID; // Es: 1254 NOT always Integer!!! Sometimes there is something like "STCAR" D:
		private String busStopName; // Es: MARCONI
        private String busStopUsername;
		private String busStopLocality; // Es: Torino
        private String latitude;
        private String longitude;
		private BusLine[] busLines;

		private Boolean isFavorite = null;

		public BusStop(String busStopID) {
			this.busStopID = busStopID;
		}

		public BusStop(String busStopID, String busStopName) {
			this.busStopID = busStopID;
			this.busStopName = busStopName;
		}

		public BusStop(String busStopID, String busStopName, BusLine[] busLines) {
			this.busStopID = busStopID;
			this.busStopName = busStopName;
			this.busLines = busLines;
		}

        public BusStop(String busStopID, String busStopName, String busStopLocality, BusLine[] busLines, String latitude, String longitude, Boolean isFavorite) {
            this.busStopID = busStopID;
            this.busStopName = busStopName;
			this.busStopLocality = busStopLocality;
            this.busLines = busLines;
            this.latitude = latitude;
            this.longitude = longitude;
			this.isFavorite = isFavorite;
        }

		public String getBusStopName() {
			return busStopName;
		}

        public String getBusStopUsername() {
            return busStopUsername;
        }

        public void setBusStopUsername(String busStopUsername) {
            this.busStopUsername = busStopUsername;
        }

        public String getBusStopLocality() {
			return busStopLocality;
		}

		public String getBusStopID() {
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

		public void setBusStopLocality(String busStopLocality) {
			this.busStopLocality = busStopLocality;
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

		public Boolean getIsFavorite() {
			return isFavorite;
		}

		public void setIsFavorite(Boolean isFavorite) {
			this.isFavorite = isFavorite;
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

		/**
		 * @return Bus line list
		 */
		public String toString() {
			String toString = busStopID;
			if(busStopName != null) {
				toString += " (" + busStopName + ")";
			}
			return toString;
		}
	}
}
