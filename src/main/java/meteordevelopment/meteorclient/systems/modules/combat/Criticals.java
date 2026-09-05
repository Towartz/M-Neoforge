package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IPlayerInteractEntityC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.ActionType;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.MaceItem;

public class Criticals extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgMace = this.settings.createGroup("Mace");
   private final Setting<Criticals.Mode> mode = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("mode"))
                  .description("The mode on how Criticals will function."))
               .defaultValue(Criticals.Mode.Packet))
            .build()
      );
   private final Setting<Boolean> ka = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("only-killaura")
            .description("Only performs crits when using killaura.")
            .defaultValue(Boolean.valueOf(false))
            .visible(() -> this.mode.get() != Criticals.Mode.None)
            .build()
      );
   private final Setting<Boolean> mace = this.sgMace
      .add(
         new BoolSetting.Builder()
            .name("smash-attack")
            .description("Will always perform smash attacks when using a mace.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Double> extraHeight = this.sgMace
      .add(
         new DoubleSetting.Builder()
            .name("additional-height")
            .description("The amount of additional height to spoof. More height means more damage.")
            .defaultValue(0.0)
            .min(0.0)
            .sliderRange(0.0, 100.0)
            .visible(this.mace::get)
            .build()
      );
   private ServerboundInteractPacket attackPacket;
   private ServerboundSwingPacket swingPacket;
   private boolean sendPackets;
   private int sendTimer;

   public Criticals() {
      super(Categories.Combat, "criticals", "Performs critical attacks when you hit your target.");
   }

   @Override
   public void onActivate() {
      this.attackPacket = null;
      this.swingPacket = null;
      this.sendPackets = false;
      this.sendTimer = 0;
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (event.packet instanceof IPlayerInteractEntityC2SPacket packet && packet.getType() == ActionType.ATTACK) {
         if (this.mace.get() && this.mc.player.getMainHandItem().getItem() instanceof MaceItem) {
            if (this.mc.player.isFallFlying()) {
               return;
            }

            this.sendPacket(0.0);
            this.sendPacket(1.501 + this.extraHeight.get());
            this.sendPacket(0.0);
            return;
         }

         if (this.skipCrit()) {
            return;
         }

         Entity entity = packet.getEntity();
         if (!(entity instanceof LivingEntity) || entity != Modules.get().get(KillAura.class).getTarget() && this.ka.get()) {
            return;
         }

         switch ((Criticals.Mode)this.mode.get()) {
            case Packet:
               this.sendPacket(0.0625);
               this.sendPacket(0.0);
               return;
            case Bypass:
               this.sendPacket(0.11);
               this.sendPacket(0.1100013579);
               this.sendPacket(1.3579E-6);
               return;
            case Jump:
            case MiniJump:
               if (!this.sendPackets) {
                  this.sendPackets = true;
                  this.sendTimer = this.mode.get() == Criticals.Mode.Jump ? 6 : 4;
                  this.attackPacket = (ServerboundInteractPacket)event.packet;
                  if (this.mode.get() == Criticals.Mode.Jump) {
                     this.mc.player.jumpFromGround();
                  } else {
                     ((IVec3d)this.mc.player.getDeltaMovement()).setY(0.25);
                  }

                  event.cancel();
               }

               return;
            default:
               return;
         }
      }

      if (event.packet instanceof ServerboundSwingPacket && this.mode.get() != Criticals.Mode.Packet) {
         if (this.skipCrit()) {
            return;
         }

         if (this.sendPackets && this.swingPacket == null) {
            this.swingPacket = (ServerboundSwingPacket)event.packet;
            event.cancel();
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.sendPackets) {
         if (this.sendTimer <= 0) {
            this.sendPackets = false;
            if (this.attackPacket == null || this.swingPacket == null) {
               return;
            }

            this.mc.getConnection().send(this.attackPacket);
            this.mc.getConnection().send(this.swingPacket);
            this.attackPacket = null;
            this.swingPacket = null;
         } else {
            this.sendTimer--;
         }
      }
   }

   private void sendPacket(double height) {
      double x = this.mc.player.getX();
      double y = this.mc.player.getY();
      double z = this.mc.player.getZ();
      ServerboundMovePlayerPacket packet = new Pos(x, y + height, z, false);
      ((IPlayerMoveC2SPacket)packet).setTag(1337);
      this.mc.player.connection.send(packet);
   }

   private boolean skipCrit() {
      return !this.mc.player.onGround() || this.mc.player.isUnderWater() || this.mc.player.isInLava() || this.mc.player.onClimbable();
   }

   @Override
   public String getInfoString() {
      return this.mode.get().name();
   }

   public static enum Mode {
      None,
      Packet,
      Bypass,
      Jump,
      MiniJump;
   }
}
