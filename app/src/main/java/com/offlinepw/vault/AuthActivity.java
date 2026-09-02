package com.offlinepw.vault;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class AuthActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "OfflinePW_Prefs";
    private static final String KEY_PIN_HASH = "master_pin_hash";
    private static final String KEY_IS_PERSIAN = "is_persian";

    private SharedPreferences prefs;
    private boolean isSetupMode = false;
    private boolean isPersian = false;

    private TextView tvAuthTitle, tvAuthWarning;
    private TextInputLayout tilPin;
    private TextInputEditText etPin;
    private MaterialButton btnSubmitPin, btnAuthLang;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_auth);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedHash = prefs.getString(KEY_PIN_HASH, null);
        isSetupMode = (savedHash == null || savedHash.isEmpty());
        isPersian = prefs.getBoolean(KEY_IS_PERSIAN, false);

        tvAuthTitle = findViewById(R.id.tvAuthTitle);
        tvAuthWarning = findViewById(R.id.tvAuthWarning);
        tilPin = findViewById(R.id.tilPin);
        etPin = findViewById(R.id.etPin);
        btnSubmitPin = findViewById(R.id.btnSubmitPin);
        btnAuthLang = findViewById(R.id.btnAuthLang);

        btnAuthLang.setOnClickListener(v -> {
            isPersian = !isPersian;
            prefs.edit().putBoolean(KEY_IS_PERSIAN, isPersian).apply();
            updateUI();
        });

        btnSubmitPin.setOnClickListener(v -> handleSubmitPin());

        updateUI();
    }

    private void updateUI() {
        btnAuthLang.setText(isPersian ? "FA" : "EN");

        if (isSetupMode) {
            if (isPersian) {
                tvAuthTitle.setText("ایجاد پین‌کد ۶ رقمی گاوصندوق");
                tvAuthWarning.setText("⚠️ هشدار مهم:\nرمز عبور شما در برنامه ذخیره نخواهد شد؛ بنابراین اگر آن را فراموش کنید، بازیابی آن غیرممکن است.");
                tilPin.setHint("پین ۶ رقمی دلخواه");
                btnSubmitPin.setText("ثبت و شروع کار");
            } else {
                tvAuthTitle.setText("Set 6-Digit Master PIN");
                tvAuthWarning.setText("⚠️ Critical Warning:\nYour master PIN will NOT be stored in the app; therefore, if you forget it, recovery is impossible.");
                tilPin.setHint("6-digit PIN");
                btnSubmitPin.setText("Create & Proceed");
            }
        } else {
            if (isPersian) {
                tvAuthTitle.setText("ورود به گاوصندوق OFFLINEPW");
                tvAuthWarning.setText("برای دسترسی به رمزهای خود، پین ۶ رقمی را وارد کنید.");
                tilPin.setHint("پین کد ۶ رقمی");
                btnSubmitPin.setText("ورود");
            } else {
                tvAuthTitle.setText("Unlock OFFLINEPW");
                tvAuthWarning.setText("Enter your 6-digit PIN to access vault.");
                tilPin.setHint("6-digit PIN");
                btnSubmitPin.setText("Unlock");
            }
        }
    }

    private void handleSubmitPin() {
        String pin = etPin.getText() != null ? etPin.getText().toString().trim() : "";
        if (pin.length() != 6) {
            Toast.makeText(this, isPersian ? "پین کد باید دقیقاً ۶ رقم باشد" : "PIN must be exactly 6 digits", Toast.LENGTH_SHORT).show();
            return;
        }

        String pinHash = hashPin(pin);

        if (isSetupMode) {
            // ذخیره هش پین کد برای اولین بار
            prefs.edit().putString(KEY_PIN_HASH, pinHash).apply();
            Toast.makeText(this, isPersian ? "پین با موفقیت ایجاد شد" : "PIN configured successfully", Toast.LENGTH_SHORT).show();
            proceedToMain();
        } else {
            // بررسی پین وارد شده با هش ذخیره‌شده
            String savedHash = prefs.getString(KEY_PIN_HASH, "");
            if (savedHash.equals(pinHash)) {
                proceedToMain();
            } else {
                Toast.makeText(this, isPersian ? "پین کد اشتباه است!" : "Incorrect PIN!", Toast.LENGTH_SHORT).show();
                etPin.setText("");
            }
        }
    }

    private void proceedToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("SALT_OFFLINEPW_" + pin).getBytes(StandardCharsets.UTF_8));
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
