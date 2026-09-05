package meteordevelopment.meteorclient.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.mixin.ClientPlayNetworkHandlerAccessor;
import meteordevelopment.meteorclient.mixin.ContainerComponentAccessor;
import meteordevelopment.meteorclient.mixin.MinecraftClientAccessor;
import meteordevelopment.meteorclient.mixin.MinecraftServerAccessor;
import meteordevelopment.meteorclient.mixin.ReloadStateAccessor;
import meteordevelopment.meteorclient.mixin.ResourceReloadLoggerAccessor;
import meteordevelopment.meteorclient.mixininterface.IMinecraftClient;
import meteordevelopment.meteorclient.settings.StatusEffectAmplifierMapSetting;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.BetterTooltips;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.EChestMemory;
import meteordevelopment.meteorclient.utils.render.PeekScreen;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockEntityIterator;
import meteordevelopment.meteorclient.utils.world.ChunkIterator;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.ResourceLoadStateTracker.ReloadState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Range;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

public class Utils {
   public static final Pattern FILE_NAME_INVALID_CHARS_PATTERN = Pattern.compile("[\\s\\\\/:*?\"<>|]");
   public static final Color WHITE = new Color(255, 255, 255);
   private static final Random random = new Random();
   public static boolean firstTimeTitleScreen = true;
   public static boolean isReleasingTrident;
   public static boolean rendering3D = true;
   public static double frameTime;
   public static Screen screenToOpen;
   public static VertexSorting vertexSorter;

   private Utils() {
   }

   @PreInit
   public static void init() {
      MeteorClient.EVENT_BUS.subscribe(Utils.class);
   }

   @EventHandler
   private static void onTick(TickEvent.Post event) {
      if (screenToOpen != null && MeteorClient.mc.screen == null) {
         MeteorClient.mc.setScreen(screenToOpen);
         screenToOpen = null;
      }
   }

   public static Vec3 getPlayerSpeed() {
      if (MeteorClient.mc.player == null) {
         return Vec3.ZERO;
      } else {
         double tX = MeteorClient.mc.player.getX() - MeteorClient.mc.player.xo;
         double tY = MeteorClient.mc.player.getY() - MeteorClient.mc.player.yo;
         double tZ = MeteorClient.mc.player.getZ() - MeteorClient.mc.player.zo;
         Timer timer = Modules.get().get(Timer.class);
         if (timer.isActive()) {
            tX *= timer.getMultiplier();
            tY *= timer.getMultiplier();
            tZ *= timer.getMultiplier();
         }

         tX *= 20.0;
         tY *= 20.0;
         tZ *= 20.0;
         return new Vec3(tX, tY, tZ);
      }
   }

   public static String getWorldTime() {
      if (MeteorClient.mc.level == null) {
         return "00:00";
      } else {
         int ticks = (int)(MeteorClient.mc.level.getDayTime() % 24000L);
         ticks += 6000;
         if (ticks > 24000) {
            ticks -= 24000;
         }

         return String.format("%02d:%02d", ticks / 1000, (int)((double)(ticks % 1000) / 1000.0 * 60.0));
      }
   }

   public static Iterable<ChunkAccess> chunks(boolean onlyWithLoadedNeighbours) {
      return () -> new ChunkIterator(onlyWithLoadedNeighbours);
   }

   public static Iterable<ChunkAccess> chunks() {
      return chunks(false);
   }

   public static Iterable<BlockEntity> blockEntities() {
      return BlockEntityIterator::new;
   }

   public static void getEnchantments(ItemStack itemStack, Object2IntMap<Holder<Enchantment>> enchantments) {
      enchantments.clear();
      if (!itemStack.isEmpty()) {
         for (Entry<Holder<Enchantment>> entry : itemStack.getItem() == Items.ENCHANTED_BOOK
            ? ((ItemEnchantments)itemStack.get(DataComponents.STORED_ENCHANTMENTS)).entrySet()
            : itemStack.getEnchantments().entrySet()) {
            enchantments.put((Holder)entry.getKey(), entry.getIntValue());
         }
      }
   }

