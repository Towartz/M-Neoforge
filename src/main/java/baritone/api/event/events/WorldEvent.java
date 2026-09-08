package baritone.api.event.events;

import baritone.api.event.events.type.EventState;
import net.minecraft.client.multiplayer.ClientLevel;

public final class WorldEvent {
   private final ClientLevel world;
   private final EventState state;

   public WorldEvent(ClientLevel world, EventState state) {
      this.world = world;
      this.state = state;
   }

   public final ClientLevel getWorld() {
      return this.world;
   }

   public final EventState getState() {
      return this.state;
   }
}
