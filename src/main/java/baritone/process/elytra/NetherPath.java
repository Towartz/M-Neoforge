package baritone.process.elytra;

import baritone.api.utils.BetterBlockPos;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class NetherPath extends AbstractList<BetterBlockPos> {
   private static final NetherPath EMPTY_PATH = new NetherPath(Collections.emptyList());
   private final List<BetterBlockPos> backing;

   NetherPath(List<BetterBlockPos> backing) {
      this.backing = backing;
   }

   public BetterBlockPos get(int index) {
      return this.backing.get(index);
   }

   @Override
   public int size() {
      return this.backing.size();
   }

   public BetterBlockPos getLast() {
      return this.isEmpty() ? null : this.backing.get(this.backing.size() - 1);
   }

   public Vec3 getVec(int index) {
      BetterBlockPos pos = this.get(index);
      return new Vec3((double)pos.x, (double)pos.y, (double)pos.z);
   }

   public static NetherPath emptyPath() {
      return EMPTY_PATH;
   }
}
