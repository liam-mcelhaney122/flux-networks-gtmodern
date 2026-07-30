package sonar.fluxnetworks.common.device;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import sonar.fluxnetworks.FluxConfig;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.energy.IEnergySystem;
import sonar.fluxnetworks.common.connection.TransferHandler;

import javax.annotation.Nonnull;

public abstract class FluxStorageHandler extends TransferHandler {

    private long mAdded;
    private long mRemoved;

    protected FluxStorageHandler(long limit) {
        super(limit);
    }

    @Override
    public void onCycleStart(@Nonnull IEnergySystem es) {
    }

    @Override
    public void onCycleEnd(@Nonnull IEnergySystem es) {
        mChange = mAdded - mRemoved;
        mAdded = 0;
        mRemoved = 0;
    }

    @Override
    public void addToBuffer(long energy) {
        mBuffer += energy;
        mAdded += energy;
    }

    @Override
    public long removeFromBuffer(long energy) {
        long op = Math.min(Math.min(energy, mBuffer), getLimit() - mRemoved);
        assert op >= 0;
        mBuffer -= op;
        mRemoved += op;
        return op;
    }

    @Override
    public long getRequest() {
        return Math.max(0, Math.min(getMaxEnergyStorage() - mBuffer, getLimit() - mAdded));
    }

    /**
     * Make this storage full of energy (debug or admin function).
     */
    void fillUp() {
        long energy = Math.max(0, Math.min(getMaxEnergyStorage() - mBuffer, Long.MAX_VALUE - mAdded));
        if (energy > 0) {
            mBuffer += energy;
            mAdded += energy;
        }
    }

    /**
     * The maximum capacity of this storage, read from {@link FluxConfig} which is
     * denominated in FE. Network-internal values are denominated in the network's
     * native unit, so on an EU network the same raw number represents 4x the
     * physical energy (1 EU = 4 FE).
     * <p>
     * TODO: converting this to network-native units (IEnergySystem#fromFE) would keep
     *  physical capacity constant across network types, but the handler has no network
     *  reference and this value is also read network-independently on the client
     *  (renderers, GUI) and before a network is bound (setLimit during NBT load), so
     *  it stays FE-denominated for now.
     */
    public abstract long getMaxEnergyStorage();

    @Override
    public int getPriority() {
        return super.getPriority() - STORAGE_PRI_DIFF;
    }

    @Override
    public void setLimit(long limit) {
        super.setLimit(Math.min(limit, getMaxEnergyStorage()));
    }

    @Override
    public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        if (type == FluxConstants.NBT_PHANTOM_UPDATE) {
            tag.putLong(FluxConstants.BUFFER, mBuffer);
        } else {
            tag.putLong(FluxConstants.ENERGY, mBuffer);
        }
    }

    @Override
    public void writePacketBuffer(@Nonnull FriendlyByteBuf buffer, byte type) {
        if (type == FluxConstants.DEVICE_S2C_STORAGE_ENERGY) {
            buffer.writeLong(mBuffer);
        } else {
            super.writePacketBuffer(buffer, type);
        }
    }

    @Override
    public void readPacketBuffer(@Nonnull FriendlyByteBuf buffer, byte type) {
        if (type == FluxConstants.DEVICE_S2C_STORAGE_ENERGY) {
            mBuffer = buffer.readLong();
        } else {
            super.readPacketBuffer(buffer, type);
        }
    }

    public static class Basic extends FluxStorageHandler {

        public Basic() {
            super(FluxConfig.basicTransfer);
        }

        @Override
        public long getMaxEnergyStorage() {
            return FluxConfig.basicCapacity;
        }
    }

    public static class Herculean extends FluxStorageHandler {

        public Herculean() {
            super(FluxConfig.herculeanTransfer);
        }

        @Override
        public long getMaxEnergyStorage() {
            return FluxConfig.herculeanCapacity;
        }
    }

    public static class Gargantuan extends FluxStorageHandler {

        public Gargantuan() {
            super(FluxConfig.gargantuanTransfer);
        }

        @Override
        public long getMaxEnergyStorage() {
            return FluxConfig.gargantuanCapacity;
        }
    }
}
