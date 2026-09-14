package meteordevelopment.meteorclient.systems.modules.player.autocraft;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalGetToBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ContainerSearcher {
   private final Set<BlockPos> visitedContainers = new HashSet<>();

   public void reset() {
      this.visitedContainers.clear();
   }

   public void markVisited(BlockPos pos) {
      this.visitedContainers.add(pos);
   }

   public boolean isVisited(BlockPos pos) {
      return this.visitedContainers.contains(pos);
   }

   public List<BlockPos> findNearbyContainers(int radius) {
      List<BlockPos> containers = new ArrayList<>();
      if (MeteorClient.mc.player == null || MeteorClient.mc.level == null) return containers;

      double rSq = (double) radius * radius;
      Set<BlockPos> found = new HashSet<>();

      for (BlockEntity be : Utils.blockEntities()) {
         if (isContainerBlockEntity(be)) {
            BlockPos pos = be.getBlockPos();
            if (!this.visitedContainers.contains(pos) && PlayerUtils.squaredDistanceTo(pos) <= rSq) {
               containers.add(pos);
               found.add(pos);
            }
         }
      }

      // Also scan nearby blocks directly to ensure Ender Chests, Shulker Boxes, and Barrels are never missed
      BlockPos playerPos = MeteorClient.mc.player.blockPosition();
      int horiz = Math.min(radius, 24);
      int vert = Math.min(radius, 12);
      for (int x = -horiz; x <= horiz; x++) {
         for (int y = -vert; y <= vert; y++) {
            for (int z = -horiz; z <= horiz; z++) {
               BlockPos check = playerPos.offset(x, y, z);
               if (found.contains(check) || this.visitedContainers.contains(check)) continue;
               if (PlayerUtils.squaredDistanceTo(check) > rSq) continue;
               BlockState state = MeteorClient.mc.level.getBlockState(check);
               if (isContainerBlock(state)) {
                  containers.add(check);
                  found.add(check);
               }
            }
         }
      }

      containers.sort(Comparator.comparingDouble(PlayerUtils::squaredDistanceTo));
      return containers;
   }

   public static boolean isContainerBlock(BlockState state) {
      if (state == null || state.isAir()) return false;
      Block b = state.getBlock();
      return b instanceof ChestBlock
         || b instanceof BarrelBlock
         || b instanceof ShulkerBoxBlock
         || b == Blocks.ENDER_CHEST
         || b == Blocks.CHEST
         || b == Blocks.TRAPPED_CHEST
         || b == Blocks.BARREL;
   }

   public static String getContainerName(BlockPos pos) {
      if (pos == null || MeteorClient.mc.level == null) return "container";
      BlockState state = MeteorClient.mc.level.getBlockState(pos);
      return state.getBlock().getName().getString();
   }

   public static boolean isContainerBlockEntity(BlockEntity be) {
      if (be == null) return false;
      return be instanceof ChestBlockEntity
         || be instanceof TrappedChestBlockEntity
         || be instanceof BarrelBlockEntity
         || be instanceof ShulkerBoxBlockEntity
         || be instanceof EnderChestBlockEntity
         || (be instanceof BaseContainerBlockEntity && !(be instanceof net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity));
   }

   public static boolean isWithinReach(BlockPos pos) {
      return PlayerUtils.squaredDistanceTo(pos) <= 4.5 * 4.5;
   }

   public static void navigateTo(BlockPos pos) {
      if (BaritoneUtils.IS_AVAILABLE) {
         try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null) {
               baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(pos));
            }
         } catch (Throwable ignored) {
         }
      }
   }

   public static void stopNavigation() {
      if (BaritoneUtils.IS_AVAILABLE) {
         try {
            IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
            if (baritone != null && baritone.getPathingBehavior().isPathing()) {
               baritone.getPathingBehavior().cancelEverything();
            }
         } catch (Throwable ignored) {
         }
      }
   }

   public static void openContainer(BlockPos pos) {
      BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
      BlockUtils.interact(bhr, InteractionHand.MAIN_HAND, true);
   }

   /**
    * Withdraws needed materials or the target item itself from the container.
    * Returns the count of target items taken.
    */
   public int lootContainer(AbstractContainerMenu menu, Map<Item, Integer> neededItems, Item targetItem, int targetNeeded) {
      int targetTaken = 0;
      int containerSlots = getContainerSlotCount(menu);
      if (containerSlots <= 0) return 0;

      for (int i = 0; i < containerSlots; i++) {
         Slot slot = menu.slots.get(i);
         if (!slot.hasItem()) continue;
         ItemStack stack = slot.getItem();

         // 1. Take target item directly if present
         if (targetItem != null && targetNeeded > 0 && stack.is(targetItem)) {
            int before = CraftRecipeHelper.countInInventory(targetItem);
            InvUtils.shiftClick().slotId(i);
            int after = CraftRecipeHelper.countInInventory(targetItem);
            int take = Math.max(0, after - before);
            if (take == 0 && !slot.hasItem()) take = Math.min(stack.getCount(), targetNeeded);
            targetTaken += take;
            targetNeeded -= take;
            continue;
         }

         // 2. Take needed ingredients
         for (Map.Entry<Item, Integer> entry : neededItems.entrySet()) {
            Item needItem = entry.getKey();
            int needCount = entry.getValue();
            if (needCount > 0 && stack.is(needItem)) {
               int before = CraftRecipeHelper.countInInventory(needItem);
               InvUtils.shiftClick().slotId(i);
               int after = CraftRecipeHelper.countInInventory(needItem);
               int take = Math.max(0, after - before);
               if (take == 0 && !slot.hasItem()) take = Math.min(stack.getCount(), needCount);
               entry.setValue(Math.max(0, needCount - take));
               break;
            }
         }
      }

      return targetTaken;
   }

   public static int getContainerSlotCount(AbstractContainerMenu menu) {
      if (menu instanceof ChestMenu chestMenu) {
         return chestMenu.getRowCount() * 9;
      } else if (menu instanceof ShulkerBoxMenu) {
         return 3 * 9;
      }
      // General container: total slots minus 36 player slots
      return Math.max(0, menu.slots.size() - 36);
   }
}
