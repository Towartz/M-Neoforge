package baritone.api.event.events;

import baritone.api.event.events.type.EventState;
import java.util.function.BiFunction;

public final class TickEvent {
   private static int overallTickCount;
   private final EventState state;
   private final TickEvent.Type type;
   private final int count;

   public TickEvent(EventState state, TickEvent.Type type, int count) {
      this.state = state;
      this.type = type;
      this.count = count;
   }

   public int getCount() {
      return this.count;
   }

   public TickEvent.Type getType() {
      return this.type;
   }

   public EventState getState() {
      return this.state;
   }

   public static synchronized BiFunction<EventState, TickEvent.Type, TickEvent> createNextProvider() {
      int count = overallTickCount++;
      return (state, type) -> new TickEvent(state, type, count);
   }

   public static enum Type {
      IN,
      OUT;
   }
}
