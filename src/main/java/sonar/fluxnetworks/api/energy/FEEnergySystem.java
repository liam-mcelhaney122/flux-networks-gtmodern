package sonar.fluxnetworks.api.energy;

import javax.annotation.Nonnull;

public class FEEnergySystem implements IEnergySystem {

    public static final FEEnergySystem INSTANCE = new FEEnergySystem();

    private FEEnergySystem() {
    }

    @Nonnull
    @Override
    public EnergyType getEnergyType() {
        return EnergyType.FE;
    }
}
