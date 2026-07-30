package sonar.fluxnetworks.common.integration.gtceu;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import sonar.fluxnetworks.FluxNetworks;

/**
 * This is the GT addon entry point of Flux Networks. GT discovers this class
 * through its {@code @GTAddon} annotation scan, and that scan runs only when
 * the gtceu mod is present. {@link #initializeAddon()} runs after GT's own
 * {@code GTMachines.init()}, inside GTCEu's construct-time init.
 * <p>
 * Class-loading contract: this class imports {@code com.gregtechceu} types.
 * The game must load it only when the gtceu mod is present. Only GT's
 * annotation scan instantiates it.
 */
@GTAddon
public class FluxGTAddon implements IGTAddon {

    public FluxGTAddon() {
    }

    @Override
    public GTRegistrate getRegistrate() {
        return FluxGTRegistration.REGISTRATE;
    }

    @Override
    public void initializeAddon() {
        FluxGTRegistration.registerMachines();
    }

    @Override
    public String addonModId() {
        return FluxNetworks.MODID;
    }
}
