package com.offlinepw.vault;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.offlinepw.vault.crypto.VaultSession;

/**
 * کاشی «قفل ولت» در نوار تنظیمات سریع (Quick Settings) — با یک ضربه کلید نشست از
 * حافظه پاک و برنامه روی صفحه‌ی احراز هویت می‌نشیند. بدون هیچ اجازه‌ی جدیدی؛
 * سیستم با BIND_QUICK_SETTINGS_TILE از دسترسی به آن محافظت می‌کند.
 *
 * رفتار امن روی صفحه‌ی قفل:
 * - VaultSession.clear() همیشه و بدون شرط اول اجرا می‌شود (بدون درخواست unlock).
 * - اگر گوشی قفل باشد (isLocked())، Activity باز نمی‌شود؛ ولت قفل شده و بار بعد
 *   که اپ باز شود صفحه‌ی رمز می‌آید.
 * - state تایل با hasDek() به‌روزرسانی می‌شود.
 */
public class LockVaultTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        // همیشه و بدون شرط، اول از همه کلید را پاک کن
        VaultSession.clear();
        // MainActivity اگر زنده و در پس‌زمینه باشد: رکوردهای decrypt‌شده و کانکشن DB فوراً پاک/بسته شوند
        try {
            sendBroadcast(new Intent(MainActivity.ACTION_SESSION_LOCKED)
                    .setPackage(getPackageName()));
        } catch (Exception ignored) {
        }
        updateTileState();

        // گوشی قفله؛ ولت قفل شد، نیازی به باز کردن اپ نیست (درخواست unlock نمی‌دهیم)
        if (isLocked()) return;

        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void updateTileState() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean open = VaultSession.hasDek();
        t.setState(open ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) {
            t.setSubtitle(getString(open ? R.string.tile_unlocked : R.string.tile_locked));
        }
        t.updateTile();
    }
}
