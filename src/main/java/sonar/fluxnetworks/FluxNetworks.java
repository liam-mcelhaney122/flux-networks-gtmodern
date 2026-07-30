package sonar.fluxnetworks;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;

@Mod(FluxNetworks.MODID)
public class FluxNetworks {

    public static final String MODID = "fluxnetworks";
    public static final String NAME = "Flux Networks";
    public static final String NAME_CPT = "FluxNetworks";

    public static final Logger LOGGER = LogManager.getLogger(NAME_CPT);

    private static boolean sCuriosLoaded;
    private static boolean sModernUILoaded;

    public FluxNetworks() {
        sCuriosLoaded = ModList.get().isLoaded("curios");
        sModernUILoaded = ModList.get().isLoaded("modernui");

        FluxConfig.init();

        // GT hatch registration rides on mod presence only. FluxConfig.enableGTCEU
        // is a COMMON config value that is not loaded yet in this constructor, and
        // registry entries must be deterministic across restarts anyway. With
        // enableGTCEU=false the hatch blocks still exist, but they stay inert: the
        // GT bridge is null, so the hatches cannot join networks.
        // Class-loading note: FluxGTRegistration (and through it, GT classes) is
        // only classloaded inside this guard, so the game never loads a GT class
        // when the gtceu mod is absent.
        if (ModList.get().isLoaded("gtceu")) {
            sonar.fluxnetworks.common.integration.gtceu.FluxGTRegistration.earlyInit();
        }
    }

    public static boolean isCuriosLoaded() {
        return sCuriosLoaded;
    }

    public static boolean isModernUILoaded() {
        return sModernUILoaded;
    }

    @Nonnull
    public static ResourceLocation location(String path) {
        return new ResourceLocation(MODID, path);
    }
}
