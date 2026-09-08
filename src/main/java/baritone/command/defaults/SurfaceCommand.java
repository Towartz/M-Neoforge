package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.utils.BetterBlockPos;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.level.block.AirBlock;

public class SurfaceCommand extends Command {
   protected SurfaceCommand(IBaritone baritone) {
      super(baritone, "surface", "top");
   }

   @Override
   public void execute(String label, IArgConsumer args) throws CommandException {
      BetterBlockPos playerPos = this.ctx.playerFeet();
      int surfaceLevel = this.ctx.world().getSeaLevel();
      int worldHeight = this.ctx.world().getHeight();
      if (playerPos.getY() > surfaceLevel && this.ctx.world().getBlockState(playerPos.above()).getBlock() instanceof AirBlock) {
         this.logDirect("Already at surface");
      } else {
         int startingYPos = Math.max(playerPos.getY(), surfaceLevel);

         for (int currentIteratedY = startingYPos; currentIteratedY < worldHeight; currentIteratedY++) {
            BetterBlockPos newPos = new BetterBlockPos(playerPos.getX(), currentIteratedY, playerPos.getZ());
            if (!(this.ctx.world().getBlockState(newPos).getBlock() instanceof AirBlock) && newPos.getY() > playerPos.getY()) {
               Goal goal = new GoalBlock(newPos.above());
               this.logDirect(String.format("Going to: %s", goal.toString()));
               this.baritone.getCustomGoalProcess().setGoalAndPath(goal);
               return;
            }
         }

         this.logDirect("No higher location found");
      }
   }

   @Override
   public Stream<String> tabComplete(String label, IArgConsumer args) {
      return Stream.empty();
   }

   @Override
   public String getShortDesc() {
      return "Used to get out of caves, mines, ...";
   }

   @Override
   public List<String> getLongDesc() {
      return Arrays.asList(
         "The surface/top command tells Baritone to head towards the closest surface-like area.",
         "",
         "This can be the surface or the highest available air space, depending on circumstances.",
         "",
         "Usage:",
         "> surface - Used to get out of caves, mines, ...",
         "> top - Used to get out of caves, mines, ..."
      );
   }
}
