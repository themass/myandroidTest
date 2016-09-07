package com.timeline.vpn.provider;

import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

public class TableCreator {
    public static final String ADS_INFO = "ads_info";

    public static void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS "
                + ADS_INFO + " ("
                + BaseColumns._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + AdsInfoModel.ADS_TYPE + " int NOT NULL,"
                + AdsInfoModel.ADS_EVENT + " int NOT NULL,"
                + AdsInfoModel.ADS_DATE + " LONG"
                + ");");
    }
}
