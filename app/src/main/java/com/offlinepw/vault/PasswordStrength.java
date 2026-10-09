package com.offlinepw.vault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Pure Java password strength estimator.
 * No android.* imports.
 */
public final class PasswordStrength {

    private PasswordStrength() {}

    // ~150 common passwords, including requested examples
    private static final List<String> DICT = Collections.unmodifiableList(Arrays.asList(
            "password","123456","123456789","qwerty","abc123","password1","12345678","111111","123123",
            "admin","letmein","welcome","monkey","dragon","football","master","login","iloveyou",
            "trustno1","sunshine","princess","shadow","superman","qwerty123","michael","jordan","harley",
            "1234567","1234567890","1234","000000","12345","654321","qwertyuiop","1q2w3e4r","1qaz2wsx",
            "password123","admin123","welcome123","iloveyou123","abc123456","qwerty12345","123qwe",
            "password1234","letmein123","monkey123","dragon123","master123","login123","football123",
            "shadow123","superman123","batman","mustang","access","flower","123456a","passw0rd","p@ssword",
            "pass","pwd","1234567890a","salam","khoda","iran","mohammad","ali","hossein","pooya",
            "reza","ahmad","hassan","mehdi","fatemeh","zahra","narges","sara","maryam","amir","omid",
            "arash","farhad","babak","kamran","dariush","kourosh","cyrus","persia","tehran","esfahan",
            "shiraz","mashhad","tabriz","iran123","salam123","khoda123","mohammad123","ali123","hossein123",
            "reza123","ahmad123","hassan123","mehdi123","fatemeh123","zahra123","sara123","amir123",
            "omid123","arash123","farhad123","password123456","adminadmin","welcome1","iloveyou1","qwerty1",
            "abc123123","123456123","letmein1","monkey1","dragon1","master1","login1","football1","shadow1",
            "superman1","batman123","mustang123","access123","flower123","12345678910","qwertyui","asdfghjkl",
            "zxcvbnm","1qazxsw2","zaq12wsx","password12","admin12","welcome12","iloveyou12","123abc","abc1234",
            "qwerty12","letmein12","monkey12","dragon12","master12","login12","football12","salam12","khoda12",
            "iran12","mohammad12","ali12","hossein12","pooya12","pass123","pwd123","123456qwerty","qwerty123456",
            "password123456789","admin123456","welcome123456","iloveyou123456","abc123456789","letmein123456",
            "monkey123456","dragon123456","master123456","login123456"
    ));

    private static final List<String> KEYBOARD_ROWS = Collections.unmodifiableList(Arrays.asList(
            "qwertyuiop",
            "asdfghjkl",
            "zxcvbnm",
            "1234567890",
            "1qaz2wsx",
            "2wsx3edc",
            "3edc4rfv",
            "4rfv5tgb",
            "5tgb6yhn",
            "6yhn7ujm",
            "7ujm8ik",
            "8ik9ol",
            "9ol0p",
            "ضصثقفغعهخحجچ",
            "شسیبلاتنمکگ",
            "ظطزرذدپو"
    ));

    public static boolean isWeak(String pw) {
        if (pw == null || pw.isEmpty()) return true;
        String norm = PasswordNormalizer.normalize(pw);
        if (norm == null) return true;
        String trunc = norm.length() > 128 ? norm.substring(0, 128) : norm;
        if (trunc.length() < 10) return true;
        return estimateBits(pw) < 50.0;
    }

