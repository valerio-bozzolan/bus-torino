package it.reyboz.bustorino.util;

import it.reyboz.bustorino.backend.Passaggio;
import org.junit.Test;
import static org.junit.Assert.*;
public class ArrivalTimesTest {

    @Test
    public void arrivalTimesTest(){
        Passaggio pass1 = Passaggio.newInstance(20,12,true, Passaggio.Source.GTTJSON,null);

        Passaggio pass2 = Passaggio.newInstance(1,12,true, Passaggio.Source.GTTJSON,null);

        assertNotNull(pass1);
        assertNotNull(pass2);
        assertTrue(pass1.compareTo(pass2) < 0);
    }

    @Test
    public void arrivalTimesWithTimeGTT(){
        Passaggio pass1 = Passaggio.newInstance("23:10*", Passaggio.Source.GTTJSON);

        Passaggio pass2 = Passaggio.newInstance(1,12,true, Passaggio.Source.GTTJSON,null);

        assertNotNull(pass1);
        assertNotNull(pass2);
        assertTrue(pass2.getDifferenceMinutes(pass1) > 0);
    }
}
