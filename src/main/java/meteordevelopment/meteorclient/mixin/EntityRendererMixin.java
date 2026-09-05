package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.mixininterface.IEntityRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Fullbright;
import meteordevelopment.meteorclient.systems.modules.render.Nametags;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({EntityRenderer.class})
public abstract class EntityRendererMixin<T extends Entity> implements IEntityRenderer {
   @Shadow
   public abstract ResourceLocation getTextureLocation(Entity var1);

   @Inject(
      method = {"renderNameTag"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderLabel(T entity, Component text, PoseStack matrices, MultiBufferSource vertexConsumers, int light, float tickDelta, CallbackInfo ci) {
      if (PostProcessShaders.rendering) {
         ci.cancel();
      }

      if (Modules.get().get(NoRender.class).noNametags()) {
         ci.cancel();
      }

      if (entity instanceof Player) {
         if (Modules.get().get(Nametags.class).playerNametags()
            && (EntityUtils.getGameMode((Player)entity) != null || !Modules.get().get(Nametags.class).excludeBots())) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"shouldRender"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void shouldRender(T entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
      if (Modules.get().get(NoRender.class).noEntity(entity)) {
         cir.setReturnValue(false);
      } else if (Modules.get().get(NoRender.class).noFallingBlocks() && entity instanceof FallingBlockEntity) {
         cir.setReturnValue(false);
      }
   }

   @ModifyReturnValue(
      method = {"getSkyLightLevel"},
      at = {@At("RETURN")}
   )
   private int onGetSkyLight(int original) {
      return Math.max(Modules.get().get(Fullbright.class).getLuminance(LightLayer.SKY), original);
   }

   @ModifyReturnValue(
      method = {"getBlockLightLevel"},
      at = {@At("RETURN")}
   )
   private int onGetBlockLight(int original) {
      return Math.max(Modules.get().get(Fullbright.class).getLuminance(LightLayer.BLOCK), original);
   }

   @Override
   public ResourceLocation getTextureInterface(Entity entity) {
      return this.getTextureLocation(entity);
   }
}
