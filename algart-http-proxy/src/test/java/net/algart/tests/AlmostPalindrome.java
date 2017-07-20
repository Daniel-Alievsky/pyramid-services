package net.algart.tests;

/**
 * Created by Daniel on 20/06/2017.
 */
public class AlmostPalindrome {
    public static boolean isAlmostPalindrome(String s) {
        final StringBuilder sb = new StringBuilder();
        for (int k = 0, len = s.length(), n = len / 2; k < n; k++) {
            sb.setLength(0);
            sb.append(s);
            sb.setCharAt(len - 1 - k, s.charAt(k));
            if (isPalindrome(sb)) {
                return true;
            }
        }
        return s.length() <= 1;
    }

    private static boolean isPalindrome(StringBuilder s) {
        for (int k = 0, len = s.length(), n = len / 2; k < n; k++) {
            if (s.charAt(k) != s.charAt(len - 1 - k)) {
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        final String[] tests = {
            "",
            "1",
            "asdf",
            "abba",
            "aka",
            "1234554321",
            "abccbx",
            "123455x321",
            "1a34554321",
            "1a34543n1",
            "1a3454an1",
        };
        for (String s : tests) {
            System.out.printf("String \"%s\": palindrom? %s; almost palindrome? %s%n",
                s, isPalindrome(new StringBuilder(s)), isAlmostPalindrome(s));
        }
    }
}
