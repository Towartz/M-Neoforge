package baritone.api.cache;

public interface ICachedRegion extends IBlockTypeAccess {
   boolean isCached(int var1, int var2);

   int getX();

   int getZ();
}
