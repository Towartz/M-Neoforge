package baritone.api.process;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public interface IFollowProcess extends IBaritoneProcess {
   void follow(Predicate<Entity> var1);

   void pickup(Predicate<ItemStack> var1);

   List<Entity> following();

   Predicate<Entity> currentFilter();

   default void cancel() {
      this.onLostControl();
   }
}
