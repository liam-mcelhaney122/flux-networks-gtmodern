package sonar.fluxnetworks.common.connection;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.List;

public class TransferIterator implements Iterator<ITransferNode> {

    private final boolean mPoint;

    private Iterator<ITransferNode> mIterator;
    private ITransferNode mNext;

    public TransferIterator(boolean point) {
        mPoint = point;
    }

    public TransferIterator reset(@Nonnull List<ITransferNode> list) {
        mIterator = list.iterator();
        if (mIterator.hasNext()) {
            mNext = mIterator.next();
        } else {
            mNext = null;
        }
        return this;
    }

    public boolean increment() {
        if (mIterator.hasNext()) {
            mNext = mIterator.next();
            return needTransfer() || increment();
        }
        mNext = null;
        return false;
    }

    private boolean needTransfer() {
        /*if (!mNext.isActive()) {
            return false;
        }*/
        if (mPoint) {
            return mNext.getTransferHandler().getRequest() > 0;
        } else {
            return mNext.getTransferHandler().getBuffer() > 0;
        }
    }

    @Override
    public boolean hasNext() {
        if (mNext == null) {
            return false;
        }
        return needTransfer() || increment();
    }

    @Override
    public ITransferNode next() {
        return mNext;
    }
}
