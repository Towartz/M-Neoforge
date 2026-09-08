package baritone.cache;

import baritone.Baritone;
import baritone.api.cache.IWorldProvider;
import baritone.api.utils.IPlayerContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.commons.lang3.SystemUtils;

public class WorldProvider implements IWorldProvider {
   private static final Map<Path, WorldData> worldCache = new HashMap<>();
   private final Baritone baritone;
   private final IPlayerContext ctx;
   private WorldData currentWorld;
   private Level mcWorld;

   public WorldProvider(Baritone baritone) {
      this.baritone = baritone;
      this.ctx = baritone.getPlayerContext();
   }

   public final WorldData getCurrentWorld() {
      this.detectAndHandleBrokenLoading();
      return this.currentWorld;
   }

   public final void initWorld(Level world) {
      this.getSaveDirectories(world).ifPresent(dirs -> {
         Path worldDir = (Path)dirs.getA();
         Path readmeDir = (Path)dirs.getB();

         try {
            Files.createDirectories(readmeDir);
            Files.write(readmeDir.resolve("readme.txt"), "https://github.com/cabaletta/baritone\n".getBytes(StandardCharsets.US_ASCII));
         } catch (IOException var10) {
         }

         Path worldDataDir = this.getWorldDataDirectory(worldDir, world);

         try {
            Files.createDirectories(worldDataDir);
         } catch (IOException var9) {
         }

         System.out.println("Baritone world data dir: " + worldDataDir);
         synchronized (worldCache) {
            this.currentWorld = worldCache.computeIfAbsent(worldDataDir, d -> new WorldData(d, world.dimensionType()));
         }

         this.mcWorld = this.ctx.world();
      });
   }

   public final void closeWorld() {
      WorldData world = this.currentWorld;
      this.currentWorld = null;
      this.mcWorld = null;
      if (world != null) {
         world.onClose();
      }
   }

   private Path getWorldDataDirectory(Path parent, Level world) {
      ResourceLocation dimId = world.dimension().location();
      int height = world.dimensionType().logicalHeight();
      return parent.resolve(dimId.getNamespace()).resolve(dimId.getPath() + "_" + height);
   }

   private Optional<Tuple<Path, Path>> getSaveDirectories(Level world) {
      Path readmeDir;
      Path worldDir;
      if (this.ctx.minecraft().hasSingleplayerServer()) {
         worldDir = this.ctx.minecraft().getSingleplayerServer().getWorldPath(LevelResource.ROOT);
         if (worldDir.relativize(this.ctx.minecraft().gameDirectory.toPath()).getNameCount() != 2) {
            worldDir = worldDir.getParent();
         }

         worldDir = worldDir.resolve("baritone");
         readmeDir = worldDir;
      } else {
         ServerData serverData = this.ctx.minecraft().getCurrentServer();
         if (serverData == null) {
            System.out.println("World seems to be a replay. Not loading Baritone cache.");
            this.currentWorld = null;
            this.mcWorld = this.ctx.world();
            return Optional.empty();
         }

         String folderName = serverData.isRealm() ? "realms" : serverData.ip;
         if (SystemUtils.IS_OS_WINDOWS) {
            folderName = folderName.replace(":", "_");
         }

         worldDir = this.baritone.getDirectory().resolve(folderName);
         readmeDir = this.baritone.getDirectory();
      }

      return Optional.of(new Tuple(worldDir, readmeDir));
   }

   private void detectAndHandleBrokenLoading() {
      if (this.mcWorld != this.ctx.world()) {
         if (this.currentWorld != null) {
            System.out.println("mc.world unloaded unnoticed! Unloading Baritone cache now.");
            this.closeWorld();
         }

         if (this.ctx.world() != null) {
            System.out.println("mc.world loaded unnoticed! Loading Baritone cache now.");
            this.initWorld(this.ctx.world());
         }
      } else if (this.currentWorld == null
         && this.ctx.world() != null
         && (this.ctx.minecraft().hasSingleplayerServer() || this.ctx.minecraft().getCurrentServer() != null)) {
         System.out.println("Retrying to load Baritone cache");
         this.initWorld(this.ctx.world());
      }
   }
}
