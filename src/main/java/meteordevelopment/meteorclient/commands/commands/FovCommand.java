package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.mixininterface.ISimpleOption;
import net.minecraft.commands.SharedSuggestionProvider;

public class FovCommand extends Command {
   public FovCommand() {
      super("fov", "Changes your fov.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(argument("fov", IntegerArgumentType.integer(0, 180)).executes(context -> {
         ((ISimpleOption)(Object)mc.options.fov()).set(context.getArgument("fov", Integer.class));
         return 1;
      }));
   }
}
