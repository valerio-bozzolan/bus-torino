/*
	BusTO (backend components)
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
package it.reyboz.bustorino.backend;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.reyboz.bustorino.backend.mato.MatoAPIFetcher;
import it.reyboz.bustorino.fragments.SettingsFragment;

public abstract class utils {
    private static final double EARTH_RADIUS = 6371.009e3;


    public static Double measuredistanceBetween(double lat1,double long1,double lat2,double long2){
        final double phi1 = Math.toRadians(lat1);
        final double phi2 = Math.toRadians(lat2);

        final double deltaPhi = Math.toRadians(lat2-lat1);
        final double deltaTheta = Math.toRadians(long2-long1);

        final double a = Math.sin(deltaPhi/2)*Math.sin(deltaPhi/2)+
                Math.cos(phi1)*Math.cos(phi2)*Math.sin(deltaTheta/2)*Math.sin(deltaTheta/2);
        final double c = 2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));

        return Math.abs(EARTH_RADIUS *c);

    }
    public static Double angleRawDifferenceFromMeters(double distanceInMeters){
         return Math.toDegrees(distanceInMeters/ EARTH_RADIUS);
    }

    public static int convertDipToPixelsInt(Context con,double dips)
    {
        return (int) (dips * con.getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Convert distance in meters on Earth in degrees of latitude, keeping the same longitude
     * @param distanceMeters distance in meters
     * @return angle in degrees
     */
    public static Double latitudeDelta(Double distanceMeters){
        final double angleRad =  distanceMeters/EARTH_RADIUS;
        return Math.toDegrees(angleRad);
    }

    /**
     * Convert distance in meters on Earth in degrees of longitude, keeping the same latitude
     * @param distanceMeters distance in meters
     * @param latitude the latitude that is fixed
     * @return angle in degrees
     */
    public static Double longitudeDelta(Double distanceMeters, Double latitude){
        final double theta = Math.toRadians(latitude);
        final double denom = Math.abs(Math.cos(theta));
        final double angleRad =  2*Math.asin(Math.sin(distanceMeters / EARTH_RADIUS) / denom);
        return Math.toDegrees(angleRad);
    }

    public static float convertDipToPixels(Context con, float dp){
        return convertDipToPixels(con.getResources(), dp);
    }
    public static float convertDipToPixels(Resources res, float dp){
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,res.getDisplayMetrics());
    }

    /*
    public static int calculateNumColumnsFromSize(View containerView, int pixelsize){
        int width = containerView.getWidth();
        float ncols = ((float)width)/pixelsize;
        return (int) Math.floor(ncols);
    }
     */

    /**
     * Check if there is an internet connection
     * @param con context object to get the system service
     * @return true if we are
     */
    public static boolean isConnected(Context con) {
        ConnectivityManager connMgr = (ConnectivityManager) con.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }


    ///////////////////// INTENT HELPER ////////////////////////////////////////////////////////////

    /**
     * Try to extract the bus stop ID from a URi
     *
     * @param uri The URL
     * @return bus stop ID or null
     */
    public static String getBusStopIDFromUri(Uri uri) {
        String busStopID;

        // everithing catches fire when passing null to a switch.
        String host = uri.getHost();
        if (host == null) {
            Log.e("ActivityMain", "Not an URL: " + uri);
            return null;
        }

        switch (host) {
            case "m.gtt.to.it":
                // http://m.gtt.to.it/m/it/arrivi.jsp?n=1254
                busStopID = uri.getQueryParameter("n");
                if (busStopID == null) {
                    Log.e("ActivityMain", "Expected ?n from: " + uri);
                }
                break;
            case "www.gtt.to.it":
            case "gtt.to.it":
                // http://www.gtt.to.it/cms/percorari/arrivi?palina=1254
                busStopID = uri.getQueryParameter("palina");
                if (busStopID == null) {
                    Log.e("ActivityMain", "Expected ?palina from: " + uri);
                }
                break;
            default:
                Log.e("ActivityMain", "Unexpected intent URL: " + uri);
                busStopID = null;
        }
        return busStopID;
    }
    final static Pattern ROMAN_PATTERN =  Pattern.compile(
            "^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$");
    private static boolean isRomanNumber(String str){
        if(str.isEmpty()) return false;
        final Matcher matcher = ROMAN_PATTERN.matcher(str);
        return matcher.find();
    }

    public static String toTitleCase(String givenString, boolean lowercaseRest) {
        String[] arr = givenString.trim().split(" ");
        StringBuilder sb = new StringBuilder();
        //Log.d("BusTO chars", "String parsing: "+givenString+" in array: "+ Arrays.toString(arr));
        for (String s : arr) {
            if (s.length() > 0) {
                String[] allsubs = s.split("\\.");

                boolean addPoint = s.contains(".");
                /*if (s.contains(".lli")|| s.contains(".LLI")) //Fratelli
                {
                DOESN'T ALWAYS WORK
                    addPoint = false;
                    allsubs = new String[]{s};
                }*/
                boolean first = true;
                for (String subs : allsubs) {
                    if(first) first=false;
                    else {
                        if (addPoint) sb.append(".");
                        sb.append(" ");
                    }
                    if(isRomanNumber(subs)){
                        //add and skip the rest
                        sb.append(subs);
                        continue;
                    }
                    //SPLIT ON ', check if contains "D'"
                    if(subs.toLowerCase(Locale.ROOT).startsWith("d'")){
                        sb.append("D'");
                        subs = subs.substring(2);
                    }
                    int index = 0;
                    char c = subs.charAt(index);
                    if(subs.length() > 1 && c=='('){
                        sb.append(c);
                        index += 1;
                        c = subs.charAt(index);
                    }
                    sb.append(Character.toUpperCase(c));
                    if (lowercaseRest)
                        sb.append(subs.substring(index+1).toLowerCase(Locale.ROOT));
                    else
                        sb.append(subs.substring(index+1));

                }
                if(addPoint && allsubs.length == 1) sb.append('.');
                sb.append(" ");
                /*sb.append(Character.toUpperCase(arr[i].charAt(0)));
                if (lowercaseRest)
                    sb.append(arr[i].substring(1).toLowerCase(Locale.ROOT));
                else
                    sb.append(arr[i].substring(1));
                sb.append(" ");

                 */
            } else sb.append(s);
        }
        return sb.toString().trim();
    }


    /**
     * Open an URL in the default browser.
     *
     * @param url URL
     */
    public static void openIceweasel(String url, Context context) {
        Intent browserIntent1 = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (browserIntent1.resolveActivity(context.getPackageManager()) != null) {
            //check we have an activity ready to receive intents (otherwise, there will be a crash)
            context.startActivity(browserIntent1);
        } else{
            Log.e("BusTO","openIceweasel can't find a browser");
        }
    }

    /**
     * Get the default list of fetchers for arrival times
     * @return array of ArrivalsFetchers to use
     */
    public static ArrivalsFetcher[] getDefaultArrivalsFetchers(){
        return  new ArrivalsFetcher[]{  new MatoAPIFetcher(),
                 new GTTJSONFetcher(), new FiveTScraperFetcher()};
    }
    /**
     * Get the default list of fetchers for arrival times
     * @return array of ArrivalsFetchers to use
     */
    public static List<ArrivalsFetcher> getDefaultArrivalsFetchers(Context context){
        SharedPreferences defSharPref = PreferenceManager.getDefaultSharedPreferences(context);
        final Set<String> setSelected = new HashSet<>(defSharPref.getStringSet(SettingsFragment.KEY_ARRIVALS_FETCHERS_USE,
                new HashSet<>()));
        if (setSelected.isEmpty()) {
            return Arrays.asList(new MatoAPIFetcher(),
                    new GTTJSONFetcher(), new FiveTScraperFetcher());
        }else{
            ArrayList<ArrivalsFetcher> outFetchers = new ArrayList<>(4);
            /*for(String s: setSelected){
                switch (s){
                    case "matofetcher":
                        outFetchers.add(new MatoAPIFetcher());
                        break;
                    case "fivetapifetcher":
                        outFetchers.add(new FiveTAPIFetcher());
                        break;
                    case "gttjsonfetcher":
                        outFetchers.add(new GTTJSONFetcher());
                        break;
                    case "fivetscraper":
                        outFetchers.add(new FiveTScraperFetcher());
                        break;
                    default:
                        throw  new IllegalArgumentException();
                }
            }*/
            if (setSelected.contains("matofetcher")) {
                outFetchers.add(new MatoAPIFetcher());
                setSelected.remove("matofetcher");
            }
            if (setSelected.contains("fivetapifetcher")) {
                outFetchers.add(new FiveTAPIFetcher());
                setSelected.remove("fivetapifetcher");
            }
            if (setSelected.contains("gttjsonfetcher")){
                outFetchers.add(new GTTJSONFetcher());
                setSelected.remove("gttjsonfetcher");
            }
            if (setSelected.contains("fivetscraper")) {
                outFetchers.add(new FiveTScraperFetcher());
                setSelected.remove("fivetscraper");
            }
            if(!setSelected.isEmpty()){
                Log.e("BusTO-Utils","Getting some fetchers values which are not contemplated: "+setSelected);
            }

            return outFetchers;
        }
    }
    /*public String getShorterDirection(String headSign){
        String[] parts = headSign.split(",");
        if (parts.length<=1){
            return  headSign.trim();
        }
        String first = parts[0].trim();
        String second = parts[1].trim();
        String firstLower = first.toLowerCase(Locale.ITALIAN);
        switch (firstLower){
            case "circolare destra":
            case "circolare sinistra":
                case
        }
    }*/
    /**
     * Print the first i lines of the the trace of an exception
     * https://stackoverflow.com/questions/21706722/fetch-only-first-n-lines-of-a-stack-trace
     */
    /*
    public static String traceCaller(Exception ex, int i) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        StringBuilder sb = new StringBuilder();
        ex.printStackTrace(pw);
        String ss = sw.toString();
        String[] splitted = ss.split("\n");
        sb.append("\n");
        if(splitted.length > 2 + i) {
            for(int x = 2; x < i+2; x++) {
                sb.append(splitted[x].trim());
                sb.append("\n");
            }
            return sb.toString();
        }
        return "Trace too Short.";
    }
     */
    public static String joinList(@Nullable List<String> dat, String separator){
        StringBuilder sb = new StringBuilder();
        if(dat==null || dat.size()==0)
            return "";
        else if(dat.size()==1)
            return dat.get(0);
        sb.append(dat.get(0));
        for (int i=1; i<dat.size(); i++){
            sb.append(separator);
            sb.append(dat.get(i));
        }
        return sb.toString();
    }

    public static <T> Set<T> convertArrayToSet(T[] array)
    {
        // Create an empty Set
        Set<T> set = new HashSet<>();
        // Add each element into the set
        set.addAll(Arrays.asList(array));

        // Return the converted Set
        return set;
    }

    public static <T> String giveClassesForArray(T[] array){
        StringBuilder sb = new StringBuilder();
        for (T f: array){
            sb.append("");
            sb.append(f.getClass().getSimpleName());
            sb.append("; ");
        }
        return sb.toString();
    }

    public static Spanned convertHtml(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT);
        } else {
            return Html.fromHtml(text);
        }
    }
}
