package com.example.chunkscout.mixin;

import com.example.chunkscout.ChunkScoutClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow private double cursorDeltaX;
    @Shadow private double cursorDeltaY;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void chunkscout$freecamMouse(double timeDelta, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ChunkScoutClient.freecam || client.currentScreen != null || client.player == null) return;

        double sensitivity = client.options.getMouseSensitivity().getValue();
        double factor = sensitivity * 0.6 + 0.2;
        factor = factor * factor * factor * 8.0;

        ChunkScoutClient.camYaw += (float)(cursorDeltaX * factor * 0.15);
        ChunkScoutClient.camPitch += (float)(cursorDeltaY * factor * 0.15);
        ChunkScoutClient.camPitch = Math.max(-90.0f, Math.min(90.0f, ChunkScoutClient.camPitch));

        cursorDeltaX = 0.0;
        cursorDeltaY = 0.0;
        ci.cancel();
    }
}
