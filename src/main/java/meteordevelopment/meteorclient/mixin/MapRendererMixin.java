package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.NoRender;
import meteordevelopment.meteorclient.utils.misc.EmptyIterator;
import net.minecraft.client.gui.MapRenderer.MapInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({MapInstance.class})
public abstract class MapRendererMixin {
   @ModifyExpressionValue(
      method = {"draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ZI)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/item/map/MapState;getDecorations()Ljava/lang/Iterable;"
      )}
   )
   private Iterable<MapDecoration> getIconsProxy(Iterable<MapDecoration> original) {
      return Modules.get().get(NoRender.class).noMapMarkers() ? EmptyIterator::new : original;
   }

   @Inject(
      method = {"draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ZI)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onDraw(PoseStack matrices, MultiBufferSource vertexConsumers, boolean hidePlayerIcons, int light, CallbackInfo ci) {
      if (Modules.get().get(NoRender.class).noMapContents()) {
         ci.cancel();
      }
   }
}
