package dev.worldprogression.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.worldprogression.arrival.ArrivalClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Iris reads this flag for both its solid and translucent hand passes. */
@Mixin(GameRenderer.class)
public abstract class ArrivalWorldHandsMixin {
    @Shadow private boolean renderHand;

    @WrapMethod(method = "renderWorld")
    private void arrival$worldHands(RenderTickCounter ticks, Operation<Void> original) {
        boolean hideVanillaHands = ArrivalClient.blocking()
                || dev.worldprogression.foundry.FoundryClient.hands();
        if (!hideVanillaHands) {
            original.call(ticks);
            return;
        }
        boolean previous = renderHand;
        renderHand = false;
        try {
            // Cinematic/workshop arms are world geometry and remain visible with this flag off.
            original.call(ticks);
        } finally {
            // Includes skipping, interruption and failed rendering; never leave hands hidden.
            renderHand = previous;
        }
    }
}
