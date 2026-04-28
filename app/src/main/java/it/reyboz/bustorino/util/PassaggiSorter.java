package it.reyboz.bustorino.util;

import java.util.Comparator;

import it.reyboz.bustorino.backend.Passaggio;

/**
 * Sorter of passaggi, giving the arrival times that are in real time first
 */
public class PassaggiSorter implements Comparator<Passaggio> {

    @Override
    public int compare(Passaggio p1, Passaggio p2) {
        if (p1.isInRealTime() != p2.isInRealTime()){
            if(p1.isInRealTime()) return -1;
            else return 1;
        }
        return p1.getArrivalTime().compareTo(p2.getArrivalTime());
    }

}
