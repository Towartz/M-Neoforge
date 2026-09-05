package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.GotoSurface;
import net.minecraft.commands.SharedSuggestionProvider;

public class SurfaceCommand extends Command {
   public SurfaceCommand() {
      super("surface", "Smartly escapes caves and navigates to the surface.", "escape", "gotosurface");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.executes(context -> {
         GotoSurface module = Modules.get().get(GotoSurface.class);
         if (module != null) {
            if (!module.isActive()) {
               module.toggle();
            } else {
               this.info("Goto Surface is already active!");
            }
         }
         return 1;
      });
   }
}
