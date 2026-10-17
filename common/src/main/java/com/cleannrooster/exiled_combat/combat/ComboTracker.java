package com.cleannrooster.exiled_combat.combat;

/**
 * Per-entity combo state. Time is the only input: target switches, weapon swaps and approaches never
 * reset it. The chain deadline is recorded when a swing starts, since its recovery end is known then.
 */
public final class ComboTracker {

    private ComboSlot lastSlot = ComboSlot.SECOND;
    private long chainDeadline = Long.MIN_VALUE;

    public ComboSlot nextSlot(long gameTime) {
        return lastSlot == ComboSlot.FIRST && gameTime <= chainDeadline ? ComboSlot.SECOND : ComboSlot.FIRST;
    }

    /** Server-side check of a client's claimed SECOND, with slack for latency. */
    public boolean canChain(long gameTime, int toleranceTicks) {
        return lastSlot == ComboSlot.FIRST && gameTime <= chainDeadline + toleranceTicks;
    }

    public void onSwingStarted(ComboSlot slot, long recoveryEndTime, int chainGraceTicks) {
        this.lastSlot = slot;
        this.chainDeadline = recoveryEndTime + chainGraceTicks;
    }

    /** A hitstop pushed the swing's recovery back; push the chain window back with it. */
    public void extendChain(int ticks) {
        if (chainDeadline != Long.MIN_VALUE) {
            chainDeadline += ticks;
        }
    }

    public void reset() {
        lastSlot = ComboSlot.SECOND;
        chainDeadline = Long.MIN_VALUE;
    }
}
