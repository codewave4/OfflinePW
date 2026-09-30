package com.offlinepw.vault;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * خالص و بدون وابستگی به Android — نرمال‌سازی رمزهای فارسی
 * برای باز شدن با کیبوردهای مختلف (عربی/فارسی) و حذف کاراکترهای نامرئی.
 */
public final class PasswordNormalizer {
    private PasswordNormalizer() {}

    public static String normalize(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); ) {
            int origCp = n.codePointAt(i);
            int charCount = Character.charCount(origCp);

            // حذف کاراکترهای نامرئی
            if (origCp == 0x200C || origCp == 0x200D || origCp == 0x200E || origCp == 0x200F
                    || origCp == 0x00AD || origCp == 0x0640) {
                i += charCount;
                continue;
            }

            int mappedCp = origCp;
            if (origCp == 0x064A || origCp == 0x0649) {
                mappedCp = 0x06CC; // ی فارسی
            } else if (origCp == 0x0643) {
                mappedCp = 0x06A9; // ک فارسی
            } else if (origCp >= 0x06F0 && origCp <= 0x06F9) {
                mappedCp = '0' + (origCp - 0x06F0);
            } else if (origCp >= 0x0660 && origCp <= 0x0669) {
                mappedCp = '0' + (origCp - 0x0660);
            }

            sb.appendCodePoint(mappedCp);
            i += charCount;
        }
        return sb.toString();
    }

    public static List<String> candidates(String raw) {
        if (raw == null) {
            List<String> list = new ArrayList<>(1);
            list.add(null);
            return list;
        }
        String norm = normalize(raw);
        if (norm.equals(raw)) {
            List<String> list = new ArrayList<>(1);
            list.add(raw);
            return list;
        } else {
            List<String> list = new ArrayList<>(2);
            list.add(raw);
            list.add(norm);
            return list;
        }
    }
}
