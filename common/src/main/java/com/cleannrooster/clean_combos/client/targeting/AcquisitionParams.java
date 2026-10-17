package com.cleannrooster.clean_combos.client.targeting;

/** Built each tick from the profile of the weapon that swings next. */
public record AcquisitionParams(double acquireRange, double trueReach,
                                double maxScreenOffset, float stickiness) {
}
