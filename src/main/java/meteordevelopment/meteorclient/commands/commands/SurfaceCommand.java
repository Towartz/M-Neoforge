package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.GotoSurface;
import net.minecraft.commands.SharedSuggestionProvider;

public class SurfaceCommand extends Command {
   public SurfaceCommand() {
      super("surface", "Smartly escapes caves or navigates to target coordinates on the surface with auto chunk height calculation.", "escape", "gotosurface");
   }

   private static int parseCoordinate(String arg, double current) {
      if (arg.startsWith("~")) {
         if (arg.length() == 1) return (int)Math.floor(current);
         return (int)Math.floor(current + Double.parseDouble(arg.substring(1)));
      }
      return (int)Math.floor(Double.parseDouble(arg));
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.executes(context -> {
         GotoSurface module = Modules.get().get(GotoSurface.class);
         if (module != null) {
            module.clearTarget();
            if (!module.isActive()) {
               module.toggle();
            } else {
               this.info("Goto Surface is already active! Use .surface stop to cancel.");
            }
         }
         return 1;
      });

      builder.then(literal("stop").executes(context -> {
         GotoSurface module = Modules.get().get(GotoSurface.class);
         if (module != null && module.isActive()) {
            module.toggle();
            this.info("Stopped Goto Surface.");
         } else {
            this.info("Goto Surface is not active.");
         }
         return 1;
      }));

      builder.then(argument("xOrY", StringArgumentType.string())
         .executes(context -> {
            if (this.mc.player == null) return 0;
            String yStr = context.getArgument("xOrY", String.class);

            try {
               int targetY = parseCoordinate(yStr, this.mc.player.getY());
               GotoSurface module = Modules.get().get(GotoSurface.class);
               if (module != null) {
                  if (module.isActive()) module.toggle();
                  module.setTargetY(targetY);
                  module.toggle();
                  this.info("Starting surface ascent to elevation Y=%d.", targetY);
               }
            } catch (NumberFormatException e) {
               this.error("Invalid Y coordinate '%s'. Use a number or ~ notation (e.g. ~100).", yStr);
            }
            return 1;
         })
         .then(argument("zOrY", StringArgumentType.string())
            .executes(context -> {
               if (this.mc.player == null) return 0;
               String xStr = context.getArgument("xOrY", String.class);
               String zStr = context.getArgument("zOrY", String.class);

               try {
                  int targetX = parseCoordinate(xStr, this.mc.player.getX());
                  int targetZ = parseCoordinate(zStr, this.mc.player.getZ());

                  GotoSurface module = Modules.get().get(GotoSurface.class);
                  if (module != null) {
                     if (module.isActive()) module.toggle();
                     module.setTarget(targetX, targetZ);
                     module.toggle();
                     this.info("Starting surface navigation to [%d, %d] with dynamic chunk heightmap.", targetX, targetZ);
                  }
               } catch (NumberFormatException e) {
                  this.error("Invalid coordinates '%s %s'. Use numbers or ~ notation (e.g. ~100 ~).", xStr, zStr);
               }
               return 1;
            })
            .then(argument("z", StringArgumentType.string())
               .executes(context -> {
                  if (this.mc.player == null) return 0;
                  String xStr = context.getArgument("xOrY", String.class);
                  String yStr = context.getArgument("zOrY", String.class);
                  String zStr = context.getArgument("z", String.class);

                  try {
                     int targetX = parseCoordinate(xStr, this.mc.player.getX());
                     int targetY = parseCoordinate(yStr, this.mc.player.getY());
                     int targetZ = parseCoordinate(zStr, this.mc.player.getZ());

                     GotoSurface module = Modules.get().get(GotoSurface.class);
                     if (module != null) {
                        if (module.isActive()) module.toggle();
                        module.setTarget(targetX, targetY, targetZ);
                        module.toggle();
                        this.info("Starting navigation to [%d, %d, %d].", targetX, targetY, targetZ);
                     }
                  } catch (NumberFormatException e) {
                     this.error("Invalid coordinates '%s %s %s'. Use numbers or ~ notation (e.g. ~ ~100 ~).", xStr, yStr, zStr);
                  }
                  return 1;
               })
            )
         )
      );
   }
}
