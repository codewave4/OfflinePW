package com.offlinepw.vault;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.offlinepw.vault.crypto.VaultSession;
import java.io.File;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class AuthActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD_LENGTH = 10;

    // سیاست امنیتی جدید: ۳ بار رمز اشتباه = پاک شدن کامل و غیرقابل بازگشت همهی دادهها.
    // شمارنده تا زمانی که رمز درست وارد نشود (یا wipe رخ ندهد) در SharedPreferences
    // باقی میماند و با ریاستارت برنامه یا چرخش صفحه ریست نمیشود.
    private static final int MAX_FAILED_ATTEMPTS = 3;

    private static final String PREF_AUTH = "OfflinePW_Auth";
    private static final String PREF_SETTINGS = "OfflinePW_Prefs";
    private static final String DB_NAME = "offline_pw_vault.db";
    private static final String KEY_IS_SETUP = "pin_is_setup";
    private static final String KEY_KEK_SALT = "kek_salt";
    private static final String KEY_WRAPPED_DEK = "wrapped_dek";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";

    private static final int PBKDF2_ITERATIONS = 600000;
    private static final int KEK_LENGTH_BITS = 256;
    private static final int DEK_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private SharedPreferences authPrefs;
    private SharedPreferences settingsPrefs;
    private TextView tvAuthPrompt;
    private TextView tvAuthWarning;
    private TextInputLayout tilMasterPassword;
    private TextInputEditText etMasterPassword;
    private MaterialButton btnUnlock;
    private MaterialButton btnAuthLang;
    private boolean isSettingUpPin = false;
    private String tempPasswordToConfirm = null;
    private boolean isPersian = false;

    // جلوگیری از اجرای همزمان چند عملیات unlock
    private boolean authBusy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_auth);

        authPrefs = getSharedPreferences(PREF_AUTH, MODE_PRIVATE);
        settingsPrefs = getSharedPreferences(PREF_SETTINGS, MODE_PRIVATE);
        isPersian = settingsPrefs.getBoolean("is_persian", false);

        tvAuthPrompt = findViewById(R.id.tvAuthPrompt);
        tvAuthWarning = findViewById(R.id.tvAuthWarning);
        tilMasterPassword = findViewById(R.id.tilMasterPassword);
        etMasterPassword = findViewById(R.id.etMasterPassword);
        btnUnlock = findViewById(R.id.btnUnlock);
        btnAuthLang = findViewById(R.id.btnAuthLang);

        MaterialButton btnAuthHelp = findViewById(R.id.btnAuthHelp);
        if (btnAuthHelp != null) {
            btnAuthHelp.setOnClickListener(v -> {
                Intent intent = new Intent(this, WelcomeActivity.class);
                intent.putExtra("force_show", true);
                startActivity(intent);
            });
        }

        boolean isSetup = authPrefs.getBoolean(KEY_IS_SETUP, false);
        isSettingUpPin = !isSetup;

        if (btnAuthLang != null) {
            btnAuthLang.setOnClickListener(v -> {
                if (authBusy) return;
                isPersian = !isPersian;
                settingsPrefs.edit().putBoolean("is_persian", isPersian).apply();
                updateTexts();
            });
        }

        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> attemptSubmit());
        }

        if (etMasterPassword != null) {
            etMasterPassword.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    attemptSubmit();
                    return true;
                }
                return false;
            });
        }

        updateTexts();
    }

    private void updateTexts() {
        if (btnAuthLang != null) {
            btnAuthLang.setText(isPersian ? "FA" : "EN");
        }

        if (tvAuthPrompt != null) {
            if (isSettingUpPin) {
                if (tempPasswordToConfirm == null) {
                    tvAuthPrompt.setText(isPersian ? ("یک رمز عبور مستر تعیین کنید (حداقل " + MIN_PASSWORD_LENGTH + " کاراکتر)") : ("Create a Master Password (min " + MIN_PASSWORD_LENGTH + " characters)"));
                } else {
                    tvAuthPrompt.setText(isPersian ? "تکرار رمز عبور برای تأیید:" : "Confirm your Master Password:");
                }
            } else {
                tvAuthPrompt.setText(isPersian ? "رمز عبور مستر را وارد کنید" : "Enter your Master Password");
            }
        }

        if (tilMasterPassword != null) {
            tilMasterPassword.setHint(isPersian ? "رمز عبور مستر" : "Master Password");
        }
        setUnlockButtonBusy(false);

        updateWarningText();
    }

    /**
     * هشدار بالای صفحه: اگر تلاش ناموفقی ثبت شده باشد، باقیماندهی تلاشها تا wipe
     * نمایش داده میشود؛ وگرنه پیام پیشفرض (غیرقابل بازیابی بودن رمز + سیاست پاکسازی).
     */
    private void updateWarningText() {
        if (tvAuthWarning == null) return;
        int failed = getFailedAttempts();

        if (failed >= MAX_FAILED_ATTEMPTS) {
            tvAuthWarning.setVisibility(View.GONE);
            return;
        }

        tvAuthWarning.setVisibility(View.VISIBLE);
        if (failed > 0) {
            int remaining = MAX_FAILED_ATTEMPTS - failed;
            tvAuthWarning.setText(isPersian
                    ? ("هشدار امنیتی: تنها " + remaining + " تلاش دیگر باقی مانده است.\nپس از آن، تمام رمزها و دادههای برنامه برای همیشه پاک میشوند!")
                    : ("SECURITY WARNING: only " + remaining + " more attempt" + (remaining == 1 ? "" : "s") + " left.\nAfter that, ALL passwords and data in this app will be permanently erased!"));
        } else {
            tvAuthWarning.setText(isPersian
                    ? "رمز عبور شما در برنامه ذخیره نخواهد شد؛ بنابراین اگر آن را فراموش کنید، بازیابی آن غیرممکن است.\nتوجه: ۳ بار ورود رمز اشتباه = پاک شدن کامل همهی دادهها."
                    : "Your master password is never stored; therefore, if forgotten, recovery is mathematically impossible.\nNote: 3 wrong attempts = ALL data will be permanently erased.");
        }
    }

    private int getFailedAttempts() {
        return authPrefs.getInt(KEY_FAILED_ATTEMPTS, 0);
    }

    private void attemptSubmit() {
        if (authBusy || etMasterPassword == null) return;
        String entered = etMasterPassword.getText() != null ? etMasterPassword.getText().toString() : "";

        if (isSettingUpPin) {
            if (entered.length() < MIN_PASSWORD_LENGTH) {
                Toast.makeText(this, isPersian ?
                        ("رمز عبور باید حداقل " + MIN_PASSWORD_LENGTH + " کاراکتر باشد") :
                        ("Password must be at least " + MIN_PASSWORD_LENGTH + " characters"), Toast.LENGTH_SHORT).show();
                return;
            }
            if (tempPasswordToConfirm == null) {
                tempPasswordToConfirm = entered;
                if (etMasterPassword != null) etMasterPassword.setText("");
                updateTexts();
            } else {
                if (tempPasswordToConfirm.equals(entered)) {
                    createNewVaultKey(entered);
                } else {
                    Toast.makeText(this, isPersian ? "رمزها مطابقت ندارند، دوباره امتحان کنید" : "Passwords do not match, try again", Toast.LENGTH_SHORT).show();
                    tempPasswordToConfirm = null;
                    if (etMasterPassword != null) etMasterPassword.setText("");
                    updateTexts();
                }
            }
        } else {
            attemptUnlockAsync(entered);
        }
    }

    /**
     * هنگام مشغول بودن عملیات سنگین (PBKDF2 روی ترد پسزمینه)، دکمه غیرفعال
     * میشود و متن آن حالت «در حال بررسی» میگیرد تا کاربر بداند اپ در حال کار است
     * و همزمان صفحه هم فریز نشود.
     */
    private void setUnlockButtonBusy(boolean busy) {
        authBusy = busy;
        if (btnUnlock == null) return;
        btnUnlock.setEnabled(!busy);
        btnUnlock.setText(busy
                ? (isPersian ? "در حال بررسی..." : "Checking...")
                : (isPersian ? "باز کردن" : "Unlock"));
    }

    private void createNewVaultKey(String password) {
        setUnlockButtonBusy(true);
        new Thread(() -> {
            try {
                byte[] salt = generateRandomBytes(SALT_LENGTH_BYTES);
                SecretKey kek = deriveKek(password, salt);

                KeyGenerator dekGen = KeyGenerator.getInstance("AES");
                dekGen.init(DEK_LENGTH_BITS, new SecureRandom());
                final SecretKey dek = dekGen.generateKey();

                final String wrappedDek = wrapDek(dek, kek);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    authPrefs.edit()
                            .putString(KEY_KEK_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                            .putString(KEY_WRAPPED_DEK, wrappedDek)
                            .putBoolean(KEY_IS_SETUP, true)
                            .putInt(KEY_FAILED_ATTEMPTS, 0)
                            .apply();

                    VaultSession.setDek(dek);
                    setUnlockButtonBusy(false);
                    Toast.makeText(this, isPersian ? "رمز مستر با موفقیت ثبت شد" : "Master Password set successfully", Toast.LENGTH_SHORT).show();
                    proceedToMain();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setUnlockButtonBusy(false);
                    Toast.makeText(this, isPersian ? "خطا در ساخت کلید امن" : "Error creating secure key", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /**
     * باز کردن قفل: PBKDF2 (۶۰۰k دور) و بازکردن DEK روی ترد پسزمینه انجام میشود
     * تا رابط کاربری فریز نشود و اپ بلافاصله پس از آمادهشدن کلید باز شود.
     */
    private void attemptUnlockAsync(String password) {
        setUnlockButtonBusy(true);
        new Thread(() -> {
            try {
                String saltB64 = authPrefs.getString(KEY_KEK_SALT, "");
                String wrappedDek = authPrefs.getString(KEY_WRAPPED_DEK, "");
                if (saltB64.isEmpty() || wrappedDek.isEmpty()) {
                    throw new IllegalStateException("No vault keys found");
                }
                byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
                SecretKey kek = deriveKek(password, salt);
                final SecretKey dek = unwrapDek(wrappedDek, kek);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    VaultSession.setDek(dek);
                    // تنها جایی که شمارندهی تلاشها ریست میشود: ورود موفق رمز درست.
                    authPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply();
                    setUnlockButtonBusy(false);
                    proceedToMain();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setUnlockButtonBusy(false);
                    if (etMasterPassword != null) etMasterPassword.setText("");
                    registerFailedAttempt();
                });
            }
        }).start();
    }

    /**
     * ثبت یک تلاش ناموفق.
     * تلاشهای ۱ و ۲: فقط اخطار با نمایش تعداد باقیمانده.
     * تلاش سوم: پاک شدن کامل و غیرقابل بازگشت تمام دادهها و بازگشت به حالت ساخت رمز جدید.
     */
    private void registerFailedAttempt() {
        int attempts = getFailedAttempts() + 1;
        authPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, attempts).apply();

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            wipeVaultAndShowMessage();
        } else {
            int remaining = MAX_FAILED_ATTEMPTS - attempts;
            if (etMasterPassword != null) etMasterPassword.setText("");
            Toast.makeText(this, isPersian
                    ? ("رمز اشتباه است! " + remaining + " تلاش دیگر باقی مانده — پس از آن همهی دادهها برای همیشه پاک میشود.")
                    : ("Incorrect password! " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " left — after that ALL data will be permanently erased."),
                    Toast.LENGTH_LONG).show();
            updateWarningText();
        }
    }

    /**
     * پاک کردن کامل: دیتابیس ولت، همهی SharedPreferences (رمز/کلیدها/تنظیمات/نشان welcome)
     * و کلید نشست. بعد از آن برنامه مثل نصب تازه است و کاربر باید رمز مستر جدید بسازد.
     */
    private void wipeVaultAndShowMessage() {
        // زبان کاربر را قبل از پاکشدن تنظیمات نگه میداریم تا پیام پایانی درست نمایش داده شود.
        final boolean langPersian = isPersian;

        // ۱) حذف فایل دیتابیس (+ فایلهای جانبی -wal / -shm / -journal)
        try {
            deleteDatabase(DB_NAME);
        } catch (Exception ignored) {
        }
        // ۲) حذف هر فایل باقیمانده در پوشهی databases (کامل و بیدریغ)
        try {
            File dbDir = new File(getApplicationInfo().dataDir, "databases");
            if (dbDir.isDirectory()) {
                File[] files = dbDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f != null) f.delete();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        // ۳) پاک کردن همهی SharedPreferences (کلیدها، نشان راهاندازی، تنظیمات زبان/تم)
        authPrefs.edit().clear().commit();
        settingsPrefs.edit().clear().commit();
        // ۴) پاک کردن کلید نشست از حافظه
        VaultSession.clear();

        // ۵) برگشت به حالت «ساخت رمز مستر جدید»
        isPersian = langPersian;
        isSettingUpPin = true;
        tempPasswordToConfirm = null;
        if (etMasterPassword != null) etMasterPassword.setText("");
        updateTexts();

        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        sheet.setCancelable(false);
        sheet.setCanceledOnTouchOutside(false);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(48, 40, 48, 48);
        root.setBackgroundColor(android.graphics.Color.parseColor("#18181B"));

        android.widget.TextView tvTitle = new android.widget.TextView(this);
        tvTitle.setText(langPersian ? "تمام دادهها پاک شد" : "All Data Wiped");
        tvTitle.setTextSize(19f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(android.graphics.Color.parseColor("#EF4444"));
        root.addView(tvTitle);

        android.view.View divider = new android.view.View(this);
        android.widget.LinearLayout.LayoutParams dividerLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 2);
        dividerLp.topMargin = 16;
        dividerLp.bottomMargin = 16;
        divider.setLayoutParams(dividerLp);
        divider.setBackgroundColor(android.graphics.Color.parseColor("#27272A"));
        root.addView(divider);

        android.widget.TextView tvMessage = new android.widget.TextView(this);
        tvMessage.setText(langPersian
                ? "بهدلیل ۳ بار ورود رمز عبور اشتباه، تمام رمزها، کلیدهای ۲FA و تنظیمات برنامه برای محافظت از شما بهصورت غیرقابل بازگشت پاک شدند.\n\nبرنامه به حالت اولیه بازگشت؛ حالا باید یک رمز عبور مستر جدید بسازید."
                : "Because the master password was entered incorrectly 3 times, all passwords, 2FA keys and app settings have been permanently erased to protect you.\n\nThe app has been reset; you must now create a new master password.");
        tvMessage.setTextSize(14f);
        tvMessage.setLineSpacing(6f, 1f);
        tvMessage.setTextColor(android.graphics.Color.parseColor("#A1A1AA"));
        root.addView(tvMessage);

        com.google.android.material.button.MaterialButton btnCreateNew =
                new com.google.android.material.button.MaterialButton(this);
        btnCreateNew.setText(langPersian ? "ساخت رمز جدید" : "Create New Password");
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = 32;
        btnCreateNew.setLayoutParams(btnLp);
        btnCreateNew.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#27272A")));
        btnCreateNew.setTextColor(android.graphics.Color.parseColor("#F4F4F5"));
        btnCreateNew.setOnClickListener(v -> {
            sheet.dismiss();
            if (etMasterPassword != null) etMasterPassword.requestFocus();
        });
        root.addView(btnCreateNew);

        sheet.setContentView(root);
        sheet.show();
    }

    private SecretKey deriveKek(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEK_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] kekBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(kekBytes, "AES");
    }

    private String wrapDek(SecretKey dek, SecretKey kek) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, kek);
        byte[] iv = cipher.getIV();
        byte[] wrapped = cipher.doFinal(dek.getEncoded());
        byte[] combined = new byte[iv.length + wrapped.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(wrapped, 0, combined, iv.length, wrapped.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    private SecretKey unwrapDek(String wrappedDekB64, SecretKey kek) throws Exception {
        byte[] combined = Base64.decode(wrappedDekB64, Base64.NO_WRAP);
        byte[] iv = new byte[IV_LENGTH];
        byte[] wrapped = new byte[combined.length - IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
        System.arraycopy(combined, IV_LENGTH, wrapped, 0, wrapped.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        byte[] dekBytes = cipher.doFinal(wrapped);
        return new SecretKeySpec(dekBytes, "AES");
    }

    private byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private void proceedToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
