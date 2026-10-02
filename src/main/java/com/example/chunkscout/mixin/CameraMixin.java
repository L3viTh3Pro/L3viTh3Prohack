package com.example.chunkscout.mixin;

import com.example.chunkscout.ChunkScoutClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void chunkscout$freecam(net.minecraft.world.World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        if (!ChunkScoutClient.isFreecam()) return;
        Vec3d p = ChunkScoutClient.getFreecamPos();
        setPos(p.x, p.y, p.z);
        setRotation(ChunkScoutClient.getFreecamYaw(), ChunkScoutClient.getFreecamPitch());
    }
}
