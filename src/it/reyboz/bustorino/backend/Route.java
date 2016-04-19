/*
	BusTO (backend components)
    Copyright (C) 2016 Ludovico Pavesi

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

package it.reyboz.bustorino.backend;

import java.util.List;

public class Route {
    public final String name;
    public final String destinazione;
    public final List<Passaggio> passaggi;

    public Route(String name, String destinazione, List<Passaggio> passaggi) {
        this.name = name;
        this.destinazione = destinazione;
        this.passaggi = passaggi;
    }
}
