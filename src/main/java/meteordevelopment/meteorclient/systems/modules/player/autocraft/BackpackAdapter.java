package meteordevelopment.meteorclient.systems.modules.player.autocraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public class BackpackAdapter {
   private static Boolean isTravelersLoaded = null;
   private static Method isWearingMethod = null;
   private static Method createActionPacketMethod = null;
   private static Method getWearingBackpackMethod = null;
   private static Method getBackpackWrapperMethod = null;
   private static Method getItemsMethod = null;
   private static DataComponentType<?> backpackContainerType = null;
   private static DataComponentType<?> toolsContainerType = null;
   private static boolean checkedComponents = false;

   private static final Map<String, Field> FIELD_CACHE = new HashMap<>();
   private static Class<?> backpackWrapperClass = null;
   private static Class<?> craftingUpgradeClass = null;
   private static Method wrapperFromStackMethod = null;
   private static Method wrapperGetUpgradeMethod = null;
   private static Method storageMethod = null;
   private static Method toolsMethod = null;
   private static Method getSlotsMethod = null;
   private static Method getStackInSlotMethod = null;
   private static Method isTabOpenedMethod = null;
   private static Method getDataHolderSlotMethod = null;
   private static Method getDataHolderStackMethod = null;
   private static Method updateModifiableSlotsPositionMethod = null;
   private static DataComponentType<Boolean> tabOpenType = null;
   private static boolean tabOpenTypeChecked = false;
   private static final Map<String, Boolean> CLASS_NAME_CONTAINS_CACHE = new HashMap<>();

   private static List<ItemStack> cachedAllBackpackItems = null;
   private static int cachedBackpackTick = -1;
   private static int cachedContainerId = -1;
   private static boolean eventBusRegistered = false;

   private static void ensureEventBus() {
      if (!eventBusRegistered) {
         eventBusRegistered = true;
         MeteorClient.EVENT_BUS.subscribe(BackpackAdapter.class);
      }
   }

   @EventHandler
   private static void onGameLeft(GameLeftEvent event) {
      clearCache();
   }

   public static void clearCache() {
      cachedAllBackpackItems = null;
      cachedBackpackTick = -1;
      cachedContainerId = -1;
   }

   public static class BackpackCraftInfo {
      public final boolean valid;
      public final int resultSlot;
      public final int gridStart;
      public final int gridEnd;
      public final int storageStart;
      public final int storageEnd;

      public BackpackCraftInfo(boolean valid, int resultSlot, int gridStart, int gridEnd, int storageStart, int storageEnd) {
         this.valid = valid;
         this.resultSlot = resultSlot;
         this.gridStart = gridStart;
         this.gridEnd = gridEnd;
         this.storageStart = storageStart;
         this.storageEnd = storageEnd;
      }
   }

   public static boolean isTravelersBackpackLoaded() {
      if (isTravelersLoaded == null) {
         try {
            Class.forName("com.tiviacz.travelersbackpack.inventory.menu.BackpackBaseMenu", false, BackpackAdapter.class.getClassLoader());
            isTravelersLoaded = true;
         } catch (Throwable t) {
            isTravelersLoaded = false;
         }
      }
      return isTravelersLoaded;
   }

   public static boolean isWearingBackpack() {
      if (!isTravelersBackpackLoaded() || MeteorClient.mc.player == null) return false;
      try {
         if (isWearingMethod == null) {
            Class<?> utilsClass = Class.forName("com.tiviacz.travelersbackpack.capability.AttachmentUtils", false, BackpackAdapter.class.getClassLoader());
            isWearingMethod = utilsClass.getMethod("isWearingBackpack", net.minecraft.world.entity.player.Player.class);
            isWearingMethod.setAccessible(true);
         }
         return (boolean) isWearingMethod.invoke(null, MeteorClient.mc.player);
      } catch (Throwable t) {
         return false;
      }
   }

   public static ItemStack getWornBackpackStack() {
      if (!isTravelersBackpackLoaded() || MeteorClient.mc.player == null) return ItemStack.EMPTY;
      try {
         if (getWearingBackpackMethod == null) {
            Class<?> utilsClass = Class.forName("com.tiviacz.travelersbackpack.capability.AttachmentUtils", false, BackpackAdapter.class.getClassLoader());
            getWearingBackpackMethod = utilsClass.getMethod("getWearingBackpack", net.minecraft.world.entity.player.Player.class);
            getWearingBackpackMethod.setAccessible(true);
         }
         ItemStack stack = (ItemStack) getWearingBackpackMethod.invoke(null, MeteorClient.mc.player);
         return stack != null ? stack : ItemStack.EMPTY;
      } catch (Throwable t) {
         return ItemStack.EMPTY;
      }
   }

   public static boolean openWornBackpack() {
      if (!isWearingBackpack()) return false;
      try {
         if (createActionPacketMethod == null) {
            Class<?> packetClass = Class.forName("com.tiviacz.travelersbackpack.network.ServerboundActionTagPacket", false, BackpackAdapter.class.getClassLoader());
            createActionPacketMethod = packetClass.getMethod("create", int.class, Object[].class);
            createActionPacketMethod.setAccessible(true);
         }
         createActionPacketMethod.invoke(null, 1, new Object[0]);
         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   public static FindItemResult findBackpackInInventory() {
      return InvUtils.find(stack -> {
         if (stack.isEmpty()) return false;
         Item item = stack.getItem();
         ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
         if (id != null && (id.getNamespace().equals("travelersbackpack") || id.getPath().contains("backpack"))) {
            return true;
         }
         return item.getClass().getName().toLowerCase().contains("backpack");
      });
   }

   public static boolean openHeldBackpack() {
      FindItemResult fir = findBackpackInInventory();
      if (!fir.found()) return false;

      if (!fir.isHotbar()) {
         InvUtils.move().from(fir.slot()).toHotbar(0);
         fir = InvUtils.findInHotbar(item -> {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem());
            return id != null && (id.getNamespace().equals("travelersbackpack") || id.getPath().contains("backpack"));
         });
      }

      if (fir.isHotbar()) {
         InvUtils.swap(fir.slot(), false);
         MeteorClient.mc.gameMode.useItem(MeteorClient.mc.player, InteractionHand.MAIN_HAND);
         return true;
      }
      return false;
   }

   public static boolean hasPortableCraftingAvailable() {
      return isWearingBackpack() || findBackpackInInventory().found();
   }

   public static boolean openPortableCrafting() {
      if (isWearingBackpack()) {
         return openWornBackpack();
      }
      return openHeldBackpack();
   }

   public static boolean isBackpackMenu(AbstractContainerMenu menu) {
      if (menu == null) return false;
      String name = menu.getClass().getName();
      return name.contains("Backpack") || name.contains("backpack");
   }

   public static BackpackCraftInfo getBackpackCraftInfo(AbstractContainerMenu menu) {
      if (menu == null || !isBackpackMenu(menu)) {
         return new BackpackCraftInfo(false, -1, -1, -1, -1, -1);
      }

      Class<?> clazz = menu.getClass();
      int storageStart = -1;
      int storageEnd = -1;

      // 1. Storage slot bounds
      int bStart = getIntField(clazz, menu, "BACKPACK_INV_START", -1);
      int bEnd = getIntField(clazz, menu, "BACKPACK_INV_END", -1);
      if (bStart != -1 && bEnd > bStart) {
         storageStart = bStart;
         storageEnd = bEnd - 1; // BACKPACK_INV_END is exclusive in Traveler's Backpack
      } else {
         // Fallback: detect storage slots by container / slot type
         for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (!(s.container instanceof Inventory)) {
               String sName = s.getClass().getName();
               if (sName.contains("Backpack") || sName.contains("ItemHandler")) {
                  if (storageStart == -1) storageStart = i;
                  storageEnd = i;
               }
            }
         }
      }

      // 2. Direct field lookup from BackpackBaseMenu (CRAFTING_GRID_START, CRAFTING_RESULT)
      int cGridStart = getIntField(clazz, menu, "CRAFTING_GRID_START", -1);
      int cResult = getIntField(clazz, menu, "CRAFTING_RESULT", -1);
      if (cGridStart > 0 && cResult > 0 && cResult < menu.slots.size() && (cGridStart + 8) < menu.slots.size()) {
         Slot rSlot = menu.slots.get(cResult);
         Slot gSlot = menu.slots.get(cGridStart);
         // Hidden upgrade tabs in Traveler's Backpack have their y shifted offscreen (y < 0 or y >= 2000)
         if (rSlot.y >= 0 && rSlot.y < 2000 && gSlot.y >= 0 && gSlot.y < 2000) {
            return new BackpackCraftInfo(true, cResult, cGridStart, cGridStart + 8, storageStart, storageEnd);
         }
      }

      // 3. Robust slot scanning fallback (handles anonymous classes, custom container classes)
      int resultSlot = -1;
      int gridStart = -1;
      int gridEnd = -1;
      List<Integer> craftSlots = new ArrayList<>();

      for (int i = 0; i < menu.slots.size(); i++) {
         Slot s = menu.slots.get(i);
         // Hidden upgrade tabs in Traveler's Backpack have their y shifted offscreen (y >= 2000 or y < 0)
         if (s.y < 0 || s.y >= 2000) continue;
         if (s.container instanceof Inventory) continue;

         // Result slot detection:
         // - s is ResultSlot (or subclass, including anonymous inner classes)
         // - s.container is ResultContainer
         // - Class name or superclass hierarchy contains "Result"
         boolean isResult = (s instanceof ResultSlot)
            || (s.container instanceof ResultContainer)
            || isClassOrSuperNameContains(s.getClass(), "Result");

         if (isResult) {
            resultSlot = i;
            continue;
         }

         // Crafting grid slot detection:
         // - s.container is CraftingContainer (CraftingContainerImproved implements CraftingContainer)
         // - Class name or superclass hierarchy contains "CraftingSlot" or "Crafting"
         boolean isCraft = (s.container instanceof CraftingContainer)
            || isClassOrSuperNameContains(s.getClass(), "CraftingSlot");

         if (isCraft) {
            craftSlots.add(i);
         }
      }

      boolean valid = resultSlot != -1 && craftSlots.size() == 9;
      if (valid) {
         gridStart = craftSlots.get(0);
         gridEnd = craftSlots.get(craftSlots.size() - 1);
      } else {
         resultSlot = -1;
         gridStart = -1;
         gridEnd = -1;
      }

      return new BackpackCraftInfo(valid, resultSlot, gridStart, gridEnd, storageStart, storageEnd);
   }

   private static boolean isClassOrSuperNameContains(Class<?> clazz, String pattern) {
      if (clazz == null) return false;
      String key = clazz.getName() + ":" + pattern;
      Boolean cached = CLASS_NAME_CONTAINS_CACHE.get(key);
      if (cached != null) return cached;

      boolean found = false;
      Class<?> cur = clazz;
      while (cur != null && cur != Object.class) {
         if (cur.getName().contains(pattern) || cur.getSimpleName().contains(pattern)) {
            found = true;
            break;
         }
         cur = cur.getSuperclass();
      }
      CLASS_NAME_CONTAINS_CACHE.put(key, found);
      return found;
   }

   private static int getIntField(Class<?> clazz, Object instance, String fieldName, int fallback) {
      String key = clazz.getName() + "#" + fieldName;
      Field f = FIELD_CACHE.get(key);
      if (f == null && !FIELD_CACHE.containsKey(key)) {
         Class<?> cur = clazz;
         while (cur != null && cur != Object.class) {
            try {
               f = cur.getDeclaredField(fieldName);
               f.setAccessible(true);
               break;
            } catch (Throwable t) {
               cur = cur.getSuperclass();
            }
         }
         FIELD_CACHE.put(key, f);
      }
      if (f != null) {
         try {
            return f.getInt(instance);
         } catch (Throwable ignored) {
         }
      }
      return fallback;
   }

   public static int countInBackpackStorage(AbstractContainerMenu menu, Item item) {
      BackpackCraftInfo info = getBackpackCraftInfo(menu);
      if (info.storageStart == -1 || info.storageEnd == -1) return 0;

      int count = 0;
      for (int i = info.storageStart; i <= Math.min(info.storageEnd, menu.slots.size() - 1); i++) {
         ItemStack stack = menu.slots.get(i).getItem();
         if (stack.is(item)) {
            count += stack.getCount();
         }
      }
      return count;
   }

   public static int countIngredientInBackpackStorage(AbstractContainerMenu menu, Ingredient ingredient) {
      BackpackCraftInfo info = getBackpackCraftInfo(menu);
      if (info.storageStart == -1 || info.storageEnd == -1) return 0;

      int count = 0;
      for (int i = info.storageStart; i <= Math.min(info.storageEnd, menu.slots.size() - 1); i++) {
         ItemStack stack = menu.slots.get(i).getItem();
         if (ingredient.test(stack)) {
            count += stack.getCount();
         }
      }
      return count;
   }

   public static List<Integer> getAvailableSourceSlots(AbstractContainerMenu menu, BackpackCraftInfo info) {
      List<Integer> slots = new ArrayList<>();
      // Backpack storage slots
      if (info.storageStart != -1 && info.storageEnd != -1) {
         for (int i = info.storageStart; i <= Math.min(info.storageEnd, menu.slots.size() - 1); i++) {
            slots.add(i);
         }
      }
      // Player inventory and hotbar slots (typically after upgrade slots)
      for (int i = 0; i < menu.slots.size(); i++) {
         if (i == info.resultSlot) continue;
         if (i >= info.gridStart && i <= info.gridEnd) continue;
         if (info.storageStart != -1 && i >= info.storageStart && i <= info.storageEnd) continue;
         Slot s = menu.slots.get(i);
         if (s.container instanceof net.minecraft.world.entity.player.Inventory) {
            slots.add(i);
         }
      }
      return slots;
   }

   public static DataComponentType<?> getBackpackContainerComponentType() {
      if (!checkedComponents) {
         initComponentTypes();
      }
      return backpackContainerType;
   }

   public static DataComponentType<?> getToolsContainerComponentType() {
      if (!checkedComponents) {
         initComponentTypes();
      }
      return toolsContainerType;
   }

   private static void initComponentTypes() {
      checkedComponents = true;
      try {
         ResourceLocation locBag = ResourceLocation.fromNamespaceAndPath("travelersbackpack", "backpack_container");
         backpackContainerType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(locBag);
      } catch (Throwable ignored) {
      }
      try {
         ResourceLocation locTools = ResourceLocation.fromNamespaceAndPath("travelersbackpack", "tools_container");
         toolsContainerType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(locTools);
      } catch (Throwable ignored) {
      }
   }

   @SuppressWarnings("unchecked")
   public static List<ItemStack> getStoredItemsFromStack(ItemStack stack) {
      List<ItemStack> items = new ArrayList<>();
      if (stack == null || stack.isEmpty()) return items;

      // 1. Traveler's Backpack backpack_container component
      DataComponentType<?> bagType = getBackpackContainerComponentType();
      if (bagType != null && stack.has(bagType)) {
         extractFromBackpackContainerContents(stack.get(bagType), items);
      }

      // 2. Traveler's Backpack tools_container component
      DataComponentType<?> toolsType = getToolsContainerComponentType();
      if (toolsType != null && stack.has(toolsType)) {
         extractFromBackpackContainerContents(stack.get(toolsType), items);
      }

      // 3. Vanilla container (shulkers, etc.)
      if (stack.has(DataComponents.CONTAINER)) {
         var container = stack.get(DataComponents.CONTAINER);
         if (container != null) {
            container.nonEmptyItems().forEach(items::add);
         }
      }

      // 4. Vanilla bundle
      if (stack.has(DataComponents.BUNDLE_CONTENTS)) {
         var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
         if (bundle != null) {
            bundle.items().forEach(items::add);
         }
      }

      return items;
   }

   private static void extractFromBackpackContainerContents(Object containerContents, List<ItemStack> out) {
      if (containerContents == null) return;
      try {
         if (getItemsMethod == null) {
            getItemsMethod = containerContents.getClass().getMethod("getItems");
            getItemsMethod.setAccessible(true);
         }
         Object res = getItemsMethod.invoke(containerContents);
         if (res instanceof Iterable<?> iterable) {
            for (Object obj : iterable) {
               if (obj instanceof ItemStack st && !st.isEmpty()) {
                  out.add(st);
               }
            }
         }
      } catch (Throwable ignored) {
      }
   }

   public static List<ItemStack> getWornBackpackItems() {
      List<ItemStack> items = new ArrayList<>();
      if (!isTravelersBackpackLoaded() || MeteorClient.mc.player == null) return items;

      // 1. Try BackpackWrapper for live worn storage & tools
      try {
         if (getBackpackWrapperMethod == null) {
            Class<?> utilsClass = Class.forName("com.tiviacz.travelersbackpack.capability.AttachmentUtils", false, BackpackAdapter.class.getClassLoader());
            getBackpackWrapperMethod = utilsClass.getMethod("getBackpackWrapper", net.minecraft.world.entity.player.Player.class);
            getBackpackWrapperMethod.setAccessible(true);
         }
         Object wrapper = getBackpackWrapperMethod.invoke(null, MeteorClient.mc.player);
         if (wrapper != null) {
            extractFromItemHandler(wrapper, "getStorage", items);
            extractFromItemHandler(wrapper, "getTools", items);
         }
      } catch (Throwable ignored) {
      }

      // 2. If wrapper gave items, return them
      if (!items.isEmpty()) {
         return items;
      }

      // 3. Fallback: inspect worn ItemStack component
      ItemStack wornStack = getWornBackpackStack();
      if (!wornStack.isEmpty()) {
         items.addAll(getStoredItemsFromStack(wornStack));
      }

      return items;
   }

   private static void extractFromItemHandler(Object wrapper, String methodName, List<ItemStack> out) {
      try {
         Method m;
         if ("getStorage".equals(methodName)) {
            if (storageMethod == null) {
               storageMethod = wrapper.getClass().getMethod("getStorage");
               storageMethod.setAccessible(true);
            }
            m = storageMethod;
         } else if ("getTools".equals(methodName)) {
            if (toolsMethod == null) {
               toolsMethod = wrapper.getClass().getMethod("getTools");
               toolsMethod.setAccessible(true);
            }
            m = toolsMethod;
         } else {
            m = wrapper.getClass().getMethod(methodName);
            m.setAccessible(true);
         }

         Object handler = m.invoke(wrapper);
         if (handler != null) {
            if (getSlotsMethod == null) {
               getSlotsMethod = handler.getClass().getMethod("getSlots");
               getSlotsMethod.setAccessible(true);
            }
            if (getStackInSlotMethod == null) {
               getStackInSlotMethod = handler.getClass().getMethod("getStackInSlot", int.class);
               getStackInSlotMethod.setAccessible(true);
            }

            int count = (int) getSlotsMethod.invoke(handler);
            for (int i = 0; i < count; i++) {
               ItemStack st = (ItemStack) getStackInSlotMethod.invoke(handler, i);
               if (st != null && !st.isEmpty()) {
                  out.add(st);
               }
            }
         }
      } catch (Throwable ignored) {
      }
   }

   public static List<ItemStack> getInventoryBackpackItems() {
      List<ItemStack> items = new ArrayList<>();
      if (MeteorClient.mc.player == null) return items;

      Inventory inv = MeteorClient.mc.player.getInventory();
      for (int i = 0; i < inv.getContainerSize(); i++) {
         ItemStack stack = inv.getItem(i);
         if (stack.isEmpty()) continue;

         ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
         boolean isBackpack = (id != null && (id.getNamespace().equals("travelersbackpack") || id.getPath().contains("backpack")))
            || (getBackpackContainerComponentType() != null && stack.has(getBackpackContainerComponentType()))
            || stack.has(DataComponents.CONTAINER)
            || stack.has(DataComponents.BUNDLE_CONTENTS);

         if (isBackpack) {
            items.addAll(getStoredItemsFromStack(stack));
         }
      }

      return items;
   }

   public static List<ItemStack> getAllBackpackItems() {
      if (MeteorClient.mc.player == null) return Collections.emptyList();
      ensureEventBus();

      int currentTick = MeteorClient.mc.player.tickCount;
      int currentContainer = MeteorClient.mc.player.containerMenu != null ? MeteorClient.mc.player.containerMenu.containerId : -1;

      if (cachedAllBackpackItems != null && cachedBackpackTick == currentTick && cachedContainerId == currentContainer) {
         return cachedAllBackpackItems;
      }

      List<ItemStack> items = new ArrayList<>();

      if (MeteorClient.mc.player.containerMenu != null && isBackpackMenu(MeteorClient.mc.player.containerMenu)) {
         BackpackCraftInfo info = getBackpackCraftInfo(MeteorClient.mc.player.containerMenu);
         if (info.storageStart != -1 && info.storageEnd != -1) {
            for (int i = info.storageStart; i <= Math.min(info.storageEnd, MeteorClient.mc.player.containerMenu.slots.size() - 1); i++) {
               ItemStack st = MeteorClient.mc.player.containerMenu.slots.get(i).getItem();
               if (!st.isEmpty()) {
                  items.add(st);
               }
            }
            cachedAllBackpackItems = items;
            cachedBackpackTick = currentTick;
            cachedContainerId = currentContainer;
            return items;
         }
      }

      items.addAll(getWornBackpackItems());
      items.addAll(getInventoryBackpackItems());

      cachedAllBackpackItems = items;
      cachedBackpackTick = currentTick;
      cachedContainerId = currentContainer;
      return items;
   }

   public static int countInAllBackpacks(Item item) {
      if (item == null) return 0;
      int count = 0;
      for (ItemStack st : getAllBackpackItems()) {
         if (st.is(item)) {
            count += st.getCount();
         }
      }
      return count;
   }

   public static int countIngredientInAllBackpacks(Ingredient ingredient) {
      if (ingredient == null || ingredient.isEmpty()) return 0;
      int count = 0;
      for (ItemStack st : getAllBackpackItems()) {
         if (ingredient.test(st)) {
            count += st.getCount();
         }
      }
      return count;
   }

   public static Object getCraftingUpgrade(AbstractContainerMenu menu) {
      if (menu == null || !isTravelersBackpackLoaded()) return null;
      try {
         Method getWrapperMethod = menu.getClass().getMethod("getWrapper");
         getWrapperMethod.setAccessible(true);
         Object wrapper = getWrapperMethod.invoke(menu);
         if (wrapper == null) return null;

         if (craftingUpgradeClass == null) {
            craftingUpgradeClass = Class.forName("com.tiviacz.travelersbackpack.inventory.upgrades.crafting.CraftingUpgrade", false, BackpackAdapter.class.getClassLoader());
         }
         if (wrapperGetUpgradeMethod == null) {
            wrapperGetUpgradeMethod = wrapper.getClass().getMethod("getUpgrade", Class.class);
            wrapperGetUpgradeMethod.setAccessible(true);
         }
         Object opt = wrapperGetUpgradeMethod.invoke(wrapper, craftingUpgradeClass);
         if (opt instanceof java.util.Optional<?> optional && optional.isPresent()) {
            return optional.get();
         }
      } catch (Throwable ignored) {
      }
      return null;
   }

   public static boolean hasCraftingUpgrade(AbstractContainerMenu menu) {
      return getCraftingUpgrade(menu) != null;
   }

   public static boolean isCraftingTabOpen(AbstractContainerMenu menu) {
      Object upgrade = getCraftingUpgrade(menu);
      if (upgrade == null) return false;
      try {
         if (isTabOpenedMethod == null) {
            isTabOpenedMethod = upgrade.getClass().getMethod("isTabOpened");
            isTabOpenedMethod.setAccessible(true);
         }
         return (boolean) isTabOpenedMethod.invoke(upgrade);
      } catch (Throwable ignored) {
         return false;
      }
   }

   public static boolean openCraftingTab(AbstractContainerMenu menu) {
      Object upgrade = getCraftingUpgrade(menu);
      if (upgrade == null) return false;
      try {
         if (getDataHolderSlotMethod == null) {
            getDataHolderSlotMethod = upgrade.getClass().getMethod("getDataHolderSlot");
            getDataHolderSlotMethod.setAccessible(true);
         }
         int dataHolderSlot = (int) getDataHolderSlotMethod.invoke(upgrade);

         if (createActionPacketMethod == null) {
            Class<?> packetClass = Class.forName("com.tiviacz.travelersbackpack.network.ServerboundActionTagPacket", false, BackpackAdapter.class.getClassLoader());
            createActionPacketMethod = packetClass.getMethod("create", int.class, Object[].class);
            createActionPacketMethod.setAccessible(true);
         }
         // ActionType 0 = UPGRADE_TAB, Arg0 = dataHolderSlot, Arg1 = true (open), Arg2 = 0
         createActionPacketMethod.invoke(null, 0, new Object[] { dataHolderSlot, true, 0 });

         // Optimistic client-side slot repositioning: update client stack component TAB_OPEN and reposition
         try {
            if (getDataHolderStackMethod == null) {
               getDataHolderStackMethod = upgrade.getClass().getMethod("getDataHolderStack");
               getDataHolderStackMethod.setAccessible(true);
            }
            ItemStack stack = (ItemStack) getDataHolderStackMethod.invoke(upgrade);
            if (stack != null && !stack.isEmpty()) {
               if (!tabOpenTypeChecked) {
                  tabOpenTypeChecked = true;
                  try {
                     Class<?> modComponentsClass = Class.forName("com.tiviacz.travelersbackpack.init.ModDataComponents", false, BackpackAdapter.class.getClassLoader());
                     Field tabOpenField = modComponentsClass.getField("TAB_OPEN");
                     tabOpenField.setAccessible(true);
                     Object tabOpenHolder = tabOpenField.get(null);
                     Method getComponentTypeMethod = tabOpenHolder.getClass().getMethod("get");
                     getComponentTypeMethod.setAccessible(true);
                     tabOpenType = (DataComponentType<Boolean>) getComponentTypeMethod.invoke(tabOpenHolder);
                  } catch (Throwable ignored) {
                  }
               }
               if (tabOpenType != null) {
                  stack.set(tabOpenType, true);
               }
            }
            if (updateModifiableSlotsPositionMethod == null) {
               updateModifiableSlotsPositionMethod = menu.getClass().getMethod("updateModifiableSlotsPosition", int.class);
               updateModifiableSlotsPositionMethod.setAccessible(true);
            }
            updateModifiableSlotsPositionMethod.invoke(menu, dataHolderSlot);
         } catch (Throwable ignored) {
         }

         return true;
      } catch (Throwable t) {
         return false;
      }
   }

   public static boolean hasCraftingUpgrade(ItemStack stack) {
      if (stack == null || stack.isEmpty() || !isTravelersBackpackLoaded()) return false;
      try {
         if (backpackWrapperClass == null) {
            backpackWrapperClass = Class.forName("com.tiviacz.travelersbackpack.inventory.BackpackWrapper", false, BackpackAdapter.class.getClassLoader());
         }
         if (wrapperFromStackMethod == null) {
            wrapperFromStackMethod = backpackWrapperClass.getMethod("fromStack", ItemStack.class);
            wrapperFromStackMethod.setAccessible(true);
         }
         Object wrapper = wrapperFromStackMethod.invoke(null, stack);
         if (wrapper == null) return false;

         if (craftingUpgradeClass == null) {
            craftingUpgradeClass = Class.forName("com.tiviacz.travelersbackpack.inventory.upgrades.crafting.CraftingUpgrade", false, BackpackAdapter.class.getClassLoader());
         }
         if (wrapperGetUpgradeMethod == null) {
            wrapperGetUpgradeMethod = wrapper.getClass().getMethod("getUpgrade", Class.class);
            wrapperGetUpgradeMethod.setAccessible(true);
         }
         Object opt = wrapperGetUpgradeMethod.invoke(wrapper, craftingUpgradeClass);
         return opt instanceof java.util.Optional<?> optional && optional.isPresent();
      } catch (Throwable ignored) {
         return false;
      }
   }

   public static boolean isWornBackpackCraftingCapable() {
      if (!isWearingBackpack()) return false;
      try {
         if (getBackpackWrapperMethod == null) {
            Class<?> utilsClass = Class.forName("com.tiviacz.travelersbackpack.capability.AttachmentUtils", false, BackpackAdapter.class.getClassLoader());
            getBackpackWrapperMethod = utilsClass.getMethod("getBackpackWrapper", net.minecraft.world.entity.player.Player.class);
            getBackpackWrapperMethod.setAccessible(true);
         }
         Object wrapper = getBackpackWrapperMethod.invoke(null, MeteorClient.mc.player);
         if (wrapper == null) return false;

         if (craftingUpgradeClass == null) {
            craftingUpgradeClass = Class.forName("com.tiviacz.travelersbackpack.inventory.upgrades.crafting.CraftingUpgrade", false, BackpackAdapter.class.getClassLoader());
         }
         if (wrapperGetUpgradeMethod == null) {
            wrapperGetUpgradeMethod = wrapper.getClass().getMethod("getUpgrade", Class.class);
            wrapperGetUpgradeMethod.setAccessible(true);
         }
         Object opt = wrapperGetUpgradeMethod.invoke(wrapper, craftingUpgradeClass);
         return opt instanceof java.util.Optional<?> optional && optional.isPresent();
      } catch (Throwable ignored) {
         return false;
      }
   }

   public static boolean hasCraftingCapableBackpack() {
      if (isWornBackpackCraftingCapable()) return true;
      FindItemResult fir = findBackpackInInventory();
      if (fir.found() && MeteorClient.mc.player != null) {
         ItemStack stack = MeteorClient.mc.player.getInventory().getItem(fir.slot());
         return hasCraftingUpgrade(stack);
      }
      return false;
   }
}
