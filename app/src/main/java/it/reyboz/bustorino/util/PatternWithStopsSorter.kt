package it.reyboz.bustorino.util

import it.reyboz.bustorino.data.gtfs.MatoPatternWithStops

/**
 * Sorter for the patterns, which takes into account direction and length of pattern
 */
class PatternWithStopsSorter: Comparator<MatoPatternWithStops> {
    override fun compare(p0: MatoPatternWithStops?, p1: MatoPatternWithStops?): Int {
        if (p0 != null && p1!=null) {
            if(p0.pattern.directionId != p1.pattern.directionId){
                return p0.pattern.directionId - p1.pattern.directionId
            }
            val g =  -1*(p0.stopsIndices.size - p1.stopsIndices.size)
            if(g!=0)
                return g;
            else return p0.pattern.code.compareTo(p1.pattern.code)
        }
        else return 0;
    }
}