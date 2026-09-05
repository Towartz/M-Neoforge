package meteordevelopment.meteorclient.mixin.sodium;

import java.util.Arrays;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Ambience;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.caffeinemc.mods.sodium.api.util.ColorABGR;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   targets = {"net/caffeinemc/mods/sodium/fabric/render/FluidRendererImpl$DefaultRenderContext"},
   remap = false
)
public abstract class SodiumFluidRendererImplDefaultRenderContextMixin {
   @Unique
   private Ambience ambience;

   @Inject(
      method = {"<init>"},
      at = {@At("TAIL")}
   )
   private void onInit(CallbackInfo info) {
      this.ambience = Modules.get().get(Ambience.class);
   }

   @Inject(
      method = {"getColorProvider"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetColorProvider(Fluid fluid, CallbackInfoReturnable<ColorProvider<FluidState>> info) {
      if (this.ambience.isActive() && this.ambience.customLavaColor.get() && fluid.defaultFluidState().is(FluidTags.LAVA)) {
         info.setReturnValue(this::lavaColorProvider);
      }
   }

   @Unique
   private void lavaColorProvider(LevelSlice level, BlockPos pos, MutableBlockPos posMutable, FluidState state, ModelQuadView quads, int[] colors) {
      Color c = this.ambience.lavaColor.get();
      Arrays.fill(colors, ColorABGR.pack(c.r, c.g, c.b, c.a));
   }
}
