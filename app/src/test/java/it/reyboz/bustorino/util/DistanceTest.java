package it.reyboz.bustorino.util;

import android.location.Location;
import it.reyboz.bustorino.backend.utils;
import org.junit.Test;
import static org.junit.Assert.*;
public class DistanceTest {

    @Test
    public void testDistance(){
        double dist = utils.measuredistanceBetween(44.161957,8.302445, 44.645321, 7.656055);
        assertEquals(dist,74334.0, 0.05);
    }
    //@Test
    //this tests fails as distance compute by Location.distanceBetween is always zero
    public void testDistanceLocation(){
        float[] result = new float[1];
        float[] actualRes = new float[]{74333.9f}; // ,939.0f,0.01f};
        Location.distanceBetween(44.161957,8.302445, 44.645321, 7.656055, result);
        assertArrayEquals(actualRes, result, 0.01f);
    }
}
