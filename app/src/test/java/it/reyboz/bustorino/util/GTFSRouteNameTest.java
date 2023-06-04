package it.reyboz.bustorino.util;

import androidx.core.util.Pair;
import it.reyboz.bustorino.backend.ServiceType;
import it.reyboz.bustorino.backend.gtfs.GtfsUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class GTFSRouteNameTest {

    @Test
    public void testName1(){
        final String test="gtt:4108E";
        assertEquals(GtfsUtils.getRouteInfoFromGTFS(test), new Pair<>(ServiceType.EXTRAURBANO,"4108"));
    }

    @Test
    public void testName2(){
        final String test="gtt:63BU";
        assertEquals(GtfsUtils.getRouteInfoFromGTFS(test), new Pair<>(ServiceType.URBANO,"63B"));
    }

    @Test
    public void testName3(){
        final String test="63BU";
        assertEquals(GtfsUtils.getRouteInfoFromGTFS(test), new Pair<>(ServiceType.URBANO,"63B"));
    }
}
