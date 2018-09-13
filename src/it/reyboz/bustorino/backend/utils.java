package it.reyboz.bustorino.backend;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;

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

        return EarthRadius*c;

    }
    public static int convertDipToPixels(Context con,float dips)
    {
        return (int) (dips * con.getResources().getDisplayMetrics().density + 0.5f);
    }
    public static int calculateNumColumnsFromSize(View containerView, int pixelsize){
        int width = containerView.getWidth();
        float ncols = ((float)width)/pixelsize;
        return (int) Math.floor(ncols);
    }
}
