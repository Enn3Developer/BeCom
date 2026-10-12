package com.enn3developer.becom.logic;

import java.util.Random;
import java.util.function.IntPredicate;

/**
 * Phone numbers (DESIGN.md 8.2, D25): {@link #DIGITS} digits, unique in the world, with no leading zero so every
 * number reads at full length. The length is one of the three fixed tunables (DESIGN.md 14.2).
 */
public final class PhoneNumbers {

    public static final int DIGITS = 6;
    public static final int FIRST = (int) Math.pow(10, DIGITS - 1);
    public static final int LAST = (int) Math.pow(10, DIGITS) - 1;

    // Random draws before falling back to a scan: with the space nearly empty, the first draw almost always wins.
    private static final int RANDOM_TRIES = 64;

    private PhoneNumbers() {}

    public static boolean isValid(int number) {
        return number >= FIRST && number <= LAST;
    }

    /**
     * A number {@code taken} rejects, picked at random so numbers don't give away how many phones exist.
     *
     * @throws IllegalStateException when every number is taken
     */
    public static int assign(Random random, IntPredicate taken) {
        int span = LAST - FIRST + 1;
        for (int i = 0; i < RANDOM_TRIES; i++) {
            int candidate = FIRST + random.nextInt(span);
            if (!taken.test(candidate)) return candidate;
        }
        int start = random.nextInt(span);
        for (int i = 0; i < span; i++) {
            int candidate = FIRST + (start + i) % span;
            if (!taken.test(candidate)) return candidate;
        }
        throw new IllegalStateException("every " + DIGITS + "-digit phone number is taken");
    }

    /** The number as typed in the dialer, or -1 when it isn't a valid number. */
    public static int parse(String typed) {
        if (typed == null) return -1;
        String digits = typed.trim();
        if (digits.length() != DIGITS) return -1;
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) return -1;
        }
        int number = Integer.parseInt(digits);
        return isValid(number) ? number : -1;
    }

    public static String format(int number) {
        return Integer.toString(number);
    }
}
