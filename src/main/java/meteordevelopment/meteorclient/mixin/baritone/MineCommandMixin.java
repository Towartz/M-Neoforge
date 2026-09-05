package meteordevelopment.meteorclient.mixin.baritone;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.utils.BlockOptionalMeta;
import baritone.command.defaults.MineCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import meteordevelopment.meteorclient.utils.world.OreDiscovery;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {MineCommand.class}, remap = false)
public abstract class MineCommandMixin {
   @Shadow(remap = false)
   @Final
   protected IBaritone baritone;

   @Inject(
      method = {"execute"},
      at = {@At("HEAD")},
      cancellable = true,
      remap = false
   )
   private void onExecute(String label, IArgConsumer args, CallbackInfo ci) {
      if (args.hasAny()) {
         try {
            String first = args.peekString();
            int max = 0;
            boolean hasCount = false;
            try {
               max = Integer.parseInt(first);
               hasCount = true;
            } catch (NumberFormatException ignored) {
            }

            String targetArg = hasCount && args.has(2) ? args.peekString(1) : first;
            if (targetArg != null) {
               String lower = targetArg.trim().toLowerCase(Locale.ROOT);
               if (lower.equals("ores") || lower.equals("ore")) {
                  ci.cancel();
                  List<Block> ores = OreDiscovery.getOres();
                  List<BlockOptionalMeta> list = new ArrayList<>(ores.size());
                  for (Block ore : ores) {
                     list.add(new BlockOptionalMeta(ore));
                  }
                  BaritoneAPI.getProvider().getWorldScanner().repack(this.baritone.getPlayerContext());
                  this.baritone.getMineProcess().mine(max, list.toArray(new BlockOptionalMeta[0]));
               }
            }
         } catch (Throwable ignored) {
         }
      }
   }
}
