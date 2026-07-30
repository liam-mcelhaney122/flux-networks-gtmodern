package sonar.fluxnetworks.api.energy;

public class FormatUtils {

    private static final double[] COMPACT_SCALE = new double[]{0.001D, 0.000_001D, 0.000_000_001D, 0.000_000_000_001D,
            0.000_000_000_000_001D, 0.000_000_000_000_000_001D};

    private FormatUtils() {
    }

    /**
     * Formats the value in compact form, for example 3.5M.
     *
     * @param in the value to format
     * @return the compact string
     */
    public static String compact(long in) {
        if (in < 1000) {
            return Long.toString(in);
        }
        int level = (int) (Math.log10(in) / 3) - 1;
        char pre = "kMGTPE".charAt(level);
        return String.format("%.1f%c", in * COMPACT_SCALE[level], pre);
    }

    public static String compact(long in, String suffix) {
        if (in < 1000) {
            return in + " " + suffix;
        }
        int level = (int) (Math.log10(in) / 3) - 1;
        char pre = "kMGTPE".charAt(level);
        return String.format("%.1f %c%s", in * COMPACT_SCALE[level], pre, suffix);
    }
}
