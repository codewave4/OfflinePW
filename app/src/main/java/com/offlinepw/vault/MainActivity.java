package com.offlinepw.vault;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new VaultDatabaseHelper(this);
        cryptoManager = new CryptoManager(this);

        RecyclerView rvVault = findViewById(R.id.rvVault);
        etSearch = findViewById(R.id.etSearch);
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);

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

        loadVaultData();
    }

    private void loadVaultData() {
        List<VaultItem> items = dbHelper.getAllDecryptedItems(cryptoManager);
        if (items.isEmpty()) {
            VaultItem sample = new VaultItem(UUID.randomUUID().toString(), "Google Account", "LOGIN", "user@gmail.com", "kX9#mP2$vL8@qW4!", "حساب اصلی گوگل");
            dbHelper.insertItem(sample, cryptoManager);
            items = dbHelper.getAllDecryptedItems(cryptoManager);
        }
        adapter.setItems(items);
    }

    private void showAddDialog(VaultItem existingItem) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_vault_item, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextInputEditText etTitle = dialogView.findViewById(R.id.etTitle);
        TextInputEditText etCategory = dialogView.findViewById(R.id.etCategory);
        TextInputEditText etUsername = dialogView.findViewById(R.id.etUsername);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etPassword);
        TextInputEditText etNotes = dialogView.findViewById(R.id.etNotes);
        View btnGenerate = dialogView.findViewById(R.id.btnGenerate);
        View btnCancel = dialogView.findViewById(R.id.btnCancel);
        View btnSave = dialogView.findViewById(R.id.btnSave);

        if (existingItem != null) {
            tvDialogTitle.setText("ویرایش رکورد");
            etTitle.setText(existingItem.getTitle());
            etCategory.setText(existingItem.getCategory());
            etUsername.setText(existingItem.getUsername());
            etPassword.setText(existingItem.getPassword());
            etNotes.setText(existingItem.getNotes());
        }

        btnGenerate.setOnClickListener(v -> {
            etPassword.setText(generateStrongPassword(16));
            Toast.makeText(this, "رمز عبور قدرتمند تولید شد", Toast.LENGTH_SHORT).show();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
            String category = etCategory.getText() != null ? etCategory.getText().toString().trim() : "LOGIN";
            String username = etUsername.getText() != null ? etUsername.getText().toString().trim() : "";
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            String notes = etNotes.getText() != null ? etNotes.getText().toString().trim() : "";

            if (title.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "عنوان و رمز عبور الزامی است", Toast.LENGTH_SHORT).show();
                return;
            }

            String id = existingItem != null ? existingItem.getId() : UUID.randomUUID().toString();
            VaultItem item = new VaultItem(id, title, category, username, password, notes);
            dbHelper.insertItem(item, cryptoManager);
            loadVaultData();
            dialog.dismiss();
            Toast.makeText(this, "با موفقیت و رمزنگاری AES-256 ذخیره شد", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showEditOrDeleteDialog(VaultItem item) {
        String[] options = {"ویرایش", "کپی نام کاربری", "کپی یادداشت", "حذف"};
        new AlertDialog.Builder(this)
                .setTitle(item.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showAddDialog(item);
                    } else if (which == 1) {
                        copyToClipboard("Username", item.getUsername());
                    } else if (which == 2) {
                        copyToClipboard("Notes", item.getNotes());
                    } else if (which == 3) {
                        // Delete logic
                        dbHelper.getWritableDatabase().delete(VaultDatabaseHelper.TABLE_ITEMS, VaultDatabaseHelper.COLUMN_ID + "=?", new String[]{item.getId()});
                        loadVaultData();
                        Toast.makeText(this, "رکورد حذف شد", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void copyToClipboard(String label, String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "موردی برای کپی وجود ندارد", Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, label + " کپی شد", Toast.LENGTH_SHORT).show();
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
