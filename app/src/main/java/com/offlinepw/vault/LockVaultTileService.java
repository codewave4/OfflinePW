package com.offlinepw.vault;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import com.offlinepw.vault.crypto.VaultSession;

/**
 * کاشی «قفل ولت» در نوار تنظیمات سریع (Quick Settings) — با یک ضربه کلید نشست از
 * حافظه پاک و برنامه روی صفحه‌ی احراز هویت می‌نشیند. بدون هیچ اجازه‌ی جدیدی؛
 * سیستم با BIND_QUICK_SETTINGS_TILE از دسترسی به آن محافظت می‌کند.
 */
public class LockVaultTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        VaultSession.clear();
        // MainActivity اگر زنده و در پس‌زمینه باشد: رکوردهای decrypt‌شده و کانکشن
        // DB فوراً پاک/بسته شوند (منتظر GC نمانیم).
        try {
            sendBroadcast(new Intent(MainActivity.ACTION_SESSION_LOCKED)
                    .setPackage(getPackageName()));
        } catch (Exception ignored) {
        }
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(intent); // نسخه‌ی قدیمی؛ روی API 26-33
        }
    }
}
