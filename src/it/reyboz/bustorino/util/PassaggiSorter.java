package it.reyboz.bustorino.util;

import java.util.Comparator;

import it.reyboz.bustorino.backend.Passaggio;

/**
 * Sorter of passaggi, giving the arrival times that are in real time first
 */
public class PassaggiSorter implements Comparator<Passaggio> {

    @Override
    public int compare(Passaggio p1, Passaggio p2) {
        if (p1.isInRealTime){
            if(p2.isInRealTime){
                //compare times
                return  p1.getMinutesDiff(p2);
            }
            else {
                return -2;
            }
        } else{
            if(p2.isInRealTime){
                // other should come first
                return 2;
            } else return p1.getMinutesDiff(p2);
        }
    }

    @Override
    public boolean equals(Object o) {
        boolean equal= this.equals(o);
        if (equal) return true;
        else{
            return o instanceof PassaggiSorter;
        }
    }
}
