package sonar.fluxnetworks.common.integration.energy;

import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import sonar.fluxnetworks.common.device.TileFluxPlug;
import sonar.fluxnetworks.common.device.TileFluxPoint;

import javax.annotation.Nonnull;

/**
 * Flux-owned indirection for exposing GregTech Modern energy capabilities on flux tiles
 * without loading any GT classes when the mod is absent. This interface must never
 * reference {@code com.gregtechceu} types; the only implementation is
 * {@code GTCEUCapabilityBridge}, which is instantiated solely inside the
 * gtceu-gated block of {@link sonar.fluxnetworks.common.util.EnergyUtils#register()}.
 */
public interface IGTEnergyBridge {

    /**
     * @return true if the given capability is GT's energy container capability
     */
    boolean isEnergyContainerCapability(@Nonnull Capability<?> cap);

    /**
     * Create a lazy optional wrapping an EU input adapter for the given plug side.
     *
     * @param plug the plug tile
     * @param side the side the capability was requested on
     * @return a lazy optional of GT's {@code IEnergyContainer}
     */
    @Nonnull
    LazyOptional<?> createPlugEnergyContainer(@Nonnull TileFluxPlug plug, @Nonnull Direction side);

    /**
     * Create a lazy optional wrapping a connection-only stub for the given point,
     * so GT cables attach; actual delivery is performed flux-side by
     * {@code GTCEUEnergyConnector}.
     *
     * @param point the point tile
     * @return a lazy optional of GT's {@code IEnergyContainer}
     */
    @Nonnull
    LazyOptional<?> createPointEnergyContainer(@Nonnull TileFluxPoint point);
}
