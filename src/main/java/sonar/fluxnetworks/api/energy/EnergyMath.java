package sonar.fluxnetworks.api.energy;

public class EnergyMath {

    private EnergyMath() {
    }

    /**
     * Overflow clamp so {@code amps * nativePerAmp} stays in long range.
     *
     * @param amperage     the amperage offered
     * @param nativePerAmp native units per amp
     * @return the amperage, clamped so it never overflows when multiplied by {@code nativePerAmp}
     */
    public static long clampAmps(long amperage, long nativePerAmp) {
        return Math.min(amperage, Long.MAX_VALUE / nativePerAmp);
    }

    /**
     * Floors a clamped amperage down to the whole amps the network actually accepted.
     *
     * @param amps            the clamped amperage
     * @param nativePerAmp    native units per amp
     * @param simulatedAccept the native units the network accepted in a simulated offer
     * @return the whole amps accepted
     */
    public static long wholeAmps(long amps, long nativePerAmp, long simulatedAccept) {
        return Math.min(amps, simulatedAccept / nativePerAmp);
    }
}
