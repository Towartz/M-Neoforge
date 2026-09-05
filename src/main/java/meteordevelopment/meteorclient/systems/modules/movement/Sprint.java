package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ClientPlayerEntityAccessor;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.ActionType;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;

public class Sprint extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<Sprint.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("speed-mode")).description("What mode of sprinting."))
               .defaultValue(Sprint.Mode.Strict))
            .build()
      );
   public final Setting<Boolean> jumpFix = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("jump-fix")
            .description("Whether to correct jumping directions.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.mode.get() == Sprint.Mode.Rage)
            .build()
      );
   private final Setting<Boolean> keepSprint = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("keep-sprint")
            .description("Whether to keep sprinting after attacking an entity.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> unsprintOnHit = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("unsprint-on-hit")
            .description("Whether to stop sprinting when attacking, to ensure you get crits and sweep attacks.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> unsprintInWater = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("unsprint-in-water")
            .description("Whether to stop sprinting when in water.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );

   public Sprint() {
      super(Categories.Movement, "sprint", "Automatically sprints.");
   }

   @Override
   public void onDeactivate() {
      this.mc.player.setSprinting(false);
   }

   @EventHandler
   private void onTickMovement(TickEvent.Post event) {
      if (this.shouldSprint()) {
         this.mc.player.setSprinting(true);
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onPacketSend(PacketEvent.Send event) {
      if (this.unsprintOnHit.get() && event.packet instanceof IPlayerInteractEntityC2SPacket packet && packet.getType() == ActionType.ATTACK) {
         this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.STOP_SPRINTING));
         this.mc.player.setSprinting(false);
      }
   }

   @EventHandler
   private void onPacketSent(PacketEvent.Sent event) {
      if (this.unsprintOnHit.get() && this.keepSprint.get()) {
         if (event.packet instanceof IPlayerInteractEntityC2SPacket packet && packet.getType() == ActionType.ATTACK) {
            if (this.shouldSprint() && !this.mc.player.isSprinting()) {
               this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.START_SPRINTING));
               this.mc.player.setSprinting(true);
            }

            return;
         }
      }
   }

   public boolean shouldSprint() {
      if (!this.unsprintInWater.get() || !this.mc.player.isInWater() && !this.mc.player.isUnderWater()) {
         boolean strictSprint = this.mc.player.zza > 1.0E-5F
            && ((ClientPlayerEntityAccessor)this.mc.player).invokeCanSprint()
            && (!this.mc.player.horizontalCollision || this.mc.player.minorHorizontalCollision)
            && (!this.mc.player.isInWater() || this.mc.player.isUnderWater());
         return this.isActive()
            && (this.mode.get() == Sprint.Mode.Rage || strictSprint)
            && (this.mc.screen == null || Modules.get().get(GUIMove.class).sprint.get());
      } else {
         return false;
      }
   }

   public boolean rageSprint() {
      return this.isActive() && this.mode.get() == Sprint.Mode.Rage;
   }

   public boolean stopSprinting() {
      return !this.isActive() || !this.keepSprint.get();
   }

   public static enum Mode {
      Strict,
      Rage;
   }
}
