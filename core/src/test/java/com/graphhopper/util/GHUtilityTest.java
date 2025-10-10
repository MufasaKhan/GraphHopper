package com.graphhopper.util;

import com.github.javafaker.Faker;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GHUtilityTest {


    @Test
    void edgeKey_zeroId_forwardAndReverse() {
        int fwd = GHUtility.createEdgeKey(0, false);
        int rev = GHUtility.createEdgeKey(0, true);
        assertEquals(0, fwd, "edgeId=0 forward should be 0");
        assertEquals(1, rev, "edgeId=0 reverse should be 1");

        assertEquals(0, GHUtility.getEdgeFromEdgeKey(fwd));
        assertEquals(0, GHUtility.getEdgeFromEdgeKey(rev));
    }

    // 2) reverseEdgeKey is an involution and flips parity
    @Test
    void reverseEdgeKey_involutionAndParity() {
        for (int edgeId : new int[]{0, 1, 2, 42, 10_000}) {
            int fwd = GHUtility.createEdgeKey(edgeId, false); // even
            int rev = GHUtility.createEdgeKey(edgeId, true);  // odd
            assertEquals(0, (fwd & 1), "forward key must be even");
            assertEquals(1, (rev & 1), "reverse key must be odd");

            assertEquals(rev, GHUtility.reverseEdgeKey(fwd));
            assertEquals(fwd, GHUtility.reverseEdgeKey(rev));

            // reverse twice = original
            assertEquals(fwd, GHUtility.reverseEdgeKey(GHUtility.reverseEdgeKey(fwd)));
            assertEquals(rev, GHUtility.reverseEdgeKey(GHUtility.reverseEdgeKey(rev)));
        }
    }

    // 3) getEdgeFromEdgeKey is inverse of createEdgeKey (both directions)
    @Test
    void createAndExtract_areInverses() {
        int[] ids = {0, 1, 2, 7, 123456, (Integer.MAX_VALUE >>> 1) - 3};
        for (int id : ids) {
            int fwdKey = GHUtility.createEdgeKey(id, false);
            int revKey = GHUtility.createEdgeKey(id, true);
            assertEquals(id, GHUtility.getEdgeFromEdgeKey(fwdKey));
            assertEquals(id, GHUtility.getEdgeFromEdgeKey(revKey));
        }
    }

    // 4) High boundary id (avoid overflow on shift) round trips
    @Test
    void edgeKey_highBoundaryId_roundTrip() {

        int maxSafeId = Integer.MAX_VALUE >>> 1; // 0x3FFFFFFF
        int fwdKey = GHUtility.createEdgeKey(maxSafeId, false);
        int revKey = GHUtility.createEdgeKey(maxSafeId, true);
        assertEquals(maxSafeId, GHUtility.getEdgeFromEdgeKey(fwdKey));
        assertEquals(maxSafeId, GHUtility.getEdgeFromEdgeKey(revKey));
        assertEquals(revKey, GHUtility.reverseEdgeKey(fwdKey));
        assertEquals(fwdKey, GHUtility.reverseEdgeKey(revKey));
    }

    // 5) Faker-based fuzz
    @Test
    void fakerGeneratedIds_roundTrip() {
        Faker faker = new Faker(new Random(123));
        // Limit to avoid overflow
        for (int i = 0; i < 50; i++) {
            int raw = Integer.parseInt(faker.number().digits(9));
            int id = raw & (Integer.MAX_VALUE >>> 1);
            int k1 = GHUtility.createEdgeKey(id, false);
            int k2 = GHUtility.createEdgeKey(id, true);
            assertEquals(id, GHUtility.getEdgeFromEdgeKey(k1));
            assertEquals(id, GHUtility.getEdgeFromEdgeKey(k2));
            assertEquals(k2, GHUtility.reverseEdgeKey(k1));
            assertEquals(k1, GHUtility.reverseEdgeKey(k2));
        }
    }

    // 6) randomDoubleInRange always within [min, max] and touches endpoints
    @Test
    void randomDoubleInRange_withinBounds_andEdgeHits() {
        Random rnd = new Random(7);
        double min = -3.5, max = 4.25;
        boolean sawNearMin = false, sawNearMax = false;
        for (int i = 0; i < 10_000; i++) {
            double v = GHUtility.randomDoubleInRange(rnd, min, max);
            assertTrue(v >= min && v <= max, "value must be within bounds");
            // Within 1e-3 of ends at least once (probabilistic but with 10k should hit)
            if (Math.abs(v - min) < 1e-3) sawNearMin = true;
            if (Math.abs(v - max) < 1e-3) sawNearMax = true;
        }
        assertTrue(sawNearMin || sawNearMax, "expected to see near at least one endpoint over many samples");
    }

    // 7) Even/odd algebra: edgeKey arithmetic properties
    @RepeatedTest(5)
    void edgeKey_arithmeticProperties() {
        Random r = new Random(99);
        int id = r.nextInt(Integer.MAX_VALUE >>> 2);
        int fwd = GHUtility.createEdgeKey(id, false);
        int rev = GHUtility.createEdgeKey(id, true);

        // fwd is exactly 2*id, rev is 2*id+1
        assertEquals(id * 2, fwd);
        assertEquals(id * 2 + 1, rev);

        // Integer division by 2 recovers id for both
        assertEquals(id, GHUtility.getEdgeFromEdgeKey(fwd));
        assertEquals(id, GHUtility.getEdgeFromEdgeKey(rev));
    }

    // 8) runConcurrently executes all given tasks correctly
    @Test
    void runConcurrently_executesAllRunnables() {
        final int N = 200;
        AtomicInteger counter = new AtomicInteger(0);

        Stream<Runnable> runnables = IntStream.range(0, N)
                .mapToObj(i -> (Runnable) () -> counter.incrementAndGet());

        // use 4 threads for concurrency
        GHUtility.runConcurrently(runnables, 4);
        assertEquals(N, counter.get(), "all runnables should have been executed exactly once");
    }

}
