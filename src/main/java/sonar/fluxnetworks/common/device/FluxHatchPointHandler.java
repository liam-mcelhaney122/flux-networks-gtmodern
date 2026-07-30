package sonar.fluxnetworks.common.device;

import net.minecraft.nbt.CompoundTag;
import sonar.fluxnetworks.FluxConfig;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.api.energy.HatchBufferMath;
import sonar.fluxnetworks.api.energy.IEnergySystem;
import sonar.fluxnetworks.common.connection.TransferHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This is the transfer handler for a flux energy input hatch. The hatch is a
 * logical point: it consumes network energy. The network fills the internal
 * buffer, in the network's native energy type. The GT energy container drains
 * the buffer in whole EU only, through {@link #drainEU}. See
 * {@link HatchBufferMath} for the unit math.
 */
public class FluxHatchPointHandler extends TransferHandler {

    /**
     * The buffer capacity, in EU. The GT side sets this to V[tier] * 16 * 2.
     */
    private final long mCapacityEU;

    // internal added energy happen inside the transfer cycle
    private long mAdded;

    // external drained energy happen outside the transfer cycle
    private long mDrained;

    /**
     * The GT container registers a listener here. It fires when the buffer
     * gains energy, so an idle GT multiblock re-subscribes its recipe logic.
     */
    @Nullable
    private Runnable mOnRefill;

    public FluxHatchPointHandler(long capacityEU) {
        super(FluxConfig.defaultLimit);
        mCapacityEU = capacityEU;
    }

    public void setOnRefill(@Nullable Runnable listener) {
        mOnRefill = listener;
    }

    @Override
    public void onCycleStart(@Nonnull IEnergySystem es) {
    }

    @Override
    public void onCycleEnd(@Nonnull IEnergySystem es) {
        // negative change = network output, matches statistics conventions
        mChange = -mDrained;
        mDrained = 0;
        mAdded = 0;
    }

    @Override
    public void addToBuffer(long energy) {
        mBuffer += energy;
        mAdded += energy;
        if (energy > 0 && mOnRefill != null) {
            mOnRefill.run();
        }
    }

    @Override
    public long getRequest() {
        return HatchBufferMath.request(capacityNative(), mBuffer, getLimit(), mAdded);
    }

    /**
     * The GT energy container calls this method when a multiblock consumes EU.
     * The method drains whole EU only, and it conserves energy: the buffer
     * loses exactly the native equivalent of the returned EU amount. On an FE
     * network, a buffer remainder smaller than 4 FE stays in the buffer.
     *
     * @param requestEU the EU amount requested, in EU
     * @return the whole-EU amount drained, never negative
     */
    public long drainEU(long requestEU) {
        IEnergySystem es = IEnergySystem.of(getEnergyUnit());
        long eu = HatchBufferMath.drainEU(requestEU, mBuffer, es);
        long op = HatchBufferMath.nativeOf(eu, es);
        mBuffer -= op;
        mDrained += op;
        return eu;
    }

    /**
     * @return the EU view of the buffer (floors)
     */
    public long getBufferEU() {
        return IEnergySystem.of(getEnergyUnit()).toConnector(getBuffer(), EnergyType.EU);
    }

    /**
     * @return the buffer capacity, in EU
     */
    public long getCapacityEU() {
        return mCapacityEU;
    }

    /**
     * Returns the buffer capacity in the network's native unit. The method
     * recomputes this value per call, so a network unit switch is safe.
     */
    private long capacityNative() {
        return HatchBufferMath.nativeOf(mCapacityEU, IEnergySystem.of(getEnergyUnit()));
    }

    @Override
    public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        tag.putLong(FluxConstants.BUFFER, mBuffer);
    }
}
