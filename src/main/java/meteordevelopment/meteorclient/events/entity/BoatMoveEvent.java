package meteordevelopment.meteorclient.events.entity;

import net.minecraft.world.entity.vehicle.Boat;

public class BoatMoveEvent {
   private static final BoatMoveEvent INSTANCE = new BoatMoveEvent();
   public Boat boat;

   public static BoatMoveEvent get(Boat entity) {
      INSTANCE.boat = entity;
      return INSTANCE;
   }
}
