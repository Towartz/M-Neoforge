package meteordevelopment.meteorclient.systems.modules.movement;

import meteordevelopment.meteorclient.events.entity.BoatMoveEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.world.phys.Vec3;

public class BoatFly extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> speed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("speed").description("Horizontal speed in blocks per second.").defaultValue(10.0).min(0.0).sliderMax(50.0).build());
   private final Setting<Double> verticalSpeed = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("vertical-speed")
            .description("Vertical speed in blocks per second.")
            .defaultValue(6.0)
            .min(0.0)
            .sliderMax(20.0)
            .build()
      );
   private final Setting<Double> fallSpeed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("fall-speed").description("How fast you fall in blocks per second.").defaultValue(0.1).min(0.0).build());
   private final Setting<Boolean> cancelServerPackets = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("cancel-server-packets")
            .description("Cancels incoming boat move packets.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );

   public BoatFly() {
      super(Categories.Movement, "boat-fly", "Transforms your boat into a plane.");
   }

   @EventHandler
   private void onBoatMove(BoatMoveEvent event) {
      if (event.boat.getControllingPassenger() == this.mc.player) {
         event.boat.setYRot(this.mc.player.getYRot());
         Vec3 vel = PlayerUtils.getHorizontalVelocity(this.speed.get());
         double velX = vel.x();
         double velY = 0.0;
         double velZ = vel.z();
         if (this.mc.options.keyJump.isDown()) {
            velY += this.verticalSpeed.get() / 20.0;
         }

         if (this.mc.options.keySprint.isDown()) {
            velY -= this.verticalSpeed.get() / 20.0;
         } else {
            velY -= this.fallSpeed.get() / 20.0;
         }

         ((IVec3d)event.boat.getDeltaMovement()).set(velX, velY, velZ);
      }
   }

   @EventHandler
   private void onReceivePacket(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundMoveVehiclePacket && this.cancelServerPackets.get()) {
         event.cancel();
      }
   }
}

