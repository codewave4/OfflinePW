package com.offlinepw.vault;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.offlinepw.vault.adapter.VaultAdapter;
import com.offlinepw.vault.crypto.CryptoManager;
import com.offlinepw.vault.db.VaultDatabaseHelper;
import com.offlinepw.vault.model.VaultItem;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private VaultAdapter adapter;
    private VaultDatabaseHelper dbHelper;
    private CryptoManager cryptoManager;
    private EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new VaultDatabaseHelper(this);
        cryptoManager = new CryptoManager(this);

        RecyclerView rvVault = findViewById(R.id.rvVault);
        etSearch = findViewById(R.id.etSearch);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

        adapter = new VaultAdapter(item -> {
            // نمایش جزییات هنگام لمس کارت
        });

        rvVault.setLayoutManager(new LinearLayoutManager(this));
        rvVault.setAdapter(adapter);

        // جستجوی لحظه‌ای با تایپ کاربر
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

        loadVaultData();
    }

    private void loadVaultData() {
        List<VaultItem> items = dbHelper.getAllDecryptedItems(cryptoManager);
        if (items.isEmpty()) {
            // افزودن نمونه تستی اولیه در صورت خالی بودن دیتابیس
            VaultItem sample = new VaultItem("1", "Google Account", "login", "user@gmail.com", "SecureP@ss2026", "ایمیل کاری");
            dbHelper.insertItem(sample, cryptoManager);
            items = dbHelper.getAllDecryptedItems(cryptoManager);
        }
        adapter.setItems(items);
    }
}
