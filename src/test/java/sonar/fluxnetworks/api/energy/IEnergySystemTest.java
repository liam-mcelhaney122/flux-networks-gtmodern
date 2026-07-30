package sonar.fluxnetworks.api.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IEnergySystemTest {

    /** 0, 1, 3, 4, 1000, and the disable-limit sentinel. */
    private static final long[] SAMPLE_VALUES = {0L, 1L, 3L, 4L, 1000L, Long.MAX_VALUE};

    @Test
    void ofReturnsTheFESingleton() {
        assertSame(FEEnergySystem.INSTANCE, IEnergySystem.of(EnergyType.FE));
    }

    @Test
    void ofReturnsTheEUSingleton() {
        assertSame(EUEnergySystem.INSTANCE, IEnergySystem.of(EnergyType.EU));
    }

    @Test
    void feSystemToConnectorIsIdentityForFEConnector() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        for (long value : SAMPLE_VALUES) {
            assertEquals(value, fe.toConnector(value, EnergyType.FE),
                    "toConnector must be identity when both the network and the connector are FE, value=" + value);
        }
    }

    @Test
    void feSystemFromConnectorIsIdentityForFEConnector() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        for (long value : SAMPLE_VALUES) {
            assertEquals(value, fe.fromConnector(value, EnergyType.FE),
                    "fromConnector must be identity when both the network and the connector are FE, value=" + value);
        }
    }

    @Test
    void feSystemFromConnectorCeilIsIdentityForFEConnector() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        for (long value : SAMPLE_VALUES) {
            assertEquals(value, fe.fromConnectorCeil(value, EnergyType.FE),
                    "fromConnectorCeil must be identity when both the network and the connector are FE, value=" + value);
        }
    }

    @Test
    void feSystemFromFEIsIdentity() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        for (long value : SAMPLE_VALUES) {
            assertEquals(value, fe.fromFE(value),
                    "fromFE must be identity on an FE-denominated network, value=" + value);
        }
    }

    @Test
    void feSystemToFEIsIdentity() {
        IEnergySystem fe = IEnergySystem.of(EnergyType.FE);
        for (long value : SAMPLE_VALUES) {
            assertEquals(value, fe.toFE(value),
                    "toFE must be identity on an FE-denominated network, value=" + value);
        }
    }

    @Test
    void euNetworkToConnectorExactlyShiftsToFE() {
        IEnergySystem eu = IEnergySystem.of(EnergyType.EU);
        long[] values = {0L, 1L, 3L, 4L, 1000L, Long.MAX_VALUE >> 2};
        for (long value : values) {
            assertEquals(value << 2, eu.toConnector(value, EnergyType.FE),
                    "1 EU must convert to exactly 4 FE, value=" + value);
        }
    }

    @Test
    void euNetworkToConnectorClampsInsteadOfOverflowingToNegative() {
        IEnergySystem eu = IEnergySystem.of(EnergyType.EU);
        long justOverThreshold = (Long.MAX_VALUE >> 2) + 1;
        assertEquals(Long.MAX_VALUE, eu.toConnector(justOverThreshold, EnergyType.FE),
                "values above Long.MAX_VALUE >> 2 must clamp to Long.MAX_VALUE instead of overflowing negative");
        assertEquals(Long.MAX_VALUE, eu.toConnector(Long.MAX_VALUE, EnergyType.FE),
                "the disable-limit sentinel must also clamp cleanly when widened to FE");
    }

    @Test
    void euNetworkFromFEFloors() {
        IEnergySystem eu = IEnergySystem.of(EnergyType.EU);
        long[] values = {0L, 1L, 2L, 3L, 4L, 5L, 1000L, Long.MAX_VALUE};
        for (long value : values) {
            assertEquals(value >> 2, eu.fromFE(value),
                    "fromFE must floor via >>2, value=" + value);
        }
    }

    @Test
    void euNetworkFromConnectorCeilRoundsUpToTheTrueCeiling() {
        IEnergySystem eu = IEnergySystem.of(EnergyType.EU);
        long[] values = {0L, 1L, 2L, 3L, 4L, 5L, 6L, 1000L, 1001L};
        for (long value : values) {
            long expectedCeil = value / 4 + (value % 4 == 0 ? 0 : 1);
            assertEquals(expectedCeil, eu.fromConnectorCeil(value, EnergyType.FE),
                    "fromConnectorCeil must be ceil(value / 4), value=" + value);
        }
    }

    @Test
    void euNetworkCeilAndFloorDifferByAtMostOne() {
        IEnergySystem eu = IEnergySystem.of(EnergyType.EU);
        for (long value = 0; value <= 20; value++) {
            long floor = eu.fromFE(value);
            long ceil = eu.fromConnectorCeil(value, EnergyType.FE);
            assertTrue(ceil - floor <= 1,
                    "ceil - floor must never exceed 1, value=" + value + " floor=" + floor + " ceil=" + ceil);
        }
    }

    @Test
    void offerThenDeductRoundTripIsExactOnEUNetwork() {
        IEnergySystem eu = IEnergySystem.of(EnergyType.EU);
        long[] values = {0L, 1L, 3L, 4L, 1000L, Long.MAX_VALUE >> 2};
        for (long amount : values) {
            long offeredFE = eu.toConnector(amount, EnergyType.FE);
            long deductedEU = eu.fromConnectorCeil(offeredFE, EnergyType.FE);
            assertEquals(amount, deductedEU,
                    "a full offer(EU->FE) then deduct(FE->EU ceil) cycle must neither create nor destroy energy, amount=" + amount);
        }
    }

    @Test
    void convertWithZeroShiftIsIdentityRegardlessOfCeilFlag() {
        assertEquals(1000L, IEnergySystem.convert(1000L, 0, false));
        assertEquals(1000L, IEnergySystem.convert(1000L, 0, true));
    }

    @Test
    void convertWithZeroShiftPassesTheDisableLimitSentinelThroughUnclamped() {
        assertEquals(Long.MAX_VALUE, IEnergySystem.convert(Long.MAX_VALUE, 0, false),
                "Long.MAX_VALUE is the 'no limit' sentinel and must survive a same-unit conversion untouched");
        assertEquals(Long.MAX_VALUE, IEnergySystem.convert(Long.MAX_VALUE, 0, true),
                "Long.MAX_VALUE is the 'no limit' sentinel and must survive a same-unit conversion untouched");
    }

    @Test
    void ceilOverflowEdgeReturnsTheTrueCeilingNotANegativeNumber() {
        IEnergySystem eu = IEnergySystem.of(EnergyType.EU);
        long result = eu.fromConnectorCeil(Long.MAX_VALUE, EnergyType.FE);
        long expected = Long.MAX_VALUE / 4 + (Long.MAX_VALUE % 4 == 0 ? 0 : 1);
        assertTrue(result > 0,
                "the ceiling of Long.MAX_VALUE / 4 must stay positive, not overflow to a negative number");
        assertEquals(expected, result,
                "fromConnectorCeil(Long.MAX_VALUE, FE) must return the true ceiling of Long.MAX_VALUE / 4");
    }
}
