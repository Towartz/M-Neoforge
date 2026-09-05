package meteordevelopment.meteorclient.mixininterface;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;

public interface ILivingEntityRenderer {
   void setupTransformsInterface(LivingEntity var1, PoseStack var2, float var3, float var4, float var5);

   void scaleInterface(LivingEntity var1, PoseStack var2, float var3);

   boolean isVisibleInterface(LivingEntity var1);

   float getAnimationCounterInterface(LivingEntity var1, float var2);
}
