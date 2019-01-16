package it.reyboz.bustorino.util;

import android.location.Location;
import it.reyboz.bustorino.backend.Stop;

import java.util.Comparator;

public class StopSorterByDistance implements Comparator<Stop> {
    private final Location locToCompare;

    public StopSorterByDistance(Location locToCompare) {
        this.locToCompare = locToCompare;
    }

    @Override
    public int compare(Stop o1, Stop o2) {
        return (int) (o1.getDistanceFromLocation(locToCompare)-o2.getDistanceFromLocation(locToCompare));
    }
}
