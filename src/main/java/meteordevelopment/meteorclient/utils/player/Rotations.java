package meteordevelopment.meteorclient.utils.player;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.PreInit;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.misc.FastMath;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class Rotations {
   private static final Pool<Rotations.Rotation> rotationPool = new Pool<>(Rotations.Rotation::new);
   private static final List<Rotations.Rotation> rotations = new ArrayList<>();
   public static float serverYaw;
   public static float serverPitch;
   public static int rotationTimer;
   private static float preYaw;
   private static float prePitch;
   private static int i = 0;
   private static Rotations.Rotation lastRotation;
   private static int lastRotationTimer;
   private static boolean sentLastRotation;
   public static boolean rotating = false;

   private Rotations() {
   }

   @PreInit
   public static void init() {
      MeteorClient.EVENT_BUS.subscribe(Rotations.class);
   }

   public static void rotate(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
      Rotations.Rotation rotation = rotationPool.get();
      rotation.set(yaw, pitch, priority, clientSide, callback);
      int i = 0;

      while (i < rotations.size() && priority <= rotations.get(i).priority) {
         i++;
      }

      rotations.add(i, rotation);
   }

   public static void rotate(double yaw, double pitch, int priority, Runnable callback) {
      rotate(yaw, pitch, priority, false, callback);
   }

   public static void rotate(double yaw, double pitch, Runnable callback) {
      rotate(yaw, pitch, 0, callback);
   }

   public static void rotate(double yaw, double pitch, int priority) {
      rotate(yaw, pitch, priority, null);
   }

   public static void rotate(double yaw, double pitch) {
      rotate(yaw, pitch, 0, null);
   }

   private static void resetLastRotation() {
      if (lastRotation != null) {
         rotationPool.free(lastRotation);
         lastRotation = null;
         lastRotationTimer = 0;
      }
   }

   @EventHandler
   private static void onGameLeft(GameLeftEvent event) {
      resetLastRotation();
      for (Rotations.Rotation r : rotations) {
         rotationPool.free(r);
      }
      rotations.clear();
      i = 0;
      rotating = false;
   }

   @EventHandler
   private static void onSendMovementPacketsPre(SendMovementPacketsEvent.Pre event) {
      if (MeteorClient.mc.cameraEntity == MeteorClient.mc.player) {
         sentLastRotation = false;
         if (!rotations.isEmpty()) {
            rotating = true;
            resetLastRotation();
            Rotations.Rotation rotation = rotations.get(i);
            setupMovementPacketRotation(rotation);
            i++;
         } else if (lastRotation != null) {
            if (lastRotationTimer >= Config.get().rotationHoldTicks.get()) {
               resetLastRotation();
               rotating = false;
            } else {
               setupMovementPacketRotation(lastRotation);
               sentLastRotation = true;
               lastRotationTimer++;
            }
         }
      }
   }

   private static void setupMovementPacketRotation(Rotations.Rotation rotation) {
      setClientRotation(rotation);
      setCamRotation(rotation.yaw, rotation.pitch);
   }

   private static void setClientRotation(Rotations.Rotation rotation) {
      if (MeteorClient.mc.player == null) return;
      preYaw = MeteorClient.mc.player.getYRot();
      prePitch = MeteorClient.mc.player.getXRot();
      MeteorClient.mc.player.setYRot((float)rotation.yaw);
      MeteorClient.mc.player.setXRot((float)rotation.pitch);
   }

   @EventHandler
   private static void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event) {
      if (!rotations.isEmpty()) {
         if (MeteorClient.mc.cameraEntity == MeteorClient.mc.player && i > 0 && i <= rotations.size()) {
            Rotations.Rotation first = rotations.get(i - 1);
            first.runCallback();
            if (rotations.size() == 1) {
               lastRotation = first;
            } else {
               rotationPool.free(first);
            }

            resetPreRotation();
         }

         for (; i < rotations.size(); i++) {
            Rotations.Rotation rotation = rotations.get(i);
            setCamRotation(rotation.yaw, rotation.pitch);
            if (rotation.clientSide) {
               setClientRotation(rotation);
            }

            rotation.sendPacket();
            if (rotation.clientSide) {
               resetPreRotation();
            }

            if (i == rotations.size() - 1) {
               lastRotation = rotation;
            } else {
               rotationPool.free(rotation);
            }
         }

         rotations.clear();
         i = 0;
      } else if (sentLastRotation) {
         resetPreRotation();
      }
   }

   private static void resetPreRotation() {
      if (MeteorClient.mc.player == null) return;
      MeteorClient.mc.player.setYRot(preYaw);
      MeteorClient.mc.player.setXRot(prePitch);
   }

   @EventHandler
   private static void onTick(TickEvent.Pre event) {
      rotationTimer++;
   }

   public static double getYaw(Entity entity) {
      double dx = entity.getX() - MeteorClient.mc.player.getX();
      double dz = entity.getZ() - MeteorClient.mc.player.getZ();
      return (double)(
         MeteorClient.mc.player.getYRot()
            + Mth.wrapDegrees(
               (float)(Math.atan2(dz, dx) * FastMath.RAD_TO_DEG)
                  - 90.0F
                  - MeteorClient.mc.player.getYRot()
            )
      );
   }

   public static double getYaw(Vec3 pos) {
      double dx = pos.x() - MeteorClient.mc.player.getX();
      double dz = pos.z() - MeteorClient.mc.player.getZ();
      return (double)(
         MeteorClient.mc.player.getYRot()
            + Mth.wrapDegrees(
               (float)(Math.atan2(dz, dx) * FastMath.RAD_TO_DEG)
                  - 90.0F
                  - MeteorClient.mc.player.getYRot()
            )
      );
   }

   public static double getPitch(Vec3 pos) {
      double diffX = pos.x() - MeteorClient.mc.player.getX();
      double diffY = pos.y() - (MeteorClient.mc.player.getY() + (double)MeteorClient.mc.player.getEyeHeight(MeteorClient.mc.player.getPose()));
      double diffZ = pos.z() - MeteorClient.mc.player.getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return (double)(
         MeteorClient.mc.player.getXRot() + Mth.wrapDegrees((float)(-Math.atan2(diffY, diffXZ) * FastMath.RAD_TO_DEG) - MeteorClient.mc.player.getXRot())
      );
   }

   public static double getPitch(Entity entity, Target target) {
      double y;
      if (target == Target.Head) {
         y = entity.getEyeY();
      } else if (target == Target.Body) {
         y = entity.getY() + (double)(entity.getBbHeight() / 2.0F);
      } else {
         y = entity.getY();
      }

      double diffX = entity.getX() - MeteorClient.mc.player.getX();
      double diffY = y - (MeteorClient.mc.player.getY() + (double)MeteorClient.mc.player.getEyeHeight(MeteorClient.mc.player.getPose()));
      double diffZ = entity.getZ() - MeteorClient.mc.player.getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return (double)(
         MeteorClient.mc.player.getXRot() + Mth.wrapDegrees((float)(-Math.atan2(diffY, diffXZ) * FastMath.RAD_TO_DEG) - MeteorClient.mc.player.getXRot())
      );
   }

   public static double getPitch(Entity entity) {
      return getPitch(entity, Target.Body);
   }

   public static double getYaw(BlockPos pos) {
      double dx = (double)pos.getX() + 0.5 - MeteorClient.mc.player.getX();
      double dz = (double)pos.getZ() + 0.5 - MeteorClient.mc.player.getZ();
      return (double)(
         MeteorClient.mc.player.getYRot()
            + Mth.wrapDegrees(
               (float)(Math.atan2(dz, dx) * FastMath.RAD_TO_DEG)
                  - 90.0F
                  - MeteorClient.mc.player.getYRot()
            )
      );
   }

   public static double getPitch(BlockPos pos) {
      double diffX = (double)pos.getX() + 0.5 - MeteorClient.mc.player.getX();
      double diffY = (double)pos.getY() + 0.5 - (MeteorClient.mc.player.getY() + (double)MeteorClient.mc.player.getEyeHeight(MeteorClient.mc.player.getPose()));
      double diffZ = (double)pos.getZ() + 0.5 - MeteorClient.mc.player.getZ();
      double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
      return (double)(
         MeteorClient.mc.player.getXRot() + Mth.wrapDegrees((float)(-Math.atan2(diffY, diffXZ) * FastMath.RAD_TO_DEG) - MeteorClient.mc.player.getXRot())
      );
   }

   public static void setCamRotation(double yaw, double pitch) {
      serverYaw = (float)yaw;
      serverPitch = (float)pitch;
      rotationTimer = 0;
   }

   private static class Rotation {
      public double yaw;
      public double pitch;
      public int priority;
      public boolean clientSide;
      public Runnable callback;

      public void set(double yaw, double pitch, int priority, boolean clientSide, Runnable callback) {
         this.yaw = yaw;
         this.pitch = pitch;
         this.priority = priority;
         this.clientSide = clientSide;
         this.callback = callback;
      }

      public void sendPacket() {
         if (MeteorClient.mc.getConnection() != null && MeteorClient.mc.player != null) {
            MeteorClient.mc.getConnection().send(new Rot((float)this.yaw, (float)this.pitch, MeteorClient.mc.player.onGround()));
         }
         this.runCallback();
      }

      public void runCallback() {
         if (this.callback != null) {
            try {
               this.callback.run();
            } catch (Throwable t) {
               t.printStackTrace();
            }
         }
      }
   }
}
