package com.openapi.ks.myapp.data.db;

import androidx.room.TypeConverter;

import java.util.Date;

public class Converters {
    @TypeConverter
    public static Long fromDate(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static Date toDate(Long millis) {
        return millis == null ? null : new Date(millis);
    }
}
