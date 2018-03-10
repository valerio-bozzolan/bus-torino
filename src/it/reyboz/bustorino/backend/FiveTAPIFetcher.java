package it.reyboz.bustorino.backend;

import android.support.annotation.Nullable;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicReference;

public class FiveTAPIFetcher implements ArrivalsFetcher {

    private static final String SECRET_KEY="759C97DC7D115966C30FD9169BB200D9";
    private static final String DEBUG_NAME = "FiveTAPIFetcher";

    @Override
    public Palina ReadArrivalTimesAll(String stopID, AtomicReference<result> res) {
        //set the date for the request as now

        Palina p = new Palina(stopID);

        //request parameters
        String response = performAPIRequest(QueryType.ARRIVALS,stopID,res);
        if(response==null){
            //an error has occured, details in res.get()
            return p;
        }

        /*
         Slight problem:
         "longName": ==> DESCRIPTION
        "name": "13N",
        "departures": [
            {
                "arrivalTimeInt": 1272,
                "time": "21:12",
                "rt": false
            }]
        "lineType": "URBANO" ==> URBANO can be either bus or tram or METRO
         */
        JSONArray arr;
        try{
            arr = new JSONArray(response);
            String type;
            Route.Type routetype;
            for(int i =0; i<arr.length();i++){
                JSONObject lineJSON = arr.getJSONObject(i);
                type = lineJSON.getString("lineType");
                String lineName=lineJSON.getString("name");
                /*String[] lineNameExploded = lineJSON.getString("name").split(" ");
                if(lineNameExploded.length==1) lineName=lineNameExploded[0];

                else {
                    //if(lineNameExploded[lineNameExploded.length-1].equals("/")) lineNameExploded[lineNameExploded.length-1] = "B";
                    StringBuilder sb = new StringBuilder();
                    for(String el:lineNameExploded) sb.append(el);
                    lineName = sb.toString();
                }*/
                //set the type of line
                if(type.equals("EXTRA"))
                    routetype = Route.Type.LONG_DISTANCE_BUS;
                else routetype = Route.Type.BUS;
                Route r = new Route(lineName.replace(" ",""),routetype,lineJSON.getString("longName"));
                Log.d(DEBUG_NAME,"Creating line with name "+lineJSON.getString("name")+" and description "+lineJSON.getString("longName"));
                JSONArray passagesJSON = lineJSON.getJSONArray("departures");
                for(int j=0;j<passagesJSON.length();j++){
                    JSONObject arrival = passagesJSON.getJSONObject(j);
                    r.addPassaggio(Route.getPassageString(arrival.getString("time"),arrival.getBoolean("rt")));
                    Log.d(DEBUG_NAME,"Adding passage with time "+arrival.getString("time")+"\nrealtime="+arrival.getBoolean("rt"));
                }
                p.addRoute(r);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            res.set(result.PARSER_ERROR);
            return p;
        }
        p.sortRoutes();
        res.set(result.OK);
        return p;
    }

    /**
     * Create and perform the network request. This method adds parameters and returns the result
     * @param t type of request to be performed
     * @param stopID optional parameter, stop ID which you need for passages and branches
     * @param res  result  container
     * @return a String which contains the result of the query, to be parsed
     */
    @Nullable
    static String performAPIRequest(QueryType t,@Nullable String stopID, AtomicReference<result> res){
        Date d = new Date();
        URL u;
        Hashtable<String,String> param = new Hashtable<>();

        try {
            String address  = getURLForOperation(t,stopID);
            param.put("TOKEN",getAccessToken(address,d));
            param.put("TIMESTAMP",String.valueOf(d.getTime()));
            param.put("Accept-Encoding","gzip");
            param.put("Connection","Keep-Alive");
            u = new URL(address);
        } catch (UnsupportedEncodingException | NoSuchAlgorithmException |MalformedURLException e) {
            e.printStackTrace();
            res.set(result.PARSER_ERROR);
            return null;
        }
        String response = networkTools.queryURL(u,res,param);
        if(response==null) {
            if(res.get()==result.SERVER_ERROR_404) {
                Log.w(DEBUG_NAME,"Got 404, either the server failed, or the stop was not found, or the hack is not working anymore");
                res.set(result.EMPTY_RESULT_SET);
            };
        }
        return response;
    }

    /**
     * Get the Token needed to access the API
     * @param URL the URL of the request
     * @return token
     * @throws NoSuchAlgorithmException if the system doesn't support MD5
     * @throws UnsupportedEncodingException if we made mistakes in writing utf-8
     */
    static String getAccessToken(String URL,Date d) throws NoSuchAlgorithmException,UnsupportedEncodingException{
        MessageDigest md = MessageDigest.getInstance("MD5");
        String strippedQuery = URL.replace("http://www.5t.torino.it/proxyws","");
        //return the time in milliseconds
        long timeMilli = d.getTime();
        StringBuilder sb = new StringBuilder();
        sb.append(strippedQuery);
        sb.append(timeMilli);
        sb.append(SECRET_KEY);
        String stringToBeHashed =  sb.toString();
        md.reset();
        byte[] data = md.digest(stringToBeHashed.getBytes("UTF-8"));
        sb = new StringBuilder();
        for (byte b : data){
            sb.append(String.format("%02x",b));
        }
        String result = sb.toString();
        Log.d(DEBUG_NAME,"getting token:\n\treduced URL: "+strippedQuery+"\n\ttimestamp: "+timeMilli+"\nTOKEN:"+result.toLowerCase());
        return result.toLowerCase();
    }

    /**
     * Get the right url for the operation you are doing, to be fed into the queryURL method
     * @param t type of operation
     * @param stopID stop on which you are working on
     * @return the Url to go to
     * @throws UnsupportedEncodingException if it cannot be converted to utf-8
     */
    public static String getURLForOperation(QueryType t,@Nullable String stopID) throws UnsupportedEncodingException {
        final StringBuilder sb = new StringBuilder();
        sb.append("http://www.5t.torino.it/proxyws/ws2.1/rest/stops/");
        switch (t){
            case ARRIVALS:
                sb.append(URLEncoder.encode(stopID,"utf-8"));
                sb.append("/departures");
                break;
            case DETAILS:
                sb.append(URLEncoder.encode(stopID,"utf-8"));
                sb.append("/branches/details");
                break;
            case STOPS:
                sb.append("all");
                break;
            case STOPS_VERSION:
                sb.append("version");
                break;
        }
        return sb.toString();
    }

    enum QueryType {
        ARRIVALS, DETAILS,STOPS, STOPS_VERSION
    }
}
