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
import meteordevelopment.meteorclient.systems.modules.movement.Sprint;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket.ActionType;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.effect.MobEffects;
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

   private final Setting<Boolean> unsprint = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("unsprint")
            .description("Temporarily stops sprinting right before attacking to ensure critical hits are not cancelled by vanilla mechanics.")
            .defaultValue(true)
            .build()
      );

   private final Setting<Boolean> resetGround = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("reset-ground")
            .description("Sends a ground-confirmation packet after the attack to prevent server-side movement desync.")
            .defaultValue(true)
            .visible(() -> this.mode.get() != Criticals.Mode.Jump && this.mode.get() != Criticals.Mode.MiniJump && this.mode.get() != Criticals.Mode.None)
            .build()
      );

   private final Setting<Boolean> visualCrit = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("visual-crit")
            .description("Spawns critical hit particle effects on the attacked entity.")
            .defaultValue(true)
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
   private boolean wasSprinting;

   public Criticals() {
      super(Categories.Combat, "criticals", "Performs critical attacks when you hit your target.");
   }

   @Override
   public void onActivate() {
      this.attackPacket = null;
      this.swingPacket = null;
      this.sendPackets = false;
      this.sendTimer = 0;
      this.wasSprinting = false;
   }

   @EventHandler
   private void onSendPacket(PacketEvent.Send event) {
      if (event.packet instanceof IPlayerInteractEntityC2SPacket packet && packet.getType() == ActionType.ATTACK) {
         if (this.mc.player == null) return;

         if (this.mace.get() && this.mc.player.getMainHandItem().getItem() instanceof MaceItem) {
            if (this.mc.player.isFallFlying()) {
               return;
            }

            this.sendPacket(0.0, false);
            this.sendPacket(1.501 + this.extraHeight.get(), false);
            this.sendPacket(0.0, false);
            if (this.resetGround.get()) this.sendPacket(0.0, true);
            return;
         }

         if (this.skipCrit()) {
            return;
         }

         Entity entity = packet.getEntity();
         if (!(entity instanceof LivingEntity) || (this.ka.get() && entity != Modules.get().get(KillAura.class).getTarget())) {
            return;
         }

         // Unsprint if needed to allow critical hit in vanilla combat mechanics
         if (this.unsprint.get() && this.mc.player.isSprinting()) {
            this.wasSprinting = true;
            this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.STOP_SPRINTING));
            this.mc.player.setSprinting(false);
         }

         switch ((Criticals.Mode)this.mode.get()) {
            case Packet:
               this.sendPacket(0.0625, false);
               this.sendPacket(0.0, false);
               if (this.resetGround.get()) this.sendPacket(0.0, true);
               break;
            case Bypass:
               // Updated NCP / Strict 4-packet micro-offset sequence
               this.sendPacket(0.0625101, false);
               this.sendPacket(0.0, false);
               this.sendPacket(0.012511, false);
               this.sendPacket(0.0, false);
               if (this.resetGround.get()) this.sendPacket(0.0, true);
               break;
            case Vulcan:
               // Vulcan physics-decay simulation (matches (v - 0.08) * 0.98)
               this.sendPacket(0.16477328182606651, false);
               this.sendPacket(0.083077817822213, false);
               this.sendPacket(0.0030162615091, false);
               this.sendPacket(0.0, false);
               if (this.resetGround.get()) this.sendPacket(0.0, true);
               break;
            case NoGround:
               this.sendPacket(0.0, false);
               if (this.resetGround.get()) this.sendPacket(0.0, true);
               break;
            case Jump:
            case MiniJump:
               if (!this.sendPackets) {
                  this.sendPackets = true;
                  this.sendTimer = this.mode.get() == Criticals.Mode.Jump ? 12 : 8;
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

         if (this.visualCrit.get() && entity != null && this.mc.particleEngine != null) {
            this.mc.particleEngine.createTrackingEmitter(entity, ParticleTypes.CRIT);
         }
      }

      if (event.packet instanceof ServerboundSwingPacket && (this.mode.get() == Criticals.Mode.Jump || this.mode.get() == Criticals.Mode.MiniJump)) {
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
   private void onPacketSent(PacketEvent.Sent event) {
      if (this.unsprint.get() && this.wasSprinting && event.packet instanceof IPlayerInteractEntityC2SPacket packet && packet.getType() == ActionType.ATTACK) {
         this.wasSprinting = false;
         if (this.mc.player != null && (this.mc.options.keySprint.isDown() || Modules.get().isActive(Sprint.class))) {
            this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.START_SPRINTING));
            this.mc.player.setSprinting(true);
         }
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.sendPackets) {
         // Dynamic Apex Detection: wait until player is descending / at jump apex
         boolean isFalling = this.mc.player != null && (this.mc.player.getDeltaMovement().y < 0.0 || this.mc.player.fallDistance > 0.0F);
         if (isFalling || this.sendTimer <= 0) {
            this.sendPackets = false;
            if (this.attackPacket != null) {
               this.mc.getConnection().send(this.attackPacket);
               this.attackPacket = null;
            }
            if (this.swingPacket != null) {
               this.mc.getConnection().send(this.swingPacket);
               this.swingPacket = null;
            }
            if (this.unsprint.get() && this.wasSprinting) {
               this.wasSprinting = false;
               if (this.mc.player != null && (this.mc.options.keySprint.isDown() || Modules.get().isActive(Sprint.class))) {
                  this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.START_SPRINTING));
                  this.mc.player.setSprinting(true);
               }
            }
         } else {
            this.sendTimer--;
         }
      }
   }

   private void sendPacket(double height, boolean onGround) {
      if (this.mc.player == null) return;
      double x = this.mc.player.getX();
      double y = this.mc.player.getY();
      double z = this.mc.player.getZ();
      ServerboundMovePlayerPacket packet = new Pos(x, y + height, z, onGround);
      ((IPlayerMoveC2SPacket)packet).setTag(1337);
      this.mc.player.connection.send(packet);
   }

   private void sendPacket(double height) {
      this.sendPacket(height, false);
   }

   private boolean skipCrit() {
      if (this.mc.player == null) return true;
      return !this.mc.player.onGround()
         || this.mc.player.isUnderWater()
         || this.mc.player.isInWater()
         || this.mc.player.isInLava()
         || this.mc.player.onClimbable()
         || this.mc.player.isPassenger()
         || this.mc.player.hasEffect(MobEffects.BLINDNESS)
         || this.mc.player.isFallFlying();
   }

   @Override
   public String getInfoString() {
      return this.mode.get().name();
   }

   public static enum Mode {
      None,
      Packet,
      Bypass,
      Vulcan,
      NoGround,
      Jump,
      MiniJump;
   }
}
