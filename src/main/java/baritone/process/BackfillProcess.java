package baritone.process;

import baritone.Baritone;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.input.Input;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.MovementState;
import baritone.pathing.path.PathExecutor;
import baritone.utils.BaritoneProcessHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;

public final class BackfillProcess extends BaritoneProcessHelper {
   public HashMap<BlockPos, BlockState> blocksToReplace = new HashMap<>();

   public BackfillProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public boolean isActive() {
      if (this.ctx.player() != null && this.ctx.world() != null) {
         if (!Baritone.settings().backfill.value) {
            return false;
         } else if (Baritone.settings().allowParkour.value) {
            this.logDirect("Backfill cannot be used with allowParkour true");
            Baritone.settings().backfill.value = false;
            return false;
         } else {
            for (BlockPos pos : new ArrayList<>(this.blocksToReplace.keySet())) {
               if (this.ctx.world().getChunk(pos) instanceof EmptyLevelChunk || this.ctx.world().getBlockState(pos).getBlock() != Blocks.AIR) {
                  this.blocksToReplace.remove(pos);
               }
            }

            this.amIBreakingABlockHMMMMMMM();
            this.baritone.getInputOverrideHandler().clearAllKeys();
            return !this.toFillIn().isEmpty();
         }
      } else {
         return false;
      }
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      if (!isSafeToCancel) {
         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      } else {
         this.baritone.getInputOverrideHandler().clearAllKeys();

         for (BlockPos toPlace : this.toFillIn()) {
            MovementState fake = new MovementState();
            switch (MovementHelper.attemptToPlaceABlock(fake, this.baritone, toPlace, false, false)) {
               case NO_OPTION:
                  break;
               case READY_TO_PLACE:
                  this.baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
                  return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
               case ATTEMPTING:
                  this.baritone.getLookBehavior().updateTarget(fake.getTarget().getRotation().get(), true);
                  return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
               default:
                  throw new IllegalStateException();
            }
         }

         return new PathingCommand(null, PathingCommandType.DEFER);
      }
   }

   private void amIBreakingABlockHMMMMMMM() {
      if (this.ctx.getSelectedBlock().isPresent() && this.baritone.getPathingBehavior().isPathing()) {
         this.blocksToReplace.put(this.ctx.getSelectedBlock().get(), this.ctx.world().getBlockState(this.ctx.getSelectedBlock().get()));
      }
   }

   public List<BlockPos> toFillIn() {
      return this.blocksToReplace
         .keySet()
         .stream()
         .filter(pos -> this.ctx.world().getBlockState(pos).getBlock() == Blocks.AIR)
         .filter(pos -> this.baritone.getBuilderProcess().placementPlausible(pos, Blocks.DIRT.defaultBlockState()))
         .filter(pos -> !this.partOfCurrentMovement(pos))
         .sorted(Comparator.comparingDouble(this.ctx.playerFeet()::distSqr).reversed())
         .collect(Collectors.toList());
   }

   private boolean partOfCurrentMovement(BlockPos pos) {
      PathExecutor exec = this.baritone.getPathingBehavior().getCurrent();
      if (exec != null && !exec.finished() && !exec.failed()) {
         Movement movement = (Movement)exec.getPath().movements().get(exec.getPosition());
         return Arrays.asList(movement.toBreakAll()).contains(pos);
      } else {
         return false;
      }
   }

   @Override
   public void onLostControl() {
      if (this.blocksToReplace != null && !this.blocksToReplace.isEmpty()) {
         this.blocksToReplace.clear();
      }
   }

   @Override
   public String displayName0() {
      return "Backfill";
   }

   @Override
   public boolean isTemporary() {
      return true;
   }

   @Override
   public double priority() {
      return 5.0;
   }
}
