package meteordevelopment.meteorclient.utils.player;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.mixin.CreativeInventoryScreenAccessor;
import meteordevelopment.meteorclient.mixin.HorseScreenHandlerAccessor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.HorseInventoryMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.CreativeModeTabs;

public class SlotUtils {
   public static final int HOTBAR_START = 0;
   public static final int HOTBAR_END = 8;
   public static final int OFFHAND = 45;
   public static final int MAIN_START = 9;
   public static final int MAIN_END = 35;
   public static final int ARMOR_START = 36;
   public static final int ARMOR_END = 39;

   private SlotUtils() {
   }

   public static int indexToId(int i) {
      if (MeteorClient.mc.player == null) {
         return -1;
      } else {
         AbstractContainerMenu handler = MeteorClient.mc.player.containerMenu;
         if (handler instanceof InventoryMenu) {
            return survivalInventory(i);
         } else if (handler instanceof ItemPickerMenu) {
            return creativeInventory(i);
         } else if (handler instanceof ChestMenu genericContainerScreenHandler) {
            return genericContainer(i, genericContainerScreenHandler.getRowCount());
         } else if (handler instanceof CraftingMenu) {
            return craftingTable(i);
         } else if (handler instanceof FurnaceMenu) {
            return furnace(i);
         } else if (handler instanceof BlastFurnaceMenu) {
            return furnace(i);
         } else if (handler instanceof SmokerMenu) {
            return furnace(i);
         } else if (handler instanceof DispenserMenu) {
            return generic3x3(i);
         } else if (handler instanceof EnchantmentMenu) {
            return enchantmentTable(i);
         } else if (handler instanceof BrewingStandMenu) {
            return brewingStand(i);
         } else if (handler instanceof MerchantMenu) {
            return villager(i);
         } else if (handler instanceof BeaconMenu) {
            return beacon(i);
         } else if (handler instanceof AnvilMenu) {
            return anvil(i);
         } else if (handler instanceof HopperMenu) {
            return hopper(i);
         } else if (handler instanceof ShulkerBoxMenu) {
            return genericContainer(i, 3);
         } else if (handler instanceof HorseInventoryMenu) {
            return horse(handler, i);
         } else if (handler instanceof CartographyTableMenu) {
            return cartographyTable(i);
         } else if (handler instanceof GrindstoneMenu) {
            return grindstone(i);
         } else if (handler instanceof LecternMenu) {
            return lectern();
         } else if (handler instanceof LoomMenu) {
            return loom(i);
         } else {
            return handler instanceof StonecutterMenu ? stonecutter(i) : -1;
         }
      }
   }

   private static int survivalInventory(int i) {
      if (isHotbar(i)) {
         return 36 + i;
      } else {
         return isArmor(i) ? 5 + (i - 36) : i;
      }
   }

   private static int creativeInventory(int i) {
      return MeteorClient.mc.screen instanceof CreativeModeInventoryScreen
            && CreativeInventoryScreenAccessor.getSelectedTab() == BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.INVENTORY)
         ? survivalInventory(i)
         : -1;
   }

   private static int genericContainer(int i, int rows) {
      if (isHotbar(i)) {
         return (rows + 3) * 9 + i;
      } else {
         return isMain(i) ? rows * 9 + (i - 9) : -1;
      }
   }

   private static int craftingTable(int i) {
      if (isHotbar(i)) {
         return 37 + i;
      } else {
         return isMain(i) ? i + 1 : -1;
      }
   }

   private static int furnace(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int generic3x3(int i) {
      if (isHotbar(i)) {
         return 36 + i;
      } else {
         return isMain(i) ? i : -1;
      }
   }

   private static int enchantmentTable(int i) {
      if (isHotbar(i)) {
         return 29 + i;
      } else {
         return isMain(i) ? 2 + (i - 9) : -1;
      }
   }

   private static int brewingStand(int i) {
      if (isHotbar(i)) {
         return 32 + i;
      } else {
         return isMain(i) ? 5 + (i - 9) : -1;
      }
   }

   private static int villager(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int beacon(int i) {
      if (isHotbar(i)) {
         return 28 + i;
      } else {
         return isMain(i) ? 1 + (i - 9) : -1;
      }
   }

   private static int anvil(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int hopper(int i) {
      if (isHotbar(i)) {
         return 32 + i;
      } else {
         return isMain(i) ? 5 + (i - 9) : -1;
      }
   }

   private static int horse(AbstractContainerMenu handler, int i) {
      AbstractHorse entity = ((HorseScreenHandlerAccessor)handler).getEntity();
      if (entity instanceof Llama llamaEntity) {
         int strength = llamaEntity.getStrength();
         if (isHotbar(i)) {
            return 2 + 3 * strength + 28 + i;
         }

         if (isMain(i)) {
            return 2 + 3 * strength + 1 + (i - 9);
         }
      } else if (!(entity instanceof Horse) && !(entity instanceof SkeletonHorse) && !(entity instanceof ZombieHorse)) {
         if (entity instanceof AbstractChestedHorse abstractDonkeyEntity) {
            boolean chest = abstractDonkeyEntity.hasChest();
            if (isHotbar(i)) {
               return (chest ? 44 : 29) + i;
            }

            if (isMain(i)) {
               return (chest ? 17 : 2) + (i - 9);
            }
         }
      } else {
         if (isHotbar(i)) {
            return 29 + i;
         }

         if (isMain(i)) {
            return 2 + (i - 9);
         }
      }

      return -1;
   }

   private static int cartographyTable(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int grindstone(int i) {
      if (isHotbar(i)) {
         return 30 + i;
      } else {
         return isMain(i) ? 3 + (i - 9) : -1;
      }
   }

   private static int lectern() {
      return -1;
   }

   private static int loom(int i) {
      if (isHotbar(i)) {
         return 31 + i;
      } else {
         return isMain(i) ? 4 + (i - 9) : -1;
      }
   }

   private static int stonecutter(int i) {
      if (isHotbar(i)) {
         return 29 + i;
      } else {
         return isMain(i) ? 2 + (i - 9) : -1;
      }
   }

   public static boolean isHotbar(int i) {
      return i >= 0 && i <= 8;
   }

   public static boolean isMain(int i) {
      return i >= 9 && i <= 35;
   }

   public static boolean isArmor(int i) {
      return i >= 36 && i <= 39;
   }
}
