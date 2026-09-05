package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.DirectionArgumentType;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public class RotationCommand extends Command {
   public RotationCommand() {
      super("rotation", "Modifies your rotation.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      ((LiteralArgumentBuilder)builder.then(
            ((LiteralArgumentBuilder)literal("set").then(argument("direction", DirectionArgumentType.create()).executes(context -> {
               mc.player.setXRot((float)(((Direction)context.getArgument("direction", Direction.class)).getNormal().getY() * -90));
               mc.player.setYRot(((Direction)context.getArgument("direction", Direction.class)).toYRot());
               return 1;
            }))).then(((RequiredArgumentBuilder)argument("pitch", FloatArgumentType.floatArg(-90.0F, 90.0F)).executes(context -> {
               mc.player.setXRot((Float)context.getArgument("pitch", Float.class));
               return 1;
            })).then(argument("yaw", FloatArgumentType.floatArg(-180.0F, 180.0F)).executes(context -> {
               mc.player.setXRot((Float)context.getArgument("pitch", Float.class));
               mc.player.setYRot((Float)context.getArgument("yaw", Float.class));
               return 1;
            })))
         ))
         .then(literal("add").then(((RequiredArgumentBuilder)argument("pitch", FloatArgumentType.floatArg(-90.0F, 90.0F)).executes(context -> {
            float pitch = mc.player.getXRot() + (Float)context.getArgument("pitch", Float.class);
            mc.player.setXRot(pitch >= 0.0F ? Math.min(pitch, 90.0F) : Math.max(pitch, -90.0F));
            return 1;
         })).then(argument("yaw", FloatArgumentType.floatArg(-180.0F, 180.0F)).executes(context -> {
            float pitch = mc.player.getXRot() + (Float)context.getArgument("pitch", Float.class);
            mc.player.setXRot(pitch >= 0.0F ? Math.min(pitch, 90.0F) : Math.max(pitch, -90.0F));
            float yaw = mc.player.getYRot() + (Float)context.getArgument("yaw", Float.class);
            mc.player.setYRot(Mth.wrapDegrees(yaw));
            return 1;
         }))));
   }
}
