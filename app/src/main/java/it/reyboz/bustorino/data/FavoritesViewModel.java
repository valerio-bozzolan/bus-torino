package it.reyboz.bustorino.data;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

import it.reyboz.bustorino.backend.Stop;

public class FavoritesViewModel extends AndroidViewModel {

    FavoritesLiveData favoritesLiveData;

    public FavoritesViewModel(@NonNull Application application) {
        super(application);
        //appContext = application.getApplicationContext();
    }

    @Override
    protected void onCleared() {
        favoritesLiveData.onClear();
        super.onCleared();
    }

    public FavoritesLiveData getFavorites(){
        if (favoritesLiveData==null){
            favoritesLiveData= new FavoritesLiveData(getApplication(), true);
        }
        return favoritesLiveData;
    }
}
