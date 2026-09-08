package baritone.launch.mixins;

import baritone.api.utils.BlockOptionalMeta;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerRegistries.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext.Builder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({Builder.class})
public abstract class MixinLootContextBuilder {
   @Shadow
   public abstract ServerLevel getLevel();

   @Redirect(
      method = {"create"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/MinecraftServer;reloadableRegistries()Lnet/minecraft/server/ReloadableServerRegistries$Holder;"
      )
   )
   private Holder create(MinecraftServer instance) {
      if (instance != null) {
         return instance.reloadableRegistries();
      } else {
         return null;
      }
   }
}
