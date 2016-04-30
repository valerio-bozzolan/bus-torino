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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Timetable for multiple routes.<br>
 * <br>
 * Apparently "palina" and a bunch of other terms can't really be translated into English.<br>
 * Not in a way that makes sense and keeps the code readable, at least.
 */
public class Palina { // TODO: extend Stop, maybe?
    //private final String stopID;
    private String stopName = "";
    private ArrayList<Route> routes = new ArrayList<>();

//    public Palina(String stopID) {
//        this.stopID = stopID;
//    }

    public final void setStopName(String name) {
        this.stopName = name;
    }

    public final String getStopName() {
        return stopName;
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
        this.routes.add(new Route(routeID, destinazione, type, new ArrayList<Passaggio>(6)));
        return this.routes.size() - 1; // last inserted element and pray that direct access to ArrayList elements really is direct
    }

    /**
     * Adds a timetable entry to a route.
     *
     * @param TimeGTT time in GTT format (e.g. "11:22*")
     * @param arrayIndex position in the array for this route (returned by addRoute)
     */
    public void addPassaggio(String TimeGTT, int arrayIndex) {
        this.routes.get(arrayIndex).addPassaggio(TimeGTT);
    }

//    /**
//     * Clears a route timetable (or creates an empty route) and returns its index
//     *
//     * @param routeID name
//     * @param destinazione end of line\terminus
//     * @return array index for this route
//     */
//    public int updateRoute(String routeID, String destinazione) {
//        int s = this.routes.size();
//        RouteInternal r;
//
//        for(int i = 0; i < s; i++) {
//            r = routes.get(i);
//            if(r.name.compareTo(routeID) == 0 && r.destinazione.compareTo(destinazione) == 0) {
//                // capire se è possibile che ci siano stessa linea e stessa destinazione su 2 righe diverse del sito e qui una sovrascrive l'altra (probabilmente no)
//                r.updateFlag();
//                r.deletePassaggio();
//                return i;
//            }
//        }
//
//        return this.addRoute(routeID, destinazione);
//    }
//
//    /**
//     * Deletes routes marked as "not updated" (= disappeared from the GTT website\API\whatever).
//     * Sets all remaining routes to "not updated" because that's how this contraption works.
//     */
//    public void finishUpdatingRoutes() {
//        RouteInternal r;
//
//        for(Iterator<RouteInternal> itr = this.routes.iterator(); itr.hasNext(); ) {
//            r = itr.next();
//            if(r.unupdateFlag()) {
//                itr.remove();
//            }
//        }
//    }

    /**
     * Gets the current timetable for a route. Returns null if the route doesn't exist.
     * This is slower than queryRouteByIndex.
     *
     * @return timetable (passaggi)
     */
    public List<Passaggio> queryRoute(String routeID) {
        for(Route r : this.routes) {
            if(routeID.equals(r.name)) {
                return r.getPassaggi();
            }
        }

        return null;
    }

    /**
     * Gets the current timetable for this route, from its index in the array.
     *
     * @return timetable (passaggi)
     */
    public List<Passaggio> queryRouteByIndex(int index) {
        return this.routes.get(index).getPassaggi();
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
}