package com.offlinepw.vault;

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
import com.offlinepw.vault.crypto.CryptoManager;
import com.offlinepw.vault.crypto.VaultSession;
import java.io.File;
import java.util.UUID;
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
    // ولت فریبنده — نام کلیدها عمداً خنثی («recovery») است تا در prefs هم چیزی
    // لو نرود؛ و فایل DB جدا دارد.
    private static final String KEY_RECOVERY_SALT = "recovery_kek_salt";
    private static final String KEY_RECOVERY_WRAPPED_DEK = "recovery_wrapped_dek";
    private static final String DECOY_DB_NAME = MainActivity.VaultDatabaseHelper.DECOY_DB_NAME;

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
    private TextInputLayout tilDecoyPassword;
    private TextInputEditText etDecoyPassword;
    private String tempDecoyToConfirm = null;
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
        tilDecoyPassword = findViewById(R.id.tilDecoyPassword);
        etDecoyPassword = findViewById(R.id.etDecoyPassword);
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

        if (etDecoyPassword != null) {
            etDecoyPassword.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    attemptSubmit();
                    return true;
                }
                return false;
            });
        }

        // اگر در مرحله‌ی «تأیید رمز» بودیم و صفحه چرخید، رمز مرحله اول هرگز در
        // حافظه‌ی ماندگار ذخیره نمی‌شود (سیاست امنیتی)؛ به‌جای گیج‌کننده بودن،
        // به کاربر می‌گوییم که پروسه از نو شروع می‌شود.
        if (savedInstanceState != null && isSettingUpPin
                && savedInstanceState.getBoolean("setup_confirm_step", false)) {
            Toast.makeText(this, isPersian
                    ? "چرخش صفحه، ساخت رمز را قطع کرد؛ لطفاً رمز را دوباره وارد کنید"
                    : "Screen rotation interrupted password setup; please enter it again",
                    Toast.LENGTH_SHORT).show();
        }

        updateTexts();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // خودِ رمز هرگز ذخیره نمی‌شود — فقط «در مرحله ۲ بودم» برای پیام UX.
        if (isSettingUpPin) {
            outState.putBoolean("setup_confirm_step", tempPasswordToConfirm != null);
        }
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
        // فیلد ولت فریبنده فقط در مرحله اولِ ساخت ولت دیده می‌شود؛ بعد از آن هیچ‌گاه.
        if (tilDecoyPassword != null) {
            boolean showDecoy = isSettingUpPin && tempPasswordToConfirm == null;
            tilDecoyPassword.setVisibility(showDecoy ? View.VISIBLE : View.GONE);
            if (showDecoy) {
                tilDecoyPassword.setHint(isPersian
                        ? "رمز ولت فریبنده — اختیاری" : "Decoy vault password — optional");
            }
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
                String decoyEntered = (etDecoyPassword != null && etDecoyPassword.getText() != null)
                        ? etDecoyPassword.getText().toString() : "";
                if (!decoyEntered.isEmpty()) {
                    if (decoyEntered.length() < MIN_PASSWORD_LENGTH) {
                        Toast.makeText(this, isPersian
                                ? ("رمز فریبنده هم باید حداقل " + MIN_PASSWORD_LENGTH + " کاراکتر باشد")
                                : ("Decoy password must also be at least " + MIN_PASSWORD_LENGTH + " characters"),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (decoyEntered.equals(entered)) {
                        Toast.makeText(this, isPersian
                                ? "رمز ولت فریبنده باید با رمز اصلی متفاوت باشد"
                                : "Decoy password must differ from the master password",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                tempDecoyToConfirm = decoyEntered.isEmpty() ? null : decoyEntered;
                if (etDecoyPassword != null) etDecoyPassword.setText("");
                tempPasswordToConfirm = entered;
                if (etMasterPassword != null) etMasterPassword.setText("");
                updateTexts();
            } else {
                if (tempPasswordToConfirm.equals(entered)) {
                    createNewVaultKey(entered, tempDecoyToConfirm);
                } else {
                    Toast.makeText(this, isPersian ? "رمزها مطابقت ندارند، دوباره امتحان کنید" : "Passwords do not match, try again", Toast.LENGTH_SHORT).show();
                    tempPasswordToConfirm = null;
                    tempDecoyToConfirm = null;
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

    private void createNewVaultKey(String password, final String decoyPassword) {
        setUnlockButtonBusy(true);
        new Thread(() -> {
            try {
                byte[] salt = generateRandomBytes(SALT_LENGTH_BYTES);
                SecretKey kek = deriveKek(password, salt);

                KeyGenerator dekGen = KeyGenerator.getInstance("AES");
                dekGen.init(DEK_LENGTH_BITS, new SecureRandom());
                final SecretKey dek = dekGen.generateKey();

                final String wrappedDek = wrapDek(dek, kek);

// ── ولت فریبنده (نسل دوم: بدون ردپا در prefs) ──
                // کلید دوم هیچ‌جا ذخیره نمی‌شود؛ مستقیماً با همان PBKDF2 روی saltِ
                // مشتق‌شده از salt اصلی ساخته می‌شود. تنها «اثر» روی دیسک، فایل DB
                // فریبنده با نام خنثی است. (ردیف‌های wrapped نسل یکم در prefs فقط
                // برای سازگاریِ بازکردن خوانده می‌شوند؛ ساخت جدید چیزی نمی‌نویسد.)
                final boolean hasDecoy = decoyPassword != null && !decoyPassword.isEmpty();
                final SecretKey dek2 = hasDecoy
                        ? deriveDekDirect(decoyPassword, deriveDecoySalt(salt))
                        : null;

                authPrefs.edit()
                        .putString(KEY_KEK_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                        .putString(KEY_WRAPPED_DEK, wrappedDek)
                        .putBoolean(KEY_IS_SETUP, true)
                        .putInt(KEY_FAILED_ATTEMPTS, 0)
                        .commit();

                // دیتابیس فریبنده با چند آیتمِ باورپذیرِ بی‌خطر پر می‌شود تا
                // «خالی بودن» زیر سؤال نبرد. اگر ساخت decoy با شکست مواجه شود به کاربر اطلاع داده می‌شود.
                boolean decoySeeded = true;
                if (hasDecoy) {
                    decoySeeded = seedDecoyVault(dek2);
                }

                final boolean finalDecoySeeded = decoySeeded;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    VaultSession.setDek(dek);
                    setUnlockButtonBusy(false);
                    if (hasDecoy && !finalDecoySeeded) {
                        Toast.makeText(this, isPersian
                                ? "ولت اصلی ساخته شد، اما ساخت ولت فریبنده با خطا مواجه شد"
                                : "Primary vault created, but decoy vault creation failed", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, isPersian ? "رمز مستر با موفقیت ثبت شد" : "Master Password set successfully", Toast.LENGTH_SHORT).show();
                    }
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

    /** آیتم‌های نمایشیِ ولت فریبنده — روی DEK دوم و فایل DB دوم نوشته می‌شوند. */
    private boolean seedDecoyVault(SecretKey dek2) {
        try {
            MainActivity.VaultDatabaseHelper helper = null;
            try {
            VaultSession.setDek(dek2); // clear() داخلش پرچم را صفر می‌کند؛ بعد از آن setDecoy
            VaultSession.setDecoy(true);
            helper = new MainActivity.VaultDatabaseHelper(this);
            CryptoManager crypto = new CryptoManager();
            long now = System.currentTimeMillis();
            long day = 24L * 3600 * 1000L;
            String[][] seeds = isPersian ? new String[][]{
                    {"جیمیل قدیمی", "LOGIN", "old.account.1377@gmail.com", "Parvaz#1381", "فقط پوشه اسپمش چک میشه", "", "gmail.com", "41"},
                    {"وای‌فای خانه", "WIFI", "TP-Link_Home2.4G", "447711990012abcd", "پسوند مودم اتاق", "", "", "12"},
                    {"نتفلیکس (اشتراکی)", "LOGIN", "share.acct@inbox.test", "N3tfl1x-Demo!", "حساب اشتراکی با همخونه", "", "netflix.com", "33"},
                    {"سامانه دانشگاه", "LOGIN", "st-81234567", "12345678aB", "نمرات ترم ۵", "", "edu-example.test", "60"}
            } : new String[][]{
                    {"Old Gmail", "LOGIN", "old.account.1998@gmail.com", "Parvaz#1381", "only the spam folder gets checked", "", "gmail.com", "41"},
                    {"Home WiFi", "WIFI", "TP-Link_Home2.4G", "447711990012abcd", "bedroom router", "", "", "12"},
                    {"Netflix (shared)", "LOGIN", "share.acct@inbox.test", "N3tfl1x-Demo!", "shared with roommate", "", "netflix.com", "33"},
                    {"University Portal", "LOGIN", "st-81234567", "12345678aB", "semester 5 grades", "", "edu-example.test", "60"}
            };
            for (String[] r : seeds) {
                long age = Long.parseLong(r[7]) * day;
                helper.insertItem(new MainActivity.VaultItem(
                        UUID.randomUUID().toString(), r[0], r[1], r[2], r[3], r[4],
                        r[5], r[6], false, now - age, now - age), crypto);
            }
            return true;
            } finally {
                // اگر insert وسط کار بترکد، connection باز نمی‌ماند
                if (helper != null) {
                    try { helper.close(); } catch (Exception ignored) { }
                }
            }
        } catch (Throwable t) {
            // خطای سطح دیتابیس را به فراخواننده برمی‌گردانیم
            return false;
        } finally {
            VaultSession.setDecoy(false);
        }
    }

    /**
     * باز کردن قفل: PBKDF2 (۶۰۰k دور) و بازکردن DEK روی ترد پسزمینه انجام میشود
     * تا رابط کاربری فریز نشود و اپ بلافاصله پس از آمادهشدن کلید باز شود.
     */
    private void attemptUnlockAsync(String password) {
        setUnlockButtonBusy(true);
        new Thread(() -> {
            SecretKey unlocked = null;
            boolean decoyHit = false;
            try {
                String saltB64 = authPrefs.getString(KEY_KEK_SALT, "");
                String wrappedDek = authPrefs.getString(KEY_WRAPPED_DEK, "");
                if (saltB64.isEmpty() || wrappedDek.isEmpty()) {
                    throw new IllegalStateException("No vault keys found");
                }
                byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
                SecretKey kek = deriveKek(password, salt);
                unlocked = unwrapDek(wrappedDek, kek);
            } catch (Throwable primaryFail) {
                // رمز اصلی نشد — همین ورودی روی ولت فریبنده هم امتحان می‌شود. اگر
                // تنظیم نشده باشد (فایلش نیست) شکست آنی و بی‌صداست؛ پیام و
                // زمان‌بندی قابل تشخیص از «حالت بدون فریبنده» نیست.
                // Throwable نه Exception: خطاهای نادرِ native/prefs هم به «رمز
                // اشتباه» تبدیل می‌شوند، نه force-close.
                try { unlocked = tryDecoyUnlock(password); } catch (Throwable t) { unlocked = null; }
                decoyHit = unlocked != null;
            }
            if (unlocked == null) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setUnlockButtonBusy(false);
                    if (etMasterPassword != null) etMasterPassword.setText("");
                    registerFailedAttempt();
                });
                return;
            }
            final SecretKey dek = unlocked;
            final boolean wasDecoy = decoyHit;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                VaultSession.setDek(dek);
                // ترتیب مهم: setDek داخلش clear() صدا می‌زند که پرچم decoy را هم صفر
                // می‌کند؛ پس پرچم باید بعد از آن ست شود.
                VaultSession.setDecoy(wasDecoy);
                // تنها جایی که شمارندهی تلاشها ریست میشود: ورود موفق رمز درست (در هر دو ولت).
                authPrefs.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply();
                setUnlockButtonBusy(false);
                proceedToMain();
            });
        }).start();
    }

    /** salt ولت فریبنده = تراشیده‌ی SHA-256(salt اصلی + برچسب دامنه) — قابل بازسازی، بدون ذخیره. */
    private static byte[] deriveDecoySalt(byte[] masterSalt) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        md.update(masterSalt);
        md.update("offlinepw-decoy-kdf-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.Arrays.copyOf(md.digest(), SALT_LENGTH_BYTES);
    }

    /** DEK ولت فریبنده — مستقیماً از خودِ رمز با همان PBKDF2؛ هیچ wrapped بلابی نیست. */
    private static SecretKey deriveDekDirect(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, DEK_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * تلاش بازکردن ولت فریبنده؛ null یعنی شکست. اعتبارسنجی = باز شدن خودِ DB با
     * کلید مشتق‌شده (SQLCipher با کلید غلط می‌شکند → catch → null). ردیف‌های
     * wrapped نسل‌یکم اگر در prefs باشند، همان مسیر قدیمی استفاده می‌شود.
     */
    private SecretKey tryDecoyUnlock(String password) {
        MainActivity.VaultDatabaseHelper helper = null;
        boolean ok = false;
        SecretKey dek2 = null;
        try {
            MainActivity.VaultDatabaseHelper.ensureDecoyMigration(this);
            if (!getDatabasePath(MainActivity.VaultDatabaseHelper.DECOY_DB_NAME).exists()) return null;

            String rSaltB64 = authPrefs.getString(KEY_RECOVERY_SALT, "");
            String rWrapped = authPrefs.getString(KEY_RECOVERY_WRAPPED_DEK, "");
            if (!rSaltB64.isEmpty() && !rWrapped.isEmpty()) {
                dek2 = unwrapDek(rWrapped, deriveKek(password, Base64.decode(rSaltB64, Base64.NO_WRAP)));
            } else {
                byte[] masterSalt = Base64.decode(authPrefs.getString(KEY_KEK_SALT, ""), Base64.NO_WRAP);
                dek2 = deriveDekDirect(password, deriveDecoySalt(masterSalt));
            }

            VaultSession.setDecoy(true);
            helper = new MainActivity.VaultDatabaseHelper(this);
            net.sqlcipher.database.SQLiteDatabase db = helper.getWritableDatabase(
                    Base64.encodeToString(dek2.getEncoded(), Base64.NO_WRAP));
            android.database.Cursor c = db.rawQuery("SELECT COUNT(*) FROM sqlite_master", null);
            try {
                ok = c.moveToNext();
            } finally {
                c.close();
            }
            return ok ? dek2 : null;
        } catch (Throwable t) {
            return null; // کلید غلط / فایل ناپدید / هر خطای دیگر → فقط «رمز اشتباه»
        } finally {
            if (helper != null) {
                try { helper.close(); } catch (Exception ignored) { }
            }
            if (!ok) VaultSession.setDecoy(false);
        }
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
     *
     * دو نکته‌ی مهم در این پیاده‌سازی:
     *  - بازگشتی هر delete() چک و در انتها presence فایل‌ها verify می‌شود؛ در صورت شکست
     *    به کاربر هشدار داده می‌شود (دیگر «پاک شد» را بدون تأیید نمی‌گوییم).
     *  - flag زبان و تم کاربر دوباره ذخیره می‌شود تا بعد از ساخت رمز جدید، UI به‌طور
     *    ناگهانی به انگلیسی/تیم پیش‌فرض برنگردد.
     */
    private void wipeVaultAndShowMessage() {
        // زبان و تم کاربر را قبل از پاکشدن تنظیمات نگه می‌داریم.
        final boolean langPersian = isPersian;
        final boolean darkMode = settingsPrefs.getBoolean("is_dark_mode", true);

        // ۱) حذف فایل دیتابیس (+ فایلهای جانبی -wal / -shm / -journal) با تأیید نتیجه
        boolean wipeOk = true;
        File dbDir = null;
        try {
            deleteDatabase(DB_NAME);
        } catch (Exception ignored) {
            wipeOk = false;
        }
        // فایل‌های ولت فریبنده (نسل دوم و نسل یکم) — نبودنشان خطا نیست
        for (String decoyDb : new String[]{DECOY_DB_NAME,
                MainActivity.VaultDatabaseHelper.LEGACY_DECOY_DB_NAME}) {
            try {
                deleteDatabase(decoyDb);
            } catch (Exception ignored) {
            }
        }
        // ۲) حذف فایل‌های دیتابیس شناخته‌شده و جانبی آنها در پوشه‌ی databases
        try {
            dbDir = new File(getApplicationInfo().dataDir, "databases");
            if (dbDir.isDirectory()) {
                File[] files = dbDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f != null && f.isFile()) {
                            String name = f.getName();
                            if (name.startsWith(DB_NAME)
                                    || name.startsWith(DECOY_DB_NAME)
                                    || name.startsWith(MainActivity.VaultDatabaseHelper.LEGACY_DECOY_DB_NAME)) {
                                if (!f.delete()) wipeOk = false;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            wipeOk = false;
        }
        // ۲.۱) verify: هیچ اثری از دیتابیس (شامل فایلهای جانبی) باقی نمانده باشد
        if (dbDir != null) {
            java.util.List<String> wipeNames = new java.util.ArrayList<>();
            for (String base : new String[]{DB_NAME, DECOY_DB_NAME,
                    MainActivity.VaultDatabaseHelper.LEGACY_DECOY_DB_NAME}) {
                wipeNames.add(base);
                wipeNames.add(base + "-wal");
                wipeNames.add(base + "-shm");
                wipeNames.add(base + "-journal");
            }
            for (String name : wipeNames) {
                if (new File(dbDir, name).exists()) wipeOk = false;
            }
        }

        // ۳) پاک کردن همهی SharedPreferences (کلیدها، نشان راهاندازی، تنظیمات زبان/تم)
        authPrefs.edit().clear().commit();
        settingsPrefs.edit().clear().commit();
        // ۳.۱) زبان و تم کاربر را مجدداً ثبت می‌کنیم تا در ادامه‌ی نشست حفظ شود
        settingsPrefs.edit()
                .putBoolean("is_persian", langPersian)
                .putBoolean("is_dark_mode", darkMode)
                .apply();
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
        String baseMsg = langPersian
                ? "بهدلیل ۳ بار ورود رمز عبور اشتباه، تمام رمزها، کلیدهای ۲FA و تنظیمات برنامه برای محافظت از شما بهصورت غیرقابل بازگشت پاک شدند.\n\nبرنامه به حالت اولیه بازگشت؛ حالا باید یک رمز عبور مستر جدید بسازید."
                : "Because the master password was entered incorrectly 3 times, all passwords, 2FA keys and app settings have been permanently erased to protect you.\n\nThe app has been reset; you must now create a new master password.";
        // اگر verify حذف شکست خورد، صادقانه هشدار می‌دهیم.
        if (!wipeOk) {
            baseMsg += "\n\n" + (langPersian
                    ? "⚠️ هشدار: برخی فایل‌ها تأییدِ حذف نشدند. برای اطمینان کامل، از تنظیمات سیستم > برنامه‌ها، «داده‌های» این برنامه را هم پاک کنید."
                    : "⚠️ Warning: deletion of some files could not be verified. For complete certainty, also clear this app's data from system settings (Settings > Apps).");
        }
        tvMessage.setText(baseMsg);
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
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEK_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] kekBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(kekBytes, "AES");
        } finally {
            // کاراکترهای رمز را بلافاصله از حافظه پاک می‌کنیم (بهداشت کلید)
            spec.clearPassword();
        }
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
