package sonar.fluxnetworks.api.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnergyMathTest {

    @Test
    void clampAmpsPassesThroughWhenWellBelowTheOverflowThreshold() {
        assertEquals(10L, EnergyMath.clampAmps(10L, 4L));
    }

    @Test
    void clampAmpsZeroAmperageStaysZero() {
        assertEquals(0L, EnergyMath.clampAmps(0L, 4L));
    }

    @Test
    void clampAmpsOneNativePerAmpNeverClampsBelowLongMaxValue() {
        assertEquals(1L, EnergyMath.clampAmps(1L, 1L));
        assertEquals(Long.MAX_VALUE, EnergyMath.clampAmps(Long.MAX_VALUE, 1L));
    }

    @Test
    void clampAmpsClampsDownWhenAmperageWouldOverflowTheMultiplication() {
        long nativePerAmp = 4L;
        long clamped = EnergyMath.clampAmps(Long.MAX_VALUE, nativePerAmp);
        assertEquals(Long.MAX_VALUE / nativePerAmp, clamped,
                "clampAmps must cap the amperage so amps * nativePerAmp can never overflow a long");
        assertTrue(clamped * nativePerAmp > 0,
                "the clamped amperage must not overflow when multiplied back out by nativePerAmp");
    }

    @Test
    void wholeAmpsAcceptsAllOfferedAmpsWhenTheNetworkHadRoom() {
        assertEquals(5L, EnergyMath.wholeAmps(5L, 4L, 20L));
    }

    @Test
    void wholeAmpsZeroSimulatedAcceptYieldsZeroWholeAmps() {
        assertEquals(0L, EnergyMath.wholeAmps(5L, 4L, 0L));
    }

    @Test
    void wholeAmpsZeroOfferedAmpsYieldsZeroRegardlessOfRoom() {
        assertEquals(0L, EnergyMath.wholeAmps(0L, 4L, 20L));
    }

    @Test
    void wholeAmpsFloorsAPartialAmpInsteadOfRoundingUp() {
        // the network only had room for 4.5 amps worth of native units; the partial amp must be refused
        assertEquals(4L, EnergyMath.wholeAmps(5L, 4L, 18L),
                "a partial amp of headroom must not be rounded up into a whole accepted amp");
    }

    @Test
    void wholeAmpsNeverExceedsTheOfferedAmperageEvenWithAbundantRoom() {
        assertEquals(5L, EnergyMath.wholeAmps(5L, 4L, Long.MAX_VALUE));
    }
}
