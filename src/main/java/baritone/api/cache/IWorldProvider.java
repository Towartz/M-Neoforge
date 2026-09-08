package baritone.api.cache;

import java.util.function.Consumer;

public interface IWorldProvider {
   IWorldData getCurrentWorld();

   default void ifWorldLoaded(Consumer<IWorldData> callback) {
      IWorldData currentWorld = this.getCurrentWorld();
      if (currentWorld != null) {
         callback.accept(currentWorld);
      }
   }
}
