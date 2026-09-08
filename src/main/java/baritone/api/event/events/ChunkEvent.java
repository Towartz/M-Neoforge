package baritone.api.event.events;

import baritone.api.event.events.type.EventState;

public final class ChunkEvent {
   private final EventState state;
   private final ChunkEvent.Type type;
   private final int x;
   private final int z;

   public ChunkEvent(EventState state, ChunkEvent.Type type, int x, int z) {
      this.state = state;
      this.type = type;
      this.x = x;
      this.z = z;
   }

   public EventState getState() {
      return this.state;
   }

   public ChunkEvent.Type getType() {
      return this.type;
   }

   public int getX() {
      return this.x;
   }

   public int getZ() {
      return this.z;
   }

   public boolean isPostPopulate() {
      return this.state == EventState.POST && this.type.isPopulate();
   }

   public static enum Type {
      LOAD,
      UNLOAD,
      POPULATE_FULL,
      POPULATE_PARTIAL;

      public final boolean isPopulate() {
         return this == POPULATE_FULL || this == POPULATE_PARTIAL;
      }
   }
}
