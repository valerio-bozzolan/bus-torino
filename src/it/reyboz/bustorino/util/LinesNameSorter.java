package it.reyboz.bustorino.util;

import android.util.Log;
import it.reyboz.bustorino.backend.Route;

import java.util.Comparator;

public class LinesNameSorter implements Comparator<String> {
    @Override
    public int compare(String name1, String name2) {

        if(name1.length()>name2.length()) return 1;
        if(name1.length()==name2.length()) {
            try{
                int num1 = Integer.parseInt(name1.trim());
                int num2 = Integer.parseInt(name2.trim());
                return num1-num2;
            } catch (NumberFormatException  ex){
                //Log.d("BUSTO Compare lines","Cannot compare lines "+name1+" and "+name2);
                return name1.compareTo(name2);
            }
        }
        return -1;

    }
}
