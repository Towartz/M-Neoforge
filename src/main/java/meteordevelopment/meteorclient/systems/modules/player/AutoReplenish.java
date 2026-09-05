package meteordevelopment.meteorclient.systems.modules.player;

import java.util.List;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.ItemStackAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.AutoTotem;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoReplenish extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> threshold = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("threshold")
            .description("The threshold of items left this actives at.")
            .defaultValue(Integer.valueOf(8))
            .min(1)
            .sliderRange(1, 63)
            .build()
      );
   private final Setting<Integer> tickDelay = this.sgGeneral
      .add(new IntSetting.Builder().name("delay").description("The tick delay to replenish your hotbar.").defaultValue(Integer.valueOf(1)).min(0).build());
   private final Setting<Boolean> offhand = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("offhand").description("Whether or not to refill your offhand with items.").defaultValue(Boolean.valueOf(true)).build()
      );
   private final Setting<Boolean> unstackable = this.sgGeneral
      .add(new BoolSetting.Builder().name("unstackable").description("Replenishes unstackable items.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> searchHotbar = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("search-hotbar")
            .description("Uses items in your hotbar to replenish if they are the only ones left.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<List<Item>> excludedItems = this.sgGeneral
      .add(new ItemListSetting.Builder().name("excluded-items").description("Items that WILL NOT replenish.").build());
   private final ItemStack[] items = new ItemStack[10];
   private boolean prevHadOpenScreen;
   private int tickDelayLeft;

   public AutoReplenish() {
      super(Categories.Player, "auto-replenish", "Automatically refills items in your hotbar, main hand, or offhand.");

      for (int i = 0; i < this.items.length; i++) {
         this.items[i] = new ItemStack(Items.AIR);
      }
   }

   @Override
   public void onActivate() {
      this.fillItems();
      this.tickDelayLeft = this.tickDelay.get();
      this.prevHadOpenScreen = this.mc.screen != null;
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      if (this.mc.screen == null && this.prevHadOpenScreen) {
         this.fillItems();
      }

      this.prevHadOpenScreen = this.mc.screen != null;
      if (this.mc.player.containerMenu.getItems().size() == 46 && this.mc.screen == null) {
         if (this.tickDelayLeft <= 0) {
            this.tickDelayLeft = this.tickDelay.get();

            for (int i = 0; i < 9; i++) {
               ItemStack stack = this.mc.player.getInventory().getItem(i);
               this.checkSlot(i, stack);
            }

            if (this.offhand.get() && !Modules.get().get(AutoTotem.class).isLocked()) {
               ItemStack stack = this.mc.player.getOffhandItem();
               this.checkSlot(45, stack);
            }
         } else {
            this.tickDelayLeft--;
         }
      }
   }

   private void checkSlot(int slot, ItemStack stack) {
      ItemStack prevStack = this.getItem(slot);
      if (!stack.isEmpty() && stack.isStackable() && !this.excludedItems.get().contains(stack.getItem()) && stack.getCount() <= this.threshold.get()) {
         this.addSlots(slot, this.findItem(stack, slot, this.threshold.get() - stack.getCount() + 1));
      }

      if (stack.isEmpty() && !prevStack.isEmpty() && !this.excludedItems.get().contains(prevStack.getItem())) {
         if (prevStack.isStackable()) {
            this.addSlots(slot, this.findItem(prevStack, slot, this.threshold.get() - stack.getCount() + 1));
         } else if (this.unstackable.get()) {
            this.addSlots(slot, this.findItem(prevStack, slot, 1));
         }
      }

      this.setItem(slot, stack);
   }

   private int findItem(ItemStack itemStack, int excludedSlot, int goodEnoughCount) {
      int slot = -1;
      int count = 0;

      for (int i = this.mc.player.getInventory().getContainerSize() - 2; i >= (this.searchHotbar.get() ? 0 : 9); i--) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (i != excludedSlot && stack.getItem() == itemStack.getItem() && ItemStack.isSameItemSameComponents(itemStack, stack) && stack.getCount() > count) {
            slot = i;
            count = stack.getCount();
            if (count >= goodEnoughCount) {
               break;
            }
         }
      }

      return slot;
   }

   private void addSlots(int to, int from) {
      InvUtils.move().from(from).to(to);
   }

   private void fillItems() {
      for (int i = 0; i < 9; i++) {
         this.setItem(i, this.mc.player.getInventory().getItem(i));
      }

      this.setItem(45, this.mc.player.getOffhandItem());
   }

   private ItemStack getItem(int slot) {
      if (slot == 45) {
         slot = 9;
      }

      return this.items[slot];
   }

   private void setItem(int slot, ItemStack stack) {
      if (slot == 45) {
         slot = 9;
      }

      ItemStack s = this.items[slot];
      ((ItemStackAccessor)(Object)s).setItem(stack.getItem());
      s.setCount(stack.getCount());
      s.applyComponents(stack.getComponents());
      if (stack.isEmpty()) {
         ((ItemStackAccessor)(Object)s).setItem(Items.AIR);
      }
   }
}
