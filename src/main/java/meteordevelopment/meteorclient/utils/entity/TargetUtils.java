package meteordevelopment.meteorclient.utils.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerEntity;
import meteordevelopment.meteorclient.utils.entity.fakeplayer.FakePlayerManager;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

public class TargetUtils {
   private static final List<Entity> ENTITIES = new ArrayList<>();

   private TargetUtils() {
   }

   @Nullable
   public static Entity get(Predicate<Entity> isGood, SortPriority sortPriority) {
      if (MeteorClient.mc.level == null) {
         return null;
      }

      Entity best = null;
      for (Entity entity : MeteorClient.mc.level.entitiesForRendering()) {
         if (entity != null && isGood.test(entity)) {
            if (best == null || sortPriority.compare(entity, best) < 0) {
               best = entity;
            }
         }
      }

      if (!FakePlayerManager.getFakePlayers().isEmpty()) {
         for (FakePlayerEntity fp : FakePlayerManager.getFakePlayers()) {
            if (fp != null && isGood.test(fp)) {
               if (best == null || sortPriority.compare(fp, best) < 0) {
                  best = fp;
               }
            }
         }
      }

      return best;
   }

   public static void getList(List<Entity> targetList, Predicate<Entity> isGood, SortPriority sortPriority, int maxCount) {
      targetList.clear();
      if (MeteorClient.mc.level == null) {
         return;
      }

      for (Entity entity : MeteorClient.mc.level.entitiesForRendering()) {
         if (entity != null && isGood.test(entity)) {
            targetList.add(entity);
         }
      }

      if (!FakePlayerManager.getFakePlayers().isEmpty()) {
         for (FakePlayerEntity fp : FakePlayerManager.getFakePlayers()) {
            if (fp != null && isGood.test(fp)) {
               targetList.add(fp);
            }
         }
      }

      if (targetList.size() > 1) {
         targetList.sort(sortPriority);
      }

      while (targetList.size() > maxCount) {
         targetList.remove(targetList.size() - 1);
      }
   }

   @Nullable
   public static Player getPlayerTarget(double range, SortPriority priority) {
      if (!Utils.canUpdate()) {
         return null;
      }

      Player best = null;
      double rangeSq = range * range;

      for (Entity entity : MeteorClient.mc.level.entitiesForRendering()) {
         if (entity instanceof Player player && player != MeteorClient.mc.player) {
            if (player.isDeadOrDying() || player.getHealth() <= 0.0F) {
               continue;
            }
            if (MeteorClient.mc.player.distanceToSqr(player) > rangeSq) {
               continue;
            }
            if (!Friends.get().shouldAttack(player)) {
               continue;
            }
            if (EntityUtils.getGameMode(player) != GameType.SURVIVAL && !(player instanceof FakePlayerEntity)) {
               continue;
            }

            if (best == null || priority.compare(player, best) < 0) {
               best = player;
            }
         }
      }

      if (!FakePlayerManager.getFakePlayers().isEmpty()) {
         for (FakePlayerEntity fp : FakePlayerManager.getFakePlayers()) {
            if (fp != null) {
               if (fp.isDeadOrDying() || fp.getHealth() <= 0.0F) {
                  continue;
               }
               if (MeteorClient.mc.player.distanceToSqr(fp) > rangeSq) {
                  continue;
               }
               if (!Friends.get().shouldAttack(fp)) {
                  continue;
               }

               if (best == null || priority.compare(fp, best) < 0) {
                  best = fp;
               }
            }
         }
      }

      return best;
   }

   public static boolean isBadTarget(Player target, double range) {
      return target == null ? true : !PlayerUtils.isWithin(target, range) || !target.isAlive() || target.isDeadOrDying() || target.getHealth() <= 0.0F;
   }
}
