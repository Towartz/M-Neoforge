package meteordevelopment.meteorclient.systems.modules.player;

import java.util.List;
import java.util.function.Predicate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoWeapon;
import meteordevelopment.meteorclient.systems.modules.render.Xray;
import meteordevelopment.meteorclient.systems.modules.world.InfinityMiner;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BambooSaplingBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;

public class AutoTool extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgWhitelist = this.settings.createGroup("Whitelist");
   private final Setting<AutoTool.EnchantPreference> prefer = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("prefer"))
                  .description("Either to prefer Silk Touch, Fortune, or none."))
               .defaultValue(AutoTool.EnchantPreference.Fortune))
            .build()
      );
   private final Setting<Boolean> silkTouchForEnderChest = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("silk-touch-for-ender-chest")
            .description("Mines Ender Chests only with the Silk Touch enchantment.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Boolean> fortuneForOresCrops = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("fortune-for-ores-and-crops")
            .description("Mines Ores and crops only with the Fortune enchantment.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> antiBreak = this.sgGeneral
      .add(new BoolSetting.Builder().name("anti-break").description("Stops you from breaking your tool.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Integer> breakDurability = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("anti-break-percentage")
            .description("The durability percentage to stop using a tool.")
            .defaultValue(Integer.valueOf(10))
            .range(1, 100)
            .sliderRange(1, 100)
            .visible(this.antiBreak::get)
            .build()
      );
   private final Setting<Boolean> switchBack = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("switch-back")
            .description("Switches your hand to whatever was selected when releasing your attack key.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Integer> switchDelay = this.sgGeneral
      .add(new IntSetting.Builder().name("switch-delay").description("Delay in ticks before switching tools.").defaultValue(Integer.valueOf(0)).build());
   private final Setting<AutoTool.ListMode> listMode = this.sgWhitelist
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("list-mode")).description("Selection mode."))
               .defaultValue(AutoTool.ListMode.Blacklist))
            .build()
      );
   private final Setting<List<Item>> whitelist = this.sgWhitelist
      .add(
         new ItemListSetting.Builder()
            .name("whitelist")
            .description("The tools you want to use.")
            .visible(() -> this.listMode.get() == AutoTool.ListMode.Whitelist)
            .filter(AutoTool::isTool)
            .build()
      );
   private final Setting<List<Item>> blacklist = this.sgWhitelist
      .add(
         new ItemListSetting.Builder()
            .name("blacklist")
            .description("The tools you don't want to use.")
            .visible(() -> this.listMode.get() == AutoTool.ListMode.Blacklist)
            .filter(AutoTool::isTool)
            .build()
      );
   private boolean wasPressed;
   private boolean shouldSwitch;
   private int ticks;
   private int bestSlot;
   private int swappedSlot = -1;

   public AutoTool() {
      super(Categories.Player, "auto-tool", "Automatically switches to the most effective tool when performing an action.");
   }

   @Override
   public void onActivate() {
      this.swappedSlot = -1;
      this.shouldSwitch = false;
      this.bestSlot = -1;
      this.wasPressed = false;
   }

   @Override
   public void onDeactivate() {
      InvUtils.clearPreviousSlot();
      this.swappedSlot = -1;
      this.shouldSwitch = false;
      this.bestSlot = -1;
      this.wasPressed = false;
   }

   @EventHandler
   private void onAttackEntity(AttackEntityEvent event) {
      InvUtils.clearPreviousSlot();
      this.shouldSwitch = false;
      this.bestSlot = -1;
      this.swappedSlot = -1;
      this.wasPressed = false;
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      if (!Modules.get().isActive(InfinityMiner.class)) {
         if (this.mc.player == null) return;

         AutoWeapon autoWeapon = Modules.get().get(AutoWeapon.class);
         if ((autoWeapon != null && autoWeapon.isActive() && autoWeapon.isCombatActive()) || this.mc.hitResult instanceof EntityHitResult) {
            InvUtils.clearPreviousSlot();
            this.swappedSlot = -1;
            this.shouldSwitch = false;
            this.bestSlot = -1;
            this.wasPressed = false;
            return;
         }

         int currentSlot = this.mc.player.getInventory().selected;
         if (this.swappedSlot != -1 && currentSlot != this.swappedSlot) {
            InvUtils.clearPreviousSlot();
            this.swappedSlot = -1;
            this.shouldSwitch = false;
            this.bestSlot = -1;
         }

         if (this.switchBack.get() && !this.mc.options.keyAttack.isDown() && this.wasPressed && InvUtils.previousSlot != -1) {
            InvUtils.swapBack();
            this.wasPressed = false;
            this.swappedSlot = -1;
         } else {
            if (this.ticks <= 0 && this.shouldSwitch && this.bestSlot != -1) {
               if (!(this.mc.hitResult instanceof EntityHitResult)) {
                  this.swappedSlot = this.bestSlot;
                  InvUtils.swap(this.bestSlot, this.switchBack.get());
               }
               this.shouldSwitch = false;
            } else if (this.ticks > 0) {
               this.ticks--;
            }

            this.wasPressed = this.mc.options.keyAttack.isDown();
         }
      }
   }

   @EventHandler(
      priority = 100
   )
   private void onStartBreakingBlock(StartBreakingBlockEvent event) {
      if (!Modules.get().isActive(InfinityMiner.class)) {
         AutoWeapon autoWeapon = Modules.get().get(AutoWeapon.class);
         if ((autoWeapon != null && autoWeapon.isActive() && autoWeapon.isCombatActive()) || this.mc.hitResult instanceof EntityHitResult) {
            return;
         }

         BlockState blockState = this.mc.level.getBlockState(event.blockPos);
         if (BlockUtils.canBreak(event.blockPos, blockState)) {
            ItemStack currentStack = this.mc.player.getMainHandItem();
            double bestScore = -1.0;
            this.bestSlot = -1;

            for (int i = 0; i < 9; i++) {
               ItemStack itemStack = this.mc.player.getInventory().getItem(i);
               if ((this.listMode.get() != AutoTool.ListMode.Whitelist || this.whitelist.get().contains(itemStack.getItem()))
                  && (this.listMode.get() != AutoTool.ListMode.Blacklist || !this.blacklist.get().contains(itemStack.getItem()))) {
                  double score = getScore(
                     itemStack,
                     blockState,
                     this.silkTouchForEnderChest.get(),
                     this.fortuneForOresCrops.get(),
                     this.prefer.get(),
                     itemStack2 -> !this.shouldStopUsing(itemStack2)
                  );
                  if (!(score < 0.0) && score > bestScore) {
                     bestScore = score;
                     this.bestSlot = i;
                  }
               }
            }

            if (this.bestSlot != -1
                  && bestScore
                     > getScore(
                        currentStack,
                        blockState,
                        this.silkTouchForEnderChest.get(),
                        this.fortuneForOresCrops.get(),
                        this.prefer.get(),
                        itemStackx -> !this.shouldStopUsing(itemStackx)
                     )
               || this.shouldStopUsing(currentStack)
               || !isTool(currentStack)) {
               this.ticks = this.switchDelay.get();
               if (this.ticks == 0) {
                  this.swappedSlot = this.bestSlot;
                  InvUtils.swap(this.bestSlot, true);
               } else {
                  this.shouldSwitch = true;
               }
            }

            currentStack = this.mc.player.getMainHandItem();
            if (this.shouldStopUsing(currentStack) && isTool(currentStack)) {
               if (this.bestSlot == -1) {
                  this.mc.options.keyAttack.setDown(false);
               }
               event.cancel();
            }
         }
      }
   }

   private boolean shouldStopUsing(ItemStack itemStack) {
      return this.antiBreak.get() && itemStack.getMaxDamage() - itemStack.getDamageValue() < itemStack.getMaxDamage() * this.breakDurability.get() / 100;
   }

   public static double getScore(
      ItemStack itemStack,
      BlockState state,
      boolean silkTouchEnderChest,
      boolean fortuneOre,
      AutoTool.EnchantPreference enchantPreference,
      Predicate<ItemStack> good
   ) {
      if (good.test(itemStack) && isTool(itemStack)) {
         if (!itemStack.isCorrectToolForDrops(state)
            && (
               !(itemStack.getItem() instanceof SwordItem)
                  || !(state.getBlock() instanceof BambooStalkBlock) && !(state.getBlock() instanceof BambooSaplingBlock)
            )
            && (!(itemStack.getItem() instanceof ShearsItem) || !(state.getBlock() instanceof LeavesBlock))
            && !state.is(BlockTags.WOOL)) {
            return -1.0;
         } else if (silkTouchEnderChest && state.getBlock() == Blocks.ENDER_CHEST && !Utils.hasEnchantments(itemStack, Enchantments.SILK_TOUCH)) {
            return -1.0;
         } else if (fortuneOre && isFortunable(state.getBlock()) && !Utils.hasEnchantments(itemStack, Enchantments.FORTUNE)) {
            return -1.0;
         } else {
            double score = 0.0;
            score += (double)(itemStack.getDestroySpeed(state) * 1000.0F);
            score += (double)Utils.getEnchantmentLevel(itemStack, Enchantments.UNBREAKING);
            score += (double)Utils.getEnchantmentLevel(itemStack, Enchantments.EFFICIENCY);
            score += (double)Utils.getEnchantmentLevel(itemStack, Enchantments.MENDING);
            if (enchantPreference == AutoTool.EnchantPreference.Fortune) {
               score += (double)Utils.getEnchantmentLevel(itemStack, Enchantments.FORTUNE);
            }

            if (enchantPreference == AutoTool.EnchantPreference.SilkTouch) {
               score += (double)Utils.getEnchantmentLevel(itemStack, Enchantments.SILK_TOUCH);
            }

            if (itemStack.getItem() instanceof SwordItem item
               && (state.getBlock() instanceof BambooStalkBlock || state.getBlock() instanceof BambooSaplingBlock)) {
               score += (double)(9000.0F + ((Tool)item.components().get(DataComponents.TOOL)).getMiningSpeed(state) * 1000.0F);
            }

            return score;
         }
      } else {
         return -1.0;
      }
   }

   public static boolean isTool(Item item) {
      return item instanceof TieredItem || item instanceof ShearsItem;
   }

   public static boolean isTool(ItemStack itemStack) {
      return isTool(itemStack.getItem());
   }

   private static boolean isFortunable(Block block) {
      return block == Blocks.ANCIENT_DEBRIS ? false : Xray.ORES.contains(block) || block instanceof CropBlock;
   }

   public static enum EnchantPreference {
      None,
      Fortune,
      SilkTouch;
   }

   public static enum ListMode {
      Whitelist,
      Blacklist;
   }
}
