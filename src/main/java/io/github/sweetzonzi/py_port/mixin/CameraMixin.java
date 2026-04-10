package io.github.sweetzonzi.py_port.mixin;

import io.github.sweetzonzi.py_port.config.CameraConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private Vec3 position;
    @Shadow private float xRot;
    @Shadow private float yRot;

    @Inject(method = "setup", at = @At("TAIL"))
    private void onSetup(CallbackInfo ci) {
        // 从共享配置读取
        if (!CameraConfig.enableOverhead) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 强制第三人称背面
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        Vec3 pos = mc.player.position();
        this.position = new Vec3(
                pos.x,
                pos.y + CameraConfig.overheadHeight,  // 使用配置的高度
                pos.z
        );

        this.yRot = mc.player.getYRot();
        this.xRot = 89.9F;
    }
}