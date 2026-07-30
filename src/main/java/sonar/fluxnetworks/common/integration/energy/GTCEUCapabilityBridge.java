package sonar.fluxnetworks.common.integration.energy;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.api.energy.IEnergySystem;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.device.FluxPlugHandler;
import sonar.fluxnetworks.common.device.TileFluxPlug;
import sonar.fluxnetworks.common.device.TileFluxPoint;

import javax.annotation.Nonnull;

/**
 * The only {@link IGTEnergyBridge} implementation, and (together with
 * {@link GTCEUEnergyConnector}) one of exactly two classes allowed to import
 * {@code com.gregtechceu} types. Instantiated solely inside the gtceu-gated block of
 * {@link sonar.fluxnetworks.common.util.EnergyUtils#register()}, so no GT class is
 * loaded unless the mod is present and enabled.
 * <p>
 * GT convention notes (verified at tag 1.20.1-1.1.3.b-build_528):
 * {@code acceptEnergyFromNetwork(side, voltage, amperage)} returns the number of
 * <em>amperes</em> accepted, and the receiver is credited {@code voltage * amps} EU.
 * Sources push with their own output voltage; the advertised input voltage/amperage
 * are receiver policy and display only. Over-voltage explosions only occur in
 * receivers implementing {@code IExplosionMachine}, which these adapters are not.
 */
public class GTCEUCapabilityBridge implements IGTEnergyBridge {

    public GTCEUCapabilityBridge() {
    }

    @Override
    public boolean isEnergyContainerCapability(@Nonnull Capability<?> cap) {
        return cap == GTCapability.CAPABILITY_ENERGY_CONTAINER;
    }

    @Nonnull
    @Override
    public LazyOptional<?> createPlugEnergyContainer(@Nonnull TileFluxPlug plug, @Nonnull Direction side) {
        final IEnergyContainer container = new PlugEnergyContainer(plug, side);
        return LazyOptional.of(() -> container);
    }

    @Nonnull
    @Override
    public LazyOptional<?> createPointEnergyContainer(@Nonnull TileFluxPoint point) {
        final IEnergyContainer container = new PointEnergyContainer(point);
        return LazyOptional.of(() -> container);
    }

    /**
     * EU input adapter for a plug, one instance per side. Accepts whole amps only,
     * backed by {@link FluxPlugHandler#receive} with the network's
     * {@link IEnergySystem} converting at the boundary (identity on EU networks,
     * {@code <<2} on FE networks), so no energy is created or lost:
     * the source deducts {@code amps * voltage} EU while the plug credits exactly
     * {@code amps * fromConnector(voltage)} native units.
     */
    private static class PlugEnergyContainer implements IEnergyContainer {

        private final TileFluxPlug mPlug;
        private final Direction mSide;

        PlugEnergyContainer(@Nonnull TileFluxPlug plug, @Nonnull Direction side) {
            mPlug = plug;
            mSide = side;
        }

        @Override
        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            if (voltage <= 0 || amperage <= 0) {
                return 0;
            }
            final FluxNetwork network = mPlug.getNetwork();
            if (!network.isValid()) {
                return 0;
            }
            final IEnergySystem es = network.getEnergySystem();
            final long nativePerAmp = es.fromConnector(voltage, EnergyType.EU);
            if (nativePerAmp <= 0) {
                return 0;
            }
            final FluxPlugHandler handler = mPlug.getTransferHandler();
            final long limiter = network.getBufferLimiter();
            // overflow clamp so amps * nativePerAmp stays in long range
            long amps = Math.min(amperage, Long.MAX_VALUE / nativePerAmp);
            final long sim = handler.receive(amps * nativePerAmp, mSide, true, limiter);
            // whole amps only
            amps = Math.min(amps, sim / nativePerAmp);
            if (amps > 0) {
                handler.receive(amps * nativePerAmp, mSide, false, limiter);
                return amps;
            }
            return 0;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            // mirrors the FE cap's canReceive()
            return mPlug.getNetwork().isValid();
        }

        @Override
        public boolean outputsEnergy(Direction side) {
            return false;
        }

        @Override
        public long changeEnergy(long differenceAmount) {
            // GT javadoc: internal use only, not for network pushing
            return 0;
        }

        @Override
        public long getEnergyCanBeInserted() {
            // honest headroom: same formula as acceptance (including its quirks), so
            // GT generators idle when the network has no demand instead of spamming
            // refused packets, and advertisement always agrees with acceptance
            final FluxNetwork network = mPlug.getNetwork();
            if (!network.isValid()) {
                return 0;
            }
            final long sim = mPlug.getTransferHandler().receive(Long.MAX_VALUE, mSide, true,
                    network.getBufferLimiter());
            return network.getEnergySystem().toConnector(sim, EnergyType.EU);
        }

        @Override
        public long getEnergyStored() {
            return mPlug.getNetwork().getEnergySystem()
                    .toConnector(mPlug.getTransferHandler().getBuffer(), EnergyType.EU);
        }

        @Override
        public long getEnergyCapacity() {
            // keeps the GT default identity (capacity - stored == canBeInserted) honest
            final long sum = getEnergyStored() + getEnergyCanBeInserted();
            return sum < 0 ? Long.MAX_VALUE : sum;
        }

        @Override
        public long getInputAmperage() {
            // == GTValues.V[MAX]; never Long.MAX_VALUE so any GT-side V*A product
            // stays in long range
            return Integer.MAX_VALUE;
        }

        @Override
        public long getInputVoltage() {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Connection-only stub for a point so GT cables attach (attachment and endpoint
     * discovery need only capability presence). All input methods refuse energy and
     * {@code getEnergyCanBeInserted()} stays at its 0 default, so nothing tries to
     * fill it. Actual delivery stays flux-side:
     * {@code SideTransfer.send -> GTCEUEnergyConnector.sendTo} into the neighbor's
     * own container.
     */
    private static class PointEnergyContainer implements IEnergyContainer {

        private final TileFluxPoint mPoint;

        PointEnergyContainer(@Nonnull TileFluxPoint point) {
            mPoint = point;
        }

        @Override
        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            return 0;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return false;
        }

        @Override
        public boolean outputsEnergy(Direction side) {
            return mPoint.getNetwork().isValid();
        }

        @Override
        public long changeEnergy(long differenceAmount) {
            return 0;
        }

        @Override
        public long getEnergyStored() {
            return 0;
        }

        @Override
        public long getEnergyCapacity() {
            return 0;
        }

        @Override
        public long getInputAmperage() {
            return 0;
        }

        @Override
        public long getInputVoltage() {
            return 0;
        }

        @Override
        public long getOutputVoltage() {
            // display-only; delivery happens flux-side
            return Integer.MAX_VALUE;
        }

        @Override
        public long getOutputAmperage() {
            // display-only; defensive nonzero
            return 1;
        }
    }
}
