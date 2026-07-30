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
 * This is an EU-native connector for GregTech Modern blocks and electric
 * items. This connector denominates all amounts that cross it in EU. The
 * caller's {@code IEnergySystem} converts the amount to and from the
 * network's native energy type at the boundary.
 * <p>
 * Do not reorder the connector registration. This connector must register
 * after the Forge connector, in
 * {@link sonar.fluxnetworks.common.util.EnergyUtils#register()}, because
 * connector lookup uses the first match. GT machines at the supported tag
 * expose no {@code ForgeCapabilities.ENERGY}. So, registering Forge before
 * GTCEU guarantees no double conversion in either direction.
 * <p>
 * GT convention: {@code acceptEnergyFromNetwork(side, voltage, amperage)}
 * returns the number of <em>amperes</em> accepted. The receiver is credited
 * {@code voltage * amps} EU, in whole amps only.
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
        // An overfilled container may report negative headroom, so check for <= 0.
        if (demand <= 0) {
            return 0;
        }
        long voltage = Math.min(Math.min(container.getInputVoltage(), amount), demand);
        if (voltage <= 0) {
            return 0;
        }
        // Keep whole amps only. By construction, voltage * amperage stays <=
        // min(amount, demand), so the product cannot overflow.
        long amperage = Math.min(Math.min(container.getInputAmperage(), amount / voltage), demand / voltage);
        if (amperage <= 0) {
            return 0;
        }
        if (simulate) {
            // This exactly predicts what execute will do, for a receiver whose
            // advertised headroom agrees with its acceptance.
            return voltage * amperage;
        }
        return voltage * container.acceptEnergyFromNetwork(side, voltage, amperage);
    }

    @Override
    public long receiveFrom(long amount, @Nonnull BlockEntity target, @Nonnull Direction side, boolean simulate) {
        // Note: nothing calls this method today. Flux never pulls energy from GT
        // blocks. Instead, GT sources push energy into the plug's IEnergyContainer.
        // This method stays correct as a defensive measure.
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
        // Keep whole amps only. By construction, voltage * amperage stays <=
        // amount, so it cannot overflow.
        long amperage = Math.min(container.getOutputAmperage(), amount / voltage);
        if (amperage <= 0) {
            return 0;
        }
        long packet = voltage * amperage;
        if (simulate) {
            // Do not mutate state during simulation. removeEnergy has no
            // simulation support.
            return packet;
        }
        // The sign convention of removeEnergy's return value is unverified. This
        // code normalizes it defensively.
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
            return electricItem.discharge(amount, electricItem.getTier(), false, true, simulate);
        }
        return 0;
    }
}
