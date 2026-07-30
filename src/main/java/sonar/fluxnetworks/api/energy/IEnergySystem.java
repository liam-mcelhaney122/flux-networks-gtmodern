package sonar.fluxnetworks.api.energy;

import javax.annotation.Nonnull;

/**
 * The energy system a network accounts in. All network-internal values (buffers,
 * limits, statistics) are denominated in the system's {@link EnergyType}; conversion
 * only happens at connector boundaries via the methods below. All conversions are
 * bit shifts (1 EU = 4 FE), so a system paired with a connector of the same type
 * reduces to identity everywhere.
 */
public interface IEnergySystem {

    @Nonnull
    static IEnergySystem of(@Nonnull EnergyType type) {
        return type == EnergyType.EU ? EUEnergySystem.INSTANCE : FEEnergySystem.INSTANCE;
    }

    @Nonnull
    EnergyType getEnergyType();

    /**
     * Convert a network-native amount to the connector's native unit, for offering
     * energy out. Overflow-clamped when converting to a smaller unit, floored when
     * converting to a bigger unit.
     */
    default long toConnector(long amount, @Nonnull EnergyType connectorType) {
        return convert(amount, getEnergyType().getFEShift() - connectorType.getFEShift(), false);
    }

    /**
     * Convert a connector-native amount to the network's native unit, flooring,
     * for crediting received energy.
     */
    default long fromConnector(long amount, @Nonnull EnergyType connectorType) {
        return convert(amount, connectorType.getFEShift() - getEnergyType().getFEShift(), false);
    }

    /**
     * Convert a connector-native amount to the network's native unit, ceiling,
     * for deducting delivered energy so conversion never creates energy.
     */
    default long fromConnectorCeil(long amount, @Nonnull EnergyType connectorType) {
        return convert(amount, connectorType.getFEShift() - getEnergyType().getFEShift(), true);
    }

    default long fromFE(long amount) {
        return convert(amount, -getEnergyType().getFEShift(), false);
    }

    default long toFE(long amount) {
        return convert(amount, getEnergyType().getFEShift(), false);
    }

    static long convert(long amount, int shift, boolean ceil) {
        if (shift > 0) {
            if (amount > (Long.MAX_VALUE >> shift)) {
                return Long.MAX_VALUE;
            }
            return amount << shift;
        }
        if (shift < 0) {
            if (ceil) {
                return (amount + (1L << -shift) - 1) >> -shift;
            }
            return amount >> -shift;
        }
        return amount;
    }
}
