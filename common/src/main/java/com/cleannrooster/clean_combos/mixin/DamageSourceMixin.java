package com.cleannrooster.clean_combos.mixin;

import com.cleannrooster.clean_combos.api.CleanCombosApi;
import com.cleannrooster.clean_combos.api.StrikeInfo;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DamageSource.class)
public abstract class DamageSourceMixin {

    /**
     * A death by a Clean Combos hit reads by weapon type ("was run through by" for a rapier, "was hammered flat
     * by" for a hammer). Only while that hit resolves and only for its own victim: every other death keeps
     * vanilla's message. Like vanilla, a named weapon adds "using [weapon]".
     */
    @Inject(method = "getDeathMessage", at = @At("HEAD"), cancellable = true)
    private void clean_combos$weaponDeathMessage(LivingEntity killed, CallbackInfoReturnable<Text> cir) {
        StrikeInfo strike = CleanCombosApi.currentStrike();
        DamageSource self = (DamageSource) (Object) this;
        if (strike == null || strike.target() != killed || self.getAttacker() != strike.attack().attacker()) {
            return;
        }
        String key = "death.clean_combos." + strike.attack().weaponType().id();
        ItemStack weapon = strike.attack().weapon();
        Text killer = strike.attack().attacker().getDisplayName();
        if (!weapon.isEmpty() && weapon.contains(DataComponentTypes.CUSTOM_NAME)) {
            cir.setReturnValue(Text.translatable(key + ".item", killed.getDisplayName(), killer,
                weapon.toHoverableText()));
        } else {
            cir.setReturnValue(Text.translatable(key, killed.getDisplayName(), killer));
        }
    }
}
