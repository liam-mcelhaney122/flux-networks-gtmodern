package sonar.fluxnetworks.api.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HatchBufferMathTest {

    // ---- request ----------------------------------------------------------

    @Test
    void requestClampsToTheCapacityHeadroomWhenTheLimitHasPlentyOfRoom() {
        // headroom is 10, the limit still has 1000 free, so headroom wins
        assertEquals(10L, HatchBufferMath.request(100L, 90L, 1000L, 0L));
    }

    @Test
    void requestClampsToTheRemainingLimitWhenCapacityHasPlentyOfRoom() {
        // headroom is 1000, but only 40 of the 50 limit is still free
        assertEquals(40L, HatchBufferMath.request(1000L, 0L, 50L, 10L));
    }

    @Test
    void requestReturnsZeroWhenTheBufferAlreadyExceedsCapacity() {
        assertEquals(0L, HatchBufferMath.request(100L, 150L, 1000L, 0L),
                "a buffer above capacity must not request a negative amount");
    }

    @Test
    void requestReturnsZeroWhenTheLimitIsZero() {
        assertEquals(0L, HatchBufferMath.request(100L, 0L, 0L, 0L));
    }

    @Test
    void requestReturnsZeroWhenTheCycleAlreadyAddedMoreThanTheLimit() {
        assertEquals(0L, HatchBufferMath.request(1000L, 0L, 100L, 200L),
                "a cycle that already exceeded its limit must not request more");
    }

    @Test
    void requestHandlesLargeValuesWithoutOverflowingOrGoingNegative() {
        long result = HatchBufferMath.request(Long.MAX_VALUE, 0L, Long.MAX_VALUE, 0L);
        assertEquals(Long.MAX_VALUE, result);
        assertTrue(result >= 0L);
    }

    @Test
    void requestNeverReturnsNegativeEvenWhenBothTermsAreNegative() {
        // buffer far above capacity AND the cycle far exceeded the limit
        assertEquals(0L, HatchBufferMath.request(0L, Long.MAX_VALUE, 0L, Long.MAX_VALUE));
    }

    // ---- drainEU / nativeOf: FE network -----------------------------------

    @Test
    void drainEUOnFENetworkFloorsAPartialEUWorthOfBuffer() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        // buffer holds 10 FE, which is 2 whole EU plus a 2 FE remainder
        long drained = HatchBufferMath.drainEU(3L, 10L, fe);
        assertEquals(2L, drained);
    }

    @Test
    void drainEUOnFENetworkIsExactWhenBufferIsAWholeEUMultipleAndCoversTheRequest() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        long requestEU = 4L;
        long buffer = 16L; // exactly 4 EU, covers the request exactly
        assertEquals(requestEU, HatchBufferMath.drainEU(requestEU, buffer, fe));
    }

    @Test
    void drainEUOnFENetworkNeverExceedsTheRequestedEU() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        long[] requests = {0L, 1L, 3L, 4L, 100L, 1000L};
        long[] buffers = {0L, 1L, 3L, 4L, 10L, 4096L, Long.MAX_VALUE};
        for (long requestEU : requests) {
            for (long buffer : buffers) {
                long drained = HatchBufferMath.drainEU(requestEU, buffer, fe);
                assertTrue(drained <= requestEU,
                        "drainEU must never exceed the requested EU, request=" + requestEU + " buffer=" + buffer);
                assertTrue(drained >= 0L,
                        "drainEU must never be negative, request=" + requestEU + " buffer=" + buffer);
            }
        }
    }

    @Test
    void drainEUOnFENetworkConservesEnergyAgainstTheBuffer() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        long[] requests = {0L, 1L, 3L, 4L, 100L, 1000L};
        long[] buffers = {0L, 1L, 3L, 4L, 10L, 4096L, 100000L};
        for (long requestEU : requests) {
            for (long buffer : buffers) {
                long drained = HatchBufferMath.drainEU(requestEU, buffer, fe);
                long nativeDrained = HatchBufferMath.nativeOf(drained, fe);
                assertTrue(nativeDrained <= buffer,
                        "the native amount removed for the drained EU must never exceed the buffer, request="
                                + requestEU + " buffer=" + buffer);
            }
        }
    }

    @Test
    void drainEUOnFENetworkLeavesASubFourFERemainderInTheBufferForALaterCycle() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        long requestEU = 100L;
        long buffer = 10L; // 2 whole EU (8 FE) plus a 2 FE remainder
        long drained = HatchBufferMath.drainEU(requestEU, buffer, fe);
        long nativeDrained = HatchBufferMath.nativeOf(drained, fe);
        long remainder = buffer - nativeDrained;
        assertTrue(remainder < 4L, "the leftover buffer must be smaller than one whole EU, remainder=" + remainder);

        // draining again against the leftover remainder yields nothing more, until the buffer refills
        long secondDrain = HatchBufferMath.drainEU(requestEU, remainder, fe);
        assertEquals(0L, secondDrain,
                "a remainder below 4 FE cannot yield another whole EU on its own");
    }

    @Test
    void drainEUOnFENetworkHandlesLongMaxValueRequestWithoutGoingNegative() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);

        long drained = HatchBufferMath.drainEU(Long.MAX_VALUE, 1000L, fe);
        assertTrue(drained >= 0L);
        assertEquals(250L, drained, "1000 FE floors to 250 whole EU regardless of the saturated request");

        long drainedFromMaxBuffer = HatchBufferMath.drainEU(Long.MAX_VALUE, Long.MAX_VALUE, fe);
        assertTrue(drainedFromMaxBuffer >= 0L, "draining from a saturated buffer must not go negative");
        assertEquals(Long.MAX_VALUE >> 2, drainedFromMaxBuffer);
    }

    // ---- drainEU: EU network (identity) ------------------------------------

    @Test
    void drainEUOnEUNetworkIsTheMinimumOfRequestAndBuffer() {
        IEnergySystem eu = IEnergySystem.of(EnergyType.EU);
        long[][] cases = {
                {0L, 0L}, {5L, 10L}, {10L, 5L}, {7L, 7L}, {0L, 100L}, {100L, 0L},
                {Long.MAX_VALUE, 42L}, {42L, Long.MAX_VALUE}, {Long.MAX_VALUE, Long.MAX_VALUE},
        };
        for (long[] c : cases) {
            long requestEU = c[0];
            long buffer = c[1];
            assertEquals(Math.min(requestEU, buffer), HatchBufferMath.drainEU(requestEU, buffer, eu),
                    "on an EU network drainEU must be identity-converted, so it is just min(request, buffer)");
        }
    }

    // ---- nativeOf -----------------------------------------------------------

    @Test
    void nativeOfIsExactOnFENetwork() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        assertEquals(0L, HatchBufferMath.nativeOf(0L, fe));
        assertEquals(4L, HatchBufferMath.nativeOf(1L, fe));
        assertEquals(400L, HatchBufferMath.nativeOf(100L, fe));
    }

    @Test
    void nativeOfIsIdentityOnEUNetwork() {
        IEnergySystem eu = IEnergySystem.of(EnergyType.EU);
        assertEquals(0L, HatchBufferMath.nativeOf(0L, eu));
        assertEquals(100L, HatchBufferMath.nativeOf(100L, eu));
        assertEquals(Long.MAX_VALUE, HatchBufferMath.nativeOf(Long.MAX_VALUE, eu));
    }

    @Test
    void nativeOfClampsInsteadOfOverflowingOnFENetwork() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        assertEquals(Long.MAX_VALUE, HatchBufferMath.nativeOf(Long.MAX_VALUE, fe));
    }

    // ---- insertableEU ---------------------------------------------------------

    @Test
    void insertableEUReturnsTheFreeSpace() {
        assertEquals(70L, HatchBufferMath.insertableEU(100L, 30L));
    }

    @Test
    void insertableEUReturnsZeroWhenFull() {
        assertEquals(0L, HatchBufferMath.insertableEU(100L, 100L));
    }

    @Test
    void insertableEUReturnsZeroWhenStoredExceedsCapacity() {
        assertEquals(0L, HatchBufferMath.insertableEU(100L, 150L),
                "an over-full hatch must report zero insertable space, not a negative one");
    }

    @Test
    void insertableEUHandlesZeroCapacity() {
        assertEquals(0L, HatchBufferMath.insertableEU(0L, 0L));
    }
}
