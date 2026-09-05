package meteordevelopment.meteorclient.systems.modules.movement.speed;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.speed.modes.Strafe;
import meteordevelopment.meteorclient.systems.modules.movement.speed.modes.Vanilla;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.MoverType;

public class Speed extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   public final Setting<SpeedModes> speedMode = this.sgGeneral
      .add(
         new EnumSetting.Builder<SpeedModes>()
            .name("mode")
            .description("The method of applying speed.")
            .defaultValue(SpeedModes.Vanilla)
            .onModuleActivated(speedModesSetting -> this.onSpeedModeChanged(speedModesSetting.get()))
            .onChanged(this::onSpeedModeChanged)
            .build()
      );
   public final Setting<Double> vanillaSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("vanilla-speed")
            .description("The speed in blocks per second.")
            .defaultValue(5.6)
            .min(0.0)
            .sliderMax(20.0)
            .visible(() -> this.speedMode.get() == SpeedModes.Vanilla)
            .build()
      );
   public final Setting<Double> ncpSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("strafe-speed")
            .description("The speed.")
            .visible(() -> this.speedMode.get() == SpeedModes.Strafe)
            .defaultValue(1.6)
            .min(0.0)
            .sliderMax(3.0)
            .build()
      );
   public final Setting<Boolean> ncpSpeedLimit = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("speed-limit")
            .description("Limits your speed on servers with very strict anticheats.")
            .visible(() -> this.speedMode.get() == SpeedModes.Strafe)
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   public final Setting<Double> timer = this.sgGeneral
      .add(new DoubleSetting.Builder().name("timer").description("Timer override.").defaultValue(1.0).min(0.01).sliderMin(0.01).sliderMax(10.0).build());
   public final Setting<Boolean> inLiquids = this.sgGeneral
      .add(new BoolSetting.Builder().name("in-liquids").description("Uses speed when in lava or water.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> whenSneaking = this.sgGeneral
      .add(new BoolSetting.Builder().name("when-sneaking").description("Uses speed when sneaking.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> vanillaOnGround = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-on-ground")
            .description("Uses speed only when standing on a block.")
            .visible(() -> this.speedMode.get() == SpeedModes.Vanilla)
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private SpeedMode currentMode;

   public Speed() {
      super(Categories.Movement, "speed", "Modifies your movement speed when moving on the ground.");
      this.onSpeedModeChanged(this.speedMode.get());
   }

   @Override
   public void onActivate() {
      this.currentMode.onActivate();
   }

   @Override
   public void onDeactivate() {
      Modules.get().get(Timer.class).setOverride(1.0);
      this.currentMode.onDeactivate();
   }

   @EventHandler
   private void onPlayerMove(PlayerMoveEvent event) {
      if (event.type == MoverType.SELF && !this.mc.player.isFallFlying() && !this.mc.player.onClimbable() && this.mc.player.getVehicle() == null) {
         if (this.whenSneaking.get() || !this.mc.player.isShiftKeyDown()) {
            if (!this.vanillaOnGround.get() || this.mc.player.onGround() || this.speedMode.get() != SpeedModes.Vanilla) {
               if (this.inLiquids.get() || !this.mc.player.isInWater() && !this.mc.player.isInLava()) {
                  if (this.timer.get() != 1.0) {
                     Modules.get().get(Timer.class).setOverride(PlayerUtils.isMoving() ? this.timer.get() : 1.0);
                  }

                  this.currentMode.onMove(event);
               }
            }
         }
      }
   }

   @EventHandler
   private void onPreTick(TickEvent.Pre event) {
      if (!this.mc.player.isFallFlying() && !this.mc.player.onClimbable() && this.mc.player.getVehicle() == null) {
         if (this.whenSneaking.get() || !this.mc.player.isShiftKeyDown()) {
            if (!this.vanillaOnGround.get() || this.mc.player.onGround() || this.speedMode.get() != SpeedModes.Vanilla) {
               if (this.inLiquids.get() || !this.mc.player.isInWater() && !this.mc.player.isInLava()) {
                  this.currentMode.onTick();
               }
            }
         }
      }
   }

   @EventHandler
   private void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundPlayerPositionPacket) {
         this.currentMode.onRubberband();
      }
   }

   private void onSpeedModeChanged(SpeedModes mode) {
      switch (mode) {
         case Vanilla:
            this.currentMode = new Vanilla();
            break;
         case Strafe:
            this.currentMode = new Strafe();
      }
   }

   @Override
   public String getInfoString() {
      return this.currentMode.getHudString();
   }
}
