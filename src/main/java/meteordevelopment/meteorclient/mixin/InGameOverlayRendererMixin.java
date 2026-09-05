package meteordevelopment.meteorclient.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ScreenEffectRenderer.class})
public abstract class InGameOverlayRendererMixin {
   @Inject(
      method = {"renderFireOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderFireOverlay(Minecraft minecraftClient, PoseStack matrixStack, CallbackInfo info) {
      if (Modules.get().get(NoRender.class).noFireOverlay()) {
         info.cancel();
      }
   }

   @Inject(
      method = {"renderUnderwaterOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderUnderwaterOverlay(Minecraft minecraftClient, PoseStack matrixStack, CallbackInfo info) {
      if (Modules.get().get(NoRender.class).noLiquidOverlay() || Modules.get().isActive(Freecam.class)) {
         info.cancel();
      }
   }

   @Inject(
      method = {"renderInWallOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void render(TextureAtlasSprite sprite, PoseStack matrices, CallbackInfo info) {
      if (Modules.get().get(NoRender.class).noInWallOverlay() || Modules.get().isActive(Freecam.class)) {
         info.cancel();
      }
   }
}