   public static int getEnchantmentLevel(ItemStack itemStack, ResourceKey<Enchantment> enchantment) {
      if (itemStack.isEmpty()) {
         return 0;
      } else {
         Object2IntMap<Holder<Enchantment>> itemEnchantments = new Object2IntArrayMap();
         getEnchantments(itemStack, itemEnchantments);
         return getEnchantmentLevel(itemEnchantments, enchantment);
      }
   }

   public static int getEnchantmentLevel(Object2IntMap<Holder<Enchantment>> itemEnchantments, ResourceKey<Enchantment> enchantment) {
      ObjectIterator var2 = Object2IntMaps.fastIterable(itemEnchantments).iterator();

      while (var2.hasNext()) {
         Entry<Holder<Enchantment>> entry = (Entry<Holder<Enchantment>>)var2.next();
         if (((Holder)entry.getKey()).is(enchantment)) {
            return entry.getIntValue();
         }
      }

      return 0;
   }

   @SafeVarargs
   public static boolean hasEnchantments(ItemStack itemStack, ResourceKey<Enchantment>... enchantments) {
      if (itemStack.isEmpty()) {
         return false;
      } else {
         Object2IntMap<Holder<Enchantment>> itemEnchantments = new Object2IntArrayMap();
         getEnchantments(itemStack, itemEnchantments);

         for (ResourceKey<Enchantment> enchantment : enchantments) {
            if (!hasEnchantment(itemEnchantments, enchantment)) {
               return false;
            }
         }

         return true;
      }
   }

   public static boolean hasEnchantment(ItemStack itemStack, ResourceKey<Enchantment> enchantmentKey) {
      if (itemStack.isEmpty()) {
         return false;
      } else {
         Object2IntMap<Holder<Enchantment>> itemEnchantments = new Object2IntArrayMap();
         getEnchantments(itemStack, itemEnchantments);
         return hasEnchantment(itemEnchantments, enchantmentKey);
      }
   }

   private static boolean hasEnchantment(Object2IntMap<Holder<Enchantment>> itemEnchantments, ResourceKey<Enchantment> enchantmentKey) {
      ObjectIterator var2 = itemEnchantments.keySet().iterator();

      while (var2.hasNext()) {
         Holder<Enchantment> enchantment = (Holder<Enchantment>)var2.next();
         if (enchantment.is(enchantmentKey)) {
            return true;
         }
      }

      return false;
   }

   public static int getRenderDistance() {
      return Math.max(
         (Integer)MeteorClient.mc.options.renderDistance().get(), ((ClientPlayNetworkHandlerAccessor)MeteorClient.mc.getConnection()).getChunkLoadDistance()
      );
   }

   public static int getWindowWidth() {
      return MeteorClient.mc.getWindow().getWidth();
   }

   public static int getWindowHeight() {
      return MeteorClient.mc.getWindow().getHeight();
   }

   private static final Deque<Matrix4f> projectionMatrixStack = new ArrayDeque<>();
   private static final Deque<VertexSorting> vertexSorterStack = new ArrayDeque<>();
   private static final Matrix4f prevProjectionMatrix = new Matrix4f();

   public static void unscaledProjection() {
      vertexSorter = RenderSystem.getVertexSorting();
      if (vertexSorter != null) {
         vertexSorterStack.push(vertexSorter);
      }
      Matrix4f currentProj = RenderSystem.getProjectionMatrix();
      if (currentProj != null) {
         prevProjectionMatrix.set(currentProj);
         projectionMatrixStack.push(new Matrix4f(currentProj));
      }
      RenderSystem.setProjectionMatrix(
         new Matrix4f().setOrtho(0.0F, (float)MeteorClient.mc.getWindow().getWidth(), (float)MeteorClient.mc.getWindow().getHeight(), 0.0F, 1000.0F, 21000.0F),
         VertexSorting.ORTHOGRAPHIC_Z
      );
      rendering3D = false;
   }

