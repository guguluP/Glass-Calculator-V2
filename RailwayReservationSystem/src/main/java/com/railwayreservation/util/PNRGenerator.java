package com.railwayreservation.util;

import java.util.Random;

public class PNRGenerator {
    private static final Random RANDOM = new Random();

    public static String generate() {
        // Generate 10-digit PNR similar to Indian Railways (e.g. 1234567890)
        long num = 1000000000L + (long) (RANDOM.nextDouble() * 9000000000L);
        return String.valueOf(num);
    }
}
