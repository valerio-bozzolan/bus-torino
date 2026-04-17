/*
	BusTO (backend components)
    Copyright (C) 2016 Ludovico Pavesi
    Copyright (c) 2026 Fabio Mazza

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

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import it.reyboz.bustorino.util.LinesNameSorter;

/**
 * Timetable for multiple routes.<br>
 * <br>
 * Apparently "palina" and a bunch of other terms can't really be translated into English.<br>
 * Not in a way that makes sense and keeps the code readable, at least.
 */
public class Palina extends Stop implements Parcelable {
    private ArrayList<Route> routes = new ArrayList<>(); // the routes with arrival times
    private boolean routesModified = false;
    private Passaggio.Source allSource = null;

    public Palina(String stopID) {
        super(stopID);
    }

    public Palina(Stop s){
        super(s.ID,s.getStopDefaultName(),s.getStopUserName(),s.location,s.type,
                s.getRoutesThatStopHere(),s.getLatitude(),s.getLongitude(), s.gtfsID);
    }

    public Palina(@NonNull String ID, @Nullable String name, @Nullable String userName,
                  @Nullable String location,
                  @Nullable Double lat, @Nullable Double lon, @Nullable String gtfsID) {
        super(ID, name, userName, location, null, null, lat, lon, gtfsID);
    }

    public Palina(@Nullable String name, @NonNull String ID, @Nullable String location, @Nullable Route.Type type, @Nullable List<String> routesThatStopHere) {
        super(name, ID, location, type, routesThatStopHere);
    }

    /**
     * Adds a timetable entry to a route.
     *
     * @param TimeGTT time in GTT format (e.g. "11:22*")
     * @param arrayIndex position in the array for this route (returned by addRoute)
     */
    public void addPassaggio(String TimeGTT, Passaggio.Source src,int arrayIndex) {
        this.routes.get(arrayIndex).addPassaggio(TimeGTT,src);
        routesModified = true;
    }

    /**
     * Count routes with missing directions
     * @return number
     */
    public int countRoutesWithMissingDirections(){
        int i = 0;
        for (Route r : routes){
            if(r.destinazione==null||r.destinazione.equals(""))
                i++;
        }
        return i;
    }

    /**
     * Adds a route to the timetable.
     *
     * @param routeID name
     * @param type bus, underground, railway, ...
     * @param destinazione end of line\terminus (underground stations have the same ID for both directions)
     * @return array index for this route
     */
    public int addRoute(String routeID, String destinazione, Route.Type type) {
        return addRoute(new Route(routeID, destinazione, type, new ArrayList<>(6)));
    }
    public int addRoute(Route r){
        this.routes.add(r);
        routesModified = true;
        buildRoutesString();
        return this.routes.size()-1; // last inserted element and pray that direct access to ArrayList elements really is direct
    }

    public void setRoutes(List<Route> routeList){
        routes = new ArrayList<>(routeList);
    }

    /**
     * Remove all arrivals from this Palina
     */
    public void clearRoutes(){
        routes.clear();
    }

    /**
     * Check how many routes (from arrival times) we have
     * @return the number of routes
     */
    public int getNumRoutesWithArrivals(){
        return routes.size();
    }

    @Nullable
    @Override
    protected String buildRoutesString() {
        // no routes => no string
        if(routes == null || routes.size() == 0) {
            return "";
        }
        /*final StringBuilder sb = new StringBuilder();
        final LinesNameSorter nameSorter = new LinesNameSorter();
        Collections.sort(routes, (o1, o2) -> nameSorter.compare(o1.getName().trim(), o2.getName().trim()));
        int i, lenMinusOne = routes.size() - 1;

        for (i = 0; i < lenMinusOne; i++) {
            sb.append(routes.get(i).getName().trim()).append(", ");
        }
        // last one:
        sb.append(routes.get(i).getName());
        */
        ArrayList<String> names = new ArrayList<>();
        for (Route r: routes){
            names.add(r.getName());
        }
        final String routesThatStopHere = buildRoutesStringFromNames(names);
        setRoutesThatStopHereString(routesThatStopHere);
        return routesThatStopHereToString();
    }

    /**
     * Sort the names of the routes for the string "routes stopping here" and make the string
     * @param names of the Routes that pass in the stop
     * @return the full string of routes stopping (eg, "10, 13, 42" ecc)
     */
    public static String buildRoutesStringFromNames(List<String> names){
        final StringBuilder sb = new StringBuilder();
        final LinesNameSorter nameSorter = new LinesNameSorter();
        Collections.sort(names, nameSorter);
        int i, lenMinusOne = names.size() - 1;

        for (i = 0; i < lenMinusOne; i++) {
            sb.append(names.get(i).trim()).append(", ");
        }
        //last one
        sb.append(names.get(i).trim());
        return sb.toString();
    }


