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


import java.util.Comparator;

public class LinesNameSorter implements Comparator<String> {
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
        }**/
        //One of them is not
        int num1 = -1;
        if(isInteger(name1)) num1 = Integer.parseInt(name1);
        int num2 = -1;
        if (isInteger(name2)) num2 = Integer.parseInt(name2);

        if(num1 >= 0 && num2 >=0){
            //we're very happy
            return (num1-num2)*10;
        } else if (num1>=0) {
            //name2 is not fully integer
            final String name2sub = name2.substring(0, name2.length()-1).trim();
            char lastchar = name2.charAt(name2.length()-1);
            if(isInteger(name2sub)){
                num2 = Integer.parseInt(name2sub);
                int diff = (num1-num2)*10;
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
                int diff = (num1-num2)*10;
                return diff + incrementFromLastChar(lastchar);
            } else {
                return name1.compareTo(name2);
            }
        }
        //last case
        return name1.compareTo(name2);

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
                return 6;
        }
    }
}
