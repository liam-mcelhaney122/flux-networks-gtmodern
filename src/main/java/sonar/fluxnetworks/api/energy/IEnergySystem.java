package sonar.fluxnetworks.api.energy;

import javax.annotation.Nonnull;

/**
 * This is the energy system that a network uses for its accounting. The
 * network denominates all internal values (buffers, limits, statistics) in
 * the system's {@link EnergyType}. Conversion happens only at the connector
 * boundaries, through the methods below. All conversions use bit shifts
 * (1 EU = 4 FE). So, if the system and the connector share the same energy
 * type, the conversion is an identity operation.
 */
public interface IEnergySystem {

    @Nonnull
    static IEnergySystem of(@Nonnull EnergyType type) {
        return type == EnergyType.EU ? EUEnergySystem.INSTANCE : FEEnergySystem.INSTANCE;
    }

    @Nonnull
    EnergyType getEnergyType();

    /**
     * Converts a network-native amount to the connector's native unit, to offer
     * energy out. The method overflow-clamps the result when it converts to a
     * smaller unit. It floors the result when it converts to a bigger unit.
     */
    default long toConnector(long amount, @Nonnull EnergyType connectorType) {
        return convert(amount, getEnergyType().getFEShift() - connectorType.getFEShift(), false);
    }

    /**
     * Converts a connector-native amount to the network's native unit. The
     * method floors the result, to credit received energy.
     */
    default long fromConnector(long amount, @Nonnull EnergyType connectorType) {
        return convert(amount, connectorType.getFEShift() - getEnergyType().getFEShift(), false);
    }

    /**
     * Converts a connector-native amount to the network's native unit. The
     * method rounds the result up, to deduct delivered energy. This way, the
     * conversion never creates energy.
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
                return amount == 0 ? 0 : ((amount - 1) >> -shift) + 1;
            }
            return amount >> -shift;
        }
        return amount;
    }
}