    protected void checkPassaggi(){
        Passaggio.Source mSource = null;
        for (Route r: routes){
            for(Passaggio pass: r.passaggi){
                if (mSource == null) {
                    mSource = pass.source;
                } else if (mSource != pass.source){
                    Log.w("BusTO-CheckPassaggi",
                            "Cannot determine the source, have got "+mSource +" so far, the next one is "+pass.source );
                    mSource = Passaggio.Source.UNDETERMINED;

                    break;
                }
            }
            if(mSource == Passaggio.Source.UNDETERMINED)
                break;
        }
        // if the Source is still null, set undetermined
        if (mSource == null) mSource = Passaggio.Source.UNDETERMINED;
        //finished with the check, setting flags
        routesModified = false;
        allSource = mSource;
    }

    @NonNull
    public Passaggio.Source getPassaggiSourceIfAny(){
        if(allSource==null || routesModified){
            checkPassaggi();
        }
        assert allSource != null;
        return allSource;
    }

    /**
     * Gets every route and its timetable.
     *
     * @return routes and timetables.
     */
    public List<Route> queryAllRoutes() {
        return this.routes;
    }

    public void sortRoutes() {
        Collections.sort(this.routes);
    }

    /**
     * Add info about the routes already found from another source
     * @param additionalRoutes ArrayList of routes to get the info from
     * @return the number of routes modified
     */
    public int addInfoFromRoutes(List<Route> additionalRoutes){
        if(routes == null || routes.size()==0) {
            this.routes = new ArrayList<>(additionalRoutes);
            buildRoutesString();
            return routes.size();
        }
        int count=0;
        final Calendar c = Calendar.getInstance();
        final int todaysInt = c.get(Calendar.DAY_OF_WEEK);
        for(Route r:routes) {
            int j = 0;
            boolean correct = false;
            Route selected = null;
            //TODO: rewrite this as a simple loop
            //MADNESS begins here
            while (!correct) {
                //find the correct route to merge to
                // scan routes and find the first which has the same name
                while (j < additionalRoutes.size() && !r.getName().equals(additionalRoutes.get(j).getName())) {
                    j++;
                }
                if (j == additionalRoutes.size()) break; //no match has been found
                //should have found the first occurrence of the line
                selected = additionalRoutes.get(j);
                //move forward
                j++;


                if (selected.serviceDays != null && selected.serviceDays.length > 0) {
                    //check if it is in service
                    for (int d : selected.serviceDays) {
                        if (d == todaysInt) {
                            correct = true;
                            break;
                        }
                    }
                } else if (r.festivo != null) {
                    switch (r.festivo) {
                        case FERIALE:
                            //Domenica = 1 --> Saturday=7
                            if (todaysInt <= 7 && todaysInt > 1) correct = true;
                            break;
                        case FESTIVO:
                            if (todaysInt == 1) correct = true; //TODO: implement way to recognize all holidays
                            break;
                        case UNKNOWN:
                            correct = true;
                    }
                } else {
                    //case a: there is no info because the line is always active
                    //case b: there is no info because the information is missing
                    correct = true;
                }
            }
            if (!correct || selected == null) {
                Log.w("Palina_mergeRoutes","Cannot match the route with name "+r.getName());
                continue; //we didn't find any match
            }
            //found the correct correspondance
            //MERGE INFO
            if(r.mergeRouteWithAnother(selected)) count++;
        }
        if (count> 0) buildRoutesString();
        return count;
    }

//    /**
//     * Route with terminus (destinazione) and timetables (passaggi), internal implementation.
//     *
//     * Contains mostly the same data as the Route public class, but methods are quite different and extending Route doesn't really work, here.
//     */
//    private final class RouteInternal {
//        public final String name;
//        public final String destinazione;
//        private boolean updated;
//        private List<Passaggio> passaggi;
//
//        /**
//         * Creates a new route and marks it as "updated", since it's new.
//         *
//         * @param routeID name
//         * @param destinazione end of line\terminus
//         */
//        public RouteInternal(String routeID, String destinazione) {
//            this.name = routeID;
//            this.destinazione = destinazione;
//            this.passaggi = new LinkedList<>();
//            this.updated = true;
//        }
//
//        /**
//         * Adds a time (passaggio) to the timetable for this route
//         *
//         * @param TimeGTT time in GTT format (e.g. "11:22*")
//         */
//        public void addPassaggio(String TimeGTT) {
//            this.passaggi.add(new Passaggio(TimeGTT));
//        }
//
//        /**
//         * Deletes al times (passaggi) from the timetable.
//         */
//        public void deletePassaggio() {
//            this.passaggi = new LinkedList<>();
//            this.updated = true;
//        }
//
//        /**
//         * Sets the "updated" flag to false.
//         *
//         * @return previous state
//         */
//        public boolean unupdateFlag() {
//            if(this.updated) {
//                this.updated = false;
//                return true;
//            } else {
//                return false;
//            }
//        }
//
//        /**
//         * Sets the "updated" flag to true.
//         *
//         * @return previous state
//         */
//        public boolean updateFlag() {
//            if(this.updated) {
//                return true;
//            } else {
//                this.updated = true;
//                return false;
//            }
//        }
//
//        /**
//         * Exactly what it says on the tin.
//         *
//         * @return times from the timetable
//         */
//        public List<Passaggio> getPassaggi() {
//            return this.passaggi;
//        }
//    }
    //remove duplicates

