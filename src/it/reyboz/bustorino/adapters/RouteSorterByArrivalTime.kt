package it.reyboz.bustorino.adapters

import it.reyboz.bustorino.backend.Route

class RouteSorterByArrivalTime : Comparator<Route> {

    override fun compare(route1: Route?, route2: Route?): Int {
        if (route1 == null){
            if(route2 == null) return 0
            else return 2;
        } else if (route2 == null){
            return -2;
        }
        val passaggi1 = route1.passaggi
        val passaggi2 = route2.passaggi
        // handle the case of midnight
        if (passaggi1 == null || passaggi1.size == 0){
            if (passaggi2 == null || passaggi2.size == 0) return 0
            else return 2
        } else if (passaggi2 == null || passaggi2.size == 0){
            return -2
        }
        passaggi1.sort()
        passaggi2.sort()

        return passaggi1[0].compareTo(passaggi2[0])
    }

}