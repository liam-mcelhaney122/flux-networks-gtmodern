package sonar.fluxnetworks.api.energy;

import javax.annotation.Nonnull;

public class EUEnergySystem implements IEnergySystem {

    public static final EUEnergySystem INSTANCE = new EUEnergySystem();

    private EUEnergySystem() {
    }

    @Nonnull
    @Override
    public EnergyType getEnergyType() {
        return EnergyType.EU;
    }
}
