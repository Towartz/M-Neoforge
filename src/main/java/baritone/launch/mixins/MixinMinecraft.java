package baritone.launch.mixins;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen.Reason;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Minecraft.class})
public class MixinMinecraft {
   @Shadow
   public LocalPlayer player;
   @Shadow
   public ClientLevel level;
   @Unique
   private BiFunction<EventState, TickEvent.Type, TickEvent> tickProvider;

   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void postInit(CallbackInfo ci) {
      BaritoneAPI.getProvider().getPrimaryBaritone();
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "FIELD",
         opcode = 180,
         target = "net/minecraft/client/Minecraft.screen:Lnet/minecraft/client/gui/screens/Screen;",
         ordinal = 0,
         shift = Shift.BEFORE
      )},
      slice = {@Slice(
         from = @At(
            value = "FIELD",
            opcode = 181,
            target = "net/minecraft/client/Minecraft.missTime:I"
         )
      )}
   )
   private void runTick(CallbackInfo ci) {
      this.tickProvider = TickEvent.createNextProvider();

      for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
         TickEvent.Type type = baritone.getPlayerContext().player() != null && baritone.getPlayerContext().world() != null
            ? TickEvent.Type.IN
            : TickEvent.Type.OUT;
         baritone.getGameEventHandler().onTick(this.tickProvider.apply(EventState.PRE, type));
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("RETURN")}
   )
   private void postRunTick(CallbackInfo ci) {
      if (this.tickProvider != null) {
         for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
            TickEvent.Type type = baritone.getPlayerContext().player() != null && baritone.getPlayerContext().world() != null
               ? TickEvent.Type.IN
               : TickEvent.Type.OUT;
            baritone.getGameEventHandler().onPostTick(this.tickProvider.apply(EventState.POST, type));
         }

         this.tickProvider = null;
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "net/minecraft/client/multiplayer/ClientLevel.tickEntities()V",
         shift = Shift.AFTER
      )}
   )
   private void postUpdateEntities(CallbackInfo ci) {
      IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(this.player);
      if (baritone != null) {
         baritone.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.POST));
      }
   }

   @Inject(
      method = {"setLevel"},
      at = {@At("HEAD")}
   )
   private void preLoadWorld(ClientLevel world, Reason arg2, CallbackInfo ci) {
      if (this.level != null || world != null) {
         BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onWorldEvent(new WorldEvent(world, EventState.PRE));
      }
   }

   @Inject(
      method = {"setLevel"},
      at = {@At("RETURN")}
   )
   private void postLoadWorld(ClientLevel world, Reason arg2, CallbackInfo ci) {
      BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onWorldEvent(new WorldEvent(world, EventState.POST));
   }

   @Redirect(
      method = {"tick"},
      at = @At(
         value = "FIELD",
         opcode = 180,
         target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"
      ),
      slice = @Slice(
         from = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showDebugScreen()Z"
         ),
         to = @At(
            value = "CONSTANT",
            args = {"stringValue=Keybindings"}
         )
      )
   )
   private Screen passEvents(Minecraft instance) {
      return BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing() && this.player != null ? null : instance.screen;
   }
}
