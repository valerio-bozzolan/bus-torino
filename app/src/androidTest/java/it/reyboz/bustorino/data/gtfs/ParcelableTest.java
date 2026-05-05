package it.reyboz.bustorino.data.gtfs;

import android.os.Parcel;
import it.reyboz.bustorino.backend.Palina;
import it.reyboz.bustorino.backend.Passaggio;
import it.reyboz.bustorino.backend.Route;
import it.reyboz.bustorino.backend.Stop;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class ParcelableTest {

    @Test
    public void testPalinaParcelableTransfer() {

        Palina p = new Palina("32", "TestPalina", "myname", "Via madre di dio", 9.211,-8.92, "gtt:none");
        ArrayList<Passaggio> pass1,pass2;
        pass1 = new ArrayList<>();
        pass2 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            pass1.add(Passaggio.newInstance(20, 9+5*i,true, Passaggio.Source.MatoAPI, 0));
            pass1.add(Passaggio.newInstance(20, 9+7*i,true, Passaggio.Source.MatoAPI, 0));
        }
        Route r;
        r= new Route("Mas","destinazione",pass1,Route.Type.BUS,"maiam");
        p.addRoute(r);
        r = new Route("18","Max",pass2,Route.Type.TRAM,"descr");
        p.addRoute(r);

        Parcel parcel = Parcel.obtain();
        p.writeToParcel(parcel, p.describeContents());

        parcel.setDataPosition(0);

        Palina p2 =  Palina.CREATOR.createFromParcel(parcel);

        parcel.recycle();

        assertEquals(p2.ID, p.ID);
        assertEquals(p2.gtfsID, p.gtfsID);
        assertEquals(p.location, p2.location);
        assertEquals("Via madre di dio", p.location);
        assertEquals(p.type, p2.type);
        assertEquals(p.getLatitude(),p2.getLatitude());
        assertEquals(p.getLongitude(),p2.getLongitude());
        assertEquals(p.getTotalNumberOfPassages(),p2.getTotalNumberOfPassages());
        List<Route> rl1, rl2;
        rl1 = p.queryAllRoutes();
        rl2 = p.queryAllRoutes();
        assertEquals(rl1.size(), rl2.size());
        Route x,y;
        for (int i = 0; i < rl1.size(); i++) {
            x = rl1.get(i);
            y = rl2.get(i);
            assertEquals(x.destinazione,y.destinazione);
            assertEquals(x.type,y.type);
            assertEquals(x.description,y.description);
            assertEquals(x.getPassaggiToString(),y.getPassaggiToString());
        }
    }
}
