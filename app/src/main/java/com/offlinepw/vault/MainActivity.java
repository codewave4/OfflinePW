package com.offlinepw.vault;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.offlinepw.vault.adapter.VaultAdapter;
import com.offlinepw.vault.crypto.CryptoManager;
import com.offlinepw.vault.db.VaultDatabaseHelper;
import com.offlinepw.vault.model.VaultItem;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
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

        // جلوگیری از اسکرین‌شات و ضبط تصویر برای حفظ امنیت رمزها
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
        rvVault.setLayoutManager(new LinearLayoutManager(this));
        rvVault.setAdapter(adapter);

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

        fabAdd.setOnClickListener(v -> showAddDialog(null));

        btnAbout.setOnClickListener(v -> showAboutSecurityDialog());

        btnLanguage.setOnClickListener(v -> {
            isPersian = !isPersian;
            prefs.edit().putBoolean("is_persian", isPersian).apply();
            updateLanguageUI();
        });

        btnThemeToggle.setOnClickListener(v -> {
            isDarkMode = !isDarkMode;
            prefs.edit().putBoolean("is_dark_mode", isDarkMode).apply();
            updateThemeUI();
        });

        updateLanguageUI();
        updateThemeUI();
        loadVaultData();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // قفل خودکار: به محض خروج یا مینیمایز کردن برنامه، صفحه اصلی بسته می‌شود
        // تا در ورود مجدد، حتماً صفحه پین ۶ رقمی باز شود
        finish();
    }

    private void updateLanguageUI() {
        if (isPersian) {
            btnLanguage.setText("FA");
            etSearch.setHint("جستجو در عنوان، حساب و تگ‌ها...");
        } else {
            btnLanguage.setText("EN");
            etSearch.setHint("Search titles, accounts, tags...");
        }
    }

    private void updateThemeUI() {
        if (isDarkMode) {
            btnThemeToggle.setText("DARK");
            mainRootLayout.setBackgroundColor(Color.parseColor("#09090B"));
            appBarLayout.setBackgroundColor(Color.parseColor("#09090B"));
            tvAppTitle.setTextColor(Color.parseColor("#F4F4F5"));
            etSearch.setTextColor(Color.parseColor("#F4F4F5"));
            etSearch.setHintTextColor(Color.parseColor("#71717A"));
            
            btnAbout.setBackgroundColor(Color.parseColor("#18181B"));
            btnAbout.setTextColor(Color.parseColor("#F4F4F5"));
            btnLanguage.setBackgroundColor(Color.parseColor("#18181B"));
            btnLanguage.setTextColor(Color.parseColor("#F4F4F5"));
            btnThemeToggle.setBackgroundColor(Color.parseColor("#18181B"));
            btnThemeToggle.setTextColor(Color.parseColor("#F4F4F5"));
        } else {
            btnThemeToggle.setText("LIGHT");
            mainRootLayout.setBackgroundColor(Color.parseColor("#FAFAFA"));
            appBarLayout.setBackgroundColor(Color.parseColor("#FAFAFA"));
            tvAppTitle.setTextColor(Color.parseColor("#09090B"));
            etSearch.setTextColor(Color.parseColor("#09090B"));
            etSearch.setHintTextColor(Color.parseColor("#A1A1AA"));
            
            btnAbout.setBackgroundColor(Color.parseColor("#E4E4E7"));
            btnAbout.setTextColor(Color.parseColor("#09090B"));
            btnLanguage.setBackgroundColor(Color.parseColor("#E4E4E7"));
            btnLanguage.setTextColor(Color.parseColor("#09090B"));
            btnThemeToggle.setBackgroundColor(Color.parseColor("#E4E4E7"));
            btnThemeToggle.setTextColor(Color.parseColor("#09090B"));
        }
    }

    private void loadVaultData() {
        List<VaultItem> items = dbHelper.getAllDecryptedItems(cryptoManager);
        adapter.setItems(items);
    }

    private void showAboutSecurityDialog() {
        String title = isPersian ? "معماری امنیت و حریم خصوصی" : "Security & Privacy Architecture";
        String message = isPersian ?
                "🔐 مشخصات امنیتی OfflinePW (Open-Source):\n\n" +
                "۱. رمزنگاری کامل (Zero-Knowledge Local):\n" +
                "تمام فیلدها شامل عنوان، نام کاربری، شماره کارت، رمز عبور و یادداشت‌ها با الگوریتم قدرتمند AES-256-GCM به صورت رمزنگاری‌شده در دیتابیس ذخیره می‌شوند.\n\n" +
                "۲. امنیت سخت‌افزاری (Hardware Keystore / StrongBox):\n" +
                "کلید رمزنگاری مستقیماً در سخت‌افزار امن دستگاه (TEE/StrongBox) نگهداری می‌شود و قابل استخراج نیست.\n\n" +
                "۳. عدم دسترسی به اینترنت (Air-Gapped):\n" +
                "برنامه فاقد هرگونه مجوز دسترسی به اینترنت (بدون INTERNET permission) است؛ در نتیجه نشت داده‌ها به سرور غیرممکن است.\n\n" +
                "۴. حفاظت از صفحه (Anti-Screen Capture):\n" +
                "قابلیت FLAG_SECURE مانع از عکس‌برداری یا ضبط صفحه توسط بدافزارها می‌شود."
                :
                "🔐 OfflinePW Security Architecture (Open-Source):\n\n" +
                "1. Full Zero-Knowledge Local Encryption:\n" +
                "All fields (Title, Card Number/Username, Password, Notes) are encrypted using AES-256-GCM before writing to the database.\n\n" +
                "2. Hardware-Backed Keys (Keystore & StrongBox):\n" +
                "Master keys are generated inside the device's hardware security module (TEE / StrongBox HSM) and never exposed.\n\n" +
                "3. Air-Gapped / Zero Internet:\n" +
                "Zero network permissions (No android.permission.INTERNET). Zero tracking, zero telemetry.\n\n" +
                "4. Anti-Screen Capture:\n" +
                "Enforced FLAG_SECURE prevents malware screen scraping and screen recording.";

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
            Toast.makeText(this, isPersian ? "با موفقیت و رمزنگاری سخت‌افزاری ذخیره شد" : "Saved with hardware encryption", Toast.LENGTH_SHORT).show();
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
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
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
