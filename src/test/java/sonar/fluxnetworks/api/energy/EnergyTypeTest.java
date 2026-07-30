package sonar.fluxnetworks.api.energy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class EnergyTypeTest {

    @Test
    void feHasWireFormatIdZero() {
        assertEquals(0, EnergyType.FE.getId(),
                "FE must stay id 0 -- existing NBT and packet bytes already encode it as 0");
    }

    @Test
    void euHasWireFormatIdOne() {
        assertEquals(1, EnergyType.EU.getId(),
                "EU must stay id 1 -- existing NBT and packet bytes already encode it as 1");
    }

    @Test
    void fromIdRoundTripsForEveryDeclaredValue() {
        for (EnergyType type : EnergyType.VALUES) {
            assertSame(type, EnergyType.fromId(type.getId()),
                    "fromId(" + type.getId() + ") must round-trip back to " + type);
        }
    }

    @Test
    void fromIdBelowZeroFallsBackToFE() {
        assertSame(EnergyType.FE, EnergyType.fromId((byte) -1));
    }

    @Test
    void fromIdAboveDeclaredRangeFallsBackToFE() {
        assertSame(EnergyType.FE, EnergyType.fromId((byte) 7));
    }

    @Test
    void feShiftIsZero() {
        assertEquals(0, EnergyType.FE.getFEShift());
    }

    @Test
    void euShiftIsTwo() {
        assertEquals(2, EnergyType.EU.getFEShift());
    }

    @Test
    void valuesOrderIsFEThenEU() {
        assertEquals(2, EnergyType.VALUES.length);
        assertSame(EnergyType.FE, EnergyType.VALUES[0]);
        assertSame(EnergyType.EU, EnergyType.VALUES[1]);
    }
}
