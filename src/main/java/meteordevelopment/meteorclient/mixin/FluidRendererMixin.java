package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LiquidBlockRenderer.class})
public abstract class FluidRendererMixin {
   @Unique
   private final ThreadLocal<Integer> alphas = new ThreadLocal<>();

   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRender(BlockAndTintGetter world, BlockPos pos, VertexConsumer vertexConsumer, BlockState blockState, FluidState fluidState, CallbackInfo info) {
      Ambience ambience = Modules.get().get(Ambience.class);
      if (ambience.isActive() && ambience.customLavaColor.get() && fluidState.is(FluidTags.LAVA)) {
         this.alphas.set(-2);
      } else {
         int alpha = Xray.getAlpha(fluidState.createLegacyBlock(), pos);
         if (alpha == 0) {
            info.cancel();
         } else {
            this.alphas.set(alpha);
         }
      }
   }

   @Inject(
      method = {"vertex"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onVertex(
      VertexConsumer vertexConsumer, float x, float y, float z, float red, float green, float blue, float u, float v, int light, CallbackInfo info
   ) {
      int customAlpha = this.alphas.get();
      if (customAlpha == -2) {
         Color color = Modules.get().get(Ambience.class).lavaColor.get();
         this.vertex(vertexConsumer, x, y, z, color.r, color.g, color.b, color.a, u, v, light);
         info.cancel();
      } else if (customAlpha != -1) {
         this.vertex(vertexConsumer, x, y, z, (int)(red * 255.0F), (int)(green * 255.0F), (int)(blue * 255.0F), customAlpha, u, v, light);
         info.cancel();
      }
   }

   @Unique
   private void vertex(VertexConsumer vertexConsumer, float x, float y, float z, int red, int green, int blue, int alpha, float u, float v, int light) {
      vertexConsumer.addVertex(x, y, z).setColor(red, green, blue, alpha).setUv(u, v).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
   }
}
