package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IFollowProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.utils.BaritoneProcessHelper;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

public final class FollowProcess extends BaritoneProcessHelper implements IFollowProcess {
   private Predicate<Entity> filter;
   private List<Entity> cache;
   private boolean into;

   public FollowProcess(Baritone baritone) {
      super(baritone);
   }

   @Override
   public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
      this.scanWorld();
      Goal goal = new GoalComposite(this.cache.stream().map(this::towards).toArray(Goal[]::new));
      return new PathingCommand(goal, PathingCommandType.REVALIDATE_GOAL_AND_PATH);
   }

   private Goal towards(Entity following) {
      BlockPos pos;
      if (Baritone.settings().followOffsetDistance.value != 0.0 && !this.into) {
         GoalXZ g = GoalXZ.fromDirection(following.position(), Baritone.settings().followOffsetDirection.value, Baritone.settings().followOffsetDistance.value);
         pos = new BetterBlockPos((double)g.getX(), following.position().y, (double)g.getZ());
      } else {
         pos = following.blockPosition();
      }

      return (Goal)(this.into ? new GoalBlock(pos) : new GoalNear(pos, Baritone.settings().followRadius.value));
   }

   private boolean followable(Entity entity) {
      if (entity == null) {
         return false;
      } else if (!entity.isAlive()) {
         return false;
      } else if (entity.equals(this.ctx.player())) {
         return false;
      } else {
         int maxDist = Baritone.settings().followTargetMaxDistance.value;
         return maxDist != 0 && entity.distanceToSqr(this.ctx.player()) > (double)(maxDist * maxDist)
            ? false
            : this.ctx.entitiesStream().anyMatch(entity::equals);
      }
   }

   private void scanWorld() {
      this.cache = this.ctx.entitiesStream().filter(this::followable).filter(this.filter).distinct().collect(Collectors.toList());
   }

   @Override
   public boolean isActive() {
      if (this.filter == null) {
         return false;
      } else {
         this.scanWorld();
         return !this.cache.isEmpty();
      }
   }

   @Override
   public void onLostControl() {
      this.filter = null;
      this.cache = null;
   }

   @Override
   public String displayName0() {
      return "Following " + this.cache;
   }

   @Override
   public void follow(Predicate<Entity> filter) {
      this.filter = filter;
      this.into = false;
   }

   @Override
   public void pickup(Predicate<ItemStack> filter) {
      this.filter = e -> e instanceof ItemEntity && filter.test(((ItemEntity)e).getItem());
      this.into = true;
   }

   @Override
   public List<Entity> following() {
      return this.cache;
   }

   @Override
   public Predicate<Entity> currentFilter() {
      return this.filter;
   }
}
