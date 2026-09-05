package meteordevelopment.meteorclient.utils.entity;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.EntityTrackingSectionAccessor;
import meteordevelopment.meteorclient.mixin.SectionedEntityCacheAccessor;
import meteordevelopment.meteorclient.mixin.SimpleEntityLookupAccessor;
import meteordevelopment.meteorclient.mixin.WorldAccessor;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

public class EntityUtils {
   private static MutableBlockPos testPos = new MutableBlockPos();

   private EntityUtils() {
   }

   public static boolean isAttackable(EntityType<?> type) {
      return type != EntityType.AREA_EFFECT_CLOUD
         && type != EntityType.ARROW
         && type != EntityType.FALLING_BLOCK
         && type != EntityType.FIREWORK_ROCKET
         && type != EntityType.ITEM
         && type != EntityType.LLAMA_SPIT
         && type != EntityType.SPECTRAL_ARROW
         && type != EntityType.ENDER_PEARL
         && type != EntityType.EXPERIENCE_BOTTLE
         && type != EntityType.POTION
         && type != EntityType.TRIDENT
         && type != EntityType.LIGHTNING_BOLT
         && type != EntityType.FISHING_BOBBER
         && type != EntityType.EXPERIENCE_ORB
         && type != EntityType.EGG;
   }

   public static boolean isRideable(EntityType<?> type) {
      return type == EntityType.MINECART
         || type == EntityType.BOAT
         || type == EntityType.CAMEL
         || type == EntityType.DONKEY
         || type == EntityType.HORSE
         || type == EntityType.LLAMA
         || type == EntityType.MULE
         || type == EntityType.PIG
         || type == EntityType.SKELETON_HORSE
         || type == EntityType.STRIDER
         || type == EntityType.ZOMBIE_HORSE;
   }

   public static float getTotalHealth(LivingEntity target) {
      return target.getHealth() + target.getAbsorptionAmount();
   }

   public static int getPing(Player player) {
      if (MeteorClient.mc.getConnection() == null) {
         return 0;
      } else {
         PlayerInfo playerListEntry = MeteorClient.mc.getConnection().getPlayerInfo(player.getUUID());
         return playerListEntry == null ? 0 : playerListEntry.getLatency();
      }
   }

   public static GameType getGameMode(Player player) {
      if (player == null) {
         return null;
      } else {
         PlayerInfo playerListEntry = MeteorClient.mc.getConnection().getPlayerInfo(player.getUUID());
         return playerListEntry == null ? null : playerListEntry.getGameMode();
      }
   }

