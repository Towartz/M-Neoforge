package meteordevelopment.meteorclient.systems.modules.world;

import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.mixininterface.IAbstractFurnaceScreenHandler;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

public class AutoSmelter extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<Item>> fuelItems = this.sgGeneral
      .add(
         new ItemListSetting.Builder()
            .name("fuel-items")
            .description("Items to use as fuel")
            .defaultValue(Items.COAL, Items.CHARCOAL)
            .filter(this::fuelItemFilter)
            .bypassFilterWhenSavingAndLoading()
            .build()
      );
   private final Setting<List<Item>> smeltableItems = this.sgGeneral
      .add(
         new ItemListSetting.Builder()
            .name("smeltable-items")
            .description("Items to smelt")
            .defaultValue(Items.IRON_ORE, Items.GOLD_ORE, Items.COPPER_ORE, Items.RAW_IRON, Items.RAW_COPPER, Items.RAW_GOLD)
            .filter(this::smeltableItemFilter)
            .bypassFilterWhenSavingAndLoading()
            .build()
      );
   private final Setting<Boolean> disableWhenOutOfItems = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("disable-when-out-of-items")
            .description("Disable the module when you run out of items")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private Map<Item, Integer> fuelTimeMap;

   public AutoSmelter() {
      super(Categories.World, "auto-smelter", "Automatically smelts items from your inventory");
   }

   private boolean fuelItemFilter(Item item) {
      if (!Utils.canUpdate() && this.fuelTimeMap == null) {
         return false;
      } else {
         if (this.fuelTimeMap == null) {
            this.fuelTimeMap = AbstractFurnaceBlockEntity.getFuel();
         }

         return this.fuelTimeMap.containsKey(item);
      }
   }

   private boolean smeltableItemFilter(Item item) {
      return this.mc.level != null
         && this.mc.level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(item.getDefaultInstance()), this.mc.level).isPresent();
   }

   public void tick(AbstractFurnaceMenu c) {
      if (this.mc.player.tickCount % 10 != 0) {
         this.checkFuel(c);
         this.takeResults(c);
         this.insertItems(c);
      }
   }

   private void insertItems(AbstractFurnaceMenu c) {
      ItemStack inputItemStack = ((Slot)c.slots.getFirst()).getItem();
      if (inputItemStack.isEmpty()) {
         int slot = -1;

         for (int i = 3; i < c.slots.size(); i++) {
            ItemStack item = ((Slot)c.slots.get(i)).getItem();
            if (((IAbstractFurnaceScreenHandler)c).isItemSmeltable(item)
               && this.smeltableItems.get().contains(item.getItem())
               && this.smeltableItemFilter(item.getItem())) {
               slot = i;
               break;
            }
         }

         if (this.disableWhenOutOfItems.get() && slot == -1) {
            this.error("You do not have any items in your inventory that can be smelted. Disabling.", new Object[0]);
            this.toggle();
         } else {
            InvUtils.move().fromId(slot).toId(0);
         }
      }
   }

   private void checkFuel(AbstractFurnaceMenu c) {
      ItemStack fuelStack = ((Slot)c.slots.get(1)).getItem();
      if (!(c.getLitProgress() > 0.0F)) {
         if (fuelStack.isEmpty()) {
            int slot = -1;

            for (int i = 3; i < c.slots.size(); i++) {
               ItemStack item = ((Slot)c.slots.get(i)).getItem();
               if (this.fuelItems.get().contains(item.getItem()) && this.fuelItemFilter(item.getItem())) {
                  slot = i;
                  break;
               }
            }

            if (this.disableWhenOutOfItems.get() && slot == -1) {
               this.error("You do not have any fuel in your inventory. Disabling.", new Object[0]);
               this.toggle();
            } else {
               InvUtils.move().fromId(slot).toId(1);
            }
         }
      }
   }

   private void takeResults(AbstractFurnaceMenu c) {
      ItemStack resultStack = ((Slot)c.slots.get(2)).getItem();
      if (!resultStack.isEmpty()) {
         InvUtils.shiftClick().slotId(2);
         if (!resultStack.isEmpty()) {
            this.error("Your inventory is full. Disabling.", new Object[0]);
            this.toggle();
         }
      }
   }
}
