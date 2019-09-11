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
