/*
	BusTO (backend components)
    Copyright (C) 2016 Ludovico Pavesi

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

package it.reyboz.bustorino.backend;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

public class Route implements Comparable<Route> {
    final static int[] reduced_week = {Calendar.MONDAY,Calendar.TUESDAY,Calendar.WEDNESDAY,Calendar.THURSDAY,Calendar.FRIDAY};
    final static int[] feriali = {Calendar.MONDAY,Calendar.TUESDAY,Calendar.WEDNESDAY,Calendar.THURSDAY,Calendar.FRIDAY,Calendar.SATURDAY};
    final static int[] weekend = {Calendar.SUNDAY,Calendar.SATURDAY};
    private final static int BRANCHID_MISSING = -1;

    private final String name;
    public String destinazione;
    public final List<Passaggio> passaggi;
    //create a copy of the list, so that
    private List<Passaggio> sortedPassaggi;
    public final Type type;
    public String description;
    //ordered list of stops, from beginning to end of line
    private List<String> stopsList = null;
    public int branchid = BRANCHID_MISSING;
    public int[] serviceDays ={};
    //0=>feriale, 1=>festivo -2=>unknown
    public FestiveInfo festivo = FestiveInfo.UNKNOWN;
    private @Nullable String gtfsId;


    public enum Type { // "long distance" sono gli extraurbani.
        BUS(1), LONG_DISTANCE_BUS(2), METRO(3), RAILWAY(4), TRAM(5), UNKNOWN(-2);
        //TODO: decide to give some special parameter to each field
        private final int code;
        Type(int code){
            this.code = code;
        }
        public int getCode(){
            return this.code;
        }
        @Nullable
        public static Type fromCode(int i){
            switch (i){
                case 1:
                    return BUS;
                case 2:
                    return LONG_DISTANCE_BUS;
                case 3:
                    return METRO;
                case 4:
                    return RAILWAY;
                case 5:
                    return TRAM;
                case -2:
                    return UNKNOWN;
                default:
                    return null;
            }
        }
    }
    public enum FestiveInfo{
        FESTIVO(1),FERIALE(0),UNKNOWN(-2);

        private final int code;
        FestiveInfo(int code){
            this.code = code;
        }

        public int getCode() {
            return code;
        }
        public static FestiveInfo fromCode(int i){
            switch (i){
                case -2:
                    return UNKNOWN;
                case 0:
                    return FERIALE;
                case 1:
                    return FESTIVO;
                default:
                    return UNKNOWN;
            }
        }
    }
    /**
     * Constructor.
     *
     * @param name route ID
     * @param destinazione terminus\end of line
     * @param type bus, long distance bus, underground, and so on
     * @param passaggi timetable, a good choice is an ArrayList of size 6
     * @param description the description of the line, usually given by the FiveTAPIFetcher
     * @see Palina Palina.addRoute() method
     */
    public Route(String name, String destinazione, List<Passaggio> passaggi, Type type, String description) {
        this.name = name;
        this.destinazione = parseDestinazione(destinazione);
        this.passaggi = passaggi;
        this.type = type;
        this.description = description;
    }

    /**
     * Constructor used in GTTJSONFetcher, see above
     */
    public Route(String name, String destinazione, Type type, List<Passaggio> passaggi) {
        this(name,destinazione,passaggi,type,null);
    }

    /**
     * Constructor used by the FiveTAPIFetcher
     * @param name stop Name
     * @param t optional type
     * @param description line rough description
     */
    public Route(String name,Type t,String description){
        this(name,null,new ArrayList<>(),t,description);
    }
    /**
     * Constructor used by the FiveTAPIFetcher
     * @param name stop Name
     * @param t optional type
     * @param description line rough description
     */
    public Route(String name,String destinazione, String description, Type t){
        this(name,destinazione,new ArrayList<>(),t,description);
    }

    /**
     * Exactly what it says on the tin.
     *
     * @return times from the timetable
     */
    public List<Passaggio> getPassaggi() {
        return this.passaggi;
    }

    public void setStopsList(List<String> stopsList) {
        this.stopsList = Collections.unmodifiableList(stopsList);
    }
    public List<String> getStopsList(){
        return this.stopsList;
    }

    /**
     * Adds a time (passaggio) to the timetable for this route
     *
     * @param TimeGTT time in GTT format (e.g. "11:22*")
     */
     public void addPassaggio(String TimeGTT, Passaggio.Source source) {
         this.passaggi.add(new Passaggio(TimeGTT, source));
     }
     //Overloaded
     public void addPassaggio(int hour, int minutes, boolean realtime, Passaggio.Source source) {
         this.passaggi.add(new Passaggio(hour, minutes, realtime, source));
     }

    public static Route.Type getTypeFromSymbol(String route) {
        switch (route) {
            case "M":
                return Route.Type.METRO;
            case "T":
                return Route.Type.RAILWAY;
        }

        // default with case "B"
        return Route.Type.BUS;
    }

     private String parseDestinazione(String direzione){
         if(direzione==null) return null;
         //trial to add space to the parenthesis
         String[] exploded = direzione.split("\\(");
         if(exploded.length>1){
             StringBuilder sb = new StringBuilder();
             sb.append(exploded[0]);
             for(int i=1; i<exploded.length;i++) {
                 sb.append(" (");
                 sb.append(exploded[i]);
             }
             direzione = sb.toString();
         }
         return direzione;
     }

    /**
     * Getter for the name
     * @return the name of the line
     */
     public String getName() {
        return name;
     }

     public String getNameForDisplay(){
         if(name.trim().equals("101Metrobus")) return "101 Metrobus";
         else return name;
     }
     /**
     * Get all passaggi in a single string
     * @return the string
     */
     public String getPassaggiToString(){
         StringBuilder sb = new StringBuilder();
         for(Passaggio passaggio : passaggi) {
             // "+" calls concat() and some other stuff internally, this should be faster
             //StringBuilder is THE WAY
             sb.append(passaggio.toString());
             sb.append(" ");
         }
         return sb.toString();
     }

     public String getPassaggiToString(int start_idx, int number, boolean sort){
         StringBuilder sb = new StringBuilder();
         List<Passaggio> arrivals;
         int max;
         if(sort){
             if(sortedPassaggi==null){
                 sortedPassaggi = new ArrayList<>(passaggi.size());
                 sortedPassaggi.addAll(passaggi);
                 Collections.sort(sortedPassaggi);
             }
             arrivals = sortedPassaggi;
         } else  arrivals = passaggi;
         max = Math.min(start_idx + number, arrivals.size());
         for(int j= start_idx; j<max;j++) {
             // "+" calls concat() and some other stuff internally, this should be faster
             //StringBuilder is THE WAY
             sb.append(arrivals.get(j).toString());
             sb.append(" ");
         }
         return sb.toString();
     }

     public int numPassaggi(){
         if (passaggi==null)
             return 0;
         return passaggi.size();
     }
     public Passaggio.Source getPassaggiSource(){
         Passaggio.Source mSource = null;

         for(Passaggio pass: passaggi){
             if (mSource == null) {
                 mSource = pass.source;
             } else if (mSource != pass.source){
                 Log.w("BusTO-CheckPassaggi",
                         "Cannot determine the source for route "+this.name+", have got "+mSource +" so far, the next one is "+pass.source );
                 mSource = Passaggio.Source.UNDETERMINED;

                 break;
             }
         }
         if (mSource == null) mSource = Passaggio.Source.UNDETERMINED;
         return mSource;
     }


    @Override
    public int compareTo(@NonNull Route other) {
        int res;
        int thisAsInt, otherAsInt;

        // sorting by numbers alone yields a far more "natural" result (36N goes before 2024, 95B next to 95, and the like)

        thisAsInt = networkTools.failsafeParseInt(this.name.replaceAll("[^0-9]", ""));
        otherAsInt = networkTools.failsafeParseInt(other.name.replaceAll("[^0-9]", ""));

        // compare.

        // numeric route IDs (names)
        if(thisAsInt != 0 && otherAsInt != 0) {
            res = thisAsInt - otherAsInt;
            if(res != 0) {
                return res;
            }
        } else {
            // non-numeric
            res = this.name.compareTo(other.name);
            if (res != 0) {
                return res;
            }
            // compare gtfsID
            if (this.gtfsId != null && other.gtfsId!=null){
                res = this.gtfsId.compareTo(other.gtfsId);
                if (res!=0) return 0;
            }

        }

        // try comparing their destination
        if(this.destinazione!=null){
            res = this.destinazione.compareTo(other.destinazione);
            if(res != 0) {
                return res;
            }
        }

        //compare the lines
        if(this.stopsList!=null && other.stopsList!=null){
            int d = this.stopsList.size()-other.stopsList.size();
            if(d!=0) return d;
            //if we are here, the two routes have the same number of stops
        }
        // probably useless, but... last attempt.

        if(this.type != other.type) {
            // ordinal() is evil or whatever, who cares.
            return this.type.ordinal() - other.type.ordinal();
        }

        return 0;
    }

    @Nullable
    public String getGtfsId() {
        return gtfsId;
    }

    public void setGtfsId(@Nullable String gtfsId) {
         if (gtfsId==null) this.gtfsId = null;
         else
            this.gtfsId = gtfsId.trim();
    }

    public boolean isBranchIdValid(){
         return branchid!=BRANCHID_MISSING;
    }

    @Override
    public boolean equals(Object obj) {
         if(obj instanceof Route){
             Route r = (Route) obj;
             boolean result  = false;
             if(this.name.equals(r.name) && this.branchid == r.branchid){
                 if(description!=null && r.description!=null)
                     if(!description.trim().equals(r.description.trim()))
                         return false;

                 if(destinazione!=null && r.destinazione!=null){
                         if(!this.destinazione.trim().equals(r.destinazione.trim()))
                             // they are not the same
                             return false;
                 }
                 if(gtfsId!=null && r.gtfsId!=null && !(gtfsId.trim().equals(r.gtfsId.trim())))
                     return false;
                 //check stops list
                 if(this.stopsList!=null && r.stopsList!=null){
                     int sizeDiff = this.stopsList.size()-r.stopsList.size();
                     if(sizeDiff!=0) {
                         return false;

                     } else {
                         //check that the stops are the same
                         result = true;
                         for(int j=0; j<this.stopsList.size();j++){
                             if(!this.stopsList.get(j).equals(r.stopsList.get(j))) {
                                result = false;
                                break;
                             }
                        }
                         return result;
                     }
                 } else{
                     //no stopsList in one or the other
                     return true;
                 }
             }
             return result;

         } else return false;
    }

    /**
     * Merge informations from another route
     * NO CONSISTENCY CHECKS, DO BEFORE CALLING THIS METHOD
     * @param other the other route
     * @return true if there have been changes
     */
    public boolean mergeRouteWithAnother(Route other){
         boolean adjusted = false;
        if ((other.serviceDays!=null && this.serviceDays!=null && this.serviceDays.length==0)
                || (other.serviceDays!=null && this.serviceDays==null)) {
            this.serviceDays = other.serviceDays;
            adjusted = true;
        }
        if (other.getStopsList() != null && this.getStopsList() == null)
            this.setStopsList(other.getStopsList());

        if(this.passaggi!=null && other.passaggi!=null && other.passaggi.size()>0){
            this.passaggi.addAll(other.passaggi);
        }

        if(this.destinazione == null && other.destinazione!=null) {
            this.destinazione = other.destinazione;
            adjusted = true;
        }
        if(!this.isBranchIdValid() && other.isBranchIdValid()) {
            this.branchid = other.branchid;
            adjusted = true;
        }
        if(this.festivo == Route.FestiveInfo.UNKNOWN && other.festivo!= Route.FestiveInfo.UNKNOWN){
            this.festivo = other.festivo;
            adjusted = true;
        }
        if(other.description!=null && (this.description==null ||
                (this.festivo == FestiveInfo.FERIALE && this.description.contains("festivo")) ||
                (this.festivo == FestiveInfo.FESTIVO && this.description.contains("feriale")) )  ) {
            this.description = other.description;
        }

         return adjusted;
    }

}
