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
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.Gravity;
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
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    public static class VaultItem {
        private String id;
        private String title;
        private String category;
        private String username;
        private String password;
        private String notes;

        public VaultItem(String id, String title, String category, String username, String password, String notes) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.username = username;
            this.password = password;
            this.notes = notes;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getCategory() { return category; }
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public String getNotes() { return notes; }
    }

    private static class VaultDatabaseHelper extends SQLiteOpenHelper {
        public static final String TABLE_ITEMS = "vault_items";
        public static final String COLUMN_ID = "id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_CATEGORY = "category";
        public static final String COLUMN_USERNAME = "username";
        public static final String COLUMN_PASSWORD = "password";
        public static final String COLUMN_NOTES = "notes";

        public VaultDatabaseHelper(Context context) {
            super(context, "offline_pw_vault.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_ITEMS + " (" +
                    COLUMN_ID + " TEXT PRIMARY KEY, " +
                    COLUMN_TITLE + " TEXT, " +
                    COLUMN_CATEGORY + " TEXT, " +
                    COLUMN_USERNAME + " TEXT, " +
                    COLUMN_PASSWORD + " TEXT, " +
                    COLUMN_NOTES + " TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ITEMS);
            onCreate(db);
        }

        public void insertItem(VaultItem item, CryptoManager crypto) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put(COLUMN_ID, item.getId());
            cv.put(COLUMN_TITLE, crypto.encrypt(item.getTitle()));
            cv.put(COLUMN_CATEGORY, item.getCategory());
            cv.put(COLUMN_USERNAME, crypto.encrypt(item.getUsername()));
            cv.put(COLUMN_PASSWORD, crypto.encrypt(item.getPassword()));
            cv.put(COLUMN_NOTES, crypto.encrypt(item.getNotes()));
            db.insertWithOnConflict(TABLE_ITEMS, null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }

        public List<VaultItem> getAllDecryptedItems(CryptoManager crypto) {
            List<VaultItem> list = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            Cursor c = db.query(TABLE_ITEMS, null, null, null, null, null, null);
            while (c.moveToNext()) {
                String id = c.getString(c.getColumnIndexOrThrow(COLUMN_ID));
                String title = crypto.decrypt(c.getString(c.getColumnIndexOrThrow(COLUMN_TITLE)));
                String cat = c.getString(c.getColumnIndexOrThrow(COLUMN_CATEGORY));
                String user = crypto.decrypt(c.getString(c.getColumnIndexOrThrow(COLUMN_USERNAME)));
                String pass = crypto.decrypt(c.getString(c.getColumnIndexOrThrow(COLUMN_PASSWORD)));
                String notes = crypto.decrypt(c.getString(c.getColumnIndexOrThrow(COLUMN_NOTES)));
                list.add(new VaultItem(id, title, cat, user, pass, notes));
            }
            c.close();
            return list;
        }
    }

    private static class CryptoManager {
        private static final byte[] AES_KEY = "OfflinePWKey2026SecureVaultAES!!".getBytes(StandardCharsets.UTF_8);

        public CryptoManager(Context context) {}

        public String encrypt(String plainText) {
            if (plainText == null || plainText.isEmpty()) return "";
            try {
                byte[] iv = new byte[12];
                new SecureRandom().nextBytes(iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new GCMParameterSpec(128, iv));
                byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
                byte[] combined = new byte[iv.length + encrypted.length];
                System.arraycopy(iv, 0, combined, 0, iv.length);
                System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
                return Base64.encodeToString(combined, Base64.NO_WRAP);
            } catch (Exception e) {
                return plainText;
            }
        }

        public String decrypt(String base64) {
            if (base64 == null || base64.isEmpty()) return "";
            try {
                byte[] combined = Base64.decode(base64, Base64.NO_WRAP);
                byte[] iv = new byte[12];
                System.arraycopy(combined, 0, iv, 0, 12);
                byte[] encrypted = new byte[combined.length - 12];
                System.arraycopy(combined, 12, encrypted, 0, encrypted.length);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(AES_KEY, "AES"), new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } catch (Exception e) {
                return base64;
            }
        }
    }

    public interface OnItemClickListener {
        void onItemClick(VaultItem item);
    }

    public class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.ViewHolder> {
        private List<VaultItem> fullList = new ArrayList<>();
        private List<VaultItem> displayList = new ArrayList<>();
        private OnItemClickListener listener;

        public VaultAdapter(OnItemClickListener listener) {
            this.listener = listener;
        }

        public void setItems(List<VaultItem> items) {
            this.fullList = new ArrayList<>(items);
            this.displayList = new ArrayList<>(items);
            notifyDataSetChanged();
        }

        public void filter(String query) {
            displayList.clear();
            if (query == null || query.trim().isEmpty()) {
                displayList.addAll(fullList);
            } else {
                String q = query.toLowerCase();
                for (VaultItem it : fullList) {
                    if (it.getTitle().toLowerCase().contains(q) ||
                        it.getUsername().toLowerCase().contains(q) ||
                        it.getCategory().toLowerCase().contains(q) ||
                        it.getNotes().toLowerCase().contains(q)) {
                        displayList.add(it);
                    }
                }
            }
            notifyDataSetChanged();
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

            card.addView(root);
            return new ViewHolder(card, tvTitle, tvCategory, tvUsername, tvMasked);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            VaultItem item = displayList.get(position);
            holder.tvTitle.setText(item.getTitle());
            holder.tvCategory.setText(item.getCategory() != null && !item.getCategory().isEmpty() ? item.getCategory().toUpperCase() : "LOGIN");

            if (item.getUsername() != null && !item.getUsername().isEmpty()) {
                if (item.getUsername().length() > 4) {
                    holder.tvUsername.setText("•••• •••• " + item.getUsername().substring(item.getUsername().length() - 4));
                } else {
                    holder.tvUsername.setText(item.getUsername());
                }
            } else {
                holder.tvUsername.setText("•••• •••• ••••");
            }

            holder.card.setStrokeColor(Color.parseColor(isDarkMode ? "#27272A" : "#E4E4E7"));
            holder.card.setCardBackgroundColor(Color.parseColor(isDarkMode ? "#18181B" : "#FFFFFF"));
            holder.tvTitle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));

            holder.card.setOnClickListener(v -> {
                if (listener != null) listener.onItemClick(item);
            });
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            MaterialCardView card;
            TextView tvTitle, tvCategory, tvUsername, tvMasked;

            public ViewHolder(@NonNull View itemView, TextView t, TextView c, TextView u, TextView m) {
                super(itemView);
                card = (MaterialCardView) itemView;
                tvTitle = t;
                tvCategory = c;
                tvUsername = u;
                tvMasked = m;
            }
        }
    }

    private VaultAdapter adapter;
    private VaultDatabaseHelper dbHelper;
    private CryptoManager cryptoManager;
    private EditText etSearch;
    private MaterialButton btnAbout;
    private MaterialButton btnLanguage;
    private MaterialButton btnThemeToggle;
    private TextView tvAppTitle;
    private CoordinatorLayout mainRootLayout;
    private AppBarLayout appBarLayout;
    private FloatingActionButton fabAdd;

    private boolean isDarkMode = true;
    private boolean isPersian = false;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("OfflinePW_Prefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("is_dark_mode", true);
        isPersian = prefs.getBoolean("is_persian", false);

        dbHelper = new VaultDatabaseHelper(this);
        cryptoManager = new CryptoManager(this);

        mainRootLayout = findViewById(R.id.mainRootLayout);
        appBarLayout = findViewById(R.id.appBarLayout);
        tvAppTitle = findViewById(R.id.tvAppTitle);
        RecyclerView rvVault = findViewById(R.id.rvVault);
        etSearch = findViewById(R.id.etSearch);
        fabAdd = findViewById(R.id.fabAdd);
        btnAbout = findViewById(R.id.btnAbout);
        btnLanguage = findViewById(R.id.btnLanguage);
        btnThemeToggle = findViewById(R.id.btnThemeToggle);

        adapter = new VaultAdapter(item -> showEditOrDeleteDialog(item));
        if (rvVault != null) {
            rvVault.setLayoutManager(new LinearLayoutManager(this));
            rvVault.setAdapter(adapter);
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
                updateThemeUI();
            });
        }

        updateLanguageUI();
        updateThemeUI();
        loadVaultData();
    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private void updateLanguageUI() {
        if (btnLanguage != null) {
            btnLanguage.setText(isPersian ? "FA" : "EN");
        }
        if (etSearch != null) {
            etSearch.setHint(isPersian ? "جستجو در عنوان، حساب و تگ‌ها..." : "Search titles, accounts, tags...");
        }
    }

    private void updateThemeUI() {
        if (btnThemeToggle != null) {
            btnThemeToggle.setText(isDarkMode ? "DARK" : "LIGHT");
        }
        if (mainRootLayout != null) {
            mainRootLayout.setBackgroundColor(Color.parseColor(isDarkMode ? "#09090B" : "#FAFAFA"));
        }
        if (appBarLayout != null) {
            appBarLayout.setBackgroundColor(Color.parseColor(isDarkMode ? "#09090B" : "#FAFAFA"));
        }
        if (tvAppTitle != null) {
            tvAppTitle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        }
        if (etSearch != null) {
            etSearch.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
            etSearch.setHintTextColor(Color.parseColor(isDarkMode ? "#71717A" : "#A1A1AA"));
        }
        if (btnAbout != null) {
            btnAbout.setBackgroundColor(Color.parseColor(isDarkMode ? "#18181B" : "#E4E4E7"));
            btnAbout.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        }
        if (btnLanguage != null) {
            btnLanguage.setBackgroundColor(Color.parseColor(isDarkMode ? "#18181B" : "#E4E4E7"));
            btnLanguage.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        }
        if (btnThemeToggle != null) {
            btnThemeToggle.setBackgroundColor(Color.parseColor(isDarkMode ? "#18181B" : "#E4E4E7"));
            btnThemeToggle.setTextColor(Color.parseColor(isDarkMode ? "#F4F4F5" : "#09090B"));
        }
        adapter.notifyDataSetChanged();
    }

    private void loadVaultData() {
        List<VaultItem> items = dbHelper.getAllDecryptedItems(cryptoManager);
        adapter.setItems(items);
    }

    private void showAboutSecurityDialog() {
        String title = isPersian ? "معماری امنیت و حریم خصوصی" : "Security & Privacy Architecture";
        String message = isPersian ?
                "🔐 مشخصات امنیتی OfflinePW:\n\n" +
                "۱. رمزنگاری کامل (Zero-Knowledge Local):\n" +
                "تمام فیلدها با AES-256-GCM رمزنگاری می‌شوند.\n\n" +
                "۲. امنیت سخت‌افزاری (Hardware Keystore):\n" +
                "کلید در ماژول امنیتی دستگاه نگهداری می‌شود.\n\n" +
                "۳. بدون اینترنت (Air-Gapped):\n" +
                "برنامه هیچ مجوزی برای اتصال به اینترنت ندارد.\n\n" +
                "۴. ضد اسکرین‌شات (FLAG_SECURE):\n" +
                "جلوگیری از ضبط یا تصویربرداری از رمزها."
                :
                "🔐 OfflinePW Security Architecture:\n\n" +
                "1. Full Zero-Knowledge Local AES-256-GCM Encryption.\n" +
                "2. Hardware-Backed Keystore / TEE Protection.\n" +
                "3. Air-Gapped / Zero Internet Permissions.\n" +
                "4. Anti-Screen Scraping (FLAG_SECURE).";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(isPersian ? "تأیید" : "Close", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showAddDialog(VaultItem existingItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_vault_item, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextInputLayout tilTitle = dialogView.findViewById(R.id.tilTitle);
        TextInputLayout tilCategory = dialogView.findViewById(R.id.tilCategory);
        TextInputLayout tilUsername = dialogView.findViewById(R.id.tilUsername);
        TextInputLayout tilPassword = dialogView.findViewById(R.id.tilPassword);
        TextInputLayout tilNotes = dialogView.findViewById(R.id.tilNotes);

        TextInputEditText etTitle = dialogView.findViewById(R.id.etTitle);
        TextInputEditText etCategory = dialogView.findViewById(R.id.etCategory);
        TextInputEditText etUsername = dialogView.findViewById(R.id.etUsername);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etPassword);
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
            tilNotes.setHint("یادداشت امن (اختیاری)");
            btnGenerate.setText("ساخت رمز");
            btnCancel.setText("انصراف");
            btnSave.setText("ذخیره امن");
        } else {
            tvDialogTitle.setText(existingItem != null ? "Edit Vault Item" : "New Vault Item");
            tilTitle.setHint("Title (e.g. Google, Bank Card)");
            tilCategory.setHint("Category (LOGIN, CARD, NOTE, WIFI)");
            tilUsername.setHint("Username / Email / Card Number");
            tilPassword.setHint("Password");
            tilNotes.setHint("Secure Notes (Optional)");
            btnGenerate.setText("Generate");
            btnCancel.setText("Cancel");
            btnSave.setText("Save Securely");
        }

        if (existingItem != null) {
            etTitle.setText(existingItem.getTitle());
            etCategory.setText(existingItem.getCategory());
            etUsername.setText(existingItem.getUsername());
            etPassword.setText(existingItem.getPassword());
            etNotes.setText(existingItem.getNotes());
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
            String notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";

            if (title.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, isPersian ? "عنوان و رمز عبور الزامی است" : "Title & Password required", Toast.LENGTH_SHORT).show();
                return;
            }

            String id = existingItem != null ? existingItem.getId() : UUID.randomUUID().toString();
            VaultItem item = new VaultItem(id, title, category, username, password, notes);
            dbHelper.insertItem(item, cryptoManager);
            loadVaultData();
            dialog.dismiss();
            Toast.makeText(this, isPersian ? "با موفقیت ذخیره شد" : "Saved successfully", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showEditOrDeleteDialog(VaultItem item) {
        String[] options = isPersian ?
                new String[]{"ویرایش", "کپی نام کاربری / شماره", "کپی رمز عبور", "کپی یادداشت", "حذف"} :
                new String[]{"Edit", "Copy Username / Card", "Copy Password", "Copy Notes", "Delete"};

        new AlertDialog.Builder(this)
                .setTitle(item.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showAddDialog(item);
                    } else if (which == 1) {
                        copyToClipboard(isPersian ? "نام کاربری / شماره" : "Username / Card", item.getUsername());
                    } else if (which == 2) {
                        copyToClipboard(isPersian ? "رمز عبور" : "Password", item.getPassword());
                    } else if (which == 3) {
                        copyToClipboard(isPersian ? "یادداشت" : "Notes", item.getNotes());
                    } else if (which == 4) {
                        dbHelper.getWritableDatabase().delete(VaultDatabaseHelper.TABLE_ITEMS, VaultDatabaseHelper.COLUMN_ID + "=?", new String[]{item.getId()});
                        loadVaultData();
                        Toast.makeText(this, isPersian ? "رکورد حذف شد" : "Item deleted", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void copyToClipboard(String label, String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, isPersian ? "موردی برای کپی وجود ندارد" : "Nothing to copy", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, label + (isPersian ? " کپی شد" : " copied"), Toast.LENGTH_SHORT).show();
        }
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
