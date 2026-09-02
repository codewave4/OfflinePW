package com.offlinepw.vault.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.offlinepw.vault.crypto.CryptoManager;
import com.offlinepw.vault.model.VaultItem;
import java.util.ArrayList;
import java.util.List;

public class VaultDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "offlinepw_vault.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_ITEMS = "vault_items";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE_ENC = "title_enc";
    public static final String COLUMN_CATEGORY = "category";
    public static final String COLUMN_USERNAME_ENC = "username_enc";
    public static final String COLUMN_PASSWORD_ENC = "password_enc";
    public static final String COLUMN_NOTES_ENC = "notes_enc";
    public static final String COLUMN_IS_FAV = "is_favorite";

    public VaultDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_TABLE = "CREATE TABLE " + TABLE_ITEMS + " ("
                + COLUMN_ID + " TEXT PRIMARY KEY,"
                + COLUMN_TITLE_ENC + " TEXT,"
                + COLUMN_CATEGORY + " TEXT,"
                + COLUMN_USERNAME_ENC + " TEXT,"
                + COLUMN_PASSWORD_ENC + " TEXT,"
                + COLUMN_NOTES_ENC + " TEXT,"
                + COLUMN_IS_FAV + " INTEGER DEFAULT 0)";
        db.execSQL(CREATE_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ITEMS);
        onCreate(db);
    }

    public void insertItem(VaultItem item, CryptoManager crypto) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ID, item.getId());
        // تمام فیلدهای حساس با الگوریتم AES-256 رمزنگاری می‌شوند
        values.put(COLUMN_TITLE_ENC, crypto.encrypt(item.getTitle()));
        values.put(COLUMN_CATEGORY, item.getCategory());
        values.put(COLUMN_USERNAME_ENC, crypto.encrypt(item.getUsername()));
        values.put(COLUMN_PASSWORD_ENC, crypto.encrypt(item.getPassword()));
        values.put(COLUMN_NOTES_ENC, crypto.encrypt(item.getNotes()));
        values.put(COLUMN_IS_FAV, item.isFavorite() ? 1 : 0);
        db.insertWithOnConflict(TABLE_ITEMS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public List<VaultItem> getAllDecryptedItems(CryptoManager crypto) {
        List<VaultItem> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_ITEMS, null);

        if (cursor.moveToFirst()) {
            do {
                VaultItem item = new VaultItem();
                item.setId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                item.setTitle(crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE_ENC))));
                item.setCategory(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CATEGORY)));
                item.setUsername(crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USERNAME_ENC))));
                item.setPassword(crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD_ENC))));
                item.setNotes(crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTES_ENC))));
                item.setFavorite(cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_FAV)) == 1);
                list.add(item);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }
}
