package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.KeyBindingAccessor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.commands.SharedSuggestionProvider;

public class InputCommand extends Command {
   private static final List<InputCommand.KeypressHandler> activeHandlers = new ArrayList<>();
   private static final List<Pair<KeyMapping, String>> holdKeys = List.of(
      new Pair(mc.options.keyUp, "forwards"),
      new Pair(mc.options.keyDown, "backwards"),
      new Pair(mc.options.keyLeft, "left"),
      new Pair(mc.options.keyRight, "right"),
      new Pair(mc.options.keyJump, "jump"),
      new Pair(mc.options.keyShift, "sneak"),
      new Pair(mc.options.keySprint, "sprint"),
      new Pair(mc.options.keyUse, "use"),
      new Pair(mc.options.keyAttack, "attack")
   );
   private static final List<Pair<KeyMapping, String>> pressKeys = List.of(new Pair(mc.options.keySwapOffhand, "swap"), new Pair(mc.options.keyDrop, "drop"));

   public InputCommand() {
      super("input", "Keyboard input simulation.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      for (Pair<KeyMapping, String> keyBinding : holdKeys) {
         builder.then(literal((String)keyBinding.getSecond()).then(argument("ticks", IntegerArgumentType.integer(1)).executes(context -> {
            activeHandlers.add(new InputCommand.KeypressHandler((KeyMapping)keyBinding.getFirst(), (Integer)context.getArgument("ticks", Integer.class)));
            return 1;
         })));
      }

      for (Pair<KeyMapping, String> keyBinding : pressKeys) {
         builder.then(literal((String)keyBinding.getSecond()).executes(context -> {
            press((KeyMapping)keyBinding.getFirst());
            return 1;
         }));
      }

      for (KeyMapping keyBinding : mc.options.keyHotbarSlots) {
         builder.then(literal(keyBinding.getName().substring(4)).executes(context -> {
            press(keyBinding);
            return 1;
         }));
      }

      builder.then(literal("clear").executes(ctx -> {
         if (activeHandlers.isEmpty()) {
            this.warning("No active keypress handlers.", new Object[0]);
         } else {
            this.info("Cleared all keypress handlers.", new Object[0]);
            activeHandlers.forEach(MeteorClient.EVENT_BUS::unsubscribe);
            activeHandlers.clear();
         }

         return 1;
      }));
      builder.then(
         literal("list")
            .executes(
               ctx -> {
                  if (activeHandlers.isEmpty()) {
                     this.warning("No active keypress handlers.", new Object[0]);
                  } else {
                     this.info("Active keypress handlers: ", new Object[0]);

                     for (int i = 0; i < activeHandlers.size(); i++) {
                        InputCommand.KeypressHandler handler = activeHandlers.get(i);
                        this.info(
                           "(highlight)%d(default) - (highlight)%s %d(default) ticks left out of (highlight)%d(default).",
                           new Object[]{i, I18n.get(handler.key.getName(), new Object[0]), handler.ticks, handler.totalTicks}
                        );
                     }
                  }

                  return 1;
               }
            )
      );
      builder.then(literal("remove").then(argument("index", IntegerArgumentType.integer(0)).executes(ctx -> {
         int index = IntegerArgumentType.getInteger(ctx, "index");
         if (index >= activeHandlers.size()) {
            this.warning("Index out of range.", new Object[0]);
         } else {
            this.info("Removed keypress handler.", new Object[0]);
            MeteorClient.EVENT_BUS.unsubscribe(activeHandlers.get(index));
            activeHandlers.remove(index);
         }

         return 1;
      })));
   }

   private static void press(KeyMapping keyBinding) {
      KeyBindingAccessor accessor = (KeyBindingAccessor)keyBinding;
      accessor.meteor$setTimesPressed(accessor.meteor$getTimesPressed() + 1);
   }

   private static class KeypressHandler {
      private final KeyMapping key;
      private final int totalTicks;
      private int ticks;

      public KeypressHandler(KeyMapping key, int ticks) {
         this.key = key;
         this.totalTicks = ticks;
         this.ticks = ticks;
         MeteorClient.EVENT_BUS.subscribe(this);
      }

      @EventHandler
      private void onTick(TickEvent.Post event) {
         if (this.ticks-- > 0) {
            this.key.setDown(true);
         } else {
            this.key.setDown(false);
            MeteorClient.EVENT_BUS.unsubscribe(this);
            InputCommand.activeHandlers.remove(this);
         }
      }
   }
}
