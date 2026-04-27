package it.reyboz.bustorino.backend;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class ArrivalsFetcherContext implements ArrivalsFetcher{

    protected @Nullable Context appContext;

    public void setContext(@NonNull Context appContext) {
        this.appContext = appContext.getApplicationContext();
    }
}
