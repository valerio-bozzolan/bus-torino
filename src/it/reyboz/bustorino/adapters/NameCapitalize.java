package it.reyboz.bustorino.adapters;

import java.util.Locale;

import it.reyboz.bustorino.backend.utils;

public enum NameCapitalize {
    DO_NOTHING, ALL, FIRST;
    public static NameCapitalize getCapitalize(String capitalize){

        switch (capitalize.trim()){
            case "KEEP":
                return NameCapitalize.DO_NOTHING;
            case "CAPITALIZE_ALL":
                return NameCapitalize.ALL;

            case "CAPITALIZE_FIRST":
                return NameCapitalize.FIRST;
        }
        return  NameCapitalize.DO_NOTHING;
    }

    /**
     * Parse the output
     * @param input the input string
     * @param capitalize the capitalize value
     * @return parsed string
     */
    public static String capitalizePass(String input, NameCapitalize capitalize){
        String dest = input;
        switch (capitalize){
            case ALL:
                dest = input.toUpperCase(Locale.ROOT);
                break;
            case FIRST:
                dest = utils.toTitleCase(input, true);
                break;
            case DO_NOTHING:
            default:

        }
        return dest;
    }
}
