package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.EntityHitResult;

public class AutoWeapon extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<AutoWeapon.Weapon> weapon = this.sgGeneral
      .add(
         new EnumSetting.Builder<AutoWeapon.Weapon>()
            .name("weapon")
            .description("Preferred type of weapon to use.")
            .defaultValue(AutoWeapon.Weapon.Sword)
            .build()
      );

   private final Setting<AutoWeapon.SwitchMode> switchMode = this.sgGeneral
      .add(
         new EnumSetting.Builder<AutoWeapon.SwitchMode>()
            .name("switch-mode")
            .description("When to switch to your weapon.")
            .defaultValue(AutoWeapon.SwitchMode.Both)
            .build()
      );

   private final Setting<Boolean> shieldBuster = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("shield-buster")
            .description("Prioritizes an Axe if the target is blocking with a shield to disable it.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );

   private final Setting<Boolean> maceSmash = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("mace-smash")
            .description("Prioritizes the Mace for smash attacks when falling from height.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );

   private final Setting<Integer> threshold = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("threshold")
            .description("Damage advantage required to favor a non-preferred weapon over your preferred weapon.")
            .defaultValue(Integer.valueOf(4))
            .min(0)
            .sliderMax(15)
            .build()
      );

   private final Setting<Boolean> switchBack = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("switch-back")
            .description("Switches back to your previous item after combat ends.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );

   private final Setting<Integer> switchBackDelay = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("switch-back-delay")
            .description("Ticks without combat before switching back.")
            .defaultValue(Integer.valueOf(20))
            .min(1)
            .sliderMax(60)
            .visible(this.switchBack::get)
            .build()
      );

   private final Setting<Boolean> antiBreak = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("anti-break")
            .description("Prevents you from breaking your weapon.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );

   private final Setting<Integer> breakDurability = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("anti-break-percentage")
            .description("The durability percentage to stop using a weapon.")
            .defaultValue(Integer.valueOf(10))
            .range(1, 100)
            .sliderRange(1, 100)
            .visible(this.antiBreak::get)
            .build()
      );

   private int combatTimer;
   private int swappedSlot = -1;
   private boolean wasSwapped;

   public AutoWeapon() {
      super(Categories.Combat, "auto-weapon", "Finds and selects the smartest weapon in your hotbar.");
   }

   @Override
   public void onActivate() {
      this.combatTimer = 0;
      this.swappedSlot = -1;
      this.wasSwapped = false;
   }

   @Override
   public void onDeactivate() {
      if (this.wasSwapped && this.switchBack.get()) {
         InvUtils.swapBack();
      }
      this.combatTimer = 0;
      this.swappedSlot = -1;
      this.wasSwapped = false;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.player == null || this.mc.level == null) return;

      int currentSlot = this.mc.player.getInventory().selected;
      if (this.wasSwapped && this.swappedSlot != -1 && currentSlot != this.swappedSlot) {
         this.wasSwapped = false;
         this.swappedSlot = -1;
         InvUtils.clearPreviousSlot();
      }

      LivingEntity target = null;
      if (this.mc.hitResult instanceof EntityHitResult eHit && eHit.getEntity() instanceof LivingEntity living && living.isAlive()) {
         target = living;
      }

      if (target != null) {
         if (this.switchMode.get() == AutoWeapon.SwitchMode.OnCrosshair || this.switchMode.get() == AutoWeapon.SwitchMode.Both) {
            int best = this.getBestWeapon(target);
            if (best != -1 && best != this.mc.player.getInventory().selected) {
               this.swappedSlot = best;
               this.wasSwapped = true;
               InvUtils.swap(best, this.switchBack.get());
            }
         }
         this.combatTimer = this.switchBackDelay.get();
      } else {
         if (this.combatTimer > 0) {
            this.combatTimer--;
            if (this.combatTimer == 0 && this.switchBack.get() && this.wasSwapped && !this.mc.options.keyAttack.isDown()) {
               InvUtils.swapBack();
               this.wasSwapped = false;
               this.swappedSlot = -1;
            }
         }
      }
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (event.entity instanceof LivingEntity livingEntity && livingEntity.isAlive()) {
         if (this.switchMode.get() == AutoWeapon.SwitchMode.OnAttack || this.switchMode.get() == AutoWeapon.SwitchMode.Both) {
            int best = this.getBestWeapon(livingEntity);
            if (best != -1 && best != this.mc.player.getInventory().selected) {
               this.swappedSlot = best;
               this.wasSwapped = true;
               InvUtils.swap(best, this.switchBack.get());
            }
         }
         this.combatTimer = this.switchBackDelay.get();
      }
   }

   public boolean isCombatActive() {
      return this.combatTimer > 0;
   }

   private boolean isUsable(ItemStack stack) {
      if (stack.isEmpty()) return false;
      if (!this.antiBreak.get()) return true;
      if (!stack.isDamageableItem()) return true;
      return (stack.getMaxDamage() - stack.getDamageValue()) > (stack.getMaxDamage() * this.breakDurability.get() / 100);
   }

   private int getBestWeapon(LivingEntity target) {
      int selected = this.mc.player.getInventory().selected;

      if (this.shieldBuster.get() && target.isBlocking()) {
         int bestAxeSlot = -1;
         double bestAxeDmg = -1.0;
         for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof AxeItem && this.isUsable(stack)) {
               double dmg = (double)DamageUtils.getAttackDamage(this.mc.player, target, stack);
               if (dmg > bestAxeDmg) {
                  bestAxeDmg = dmg;
                  bestAxeSlot = i;
               }
            }
         }
         if (bestAxeSlot != -1) return bestAxeSlot;
      }

      int slotS = -1;
      int slotA = -1;
      int slotM = -1;
      double damageS = -1.0;
      double damageA = -1.0;
      double damageM = -1.0;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (!this.isUsable(stack)) continue;

         if (stack.getItem() instanceof SwordItem) {
            double dmg = (double)DamageUtils.getAttackDamage(this.mc.player, target, stack);
            if (dmg > damageS) {
               damageS = dmg;
               slotS = i;
            }
         } else if (stack.getItem() instanceof AxeItem) {
            double dmg = (double)DamageUtils.getAttackDamage(this.mc.player, target, stack);
            if (dmg > damageA) {
               damageA = dmg;
               slotA = i;
            }
         } else if (stack.getItem() instanceof MaceItem) {
            double dmg = (double)DamageUtils.getAttackDamage(this.mc.player, target, stack);
            if (dmg > damageM) {
               damageM = dmg;
               slotM = i;
            }
         }
      }

      boolean isAirborne = this.mc.player.fallDistance > 1.5F || (!this.mc.player.onGround() && this.mc.player.getDeltaMovement().y < -0.2);
      if (this.maceSmash.get() && isAirborne && slotM != -1) {
         if (damageM > damageS && damageM > damageA) {
            return slotM;
         }
      }

      int thresholdVal = this.threshold.get();
      AutoWeapon.Weapon pref = this.weapon.get();

      if (pref == AutoWeapon.Weapon.Mace && slotM != -1) {
         if (damageM >= damageS - thresholdVal && damageM >= damageA - thresholdVal) {
            return slotM;
         }
      } else if (pref == AutoWeapon.Weapon.Axe && slotA != -1) {
         if (damageA >= damageS - thresholdVal && damageA >= damageM - thresholdVal) {
            return slotA;
         }
      } else if (pref == AutoWeapon.Weapon.Sword && slotS != -1) {
         if (damageS >= damageA - thresholdVal && damageS >= damageM - thresholdVal) {
            return slotS;
         }
      }

      double highest = -1.0;
      int bestSlot = selected;
      if (damageS > highest && slotS != -1) {
         highest = damageS;
         bestSlot = slotS;
      }
      if (damageA > highest && slotA != -1) {
         highest = damageA;
         bestSlot = slotA;
      }
      if (damageM > highest && slotM != -1) {
         highest = damageM;
         bestSlot = slotM;
      }

      return bestSlot;
   }

   public static enum Weapon {
      Sword,
      Axe,
      Mace;
   }

   public static enum SwitchMode {
      Both,
      OnAttack,
      OnCrosshair;
   }
}
