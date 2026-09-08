package baritone.api.cache;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;

public interface ICachedWorld {
   ICachedRegion getRegion(int var1, int var2);

   void queueForPacking(LevelChunk var1);

   boolean isCached(int var1, int var2);

   ArrayList<BlockPos> getLocationsOf(String var1, int var2, int var3, int var4, int var5);

   void reloadAllFromDisk();

   void save();
}