    public static double estimateBits(String pw) {
        if (pw == null || pw.isEmpty()) return 0.0;
        String norm = PasswordNormalizer.normalize(pw);
        if (norm == null) norm = pw;
        String s = norm.length() > 128 ? norm.substring(0, 128) : norm;
        int n = s.length();
        if (n == 0) return 0.0;

        String lower = s.toLowerCase(Locale.ROOT);
        String leet = leetTransform(lower);

        List<int[]> intervals = new ArrayList<>();

        // Mobile Iran ^09\d{9}$
        if (n == 11 && s.matches("^09\\d{9}$")) {
            intervals.add(new int[]{0, n});
        } else {
            // also check lower for mobile after normalization (digits already ascii)
            if (n == 11 && lower.matches("^09\\d{9}$")) {
                intervals.add(new int[]{0, n});
            }
        }

        // Dictionary after stripping trailing digits/symbols
        String stripped = stripTrailingNonLetters(leet);
        if (!stripped.isEmpty()) {
            for (String d : DICT) {
                String dl = d.toLowerCase(Locale.ROOT);
                if (dl.length() < 5) {
                    if (stripped.equals(dl)) {
                        intervals.add(new int[]{0, stripped.length()});
                    }
                } else {
                    if (stripped.equals(dl)) {
                        intervals.add(new int[]{0, stripped.length()});
                    } else if (stripped.contains(dl)) {
                        int from = 0;
                        while (true) {
                            int idx = stripped.indexOf(dl, from);
                            if (idx < 0) break;
                            intervals.add(new int[]{idx, idx + dl.length()});
                            from = idx + 1;
                            if (from >= stripped.length()) break;
                        }
                    }
                }
            }
        }

        // Sequential ascending/descending >=4 on letters and digits
        // letters a-z and digits 0-9
        int i = 0;
        while (i <= n - 4) {
            char c0 = lower.charAt(i);
            char c1 = lower.charAt(i + 1);
            char c2 = lower.charAt(i + 2);
            char c3 = lower.charAt(i + 3);
            if (isSeqChar(c0) && isSeqChar(c1) && isSeqChar(c2) && isSeqChar(c3)) {
                int d1 = c1 - c0;
                int d2 = c2 - c1;
                int d3 = c3 - c2;
                if ((d1 == 1 && d2 == 1 && d3 == 1) || (d1 == -1 && d2 == -1 && d3 == -1)) {
                    // ensure same class: all letters or all digits
                    if (sameSeqClass(c0, c1, c2, c3)) {
                        int dir = d1;
                        int j = i + 4;
                        while (j < n) {
                            char prev = lower.charAt(j - 1);
                            char cur = lower.charAt(j);
                            if (!isSeqChar(cur)) break;
                            if (!sameSeqClass(prev, cur)) break;
                            if (cur - prev != dir) break;
                            j++;
                        }
                        intervals.add(new int[]{i, j});
                        i = j; // skip
                        continue;
                    }
                }
            }
            i++;
        }

        // Keyboard rows substrings >=4
        for (String row : KEYBOARD_ROWS) {
            String rl = row.toLowerCase(Locale.ROOT);
            String rev = new StringBuilder(rl).reverse().toString();
            for (String pattern : new String[]{rl, rev}) {
                int plen = pattern.length();
                if (plen < 4) continue;
                // generate substrings >=4
                for (int start = 0; start <= plen - 4; start++) {
                    for (int end = start + 4; end <= plen; end++) {
                        String sub = pattern.substring(start, end);
                        if (sub.length() < 4) continue;
                        // find all occurrences in lower
                        int from = 0;
                        while (true) {
                            int idx = lower.indexOf(sub, from);
                            if (idx < 0) break;
                            intervals.add(new int[]{idx, idx + sub.length()});
                            from = idx + 1;
                            if (from >= n) break;
                        }
                    }
                }
            }
        }

        // Repeat same char >=4
        int r = 0;
        while (r < n) {
            int k = r + 1;
            while (k < n && lower.charAt(k) == lower.charAt(r)) k++;
            int run = k - r;
            if (run >= 4) {
                intervals.add(new int[]{r, k});
            }
            r = k;
        }

        // Repeat short substring period 1..4 covering whole or large part
        for (int p = 1; p <= 4; p++) {
            if (n < p * 2) continue; // need at least 2 repeats
            // check full repeat
            boolean full = true;
            for (int idx = p; idx < n; idx++) {
                if (lower.charAt(idx) != lower.charAt(idx % p)) {
                    full = false;
                    break;
                }
            }
            if (full) {
                intervals.add(new int[]{0, n});
                continue;
            }
            // large part: longest prefix that follows period
            int match = p;
            while (match < n && lower.charAt(match) == lower.charAt(match % p)) {
                match++;
            }
            if (match >= n * 0.8 && match >= 8) {
                intervals.add(new int[]{0, match});
            }
        }

        // Year patterns 13xx,14xx,19xx,20xx
        for (int idx = 0; idx <= n - 4; idx++) {
            char a = s.charAt(idx);
            char b = s.charAt(idx + 1);
            char c = s.charAt(idx + 2);
            char d2 = s.charAt(idx + 3);
            if (Character.isDigit(a) && Character.isDigit(b) && Character.isDigit(c) && Character.isDigit(d2)) {
                String two = "" + a + b;
                if (two.equals("13") || two.equals("14") || two.equals("19") || two.equals("20")) {
                    intervals.add(new int[]{idx, idx + 4});
                }
            }
        }

        // Merge intervals
        List<int[]> merged = mergeIntervals(intervals);

        int covered = 0;
        for (int[] iv : merged) {
            covered += (iv[1] - iv[0]);
        }
        if (covered > n) covered = n;
        int remaining = n - covered;

        int charset = charsetSize(s);
        double log2 = Math.log(charset) / Math.log(2.0);
        if (log2 < 1) log2 = 1;

        double bits = merged.size() * 10.0 + remaining * log2;
        // If no guessable parts, bits = n * log2
        if (merged.isEmpty()) {
            bits = n * log2;
        }
        return bits;
    }

