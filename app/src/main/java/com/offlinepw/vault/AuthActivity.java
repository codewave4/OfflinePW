package com.offlinepw.vault;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AuthActivity extends AppCompatActivity {
    private static final String PREF_NAME = "OfflinePW_Auth";
    private static final String KEY_PIN_HASH = "master_pin_hash";
    private static final String KEY_IS_SETUP = "pin_is_setup";

    private SharedPreferences authPrefs;
    private TextView tvAuthPrompt;
    private TextView tvPinIndicator;
    private final StringBuilder currentPin = new StringBuilder();
    private boolean isSettingUpPin = false;
    private String tempPinToConfirm = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_auth);

        authPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        tvAuthPrompt = findViewById(R.id.tvAuthPrompt);
        tvPinIndicator = findViewById(R.id.tvPinIndicator);

        boolean isSetup = authPrefs.getBoolean(KEY_IS_SETUP, false);
        if (!isSetup) {
            isSettingUpPin = true;
            if (tvAuthPrompt != null) tvAuthPrompt.setText("یک رمز ۶ رقمی مستر تعیین کنید");
        } else {
            isSettingUpPin = false;
            if (tvAuthPrompt != null) tvAuthPrompt.setText("رمز مستر ۶ رقمی را وارد کنید");
        }

        setupNumericKeypad();
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
                    if (currentPin.length() < 6) {
                        currentPin.append(btn.getText().toString());
                        updateIndicator();
                        if (currentPin.length() == 6) {
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
        for (int i = currentPin.length(); i < 6; i++) {
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
                if (tvAuthPrompt != null) tvAuthPrompt.setText("تکرار رمز ۶ رقمی برای تأیید:");
            } else {
                if (tempPinToConfirm.equals(enteredPin)) {
                    String hash = hashPin(enteredPin);
                    authPrefs.edit()
                            .putString(KEY_PIN_HASH, hash)
                            .putBoolean(KEY_IS_SETUP, true)
                            .apply();
                    Toast.makeText(this, "رمز مستر با موفقیت ثبت شد", Toast.LENGTH_SHORT).show();
                    proceedToMain();
                } else {
                    Toast.makeText(this, "رمزها مطابقت ندارند، دوباره امتحان کنید", Toast.LENGTH_SHORT).show();
                    tempPinToConfirm = null;
                    currentPin.setLength(0);
                    updateIndicator();
                    if (tvAuthPrompt != null) tvAuthPrompt.setText("یک رمز ۶ رقمی مستر تعیین کنید");
                }
            }
        } else {
            String savedHash = authPrefs.getString(KEY_PIN_HASH, "");
            String enteredHash = hashPin(enteredPin);

            if (savedHash.equals(enteredHash)) {
                proceedToMain();
            } else {
                Toast.makeText(this, "رمز اشتباه است", Toast.LENGTH_SHORT).show();
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
