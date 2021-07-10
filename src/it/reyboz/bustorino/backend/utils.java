package it.reyboz.bustorino.backend;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

public abstract class utils {
    private static final double EarthRadius = 6371e3;
    public static Double measuredistanceBetween(double lat1,double long1,double lat2,double long2){
        final double phi1 = Math.toRadians(lat1);
        final double phi2 = Math.toRadians(lat2);

        final double deltaPhi = Math.toRadians(lat2-lat1);
        final double deltaTheta = Math.toRadians(long2-long1);

        final double a = Math.sin(deltaPhi/2)*Math.sin(deltaPhi/2)+
                Math.cos(phi1)*Math.cos(phi2)*Math.sin(deltaTheta/2)*Math.sin(deltaTheta/2);
        final double c = 2*Math.atan2(Math.sqrt(a),Math.sqrt(1-a));

        return Math.abs(EarthRadius*c);

    }
    /*
    public static int convertDipToPixels(Context con,float dips)
    {
        return (int) (dips * con.getResources().getDisplayMetrics().density + 0.5f);
    }
     */

    public static float convertDipToPixels(Context con, float dp){
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,con.getResources().getDisplayMetrics());
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

    public static String toTitleCase(String givenString) {
        String[] arr = givenString.split(" ");
        StringBuffer sb = new StringBuffer();
        //Log.d("BusTO chars", "String parsing: "+givenString+" in array: "+ Arrays.toString(arr));
        for (int i = 0; i < arr.length; i++) {
            if (arr[i].length() > 1)
            sb.append(Character.toUpperCase(arr[i].charAt(0)))
                    .append(arr[i].substring(1)).append(" ");
            else sb.append(arr[i]);
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
        context.startActivity(browserIntent1);
    }

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
}
