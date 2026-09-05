package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {
    @Invoker("setupRotations")
    void meteor$setupRotations(LivingEntity entity, PoseStack poseStack, float progress, float bodyYaw, float tickDelta, float scale);

    @Invoker("scale")
    void meteor$scale(LivingEntity entity, PoseStack poseStack, float tickDelta);

    @Invoker("getBob")
    float meteor$getBob(LivingEntity entity, float tickDelta);
}
