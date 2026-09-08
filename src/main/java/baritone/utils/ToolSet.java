package baritone.utils;

import baritone.Baritone;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ToolSet {
   private final Map<Block, Double> breakStrengthCache = new HashMap<>();
   private final Function<Block, Double> backendCalculation;
   private final LocalPlayer player;

   public ToolSet(LocalPlayer player) {
      this.player = player;
      if (Baritone.settings().considerPotionEffects.value) {
         double amplifier = this.potionAmplifier();
         Function<Double, Double> amplify = x -> amplifier * x;
         this.backendCalculation = amplify.compose(this::getBestDestructionTime);
      } else {
         this.backendCalculation = this::getBestDestructionTime;
      }
   }

   public double getStrVsBlock(BlockState state) {
      return this.breakStrengthCache.computeIfAbsent(state.getBlock(), this.backendCalculation);
   }

   private int getMaterialCost(ItemStack itemStack) {
      if (itemStack.getItem() instanceof TieredItem) {
         TieredItem tool = (TieredItem)itemStack.getItem();
         return (int)tool.getTier().getAttackDamageBonus();
      } else {
         return -1;
      }
   }

   public boolean hasSilkTouch(ItemStack stack) {
      ItemEnchantments enchantments = stack.getEnchantments();

      for (Holder<Enchantment> enchant : enchantments.keySet()) {
         if (enchant.is(Enchantments.SILK_TOUCH) && enchantments.getLevel(enchant) > 0) {
            return true;
         }
      }

      return false;
   }

   public int getBestSlot(Block b, boolean preferSilkTouch) {
      return this.getBestSlot(b, preferSilkTouch, false);
   }

   public int getBestSlot(Block b, boolean preferSilkTouch, boolean pathingCalculation) {
      if (!Baritone.settings().autoTool.value && pathingCalculation) {
         return this.player.getInventory().selected;
      } else {
         int best = 0;
         double highestSpeed = Double.NEGATIVE_INFINITY;
         int lowestCost = Integer.MIN_VALUE;
         boolean bestSilkTouch = false;
         BlockState blockState = b.defaultBlockState();

         for (int i = 0; i < 9; i++) {
            ItemStack itemStack = this.player.getInventory().getItem(i);
            if ((Baritone.settings().useSwordToMine.value || !itemStack.is(ItemTags.SWORDS))
               && (
                  !Baritone.settings().itemSaver.value
                     || itemStack.getDamageValue() + Baritone.settings().itemSaverThreshold.value < itemStack.getMaxDamage()
                     || itemStack.getMaxDamage() <= 1
               )) {
               double speed = calculateSpeedVsBlock(itemStack, blockState);
               boolean silkTouch = this.hasSilkTouch(itemStack);
               if (speed > highestSpeed) {
                  highestSpeed = speed;
                  best = i;
                  lowestCost = this.getMaterialCost(itemStack);
                  bestSilkTouch = silkTouch;
               } else if (speed == highestSpeed) {
                  int cost = this.getMaterialCost(itemStack);
                  if (cost < lowestCost && (silkTouch || !bestSilkTouch) || preferSilkTouch && !bestSilkTouch && silkTouch) {
                     highestSpeed = speed;
                     best = i;
                     lowestCost = cost;
                     bestSilkTouch = silkTouch;
                  }
               }
            }
         }

         return best;
      }
   }

   private double getBestDestructionTime(Block b) {
      ItemStack stack = this.player.getInventory().getItem(this.getBestSlot(b, false, true));
      return calculateSpeedVsBlock(stack, b.defaultBlockState()) * this.avoidanceMultiplier(b);
   }

   private double avoidanceMultiplier(Block b) {
      return Baritone.settings().blocksToAvoidBreaking.value.contains(b) ? Baritone.settings().avoidBreakingMultiplier.value : 1.0;
   }

   public static double calculateSpeedVsBlock(ItemStack item, BlockState state) {
      float hardness;
      try {
         hardness = state.getDestroySpeed(null, null);
      } catch (NullPointerException var10) {
         return -1.0;
      }

      if (hardness < 0.0F) {
         return -1.0;
      } else {
         float speed = item.getDestroySpeed(state);
         if (speed > 1.0F) {
            ItemEnchantments itemEnchantments = item.getEnchantments();

            label45:
            for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
               for (EnchantmentAttributeEffect e : ((Enchantment)enchant.value()).getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
                  if (e.attribute().is((ResourceKey)Attributes.MINING_EFFICIENCY.unwrapKey().get())) {
                     speed += e.amount().calculate(itemEnchantments.getLevel(enchant));
                     break label45;
                  }
               }
            }
         }

         speed /= hardness;
         return state.requiresCorrectToolForDrops() && (item.isEmpty() || !item.isCorrectToolForDrops(state))
            ? (double)(speed / 100.0F)
            : (double)(speed / 30.0F);
      }
   }

   private double potionAmplifier() {
      double speed = 1.0;
      if (this.player.hasEffect(MobEffects.DIG_SPEED)) {
         speed *= 1.0 + (double)(this.player.getEffect(MobEffects.DIG_SPEED).getAmplifier() + 1) * 0.2;
      }

      if (this.player.hasEffect(MobEffects.DIG_SLOWDOWN)) {
         switch (this.player.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) {
            case 0:
               speed *= 0.3;
               break;
            case 1:
               speed *= 0.09;
               break;
            case 2:
               speed *= 0.0027;
               break;
            default:
               speed *= 8.1E-4;
         }
      }

      return speed;
   }
}
