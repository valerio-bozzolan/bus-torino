/*
	BusTO  - Backend components
    Copyright (C) 2018 Fabio Mazza

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
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class FiveTAPIFetcher implements ArrivalsFetcher{

    private static final String SECRET_KEY="759C97DC7D115966C30FD9169BB200D9";
    private static final String DEBUG_NAME = "FiveTAPIFetcher";
    final static LinkedList<String> apiDays = new LinkedList<>(Arrays.asList("dom","lun","mar","mer","gio","ven","sab"));


    @Override
    public Palina ReadArrivalTimesAll(String stopID, AtomicReference<result> res) {
        //set the date for the request as now

        Palina p = new Palina(stopID);

        //request parameters
        String response = performAPIRequest(QueryType.ARRIVALS,stopID,res);

        if(response==null) {
            if(res.get()==result.SERVER_ERROR_404) {
                Log.w(DEBUG_NAME,"Got 404, either the server failed, or the stop was not found, or the hack is not working anymore");
                res.set(result.EMPTY_RESULT_SET);
            };
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

                //set the type of line
                if(type.equals("EXTRA"))
                    routetype = Route.Type.LONG_DISTANCE_BUS;
                else routetype = Route.Type.BUS;
                //Cut out the spaces in the line Name
                //temporary fix
                lineName = lineName.replace(" ","").replace("/","B");
                //TODO: parse the line description
                Route r = new Route(lineName,routetype,lineJSON.getString("longName"));
                Log.d(DEBUG_NAME,"Creating line with name "+lineJSON.getString("name")+" and description "+lineJSON.getString("longName"));
                JSONArray passagesJSON = lineJSON.getJSONArray("departures");
                for(int j=0;j<passagesJSON.length();j++){
                    JSONObject arrival = passagesJSON.getJSONObject(j);
                    r.addPassaggio(Route.getPassageString(arrival.getString("time"),arrival.getBoolean("rt")));
                    //Log.d(DEBUG_NAME,"Adding passage with time "+arrival.getString("time")+"\nrealtime="+arrival.getBoolean("rt"));
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

    public List<Route> getDirectionsForStop(String stopID, AtomicReference<result> res) {

        String response = performAPIRequest(QueryType.DETAILS,stopID,res);
        if(response == null) return null;
        ArrayList<Route> routes = new ArrayList<>(10);

        try {
            JSONArray lines =new JSONArray(response);
            for(int i=0; i<lines.length();i++){
                Route.FestiveInfo festivo = Route.FestiveInfo.UNKNOWN;
                final JSONObject branchJSON = lines.getJSONObject(i);
                int branchid = branchJSON.getInt("branch");
                String description = branchJSON.getString("description");
                String direction = branchJSON.getString("direction");
                String stops = branchJSON.getJSONObject("branchDetail").getString("stops");
                String lineName = branchJSON.getString("lineName");
                Route.Type t = null;
                //parsing description
                String[] exploded = description.split(",");
                description = exploded[exploded.length-1]; //the real description
                int[]  serviceDays = {};
                if(exploded.length > 1) {
                    String secondo = exploded[exploded.length-2];
                    if (secondo.contains("festivo")) {
                        festivo = Route.FestiveInfo.FESTIVO;
                    } else if (secondo.contains("feriale")) {
                        festivo = Route.FestiveInfo.FERIALE;
                    } else if(secondo.contains("lun. - ven")) {
                        serviceDays = Route.reduced_week;
                    } else if(secondo.contains("sab - fest.")){
                        serviceDays = Route.weekend;
                        festivo = Route.FestiveInfo.FESTIVO;
                    } else {
                        Log.d(DEBUG_NAME,"Parsing details of line "+lineName+" branchid "+branchid+":\n\t"+
                            "Couldn't find a the service days\n"+
                            "Description: "+secondo+","+description
                        );
                    }
                    if(exploded.length>2){
                        switch (exploded[exploded.length-3].trim()) {
                            case "bus":
                                t = Route.Type.BUS;
                                break;
                            case "tram":
                                //never happened, but if it could happen you can get it
                                t = Route.Type.TRAM;
                                break;
                            default:
                                //nothing
                        }

                    }
                } else //only one piece
                    if(description.contains("festivo")){
                        festivo = Route.FestiveInfo.FESTIVO;
                    } else if(description.contains("feriale")){
                        festivo = Route.FestiveInfo.FERIALE;
                    }
                if(lineName.trim().equals("10")|| lineName.trim().equals("15")) t= Route.Type.TRAM;
                if(direction.contains("-")){
                    //
                    direction = direction.split("-")[1];
                }
                Route r = new Route(lineName.trim(),direction.trim(),t,null);
                if(serviceDays.length>0) r.serviceDays = serviceDays;
                r.festivo = festivo;
                r.branchid = branchid;
                r.description = description.trim();
                r.setStopsList(Arrays.asList(stops.split(",")));

                routes.add(r);
            }

            res.set(result.OK);

        } catch (JSONException e) {
            res.set(result.PARSER_ERROR);
            e.printStackTrace();
            return null;
        }
        return routes;
    }
    public List<Stop> getAllStopsFromGTT(AtomicReference<result> res){

        String response = performAPIRequest(QueryType.STOPS_ALL,null,res);
        ArrayList<Stop> stopslist = null;
        try{
            JSONArray stops = new JSONArray(response);
            stopslist = new ArrayList<>(stops.length());
            for (int i=0;i<stops.length();i++){
                JSONObject currentStop = stops.getJSONObject(i);
                String location = currentStop.getString("location");
                if(location.trim().equals("_")) location = null;
                String placeName = currentStop.getString("placeName");
                if(placeName.trim().equals("_")) placeName = null;
                String[] lines = currentStop.getString("lines").split(",");
                Route.Type t;
                    switch (currentStop.getString("type")){
                        case "BUS":
                            t = Route.Type.BUS;
                            break;
                        case "METRO":
                            t = Route.Type.METRO;
                            break;
                        case "TRENO":
                            t = Route.Type.RAILWAY;
                            break;
                        default:
                            t = null;
                    }
                Stop s = new Stop(currentStop.getString("id"),
                        currentStop.getString("name"),null,location,t,Arrays.asList(lines),
                        Double.parseDouble(currentStop.getString("lat")),
                        Double.parseDouble(currentStop.getString("lng")));
                if(placeName!=null)
                s.setAbsurdGTTPlaceName(placeName);
                stopslist.add(s);
            }
            res.set(result.OK);

        } catch (JSONException e) {
            e.printStackTrace();
            res.set(result.PARSER_ERROR);
            return null;
        }

        return stopslist;
    }

    /**
     * Create and perform the network request. This method adds parameters and returns the result
     * @param t type of request to be performed
     * @param stopID optional parameter, stop ID which you need for passages and branches
     * @param res  result  container
     * @return a String which contains the result of the query, to be parsed
     */
    @Nullable
    public static String performAPIRequest(QueryType t,@Nullable String stopID, AtomicReference<result> res){
        Date d = new Date();
        URL u;
        Hashtable<String,String> param = new Hashtable<>();

        try {
            String address  = getURLForOperation(t,stopID);
            //Log.d(DEBUG_NAME,"The address to query is: "+address);
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

        return response;
    }

    /**
     * Get the Token needed to access the API
     * @param URL the URL of the request
     * @return token
     * @throws NoSuchAlgorithmException if the system doesn't support MD5
     * @throws UnsupportedEncodingException if we made mistakes in writing utf-8
     */
    private static String getAccessToken(String URL,Date d) throws NoSuchAlgorithmException,UnsupportedEncodingException{
        MessageDigest md = MessageDigest.getInstance("MD5");
        String strippedQuery = URL.replace("http://www.5t.torino.it/proxyws","");
        //return the time in milliseconds
        long timeMilli = d.getTime();
        StringBuilder sb = new StringBuilder();
        sb.append(strippedQuery);
        sb.append(timeMilli);
        sb.append(SECRET_KEY);
        String stringToBeHashed =  sb.toString();
        //Log.d(DEBUG_NAME,"Hashing string: "+stringToBeHashed);
        md.reset();
        byte[] data = md.digest(stringToBeHashed.getBytes("UTF-8"));
        sb = new StringBuilder();
        for (byte b : data){
            sb.append(String.format("%02x",b));
        }
        String result = sb.toString();
        //Log.d(DEBUG_NAME,"getting token:\n\treduced URL: "+strippedQuery+"\n\ttimestamp: "+timeMilli+"\nTOKEN:"+result.toLowerCase());
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
            case STOPS_ALL:
                sb.append("all");
                break;
            case STOPS_VERSION:
                sb.append("version");
                break;
        }
        return sb.toString();
    }



    public enum QueryType {
        ARRIVALS, DETAILS,STOPS_ALL, STOPS_VERSION
    }
}
