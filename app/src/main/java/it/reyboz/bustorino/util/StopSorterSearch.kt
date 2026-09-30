package it.reyboz.bustorino.util

import it.reyboz.bustorino.backend.Stop

class StopSorterSearch(val searchById: Boolean): Comparator<Stop> {
    override fun compare(s0: Stop?, s1: Stop?): Int {
        if (s0 ==null) {
            if (s1 != null) return 1
            else return 0
        }
        else if(s1 == null){
            //s0 is not null
            return -1
        }
        if(s0.location==null){
            if(s1.location != null) return 1
        } else if(s1.location==null) return -1

        if(searchById){
            val lenDiff = s0.ID.length - s1.ID.length
            if(lenDiff!=0) return lenDiff
            //second criterion
            val diff = s0.ID.toInt() - s1.ID.toInt()
            //if(diff!=0) return diff
            return diff
        } else{
            return -1*(s0.numRoutesStopping - s1.numRoutesStopping)
        }
    }
}