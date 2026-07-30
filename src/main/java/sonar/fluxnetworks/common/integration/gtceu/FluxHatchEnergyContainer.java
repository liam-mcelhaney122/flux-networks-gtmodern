package sonar.fluxnetworks.common.integration.gtceu;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import sonar.fluxnetworks.common.device.FluxHatchDynamoHandler;
import sonar.fluxnetworks.common.device.FluxHatchPointHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This is the GT energy container trait of a flux hatch. The flux transfer
 * handler owns the real energy state; this trait is a stateless EU view of
 * that buffer. The GT recipe path reads {@link #getEnergyStored()},
 * {@link #getEnergyCapacity()}, and moves energy through
 * {@link #changeEnergy(long)}.
 * <p>
 * Cable isolation: the constructor sets the capability validator and both
 * side conditions to always-false. The holder block entity filters traits by
 * {@code hasCapability(side)} before it exposes the GT energy capability, so
 * GT cables can never connect to a flux hatch, and an adjacent flux plug or
 * point sees no capability either. This blocks double network membership and
 * energy loops through the block boundary. The recipe path uses the trait
 * list directly, not block capabilities, so the multiblock still works.
 * <p>
 * Over-voltage safety: {@code acceptEnergyFromNetwork} is unreachable, because
 * the capability is never exposed. So the hatch can never explode from GT
 * cable input.
 * <p>
 * Persistence: this trait is not registered as a persisted field, and the
 * inherited {@code energyStored} field stays at 0. The real buffer lives in
 * the flux transfer handler, which the machine saves through
 * {@code saveCustomPersistedData}.
 * <p>
 * Class-loading contract: this class imports {@code com.gregtechceu} types.
 * The game must load it only when the gtceu mod is present. See
 * {@link FluxHatchPartMachine}.
 */
public class FluxHatchEnergyContainer extends NotifiableEnergyContainer {

    @Nullable
    private final FluxHatchPointHandler mPointHandler;
    @Nullable
    private final FluxHatchDynamoHandler mDynamoHandler;

    private FluxHatchEnergyContainer(@Nonnull MetaMachine machine, long capacityEU,
                                     long maxInputVoltage, long maxInputAmperage,
                                     long maxOutputVoltage, long maxOutputAmperage,
                                     @Nullable FluxHatchPointHandler pointHandler,
                                     @Nullable FluxHatchDynamoHandler dynamoHandler) {
        super(machine, capacityEU, maxInputVoltage, maxInputAmperage, maxOutputVoltage, maxOutputAmperage);
        mPointHandler = pointHandler;
        mDynamoHandler = dynamoHandler;
        // Cable isolation, see the class javadoc.
        setCapabilityValidator(side -> false);
        setSideInputCondition(side -> false);
        setSideOutputCondition(side -> false);
    }

    /**
     * Creates the receiver-shaped container of a flux energy input hatch.
     * This mirrors {@code NotifiableEnergyContainer#receiverContainer}, so the
     * handler IO resolves to IN and the controller reads the input voltage
     * and amperage for its EUt cap.
     *
     * @param machine  the input hatch machine
     * @param handler  the flux point handler that owns the buffer
     * @param voltage  the input voltage, {@code GTValues.V[tier]}
     * @param amperage the input amperage
     * @return the container trait, already attached to the machine
     */
    @Nonnull
    public static FluxHatchEnergyContainer receiver(@Nonnull MetaMachine machine,
                                                    @Nonnull FluxHatchPointHandler handler,
                                                    long voltage, long amperage) {
        return new FluxHatchEnergyContainer(machine, handler.getCapacityEU(),
                voltage, amperage, 0L, 0L, handler, null);
    }

    /**
     * Creates the emitter-shaped container of a flux dynamo hatch.
     * This mirrors {@code NotifiableEnergyContainer#emitterContainer}, so the
     * handler IO resolves to OUT and generator recipes credit into it.
     *
     * @param machine  the dynamo hatch machine
     * @param handler  the flux dynamo handler that owns the buffer
     * @param voltage  the output voltage, {@code GTValues.V[tier]}
     * @param amperage the output amperage
     * @return the container trait, already attached to the machine
     */
    @Nonnull
    public static FluxHatchEnergyContainer emitter(@Nonnull MetaMachine machine,
                                                   @Nonnull FluxHatchDynamoHandler handler,
                                                   long voltage, long amperage) {
        return new FluxHatchEnergyContainer(machine, handler.getCapacityEU(),
                0L, 0L, voltage, amperage, null, handler);
    }

    /**
     * @return the EU view of the flux buffer (floors on an FE network)
     */
    @Override
    public long getEnergyStored() {
        if (mPointHandler != null) {
            return mPointHandler.getBufferEU();
        }
        if (mDynamoHandler != null) {
            return mDynamoHandler.getBufferEU();
        }
        return 0;
    }

    @Override
    public long getEnergyCapacity() {
        if (mPointHandler != null) {
            return mPointHandler.getCapacityEU();
        }
        if (mDynamoHandler != null) {
            return mDynamoHandler.getCapacityEU();
        }
        return 0;
    }

    /**
     * Moves energy between the recipe path and the flux buffer. A negative
     * amount drains the input hatch. A positive amount credits the dynamo
     * hatch. A wrong-direction call returns 0.
     *
     * @param differenceAmount the EU amount, negative to drain
     * @return the actual EU change, same sign convention
     */
    @Override
    public long changeEnergy(long differenceAmount) {
        if (differenceAmount < 0 && mPointHandler != null) {
            return -mPointHandler.drainEU(-differenceAmount);
        }
        if (differenceAmount > 0 && mDynamoHandler != null) {
            return mDynamoHandler.creditEU(differenceAmount);
        }
        return 0;
    }

    /**
     * No-op. The parent's server tick pushes buffered EU into neighbor
     * containers, which would bypass the flux network. The always-false side
     * conditions are the backstop, this override removes the work entirely.
     */
    @Override
    public void serverTick() {
    }

    /**
     * No-op. The parent subscribes {@code serverTick} when an emitter holds
     * energy. The suppressed tick makes that subscription pointless, so this
     * override skips it.
     */
    @Override
    public void checkOutputSubscription() {
    }

    /**
     * Notifies the recipe-handler listeners. The input hatch's refill hook
     * calls this method, so an idle multiblock re-checks its recipe when the
     * network delivers energy.
     */
    public void notifyChanged() {
        notifyListeners();
    }
}
