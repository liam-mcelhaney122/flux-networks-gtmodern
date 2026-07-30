package sonar.fluxnetworks.api.energy;

public class EnergyMath {

    private EnergyMath() {
    }

    /**
     * Clamps the amperage so {@code amps * nativePerAmp} stays in the long range.
     *
     * @param amperage     the amperage offered
     * @param nativePerAmp the native units per amp
     * @return the amperage, clamped so it does not overflow when multiplied by {@code nativePerAmp}
     */
    public static long clampAmps(long amperage, long nativePerAmp) {
        return Math.min(amperage, Long.MAX_VALUE / nativePerAmp);
    }

    /**
     * Floors a clamped amperage down to the whole amps that the network accepted.
     *
     * @param amps            the clamped amperage
     * @param nativePerAmp    the native units per amp
     * @param simulatedAccept the native units that the network accepted in a simulated offer
     * @return the whole amps that the network accepted
     */
    public static long wholeAmps(long amps, long nativePerAmp, long simulatedAccept) {
        return Math.min(amps, simulatedAccept / nativePerAmp);
    }
}
