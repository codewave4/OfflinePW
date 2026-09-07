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

        if (authPrefs.getBoolean(KEY_WELCOME_SHOWN, false)) {
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
                    "امنیت شما کاملاً به رمز عبور مستری که انتخاب می‌کنید بستگی دارد. هر چقدر رمزتان قوی‌تر و ترکیبی‌تر باشد (حروف بزرگ، حروف کوچک، عدد، نماد)، شکستن آن سخت‌تر می‌شود — خودتان بهتر می‌دانید چطور رمزی قوی بسازید.\n\nOfflinePW کاملاً متن‌باز (Open Source) است و می‌توانید تمام کدهای آن را بررسی کنید." :
                    "Your security depends entirely on the master password you choose. The stronger and more mixed it is (uppercase, lowercase, numbers, symbols), the harder it becomes to break — you know best how to make it strong.\n\nOfflinePW is fully open-source; you can review every line of its code.");
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
        Intent intent = new Intent(this, AuthActivity.class);
        startActivity(intent);
        finish();
    }
}
