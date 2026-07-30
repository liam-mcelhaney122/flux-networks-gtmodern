package sonar.fluxnetworks.api.energy;

import javax.annotation.Nonnull;

/**
 * This class holds the math for flux hatches. A flux hatch is an EU-denominated
 * buffer. A device on a flux network owns the hatch. The device's internal
 * buffer stays in the network's own energy type (FE or EU). EU is always the
 * coarser unit: 1 EU equals 4 FE. So, a conversion from EU to the network's
 * native unit is exact. A conversion from the native unit to EU floors, because
 * a partial EU cannot leave the buffer.
 */
public final class HatchBufferMath {

    private HatchBufferMath() {
    }

    /**
     * Computes how much native-unit energy an input hatch requests from the
     * network this cycle. The request cannot exceed the hatch's remaining
     * capacity, and it cannot exceed the transfer limit that is still free
     * this cycle. The method never returns a negative amount, even when the
     * buffer already holds more than the capacity, or when the cycle already
     * added more than the limit allows.
     *
     * @param capacityNative the hatch's buffer capacity, in the network's native unit
     * @param buffer         the hatch's current buffer level, in the network's native unit
     * @param limit          the transfer limit for this cycle, in the network's native unit
     * @param addedThisCycle the amount already added to the buffer this cycle, in the network's native unit
     * @return the native-unit amount to request this cycle, never negative
     */
    public static long request(long capacityNative, long buffer, long limit, long addedThisCycle) {
        long headroom = capacityNative - buffer;
        long remainingLimit = limit - addedThisCycle;
        return Math.max(0L, Math.min(headroom, remainingLimit));
    }

    /**
     * Computes the whole-EU amount that a flux hatch can drain from its buffer
     * this cycle. The method converts the requested EU amount to the network's
     * native unit, an exact conversion. It clamps the request to the buffer's
     * current level. Then it floors the result back down to whole EU, because
     * a hatch can only ever drain whole EU. On an FE network, a buffer
     * remainder smaller than 4 FE stays in the buffer; the hatch drains it on
     * a later cycle.
     * <p>
     * The caller must then remove {@link #nativeOf(long, IEnergySystem)} of
     * the returned amount from the buffer. This way, the drain never destroys
     * or creates energy: it only ever removes whole multiples of the
     * native-per-EU ratio.
     *
     * @param requestEU the EU amount requested, in EU
     * @param buffer    the hatch's current buffer level, in the network's native unit
     * @param es        the network's energy system
     * @return the whole-EU amount the hatch can drain this cycle, never negative
     */
    public static long drainEU(long requestEU, long buffer, @Nonnull IEnergySystem es) {
        long nativeReq = es.fromConnector(requestEU, EnergyType.EU);
        long drainableNative = Math.min(nativeReq, buffer);
        return es.toConnector(drainableNative, EnergyType.EU);
    }

    /**
     * Converts a whole-EU amount to the network's native unit. The conversion
     * is exact, because EU is always the coarser unit. Large values clamp
     * instead of overflowing, the same as any other connector conversion.
     *
     * @param eu the EU amount, in EU
     * @param es the network's energy system
     * @return the equivalent amount in the network's native unit
     */
    public static long nativeOf(long eu, @Nonnull IEnergySystem es) {
        return es.fromConnector(eu, EnergyType.EU);
    }

    /**
     * Computes how much EU a flux hatch can still accept.
     *
     * @param capacityEU the hatch's buffer capacity, in EU
     * @param storedEU   the EU amount already stored, in EU
     * @return the EU amount the hatch can still accept, never negative
     */
    public static long insertableEU(long capacityEU, long storedEU) {
        return Math.max(0L, capacityEU - storedEU);
    }
}
