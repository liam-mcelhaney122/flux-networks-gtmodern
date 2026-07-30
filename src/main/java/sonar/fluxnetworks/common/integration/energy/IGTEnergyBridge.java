package sonar.fluxnetworks.common.integration.energy;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import sonar.fluxnetworks.common.connection.ITransferNode;
import sonar.fluxnetworks.common.device.TileFluxPlug;
import sonar.fluxnetworks.common.device.TileFluxPoint;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Do not reference {@code com.gregtechceu} types in this interface. Flux owns
 * this indirection layer, which exposes GregTech Modern energy capabilities
 * on flux tiles without loading any GT classes when the mod is absent. The
 * only implementation is {@code GTCEUCapabilityBridge}. The code creates that
 * implementation only inside the gtceu-gated block of
 * {@link sonar.fluxnetworks.common.util.EnergyUtils#register()}.
 */
public interface IGTEnergyBridge {

    /**
     * @return true if the given capability is GT's energy container capability
     */
    boolean isEnergyContainerCapability(@Nonnull Capability<?> cap);

    /**
     * Creates a lazy optional that wraps an EU input adapter for the given
     * plug side.
     *
     * @param plug the plug tile
     * @param side the side the capability was requested on
     * @return a lazy optional of GT's {@code IEnergyContainer}
     */
    @Nonnull
    LazyOptional<?> createPlugEnergyContainer(@Nonnull TileFluxPlug plug, @Nonnull Direction side);

    /**
     * Creates a lazy optional that wraps a connection-only stub for the given
     * point, so that GT cables can attach. The flux side performs actual
     * delivery, through {@code GTCEUEnergyConnector}.
     *
     * @param point the point tile
     * @return a lazy optional of GT's {@code IEnergyContainer}
     */
    @Nonnull
    LazyOptional<?> createPointEnergyContainer(@Nonnull TileFluxPoint point);

    /**
     * Resolves a GT machine block entity to its flux transfer node, if the machine is one of
     * this mod's hatch machines. Returns null for anything else.
     */
    @Nullable
    ITransferNode getTransferNode(@Nullable BlockEntity target);
}
