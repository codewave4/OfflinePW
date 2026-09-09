package com.offlinepw.vault;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class WelcomeActivity extends AppCompatActivity {
    private static final String PREF_AUTH = "OfflinePW_Auth";
    private static final String PREF_SETTINGS = "OfflinePW_Prefs";
    private static final String KEY_WELCOME_SHOWN = "welcome_shown";
    private static final String GITHUB_URL = "https://github.com/codewave4/OfflinePW";

    private SharedPreferences authPrefs;
    private SharedPreferences settingsPrefs;
    private boolean isPersian = false;

    private TextView tvWelcomeTitle;
    private TextView tvWelcomeBody;
    private MaterialButton btnWelcomeLang;
    private MaterialButton btnWelcomeContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        authPrefs = getSharedPreferences(PREF_AUTH, MODE_PRIVATE);

        boolean forceShow = getIntent().getBooleanExtra("force_show", false);
        if (authPrefs.getBoolean(KEY_WELCOME_SHOWN, false) && !forceShow) {
            goToAuth();
            return;
        }

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_welcome);

        settingsPrefs = getSharedPreferences(PREF_SETTINGS, MODE_PRIVATE);
        isPersian = settingsPrefs.getBoolean("is_persian", false);

        tvWelcomeTitle = findViewById(R.id.tvWelcomeTitle);
        tvWelcomeBody = findViewById(R.id.tvWelcomeBody);
        TextView tvGithubLink = findViewById(R.id.tvGithubLink);
        ImageView ivCopyGithub = findViewById(R.id.ivCopyGithub);
        btnWelcomeLang = findViewById(R.id.btnWelcomeLang);
        btnWelcomeContinue = findViewById(R.id.btnWelcomeContinue);

        if (tvGithubLink != null) tvGithubLink.setText(GITHUB_URL);

        if (ivCopyGithub != null) {
            ivCopyGithub.setOnClickListener(v -> copyGithubLink());
        }

        if (btnWelcomeLang != null) {
            btnWelcomeLang.setOnClickListener(v -> {
                isPersian = !isPersian;
                settingsPrefs.edit().putBoolean("is_persian", isPersian).apply();
                updateTexts();
            });
        }

        if (btnWelcomeContinue != null) {
            btnWelcomeContinue.setOnClickListener(v -> {
                authPrefs.edit().putBoolean(KEY_WELCOME_SHOWN, true).apply();
                goToAuth();
            });
        }

        updateTexts();
    }

    private void updateTexts() {
        if (btnWelcomeLang != null) {
            btnWelcomeLang.setText(isPersian ? "FA" : "EN");
        }
        if (tvWelcomeTitle != null) {
            tvWelcomeTitle.setText(isPersian ? "به OfflinePW خوش آمدید" : "Welcome to OfflinePW");
        }
        if (tvWelcomeBody != null) {
            tvWelcomeBody.setText(isPersian ?
                    "امنیت داده های شما بر پایه ی رمزنگاری استاندارد صنعتی (AES-256) و رمز عبور مستری است که تنها خودتان می‌دانید. برای بالاترین سطح امنیت، توصیه میشود رمز عبور مستری ترکیبی از حروف بزرگ، حروف کوچک، عدد و نماد انتخاب کنید.\\n\\nتوجه: پس از ۳ بار ورود رمز اشتباه، تمام داده‌ها برای همیشه پاک می‌شوند.\\n\\nOfflinePW کاملاً متنباز (Open Source) است و میتوانید تمام کدهای آن را بررسی کنید." :
                    "Your data is protected by industry-standard encryption (AES-256) and the master password that only you know. For maximum security, we recommend choosing a master password that combines uppercase, lowercase, numbers, and symbols.\\n\\nNote: after 3 wrong attempts, all data is permanently erased.\\n\\nOfflinePW is fully open-source; you can review every line of its code.");
        }
        if (btnWelcomeContinue != null) {
            btnWelcomeContinue.setText(isPersian ? "ادامه" : "Continue");
        }
    }

    private void copyGithubLink() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("GitHub", GITHUB_URL);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, isPersian ? "لینک کپی شد" : "Link copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void goToAuth() {
        if (getIntent().getBooleanExtra("force_show", false)) {
            finish();
            return;
        }
        Intent intent = new Intent(this, AuthActivity.class);
        startActivity(intent);
        finish();
    }
}
