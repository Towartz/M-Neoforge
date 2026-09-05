package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.WaypointArgumentType;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;

public class WaypointCommand extends Command {
   public WaypointCommand() {
      super("waypoint", "Manages waypoints.", "wp");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(
         literal("list")
            .executes(
               context -> {
                  if (Waypoints.get().isEmpty()) {
                     this.error("No created waypoints.", new Object[0]);
                  } else {
                     this.info(ChatFormatting.WHITE + "Created Waypoints:", new Object[0]);

                     for (Waypoint waypoint : Waypoints.get()) {
                        this.info(
                           "Name: (highlight)'%s'(default), Dimension: (highlight)%s(default), Pos: (highlight)%s(default)",
                           new Object[]{waypoint.name.get(), waypoint.dimension.get(), this.waypointPos(waypoint)}
                        );
                     }
                  }

                  return 1;
               }
            )
      );
      builder.then(literal("get").then(argument("waypoint", WaypointArgumentType.create()).executes(context -> {
         Waypoint waypoint = WaypointArgumentType.get(context);
         this.info("Name: " + ChatFormatting.WHITE + waypoint.name.get(), new Object[0]);
         this.info("Actual Dimension: " + ChatFormatting.WHITE + waypoint.dimension.get(), new Object[0]);
         this.info("Position: " + ChatFormatting.WHITE + this.waypointFullPos(waypoint), new Object[0]);
         this.info("Visible: " + (waypoint.visible.get() ? ChatFormatting.GREEN + "True" : ChatFormatting.RED + "False"), new Object[0]);
         return 1;
      })));
      builder.then(
         ((LiteralArgumentBuilder)literal("add")
               .then(
                  argument("pos", Vec3Argument.vec3())
                     .then(argument("waypoint", StringArgumentType.greedyString()).executes(context -> this.addWaypoint(context, true)))
               ))
            .then(argument("waypoint", StringArgumentType.greedyString()).executes(context -> this.addWaypoint(context, false)))
      );
      builder.then(literal("delete").then(argument("waypoint", WaypointArgumentType.create()).executes(context -> {
         Waypoint waypoint = WaypointArgumentType.get(context);
         this.info("The waypoint (highlight)'%s'(default) has been deleted.", new Object[]{waypoint.name.get()});
         Waypoints.get().remove(waypoint);
         return 1;
      })));
      builder.then(literal("toggle").then(argument("waypoint", WaypointArgumentType.create()).executes(context -> {
         Waypoint waypoint = WaypointArgumentType.get(context);
         waypoint.visible.set(!waypoint.visible.get());
         Waypoints.get().save();
         return 1;
      })));
   }

   private String waypointPos(Waypoint waypoint) {
      return "X: " + waypoint.pos.get().getX() + " Z: " + waypoint.pos.get().getZ();
   }

   private String waypointFullPos(Waypoint waypoint) {
      return "X: " + waypoint.pos.get().getX() + ", Y: " + waypoint.pos.get().getY() + ", Z: " + waypoint.pos.get().getZ();
   }

   private int addWaypoint(CommandContext<SharedSuggestionProvider> context, boolean withCoords) {
      if (mc.player == null) {
         return -1;
      } else {
         BlockPos pos = withCoords
            ? ((Coordinates)context.getArgument("pos", Coordinates.class)).getBlockPos(mc.player.createCommandSourceStack())
            : mc.player.blockPosition().above(2);
         Waypoint waypoint = new Waypoint.Builder()
            .name(StringArgumentType.getString(context, "waypoint"))
            .pos(pos)
            .dimension(PlayerUtils.getDimension())
            .build();
         Waypoints.get().add(waypoint);
         this.info("Created waypoint with name: (highlight)%s(default)", new Object[]{waypoint.name.get()});
         return 1;
      }
   }
}