    public void mergeDuplicateRoutes(int startidx){
       //ArrayList<Route> routesCopy = new ArrayList<>(routes);
       //for
        if(routes.size()<=1|| startidx >= routes.size()) //we have finished
            return;
        Route routeCheck = routes.get(startidx);
        boolean found = false;
        for(int i=startidx+1; i<routes.size(); i++){
            final Route r = routes.get(i);
            if(routeCheck.equals(r)){
                //we have found a match, merge
                routes.remove(routeCheck);
                r.mergeRouteWithAnother(routeCheck);
                found=true;
                break;
            }
        }
        if (found) mergeDuplicateRoutes(startidx);
        else mergeDuplicateRoutes(startidx+1);
    }

    public int getTotalNumberOfPassages(){

        int tot = 0;
        if(routes==null)
            return tot;
        for(Route r: routes){
            tot += r.numPassaggi();
        }
        return tot;
    }

    /**
     * Compute the minimum number of passages per route
     * Ignoring empty routes
     * @return the minimum, or 0 if there are no passages/routes
     */
    public int getMinNumberOfPassages(){
        if (routes == null) return 0;

        int min = Integer.MAX_VALUE;
        if( routes.size() == 0) min = 0;
        else for (Route r : routes){
            if(r.numPassaggi()>0)
                min = Math.min(min,r.numPassaggi());
        }
        if (min == Integer.MAX_VALUE) return 0;
        else return min;
    }

    public ArrayList<String> getRoutesNamesWithNoPassages(){
        ArrayList<String> mList = new ArrayList<>();
        if(routes==null || routes.size() == 0){
            return mList;
        }
        for(Route r: routes){
            if(r.numPassaggi()==0)
                mList.add(r.getDisplayCode());
        }

        return  mList;
    }

    private static String pick(String a, String b) {
        return (a != null && !a.isEmpty()) ? a : b;
    }

    /**
     * Merge two Palinas, including information from both
     * @param p1 the first one, which has priority
     * @param p2 the second one
     * @return the merged Palina data
     */
    public static @Nullable Palina mergePaline(@Nullable Palina p1, @Nullable Palina p2) {
        if (p1 == null) return p2;
        if (p2 == null) return p1;

        // --- Campi base (Stop) ---
        String id = p1.ID; // assumiamo stesso ID

        String name = pick(p1.getStopDefaultName(), p2.getStopDefaultName());
        String userName = pick(p1.getStopUserName(), p2.getStopUserName());
        String location = pick(p1.location, p2.location);

        Double lat = p1.getLatitude() != null ? p1.getLatitude() : p2.getLatitude();
        Double lon = p1.getLongitude() != null ? p1.getLongitude() : p2.getLongitude();

        String gtfsID = pick(p1.gtfsID, p2.gtfsID);

        Palina result = new Palina(id, name, userName, location, lat, lon, gtfsID);

        // --- Routes ---
        List<Route> mergedRoutes = new ArrayList<>();
        boolean addFromSecond = false;

        if (p1.queryAllRoutes() != null)
            mergedRoutes.addAll(p1.routes);

        else if (p2.queryAllRoutes() != null)
            mergedRoutes.addAll(p2.routes);
        else {
            //assume the first one has more important imformation
            mergedRoutes.addAll(p1.routes);
            addFromSecond = true;

        }

        result.setRoutes(mergedRoutes);
        if(addFromSecond){
            result.addInfoFromRoutes(p2.routes);
        }

        // Unisci eventuali duplicati (stesso routeID)
        result.mergeDuplicateRoutes(0);

        // Aggiorna stringa routes
        result.buildRoutesString();

        return result;
    }

    /// ------- Parcelable stuff ---
    protected Palina(Parcel in) {
        super(in);
        routes = in.createTypedArrayList(Route.CREATOR);
        routesModified = in.readByte() != 0;
        allSource = in.readByte() == 0 ? null : Passaggio.Source.valueOf(in.readString());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeTypedList(routes);
        dest.writeByte((byte) (routesModified ? 1 : 0));
        if (allSource == null) {
            dest.writeByte((byte) 0);
        } else {
            dest.writeByte((byte) 1);
            dest.writeString(allSource.name());
        }
    }

    public static final Creator<Palina> CREATOR = new Creator<Palina>() {
        @Override
        public Palina createFromParcel(Parcel in) {
            return new Palina(in);
        }

        @Override
        public Palina[] newArray(int size) {
            return new Palina[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }
    // Methods using the parcelable
    public byte[] asByteArray(){
        final Parcel p = Parcel.obtain();
        writeToParcel(p,0);
        final byte[] b = p.marshall();
        p.recycle();
        return b;
    }

    public static Palina fromByteArray(byte[] data){
        final Parcel p = Parcel.obtain();
        p.unmarshall(data, 0, data.length);
        p.setDataPosition(0);
        final Palina palina = Palina.CREATOR.createFromParcel(p);
        p.recycle();
        return palina;
    }
}