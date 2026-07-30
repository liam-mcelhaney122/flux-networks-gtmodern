package sonar.fluxnetworks.api.energy;

import javax.annotation.Nonnull;
import java.text.NumberFormat;

public enum EnergyType {
    FE("Forge Energy", "FE", "FE/t"),
    EU("Energy Unit", "EU", "EU/t");

    /**
     * Use this array instead of calling {@code values()}. Calling {@code values()}
     * creates a new array each time.
     */
    public static final EnergyType[] VALUES = values();

    private final String name;
    private final String storage;
    private final String usage;

    EnergyType(String name, String storage, String usage) {
        this.name = name;
        this.storage = storage;
        this.usage = usage;
    }

    @Nonnull
    public static EnergyType fromId(byte id) {
        if (id < 0 || id >= VALUES.length) {
            return FE;
        }
        return VALUES[id];
    }

    public byte getId() {
        return (byte) ordinal();
    }

    /**
     * Returns the left shift that converts one unit of this type to FE. FE shifts
     * by 0; EU shifts by 2.
     */
    public int getFEShift() {
        return this == EU ? 2 : 0;
    }

    public String getName() {
        return name;
    }

    public String getStorageSuffix() {
        return storage;
    }

    public String getUsageSuffix() {
        return usage;
    }

    @Nonnull
    public String getUsage(long in) {
        return NumberFormat.getInstance().format(in) + " " + usage;
    }

    @Nonnull
    public String getUsageCompact(long in) {
        return FormatUtils.compact(in, usage);
    }

    @Nonnull
    public String getStorage(long in) {
        return NumberFormat.getInstance().format(in) + " " + storage;
    }

    @Nonnull
    public String getStorageCompact(long in) {
        return FormatUtils.compact(in, storage);
    }
}
