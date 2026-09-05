package meteordevelopment.meteorclient.utils.player;

import java.util.function.Predicate;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixininterface.IClientPlayerInteractionManager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class InvUtils {
   private static final InvUtils.Action ACTION = new InvUtils.Action();
   public static int previousSlot = -1;

   private InvUtils() {
   }

   public static boolean testInMainHand(Predicate<ItemStack> predicate) {
      return predicate.test(MeteorClient.mc.player.getMainHandItem());
   }

   public static boolean testInMainHand(Item... items) {
      return testInMainHand(itemStack -> {
         for (Item item : items) {
            if (itemStack.is(item)) {
               return true;
            }
         }

         return false;
      });
   }

   public static boolean testInOffHand(Predicate<ItemStack> predicate) {
      return predicate.test(MeteorClient.mc.player.getOffhandItem());
   }

   public static boolean testInOffHand(Item... items) {
      return testInOffHand(itemStack -> {
         for (Item item : items) {
            if (itemStack.is(item)) {
               return true;
            }
         }

         return false;
      });
   }

   public static boolean testInHands(Predicate<ItemStack> predicate) {
      return testInMainHand(predicate) || testInOffHand(predicate);
   }

   public static boolean testInHands(Item... items) {
      return testInMainHand(items) || testInOffHand(items);
   }

   public static boolean testInHotbar(Predicate<ItemStack> predicate) {
      if (testInHands(predicate)) {
         return true;
      } else {
         for (int i = 0; i < 9; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (predicate.test(stack)) {
               return true;
            }
         }

         return false;
      }
   }

   public static boolean testInHotbar(Item... items) {
      return testInHotbar(itemStack -> {
         for (Item item : items) {
            if (itemStack.is(item)) {
               return true;
            }
         }

         return false;
      });
   }

   public static FindItemResult findEmpty() {
      return find(ItemStack::isEmpty);
   }

   public static FindItemResult findInHotbar(Item... items) {
      return findInHotbar(itemStack -> {
         for (Item item : items) {
            if (itemStack.getItem() == item) {
               return true;
            }
         }

         return false;
      });
   }

   public static FindItemResult findInHotbar(Predicate<ItemStack> isGood) {
      if (testInOffHand(isGood)) {
         return new FindItemResult(45, MeteorClient.mc.player.getOffhandItem().getCount());
      } else {
         return testInMainHand(isGood)
            ? new FindItemResult(MeteorClient.mc.player.getInventory().selected, MeteorClient.mc.player.getMainHandItem().getCount())
            : find(isGood, 0, 8);
      }
   }

   public static FindItemResult find(Item... items) {
      return find(itemStack -> {
         for (Item item : items) {
            if (itemStack.getItem() == item) {
               return true;
            }
         }

         return false;
      });
   }

   public static FindItemResult find(Predicate<ItemStack> isGood) {
      return MeteorClient.mc.player == null ? new FindItemResult(0, 0) : find(isGood, 0, MeteorClient.mc.player.getInventory().getContainerSize());
   }

   public static FindItemResult find(Predicate<ItemStack> isGood, int start, int end) {
      if (MeteorClient.mc.player == null) {
         return new FindItemResult(0, 0);
      } else {
         int slot = -1;
         int count = 0;

         for (int i = start; i <= end; i++) {
            ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
            if (isGood.test(stack)) {
               if (slot == -1) {
                  slot = i;
               }

               count += stack.getCount();
            }
         }

         return new FindItemResult(slot, count);
      }
   }

   public static FindItemResult findFastestTool(BlockState state) {
      float bestScore = 1.0F;
      int slot = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = MeteorClient.mc.player.getInventory().getItem(i);
         if (stack.isCorrectToolForDrops(state)) {
            float score = stack.getDestroySpeed(state);
            if (score > bestScore) {
               bestScore = score;
               slot = i;
            }
         }
      }

      return new FindItemResult(slot, 1);
   }

   public static boolean swap(int slot, boolean swapBack) {
      if (slot == 45) {
         return true;
      } else if (slot >= 0 && slot <= 8) {
         if (swapBack && previousSlot == -1) {
            previousSlot = MeteorClient.mc.player.getInventory().selected;
         } else if (!swapBack) {
            previousSlot = -1;
         }

         MeteorClient.mc.player.getInventory().selected = slot;
         ((IClientPlayerInteractionManager)MeteorClient.mc.gameMode).meteor$syncSelected();
         return true;
      } else {
         return false;
      }
   }

   public static boolean swapBack() {
      if (previousSlot == -1) {
         return false;
      } else {
         boolean return_ = swap(previousSlot, false);
         previousSlot = -1;
         return return_;
      }
   }

   public static InvUtils.Action move() {
      ACTION.type = ClickType.PICKUP;
      ACTION.two = true;
      return ACTION;
   }

   public static InvUtils.Action click() {
      ACTION.type = ClickType.PICKUP;
      return ACTION;
   }

   public static InvUtils.Action quickSwap() {
      ACTION.type = ClickType.SWAP;
      return ACTION;
   }

   public static InvUtils.Action shiftClick() {
      ACTION.type = ClickType.QUICK_MOVE;
      return ACTION;
   }

   public static InvUtils.Action drop() {
      ACTION.type = ClickType.THROW;
      ACTION.data = 1;
      return ACTION;
   }

   public static void dropHand() {
      if (!MeteorClient.mc.player.containerMenu.getCarried().isEmpty()) {
         MeteorClient.mc
            .gameMode
            .handleInventoryMouseClick(MeteorClient.mc.player.containerMenu.containerId, -999, 0, ClickType.PICKUP, MeteorClient.mc.player);
      }
   }

   public static class Action {
      private ClickType type = null;
      private boolean two = false;
      private int from = -1;
      private int to = -1;
      private int data = 0;
      private boolean isRecursive = false;

      private Action() {
      }

      public InvUtils.Action fromId(int id) {
         this.from = id;
         return this;
      }

      public InvUtils.Action from(int index) {
         return this.fromId(SlotUtils.indexToId(index));
      }

      public InvUtils.Action fromHotbar(int i) {
         return this.from(0 + i);
      }

      public InvUtils.Action fromOffhand() {
         return this.from(45);
      }

      public InvUtils.Action fromMain(int i) {
         return this.from(9 + i);
      }

      public InvUtils.Action fromArmor(int i) {
         return this.from(36 + (3 - i));
      }

      public void toId(int id) {
         this.to = id;
         this.run();
      }

      public void to(int index) {
         this.toId(SlotUtils.indexToId(index));
      }

      public void toHotbar(int i) {
         this.to(0 + i);
      }

      public void toOffhand() {
         this.to(45);
      }

      public void toMain(int i) {
         this.to(9 + i);
      }

      public void toArmor(int i) {
         this.to(36 + (3 - i));
      }

      public void slotId(int id) {
         this.from = this.to = id;
         this.run();
      }

      public void slot(int index) {
         this.slotId(SlotUtils.indexToId(index));
      }

      public void slotHotbar(int i) {
         this.slot(0 + i);
      }

      public void slotOffhand() {
         this.slot(45);
      }

      public void slotMain(int i) {
         this.slot(9 + i);
      }

      public void slotArmor(int i) {
         this.slot(36 + (3 - i));
      }

      private void run() {
         boolean hadEmptyCursor = MeteorClient.mc.player.containerMenu.getCarried().isEmpty();
         if (this.type == ClickType.SWAP) {
            this.data = this.from;
            this.from = this.to;
         }

         if (this.type != null && this.from != -1 && this.to != -1) {
            this.click(this.from);
            if (this.two) {
               this.click(this.to);
            }
         }

         ClickType preType = this.type;
         boolean preTwo = this.two;
         int preFrom = this.from;
         int preTo = this.to;
         this.type = null;
         this.two = false;
         this.from = -1;
         this.to = -1;
         this.data = 0;
         if (!this.isRecursive
            && hadEmptyCursor
            && preType == ClickType.PICKUP
            && preTwo
            && preFrom != -1
            && preTo != -1
            && !MeteorClient.mc.player.containerMenu.getCarried().isEmpty()) {
            this.isRecursive = true;
            InvUtils.click().slotId(preFrom);
            this.isRecursive = false;
         }
      }

      private void click(int id) {
         MeteorClient.mc.gameMode.handleInventoryMouseClick(MeteorClient.mc.player.containerMenu.containerId, id, this.data, this.type, MeteorClient.mc.player);
      }
   }
}
