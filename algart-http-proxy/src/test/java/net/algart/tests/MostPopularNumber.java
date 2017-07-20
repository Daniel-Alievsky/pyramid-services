package net.algart.tests;

import java.util.Arrays;

/**
 * Created by Daniel on 20/06/2017.
 */
public class MostPopularNumber {
    private static final int MIN_VALUE = 1;
    private static final int MAX_VALUE = 5000;

    public static int mostPopular(int[] array) {
        if (array.length == 0) {
            throw new IllegalArgumentException("Empty array");
        }
        final int[] histogram = histogram(array);
        int maxIndex = 0;
        for (int i = 0; i < histogram.length; i++) {
            if (histogram[i] > histogram[maxIndex]) {
                maxIndex = i;
            }
        }
        return MIN_VALUE + maxIndex;
    }

    private static int[] histogram(int[] array) {
        final int[] result = new int[MAX_VALUE - MIN_VALUE + 1];
        for (int a : array) {
            if (a < MIN_VALUE || a > MAX_VALUE) {
                throw new IllegalArgumentException("Illegal number " + a);
            }
            result[a - MIN_VALUE]++;
        }
        return result;
    }

    public static void main(String[] args) {
        final int[][] tests = {
            {34, 31, 34, 77, 85},
            {22, 101, 102, 101, 102, 585, 88},
            {66},
            {14, 14, 2342, 2342, 2342},
            {1, 2, 2, 2, 2, 3, 3, 3, 3},
        };
        for (int[] test : tests) {
            System.out.printf("Array: %s, most frequently: %d%n", Arrays.toString(test), mostPopular(test));
        }
    }
}
