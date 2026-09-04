package com.offlinepw.vault;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AuthActivity extends AppCompatActivity {
    private static final int PIN_LENGTH = 8;
    private static final String PREF_AUTH = "OfflinePW_Auth";
    private static final String PREF_SETTINGS = "OfflinePW_Prefs";
    private static final String KEY_PIN_HASH = "master_pin_hash";
    private static final String KEY_IS_SETUP = "pin_is_setup";

    private SharedPreferences authPrefs;
    private SharedPreferences settingsPrefs;
    private TextView tvAuthPrompt;
    private TextView tvAuthWarning;
    private TextView tvPinIndicator;
    private MaterialButton btnAuthLang;
    private final StringBuilder currentPin = new StringBuilder();
    private boolean isSettingUpPin = false;
    private String tempPinToConfirm = null;
    private boolean isPersian = false;

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
        tvPinIndicator = findViewById(R.id.tvPinIndicator);
        btnAuthLang = findViewById(R.id.btnAuthLang);

        boolean isSetup = authPrefs.getBoolean(KEY_IS_SETUP, false);
        isSettingUpPin = !isSetup;

        if (btnAuthLang != null) {
            btnAuthLang.setOnClickListener(v -> {
                isPersian = !isPersian;
                settingsPrefs.edit().putBoolean("is_persian", isPersian).apply();
                updateTexts();
            });
        }

        updateTexts();
        setupNumericKeypad();
    }

    private void updateTexts() {
        if (btnAuthLang != null) {
            btnAuthLang.setText(isPersian ? "FA" : "EN");
        }

        if (tvAuthWarning != null) {
            tvAuthWarning.setText(isPersian ?
                    "رمز عبور شما در برنامه ذخیره نخواهد شد؛ بنابراین اگر آن را فراموش کنید، بازیابی آن غیرممکن است." :
                    "Your master PIN is never stored; therefore, if forgotten, recovery is mathematically impossible.");
        }

        if (tvAuthPrompt != null) {
            if (isSettingUpPin) {
                if (tempPinToConfirm == null) {
                    tvAuthPrompt.setText(isPersian ? "یک رمز ۸ رقمی مستر تعیین کنید" : "Create an 8-digit Master PIN");
                } else {
                    tvAuthPrompt.setText(isPersian ? "تکرار رمز ۸ رقمی برای تأیید:" : "Confirm your 8-digit Master PIN:");
                }
            } else {
                tvAuthPrompt.setText(isPersian ? "رمز مستر ۸ رقمی را وارد کنید" : "Enter your 8-digit Master PIN");
            }
        }
    }

    private void setupNumericKeypad() {
        int[] buttonIds = new int[]{
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };

        for (int id : buttonIds) {
            Button btn = findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(v -> {
                    if (currentPin.length() < PIN_LENGTH) {
                        currentPin.append(btn.getText().toString());
                        updateIndicator();
                        if (currentPin.length() == PIN_LENGTH) {
                            handlePinComplete();
                        }
                    }
                });
            }
        }

        Button btnBackspace = findViewById(R.id.btnBackspace);
        if (btnBackspace != null) {
            btnBackspace.setOnClickListener(v -> {
                if (currentPin.length() > 0) {
                    currentPin.deleteCharAt(currentPin.length() - 1);
                    updateIndicator();
                }
            });
        }
    }

    private void updateIndicator() {
        if (tvPinIndicator == null) return;
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < currentPin.length(); i++) {
            dots.append("● ");
        }
        for (int i = currentPin.length(); i < PIN_LENGTH; i++) {
            dots.append("○ ");
        }
        tvPinIndicator.setText(dots.toString().trim());
    }

    private void handlePinComplete() {
        String enteredPin = currentPin.toString();

        if (isSettingUpPin) {
            if (tempPinToConfirm == null) {
                tempPinToConfirm = enteredPin;
                currentPin.setLength(0);
                updateIndicator();
                updateTexts();
            } else {
                if (tempPinToConfirm.equals(enteredPin)) {
                    String hash = hashPin(enteredPin);
                    authPrefs.edit()
                            .putString(KEY_PIN_HASH, hash)
                            .putBoolean(KEY_IS_SETUP, true)
                            .apply();
                    Toast.makeText(this, isPersian ? "رمز مستر با موفقیت ثبت شد" : "Master PIN set successfully", Toast.LENGTH_SHORT).show();
                    proceedToMain();
                } else {
                    Toast.makeText(this, isPersian ? "رمزها مطابقت ندارند، دوباره امتحان کنید" : "PINs do not match, try again", Toast.LENGTH_SHORT).show();
                    tempPinToConfirm = null;
                    currentPin.setLength(0);
                    updateIndicator();
                    updateTexts();
                }
            }
        } else {
            String savedHash = authPrefs.getString(KEY_PIN_HASH, "");
            String enteredHash = hashPin(enteredPin);

            if (savedHash.equals(enteredHash)) {
                proceedToMain();
            } else {
                Toast.makeText(this, isPersian ? "رمز اشتباه است" : "Incorrect PIN", Toast.LENGTH_SHORT).show();
                currentPin.setLength(0);
                updateIndicator();
            }
        }
    }

    private void proceedToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((pin + "OfflinePW_Salt_2026").getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return pin;
        }
    }
}
