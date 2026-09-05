package meteordevelopment.meteorclient.mixininterface;

import net.minecraft.core.BlockPos;

public interface IBox {
   void expand(double var1);

   void set(double var1, double var3, double var5, double var7, double var9, double var11);

   default void set(BlockPos pos) {
      this.set((double)pos.getX(), (double)pos.getY(), (double)pos.getZ(), (double)(pos.getX() + 1), (double)(pos.getY() + 1), (double)(pos.getZ() + 1));
   }
}
