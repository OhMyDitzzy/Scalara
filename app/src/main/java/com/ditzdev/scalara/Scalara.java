package com.ditzdev.scalara;

import android.app.Application;
import com.ditzdev.scalara.handler.EHandler;

public class Scalara extends Application {
    
    @Override
    public void onCreate() {
        super.onCreate();
        EHandler.initialize(this);
    }
}
