package sonar.fluxnetworks.common.integration.gtceu;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.device.IFluxPlug;
import sonar.fluxnetworks.common.device.FluxHatchDynamoHandler;

import javax.annotation.Nonnull;

/**
 * This is the flux dynamo hatch. It is a logical plug on its flux network:
 * the GT generator multiblock credits generated EU into the internal buffer
 * through the recipe path, and the network drains the buffer. The buffer
 * capacity is {@code V[tier] * 64 * 2} EU, the same as a 2A GT dynamo hatch.
 * <p>
 * Class-loading contract: this class imports {@code com.gregtechceu} types.
 * The game must load it only when the gtceu mod is present. See
 * {@link FluxHatchPartMachine}.
 */
public class FluxDynamoHatchMachine extends FluxHatchPartMachine implements IFluxPlug {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(FluxDynamoHatchMachine.class, FluxHatchPartMachine.MANAGED_FIELD_HOLDER);

    /**
     * The amperage of the hatch, matches GT's common 2A dynamo hatch.
     */
    public static final int AMPERAGE = 2;

    private final FluxHatchDynamoHandler mHandler;
    private final FluxHatchEnergyContainer mEnergyContainer;

    public FluxDynamoHatchMachine(IMachineBlockEntity holder, int tier) {
        super(holder, tier, IO.OUT);
        mHandler = new FluxHatchDynamoHandler(GTValues.V[tier] * 64L * AMPERAGE);
        // the trait auto-attaches to this machine in its constructor
        mEnergyContainer = FluxHatchEnergyContainer.emitter(this, mHandler, GTValues.V[tier], AMPERAGE);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Nonnull
    @Override
    public FluxHatchDynamoHandler getTransferHandler() {
        return mHandler;
    }

    @Nonnull
    @Override
    public FluxDeviceType getDeviceType() {
        return FluxDeviceType.PLUG;
    }
}
