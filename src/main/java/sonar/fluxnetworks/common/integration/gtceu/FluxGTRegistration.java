package sonar.fluxnetworks.common.integration.gtceu;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.client.renderer.machine.OverlayTieredMachineRenderer;
import net.minecraft.network.chat.Component;
import sonar.fluxnetworks.FluxNetworks;
import sonar.fluxnetworks.register.RegistryCreativeModeTabs;

import java.util.Locale;

/**
 * This class registers the flux energy hatches with GT's registrate. The
 * hatches exist for every tier from LV to UV, at 2A. They reuse GT's own
 * energy hatch overlay models, so this mod ships no new assets.
 * <p>
 * Call order: {@code FluxNetworks}'s constructor calls {@link #earlyInit()}
 * inside its gtceu-presence guard, in mod-loading context. GT's addon scan
 * then calls {@link #registerMachines()} through
 * {@link FluxGTAddon#initializeAddon()}, after GT's own machines registered.
 * <p>
 * Class-loading contract: this class imports {@code com.gregtechceu} types.
 * The game must load it only when the gtceu mod is present. Only the
 * gtceu-gated block in {@code FluxNetworks}'s constructor and the GT addon
 * scan reach it.
 */
public final class FluxGTRegistration {

    public static final GTRegistrate REGISTRATE = GTRegistrate.create(FluxNetworks.MODID);

    /**
     * The registered input hatch definitions, indexed by tier. Entries outside
     * LV..UV stay null.
     */
    public static final MachineDefinition[] FLUX_ENERGY_INPUT_HATCHES =
            new MachineDefinition[GTValues.TIER_COUNT];

    /**
     * The registered dynamo hatch definitions, indexed by tier. Entries outside
     * LV..UV stay null.
     */
    public static final MachineDefinition[] FLUX_DYNAMO_HATCHES =
            new MachineDefinition[GTValues.TIER_COUNT];

    private FluxGTRegistration() {
    }

    /**
     * Binds the registrate to the mod event bus. Call from the mod
     * constructor only, in mod-loading context.
     */
    public static void earlyInit() {
        REGISTRATE.registerRegistrate();
    }

    /**
     * Registers both hatch lines for every tier from LV to UV, then appends
     * the hatch items to this mod's creative tab. GT calls this method through
     * the addon path, after {@code GTMachines.init()}.
     */
    public static void registerMachines() {
        for (int t = GTValues.LV; t <= GTValues.UV; t++) {
            final int tier = t;
            FLUX_ENERGY_INPUT_HATCHES[tier] = REGISTRATE
                    .machine("flux_energy_input_hatch_" + GTValues.VN[tier].toLowerCase(Locale.ROOT),
                            holder -> new FluxEnergyInputHatchMachine(holder, tier))
                    .tier(tier)
                    .langValue(GTValues.VNF[tier] + " Flux Energy Input Hatch")
                    .rotationState(RotationState.ALL)
                    .abilities(PartAbility.INPUT_ENERGY)
                    .tooltips(Component.translatable("tooltip.fluxnetworks.flux_input_hatch"))
                    // reuse GT's own overlay model, no new assets
                    .renderer(() -> new OverlayTieredMachineRenderer(tier,
                            GTCEu.id("block/machine/part/energy_hatch.input")))
                    .register();
            FLUX_DYNAMO_HATCHES[tier] = REGISTRATE
                    .machine("flux_dynamo_hatch_" + GTValues.VN[tier].toLowerCase(Locale.ROOT),
                            holder -> new FluxDynamoHatchMachine(holder, tier))
                    .tier(tier)
                    .langValue(GTValues.VNF[tier] + " Flux Dynamo Hatch")
                    .rotationState(RotationState.ALL)
                    .abilities(PartAbility.OUTPUT_ENERGY)
                    .tooltips(Component.translatable("tooltip.fluxnetworks.flux_dynamo_hatch"))
                    .renderer(() -> new OverlayTieredMachineRenderer(tier,
                            GTCEu.id("block/machine/part/energy_hatch.output")))
                    .register();
        }
        for (MachineDefinition definition : FLUX_ENERGY_INPUT_HATCHES) {
            if (definition != null) {
                RegistryCreativeModeTabs.EXTRA_TAB_ITEMS.add(definition::asStack);
            }
        }
        for (MachineDefinition definition : FLUX_DYNAMO_HATCHES) {
            if (definition != null) {
                RegistryCreativeModeTabs.EXTRA_TAB_ITEMS.add(definition::asStack);
            }
        }
    }
}
