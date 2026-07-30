package sonar.fluxnetworks.common.device;

import net.minecraft.nbt.CompoundTag;
import sonar.fluxnetworks.FluxConfig;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.api.energy.HatchBufferMath;
import sonar.fluxnetworks.api.energy.IEnergySystem;
import sonar.fluxnetworks.common.connection.TransferHandler;

import javax.annotation.Nonnull;

/**
 * This is the transfer handler for a flux energy dynamo hatch. The hatch is a
 * logical plug: it feeds the network. The GT energy container credits
 * generated EU into the internal buffer, through {@link #creditEU}. The
 * network drains the buffer, in the network's native energy type. See
 * {@link HatchBufferMath} for the unit math.
 */
public class FluxHatchDynamoHandler extends TransferHandler {

    /**
     * The buffer capacity, in EU. The GT side sets this to V[tier] * 64 * 2.
     */
    private final long mCapacityEU;

    // external received energy happen outside the transfer cycle
    private long mReceived;

    // internal removed energy happen inside the transfer cycle
    private long mRemoved;

    public FluxHatchDynamoHandler(long capacityEU) {
        super(FluxConfig.defaultLimit);
        mCapacityEU = capacityEU;
    }

    @Override
    public void onCycleStart(@Nonnull IEnergySystem es) {
    }

    @Override
    public void onCycleEnd(@Nonnull IEnergySystem es) {
        mChange = mReceived;
        mReceived = 0;
        mRemoved = 0;
    }

    @Override
    public long removeFromBuffer(long energy) {
        long op = Math.min(Math.min(energy, mBuffer), getLimit() - mRemoved);
        assert op >= 0;
        mBuffer -= op;
        mRemoved += op;
        return op;
    }

    /**
     * The GT energy container calls this method when a generator produces EU.
     * GT pre-clamps the amount by the advertised headroom (capacity minus
     * stored), so this method applies no clamp. On an FE network, the floored
     * stored view can allow at most 3 FE of overshoot above the nominal
     * capacity, which is acceptable.
     *
     * @param eu the generated EU amount, in EU
     * @return the credited EU amount, always the full input
     */
    public long creditEU(long eu) {
        IEnergySystem es = IEnergySystem.of(getEnergyUnit());
        long op = HatchBufferMath.nativeOf(eu, es);
        mBuffer += op;
        mReceived += op;
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
     * @return the EU amount the hatch can still accept, the GT container's headroom view
     */
    public long getInsertableEU() {
        return HatchBufferMath.insertableEU(mCapacityEU, getBufferEU());
    }

    @Override
    public void writeCustomTag(@Nonnull CompoundTag tag, byte type) {
        super.writeCustomTag(tag, type);
        tag.putLong(FluxConstants.BUFFER, mBuffer);
    }
}
