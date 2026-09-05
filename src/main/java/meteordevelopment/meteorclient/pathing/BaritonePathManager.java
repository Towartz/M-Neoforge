package meteordevelopment.meteorclient.pathing;

import baritone.api.BaritoneAPI;
import baritone.api.behavior.ILookBehavior;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Rotation;
import baritone.api.utils.SettingsUtil;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public class BaritonePathManager implements IPathManager {
   private final VarHandle rotationField;
   private final BaritoneSettings settings;
   private BaritonePathManager.GoalDirection directionGoal;
   private boolean pathingPaused;

   public BaritonePathManager() {
      MeteorClient.EVENT_BUS.subscribe(this);
      Class<?> klass = BaritoneAPI.getProvider().getPrimaryBaritone().getLookBehavior().getClass();
      VarHandle rotationField = null;

      for (Field field : klass.getDeclaredFields()) {
         if (field.getType() == Rotation.class) {
            try {
               rotationField = MethodHandles.lookup().unreflectVarHandle(field);
               break;
            } catch (IllegalAccessException var8) {
               throw new RuntimeException(var8);
            }
         }
      }

      this.rotationField = rotationField;
      this.settings = new BaritoneSettings();
      BaritoneAPI.getSettings().repackOnAnyBlockChange.value = false;
      BaritoneAPI.getSettings().chunkCaching.value = false;
      BaritoneAPI.getProvider().getPrimaryBaritone().getPathingControlManager().registerProcess(new BaritonePathManager.BaritoneProcess());
   }

   @Override
   public String getName() {
      return "Baritone";
   }

   @Override
   public boolean isPathing() {
      return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing();
   }

   @Override
   public void pause() {
      this.pathingPaused = true;
   }

   @Override
   public void resume() {
      this.pathingPaused = false;
   }

   @Override
   public void stop() {
      BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().cancelEverything();
   }

   @Override
   public void moveTo(BlockPos pos, boolean ignoreY) {
      if (ignoreY) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ(pos.getX(), pos.getZ()));
      } else {
         BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos));
      }
   }

   @Override
   public void moveInDirection(float yaw) {
      this.directionGoal = new BaritonePathManager.GoalDirection(yaw);
      BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(this.directionGoal);
   }

   @Override
   public void mine(Block... blocks) {
      BaritoneAPI.getProvider().getPrimaryBaritone().getMineProcess().mine(blocks);
   }

   @Override
   public void follow(Predicate<Entity> entity) {
      BaritoneAPI.getProvider().getPrimaryBaritone().getFollowProcess().follow(entity);
   }

   @Override
   public float getTargetYaw() {
      Rotation rotation = (Rotation)this.rotationField.get((ILookBehavior)BaritoneAPI.getProvider().getPrimaryBaritone().getLookBehavior());
      return rotation == null ? 0.0F : rotation.getYaw();
   }

   @Override
   public float getTargetPitch() {
      Rotation rotation = (Rotation)this.rotationField.get((ILookBehavior)BaritoneAPI.getProvider().getPrimaryBaritone().getLookBehavior());
      return rotation == null ? 0.0F : rotation.getPitch();
   }

   @Override
   public IPathManager.ISettings getSettings() {
      return this.settings;
   }

   @EventHandler(
      priority = 200
   )
   private void onTick(TickEvent.Pre event) {
      if (this.directionGoal != null) {
         if (this.directionGoal != BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().getGoal()) {
            this.directionGoal = null;
         } else {
            this.directionGoal.tick();
         }
      }
   }

   private class BaritoneProcess implements IBaritoneProcess {
      public boolean isActive() {
         return BaritonePathManager.this.pathingPaused;
      }

      public PathingCommand onTick(boolean b, boolean b1) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getInputOverrideHandler().clearAllKeys();
         return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
      }

      public boolean isTemporary() {
         return true;
      }

      public void onLostControl() {
      }

      public double priority() {
         return 0.0;
      }

      public String displayName0() {
         return "Meteor Client";
      }
   }

   private static class GoalDirection implements Goal {
      private static final double SQRT_2 = Math.sqrt(2.0);
      private final float yaw;
      private int x;
      private int z;
      private int timer;

      public GoalDirection(float yaw) {
         this.yaw = yaw;
         this.tick();
      }

      public static double calculate(double xDiff, double zDiff) {
         double x = Math.abs(xDiff);
         double z = Math.abs(zDiff);
         double straight;
         double diagonal;
         if (x < z) {
            straight = z - x;
            diagonal = x;
         } else {
            straight = x - z;
            diagonal = z;
         }

         diagonal *= SQRT_2;
         return (diagonal + straight) * (Double)BaritoneAPI.getSettings().costHeuristic.value;
      }

      public void tick() {
         if (this.timer <= 0) {
            this.timer = 20;
            Vec3 pos = MeteorClient.mc.player.position();
            float theta = (float)Math.toRadians((double)this.yaw);
            this.x = (int)Math.floor(pos.x - (double)Mth.sin(theta) * 100.0);
            this.z = (int)Math.floor(pos.z + (double)Mth.cos(theta) * 100.0);
         }

         this.timer--;
      }

      public boolean isInGoal(int x, int y, int z) {
         return x == this.x && z == this.z;
      }

      public double heuristic(int x, int y, int z) {
         int xDiff = x - this.x;
         int zDiff = z - this.z;
         return calculate((double)xDiff, (double)zDiff);
      }

      @Override
      public String toString() {
         return String.format("GoalXZ{x=%s,z=%s}", SettingsUtil.maybeCensor(this.x), SettingsUtil.maybeCensor(this.z));
      }

      public int getX() {
         return this.x;
      }

      public int getZ() {
         return this.z;
      }
   }
}
