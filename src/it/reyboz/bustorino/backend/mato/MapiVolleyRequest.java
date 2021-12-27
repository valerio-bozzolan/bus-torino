package it.reyboz.bustorino.backend.mato;

import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;

import java.util.Map;

public abstract class MapiVolleyRequest<T> extends Request<T> {
    private static final String API_URL="https://mapi.5t.torino.it/routing/v1/routers/mat/index/graphql";

    protected final Response.Listener<T> listener;
    private final MatoAPIFetcher.QueryType type;
    public MapiVolleyRequest(
            MatoAPIFetcher.QueryType type,
            Response.Listener<T> listener,
            @Nullable Response.ErrorListener errorListener) {
        super(Method.POST, API_URL, errorListener);
        this.type = type;
        this.listener = listener;

    }


    @Override
    protected void deliverResponse(T response) {
        listener.onResponse(response);
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return MatoAPIFetcher.Companion.getREQ_PARAMETERS();
    }

}
