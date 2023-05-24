/*
	BusTO (networking components)
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
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.Volley;

public class NetworkVolleyManager {
    private RequestQueue reqQueue;
    private static NetworkVolleyManager instance;
    private static Context context;
    public final static int DEFAULT_CACHE_SIZE = 1024;

    private NetworkVolleyManager(Context ctx) {
        context = ctx.getApplicationContext();
    }

    public static synchronized NetworkVolleyManager getInstance(Context ctx){
        if(instance==null) instance = new NetworkVolleyManager(ctx.getApplicationContext());
        return instance;
    }
    public RequestQueue getRequestQueue(){
        if(reqQueue==null) {
            //Instantiate the queue
            reqQueue = Volley.newRequestQueue(context);
        }
        return reqQueue;
    }
    public <Typ> void addToRequestQueue(Request<Typ> req){
        getRequestQueue().add(req);
    }
}
