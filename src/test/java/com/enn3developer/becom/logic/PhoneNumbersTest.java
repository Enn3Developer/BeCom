package com.enn3developer.becom.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** Every phone has a 6-digit number, unique in the world. */
class PhoneNumbersTest {

    @Test
    void numbersHaveSixDigitsAndNoLeadingZero() {
        Random random = new Random(7);
        for (int i = 0; i < 1000; i++) {
            int number = PhoneNumbers.assign(random, n -> false);
            assertEquals(
                6,
                PhoneNumbers.format(number)
                    .length());
            assertTrue(PhoneNumbers.isValid(number));
        }
    }

    @Test
    void assignedNumbersAreUnique() {
        Random random = new Random(3);
        Set<Integer> taken = new HashSet<>();
        for (int i = 0; i < 50_000; i++) assertTrue(taken.add(PhoneNumbers.assign(random, taken::contains)));
    }

    @Test
    void findsTheLastFreeNumberWhenAlmostAllAreTaken() {
        int free = 654_321;
        int number = PhoneNumbers.assign(new Random(1), n -> n != free);
        assertEquals(free, number);
    }

    @Test
    void throwsWhenEveryNumberIsTaken() {
        assertThrows(IllegalStateException.class, () -> PhoneNumbers.assign(new Random(1), n -> true));
    }

    @Test
    void parsesOnlyValidSixDigitNumbers() {
        assertEquals(123456, PhoneNumbers.parse("123456"));
        assertEquals(123456, PhoneNumbers.parse(" 123456 "));
        assertEquals(-1, PhoneNumbers.parse("012345"));
        assertEquals(-1, PhoneNumbers.parse("12345"));
        assertEquals(-1, PhoneNumbers.parse("1234567"));
        assertEquals(-1, PhoneNumbers.parse("12a456"));
        assertEquals(-1, PhoneNumbers.parse(null));
        assertEquals(-1, PhoneNumbers.parse("-12345"));
    }
}
