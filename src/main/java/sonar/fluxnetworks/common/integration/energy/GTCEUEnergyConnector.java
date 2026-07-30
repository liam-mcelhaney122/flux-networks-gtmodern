package sonar.fluxnetworks.common.integration.energy;

import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.api.energy.IBlockEnergyConnector;
import sonar.fluxnetworks.api.energy.IItemEnergyConnector;
import sonar.fluxnetworks.common.util.FluxUtils;

import javax.annotation.Nonnull;

/**
 * EU-native connector for GregTech Modern blocks and electric items. All amounts
 * crossing this connector are denominated in EU; the caller's {@code IEnergySystem}
 * converts to/from the network's native unit at the boundary.
 * <p>
 * Registration order dependency: this connector is added after the Forge connector
 * in {@link sonar.fluxnetworks.common.util.EnergyUtils#register()}, and connector
 * lookup is first-match. GT machines at the supported tag expose no
 * {@code ForgeCapabilities.ENERGY}, so Forge-before-GTCEU guarantees no double
 * conversion in either direction. Do not reorder.
 * <p>
 * GT convention: {@code acceptEnergyFromNetwork(side, voltage, amperage)} returns
 * the number of <em>amperes</em> accepted; the receiver is credited
 * {@code voltage * amps} EU, whole amps only.
 */
public class GTCEUEnergyConnector implements IBlockEnergyConnector, IItemEnergyConnector {

    public static final GTCEUEnergyConnector INSTANCE = new GTCEUEnergyConnector();

    @Nonnull
    @Override
    public EnergyType getNativeType() {
        return EnergyType.EU;
    }

    @Override
    public boolean hasCapability(@Nonnull BlockEntity target, @Nonnull Direction side) {
        return !target.isRemoved() && target.getCapability(GTCapability.CAPABILITY_ENERGY_CONTAINER, side).isPresent();
    }

    @Override
    public boolean canSendTo(@Nonnull BlockEntity target, @Nonnull Direction side) {
        if (!target.isRemoved()) {
            IEnergyContainer container = FluxUtils.get(target, GTCapability.CAPABILITY_ENERGY_CONTAINER, side);
            return container != null && container.inputsEnergy(side);
        }
        return false;
    }

    @Override
    public boolean canReceiveFrom(@Nonnull BlockEntity target, @Nonnull Direction side) {
        if (!target.isRemoved()) {
            IEnergyContainer container = FluxUtils.get(target, GTCapability.CAPABILITY_ENERGY_CONTAINER, side);
            return container != null && container.outputsEnergy(side);
        }
        return false;
    }

    @Override
    public long sendTo(long amount, @Nonnull BlockEntity target, @Nonnull Direction side, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        IEnergyContainer container = FluxUtils.get(target, GTCapability.CAPABILITY_ENERGY_CONTAINER, side);
        if (container == null) {
            return 0;
        }
        long demand = container.getEnergyCanBeInserted();
        // <= 0: an overfilled container may report negative headroom
        if (demand <= 0) {
            return 0;
        }
        long voltage = Math.min(Math.min(container.getInputVoltage(), amount), demand);
        if (voltage <= 0) {
            return 0;
        }
        // whole amps only; voltage * amperage <= min(amount, demand) by construction,
        // so the product cannot overflow
        long amperage = Math.min(Math.min(container.getInputAmperage(), amount / voltage), demand / voltage);
        if (amperage <= 0) {
            return 0;
        }
        if (simulate) {
            // exactly predicts execute against a receiver whose advertised headroom
            // agrees with its acceptance
            return voltage * amperage;
        }
        return voltage * container.acceptEnergyFromNetwork(side, voltage, amperage);
    }

    @Override
    public long receiveFrom(long amount, @Nonnull BlockEntity target, @Nonnull Direction side, boolean simulate) {
        // Note: currently uncalled — flux never pulls from GT blocks (GT sources push
        // into the plug's IEnergyContainer instead); kept correct defensively.
        if (amount <= 0) {
            return 0;
        }
        IEnergyContainer container = FluxUtils.get(target, GTCapability.CAPABILITY_ENERGY_CONTAINER, side);
        if (container == null) {
            return 0;
        }
        long voltage = container.getOutputVoltage();
        if (voltage <= 0) {
            return 0;
        }
        // whole amps only; voltage * amperage <= amount by construction, overflow-free
        long amperage = Math.min(container.getOutputAmperage(), amount / voltage);
        if (amperage <= 0) {
            return 0;
        }
        long packet = voltage * amperage;
        if (simulate) {
            // removeEnergy has no simulation support; do not mutate on simulate
            return packet;
        }
        // removeEnergy's return sign convention is unverified; normalize defensively
        return Math.min(Math.abs(container.removeEnergy(packet)), packet);
    }

    @Override
    public boolean hasCapability(@Nonnull ItemStack stack) {
        return !stack.isEmpty() && stack.getCapability(GTCapability.CAPABILITY_ELECTRIC_ITEM).isPresent();
    }

    @Override
    public boolean canSendTo(@Nonnull ItemStack stack) {
        return hasCapability(stack);
    }

    @Override
    public boolean canReceiveFrom(@Nonnull ItemStack stack) {
        return hasCapability(stack);
    }

    @Override
    public long sendTo(long amount, @Nonnull ItemStack stack, boolean simulate) {
        IElectricItem electricItem = FluxUtils.get(stack, GTCapability.CAPABILITY_ELECTRIC_ITEM);
        if (electricItem != null) {
            return electricItem.charge(amount, electricItem.getTier(), false, simulate);
        }
        return 0;
    }

    @Override
    public long receiveFrom(long amount, @Nonnull ItemStack stack, boolean simulate) {
        IElectricItem electricItem = FluxUtils.get(stack, GTCapability.CAPABILITY_ELECTRIC_ITEM);
        if (electricItem != null) {
            return electricItem.discharge(amount, electricItem.getTier(), false, true, false);
        }
        return 0;
    }
}
