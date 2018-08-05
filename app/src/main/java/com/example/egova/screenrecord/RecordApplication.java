package com.example.egova.screenrecord;

import android.app.Application;
import android.content.Context;

/**
 * Created by JinZenghui on 2018/8/2.
 */

public class RecordApplication extends Application {

    private static RecordApplication application;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        application = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    public static RecordApplication getInstance(){
        return application;
    }
}
