package com.offlinepw.vault;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * همگام‌سازی مد DayNightِ سیستم با flag تم خودِ اپ (is_dark_mode).
 * بدون این کار، دیالوگ‌های سیستمی (AlertDialog، فریم دیالوگ‌ها) طبق نایت‌مود
 * «سیستم» رنگ می‌شدند در حالی که رابط اپ طبق flag داخلی رنگ می‌شد و در ترکیب
 * «اپ تیره + سیستم روشن» (پیش‌فرض) ظاهر نامعتبر داشتند.
 */
public class OfflinePWApplication extends Application {
    public static final String PREF_SETTINGS = "OfflinePW_Prefs";
    public static final String KEY_IS_DARK_MODE = "is_dark_mode";

    @Override
    public void onCreate() {
        super.onCreate();
        setNightModeFromPrefs(this);
    }

    /**
     * مد نایت را از flag تم اپ می‌خواند و اعمال می‌کند.
     * هنگام toggle در MainActivity هم دوباره فراخوانی می‌شود تا
     * AppCompat Activityهای فعال را با مد جدید recreate کند.
     */
    public static void setNightModeFromPrefs(@NonNull Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
        AppCompatDelegate.setDefaultNightMode(prefs.getBoolean(KEY_IS_DARK_MODE, true)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }
}
