package dev.worldprogression.mixin;

import dev.worldprogression.foundry.FoundryClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps the work sequence unobstructed while first-person tools are animated. */
@Mixin(InGameHud.class)
public abstract class FoundryHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void foundry$cinematicHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (FoundryClient.cinematic()) ci.cancel();
    }
}