    private static String leetTransform(String lower) {
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case '@': sb.append('a'); break;
                case '0': sb.append('o'); break;
                case '1': sb.append('l'); break; // maps to l, also covers i partially
                case '3': sb.append('e'); break;
                case '$': sb.append('s'); break;
                case '5': sb.append('s'); break;
                case '7': sb.append('t'); break;
                case '!': sb.append('i'); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
    }

    private static String stripTrailingNonLetters(String s) {
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (Character.isLetter(c)) break;
            end--;
        }
        return s.substring(0, end);
    }

    private static boolean isSeqChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
    }

    private static boolean sameSeqClass(char... chars) {
        boolean allDigits = true;
        boolean allLetters = true;
        for (char c : chars) {
            if (c >= '0' && c <= '9') {
                allLetters = false;
            } else if (c >= 'a' && c <= 'z') {
                allDigits = false;
            } else {
                return false;
            }
        }
        return allDigits || allLetters;
    }

    private static boolean sameSeqClass(char a, char b) {
        boolean aDigit = (a >= '0' && a <= '9');
        boolean bDigit = (b >= '0' && b <= '9');
        boolean aLetter = (a >= 'a' && a <= 'z');
        boolean bLetter = (b >= 'a' && b <= 'z');
        return (aDigit && bDigit) || (aLetter && bLetter);
    }

    private static List<int[]> mergeIntervals(List<int[]> intervals) {
        if (intervals.isEmpty()) return Collections.emptyList();
        // sort by start
        intervals.sort(Comparator.comparingInt(a -> a[0]));
        List<int[]> merged = new ArrayList<>();
        int[] cur = intervals.get(0).clone();
        for (int i = 1; i < intervals.size(); i++) {
            int[] nxt = intervals.get(i);
            if (nxt[0] <= cur[1]) { // overlap or adjacent
                cur[1] = Math.max(cur[1], nxt[1]);
            } else {
                merged.add(cur);
                cur = nxt.clone();
            }
        }
        merged.add(cur);
        return merged;
    }

    private static int charsetSize(String s) {
        boolean hasLower = false;
        boolean hasUpper = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        boolean hasPersian = false;
        boolean hasOther = false;

        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int charCount = Character.charCount(cp);

            if (cp >= 'a' && cp <= 'z') {
                hasLower = true;
            } else if (cp >= 'A' && cp <= 'Z') {
                hasUpper = true;
            } else if (cp >= '0' && cp <= '9') {
                hasDigit = true;
            } else if (cp < 128) {
                // ascii symbol (including space)
                if (!Character.isLetterOrDigit(cp)) {
                    hasSymbol = true;
                } else {
                    hasOther = true;
                }
            } else {
                // Persian / Arabic ranges
                if ((cp >= 0x0600 && cp <= 0x06FF) ||
                    (cp >= 0x0750 && cp <= 0x077F) ||
                    (cp >= 0x08A0 && cp <= 0x08FF) ||
                    (cp >= 0xFB50 && cp <= 0xFDFF) ||
                    (cp >= 0xFE70 && cp <= 0xFEFF)) {
                    hasPersian = true;
                } else {
                    hasOther = true;
                }
            }
            i += charCount;
        }

        int size = 0;
        if (hasLower) size += 26;
        if (hasUpper) size += 26;
        if (hasDigit) size += 10;
        if (hasSymbol) size += 33;
        if (hasPersian) size += 32;
        if (hasOther) size += 100;
        if (size == 0) size = 1;
        return size;
    }
}
