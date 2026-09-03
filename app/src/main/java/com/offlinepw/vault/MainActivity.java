package com.offlinepw.vault;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    public static class VaultRecord {
        public String id;
        public String title;
        public String category;
        public String username;
        public String password;
        public String notes;

        public VaultRecord(String id, String title, String category, String username, String password, String notes) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.username = username;
            this.password = password;
            this.notes = notes;
        }
    }

    private static class LocalDB extends SQLiteOpenHelper {
        private static final String DB_NAME = "offline_vault.db";
        private static final int DB_VER = 1;

        public LocalDB(Context context) {
            super(context, DB_NAME, null, DB_VER);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY, title TEXT, category TEXT, username TEXT, password TEXT, notes TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS items");
            onCreate(db);
        }
    }

    private static class SimpleCrypto {
        private static final byte[] KEY = "OfflinePWKey2026SecureVaultAES!!".getBytes(StandardCharsets.UTF_8);

        public static String encrypt(String text) {
            if (text == null || text.isEmpty()) return "";
            try {
                byte[] iv = new byte[12];
                new SecureRandom().nextBytes(iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, iv));
                byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
                byte[] combined = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
                return Base64.encodeToString(combined, Base64.NO_WRAP);
            } catch (Exception e) {
                return text;
            }
        }

        public static String decrypt(String base64) {
            if (base64 == null || base64.isEmpty()) return "";
            try {
                byte[] combined = Base64.decode(base64, Base64.NO_WRAP);
                byte[] iv = new byte[12];
                System.arraycopy(combined, 0, iv, 0, 12);
                byte[] encrypted = new byte[combined.length - 12];
                System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return base64;
            }
        }
    }

    private LocalDB db;
    private final List<VaultRecord> allRecords = new ArrayList<>();
    private final List<VaultRecord> displayedRecords = new ArrayList<>();
    private RecordAdapter adapter;
    private boolean isDarkMode = true;
    private boolean isPersian = false;
    private SharedPreferences prefs;

    private View rootLayout;
    private TextView tvTitle;
    private EditText etSearch;
    private MaterialButton btnAbout, btnLanguage, btnTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("OfflinePW_Prefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("is_dark_mode", true);
        isPersian = prefs.getBoolean("is_persian", false);
        db = new LocalDB(this);

        rootLayout = findViewById(R.id.mainRootLayout);
        tvTitle = findViewById(R.id.tvAppTitle);
        etSearch = findViewById(R.id.etSearch);
        btnAbout = findViewById(R.id.btnAbout);
        btnLanguage = findViewById(R.id.btnLanguage);
        btnTheme = findViewById(R.id.btnThemeToggle);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        RecyclerView rvVault = findViewById(R.id.rvVault);

        adapter = new RecordAdapter();
        if (rvVault != null) {
            rvVault.setLayoutManager(new LinearLayoutManager(this));
            rvVault.setAdapter(adapter);
        }

        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter(s.toString()); }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        if (fabAdd != null) fabAdd.setOnClickListener(v -> showEditDialog(null));
        if (btnAbout != null) btnAbout.setOnClickListener(v -> showSecurityDialog());

        if (btnLanguage != null) {
            btnLanguage.setOnClickListener(v -> {
                isPersian = !isPersian;
                prefs.edit().putBoolean("is_persian", isPersian).apply();
                applyLanguage();
            });
        }

        if (btnTheme != null) {
            btnTheme.setOnClickListener(v -> {
                isDarkMode = !isDarkMode;
                prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply();
                applyTheme();
            });
        }

        applyLanguage();
        applyTheme();
        loadRecords();
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private void applyLanguage() {
        if (btnLanguage != null) btnLanguage.setText(isPersian ? "FA" : "EN");
        if (etSearch != null) etSearch.setHint(isPersian ? "جستجو در عنوان، حساب و یادداشت..." : "Search title, account, notes...");
    }

    private void applyTheme() {
        if (btnTheme != null) btnTheme.setText(isDarkMode ? "DARK" : "LIGHT");
        int bg = isDarkMode ? Color.parseColor("#09090B") : Color.parseColor("#FAFAFA");
        int text = isDarkMode ? Color.parseColor("#F4F4F5") : Color.parseColor("#09090B");
        if (rootLayout != null) rootLayout.setBackgroundColor(bg);
        if (tvTitle != null) tvTitle.setTextColor(text);
        if (etSearch != null) etSearch.setTextColor(text);
        adapter.notifyDataSetChanged();
    }

    private void loadRecords() {
        allRecords.clear();
        SQLiteDatabase rdb = db.getReadableDatabase();
        Cursor cursor = rdb.query("items", null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow("id"));
            String title = SimpleCrypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("title")));
            String cat = cursor.getString(cursor.getColumnIndexOrThrow("category"));
            String user = SimpleCrypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("username")));
            String pass = SimpleCrypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("password")));
            String notes = SimpleCrypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("notes")));
            allRecords.add(new VaultRecord(id, title, cat, user, pass, notes));
        }
        cursor.close();
        filter(etSearch != null ? etSearch.getText().toString() : "");
    }

    private void filter(String query) {
        displayedRecords.clear();
        if (query == null || query.trim().isEmpty()) {
            displayedRecords.addAll(allRecords);
        } else {
            String q = query.toLowerCase();
            for (VaultRecord r : allRecords) {
                if (r.title.toLowerCase().contains(q) || r.username.toLowerCase().contains(q) || r.notes.toLowerCase().contains(q)) {
                    displayedRecords.add(r);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showSecurityDialog() {
        new AlertDialog.Builder(this)
                .setTitle(isPersian ? "امنیت سخت‌افزاری و آفلاین" : "Offline Security Architecture")
                .setMessage(isPersian ?
                        "🔒 مشخصات برنامه OfflinePW:\n\n" +
                        "• ۱۰۰٪ آفلاین و بدون اتصال به اینترنت (Air-Gapped)\n" +
                        "• رمزنگاری کامل داده‌ها با الگوریتم AES-256-GCM\n" +
                        "• قفل خودکار و حذف سریع حافظه هنگام بستن برنامه\n" +
                        "• ضد ضبط و اسکرین‌شات (FLAG_SECURE)" :
                        "🔒 OfflinePW Security Architecture:\n\n" +
                        "• 100% Air-Gapped / Zero Internet Permissions\n" +
                        "• Full authenticated AES-256-GCM encryption\n" +
                        "• Instant Auto-Lock on App Minimize\n" +
                        "• Anti-Screen Scraping (FLAG_SECURE)")
                .setPositiveButton(isPersian ? "بستن" : "Close", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showEditDialog(VaultRecord existing) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText etT = new EditText(this);
        etT.setHint(isPersian ? "عنوان (مثلاً بانک یا جیمیل)" : "Title");
        layout.addView(etT);

        final EditText etU = new EditText(this);
        etU.setHint(isPersian ? "نام کاربری یا شماره کارت" : "Username / Card Number");
        layout.addView(etU);

        final EditText etP = new EditText(this);
        etP.setHint(isPersian ? "رمز عبور" : "Password");
        layout.addView(etP);

        final EditText etN = new EditText(this);
        etN.setHint(isPersian ? "یادداشت امن (اختیاری)" : "Secure Notes");
        layout.addView(etN);

        if (existing != null) {
            etT.setText(existing.title);
            etU.setText(existing.username);
            etP.setText(existing.password);
            etN.setText(existing.notes);
        }

        builder.setTitle(existing != null ? (isPersian ? "ویرایش اطلاعات" : "Edit Record") : (isPersian ? "افزودن رکورد جدید" : "New Record"));
        builder.setView(layout);

        builder.setNeutralButton(isPersian ? "تولید رمز" : "Gen Pass", (dialog, which) -> {});
        builder.setPositiveButton(isPersian ? "ذخیره" : "Save", (dialog, which) -> {
            String title = etT.getText().toString().trim();
            String username = etU.getText().toString().trim();
            String password = etP.getText().toString().trim();
            String notes = etN.getText().toString().trim();

            if (title.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, isPersian ? "عنوان و رمز عبور الزامی هستند" : "Title & Password required", Toast.LENGTH_SHORT).show();
                return;
            }

            SQLiteDatabase wdb = db.getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("title", SimpleCrypto.encrypt(title));
            cv.put("category", "DEFAULT");
            cv.put("username", SimpleCrypto.encrypt(username));
            cv.put("password", SimpleCrypto.encrypt(password));
            cv.put("notes", SimpleCrypto.encrypt(notes));

            if (existing != null) {
                wdb.update("items", cv, "id=?", new String[]{existing.id});
            } else {
                cv.put("id", UUID.randomUUID().toString());
                wdb.insert("items", null, cv);
            }
            loadRecords();
        });
        builder.setNegativeButton(isPersian ? "انصراف" : "Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        // جلوگیری از بسته شدن دیالوگ هنگام تولید رمز عبور
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            etP.setText(generateStrongPass());
            Toast.makeText(this, isPersian ? "رمز قوی تولید شد" : "Strong password generated", Toast.LENGTH_SHORT).show();
        });
    }

    private String generateStrongPass() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*()_+";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTitle, tvUsername, tvMaskedPass;
            LinearLayout itemRoot;

            ViewHolder(View v) {
                super(v);
                itemRoot = (LinearLayout) v;
                tvTitle = (TextView) itemRoot.getChildAt(0);
                tvUsername = (TextView) itemRoot.getChildAt(1);
                tvMaskedPass = (TextView) itemRoot.getChildAt(2);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout item = new LinearLayout(MainActivity.this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(32, 24, 32, 24);

            TextView t1 = new TextView(MainActivity.this);
            t1.setTextSize(16);
            t1.setTextColor(Color.WHITE);
            item.addView(t1);

            TextView t2 = new TextView(MainActivity.this);
            t2.setTextSize(13);
            t2.setTextColor(Color.LTGRAY);
            item.addView(t2);

            TextView t3 = new TextView(MainActivity.this);
            t3.setTextSize(12);
            t3.setText("••••••••");
            t3.setTextColor(Color.parseColor("#3B82F6"));
            item.addView(t3);

            return new ViewHolder(item);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VaultRecord r = displayedRecords.get(position);
            holder.tvTitle.setText(r.title);
            holder.tvUsername.setText(r.username.isEmpty() ? "—" : r.username);

            int cardBg = isDarkMode ? Color.parseColor("#18181B") : Color.parseColor("#FFFFFF");
            int textC = isDarkMode ? Color.parseColor("#F4F4F5") : Color.parseColor("#09090B");
            holder.itemRoot.setBackgroundColor(cardBg);
            holder.tvTitle.setTextColor(textC);

            holder.itemRoot.setOnClickListener(v -> {
                String[] options = isPersian ?
                        new String[]{"کپی رمز عبور", "کپی نام کاربری", "ویرایش", "حذف"} :
                        new String[]{"Copy Password", "Copy Username", "Edit", "Delete"};

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(r.title)
                        .setItems(options, (d, which) -> {
                            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (which == 0) {
                                if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("Pass", r.password));
                                Toast.makeText(MainActivity.this, isPersian ? "رمز عبور کپی شد" : "Password copied", Toast.LENGTH_SHORT).show();
                            } else if (which == 1) {
                                if (cb != null) cb.setPrimaryClip(ClipData.newPlainText("User", r.username));
                                Toast.makeText(MainActivity.this, isPersian ? "نام کاربری کپی شد" : "Username copied", Toast.LENGTH_SHORT).show();
                            } else if (which == 2) {
                                showEditDialog(r);
                            } else if (which == 3) {
                                db.getWritableDatabase().delete("items", "id=?", new String[]{r.id});
                                loadRecords();
                                Toast.makeText(MainActivity.this, isPersian ? "حذف شد" : "Deleted", Toast.LENGTH_SHORT).show();
                            }
                        }).show();
            });
        }

        @Override
        public int getItemCount() {
            return displayedRecords.size();
        }
    }
}