   public static boolean isAboveWater(Entity entity) {
      if (entity == null || MeteorClient.mc.level == null) {
         return false;
      }
      testPos.set(entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
      int i = 0;

      while (i < 64) {
         BlockState state = MeteorClient.mc.level.getBlockState(testPos);
         if (!state.blocksMotion()) {
            Fluid fluid = state.getFluidState().getType();
            if (fluid != Fluids.WATER && fluid != Fluids.FLOWING_WATER) {
               testPos.move(0, -1, 0);
               i++;
               continue;
            }

            return true;
         }
         break;
      }

      return false;
   }

   public static boolean isInRenderDistance(Entity entity) {
      return entity == null ? false : isInRenderDistance(entity.getX(), entity.getZ());
   }

   public static boolean isInRenderDistance(BlockEntity entity) {
      return entity == null ? false : isInRenderDistance((double)entity.getBlockPos().getX(), (double)entity.getBlockPos().getZ());
   }

   public static boolean isInRenderDistance(BlockPos pos) {
      return pos == null ? false : isInRenderDistance((double)pos.getX(), (double)pos.getZ());
   }

   public static boolean isInRenderDistance(double posX, double posZ) {
      double x = Math.abs(MeteorClient.mc.gameRenderer.getMainCamera().getPosition().x - posX);
      double z = Math.abs(MeteorClient.mc.gameRenderer.getMainCamera().getPosition().z - posZ);
      double d = (double)(((Integer)MeteorClient.mc.options.renderDistance().get() + 1) * 16);
      return x < d && z < d;
   }

   public static BlockPos getCityBlock(Player player) {
      if (player == null) {
         return null;
      } else {
         double bestDistanceSquared = 36.0;
         Direction bestDirection = null;

         for (Direction direction : Direction.BY_2D_DATA) {
            testPos.set(player.blockPosition().relative(direction));
            Block block = MeteorClient.mc.level.getBlockState(testPos).getBlock();
            if (block == Blocks.OBSIDIAN
               || block == Blocks.NETHERITE_BLOCK
               || block == Blocks.CRYING_OBSIDIAN
               || block == Blocks.RESPAWN_ANCHOR
               || block == Blocks.ANCIENT_DEBRIS) {
               double testDistanceSquared = PlayerUtils.squaredDistanceTo(testPos);
               if (testDistanceSquared < bestDistanceSquared) {
                  bestDistanceSquared = testDistanceSquared;
                  bestDirection = direction;
               }
            }
         }

         return bestDirection == null ? null : player.blockPosition().relative(bestDirection);
      }
   }

   public static String getName(Entity entity) {
      if (entity == null) {
         return null;
      } else {
         return entity instanceof Player ? entity.getName().getString() : entity.getType().getDescription().getString();
      }
   }

   public static Color getColorFromDistance(Entity entity) {
      Color distanceColor = new Color(255, 255, 255);
      double distance = PlayerUtils.distanceToCamera(entity);
      double percent = distance / 60.0;
      if (!(percent < 0.0) && !(percent > 1.0)) {
         int r;
         int g;
         if (percent < 0.5) {
            r = 255;
            g = (int)(255.0 * percent / 0.5);
         } else {
            g = 255;
            r = 255 - (int)(255.0 * (percent - 0.5) / 0.5);
         }

         distanceColor.set(r, g, 0, 255);
         return distanceColor;
      } else {
         distanceColor.set(0, 255, 0, 255);
         return distanceColor;
      }
   }

   public static boolean intersectsWithEntity(AABB box, Predicate<Entity> predicate) {
      LevelEntityGetter<Entity> entityLookup = ((WorldAccessor)MeteorClient.mc.level).getEntityLookup();
      if (!(entityLookup instanceof LevelEntityGetterAdapter<Entity> simpleEntityLookup)) {
         AtomicBoolean found = new AtomicBoolean(false);
         entityLookup.get(box, entityx -> {
            if (!found.get() && predicate.test(entityx)) {
               found.set(true);
            }
         });
         return found.get();
      } else {
         EntitySectionStorage<Entity> cache = ((SimpleEntityLookupAccessor)simpleEntityLookup).getCache();
         LongSortedSet trackedPositions = ((SectionedEntityCacheAccessor)cache).getTrackedPositions();
         Long2ObjectMap<EntitySection<Entity>> trackingSections = ((SectionedEntityCacheAccessor)cache).getTrackingSections();
         int i = SectionPos.posToSectionCoord(box.minX - 2.0);
         int j = SectionPos.posToSectionCoord(box.minY - 2.0);
         int k = SectionPos.posToSectionCoord(box.minZ - 2.0);
         int l = SectionPos.posToSectionCoord(box.maxX + 2.0);
         int m = SectionPos.posToSectionCoord(box.maxY + 2.0);
         int n = SectionPos.posToSectionCoord(box.maxZ + 2.0);

         for (int o = i; o <= l; o++) {
            long p = SectionPos.asLong(o, 0, 0);
            long q = SectionPos.asLong(o, -1, -1);
            LongBidirectionalIterator longIterator = trackedPositions.subSet(p, q + 1L).iterator();

            while (longIterator.hasNext()) {
               long r = longIterator.nextLong();
               int s = SectionPos.y(r);
               int t = SectionPos.z(r);
               if (s >= j && s <= m && t >= k && t <= n) {
                  EntitySection<Entity> entityTrackingSection = (EntitySection<Entity>)trackingSections.get(r);
                  if (entityTrackingSection != null && entityTrackingSection.getStatus().isAccessible()) {
                     for (Entity entity : ((EntityTrackingSectionAccessor)(Object)entityTrackingSection).<Entity>getCollection()) {
                        if (entity.getBoundingBox().intersects(box) && predicate.test(entity)) {
                           return true;
                        }
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   public static EntityType<?> getGroup(Entity entity) {
      return entity.getType();
   }
}
