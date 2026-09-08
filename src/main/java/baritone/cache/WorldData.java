package baritone.cache;

import baritone.Baritone;
import baritone.api.cache.ICachedWorld;
import baritone.api.cache.IWaypointCollection;
import baritone.api.cache.IWorldData;
import java.nio.file.Path;
import net.minecraft.world.level.dimension.DimensionType;

public class WorldData implements IWorldData {
   public final CachedWorld cache;
   private final WaypointCollection waypoints;
   public final Path directory;
   public final DimensionType dimension;

   WorldData(Path directory, DimensionType dimension) {
      this.directory = directory;
      this.cache = new CachedWorld(directory.resolve("cache"), dimension);
      this.waypoints = new WaypointCollection(directory.resolve("waypoints"));
      this.dimension = dimension;
   }

   public void onClose() {
      Baritone.getExecutor().execute(() -> {
         System.out.println("Started saving the world in a new thread");
         this.cache.save();
      });
   }

   @Override
   public ICachedWorld getCachedWorld() {
      return this.cache;
   }

   @Override
   public IWaypointCollection getWaypoints() {
      return this.waypoints;
   }
}