   public static void scaledProjection() {
      if (!projectionMatrixStack.isEmpty()) {
         Matrix4f proj = projectionMatrixStack.pop();
         VertexSorting sorter = !vertexSorterStack.isEmpty() ? vertexSorterStack.pop() : vertexSorter;
         RenderSystem.setProjectionMatrix(proj, sorter);
      } else {
         RenderSystem.setProjectionMatrix(prevProjectionMatrix, vertexSorter);
      }
      rendering3D = true;
   }

   public static Vec3 vec3d(BlockPos pos) {
      return new Vec3((double)pos.getX(), (double)pos.getY(), (double)pos.getZ());
   }

   public static boolean openContainer(ItemStack itemStack, ItemStack[] contents, boolean pause) {
      if (!hasItems(itemStack) && itemStack.getItem() != Items.ENDER_CHEST) {
         return false;
      } else {
         getItemsInContainerItem(itemStack, contents);
         if (pause) {
            screenToOpen = new PeekScreen(itemStack, contents);
         } else {
            MeteorClient.mc.setScreen(new PeekScreen(itemStack, contents));
         }

         return true;
      }
   }

   public static void getItemsInContainerItem(ItemStack itemStack, ItemStack[] items) {
      if (itemStack.getItem() == Items.ENDER_CHEST) {
         for (int i = 0; i < EChestMemory.ITEMS.size(); i++) {
            items[i] = (ItemStack)EChestMemory.ITEMS.get(i);
         }
      } else {
         Arrays.fill(items, ItemStack.EMPTY);
         DataComponentMap components = itemStack.getComponents();
         if (components.has(DataComponents.CONTAINER)) {
            ContainerComponentAccessor container = (ContainerComponentAccessor)(Object)components.get(DataComponents.CONTAINER);
            NonNullList<ItemStack> stacks = container.getStacks();

            for (int i = 0; i < stacks.size(); i++) {
               if (i >= 0 && i < items.length) {
                  items[i] = (ItemStack)stacks.get(i);
               }
            }
         } else if (components.has(DataComponents.BLOCK_ENTITY_DATA)) {
            CustomData nbt2 = (CustomData)components.get(DataComponents.BLOCK_ENTITY_DATA);
            if (nbt2.contains("Items")) {
               ListTag nbt3 = (ListTag)nbt2.getUnsafe().get("Items");

               for (int ix = 0; ix < nbt3.size(); ix++) {
                  int slot = nbt3.getCompound(ix).getByte("Slot");
                  if (slot >= 0 && slot < items.length) {
                     items[slot] = ItemStack.parseOptional(MeteorClient.mc.player.registryAccess(), nbt3.getCompound(ix));
                  }
               }
            }
         }
      }
   }

   public static Color getShulkerColor(ItemStack shulkerItem) {
      if (shulkerItem.getItem() instanceof BlockItem blockItem) {
         Block block = blockItem.getBlock();
         if (block == Blocks.ENDER_CHEST) {
            return BetterTooltips.ECHEST_COLOR;
         }

         if (block instanceof ShulkerBoxBlock shulkerBlock) {
            DyeColor dye = shulkerBlock.getColor();
            if (dye == null) {
               return WHITE;
            }

            int color = dye.getTextureDiffuseColor();
            return new Color((float)(color >> 16 & 0xFF), (float)(color >> 8 & 0xFF), (float)(color & 0xFF), 1.0F);
         }
      }

      return WHITE;
   }

