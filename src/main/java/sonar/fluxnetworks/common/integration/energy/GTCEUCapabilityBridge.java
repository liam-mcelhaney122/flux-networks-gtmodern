package sonar.fluxnetworks.common.integration.energy;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import sonar.fluxnetworks.api.energy.EnergyMath;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.api.energy.IEnergySystem;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.ITransferNode;
import sonar.fluxnetworks.common.device.FluxPlugHandler;
import sonar.fluxnetworks.common.device.TileFluxPlug;
import sonar.fluxnetworks.common.device.TileFluxPoint;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This is the only {@link IGTEnergyBridge} implementation. Together with
 * {@link GTCEUEnergyConnector}, it is one of exactly two classes that may
 * import {@code com.gregtechceu} types. The code creates this class only
 * inside the gtceu-gated block of
 * {@link sonar.fluxnetworks.common.util.EnergyUtils#register()}. So, the game
 * loads no GT class unless the mod is present and enabled.
 * <p>
 * GT convention notes (verified at tag 1.20.1-1.1.3.b-build_528):
 * {@code acceptEnergyFromNetwork(side, voltage, amperage)} returns the number
 * of <em>amperes</em> accepted. The receiver is credited {@code voltage * amps}
 * EU. Sources push energy at their own output voltage. The advertised input
 * voltage and amperage are receiver policy, for display only. Over-voltage
 * explosions only occur in receivers that implement {@code IExplosionMachine}.
 * These adapters do not implement it.
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

    @Nullable
    @Override
    public ITransferNode getTransferNode(@Nullable BlockEntity target) {
        // This mod's hatch machines implement ITransferNode on the MetaMachine,
        // not on the holder block entity.
        if (target instanceof IMachineBlockEntity machineBE &&
                machineBE.getMetaMachine() instanceof ITransferNode node) {
            return node;
        }
        return null;
    }

    /**
     * This is an EU input adapter for a plug, with one instance per side. It
     * accepts whole amps only. {@link FluxPlugHandler#receive} backs it, and
     * the network's {@link IEnergySystem} converts the amount at the boundary
     * (identity on EU networks, {@code <<2} on FE networks). So, this adapter
     * creates or loses no energy: the source deducts {@code amps * voltage}
     * EU, and the plug credits exactly {@code amps * fromConnector(voltage)}
     * native units.
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
            // Overflow-clamp the amps so amps * nativePerAmp stays in the long range.
            long amps = EnergyMath.clampAmps(amperage, nativePerAmp);
            final long sim = handler.receive(amps * nativePerAmp, mSide, true, limiter);
            // Keep only whole amps.
            amps = EnergyMath.wholeAmps(amps, nativePerAmp, sim);
            if (amps > 0) {
                handler.receive(amps * nativePerAmp, mSide, false, limiter);
                return amps;
            }
            return 0;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            // This mirrors the FE capability's canReceive() method.
            return mPlug.getNetwork().isValid();
        }

        @Override
        public boolean outputsEnergy(Direction side) {
            return false;
        }

        @Override
        public long changeEnergy(long differenceAmount) {
            // The GT javadoc marks this method for internal use only, not for
            // pushing into the network.
            return 0;
        }

        @Override
        public long getEnergyCanBeInserted() {
            // This reports honest headroom, using the same formula as acceptance,
            // including its quirks. So, GT generators idle when the network has no
            // demand, instead of sending refused packets repeatedly. The advertised
            // headroom always agrees with what the network accepts.
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
            // This keeps the GT default identity true: capacity minus stored equals
            // canBeInserted.
            final long sum = getEnergyStored() + getEnergyCanBeInserted();
            return sum < 0 ? Long.MAX_VALUE : sum;
        }

        @Override
        public long getInputAmperage() {
            // This equals GTValues.V[MAX]. It is never Long.MAX_VALUE, so any
            // GT-side V * A product stays in the long range.
            return Integer.MAX_VALUE;
        }

        @Override
        public long getInputVoltage() {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * This is a connection-only stub for a point, so that GT cables can attach.
     * Attachment and endpoint discovery need only capability presence. All input
     * methods refuse energy, and {@code getEnergyCanBeInserted()} stays at its
     * default value of 0. So, nothing tries to fill it. Actual delivery stays
     * on the flux side: {@code SideTransfer.send -> GTCEUEnergyConnector.sendTo}
     * delivers energy into the neighbor's own container.
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
            // This value is for display only. Delivery happens on the flux side.
            return Integer.MAX_VALUE;
        }

        @Override
        public long getOutputAmperage() {
            // This value is for display only. It is a defensive nonzero value.
            return 1;
        }
    }
}
