package it.reyboz.bustorino.util;

import it.reyboz.bustorino.backend.utils;
import org.junit.Test;
import static org.junit.Assert.*;
public class DistanceTest {

    @Test
    public void testDistance(){
        double dist = utils.measuredistanceBetween(44.161957,8.302445, 44.645321, 7.656055);
        assertEquals(dist,74333.9, 0.05);
    }
}
