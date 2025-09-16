//package com.openai.commonutil;
//
//import android.os.Parcel;
//import android.os.Parcelable;
//
//import java.net.MalformedURLException;
//import java.net.URL;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
///**
// * Parcel 序列化的工具类 Created by CyningLi on 2014/9/23 0023.
// * <p/>
// * Parcelable-->各种类型，各种类型-->Parcelable
// */
//public class ParcelableUtils {
//    public static void write(Parcel dest, String string) {
//        dest.writeByte((byte) (string == null ? 0 : 1));
//        if (string != null) {
//            dest.writeString(string);
//        }
//    }
//
//    public static String readString(Parcel source) {
//        if (source.readByte() == 1) {
//            return source.readString();
//        }
//        return null;
//    }
//
//    public static void write(Parcel dest, Parcelable parcelable, int flags) {
//        dest.writeByte((byte) (parcelable == null ? 0 : 1));
//        if (parcelable != null) {
//            dest.writeParcelable(parcelable, flags);
//        }
//    }
//
//
//
//
//
//
//
//    public static void write(Parcel dest, URL url) {
//        dest.writeString(url.toExternalForm());
//    }
//
//    public static URL readURL(Parcel source) {
//        try {
//            return new URL(source.readString());
//        } catch (MalformedURLException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    public static void write(Parcel dest, Date date) {
//        dest.writeByte((byte) (date == null ? 0 : 1));
//        if (date != null) {
//            dest.writeLong(date.getTime());
//        }
//    }
//
//    public static Date readDate(Parcel source) {
//        if (source.readByte() == 1) {
//            return new Date(source.readLong());
//        }
//        return null;
//    }
//
//    public static <T extends Enum<T>> void write(Parcel dest, Enum<T> enu) {
//        if (enu == null) {
//            dest.writeString("");
//        } else {
//            dest.writeString(enu.name());
//        }
//    }
//
//    public static <T extends Enum<T>> T readEnum(Parcel dest, Class<T> clazz) {
//        String name = dest.readString();
//        if ("".equals(name)) {
//            return null;
//        }
//        return Enum.valueOf(clazz, name);
//    }
//
//    public static void write(Parcel dest, boolean bool) {
//        dest.writeByte((byte) (bool ? 1 : 0));
//    }
//
//    public static boolean readBoolean(Parcel source) {
//        return source.readByte() == 1;
//    }
//
//    public static <T extends Parcelable> void writeList(Parcel dest,
//                                                        List<T> fields, int flags) {
//        if (fields == null) {
//            dest.writeInt(-1);
//        } else {
//            dest.writeInt(fields.size());
//            for (T field : fields) {
//                dest.writeParcelable(field, flags);
//            }
//        }
//    }
//
//    public static <T extends Parcelable> ArrayList<T> readParcelableList(
//            Parcel source, ClassLoader mClassLoader) {
//        int size = source.readInt();
//        if (size == -1) {
//            return null;
//        }
//        ArrayList<T> list = new ArrayList<>();
//        for (int i = 0; i < size; i++) {
//            // 安全的类型转换，Parcelable已经确保类型安全
//            @SuppressWarnings("unchecked")
//            T item = (T) source.readParcelable(mClassLoader);
//            list.add(item);
//        }
//        return list;
//    }
//}