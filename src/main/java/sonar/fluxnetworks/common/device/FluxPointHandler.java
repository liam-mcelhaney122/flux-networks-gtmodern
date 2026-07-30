package sonar.fluxnetworks.common.device;

import sonar.fluxnetworks.api.energy.IEnergySystem;

import javax.annotation.Nonnull;

public class FluxPointHandler extends FluxConnectorHandler {

    private long mDesired;

    public FluxPointHandler() {
    }

    @Override
    public void onCycleStart(@Nonnull IEnergySystem es) {
        super.onCycleStart(es);
        mDesired = sendToConsumers(getLimit(), true, es);
    }

    @Override
    public void onCycleEnd(@Nonnull IEnergySystem es) {
        mBuffer += mChange = -sendToConsumers(Math.min(mBuffer, getLimit()), false, es);
    }

    @Override
    public void addToBuffer(long energy) {
        mBuffer += energy;
    }

    @Override
    public long getRequest() {
        return Math.max(mDesired - mBuffer, 0);
    }

    private long sendToConsumers(long energy, boolean simulate, @Nonnull IEnergySystem es) {
        long leftover = energy;
        for (SideTransfer transfer : mTransfers) {
            if (transfer != null) {
                leftover -= transfer.send(leftover, simulate, es);
                if (leftover <= 0) {
                    return energy;
                }
            }
        }
        return energy - leftover;
    }
}
