package sonar.fluxnetworks.common.integration.gtceu;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPoint;
import sonar.fluxnetworks.common.device.FluxHatchPointHandler;

import javax.annotation.Nonnull;

/**
 * This is the flux energy input hatch. It is a logical point on its flux
 * network: the network fills the internal buffer, and the GT multiblock
 * drains the buffer as EU through the recipe path. The buffer capacity is
 * {@code V[tier] * 16 * 2} EU, the same as a 2A GT energy hatch.
 * <p>
 * Class-loading contract: this class imports {@code com.gregtechceu} types.
 * The game must load it only when the gtceu mod is present. See
 * {@link FluxHatchPartMachine}.
 */
public class FluxEnergyInputHatchMachine extends FluxHatchPartMachine implements IFluxPoint {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(FluxEnergyInputHatchMachine.class, FluxHatchPartMachine.MANAGED_FIELD_HOLDER);

    /**
     * The amperage of the hatch, matches GT's common 2A energy hatch.
     */
    public static final int AMPERAGE = 2;

    private final FluxHatchPointHandler mHandler;
    private final FluxHatchEnergyContainer mEnergyContainer;

    public FluxEnergyInputHatchMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, IO.IN);
        mHandler = new FluxHatchPointHandler(GTValues.V[tier] * 16L * AMPERAGE);
        // the trait auto-attaches to this machine in its constructor
        mEnergyContainer = FluxHatchEnergyContainer.receiver(this, mHandler, GTValues.V[tier], AMPERAGE);
        // a refill wakes the idle multiblock recipe logic
        mHandler.setOnRefill(mEnergyContainer::notifyChanged);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Nonnull
    @Override
    public FluxHatchPointHandler getTransferHandler() {
        return mHandler;
    }

    @Nonnull
    @Override
    public FluxDeviceType getDeviceType() {
        return FluxDeviceType.POINT;
    }
}
