package baritone.pathing.movement;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.cache.WorldData;
import baritone.pathing.precompute.PrecomputedData;
import baritone.utils.BlockStateInterface;
import baritone.utils.ToolSet;
import baritone.utils.pathing.BetterWorldBorder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class CalculationContext {
   private static final ItemStack STACK_BUCKET_WATER = new ItemStack(Items.WATER_BUCKET);
   public final boolean safeForThreadedUse;
   public final IBaritone baritone;
   public final Level world;
   public final WorldData worldData;
   public final BlockStateInterface bsi;
   public final ToolSet toolSet;
   public final boolean hasWaterBucket;
   public final boolean hasThrowaway;
   public final boolean canSprint;
   protected final double placeBlockCost;
   public final boolean allowBreak;
   public final List<Block> allowBreakAnyway;
   public final boolean allowParkour;
   public final boolean allowParkourPlace;
   public final boolean allowJumpAtBuildLimit;
   public final boolean allowParkourAscend;
   public final boolean assumeWalkOnWater;
   public boolean allowFallIntoLava;
   public final int frostWalker;
   public final boolean allowDiagonalDescend;
   public final boolean allowDiagonalAscend;
   public final boolean allowDownward;
   public int minFallHeight;
   public int maxFallHeightNoWater;
   public final int maxFallHeightBucket;
   public final double waterWalkSpeed;
   public final double breakBlockAdditionalCost;
   public double backtrackCostFavoringCoefficient;
   public double jumpPenalty;
   public final double walkOnWaterOnePenalty;
   public final boolean allowWalkOnMagmaBlocks;
   public final boolean assumeStep;
   public final BetterWorldBorder worldBorder;
   public final PrecomputedData precomputedData = new PrecomputedData();

   public CalculationContext(IBaritone baritone) {
      this(baritone, false);
   }

   public CalculationContext(IBaritone baritone, boolean forUseOnAnotherThread) {
      this.safeForThreadedUse = forUseOnAnotherThread;
      this.baritone = baritone;
      LocalPlayer player = baritone.getPlayerContext().player();
      this.world = baritone.getPlayerContext().world();
      this.worldData = (WorldData)baritone.getPlayerContext().worldData();
      this.bsi = new BlockStateInterface(baritone.getPlayerContext(), forUseOnAnotherThread);
      this.toolSet = new ToolSet(player);
      this.hasThrowaway = Baritone.settings().allowPlace.value && ((Baritone)baritone).getInventoryBehavior().hasGenericThrowaway();
      this.hasWaterBucket = Baritone.settings().allowWaterBucketFall.value
         && Inventory.isHotbarSlot(player.getInventory().findSlotMatchingItem(STACK_BUCKET_WATER))
         && this.world.dimension() != Level.NETHER;
      boolean modSprint = MovementHelper.isSprintActive();
      this.canSprint = (Baritone.settings().allowSprint.value || modSprint) && (modSprint || player.getFoodData().getFoodLevel() > 6);
      this.placeBlockCost = Baritone.settings().blockPlacementPenalty.value;
      this.allowBreak = Baritone.settings().allowBreak.value;
      this.allowBreakAnyway = new ArrayList<>(Baritone.settings().allowBreakAnyway.value);
      this.allowParkour = Baritone.settings().allowParkour.value;
      this.allowParkourPlace = Baritone.settings().allowParkourPlace.value;
      this.allowJumpAtBuildLimit = Baritone.settings().allowJumpAtBuildLimit.value;
      this.allowParkourAscend = Baritone.settings().allowParkourAscend.value;
      this.assumeWalkOnWater = Baritone.settings().assumeWalkOnWater.value;
      this.allowFallIntoLava = false;
      int frostWalkerLevel = 0;

      for (EquipmentSlot slot : EquipmentSlot.values()) {
         ItemEnchantments itemEnchantments = baritone.getPlayerContext().player().getItemBySlot(slot).getEnchantments();

         for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
            if (enchant.is(Enchantments.FROST_WALKER)) {
               frostWalkerLevel = itemEnchantments.getLevel(enchant);
            }
         }
      }

      this.frostWalker = frostWalkerLevel;
      this.allowDiagonalDescend = Baritone.settings().allowDiagonalDescend.value;
      this.allowDiagonalAscend = Baritone.settings().allowDiagonalAscend.value;
      this.allowDownward = Baritone.settings().allowDownward.value;
      this.minFallHeight = 3;
      this.maxFallHeightNoWater = MovementHelper.isNoFallActive() ? 159159 : Baritone.settings().maxFallHeightNoWater.value;
      this.maxFallHeightBucket = Baritone.settings().maxFallHeightBucket.value;
      float waterSpeedMultiplier = 1.0F;

      label51:
      for (EquipmentSlot slot : EquipmentSlot.values()) {
         ItemEnchantments itemEnchantments = baritone.getPlayerContext().player().getItemBySlot(slot).getEnchantments();

         for (Holder<Enchantment> enchantx : itemEnchantments.keySet()) {
            for (EnchantmentAttributeEffect effect : ((Enchantment)enchantx.value()).getEffects(EnchantmentEffectComponents.ATTRIBUTES)) {
               if (effect.attribute().is((ResourceKey)Attributes.WATER_MOVEMENT_EFFICIENCY.unwrapKey().get())) {
                  waterSpeedMultiplier = effect.amount().calculate(itemEnchantments.getLevel(enchantx));
                  break label51;
               }
            }
         }
      }

      this.waterWalkSpeed = 9.09090909090909 * (double)(1.0F - waterSpeedMultiplier) + 4.63284688441047 * (double)waterSpeedMultiplier;
      this.breakBlockAdditionalCost = Baritone.settings().blockBreakAdditionalPenalty.value;
      this.backtrackCostFavoringCoefficient = Baritone.settings().backtrackCostFavoringCoefficient.value;
      this.jumpPenalty = Baritone.settings().jumpPenalty.value;
      this.walkOnWaterOnePenalty = Baritone.settings().walkOnWaterOnePenalty.value;
      this.allowWalkOnMagmaBlocks = Baritone.settings().allowWalkOnMagmaBlocks.value;
      this.assumeStep = MovementHelper.canAutoStep(baritone.getPlayerContext(), 1.0);
      this.worldBorder = new BetterWorldBorder(this.world.getWorldBorder());
   }

   public final IBaritone getBaritone() {
      return this.baritone;
   }

   public BlockState get(int x, int y, int z) {
      return this.bsi.get0(x, y, z);
   }

   public boolean isLoaded(int x, int z) {
      return this.bsi.isLoaded(x, z);
   }

   public BlockState get(BlockPos pos) {
      return this.get(pos.getX(), pos.getY(), pos.getZ());
   }

   public Block getBlock(int x, int y, int z) {
      return this.get(x, y, z).getBlock();
   }

   public double costOfPlacingAt(int x, int y, int z, BlockState current) {
      if (!this.hasThrowaway) {
         return 1000000.0;
      } else if (this.isPossiblyProtected(x, y, z)) {
         return 1000000.0;
      } else if (!this.worldBorder.canPlaceAt(x, z)) {
         return 1000000.0;
      } else if (!Baritone.settings().allowPlaceInFluidsSource.value && current.getFluidState().isSource()) {
         return 1000000.0;
      } else {
         return !Baritone.settings().allowPlaceInFluidsFlow.value && !current.getFluidState().isEmpty() && !current.getFluidState().isSource()
            ? 1000000.0
            : this.placeBlockCost;
      }
   }

   public double breakCostMultiplierAt(int x, int y, int z, BlockState current) {
      if (!this.allowBreak && !this.allowBreakAnyway.contains(current.getBlock())) {
         return 1000000.0;
      } else {
         return this.isPossiblyProtected(x, y, z) ? 1000000.0 : 1.0;
      }
   }

   public double placeBucketCost() {
      return this.placeBlockCost;
   }

   public boolean isPossiblyProtected(int x, int y, int z) {
      return false;
   }
}
