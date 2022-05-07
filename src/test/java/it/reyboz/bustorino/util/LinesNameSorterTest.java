package it.reyboz.bustorino.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class LinesNameSorterTest {



    @Test
    public void compare() {

        final LinesNameSorter sorter = new LinesNameSorter();


        assertTrue(sorter.compare("13N","130") < 0);

    }

    @Test
    public void lineeStrane() {

        final LinesNameSorter sorter = new LinesNameSorter();


        assertTrue(sorter.compare("3 "," 8") < 0);


    }
}