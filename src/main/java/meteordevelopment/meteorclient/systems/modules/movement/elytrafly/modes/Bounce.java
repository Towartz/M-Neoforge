package meteordevelopment.meteorclient.systems.modules.movement.elytrafly.modes;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightMode;
import meteordevelopment.meteorclient.systems.modules.movement.elytrafly.ElytraFlightModes;
import meteordevelopment.meteorclient.systems.modules.player.Rotation;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class Bounce extends ElytraFlightMode {
   boolean rubberbanded = false;
   int tickDelay = this.elytraFly.restartDelay.get();
   double prevFov;

   public Bounce() {
      super(ElytraFlightModes.Bounce);
   }

   @Override
   public void onTick() {
      super.onTick();
      if (this.mc.options.keyJump.isDown() && !this.mc.player.isFallFlying()) {
         this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.START_FALL_FLYING));
      }

      if (checkConditions(this.mc.player)) {
         if (!this.rubberbanded) {
            if (this.prevFov != 0.0 && !this.elytraFly.sprint.get()) {
               this.mc.options.fovEffectScale().set(0.0);
            }

            if (this.elytraFly.autoJump.get()) {
               this.setPressed(this.mc.options.keyJump, true);
            }

            this.setPressed(this.mc.options.keyUp, true);
            this.mc.player.setYRot(this.getYawDirection());
            this.mc.player.setXRot(this.elytraFly.pitch.get().floatValue());
         }

         if (!this.elytraFly.sprint.get()) {
            if (this.mc.player.isFallFlying()) {
               this.mc.player.setSprinting(this.mc.player.onGround());
            } else {
               this.mc.player.setSprinting(true);
            }
         }

         if (this.rubberbanded && this.elytraFly.restart.get()) {
            if (this.tickDelay > 0) {
               this.tickDelay--;
            } else {
               this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.START_FALL_FLYING));
               this.rubberbanded = false;
               this.tickDelay = this.elytraFly.restartDelay.get();
            }
         }
      }
   }

   @Override
   public void onPreTick() {
      super.onPreTick();
      if (checkConditions(this.mc.player) && this.elytraFly.sprint.get()) {
         this.mc.player.setSprinting(true);
      }
   }

   private void unpress() {
      this.setPressed(this.mc.options.keyUp, false);
      if (this.elytraFly.autoJump.get()) {
         this.setPressed(this.mc.options.keyJump, false);
      }
   }

   @Override
   public void onPacketReceive(PacketEvent.Receive event) {
      if (event.packet instanceof ClientboundPlayerPositionPacket) {
         this.rubberbanded = true;
         this.mc.player.stopFallFlying();
      }
   }

   @Override
   public void onPacketSend(PacketEvent.Send event) {
      if (event.packet instanceof ServerboundPlayerCommandPacket
         && ((ServerboundPlayerCommandPacket)event.packet).getAction().equals(Action.START_FALL_FLYING)
         && !this.elytraFly.sprint.get()) {
         this.mc.player.setSprinting(true);
      }
   }

   private void setPressed(KeyMapping key, boolean pressed) {
      key.setDown(pressed);
      Input.setKeyState(key, pressed);
   }

   public static boolean recastElytra(LocalPlayer player) {
      if (checkConditions(player) && ignoreGround(player)) {
         player.connection.send(new ServerboundPlayerCommandPacket(player, Action.START_FALL_FLYING));
         return true;
      } else {
         return false;
      }
   }

   public static boolean checkConditions(LocalPlayer player) {
      ItemStack itemStack = player.getItemBySlot(EquipmentSlot.CHEST);
      return !player.getAbilities().flying
         && !player.isPassenger()
         && !player.onClimbable()
         && itemStack.is(Items.ELYTRA)
         && ElytraItem.isFlyEnabled(itemStack);
   }

   private static boolean ignoreGround(LocalPlayer player) {
      if (!player.isInWater() && !player.hasEffect(MobEffects.LEVITATION)) {
         ItemStack itemStack = player.getItemBySlot(EquipmentSlot.CHEST);
         if (itemStack.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(itemStack)) {
            player.startFallFlying();
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private float getYawDirection() {
      return switch ((Rotation.LockMode)this.elytraFly.yawLockMode.get()) {
         case None -> this.mc.player.getYRot();
         case Smart -> (float)Math.round((this.mc.player.getYRot() + 1.0F) / 45.0F) * 45.0F;
         case Simple -> this.elytraFly.yaw.get().floatValue();
      };
   }

   @Override
   public void onActivate() {
      this.prevFov = (Double)this.mc.options.fovEffectScale().get();
   }

   @Override
   public void onDeactivate() {
      this.unpress();
      this.rubberbanded = false;
      if (this.prevFov != 0.0 && !this.elytraFly.sprint.get()) {
         this.mc.options.fovEffectScale().set(this.prevFov);
      }
   }
}
