package com.cleannrooster.clean_combos.combat;

import com.cleannrooster.clean_combos.CleanCombos;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The swing whoosh and the hit each attack makes, by weight: light (one-handed and dual-wielded swings), heavy
 * (two-handed swings, chops and jump attacks), quick (spear thrusts) and blunt (fists: a duller, lower whoosh
 * with no blade edge, and a thud on the hit). Vanilla's own attack sounds are muted during a strike (see
 * PlayerEntityMixin), so these are the only ones.
 */
public final class CombatSounds {

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(CleanCombos.MOD_ID,
        RegistryKeys.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> SWING_LIGHT = register("swing.light");
    public static final RegistrySupplier<SoundEvent> SWING_HEAVY = register("swing.heavy");
    public static final RegistrySupplier<SoundEvent> SWING_QUICK = register("swing.quick");
    public static final RegistrySupplier<SoundEvent> SWING_BLUNT = register("swing.blunt");
    public static final RegistrySupplier<SoundEvent> HIT_LIGHT = register("hit.light");
    public static final RegistrySupplier<SoundEvent> HIT_HEAVY = register("hit.heavy");
    public static final RegistrySupplier<SoundEvent> HIT_QUICK = register("hit.quick");
    public static final RegistrySupplier<SoundEvent> HIT_BLUNT = register("hit.blunt");

    private enum Weight {
        LIGHT,
        HEAVY,
        QUICK,
        BLUNT
    }

    private CombatSounds() {
    }

    public static void register() {
        SOUNDS.register();
    }

    /** The whoosh as the swing's fast part begins. */
    public static SoundEvent swingFor(PlannedSwing swing) {
        return switch (weightOf(swing)) {
            case LIGHT -> SWING_LIGHT.get();
            case HEAVY -> SWING_HEAVY.get();
            case QUICK -> SWING_QUICK.get();
            case BLUNT -> SWING_BLUNT.get();
        };
    }

    /** The sound of the blow connecting: a blade's ring, or a dull thud for a punch. */
    public static @Nullable SoundEvent hitFor(PlannedSwing swing) {
        return switch (weightOf(swing)) {
            case LIGHT -> HIT_LIGHT.get();
            case HEAVY -> HIT_HEAVY.get();
            case QUICK -> HIT_QUICK.get();
            case BLUNT -> HIT_BLUNT.get();
        };
    }

    private static Weight weightOf(PlannedSwing swing) {
        return switch (swing.profile().moveset()) {
            case SWING -> swing.grip() == Grip.TWO_HANDED ? Weight.HEAVY : Weight.LIGHT;
            case CHOP, AERIAL -> Weight.HEAVY;
            case THRUST -> Weight.QUICK;
            case FIST -> Weight.BLUNT;
        };
    }

    private static RegistrySupplier<SoundEvent> register(String name) {
        Identifier id = Identifier.of(CleanCombos.MOD_ID, name);
        return SOUNDS.register(id, () -> SoundEvent.of(id));
    }
}
