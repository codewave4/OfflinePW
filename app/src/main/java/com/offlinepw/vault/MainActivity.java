package com.offlinepw.vault;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.offlinepw.vault.crypto.CryptoManager;
import com.offlinepw.vault.crypto.VaultSession;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    public static class VaultItem {
        private String id;
        private String title;
        private String category;
        private String username;
        private String password;
        private String notes;
        private String totpSecret;
        private String website;
        private boolean pinned;
        private long createdAt;   // epoch millis (0 = نامعلوم/قدیمی)
        private long updatedAt;   // epoch millis (0 = نامعلوم/قدیمی)

        public VaultItem(String id, String title, String category, String username, String password, String notes, String totpSecret, String website, boolean pinned) {
            this(id, title, category, username, password, notes, totpSecret, website, pinned, 0L, 0L);
        }

        public VaultItem(String id, String title, String category, String username, String password, String notes, String totpSecret, String website, boolean pinned, long createdAt, long updatedAt) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.username = username;
            this.password = password;
            this.notes = notes;
            this.totpSecret = totpSecret;
            this.website = website;
            this.pinned = pinned;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getCategory() { return category; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getNotes() { return notes; }
        public String getTotpSecret() { return totpSecret; }
        public String getWebsite() { return website; }
        public boolean isPinned() { return pinned; }
        public void setPinned(boolean pinned) { this.pinned = pinned; }
        public long getCreatedAt() { return createdAt; }
        public long getUpdatedAt() { return updatedAt; }
    }

    public static class VaultDatabaseHelper extends SQLiteOpenHelper {
        public static final String TABLE_ITEMS = "vault_items";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_CATEGORY = "category";
        public static final String COLUMN_USERNAME = "username";
        public static final String COLUMN_PASSWORD = "password";
        public static final String COLUMN_NOTES = "notes";
        public static final String COLUMN_TOTP = "totp_secret";
        public static final String COLUMN_WEBSITE = "website";
        public static final String COLUMN_PINNED = "pinned";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_UPDATED_AT = "updated_at";

        public VaultDatabaseHelper(Context context) {
            super(context, "offline_pw_vault.db", null, 5);
        }

        private String getPassphrase() {
            javax.crypto.SecretKey dek = com.offlinepw.vault.crypto.VaultSession.getDek();
            if (dek == null) return "";
            return android.util.Base64.encodeToString(dek.getEncoded(), android.util.Base64.NO_WRAP);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_ITEMS + " (" +
                    COLUMN_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_TITLE + " TEXT, " +
                    COLUMN_CATEGORY + " TEXT, " +
                    COLUMN_USERNAME + " TEXT, " +
                    COLUMN_PASSWORD + " TEXT, " +
                    COLUMN_NOTES + " TEXT, " +
                    COLUMN_TOTP + " TEXT, " +
                    COLUMN_WEBSITE + " TEXT, " +
                    COLUMN_PINNED + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0, " +
                    COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                try {
                    db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COLUMN_TOTP + " TEXT");
                } catch (Exception ignored) {}
            }
            if (oldVersion < 3) {
                try {
                    db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COLUMN_WEBSITE + " TEXT");
                } catch (Exception ignored) {}
            }
            if (oldVersion < 4) {
                try {
                    db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COLUMN_PINNED + " INTEGER NOT NULL DEFAULT 0");
                } catch (Exception ignored) {}
            }
            if (oldVersion < 5) {
                // زمان‌ها به‌صورت عددی و داخل همان DB رمزنگاری‌شده (SQLCipher) ذخیره
                // می‌شوند؛ حساسیت محتوایی ندارند (مثل category/pinned).
                try {
                    db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COLUMN_CREATED_AT + " INTEGER NOT NULL DEFAULT 0");
                } catch (Exception ignored) {}
                try {
                    db.execSQL("ALTER TABLE " + TABLE_ITEMS + " ADD COLUMN " + COLUMN_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0");
                } catch (Exception ignored) {}
                // رکوردهای قدیمی بی‌تاریخ را با زمان مهاجرت پر می‌کنیم تا
                // «آخرین به‌روزرسانی» و گزارش کهنه‌بودن معنادار بمانند.
                try {
                    long now = System.currentTimeMillis();
                    db.execSQL("UPDATE " + TABLE_ITEMS + " SET " + COLUMN_CREATED_AT + "=" + now +
                            " WHERE " + COLUMN_CREATED_AT + "=0");
                    db.execSQL("UPDATE " + TABLE_ITEMS + " SET " + COLUMN_UPDATED_AT + "=" + now +
                            " WHERE " + COLUMN_UPDATED_AT + "=0");
                } catch (Exception ignored) {}
            }
        }

        public void insertItem(VaultItem item, CryptoManager crypto) {
            String passphrase = getPassphrase();
            if (passphrase.isEmpty()) throw new IllegalStateException("Session key missing");
            SQLiteDatabase db = getWritableDatabase(passphrase);
            ContentValues cv = new ContentValues();
            String id = item.getId();
            cv.put(COLUMN_ID, id);
            cv.put(COLUMN_TITLE, crypto.encrypt(item.getTitle(), id + "|title"));
            cv.put(COLUMN_CATEGORY, item.getCategory());
            cv.put(COLUMN_USERNAME, crypto.encrypt(item.getUsername(), id + "|username"));
            cv.put(COLUMN_PASSWORD, crypto.encrypt(item.getPassword(), id + "|password"));
            cv.put(COLUMN_NOTES, crypto.encrypt(item.getNotes(), id + "|notes"));
            cv.put(COLUMN_TOTP, crypto.encrypt(item.getTotpSecret(), id + "|totp"));
            cv.put(COLUMN_WEBSITE, crypto.encrypt(item.getWebsite(), id + "|website"));
            cv.put(COLUMN_PINNED, item.isPinned() ? 1 : 0);
            cv.put(COLUMN_CREATED_AT, item.getCreatedAt());
            cv.put(COLUMN_UPDATED_AT, item.getUpdatedAt());
            db.insertWithOnConflict(TABLE_ITEMS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }

        public void setPinned(String id, boolean pinned) {
            String passphrase = getPassphrase();
            if (passphrase.isEmpty()) throw new IllegalStateException("Session key missing");
            SQLiteDatabase db = getWritableDatabase(passphrase);
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_PINNED, pinned ? 1 : 0);
            db.update(TABLE_ITEMS, cv, COLUMN_ID + "=?", new String[]{id});
        }

        public List<VaultItem> getAllDecryptedItems(CryptoManager crypto, AtomicBoolean corruptionFlag) {
            List<VaultItem> list = new ArrayList<>();
            String passphrase = getPassphrase();
            if (passphrase.isEmpty()) throw new IllegalStateException("Session key missing");
            SQLiteDatabase db = getReadableDatabase(passphrase);
            Cursor c = null;
            try {
                // آیتم‌های پین‌شده همیشه بالای لیست، بقیه به ترتیب ثبت
                c = db.query(TABLE_ITEMS, null, null, null, null, null,
                        COLUMN_PINNED + " DESC, rowid ASC");
                while (c.moveToNext()) {
                    String id = c.getString(c.getColumnIndexOrThrow(COLUMN_ID));
                    try {
                        String title = crypto.decrypt(c.getString(c.getColumnIndexOrThrow(COLUMN_TITLE)), id + "|title");
                        String cat = c.getString(c.getColumnIndexOrThrow(COLUMN_CATEGORY));
                        String user = crypto.decrypt(c.getString(c.getColumnIndexOrThrow(COLUMN_USERNAME)), id + "|username");
                        String pass = crypto.decrypt(c.getString(c.getColumnIndexOrThrow(COLUMN_PASSWORD)), id + "|password");
                        String notes = crypto.decrypt(c.getString(c.getColumnIndexOrThrow(COLUMN_NOTES)), id + "|notes");
                        String totp = "";
                        int totpIndex = c.getColumnIndex(COLUMN_TOTP);
                        if (totpIndex != -1) {
                            totp = crypto.decrypt(c.getString(totpIndex), id + "|totp");
                        }
                        String website = "";
                        int websiteIndex = c.getColumnIndex(COLUMN_WEBSITE);
                        if (websiteIndex != -1) {
                            website = crypto.decrypt(c.getString(websiteIndex), id + "|website");
                        }
                        boolean pinned = false;
                        int pinnedIndex = c.getColumnIndex(COLUMN_PINNED);
                        if (pinnedIndex != -1) {
                            pinned = c.getInt(pinnedIndex) != 0;
                        }
                        long createdAt = 0L, updatedAt = 0L;
                        int createdIdx = c.getColumnIndex(COLUMN_CREATED_AT);
                        if (createdIdx != -1) createdAt = c.getLong(createdIdx);
                        int updatedIdx = c.getColumnIndex(COLUMN_UPDATED_AT);
                        if (updatedIdx != -1) updatedAt = c.getLong(updatedIdx);
                        list.add(new VaultItem(id, title, cat, user, pass, notes, totp, website, pinned, createdAt, updatedAt));
                    } catch (Exception perRow) {
                        // یک رکورد خراب نباید جلوی خواندن بقیه‌ی رکوردها را بگیرد؛
                        // فقط با پرچم خطا ادامه می‌دهیم تا UI اطلاع‌رسانی کند.
                        if (corruptionFlag != null) corruptionFlag.set(true);
                    }
                }
            } finally {
                if (c != null) c.close();
            }
            return list;
        }

        public void deleteItem(String id) {
            String passphrase = getPassphrase();
            if (passphrase.isEmpty()) throw new IllegalStateException("Session key missing");
            SQLiteDatabase db = getWritableDatabase(passphrase);
            db.delete(TABLE_ITEMS, COLUMN_ID + "=?", new String[]{id});
        }
    }

    public interface OnItemClickListener {
        void onItemClick(VaultItem item);
    }

    private final Set<String> revealedTotpItemIds = new HashSet<>();

    public class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.ViewHolder> {
        private List<VaultItem> fullList = new ArrayList<>();
        private List<VaultItem> displayList = new ArrayList<>();
        private OnItemClickListener listener;
        private String lastQuery = "";
        /** 0=پیش‌فرض (ترتیب ثبت)، 1=عنوان، 2=دسته‌بندی، 3=آخرین به‌روزرسانی */
        private int sortMode = 0;

        public VaultAdapter(OnItemClickListener listener) {
            this.listener = listener;
        }

        public void setItems(List<VaultItem> items) {
            this.fullList = new ArrayList<>(items);
            applyFilterAndSort();
        }

        public void setSortMode(int mode) {
            this.sortMode = mode;
            applyFilterAndSort();
        }

        public int getSortMode() {
            return sortMode;
        }

        /** نسخه‌ی محافظت‌شده از کل آیتم‌ها (برای گزارش سلامت) — بدون اعمال فیلتر. */
        public List<VaultItem> getFullSnapshot() {
            return new ArrayList<>(fullList);
        }

        public void filter(String query) {
            this.lastQuery = query == null ? "" : query;
            applyFilterAndSort();
        }

        /** فیلتر + مرتب‌سازی روی displayList؛ پین‌شده‌ها در هر حالتِ مرتب‌سازی بالا می‌مانند. */
        private void applyFilterAndSort() {
            List<VaultItem> out = new ArrayList<>();
            String q = lastQuery.toLowerCase(Locale.ROOT); // Locale.ROOT: case-folding وابسته به زبان سیستم نباشد
            if (q.trim().isEmpty()) {
                out.addAll(fullList);
            } else {
                for (VaultItem it : fullList) {
                    if (containsIgnoreCase(it.getTitle(), q) ||
                        containsIgnoreCase(it.getUsername(), q) ||
                        containsIgnoreCase(it.getCategory(), q) ||
                        containsIgnoreCase(it.getNotes(), q) ||
                        containsIgnoreCase(it.getWebsite(), q)) {
                        out.add(it);
                    }
                }
            }
            sortDisplayList(out);
            displayList = out;
            notifyDataSetChanged();
        }

        private void sortDisplayList(List<VaultItem> list) {
            final java.text.Collator collator = java.text.Collator.getInstance(new Locale("fa"));
            java.util.Collections.sort(list, (a, b) -> {
                if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
                switch (sortMode) {
                    case 1: { // عنوان (الفبای فارسی‌آگاه)
                        int c = collator.compare(nz(a.getTitle()), nz(b.getTitle()));
                        return c != 0 ? c : Long.compare(a.getCreatedAt(), b.getCreatedAt());
                    }
                    case 2: { // دسته‌بندی، سپس عنوان
                        int c = collator.compare(nz(a.getCategory()).toUpperCase(Locale.ROOT),
                                nz(b.getCategory()).toUpperCase(Locale.ROOT));
                        if (c != 0) return c;
                        return collator.compare(nz(a.getTitle()), nz(b.getTitle()));
                    }
                    case 3: { // آخرین به‌روزرسانی (جدید اول)
                        return Long.compare(refTime(b), refTime(a));
                    }
                    default:
                        return 0; // پیش‌فرض: ترتیب ثبت (از DB: پین‌ها اول، بعد rowid)
                }
            });
        }

        private String nz(String s) { return s == null ? "" : s; }
        private long refTime(VaultItem it) {
            return it.getUpdatedAt() > 0 ? it.getUpdatedAt() : it.getCreatedAt();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            MaterialCardView card = new MaterialCardView(ctx);
            RecyclerView.LayoutParams cardLp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(24, 12, 24, 12);
            card.setLayoutParams(cardLp);
            card.setRadius(24f);
            card.setStrokeWidth(1);
            card.setStrokeColor(Color.parseColor(isDarkMode ? "#27272A" : "#E4E4E7"));
            card.setCardBackgroundColor(Color.parseColor(isDarkMode ? "#18181B" : "#FFFFFF"));
            card.setCardElevation(2f);

            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(32, 28, 32, 28);

            LinearLayout header = new LinearLayout(ctx);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);

            // نشان پین — فقط وقتی آیتم پین شده باشد دیده می‌شود
            ImageView ivPin = new ImageView(ctx);
            ivPin.setImageResource(R.drawable.ic_pin);
            ivPin.setColorFilter(Color.parseColor("#F59E0B"));
            LinearLayout.LayoutParams pinLp = new LinearLayout.LayoutParams(20, 20);
            pinLp.setMarginEnd(8);
            ivPin.setLayoutParams(pinLp);
            ivPin.setVisibility(View.GONE);
            header.addView(ivPin);

            TextView tvTitle = new TextView(ctx);
            tvTitle.setTextSize(17f);
            tvTitle.setTypeface(null, Typeface.BOLD);
            tvTitle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            header.addView(tvTitle, titleLp);

            TextView tvCategory = new TextView(ctx);
            tvCategory.setTextSize(11f);
            tvCategory.setTypeface(null, Typeface.BOLD);
            tvCategory.setTextColor(Color.parseColor("#3B82F6"));
            tvCategory.setBackgroundColor(Color.parseColor(isDarkMode ? "#1E293B" : "#EFF6FF"));
            tvCategory.setPadding(18, 6, 18, 6);
            header.addView(tvCategory);

            root.addView(header);

            TextView tvUsername = new TextView(ctx);
            tvUsername.setTextSize(14f);
            tvUsername.setTextColor(Color.parseColor(isDarkMode ? "#A1A1AA" : "#71717A"));
            tvUsername.setPadding(0, 12, 0, 0);
            root.addView(tvUsername);

            TextView tvMasked = new TextView(ctx);
            tvMasked.setTextSize(13f);
            tvMasked.setTextColor(Color.parseColor("#10B981"));
            tvMasked.setText("•••• •••• •••• ••••");
            tvMasked.setPadding(0, 6, 0, 0);
            root.addView(tvMasked);

            TextView tvTotpDisplay = new TextView(ctx);
            tvTotpDisplay.setTextSize(13f);
            tvTotpDisplay.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvTotpDisplay.setTextColor(Color.parseColor("#F59E0B"));
            tvTotpDisplay.setPadding(0, 8, 0, 0);
            tvTotpDisplay.setVisibility(View.GONE);
            root.addView(tvTotpDisplay);

            // خط «آخرین به‌روزرسانی» — پایین کارت، فقط وقتی تاریخ معلوم باشد
            TextView tvUpdated = new TextView(ctx);
            tvUpdated.setTextSize(11f);
            tvUpdated.setTextColor(Color.parseColor(isDarkMode ? "#A1A1AA" : "#71717A"));
            tvUpdated.setPadding(0, 8, 0, 0);
            tvUpdated.setVisibility(View.GONE);
            root.addView(tvUpdated);

            card.addView(root);
            return new ViewHolder(card, ivPin, tvTitle, tvCategory, tvUsername, tvMasked, tvTotpDisplay, tvUpdated);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VaultItem item = displayList.get(position);
            holder.ivPin.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);
            holder.tvTitle.setText(item.getTitle());

            // نمایش «آخرین به‌روزرسانی» (و در نبودش «تاریخ ساخت») در پایین کارت
            long refTs = item.getUpdatedAt() > 0 ? item.getUpdatedAt() : item.getCreatedAt();
            if (refTs > 0) {
                holder.tvUpdated.setText((isPersian ? "به‌روزرسانی: " : "updated: ") + formatVaultDate(refTs));
                holder.tvUpdated.setVisibility(View.VISIBLE);
            } else {
                holder.tvUpdated.setVisibility(View.GONE);
            }

            String cat = item.getCategory() != null && !item.getCategory().isEmpty() ? item.getCategory().toUpperCase() : "LOGIN";
            holder.tvCategory.setText(cat);
            holder.tvUsername.setText(item.getUsername());

            if (item.getTotpSecret() != null && !item.getTotpSecret().trim().isEmpty()) {
                holder.tvTotpDisplay.setVisibility(View.VISIBLE);
                boolean isRevealed = revealedTotpItemIds.contains(item.getId());
                long remainingSecs = 30 - ((System.currentTimeMillis() / 1000) % 30);
                if (isRevealed) {
                    String code = TotpGenerator.generateCode(item.getTotpSecret());
                    holder.tvTotpDisplay.setText((isPersian ? "کد ۲مرحله‌ای: " : "TOTP: ") + code + " (" + remainingSecs + "s)");
                } else {
                    holder.tvTotpDisplay.setText((isPersian ? "کد ۲مرحله‌ای: ••••••" : "TOTP: ••••••") + " (" + remainingSecs + "s)");
                }
                holder.tvTotpDisplay.setOnClickListener(v -> {
                    String currentCode = TotpGenerator.generateCode(item.getTotpSecret());
                    copyToClipboard(isPersian ? "کد TOTP" : "TOTP Code", currentCode);
                    revealedTotpItemIds.add(item.getId());
                    // اگر در لحظه‌ی کلیک لیست تغییر کرده باشد (فیلتر/ریلود)،
                    // پوزیشن -1 می‌شود و notifyItemChanged(-1) کرش می‌دهد.
                    int pos = holder.getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
                    holder.tvTotpDisplay.postDelayed(() -> {
                        revealedTotpItemIds.remove(item.getId());
                        notifyDataSetChanged();
                    }, 5000L);
                });
            } else {
                holder.tvTotpDisplay.setVisibility(View.GONE);
            }

            holder.card.setStrokeColor(Color.parseColor(isDarkMode ? "#27272A" : "#E4E4E7"));
            holder.card.setCardBackgroundColor(Color.parseColor(isDarkMode ? "#18181B" : "#FFFFFF"));
            holder.tvTitle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));

            holder.card.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }

        public boolean hasTotpItems() {
            for (VaultItem it : displayList) {
                if (it.getTotpSecret() != null && !it.getTotpSecret().trim().isEmpty()) return true;
            }
            return false;
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            ImageView ivPin;
            TextView tvTitle, tvCategory, tvUsername, tvMasked, tvTotpDisplay, tvUpdated;

            public ViewHolder(@NonNull View itemView, ImageView pin, TextView t, TextView c, TextView u, TextView m, TextView totp, TextView updated) {
                super(itemView);
                card = (MaterialCardView) itemView;
                ivPin = pin;
                tvTitle = t;
                tvCategory = c;
                tvUsername = u;
                tvMasked = m;
                tvTotpDisplay = totp;
                tvUpdated = updated;
            }
        }

        public VaultItem getItem(int position) {
            if (position < 0 || position >= displayList.size()) return null;
            return displayList.get(position);
        }
    }

    private VaultAdapter adapter;
    private VaultDatabaseHelper dbHelper;
    private CryptoManager cryptoManager;
    private EditText etSearch;
    private MaterialButton btnAbout;
    private MaterialButton btnMore;
    private MaterialButton btnLanguage;
    private MaterialButton btnThemeToggle;
    private TextView tvAppTitle;
    private CoordinatorLayout mainRootLayout;
    private AppBarLayout appBarLayout;
    private FloatingActionButton fabAdd;
    private FloatingActionButton fabBackup;

    // --- اسوایپ پین/آن‌پین ---
    private final Paint swipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Drawable swipeIconPin;   // lazy-load با tint سفید
    private Drawable swipeIconUnpin;
    private static final int SWIPE_PIN_COLOR = 0xFFF59E0B;    // کهربایی: پین (اسوایپ به چپ)
    private static final int SWIPE_UNPIN_COLOR = 0xFFEF4444;  // قرمز: آن‌پین (اسوایپ به راست)

    // --- بکاپ/بازیابی ---
    private static final String PROVIDER_AUTHORITY = "com.offlinepw.vault.fileprovider";
    private Uri pendingImportUri;
    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                pendingImportUri = uri;
                showBackupImportDialog();
            });

    private boolean isDarkMode = true;
    private boolean isPersian = false;
    private SharedPreferences prefs;

    private static final long AUTO_LOCK_DELAY_MS = 30 * 1000L; // قفل خودکار پس از ۳۰ ثانیه در پس‌زمینه

    // --- مدیریت فرم افزودن/ویرایش در چرخش صفحه ---
    private AlertDialog currentAddDialog;
    private String editingItemId;
    private Bundle pendingDlgRestore;      // values فیلدها هنگام چرخش صفحه
    private String pendingDlgEditId;       // id آیتم در حال ویرایش هنگام چرخش صفحه

    // --- پاک‌سازی امن کلیپ‌بورد ---
    private static final String CLIP_MARKER_KEY = "offlinepw_sensitive_marker";
    private static final long CLIP_CLEAR_DELAY_MS = 45 * 1000L;
    private final Handler clipboardClearHandler = new Handler(Looper.getMainLooper());
    private String lastCopiedText;         // فقط برای نسخه‌های قبل از Android 13 (بدون extras ماندگار)

    private Handler totpHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoLockRunnable = this::lockVaultNow;
    private Runnable totpRunnable = new Runnable() {
        private long lastTickSecond = -1;

        @Override
        public void run() {
            long second = System.currentTimeMillis() / 1000;
            if (adapter != null && adapter.hasTotpItems()) {
                // فقط وقتی ثانیه‌ی شمارش معکوس عوض شده، لیست را به‌روز کن
                if (second != lastTickSecond) {
                    lastTickSecond = second;
                    adapter.notifyDataSetChanged();
                }
            }
            totpHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("OfflinePW_Prefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("is_dark_mode", true);
        isPersian = prefs.getBoolean("is_persian", false);

        SQLiteDatabase.loadLibs(this);

        cryptoManager = new CryptoManager();
        dbHelper = new VaultDatabaseHelper(this);

        mainRootLayout = findViewById(R.id.mainRootLayout);
        appBarLayout = findViewById(R.id.appBarLayout);
        tvAppTitle = findViewById(R.id.tvAppTitle);
        RecyclerView rvVault = findViewById(R.id.rvVault);
        etSearch = findViewById(R.id.etSearch);
        fabAdd = findViewById(R.id.fabAdd);
        btnAbout = findViewById(R.id.btnAbout);
        btnMore = findViewById(R.id.btnMore);
        btnLanguage = findViewById(R.id.btnLanguage);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);
        if (btnMore != null) btnMore.setOnClickListener(v -> showVaultOptionsMenu());

        adapter = new VaultAdapter(item -> showEditOrDeleteDialog(item));
        adapter.setSortMode(prefs.getInt("vault_sort_mode", 0));
        if (rvVault != null) {
            rvVault.setLayoutManager(new LinearLayoutManager(this));
            rvVault.setAdapter(adapter);
            // اسوایپ افقی: چپ = پین، راست = آن‌پین
            ItemTouchHelper pinTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                @Override
                public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                      @NonNull RecyclerView.ViewHolder target) {
                    return false; // بدون جابه‌جایی ترتیب
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                    handlePinSwipe(viewHolder, direction);
                }

                @Override
                public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                        @NonNull RecyclerView.ViewHolder holder,
                                        float dX, float dY, int actionState, boolean isCurrentlyActive) {
                    drawSwipeBackground(c, holder, dX, actionState);
                    super.onChildDraw(c, rv, holder, dX, dY, actionState, isCurrentlyActive);
                }
            });
            pinTouchHelper.attachToRecyclerView(rvVault);
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    adapter.filter(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (fabAdd != null) fabAdd.setOnClickListener(v -> showAddDialog(null));
        fabBackup = findViewById(R.id.fabBackup);
        if (fabBackup != null) fabBackup.setOnClickListener(v -> showBackupSheet());
        if (btnAbout != null) btnAbout.setOnClickListener(v -> showAboutSecurityDialog());

        if (btnLanguage != null) {
            btnLanguage.setOnClickListener(v -> {
                isPersian = !isPersian;
                prefs.edit().putBoolean("is_persian", isPersian).apply();
                updateLanguageUI();
            });
        }

        if (btnThemeToggle != null) {
            btnThemeToggle.setOnClickListener(v -> {
                isDarkMode = !isDarkMode;
                prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply();
                // همگام‌سازی نایت‌مود (دیالوگ‌ها/فریم‌ها) + recreate خودکار توسط AppCompat
                AppCompatDelegate.setDefaultNightMode(isDarkMode
                        ? AppCompatDelegate.MODE_NIGHT_YES
                        : AppCompatDelegate.MODE_NIGHT_NO);
            });
        }

        updateLanguageUI();
        updateThemeUI();

        // اگر فرم افزودن/ویرایش هنگام چرخش صفحه باز بوده، بازسازی‌اش می‌کنیم.
        if (savedInstanceState != null && savedInstanceState.getBoolean("dlg_open", false)) {
            pendingDlgRestore = savedInstanceState;
            pendingDlgEditId = savedInstanceState.getString("dlg_editing_id");
            if (pendingDlgEditId == null) {
                // در حال «افزودن آیتم جدید» بودیم: نیازی به لیست نیست، همین حالا باز می‌کنیم.
                showAddDialog(null, pendingDlgRestore);
                pendingDlgRestore = null;
            }
            // وگرنه (در حال ویرایش) بعد از بارگذاری لیست در loadVaultData باز می‌شود.
        }

        loadVaultData();
        scheduleAutoLock(); // اگر Activity در پس‌زمینه از نو ساخته شود (مثلاً بعد از kill)، سریعاً قفل می‌شود
    }

    @Override
    protected void onStart() {
        super.onStart();
        totpHandler.post(totpRunnable);
        cancelAutoLock();
        // اگر کلیپی متعلق به ما در کلیپ‌بورد مانده (مثلاً پروسه در میانه‌ی
        // مهلت ۴۵ ثانیه‌ی پاک‌سازی خاتمه یافته)، حالا پاکش می‌کنیم.
        clearStaleClipboard();
    }

    @Override
    protected void onStop() {
        super.onStop();
        totpHandler.removeCallbacks(totpRunnable);
        scheduleAutoLock();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        totpHandler.removeCallbacks(totpRunnable);
        totpHandler.removeCallbacks(autoLockRunnable);
        clipboardClearHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // اگر فرم افزودن/ویرایش باز است، مقادیر فیلدها را برای چرخش صفحه نگه می‌داریم.
        if (currentAddDialog != null && currentAddDialog.isShowing()) {
            View dv = currentAddDialog.getWindow().getDecorView();
            outState.putBoolean("dlg_open", true);
            outState.putString("dlg_editing_id", editingItemId);
            outState.putString("dlg_title", fieldValue(dv.findViewById(R.id.etTitle)));
            outState.putString("dlg_category", fieldValue(dv.findViewById(R.id.etCategory)));
            outState.putString("dlg_username", fieldValue(dv.findViewById(R.id.etUsername)));
            outState.putString("dlg_password", fieldValue(dv.findViewById(R.id.etPassword)));
            outState.putString("dlg_totp", fieldValue(dv.findViewById(R.id.etTotpSecret)));
            outState.putString("dlg_website", fieldValue(dv.findViewById(R.id.etWebsite)));
            outState.putString("dlg_notes", fieldValue(dv.findViewById(R.id.etNotes)));
        }
    }

    private static String fieldValue(android.widget.EditText et) {
        return (et != null && et.getText() != null) ? et.getText().toString() : "";
    }

    private void scheduleAutoLock() {
        cancelAutoLock();
        totpHandler.postDelayed(autoLockRunnable, AUTO_LOCK_DELAY_MS);
    }

    private void cancelAutoLock() {
        totpHandler.removeCallbacks(autoLockRunnable);
    }

    /**
     * قفل فوری نشست (مثلاً هنگام ورود به پس‌زمینه)؛
     * کلید از حافظه پاک، کانکشن دیتابیس بسته، لیست (رکوردهای decrypt‌شده) از
     * حافظه تخلیه و کاربر به صفحه‌ی احراز هویت برمی‌گردد.
     */
    private void lockVaultNow() {
        if (isFinishing() || isChangingConfigurations()) return;
        VaultSession.clear();
        if (adapter != null) adapter.setItems(new ArrayList<>());
        try {
            dbHelper.close(); // کانکشن SQLCipher استخری را ببند تا اثری از فایل در حافظه نماند
        } catch (Exception ignored) {
        }
        Intent intent = new Intent(this, AuthActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void updateLanguageUI() {
        if (btnLanguage != null) {
            btnLanguage.setText(isPersian ? "FA" : "EN");
        }
        if (etSearch != null) {
            etSearch.setHint(isPersian ? "جستجو در عنوان، حساب و تگ‌ها..." : "Search titles, accounts, tags...");
        }
        if (tvAppTitle != null) {
            tvAppTitle.setText("OfflinePW");
        }
    }

    private void updateThemeUI() {
        int bgColor = Color.parseColor(isDarkMode ? "#09090B" : "#F4F4F5");
        int cardBg = Color.parseColor(isDarkMode ? "#18181B" : "#FFFFFF");
        int textColor = Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B");
        int strokeColor = Color.parseColor(isDarkMode ? "#27272A" : "#E4E4E7");

        if (mainRootLayout != null) mainRootLayout.setBackgroundColor(bgColor);
        if (appBarLayout != null) appBarLayout.setBackgroundColor(bgColor);
        if (tvAppTitle != null) tvAppTitle.setTextColor(textColor);
        if (btnThemeToggle != null) btnThemeToggle.setText(isDarkMode ? "DARK" : "LIGHT");

        if (btnAbout != null) {
            btnAbout.setBackgroundTintList(ColorStateList.valueOf(cardBg));
            btnAbout.setStrokeColor(ColorStateList.valueOf(strokeColor));
            btnAbout.setTextColor(textColor);
        }
        if (btnMore != null) {
            btnMore.setBackgroundTintList(ColorStateList.valueOf(cardBg));
            btnMore.setStrokeColor(ColorStateList.valueOf(strokeColor));
            btnMore.setIconTint(ColorStateList.valueOf(textColor));
        }
        if (btnLanguage != null) {
            btnLanguage.setBackgroundTintList(ColorStateList.valueOf(cardBg));
            btnLanguage.setStrokeColor(ColorStateList.valueOf(strokeColor));
            btnLanguage.setTextColor(textColor);
        }
        if (btnThemeToggle != null) {
            btnThemeToggle.setBackgroundTintList(ColorStateList.valueOf(cardBg));
            btnThemeToggle.setStrokeColor(ColorStateList.valueOf(strokeColor));
            btnThemeToggle.setTextColor(textColor); // باگ قبلی: رنگ متن جا افتاده بود (نامرئی در Light)
        }
        if (etSearch != null) {
            // بدون setBackgroundColor: drawable گردِ bg_search_input (با رنگ‌های معنایی) حفظ می‌شود.
            etSearch.setTextColor(textColor);
        }

        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void loadVaultData() {
        new Thread(() -> {
            try {
                AtomicBoolean corruptionFlag = new AtomicBoolean(false);
                List<VaultItem> items = dbHelper.getAllDecryptedItems(cryptoManager, corruptionFlag);
                final boolean hadBrokenRows = corruptionFlag.get();
                runOnUiThread(() -> {
                    if (adapter != null) adapter.setItems(items);
                    // فرم ویرایشی که هنگام چرخش صفحه باز بوده را بعد از آماده‌شدن لیست باز می‌کنیم.
                    if (pendingDlgRestore != null) {
                        VaultItem target = null;
                        for (VaultItem it : items) {
                            if (it.getId().equals(pendingDlgEditId)) {
                                target = it;
                                break;
                            }
                        }
                        Bundle rs = pendingDlgRestore;
                        pendingDlgRestore = null;
                        pendingDlgEditId = null;
                        if (target != null) showAddDialog(target, rs);
                        // اگر آیتم در لیست نبود (مثلاً حذف شده)، فرم را بی‌صدا رها می‌کنیم.
                    }
                    if (hadBrokenRows) {
                        Toast.makeText(this,
                                isPersian ? "هشدار: برخی از رکوردها قابل خواندن نبودند (احتمال خرابی یا تغییر کلید)."
                                          : "Warning: some records could not be decrypted (corruption or key change).",
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IllegalStateException e) {
                // کلید نشست موجود نیست (مثلاً قفل خودکار در پس‌زمینه زده شده) → به احراز هویت برگرد.
                runOnUiThread(() -> {
                    if (!isFinishing() && !isChangingConfigurations()) {
                        VaultSession.clear();
                        Intent intent = new Intent(this, AuthActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                });
            } catch (Exception e) {
                // خطای گذرای SQLite و... نباید نشست سالم را پاک کند؛ فقط اطلاع بده.
                runOnUiThread(() -> {
                    if (!isFinishing() && !isChangingConfigurations()) {
                        Toast.makeText(this,
                                isPersian ? "خطا در خواندن داده‌ها؛ لطفاً دوباره تلاش کنید."
                                          : "Error loading data; please try again.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }
        private void showAboutSecurityDialog() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 40, 48, 48);
        root.setBackgroundColor(Color.parseColor(isDarkMode ? "#18181B" : "#FFFFFF"));

        TextView tvHeaderTitle = new TextView(this);
        tvHeaderTitle.setText(isPersian ? "معماری و لایه‌های امنیتی OfflinePW" : "OfflinePW Security Architecture");
        tvHeaderTitle.setTextSize(19f);
        tvHeaderTitle.setTypeface(null, Typeface.BOLD);
        tvHeaderTitle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        root.addView(tvHeaderTitle);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2);
        dividerLp.topMargin = 16;
        dividerLp.bottomMargin = 4;
        divider.setLayoutParams(dividerLp);
        divider.setBackgroundColor(Color.parseColor(isDarkMode ? "#27272A" : "#E4E4E7"));
        root.addView(divider);

        String[][] itemsFa = {
            {"رمزنگاری کامل پایگاه داده (SQLCipher)", "تمام فایل پایگاه داده در سطح دیسک با استفاده از الگوریتم ۲۵۶ بیتی SQLCipher رمزنگاری شده است."},
            {"رمزنگاری دو لایه فیلدها", "علاوه بر رمزنگاری فایل پایگاه داده، تمام فیلدهای حساس (رمز عبور، کلید TOTP، یادداشت) مجدداً با AES-256-GCM رمزنگاری می‌شوند."},
            {"جداسازی کلید رمز عبور (DEK / KEK)", "کلید داده‌ها (DEK) به‌صورت کاملاً تصادفی ایجاد شده و توسط کلید مشتق‌شده از پسورد شما (KEK با ۶۰۰,۰۰۰ دور PBKDF2) محافظت می‌شود."},
            {"حذف کامل داده‌ها پس از ۳ تلاش ناموفق", "پس از ۳ بار وارد کردن رمز اشتباه، تمام رمزها، کلیدهای ۲FA و تنظیمات برنامه برای همیشه و به‌صورت غیرقابل بازگشت پاک می‌شوند تا داده‌های شما هرگز به دست مهاجم نیفتد."},
            {"بدون اتصال اینترنت", "برنامه هیچ مجوز اتصال به اینترنت ندارد؛ هیچ داده‌ای هرگز از دستگاه شما خارج نمی‌شود."},
            {"محافظت در برابر اسکرین‌شات", "با فعال‌سازی FLAG_SECURE، امکان اسکرین‌شات یا ضبط صفحه در تمام صفحات حساس برنامه غیرفعال است."}
        };

        String[][] itemsEn = {
            {"Full Database Encryption (SQLCipher)", "The entire SQLite database file is encrypted at-rest using 256-bit SQLCipher technology."},
            {"Double-Layer Field Encryption", "Beyond database file encryption, sensitive fields (passwords, TOTP secrets, notes) are individually encrypted using AES-256-GCM."},
            {"Key Hierarchy (DEK / KEK)", "Data Encryption Key (DEK) is randomly generated and wrapped using a Key Encryption Key (KEK) derived with 600,000 PBKDF2 iterations."},
            {"Total Wipe After 3 Failed Attempts", "After 3 incorrect master password attempts, all passwords, 2FA keys and app settings are permanently and irreversibly erased, so your data can never fall into an attacker's hands."},
            {"Zero Internet Access", "The app requests no internet permission whatsoever; no data ever leaves your device."},
            {"Screenshot Protection", "FLAG_SECURE is enabled across all sensitive screens, blocking screenshots and screen recording."}
        };

        String[][] items = isPersian ? itemsFa : itemsEn;

        for (String[] entry : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, 20, 0, 0);

            TextView tvItemTitle = new TextView(this);
            tvItemTitle.setText(entry[0]);
            tvItemTitle.setTextSize(14f);
            tvItemTitle.setTypeface(null, Typeface.BOLD);
            tvItemTitle.setTextColor(Color.parseColor("#3B82F6"));
            row.addView(tvItemTitle);

            TextView tvItemDesc = new TextView(this);
            tvItemDesc.setText(entry[1]);
            tvItemDesc.setTextSize(13f);
            tvItemDesc.setTextColor(Color.parseColor(isDarkMode ? "#A1A1AA" : "#71717A"));
            LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descLp.topMargin = 4;
            tvItemDesc.setLayoutParams(descLp);
            row.addView(tvItemDesc);

            root.addView(row);
        }

        MaterialButton btnClose = new MaterialButton(this);
        btnClose.setText(isPersian ? "تأیید" : "Close");
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.topMargin = 32;
        btnClose.setLayoutParams(closeLp);
        btnClose.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#27272A")));
        btnClose.setTextColor(Color.parseColor("#F4F4F5"));
        btnClose.setOnClickListener(v -> sheet.dismiss());
        root.addView(btnClose);

        sheet.setContentView(root);
        sheet.show();
    }

    private void showAddDialog(VaultItem existingItem) {
        showAddDialog(existingItem, null);
    }

    /**
     * @param existingItem آیتم در حال ویرایش یا null برای آیتم جدید
     * @param restored values فیلدها هنگام بازسازی بعد از چرخش صفحه (یا null)
     */
    private void showAddDialog(VaultItem existingItem, Bundle restored) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_vault_item, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        // ردیابی برای restore در چرخش صفحه
        currentAddDialog = dialog;
        editingItemId = existingItem != null ? existingItem.getId() : null;
        dialog.setOnDismissListener(d -> {
            currentAddDialog = null;
            editingItemId = null;
        });

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextInputLayout tilTitle = dialogView.findViewById(R.id.tilTitle);
        TextInputLayout tilCategory = dialogView.findViewById(R.id.tilCategory);
        TextInputLayout tilUsername = dialogView.findViewById(R.id.tilUsername);
        TextInputLayout tilPassword = dialogView.findViewById(R.id.tilPassword);
        TextInputLayout tilTotpSecret = dialogView.findViewById(R.id.tilTotpSecret);
        TextInputLayout tilWebsite = dialogView.findViewById(R.id.tilWebsite);
        TextInputLayout tilNotes = dialogView.findViewById(R.id.tilNotes);

        TextInputEditText etTitle = dialogView.findViewById(R.id.etTitle);
        TextInputEditText etCategory = dialogView.findViewById(R.id.etCategory);
        TextInputEditText etUsername = dialogView.findViewById(R.id.etUsername);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etPassword);
        TextInputEditText etTotpSecret = dialogView.findViewById(R.id.etTotpSecret);
        TextInputEditText etWebsite = dialogView.findViewById(R.id.etWebsite);
        TextInputEditText etNotes = dialogView.findViewById(R.id.etNotes);

        MaterialButton btnGenerate = dialogView.findViewById(R.id.btnGenerate);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancel);
        MaterialButton btnSave = dialogView.findViewById(R.id.btnSave);

        if (isPersian) {
            tvDialogTitle.setText(existingItem != null ? "ویرایش رکورد" : "ثبت رکورد جدید");
            tilTitle.setHint("عنوان (مثلاً Google یا کارت بانک)");
            tilCategory.setHint("دسته‌بندی (LOGIN, CARD, NOTE, WIFI)");
            tilUsername.setHint("نام کاربری یا ایمیل یا شماره کارت");
            tilPassword.setHint("رمز عبور");
            if (tilTotpSecret != null) tilTotpSecret.setHint("کلید TOTP دو‌مرحله‌ای (اختیاری، Base32)");
            if (tilWebsite != null) tilWebsite.setHint("وبسایت (ها) — هر کدام در یک خط");
            tilNotes.setHint("یادداشت امن (اختیاری)");
            btnGenerate.setText("ساخت رمز");
            btnCancel.setText("انصراف");
            btnSave.setText("ذخیره");
        } else {
            tvDialogTitle.setText(existingItem != null ? "Edit Vault Item" : "New Vault Item");
            tilTitle.setHint("Title (e.g. Google, Bank Card)");
            tilCategory.setHint("Category (LOGIN, CARD, NOTE, WIFI)");
            tilUsername.setHint("Username / Email / Card Number");
            tilPassword.setHint("Password");
            if (tilTotpSecret != null) tilTotpSecret.setHint("2FA TOTP Secret Key (Optional, Base32)");
            if (tilWebsite != null) tilWebsite.setHint("Website(s) - one per line");
            tilNotes.setHint("Secure Notes (Optional)");
            btnGenerate.setText("Generate");
            btnCancel.setText("Cancel");
            btnSave.setText("Save");
        }

        btnCancel.setTextSize(14f);
        btnSave.setTextSize(14f);

        if (existingItem != null) {
            etTitle.setText(existingItem.getTitle());
            etCategory.setText(existingItem.getCategory());
            etUsername.setText(existingItem.getUsername());
            etPassword.setText(existingItem.getPassword());
            if (etTotpSecret != null) etTotpSecret.setText(existingItem.getTotpSecret());
            if (etWebsite != null) etWebsite.setText(existingItem.getWebsite());
            etNotes.setText(existingItem.getNotes());
        }

        // بازسازی بعد از چرخش صفحه: مقداری که کاربر تایپ کرده بود برمی‌گردد.
        if (restored != null) {
            etTitle.setText(restored.getString("dlg_title", ""));
            etCategory.setText(restored.getString("dlg_category", ""));
            etUsername.setText(restored.getString("dlg_username", ""));
            etPassword.setText(restored.getString("dlg_password", ""));
            if (etTotpSecret != null) etTotpSecret.setText(restored.getString("dlg_totp", ""));
            if (etWebsite != null) etWebsite.setText(restored.getString("dlg_website", ""));
            etNotes.setText(restored.getString("dlg_notes", ""));
        }

        btnGenerate.setOnClickListener(v -> {
            etPassword.setText(generateStrongPassword(16));
            Toast.makeText(this, isPersian ? "رمز عبور قدرتمند تولید شد" : "Strong password generated", Toast.LENGTH_SHORT).show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
            String category = etCategory.getText() != null ? etCategory.getText().toString().trim() : "LOGIN";
            String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            String totp = (etTotpSecret != null && etTotpSecret.getText() != null) ? etTotpSecret.getText().toString().trim() : "";
            String website = (etWebsite != null && etWebsite.getText() != null) ? etWebsite.getText().toString().trim() : "";
            String notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";

            if (title.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, isPersian ? "عنوان و رمز عبور الزامی است" : "Title & Password required", Toast.LENGTH_SHORT).show();
                return;
            }

            String id = existingItem != null ? existingItem.getId() : UUID.randomUUID().toString();
            // حالت پین در ویرایش حفظ می‌شود (تغییر پین فقط از طریق اسوایپ انجام می‌شود)
            boolean keepPinned = existingItem != null && existingItem.isPinned();
            long nowTs = System.currentTimeMillis();
            long createdTs = existingItem != null && existingItem.getCreatedAt() > 0
                    ? existingItem.getCreatedAt() : nowTs;
            VaultItem item = new VaultItem(id, title, category, username, password, notes, totp, website, keepPinned, createdTs, nowTs);

            if (!totp.isEmpty() && !TotpGenerator.isValidSecret(totp)) {
                Toast.makeText(this, isPersian
                                ? "کلید TOTP نامعتبر است (باید Base32 و حداقل ۱۶ کاراکتر باشد)"
                                : "Invalid TOTP secret (must be Base32, min 16 chars)",
                        Toast.LENGTH_LONG).show();
                return;
            }

            btnSave.setEnabled(false);
            new Thread(() -> {
                try {
                    dbHelper.insertItem(item, cryptoManager);
                    runOnUiThread(() -> {
                        if (isFinishing()) return;
                        dialog.dismiss();
                        loadVaultData();
                        Toast.makeText(this, isPersian ? "با موفقیت ذخیره شد" : "Saved successfully", Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(this, isPersian
                                        ? "خطا در ذخیره‌سازی؛ دوباره تلاش کنید"
                                        : "Save failed; please try again",
                                Toast.LENGTH_LONG).show();
                    });
                }
            }).start();
        });

        dialog.show();
    }

    private void showEditOrDeleteDialog(VaultItem item) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 40, 48, 48);
        root.setBackgroundColor(Color.parseColor(isDarkMode ? "#18181B" : "#FFFFFF"));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 20);

        TextView tvHeaderTitle = new TextView(this);
        tvHeaderTitle.setText(item.getTitle());
        tvHeaderTitle.setTextSize(19f);
        tvHeaderTitle.setTypeface(null, Typeface.BOLD);
        tvHeaderTitle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        LinearLayout.LayoutParams headerTitleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(tvHeaderTitle, headerTitleLp);

        TextView tvCategoryBadge = new TextView(this);
        String cat = item.getCategory() != null && !item.getCategory().isEmpty() ? item.getCategory().toUpperCase() : "LOGIN";
        tvCategoryBadge.setText(cat);
        tvCategoryBadge.setTextSize(11f);
        tvCategoryBadge.setTypeface(null, Typeface.BOLD);
        tvCategoryBadge.setTextColor(Color.parseColor("#3B82F6"));
        tvCategoryBadge.setBackgroundColor(Color.parseColor(isDarkMode ? "#1E293B" : "#EFF6FF"));
        tvCategoryBadge.setPadding(20, 8, 20, 8);
        header.addView(tvCategoryBadge);
        root.addView(header);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
        divider.setBackgroundColor(Color.parseColor(isDarkMode ? "#27272A" : "#E4E4E7"));
        root.addView(divider);

        if (item.getUsername() != null && !item.getUsername().isEmpty()) {
            root.addView(buildFieldRow(isPersian ? "نام کاربری / شماره" : "Username / Card", item.getUsername(), false));
        }
        if (item.getPassword() != null && !item.getPassword().isEmpty()) {
            root.addView(buildFieldRow(isPersian ? "رمز عبور" : "Password", item.getPassword(), true));
        }
        if (item.getTotpSecret() != null && !item.getTotpSecret().trim().isEmpty()) {
            root.addView(buildTotpFieldRow(item));
        }
        if (item.getWebsite() != null && !item.getWebsite().trim().isEmpty()) {
            root.addView(buildWebsiteFieldRow(item));
        }
        if (item.getNotes() != null && !item.getNotes().isEmpty()) {
            root.addView(buildFieldRow(isPersian ? "یادداشت" : "Notes", item.getNotes(), false));
        }

        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setPadding(0, 32, 0, 0);

        MaterialButton btnEdit = new MaterialButton(this);
        btnEdit.setText(isPersian ? "ویرایش" : "Edit");
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        editLp.setMarginEnd(12);
        btnEdit.setLayoutParams(editLp);
        btnEdit.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#27272A")));
        btnEdit.setTextColor(Color.parseColor("#F4F4F5"));
        btnEdit.setOnClickListener(v -> { sheet.dismiss(); showAddDialog(item); });
        actionsRow.addView(btnEdit);

        MaterialButton btnDelete = new MaterialButton(this);
        btnDelete.setText(isPersian ? "حذف" : "Delete");
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnDelete.setLayoutParams(deleteLp);
        btnDelete.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#7F1D1D")));
        btnDelete.setTextColor(Color.parseColor("#FEE2E2"));
        btnDelete.setOnClickListener(v -> {
            sheet.dismiss();
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(isPersian ? "حذف رکورد" : "Delete Item")
                    .setMessage(isPersian
                            ? "«" + item.getTitle() + "» برای همیشه حذف شود؟ این عمل قابل بازگشت نیست."
                            : "Delete \"" + item.getTitle() + "\" permanently? This cannot be undone.")
                    .setPositiveButton(isPersian ? "حذف" : "Delete", (d, w) -> {
                        new Thread(() -> {
                            try {
                                dbHelper.deleteItem(item.getId());
                                runOnUiThread(() -> {
                                    loadVaultData();
                                    Toast.makeText(this, isPersian ? "رکورد حذف شد" : "Item deleted", Toast.LENGTH_SHORT).show();
                                });
                            } catch (Exception e) {
                                runOnUiThread(() -> Toast.makeText(this,
                                        isPersian ? "خطا در حذف؛ دوباره تلاش کنید" : "Delete failed; try again",
                                        Toast.LENGTH_SHORT).show());
                            }
                        }).start();
                    })
                    .setNegativeButton(isPersian ? "انصراف" : "Cancel", null)
                    .show();
        });
        actionsRow.addView(btnDelete);

        root.addView(actionsRow);
        sheet.setContentView(root);
        sheet.show();
    }

    private LinearLayout buildFieldRow(String label, String value, boolean sensitive) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 24, 0, 0);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(12f);
        tvLabel.setTextColor(Color.parseColor(isDarkMode ? "#71717A" : "#A1A1AA"));
        row.addView(tvLabel);

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.setPadding(0, 6, 0, 0);

        TextView tvValue = new TextView(this);
        tvValue.setTextSize(15f);
        tvValue.setTypeface(sensitive ? Typeface.MONOSPACE : Typeface.DEFAULT);
        tvValue.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        final boolean[] revealed = {!sensitive};
        Runnable updateText = () -> tvValue.setText(revealed[0] ? value : "••••••••••••");
        updateText.run();

        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueRow.addView(tvValue, valueLp);

        int iconColor = Color.parseColor(isDarkMode ? "#A1A1AA" : "#71717A");

        if (sensitive) {
            ImageView ivEye = new ImageView(this);
            ivEye.setImageResource(R.drawable.ic_visibility_off);
            ivEye.setColorFilter(iconColor);
            LinearLayout.LayoutParams eyeLp = new LinearLayout.LayoutParams(56, 56);
            eyeLp.setMarginStart(16);
            ivEye.setLayoutParams(eyeLp);
            ivEye.setOnClickListener(v -> {
                revealed[0] = !revealed[0];
                updateText.run();
                ivEye.setImageResource(revealed[0] ? R.drawable.ic_visibility : R.drawable.ic_visibility_off);
            });
            valueRow.addView(ivEye);
        }

        ImageView ivCopy = new ImageView(this);
        ivCopy.setImageResource(R.drawable.ic_content_copy);
        ivCopy.setColorFilter(iconColor);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(56, 56);
        copyLp.setMarginStart(16);
        ivCopy.setLayoutParams(copyLp);
        ivCopy.setOnClickListener(v -> copyToClipboard(label, value));
        valueRow.addView(ivCopy);

        row.addView(valueRow);
        return row;
    }

    private LinearLayout buildTotpFieldRow(VaultItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 24, 0, 0);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(isPersian ? "کد یکبارمصرف (TOTP)" : "2FA Code (TOTP)");
        tvLabel.setTextSize(12f);
        tvLabel.setTextColor(Color.parseColor(isDarkMode ? "#71717A" : "#A1A1AA"));
        row.addView(tvLabel);

        LinearLayout valueRow = new LinearLayout(this);
        valueRow.setOrientation(LinearLayout.HORIZONTAL);
        valueRow.setGravity(Gravity.CENTER_VERTICAL);
        valueRow.setPadding(0, 6, 0, 0);

        TextView tvValue = new TextView(this);
        tvValue.setTextSize(16f);
        tvValue.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvValue.setTextColor(Color.parseColor("#F59E0B"));
        tvValue.setText("••••••");
        tvValue.setOnClickListener(v -> {
            String code = TotpGenerator.generateCode(item.getTotpSecret());
            tvValue.setText(code);
            copyToClipboard(isPersian ? "کد TOTP" : "TOTP Code", code);
            tvValue.postDelayed(() -> tvValue.setText("••••••"), 5000);
        });

        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        valueRow.addView(tvValue, valueLp);
        row.addView(valueRow);
        return row;
    }

    private LinearLayout buildWebsiteFieldRow(VaultItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 24, 0, 0);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(isPersian ? "وبسایت" : "Website");
        tvLabel.setTextSize(12f);
        tvLabel.setTextColor(Color.parseColor(isDarkMode ? "#71717A" : "#A1A1AA"));
        row.addView(tvLabel);

        int iconColor = Color.parseColor(isDarkMode ? "#A1A1AA" : "#71717A");
        String[] lines = item.getWebsite().split("\\r?\\n");

        for (String rawLine : lines) {
            final String url = rawLine.trim();
            if (url.isEmpty()) continue;

            LinearLayout valueRow = new LinearLayout(this);
            valueRow.setOrientation(LinearLayout.HORIZONTAL);
            valueRow.setGravity(Gravity.CENTER_VERTICAL);
            valueRow.setPadding(0, 6, 0, 0);

            ImageView ivGlobe = new ImageView(this);
            ivGlobe.setImageResource(R.drawable.ic_public);
            ivGlobe.setColorFilter(Color.parseColor("#3B82F6"));
            LinearLayout.LayoutParams globeLp = new LinearLayout.LayoutParams(44, 44);
            globeLp.setMarginEnd(12);
            ivGlobe.setLayoutParams(globeLp);
            valueRow.addView(ivGlobe);

            TextView tvValue = new TextView(this);
            tvValue.setTextSize(14f);
            tvValue.setTextColor(Color.parseColor("#3B82F6"));
            tvValue.setText(url);
            tvValue.setSingleLine(true);
            tvValue.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tvValue.setLayoutParams(valueLp);
            valueRow.addView(tvValue);

            ImageView ivCopy = new ImageView(this);
            ivCopy.setImageResource(R.drawable.ic_content_copy);
            ivCopy.setColorFilter(iconColor);
            LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(44, 44);
            copyLp.setMarginStart(16);
            ivCopy.setLayoutParams(copyLp);
            ivCopy.setOnClickListener(v -> copyToClipboard(isPersian ? "وبسایت" : "Website", url));
            valueRow.addView(ivCopy);

            row.addView(valueRow);
        }

        return row;
    }

    private void copyToClipboard(String label, String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, isPersian ? "موردی برای کپی وجود ندارد" : "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = new PersistableBundle();
            extras.putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true);
            // مارکر ماندگار: اگر در این ۴۵ ثانیه پروسه خاتمه یابد، بازگشت بعدی
            // به اپ در onStart این مارکر را می‌بیند و کلیپ را پاک می‌کند.
            extras.putLong(CLIP_MARKER_KEY, System.currentTimeMillis());
            clip.getDescription().setExtras(extras);
        }

        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            lastCopiedText = text;
            Toast.makeText(this, label + (isPersian ? " کپی شد" : " copied"), Toast.LENGTH_SHORT).show();

            // پاک‌سازی معوق (Handler مشترک است تا در onDestroy همه‌چیز remove شود)
            clipboardClearHandler.removeCallbacksAndMessages(null);
            clipboardClearHandler.postDelayed(this::clearStaleClipboard, CLIP_CLEAR_DELAY_MS);
        }
    }

    /**
     * اگر کلیپ فعلیِ کلیپ‌بورد هنوز متعلق به ماست (مارکر ماندگار API 33+ یا مقایسه‌ی
     * متن در نسخه‌های قدیمی)، پس از پایان مهلت پاکش می‌کند.
     *
     * در onStart (بعد از هر بازگشت از پس‌زمینه یا مرگ پروسه) و به‌عنوان تایمر ۴۵
     * ثانیه‌ای فراخوانی می‌شود. اگر مهلت هنوز تمام نشده باشد (مثلاً Activity destroy و
     * recreate شده و تایمر قبلی از بین رفته)، زمان باقی‌مانده را مجدداً زمان‌بندی می‌کند
     * و زودتر از موعد پاک نمی‌کند.
     */
    private void clearStaleClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        ClipData current = clipboard.getPrimaryClip();
        if (current == null || current.getItemCount() == 0) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PersistableBundle extras = current.getDescription().getExtras();
            if (extras == null || !extras.containsKey(CLIP_MARKER_KEY)) return; // کلپ متعلق به ماست نه
            long elapsed = System.currentTimeMillis() - extras.getLong(CLIP_MARKER_KEY, System.currentTimeMillis());
            if (elapsed < CLIP_CLEAR_DELAY_MS) {
                // هنوز در مهلت؛ تایمر باقی‌مانده را (باز)راه‌اندازی کن
                clipboardClearHandler.removeCallbacksAndMessages(null);
                clipboardClearHandler.postDelayed(this::clearStaleClipboard, CLIP_CLEAR_DELAY_MS - elapsed);
                return;
            }
        } else {
            boolean isOurs = lastCopiedText != null
                    && lastCopiedText.equals(String.valueOf(current.getItemAt(0).getText()));
            if (!isOurs) return;
        }

        clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
        lastCopiedText = null;
    }

    // ================= منوی بیشتر: ترتیب لیست + گزارش سلامت رمزها =================

    private void showVaultOptionsMenu() {
        String[] opts = isPersian
                ? new String[]{"گزارش سلامت رمزها", "ترتیب لیست"}
                : new String[]{"Password Health Report", "List Order"};
        new AlertDialog.Builder(this)
                .setTitle(isPersian ? "بیشتر" : "More")
                .setItems(opts, (d, which) -> {
                    if (which == 0) showPasswordHealthDialog();
                    else showSortDialog();
                })
                .show();
    }

    private void showSortDialog() {
        String[] opts = isPersian
                ? new String[]{"پیش‌فرض (ترتیب ثبت)", "عنوان (الفبا)", "دسته‌بندی", "آخرین به‌روزرسانی (جدید اول)"}
                : new String[]{"Default (creation order)", "Title (A-Z)", "Category", "Recently updated first"};
        new AlertDialog.Builder(this)
                .setTitle(isPersian ? "ترتیب لیست" : "List Order")
                .setSingleChoiceItems(opts, adapter != null ? adapter.getSortMode() : 0, (d, which) -> {
                    prefs.edit().putInt("vault_sort_mode", which).apply();
                    if (adapter != null) adapter.setSortMode(which);
                    d.dismiss();
                })
                .show();
    }

    private static final long STALE_AFTER_MS = 180L * 24 * 60 * 60 * 1000;

    /**
     * گزارش سلامت — کاملاً آفلاین و محلی؛ از روی آیتم‌های decrypt‌شده در حافظه ساخته
     * می‌شود و هیچ داده‌ای از دستگاه خارج نمی‌شود:
     *  - رمزهای تکراری (خطر اصلی: شکستن یکی = ورود به همه)
     *  - رمزهای ضعیف (آنتروپی شانون کم یا الگوی ساده)
     *  - رمزهای کهنه (بیش از ۱۸۰ روز به‌روزرسانی‌نشده)
     */
    private void showPasswordHealthDialog() {
        if (adapter == null) return;
        List<VaultItem> items = adapter.getFullSnapshot();

        java.util.LinkedHashMap<String, List<String>> byPassword = new java.util.LinkedHashMap<>();
        List<String> weakTitles = new ArrayList<>();
        List<String> staleLines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (VaultItem it : items) {
            String pw = it.getPassword();
            if (pw == null || pw.isEmpty()) continue;
            byPassword.computeIfAbsent(pw, k -> new ArrayList<>()).add(healthTitleOf(it));
            if (isWeakPassword(pw)) weakTitles.add(healthTitleOf(it));
            long ref = it.getUpdatedAt() > 0 ? it.getUpdatedAt() : it.getCreatedAt();
            if (ref > 0 && now - ref > STALE_AFTER_MS) {
                staleLines.add(healthTitleOf(it) + " (" + ((int) ((now - ref) / (24L * 3600 * 1000)))
                        + (isPersian ? " روز)" : " days)"));
            }
        }

        List<List<String>> dupGroups = new ArrayList<>();
        int dupItems = 0;
        for (List<String> g : byPassword.values()) {
            if (g.size() > 1) { dupGroups.add(g); dupItems += g.size(); }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(isPersian ? "بررسی " + items.size() + " آیتم:\n\n"
                            : "Checked " + items.size() + " item(s):\n\n");
        boolean anyIssue = false;
        if (!dupGroups.isEmpty()) {
            anyIssue = true;
            sb.append(isPersian ? "🔴 رمز تکراری — " + dupItems + " آیتم در " + dupGroups.size() + " گروه:\n"
                                : "🔴 Reused passwords — " + dupItems + " items in " + dupGroups.size() + " group(s):\n");
            int shown = 0;
            for (List<String> g : dupGroups) {
                if (shown++ >= 5) { sb.append(isPersian ? "… و " + (dupGroups.size() - shown + 1) + " گروه دیگر\n" : "… and " + (dupGroups.size() - shown + 1) + " more group(s)\n"); break; }
                sb.append("   ").append(String.join(isPersian ? "، " : ", ", g)).append("\n");
            }
        }
        if (!weakTitles.isEmpty()) {
            anyIssue = true;
            sb.append(isPersian ? "🟠 رمز ضعیف — " + weakTitles.size() + " آیتم:\n" : "🟠 Weak passwords — " + weakTitles.size() + " item(s):\n");
            for (int i = 0; i < Math.min(6, weakTitles.size()); i++) sb.append("   ").append(weakTitles.get(i)).append("\n");
            if (weakTitles.size() > 6) sb.append(isPersian ? "   … و " + (weakTitles.size() - 6) + " مورد دیگر\n" : "   … and " + (weakTitles.size() - 6) + " more\n");
        }
        if (!staleLines.isEmpty()) {
            anyIssue = true;
            sb.append(isPersian ? "🟡 بیش از ۱۸۰ روز بی‌تغییر — " + staleLines.size() + " آیتم:\n"
                                : "🟡 Unchanged for 180+ days — " + staleLines.size() + " item(s):\n");
            for (int i = 0; i < Math.min(6, staleLines.size()); i++) sb.append("   ").append(staleLines.get(i)).append("\n");
            if (staleLines.size() > 6) sb.append(isPersian ? "   … و " + (staleLines.size() - 6) + " مورد دیگر\n" : "   … and " + (staleLines.size() - 6) + " more\n");
        }
        if (!anyIssue) {
            sb.append(isPersian ? "✅ همه‌چیز سالم است — بدون تکرار، بدون رمز ضعیف، بدون مورد کهنه."
                                : "✅ All good — no reuse, no weak passwords, nothing stale.");
        }

        new AlertDialog.Builder(this)
                .setTitle(isPersian ? "گزارش سلامت رمزها" : "Password Health Report")
                .setMessage(sb.toString())
                .setPositiveButton(isPersian ? "بستن" : "Close", null)
                .show();
    }

    private String healthTitleOf(VaultItem it) {
        String t = it.getTitle();
        return (t == null || t.isEmpty()) ? (isPersian ? "بی‌نام" : "untitled") : t;
    }

    /** سنجش ساده و محافظه‌کارانه: طول + آنتروپی شانون + الگوی تک‌کلاسه. */
    static boolean isWeakPassword(String pw) {
        if (pw.length() < 10) return true;
        if (shannonEntropyBits(pw) < 45) return true;
        boolean allDigits = true, allLower = true;
        for (char c : pw.toCharArray()) {
            if (!Character.isDigit(c)) allDigits = false;
            if (!(c >= 'a' && c <= 'z')) allLower = false;
            if (!allDigits && !allLower) break;
        }
        return (allDigits || allLower) && pw.length() < 16;
    }

    static double shannonEntropyBits(String s) {
        java.util.HashMap<Character, Integer> freq = new java.util.HashMap<>();
        for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);
        int n = s.length();
        double h = 0;
        for (int cnt : freq.values()) {
            double p = (double) cnt / n;
            h -= p * (Math.log(p) / Math.log(2));
        }
        return h * n;
    }

    /** تاریخ کوتاه برای خط «به‌روزرسانی» کارت (جلالی برای فارسی، از طریق ICU). */
    private String formatVaultDate(long millis) {
        try {
            Locale loc = isPersian ? new Locale("fa") : Locale.US;
            return java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, loc)
                    .format(new java.util.Date(millis));
        } catch (Exception e) {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date(millis));
        }
    }

    // ================= پین (اسوایپ چپ = پین / اسوایپ راست = آن‌پین) =================

    private void handlePinSwipe(RecyclerView.ViewHolder viewHolder, int direction) {
        int pos = viewHolder.getAdapterPosition();
        if (pos == RecyclerView.NO_POSITION || adapter == null) return;
        VaultItem item = adapter.getItem(pos);
        if (item == null) {
            adapter.notifyItemChanged(pos);
            return;
        }
        // طبق درخواست کاربر: اسوایپ به چپ = پین، اسوایپ به راست = آن‌پین
        boolean wantPinned = (direction == ItemTouchHelper.LEFT);
        if (item.isPinned() == wantPinned) {
            // تغییری لازم نیست؛ ردیف را برگردان و راهنما نشان بده
            adapter.notifyItemChanged(pos);
            Toast.makeText(this, isPersian
                    ? (wantPinned ? "این آیتم قبلاً پین شده است" : "این آیتم پین نیست")
                    : (wantPinned ? "Item is already pinned" : "Item is not pinned"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // ابتدا ردیف را برگردان (اگر خطایی رخ داد ردیف گم نمی‌شود)، بعد DB را به‌روز کن
        adapter.notifyItemChanged(pos);
        final String itemId = item.getId();
        new Thread(() -> {
            try {
                dbHelper.setPinned(itemId, wantPinned);
                runOnUiThread(() -> {
                    if (isFinishing() || isChangingConfigurations()) return;
                    // دوباره مرتب‌سازی: آیتم پین‌شده بالای لیست می‌رود
                    loadVaultData();
                    Toast.makeText(this, isPersian
                            ? (wantPinned ? "آیتم پین شد" : "پین برداشته شد")
                            : (wantPinned ? "Item pinned" : "Pin removed"),
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        isPersian ? "به‌روزرسانی پین ناموفق بود؛ دوباره تلاش کنید"
                                  : "Pin update failed; please try again",
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /**
     * هنگام اسوایپ، نوار رنگی و آیکن پین را پشت ردیف رسم می‌کند.
     * اسوایپ به چپ (dX < 0) → فاصله در سمت راست باز می‌شود (اقدام: پین، رنگ کهربایی).
     * اسوایپ به راست (dX > 0) → فاصله در سمت چپ (اقدام: آن‌پین، رنگ قرمز).
     */
    private void drawSwipeBackground(Canvas canvas, RecyclerView.ViewHolder holder, float dX, int actionState) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE || Math.abs(dX) < 2f) return;
        View itemView = holder.itemView;
        float left = itemView.getLeft();
        float right = itemView.getRight();
        float top = itemView.getTop();
        float bottom = itemView.getBottom();
        boolean toLeft = dX < 0;

        float rectLeft = toLeft ? left : left - dX;
        float rectRight = toLeft ? right - dX : right;
        swipePaint.setColor(toLeft ? SWIPE_PIN_COLOR : SWIPE_UNPIN_COLOR);
        float radius = 24;
        canvas.drawRoundRect(rectLeft, top, rectRight, bottom, radius, radius, swipePaint);

        // آیکن در مرکزِ فاصله (وقتی فاصله کافی باز شده)
        float gapWidth = Math.abs(dX);
        float iconSize = 32;
        if (gapWidth >= iconSize + 12) {
            Drawable icon = getSwipeIcon();
            if (icon != null) {
                float gapStart = toLeft ? right : left - dX;
                float iconX = gapStart + (gapWidth - iconSize) / 2f;
                float iconY = (top + bottom) / 2f - iconSize / 2f;
                icon.setBounds(Math.round(iconX), Math.round(iconY),
                        Math.round(iconX + iconSize), Math.round(iconY + iconSize));
                icon.draw(canvas);
            }
        }
    }

    private Drawable getSwipeIcon() {
        if (swipeIconPin == null) {
            swipeIconPin = ContextCompat.getDrawable(this, R.drawable.ic_pin);
            if (swipeIconPin != null) {
                swipeIconPin = swipeIconPin.mutate();
                swipeIconPin.setTint(Color.WHITE);
            }
        }
        return swipeIconPin;
    }

    // ================= بکاپ / بازیابی =================

    private static final int MIN_BACKUP_PASSWORD_LENGTH = 10;

    private void showBackupSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 20, 24, 24);
        root.setBackgroundColor(Color.parseColor(isDarkMode ? "#18181B" : "#FFFFFF"));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(isPersian ? "بکاپ و بازیابی" : "Backup & Restore");
        tvTitle.setTextSize(19f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        root.addView(tvTitle);

        MaterialButton btnExport = new MaterialButton(this);
        btnExport.setText(isPersian ? "ساخت بکاپ (خروجی)" : "Create Backup (Export)");
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        exportLp.topMargin = 16;
        btnExport.setLayoutParams(exportLp);
        btnExport.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E4E4E7")));
        btnExport.setTextColor(Color.parseColor("#09090B"));
        btnExport.setOnClickListener(v -> {
            sheet.dismiss();
            showBackupExportDialog();
        });
        root.addView(btnExport);

        MaterialButton btnImport = new MaterialButton(this);
        btnImport.setText(isPersian ? "بازیابی از بکاپ (ورودی)" : "Restore from Backup (Import)");
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        importLp.topMargin = 10;
        btnImport.setLayoutParams(importLp);
        btnImport.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#27272A")));
        btnImport.setTextColor(Color.parseColor("#F4F4F5"));
        btnImport.setOnClickListener(v -> {
            sheet.dismiss();
            openBackupLauncher.launch(new String[]{"*/*"});
        });
        root.addView(btnImport);

        TextView tvNote = new TextView(this);
        tvNote.setText(isPersian
                ? "بکاپ به‌صورت سر-به-سر رمزنگاری می‌شود (AES-256-GCM). بدون «رمز بکاپ» محتوای فایل غیرقابل‌خواندن است و فراموشی آن، بازیابی را برای همیشه غیرممکن می‌کند."
                : "Backups are end-to-end encrypted (AES-256-GCM). Without the backup password the file is unreadable, and a forgotten backup password makes recovery permanently impossible.");
        tvNote.setTextSize(12f);
        tvNote.setTextColor(Color.parseColor(isDarkMode ? "#A1A1AA" : "#71717A"));
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteLp.topMargin = 16;
        tvNote.setLayoutParams(noteLp);
        root.addView(tvNote);

        sheet.setContentView(root);
        sheet.show();
    }

    private void showBackupExportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 20, 24, 8);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(isPersian ? "رمز بکاپ" : "Backup Password");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        root.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(isPersian
                ? "رمزی با حداقل " + MIN_BACKUP_PASSWORD_LENGTH + " کاراکتر انتخاب کنید. برای بازگرداندن همین بکاپ، به این رمز نیاز دارید."
                : "Choose a password of at least " + MIN_BACKUP_PASSWORD_LENGTH + " characters. You will need it to restore this backup.");
        tvDesc.setTextSize(13f);
        tvDesc.setTextColor(Color.parseColor(isDarkMode ? "#A1A1AA" : "#71717A"));
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = 8;
        tvDesc.setLayoutParams(descLp);
        root.addView(tvDesc);

        TextInputLayout tilPass = new TextInputLayout(this);
        tilPass.setHint(isPersian ? "رمز بکاپ" : "Backup Password");
        tilPass.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText etPass = new TextInputEditText(this);
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilPass.addView(etPass);
        LinearLayout.LayoutParams tilLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tilLp.topMargin = 16;
        tilPass.setLayoutParams(tilLp);
        root.addView(tilPass);

        MaterialButton btnCreate = new MaterialButton(this);
        btnCreate.setText(isPersian ? "ساخت فایل بکاپ" : "Create Backup File");
        btnCreate.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E4E4E7")));
        btnCreate.setTextColor(Color.parseColor("#09090B"));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = 16;
        btnCreate.setLayoutParams(btnLp);
        root.addView(btnCreate);

        AlertDialog dialog = builder.setView(root).create();
        dialog.show();

        btnCreate.setOnClickListener(v -> {
            String pass = etPass.getText() != null ? etPass.getText().toString() : "";
            if (pass.length() < MIN_BACKUP_PASSWORD_LENGTH) {
                Toast.makeText(this, isPersian
                        ? ("رمز بکاپ باید حداقل " + MIN_BACKUP_PASSWORD_LENGTH + " کاراکتر باشد")
                        : ("Backup password must be at least " + MIN_BACKUP_PASSWORD_LENGTH + " characters"),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            btnCreate.setEnabled(false);
            doBackupExport(pass, dialog, btnCreate);
        });
    }

    private void doBackupExport(String backupPassword, AlertDialog dialog, MaterialButton busyButton) {
        new Thread(() -> {
            try {
                List<VaultItem> items = dbHelper.getAllDecryptedItems(cryptoManager, null);
                String json = buildBackupJson(items);
                byte[] fileBytes = BackupManager.encrypt(json, backupPassword);

                File base = getExternalFilesDir(null) != null ? getExternalFilesDir(null) : getFilesDir();
                File dir = new File(base, "backups");
                if (!dir.exists() && !dir.mkdirs()) throw new java.io.IOException("cannot create backup dir");
                String ts = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new java.util.Date());
                File outFile = new File(dir, "OfflinePW-Backup-" + ts + ".opwb");
                try (OutputStream os = new java.io.FileOutputStream(outFile)) {
                    os.write(fileBytes);
                }
                Uri shareUri = FileProvider.getUriForFile(this, PROVIDER_AUTHORITY, outFile);

                runOnUiThread(() -> {
                    if (isFinishing() || isChangingConfigurations()) return;
                    dialog.dismiss();
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("application/octet-stream");
                    share.putExtra(Intent.EXTRA_STREAM, shareUri);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(share, isPersian ? "اشتراک‌گذاری فایل بکاپ" : "Share backup file"));
                    Toast.makeText(this, isPersian
                            ? ("بکاپ " + items.size() + " آیتم ساخته شد")
                            : ("Backup of " + items.size() + " item(s) created"),
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isChangingConfigurations()) return;
                    busyButton.setEnabled(true);
                    dialog.dismiss();
                    Toast.makeText(this, isPersian
                            ? "ساخت بکاپ ناموفق بود؛ دوباره تلاش کنید"
                            : "Backup failed; please try again",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private String buildBackupJson(List<VaultItem> items) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "offlinepw-backup-v1-vault");
        root.put("app", "OfflinePW");
        root.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                .format(new java.util.Date()));
        JSONArray arr = new JSONArray();
        for (VaultItem it : items) {
            JSONObject o = new JSONObject();
            o.put("id", it.getId());
            o.put("title", it.getTitle());
            o.put("category", it.getCategory());
            o.put("username", it.getUsername());
            o.put("password", it.getPassword());
            o.put("notes", it.getNotes());
            o.put("totp", it.getTotpSecret());
            o.put("website", it.getWebsite());
            o.put("pinned", it.isPinned());
            o.put("created_at", it.getCreatedAt());
            o.put("updated_at", it.getUpdatedAt());
            arr.put(o);
        }
        root.put("item_count", arr.length());
        root.put("items", arr);
        return root.toString();
    }

    private void showBackupImportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 20, 24, 8);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(isPersian ? "بازیابی از بکاپ" : "Restore from Backup");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        root.addView(tvTitle);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(isPersian
                ? "رمز بکاپی که هنگام ساخت فایل بکاپ انتخاب کرده بودید را وارد کنید."
                : "Enter the backup password you used when the backup file was created.");
        tvDesc.setTextSize(13f);
        tvDesc.setTextColor(Color.parseColor(isDarkMode ? "#A1A1AA" : "#71717A"));
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = 8;
        tvDesc.setLayoutParams(descLp);
        root.addView(tvDesc);

        TextInputLayout tilPass = new TextInputLayout(this);
        tilPass.setHint(isPersian ? "رمز بکاپ" : "Backup Password");
        tilPass.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        TextInputEditText etPass = new TextInputEditText(this);
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tilPass.addView(etPass);
        LinearLayout.LayoutParams tilLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tilLp.topMargin = 16;
        tilPass.setLayoutParams(tilLp);
        root.addView(tilPass);

        MaterialButton btnRestore = new MaterialButton(this);
        btnRestore.setText(isPersian ? "رمزگشایی و ادامه" : "Decrypt & Continue");
        btnRestore.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E4E4E7")));
        btnRestore.setTextColor(Color.parseColor("#09090B"));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = 16;
        btnRestore.setLayoutParams(btnLp);
        root.addView(btnRestore);

        AlertDialog dialog = builder.setView(root).create();
        dialog.show();

        btnRestore.setOnClickListener(v -> {
            String pass = etPass.getText() != null ? etPass.getText().toString() : "";
            if (pass.isEmpty()) {
                Toast.makeText(this, isPersian ? "رمز بکاپ را وارد کنید" : "Enter the backup password",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            btnRestore.setEnabled(false);
            doBackupImport(pass, dialog, btnRestore);
        });
    }

    private void doBackupImport(String backupPassword, AlertDialog dialog, MaterialButton busyButton) {
        final Uri uri = pendingImportUri;
        new Thread(() -> {
            try {
                byte[] fileBytes = readAllBytes(uri);
                String json = BackupManager.decrypt(fileBytes, backupPassword);
                JSONObject root = new JSONObject(json);
                if (!"offlinepw-backup-v1-vault".equals(root.optString("format", ""))) {
                    throw new IllegalArgumentException("bad backup format");
                }
                JSONArray arr = root.getJSONArray("items");
                List<VaultItem> items = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    items.add(new VaultItem(
                            o.optString("id", UUID.randomUUID().toString()),
                            o.optString("title"),
                            o.optString("category", "LOGIN"),
                            o.optString("username"),
                            o.optString("password"),
                            o.optString("notes"),
                            o.optString("totp"),
                            o.optString("website"),
                            o.optBoolean("pinned", false),
                            o.optLong("created_at", 0L),
                            o.optLong("updated_at", 0L)));
                }
                final int count = items.size();
                runOnUiThread(() -> {
                    if (isFinishing() || isChangingConfigurations()) return;
                    dialog.dismiss();
                    busyButton.setEnabled(true);
                    new AlertDialog.Builder(this)
                            .setTitle(isPersian ? "بازیابی بکاپ" : "Restore Backup")
                            .setMessage(isPersian
                                    ? ("بکاپ " + count + " آیتم دارد.\nآیتم‌هایی که شناسه‌ی یکسان دارند بروزرسانی و بقیه به‌عنوان جدید اضافه می‌شوند.\nادامه می‌دهید؟")
                                    : ("The backup contains " + count + " item(s).\nItems with the same ID will be updated, the rest will be added.\nContinue?"))
                            .setPositiveButton(isPersian ? "بازیابی" : "Restore", (d, w) -> writeImportedItems(items))
                            .setNegativeButton(isPersian ? "انصراف" : "Cancel", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isChangingConfigurations()) return;
                    dialog.dismiss();
                    busyButton.setEnabled(true);
                    pendingImportUri = null;
                    Toast.makeText(this, isPersian
                            ? "رمز بکاپ اشتباه است یا فایل یک بکاپ معتبر نیست"
                            : "Wrong backup password or the file is not a valid backup",
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void writeImportedItems(List<VaultItem> items) {
        new Thread(() -> {
            try {
                for (VaultItem it : items) {
                    dbHelper.insertItem(it, cryptoManager);
                }
                final int count = items.size();
                runOnUiThread(() -> {
                    if (isFinishing() || isChangingConfigurations()) return;
                    pendingImportUri = null;
                    loadVaultData();
                    Toast.makeText(this, isPersian
                            ? (count + " آیتم بازیابی شد")
                            : (count + " item(s) restored"),
                            Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        isPersian ? "بازیابی ناموفق بود؛ دوباره تلاش کنید"
                                  : "Restore failed; please try again",
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private byte[] readAllBytes(Uri uri) throws Exception {
        if (uri == null) throw new java.io.IOException("no uri");
        try (InputStream is = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (is == null) throw new java.io.IOException("cannot open file");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        }
    }

    private static boolean containsIgnoreCase(String text, String query) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(query);
    }

    private String generateStrongPassword(int length) {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
    
    
    
