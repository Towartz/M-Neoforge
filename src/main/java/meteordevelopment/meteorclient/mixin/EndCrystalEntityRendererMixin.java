package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Chams;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin({EndCrystalRenderer.class})
public abstract class EndCrystalEntityRendererMixin {
   @Mutable
   @Shadow
   @Final
   private static RenderType RENDER_TYPE;
   @Shadow
   @Final
   private static ResourceLocation END_CRYSTAL_LOCATION;
   @Shadow
   @Final
   public ModelPart cube;
   @Shadow
   @Final
   public ModelPart glass;

   @Inject(
      method = {"render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = {@At("HEAD")}
   )
   private void render(EndCrystal endCrystalEntity, float f, float g, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i, CallbackInfo ci) {
      Chams module = Modules.get().get(Chams.class);
      RENDER_TYPE = RenderType.entityTranslucent(
         module.isActive() && module.crystals.get() && !module.crystalsTexture.get() ? Chams.BLANK : END_CRYSTAL_LOCATION
      );
   }

   @ModifyArgs(
      method = {"render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",
         ordinal = 0
      )
   )
   private void modifyScale(Args args) {
      Chams module = Modules.get().get(Chams.class);
      if (module.isActive() && module.crystals.get()) {
         args.set(0, 2.0F * module.crystalsScale.get().floatValue());
         args.set(1, 2.0F * module.crystalsScale.get().floatValue());
         args.set(2, 2.0F * module.crystalsScale.get().floatValue());
      }
   }

   @Redirect(
      method = {"render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/entity/EndCrystalRenderer;getY(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;F)F"
      )
   )
   private float getYOff(EndCrystal crystal, float tickDelta) {
      Chams module = Modules.get().get(Chams.class);
      if (module.isActive() && module.crystals.get()) {
         float f = (float)crystal.time + tickDelta;
         float g = Mth.sin(f * 0.2F) / 2.0F + 0.5F;
         g = (g * g + g) * 0.4F * module.crystalsBounce.get().floatValue();
         return g - 1.4F;
      } else {
         return EndCrystalRenderer.getY(crystal, tickDelta);
      }
   }

   @ModifyArgs(
      method = {"render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"
      )
   )
   private void modifySpeed(Args args) {
      Chams module = Modules.get().get(Chams.class);
      if (module.isActive() && module.crystals.get()) {
         args.set(0, (Float)args.get(0) * module.crystalsRotationSpeed.get().floatValue());
      }
   }

   @Redirect(
      method = {"render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
         ordinal = 3
      )
   )
   private void modifyCore(ModelPart modelPart, PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
      Chams module = Modules.get().get(Chams.class);
      if (module.isActive() && module.crystals.get()) {
         if (module.renderCore.get()) {
            Color color = module.crystalsCoreColor.get();
            this.cube.render(matrices, vertices, light, overlay, color.getPacked());
         }
      } else {
         this.cube.render(matrices, vertices, light, overlay);
      }
   }

   @Redirect(
      method = {"render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
         ordinal = 1
      )
   )
   private void modifyFrame1(ModelPart modelPart, PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
      Chams module = Modules.get().get(Chams.class);
      if (module.isActive() && module.crystals.get()) {
         if (module.renderFrame1.get()) {
            Color color = module.crystalsFrame1Color.get();
            this.glass.render(matrices, vertices, light, overlay, color.getPacked());
         }
      } else {
         this.glass.render(matrices, vertices, light, overlay);
      }
   }

   @Redirect(
      method = {"render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
         ordinal = 2
      )
   )
   private void modifyFrame2(ModelPart modelPart, PoseStack matrices, VertexConsumer vertices, int light, int overlay) {
      Chams module = Modules.get().get(Chams.class);
      if (module.isActive() && module.crystals.get()) {
         if (module.renderFrame2.get()) {
            Color color = module.crystalsFrame2Color.get();
            this.glass.render(matrices, vertices, light, overlay, color.getPacked());
         }
      } else {
         this.glass.render(matrices, vertices, light, overlay);
      }
   }
}