   public static boolean hasItems(ItemStack itemStack) {
      ContainerComponentAccessor container = (ContainerComponentAccessor)(Object)itemStack.get(DataComponents.CONTAINER);
      if (container != null && !container.getStacks().isEmpty()) {
         return true;
      } else {
         CompoundTag compoundTag = ((CustomData)itemStack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY)).getUnsafe();
         return compoundTag != null && compoundTag.contains("Items", 9);
      }
   }

   public static Reference2IntMap<MobEffect> createStatusEffectMap() {
      return new Reference2IntArrayMap(StatusEffectAmplifierMapSetting.EMPTY_STATUS_EFFECT_MAP);
   }

   public static String getEnchantSimpleName(Holder<Enchantment> enchantment, int length) {
      String name = Names.get(enchantment);
      return name.length() > length ? name.substring(0, length) : name;
   }

   public static boolean searchTextDefault(String text, String filter, boolean caseSensitive) {
      return searchInWords(text, filter) > 0 || searchLevenshteinDefault(text, filter, caseSensitive) < text.length() / 2;
   }

   public static int searchLevenshteinDefault(String text, String filter, boolean caseSensitive) {
      return levenshteinDistance(caseSensitive ? filter : filter.toLowerCase(Locale.ROOT), caseSensitive ? text : text.toLowerCase(Locale.ROOT), 1, 8, 8);
   }

   public static int searchInWords(String text, String filter) {
      if (filter.isEmpty()) {
         return 1;
      } else {
         int wordsFound = 0;
         text = text.toLowerCase(Locale.ROOT);
         String[] words = filter.toLowerCase(Locale.ROOT).split(" ");

         for (String word : words) {
            if (!text.contains(word)) {
               return 0;
            }

            wordsFound += StringUtils.countMatches(text, word);
         }

         return wordsFound;
      }
   }

   public static int levenshteinDistance(String from, String to, int insCost, int subCost, int delCost) {
      int textLength = from.length();
      int filterLength = to.length();
      if (textLength == 0) {
         return filterLength * insCost;
      } else if (filterLength == 0) {
         return textLength * delCost;
      } else {
         int[][] d = new int[textLength + 1][filterLength + 1];

         for (int i = 0; i <= textLength; i++) {
            d[i][0] = i * delCost;
         }

         for (int j = 0; j <= filterLength; j++) {
            d[0][j] = j * insCost;
         }

         for (int i = 1; i <= textLength; i++) {
            for (int j = 1; j <= filterLength; j++) {
               int sCost = d[i - 1][j - 1] + (from.charAt(i - 1) == to.charAt(j - 1) ? 0 : subCost);
               int dCost = d[i - 1][j] + delCost;
               int iCost = d[i][j - 1] + insCost;
               d[i][j] = Math.min(Math.min(dCost, iCost), sCost);
            }
         }

         return d[textLength][filterLength];
      }
   }

   public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
      double dX = x2 - x1;
      double dY = y2 - y1;
      double dZ = z2 - z1;
      return dX * dX + dY * dY + dZ * dZ;
   }

   public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
      double dX = x2 - x1;
      double dY = y2 - y1;
      double dZ = z2 - z1;
      return Math.sqrt(dX * dX + dY * dY + dZ * dZ);
   }

   public static String getFileWorldName() {
      return FILE_NAME_INVALID_CHARS_PATTERN.matcher(getWorldName()).replaceAll("_");
   }

   public static String getWorldName() {
      if (MeteorClient.mc.isLocalServer()) {
         if (MeteorClient.mc.level == null) {
            return "";
         } else {
            File folder = ((MinecraftServerAccessor)MeteorClient.mc.getSingleplayerServer())
               .getSession()
               .getDimensionPath(MeteorClient.mc.level.dimension())
               .toFile();
            if (folder.toPath().relativize(MeteorClient.mc.gameDirectory.toPath()).getNameCount() != 2) {
               folder = folder.getParentFile();
            }

            return folder.getName();
         }
      } else if (MeteorClient.mc.getCurrentServer() != null) {
         return MeteorClient.mc.getCurrentServer().isRealm() ? "realms" : MeteorClient.mc.getCurrentServer().ip;
      } else {
         return "";
      }
   }

   public static String nameToTitle(String name) {
      return Arrays.stream(name.split("-")).<CharSequence>map(StringUtils::capitalize).collect(Collectors.joining(" "));
   }

   public static String titleToName(String title) {
      return title.replace(" ", "-").toLowerCase(Locale.ROOT);
   }

   public static String getKeyName(int key) {
      return switch (key) {
         case -1 -> "Unknown";
         case 32 -> "Space";
         case 39 -> "Apostrophe";
         case 96 -> "Grave Accent";
         case 161 -> "World 1";
         case 162 -> "World 2";
         case 256 -> "Esc";
         case 257 -> "Enter";
         case 258 -> "Tab";
         case 259 -> "Backspace";
         case 260 -> "Insert";
         case 261 -> "Delete";
         case 262 -> "Arrow Right";
         case 263 -> "Arrow Left";
         case 264 -> "Arrow Down";
         case 265 -> "Arrow Up";
         case 266 -> "Page Up";
         case 267 -> "Page Down";
         case 268 -> "Home";
         case 269 -> "End";
         case 280 -> "Caps Lock";
         case 282 -> "Num Lock";
         case 283 -> "Print Screen";
         case 284 -> "Pause";
         case 290 -> "F1";
         case 291 -> "F2";
         case 292 -> "F3";
         case 293 -> "F4";
         case 294 -> "F5";
         case 295 -> "F6";
         case 296 -> "F7";
         case 297 -> "F8";
         case 298 -> "F9";
         case 299 -> "F10";
         case 300 -> "F11";
         case 301 -> "F12";
         case 302 -> "F13";
         case 303 -> "F14";
         case 304 -> "F15";
         case 305 -> "F16";
         case 306 -> "F17";
         case 307 -> "F18";
         case 308 -> "F19";
         case 309 -> "F20";
         case 310 -> "F21";
         case 311 -> "F22";
         case 312 -> "F23";
         case 313 -> "F24";
         case 314 -> "F25";
         case 335 -> "Numpad Enter";
         case 340 -> "Left Shift";
         case 341 -> "Left Control";
         case 342 -> "Left Alt";
         case 343 -> "Left Super";
         case 344 -> "Right Shift";
         case 345 -> "Right Control";
         case 346 -> "Right Alt";
         case 347 -> "Right Super";
         case 348 -> "Menu";
         default -> {
            String keyName = GLFW.glfwGetKeyName(key, 0);
            yield keyName == null ? "Unknown" : StringUtils.capitalize(keyName);
         }
      };
   }

   public static String getButtonName(int button) {
      return switch (button) {
         case -1 -> "Unknown";
         case 0 -> "Mouse Left";
         case 1 -> "Mouse Right";
         case 2 -> "Mouse Middle";
         default -> "Mouse " + button;
      };
   }

   public static byte[] readBytes(InputStream in) {
      try (InputStream input = in) {
         return input.readAllBytes();
      } catch (IOException var6) {
         MeteorClient.LOG.error("Error reading from stream.", var6);
         return new byte[0];
      }
   }

   public static boolean canUpdate() {
      return MeteorClient.mc != null && MeteorClient.mc.level != null && MeteorClient.mc.player != null;
   }

   public static boolean canOpenGui() {
      return canUpdate()
         ? MeteorClient.mc.screen == null
         : MeteorClient.mc.screen instanceof TitleScreen
            || MeteorClient.mc.screen instanceof JoinMultiplayerScreen
            || MeteorClient.mc.screen instanceof SelectWorldScreen;
   }

   public static boolean canCloseGui() {
      return MeteorClient.mc.screen instanceof WidgetScreen;
   }

   public static int random(int min, int max) {
      return random.nextInt(max - min) + min;
   }

   public static double random(double min, double max) {
      return min + (max - min) * random.nextDouble();
   }

   public static void leftClick() {
      MeteorClient.mc.options.keyAttack.setDown(true);
      ((MinecraftClientAccessor)MeteorClient.mc).leftClick();
      MeteorClient.mc.options.keyAttack.setDown(false);
   }

   public static void rightClick() {
      ((IMinecraftClient)MeteorClient.mc).meteor_client$rightClick();
   }

   public static boolean isShulker(Item item) {
      return item == Items.SHULKER_BOX
         || item == Items.WHITE_SHULKER_BOX
         || item == Items.ORANGE_SHULKER_BOX
         || item == Items.MAGENTA_SHULKER_BOX
         || item == Items.LIGHT_BLUE_SHULKER_BOX
         || item == Items.YELLOW_SHULKER_BOX
         || item == Items.LIME_SHULKER_BOX
         || item == Items.PINK_SHULKER_BOX
         || item == Items.GRAY_SHULKER_BOX
         || item == Items.LIGHT_GRAY_SHULKER_BOX
         || item == Items.CYAN_SHULKER_BOX
         || item == Items.PURPLE_SHULKER_BOX
         || item == Items.BLUE_SHULKER_BOX
         || item == Items.BROWN_SHULKER_BOX
         || item == Items.GREEN_SHULKER_BOX
         || item == Items.RED_SHULKER_BOX
         || item == Items.BLACK_SHULKER_BOX;
   }

   public static boolean isThrowable(Item item) {
      return item instanceof ExperienceBottleItem
         || item instanceof BowItem
         || item instanceof CrossbowItem
         || item instanceof SnowballItem
         || item instanceof EggItem
         || item instanceof EnderpearlItem
         || item instanceof SplashPotionItem
         || item instanceof LingeringPotionItem
         || item instanceof FishingRodItem
         || item instanceof TridentItem;
   }

   public static void addEnchantment(ItemStack itemStack, Holder<Enchantment> enchantment, int level) {
      Mutable b = new Mutable(EnchantmentHelper.getEnchantmentsForCrafting(itemStack));
      b.upgrade(enchantment, level);
      EnchantmentHelper.setEnchantments(itemStack, b.toImmutable());
   }

   public static void clearEnchantments(ItemStack itemStack) {
      EnchantmentHelper.updateEnchantments(itemStack, components -> components.removeIf(a -> true));
   }

   public static void removeEnchantment(ItemStack itemStack, Enchantment enchantment) {
      EnchantmentHelper.updateEnchantments(
         itemStack, components -> components.removeIf(enchantment1 -> ((Enchantment)enchantment1.value()).equals(enchantment))
      );
   }

   public static Color lerp(Color first, Color second, @Range(from = 0L,to = 1L) float v) {
      return new Color(
         (int)((float)first.r * (1.0F - v) + (float)second.r * v),
         (int)((float)first.g * (1.0F - v) + (float)second.g * v),
         (int)((float)first.b * (1.0F - v) + (float)second.b * v)
      );
   }

   public static boolean isLoading() {
      ReloadState state = ((ResourceReloadLoggerAccessor)((MinecraftClientAccessor)MeteorClient.mc).getResourceReloadLogger()).getReloadState();
      return state == null || !((ReloadStateAccessor)state).isFinished();
   }

   public static int parsePort(String full) {
      if (full != null && !full.isBlank() && full.contains(":")) {
         int port;
         try {
            port = Integer.parseInt(full.substring(full.lastIndexOf(58) + 1, full.length() - 1));
         } catch (NumberFormatException var3) {
            port = -1;
         }

         return port;
      } else {
         return -1;
      }
   }

   public static String parseAddress(String full) {
      return full != null && !full.isBlank() && full.contains(":") ? full.substring(0, full.lastIndexOf(58)) : full;
   }

   public static boolean resolveAddress(String address) {
      if (address != null && !address.isBlank()) {
         int port = parsePort(address);
         if (port == -1) {
            port = 25565;
         } else {
            address = parseAddress(address);
         }

         return resolveAddress(address, port);
      } else {
         return false;
      }
   }

   public static boolean resolveAddress(String address, int port) {
      if (port > 0 && port <= 65535 && address != null && !address.isBlank()) {
         InetSocketAddress socketAddress = new InetSocketAddress(address, port);
         return !socketAddress.isUnresolved();
      } else {
         return false;
      }
   }

   public static Vector3d set(Vector3d vec, Vec3 v) {
      vec.x = v.x;
      vec.y = v.y;
      vec.z = v.z;
      return vec;
   }

   public static Vector3d set(Vector3d vec, Entity entity, double tickDelta) {
      vec.x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
      vec.y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
      vec.z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
      return vec;
   }

   public static boolean nameFilter(String text, char character) {
      return character >= 'a' && character <= 'z'
         || character >= 'A' && character <= 'Z'
         || character >= '0' && character <= '9'
         || character == '_'
         || character == '-'
         || character == '.'
         || character == ' ';
   }

   public static boolean ipFilter(String text, char character) {
      return text.contains(":") && character == ':'
         ? false
         : character >= 'a' && character <= 'z'
            || character >= 'A' && character <= 'Z'
            || character >= '0' && character <= '9'
            || character == '.'
            || character == '-';
   }
}
