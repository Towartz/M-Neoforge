package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.Helper;
import baritone.utils.ToolSet;
import java.util.ArrayList;
import java.util.OptionalInt;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class InventoryBehavior extends Behavior implements Helper {
   int ticksSinceLastInventoryMove;
   int[] lastTickRequestedMove;

   public InventoryBehavior(Baritone baritone) {
      super(baritone);
   }

   @Override
   public void onTick(TickEvent event) {
      if (Baritone.settings().allowInventory.value) {
         if (event.getType() != TickEvent.Type.OUT) {
            if (this.ctx.player().containerMenu == this.ctx.player().inventoryMenu) {
               this.ticksSinceLastInventoryMove++;
               if (this.baritone.getPathingBehavior().isPathing()) {
                  if (this.firstValidThrowaway() >= 9) {
                     this.requestSwapWithHotBar(this.firstValidThrowaway(), 8);
                  }

                  int pick = this.bestToolAgainst(Blocks.STONE, PickaxeItem.class);
                  if (pick >= 9) {
                     this.requestSwapWithHotBar(pick, 0);
                  }
               }

               if (this.lastTickRequestedMove != null) {
                  this.logDebug("Remembering to move " + this.lastTickRequestedMove[0] + " " + this.lastTickRequestedMove[1] + " from a previous tick");
                  this.requestSwapWithHotBar(this.lastTickRequestedMove[0], this.lastTickRequestedMove[1]);
               }
            }
         }
      }
   }

   public boolean attemptToPutOnHotbar(int inMainInvy, Predicate<Integer> disallowedHotbar) {
      OptionalInt destination = this.getTempHotbarSlot(disallowedHotbar);
      return !destination.isPresent() || this.requestSwapWithHotBar(inMainInvy, destination.getAsInt());
   }

   public OptionalInt getTempHotbarSlot(Predicate<Integer> disallowedHotbar) {
      ArrayList<Integer> candidates = new ArrayList<>();

      for (int i = 1; i < 8; i++) {
         if (((ItemStack)this.ctx.player().getInventory().items.get(i)).isEmpty() && !disallowedHotbar.test(i)) {
            candidates.add(i);
         }
      }

      if (candidates.isEmpty()) {
         for (int ix = 1; ix < 8; ix++) {
            if (!disallowedHotbar.test(ix)) {
               candidates.add(ix);
            }
         }
      }

      return candidates.isEmpty() ? OptionalInt.empty() : OptionalInt.of(candidates.get(new Random().nextInt(candidates.size())));
   }

   private boolean requestSwapWithHotBar(int inInventory, int inHotbar) {
      this.lastTickRequestedMove = new int[]{inInventory, inHotbar};
      if (this.ticksSinceLastInventoryMove < Baritone.settings().ticksBetweenInventoryMoves.value) {
         this.logDebug("Inventory move requested but delaying " + this.ticksSinceLastInventoryMove + " " + Baritone.settings().ticksBetweenInventoryMoves.value);
         return false;
      } else if (Baritone.settings().inventoryMoveOnlyIfStationary.value && !this.baritone.getInventoryPauserProcess().stationaryForInventoryMove()) {
         this.logDebug("Inventory move requested but delaying until stationary");
         return false;
      } else {
         this.ctx
            .playerController()
            .windowClick(
               this.ctx.player().inventoryMenu.containerId, inInventory < 9 ? inInventory + 36 : inInventory, inHotbar, ClickType.SWAP, this.ctx.player()
            );
         this.ticksSinceLastInventoryMove = 0;
         this.lastTickRequestedMove = null;
         return true;
      }
   }

   private int firstValidThrowaway() {
      NonNullList<ItemStack> invy = this.ctx.player().getInventory().items;

      for (int i = 0; i < invy.size(); i++) {
         if (Baritone.settings().acceptableThrowawayItems.value.contains(((ItemStack)invy.get(i)).getItem())) {
            return i;
         }
      }

      return -1;
   }

   private int bestToolAgainst(Block against, Class<? extends DiggerItem> cla$$) {
      NonNullList<ItemStack> invy = this.ctx.player().getInventory().items;
      int bestInd = -1;
      double bestSpeed = -1.0;

      for (int i = 0; i < invy.size(); i++) {
         ItemStack stack = (ItemStack)invy.get(i);
         if (!stack.isEmpty()
            && (
               !Baritone.settings().itemSaver.value
                  || stack.getDamageValue() + Baritone.settings().itemSaverThreshold.value < stack.getMaxDamage()
                  || stack.getMaxDamage() <= 1
            )
            && cla$$.isInstance(stack.getItem())) {
            double speed = ToolSet.calculateSpeedVsBlock(stack, against.defaultBlockState());
            if (speed > bestSpeed) {
               bestSpeed = speed;
               bestInd = i;
            }
         }
      }

      return bestInd;
   }

   public boolean hasGenericThrowaway() {
      for (Item item : Baritone.settings().acceptableThrowawayItems.value) {
         if (this.throwaway(false, stack -> item.equals(stack.getItem()))) {
            return true;
         }
      }

      return false;
   }

   public boolean selectThrowawayForLocation(boolean select, int x, int y, int z) {
      BlockState maybe = this.baritone.getBuilderProcess().placeAt(x, y, z, this.baritone.bsi.get0(x, y, z));
      if (maybe != null
         && this.throwaway(
            select,
            stack -> stack.getItem() instanceof BlockItem
                  && maybe.equals(
                     ((BlockItem)stack.getItem())
                        .getBlock()
                        .getStateForPlacement(
                           new BlockPlaceContext(
                              new UseOnContext(
                                 this.ctx.world(),
                                 this.ctx.player(),
                                 InteractionHand.MAIN_HAND,
                                 stack,
                                 new BlockHitResult(
                                    new Vec3(this.ctx.player().position().x, this.ctx.player().position().y, this.ctx.player().position().z),
                                    Direction.UP,
                                    this.ctx.playerFeet(),
                                    false
                                 )
                              ) {
                              }
                           )
                        )
                  )
         )) {
         return true;
      } else if (maybe != null
         && this.throwaway(select, stack -> stack.getItem() instanceof BlockItem && ((BlockItem)stack.getItem()).getBlock().equals(maybe.getBlock()))) {
         return true;
      } else {
         for (Item item : Baritone.settings().acceptableThrowawayItems.value) {
            if (this.throwaway(select, stack -> item.equals(stack.getItem()))) {
               return true;
            }
         }

         return false;
      }
   }

   public boolean throwaway(boolean select, Predicate<? super ItemStack> desired) {
      return this.throwaway(select, desired, Baritone.settings().allowInventory.value);
   }

   public boolean throwaway(boolean select, Predicate<? super ItemStack> desired, boolean allowInventory) {
      LocalPlayer p = this.ctx.player();
      NonNullList<ItemStack> inv = p.getInventory().items;

      for (int i = 0; i < 9; i++) {
         ItemStack item = (ItemStack)inv.get(i);
         if (desired.test(item)) {
            if (select) {
               p.getInventory().selected = i;
            }

            return true;
         }
      }

      if (desired.test((ItemStack)p.getInventory().offhand.get(0))) {
         for (int ix = 0; ix < 9; ix++) {
            ItemStack item = (ItemStack)inv.get(ix);
            if (item.isEmpty() || item.getItem() instanceof PickaxeItem) {
               if (select) {
                  p.getInventory().selected = ix;
               }

               return true;
            }
         }
      }

      if (allowInventory) {
         for (int ixx = 9; ixx < 36; ixx++) {
            if (desired.test((ItemStack)inv.get(ixx))) {
               if (select) {
                  this.requestSwapWithHotBar(ixx, 7);
                  p.getInventory().selected = 7;
               }

               return true;
            }
         }
      }

      return false;
   }
}
