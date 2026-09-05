package meteordevelopment.meteorclient.mixin.baritone;

import baritone.api.utils.BlockUtils;
import java.util.Locale;
import meteordevelopment.meteorclient.utils.world.OreDiscovery;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = {BlockUtils.class}, remap = false)
public abstract class BaritoneBlockUtilsMixin {
   @Inject(
      method = {"stringToBlockNullable"},
      at = {@At("RETURN")},
      cancellable = true,
      remap = false
   )
   private static void onStringToBlockNullable(String name, CallbackInfoReturnable<Block> cir) {
      if (cir.getReturnValue() == null && name != null) {
         String clean = name.trim().toLowerCase(Locale.ROOT);
         if (clean.startsWith("minecraft:")) {
            clean = clean.substring(10);
         }

         Block ore = OreDiscovery.findOre(clean);
         if (ore != null) {
            cir.setReturnValue(ore);
            return;
         }

         for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id != null && id.getPath().equalsIgnoreCase(clean)) {
               cir.setReturnValue(block);
               return;
            }
         }
      }
   }
}
