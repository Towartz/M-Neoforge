package meteordevelopment.meteorclient.systems.modules.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.events.entity.player.ItemUseCrosshairTargetEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AnchorAura;
import meteordevelopment.meteorclient.systems.modules.combat.BedAura;
import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoGap extends Module {
   private static final Class<? extends Module>[] AURAS = new Class[]{KillAura.class, CrystalAura.class, AnchorAura.class, BedAura.class};
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPotions = this.settings.createGroup("Potions");
   private final SettingGroup sgHealth = this.settings.createGroup("Health");
   private final Setting<Boolean> allowEgap = this.sgGeneral
      .add(new BoolSetting.Builder().name("allow-egap").description("Allow eating E-Gaps over Gaps if found.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> always = this.sgGeneral
      .add(new BoolSetting.Builder().name("always").description("If it should always eat.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> pauseAuras = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-auras").description("Pauses all auras when eating.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> pauseBaritone = this.sgGeneral
      .add(new BoolSetting.Builder().name("pause-baritone").description("Pause baritone when eating.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> potionsRegeneration = this.sgPotions
      .add(
         new BoolSetting.Builder()
            .name("potions-regeneration")
            .description("If it should eat when Regeneration runs out.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> potionsFireResistance = this.sgPotions
      .add(
         new BoolSetting.Builder()
            .name("potions-fire-resistance")
            .description("If it should eat when Fire Resistance runs out. Requires E-Gaps.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.allowEgap::get)
            .build()
      );
   private final Setting<Boolean> potionsResistance = this.sgPotions
      .add(
         new BoolSetting.Builder()
            .name("potions-absorption")
            .description("If it should eat when Resistance runs out. Requires E-Gaps.")
            .defaultValue(Boolean.valueOf(false))
            .visible(this.allowEgap::get)
            .build()
      );
   private final Setting<Boolean> healthEnabled = this.sgHealth
      .add(
         new BoolSetting.Builder()
            .name("health-enabled")
            .description("If it should eat when health drops below threshold.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Integer> healthThreshold = this.sgHealth
      .add(
         new IntSetting.Builder()
            .name("health-threshold")
            .description("Health threshold to eat at. Includes absorption.")
            .defaultValue(Integer.valueOf(20))
            .min(0)
            .sliderMax(40)
            .build()
      );
   private boolean requiresEGap;
   private boolean eating;
   private int slot;
   private int prevSlot;
   private final List<Class<? extends Module>> wasAura = new ArrayList<>();
   private boolean wasBaritone;

   public AutoGap() {
      super(Categories.Player, "auto-gap", "Automatically eats Gaps or E-Gaps.");
   }

   @Override
   public void onDeactivate() {
      if (this.eating) {
         this.stopEating();
      }
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.eating) {
         if (this.shouldEat()) {
            if (this.isNotGapOrEGap(this.mc.player.getInventory().getItem(this.slot))) {
               int slot = this.findSlot();
               if (slot == -1) {
                  this.stopEating();
                  return;
               }

               this.changeSlot(slot);
            }

            this.eat();
         } else {
            this.stopEating();
         }
      } else if (this.shouldEat()) {
         this.slot = this.findSlot();
         if (this.slot != -1) {
            this.startEating();
         }
      }
   }

   @EventHandler
   private void onItemUseCrosshairTarget(ItemUseCrosshairTargetEvent event) {
      if (this.eating) {
         event.target = null;
      }
   }

   private void startEating() {
      this.prevSlot = this.mc.player.getInventory().selected;
      this.eat();
      this.wasAura.clear();
      if (this.pauseAuras.get()) {
         for (Class<? extends Module> klass : AURAS) {
            Module module = Modules.get().get(klass);
            if (module.isActive()) {
               this.wasAura.add(klass);
               module.toggle();
            }
         }
      }

      this.wasBaritone = false;
      if (this.pauseBaritone.get() && PathManagers.get().isPathing()) {
         this.wasBaritone = true;
         PathManagers.get().pause();
      }
   }

   private void eat() {
      this.changeSlot(this.slot);
      this.setPressed(true);
      if (!this.mc.player.isUsingItem()) {
         Utils.rightClick();
      }

      this.eating = true;
   }

   private void stopEating() {
      this.changeSlot(this.prevSlot);
      this.setPressed(false);
      this.eating = false;
      if (this.pauseAuras.get()) {
         for (Class<? extends Module> klass : AURAS) {
            Module module = Modules.get().get(klass);
            if (this.wasAura.contains(klass) && !module.isActive()) {
               module.toggle();
            }
         }
      }

      if (this.pauseBaritone.get() && this.wasBaritone) {
         PathManagers.get().resume();
      }
   }

   private void setPressed(boolean pressed) {
      this.mc.options.keyUse.setDown(pressed);
   }

   private void changeSlot(int slot) {
      InvUtils.swap(slot, false);
      this.slot = slot;
   }

   private boolean shouldEat() {
      this.requiresEGap = false;
      if (this.always.get()) {
         return true;
      } else {
         return this.shouldEatPotions() ? true : this.shouldEatHealth();
      }
   }

   private boolean shouldEatPotions() {
      Map<Holder<MobEffect>, MobEffectInstance> effects = this.mc.player.getActiveEffectsMap();
      if (this.potionsRegeneration.get() && !effects.containsKey(MobEffects.REGENERATION)) {
         return true;
      } else if (this.potionsFireResistance.get() && !effects.containsKey(MobEffects.FIRE_RESISTANCE)) {
         this.requiresEGap = true;
         return true;
      } else if (this.potionsResistance.get() && !effects.containsKey(MobEffects.DAMAGE_RESISTANCE)) {
         this.requiresEGap = true;
         return true;
      } else {
         return false;
      }
   }

   private boolean shouldEatHealth() {
      if (!this.healthEnabled.get()) {
         return false;
      } else {
         int health = Math.round(this.mc.player.getHealth() + this.mc.player.getAbsorptionAmount());
         return health < this.healthThreshold.get();
      }
   }

   private int findSlot() {
      boolean preferEGap = this.allowEgap.get() || this.requiresEGap;
      int slot = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (!stack.isEmpty() && !this.isNotGapOrEGap(stack)) {
            Item item = stack.getItem();
            if (item == Items.ENCHANTED_GOLDEN_APPLE && preferEGap) {
               slot = i;
               break;
            }

            if (item == Items.GOLDEN_APPLE && !this.requiresEGap) {
               slot = i;
               if (!preferEGap) {
                  break;
               }
            }
         }
      }

      return slot;
   }

   private boolean isNotGapOrEGap(ItemStack stack) {
      Item item = stack.getItem();
      return item != Items.GOLDEN_APPLE && item != Items.ENCHANTED_GOLDEN_APPLE;
   }

   public boolean isEating() {
      return this.isActive() && this.eating;
   }
}
