/*
	BusTO (util)
    Copyright (C) 2019 Fabio Mazza

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.reyboz.bustorino.util;


import android.util.Log;
import androidx.annotation.NonNull;

import java.util.Comparator;

public class LinesNameSorter implements Comparator<String> {
    final static private int cc = 100;
    final static private int ERROR_PARSING = -10;
    final static private int FIRST_1_LETTER = 120;
    final static private int FIRST_2_LETTERS=220;
    @Override
    public int compare(String name1, String name2) {
        name1 = name1.trim();
        name2 = name2.trim();

        /*
        if(name1.length()>name2.length()) return 1;
        if(name1.length()==name2.length()) {


            try{
                int num1 = Integer.parseInt(name1.trim());
                int num2 = Integer.parseInt(name2.trim());
                return num1-num2;
            } catch (NumberFormatException  ex){
                //Log.d("BUSTO Compare lines","Cannot compare lines "+name1+" and "+name2);
                //return name1.compareTo(name2);
                //One of them is not a line
                String trim1 = name1.substring(0, name1.length() - 1).trim();
                String trim2 = name2.substring(0, name2.length()-1).trim();
                if(isInteger(trim1)){ //cut away the last part
                    //this means it's a line
                    return compare(trim1, name2);
                } else if(isInteger(trim2)){
                    return compare(name1,trim2);
                }
                return name1.compareTo(name2);
            }
        }*
        //One of them is not
        int num1 = -1;
        if(isInteger(name1)) num1 = Integer.parseInt(name1);
        int num2 = -1;
        if (isInteger(name2)) num2 = Integer.parseInt(name2);

        if(num1 >= 0 && num2 >=0){
            //we're very happy
            return (num1-num2)*cc;
        } else if (num1>=0) {
            //name2 is not fully integer
            if(name2.contains(" ")){
                final String[] allStr = name2.split(" ");
                if(isInteger(allStr[0])) {
                    return (num1-Integer.parseInt(allStr[0]))*cc - incrementFromLastChar(allStr[1].trim().charAt(0));
                }
                //sennò si fa come sotto
            }
            final String name2sub = name2.substring(0, name2.length()-1).trim();
            char lastchar = name2.charAt(name2.length()-1);
            if(isInteger(name2sub)){
                num2 = Integer.parseInt(name2sub);
                int diff = (num1-num2)*cc;
                return diff - incrementFromLastChar(lastchar);
            } else{
                //failed
                return name1.compareTo(name2);
            }
        } else if (num2>=0) {
            //name1 is not fully integer
            final String name1sub = name1.substring(0, name1.length()-1).trim();
            char lastchar = name1.charAt(name1.length()-1);
            if (isInteger(name1sub)){
                num1 = Integer.parseInt(name1sub);
                int diff = (num1-num2)*cc;
                return diff + incrementFromLastChar(lastchar);
            } else {
                return name1.compareTo(name2);
            }
        }
        //last case
        return name1.compareTo(name2);
        **/
        //DO ALL CASES
        final CompareHolder c1 = getValueOfComplexName(name1);
        final CompareHolder c2 = getValueOfComplexName(name2);
        if (c1.value != ERROR_PARSING && c2.value != ERROR_PARSING){

                return (c1.value-c2.value)*100+c1.extra.compareTo(c2.extra);
            } else {
            if(c2.value== ERROR_PARSING && c1.value==ERROR_PARSING){
                return c1.extra.compareTo(c2.extra);
            }
            else if(c1.value == ERROR_PARSING){
                return 1;
            }
            else {
                return -1;
            }
                //Log.e("BusTo-Parsing","Error with the string");
                //throw new IllegalArgumentException("Error with the string name parsing");
            }

    }

    private static CompareHolder getValueOfComplexName(String name){
        String namec = name.trim();

        if (isInteger(namec)) return new CompareHolder(Integer.parseInt(namec),"");

        //check for the first part
        if(namec.contains(" ")){
            final String[] allStr = namec.split(" ");
            if(isInteger(allStr[0])) {
                int g = Integer.parseInt(allStr[0]);
                return new CompareHolder(g, allStr[1]);
            }
            else return new CompareHolder(-7, namec);
        }else {
            final String name1sub = namec.substring(0, namec.length()-1).trim();
            String lastPart = namec.substring(namec.length()-1);
            int g;
            if (isInteger(name1sub)) {
                return new CompareHolder(Integer.parseInt(name1sub), lastPart);
            } else if(name1sub.equals("M1")){
                return new CompareHolder(-1, lastPart);
            } else {
                //check NightBuster (X+name)
                if(isInteger(namec.substring(1))){
                    g = Integer.parseInt((namec.substring(1)));
                    return new CompareHolder(FIRST_1_LETTER, namec);
                } else if (isInteger(namec.substring(2))) {
                    return new CompareHolder(FIRST_2_LETTERS, namec);
                }
                return new CompareHolder(ERROR_PARSING, namec);
            }
        }
    }

    private static class CompareHolder {
        int value;
        String extra;

        public CompareHolder(int value,@NonNull String extra) {
            this.value = value;
            this.extra = extra;
        }
    }
    public static boolean isInteger(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            int d = Integer.parseInt(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }
    private static int incrementFromLastChar(char lastchar){
        switch (lastchar){
            case 'B':
            case 'b':
            case '/':
                return 1;
            case 'n':
            case 'N':
                return 3;
            default:
                return 1;
        }
    }
}
