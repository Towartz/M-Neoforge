package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.Comparator;
import java.util.List;
import meteordevelopment.meteorclient.events.game.ItemStackTooltipEvent;
import meteordevelopment.meteorclient.events.render.TooltipDataEvent;
import meteordevelopment.meteorclient.mixin.EntityAccessor;
import meteordevelopment.meteorclient.mixin.EntityBucketItemAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.ByteCountDataOutput;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.EChestMemory;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.tooltip.BannerTooltipComponent;
import meteordevelopment.meteorclient.utils.tooltip.BookTooltipComponent;
import meteordevelopment.meteorclient.utils.tooltip.ContainerTooltipComponent;
import meteordevelopment.meteorclient.utils.tooltip.EntityTooltipComponent;
import meteordevelopment.meteorclient.utils.tooltip.MapTooltipComponent;
import meteordevelopment.meteorclient.utils.tooltip.TextTooltipComponent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.BannerPatternItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.component.SuspiciousStewEffects.Entry;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatternLayers.Builder;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity.Occupant;
import net.minecraft.world.level.saveddata.maps.MapId;

public class BetterTooltips extends Module {
   public static final Color ECHEST_COLOR = new Color(0, 50, 50);
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPreviews = this.settings.createGroup("Previews");
   private final SettingGroup sgOther = this.settings.createGroup("Other");
   private final SettingGroup sgHideFlags = this.settings.createGroup("Hide Flags");
   private final Setting<BetterTooltips.DisplayWhen> displayWhen = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("display-when"))
                     .description("When to display previews."))
                  .defaultValue(BetterTooltips.DisplayWhen.Keybind))
               .onChanged(value -> this.updateTooltips = true))
            .build()
      );
   private final Setting<Keybind> keybind = this.sgGeneral
      .add(
         new KeybindSetting.Builder()
            .name("keybind")
            .description("The bind for keybind mode.")
            .defaultValue(Keybind.fromKey(342))
            .visible(() -> this.displayWhen.get() == BetterTooltips.DisplayWhen.Keybind)
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> middleClickOpen = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("middle-click-open")
            .description("Opens a GUI window with the inventory of the storage block or book when you middle click the item.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Boolean> pauseInCreative = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("pause-in-creative")
            .description("Pauses middle click open while the player is in creative mode.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.middleClickOpen::get)
            .build()
      );
   private final Setting<Boolean> shulkers = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("containers")
            .description("Shows a preview of a containers when hovering over it in an inventory.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> shulkerCompactTooltip = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("compact-shulker-tooltip")
            .description("Compacts the lines of the shulker tooltip.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   public final Setting<Boolean> echest = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("echests")
            .description("Shows a preview of your echest when hovering over it in an inventory.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> maps = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("maps")
            .description("Shows a preview of a map when hovering over it in an inventory.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   public final Setting<Double> mapsScale = this.sgPreviews
      .add(
         new DoubleSetting.Builder()
            .name("map-scale")
            .description("The scale of the map preview.")
            .defaultValue(1.0)
            .min(0.001)
            .sliderMax(1.0)
            .visible(this.maps::get)
            .build()
      );
   private final Setting<Boolean> books = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("books")
            .description("Shows contents of a book when hovering over it in an inventory.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> banners = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("banners")
            .description("Shows banners' patterns when hovering over it in an inventory. Also works with shields.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> entitiesInBuckets = this.sgPreviews
      .add(
         new BoolSetting.Builder()
            .name("entities-in-buckets")
            .description("Shows entities in buckets when hovering over it in an inventory.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   public final Setting<Boolean> byteSize = this.sgOther
      .add(
         new BoolSetting.Builder()
            .name("byte-size")
            .description("Displays an item's size in bytes in the tooltip.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> statusEffects = this.sgOther
      .add(
         new BoolSetting.Builder()
            .name("status-effects")
            .description("Adds list of status effects to tooltips of food items.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   private final Setting<Boolean> beehive = this.sgOther
      .add(
         new BoolSetting.Builder()
            .name("beehive")
            .description("Displays information about a beehive or bee nest.")
            .defaultValue(Boolean.valueOf(true))
            .onChanged(value -> this.updateTooltips = true)
            .build()
      );
   public final Setting<Boolean> tooltip = this.sgHideFlags
      .add(new BoolSetting.Builder().name("tooltip").description("Show the tooltip when it's hidden.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> enchantments = this.sgHideFlags
      .add(new BoolSetting.Builder().name("enchantments").description("Show enchantments when it's hidden.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> modifiers = this.sgHideFlags
      .add(new BoolSetting.Builder().name("modifiers").description("Show item modifiers when it's hidden.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> unbreakable = this.sgHideFlags
      .add(new BoolSetting.Builder().name("unbreakable").description("Show \"Unbreakable\" tag when it's hidden.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> canDestroy = this.sgHideFlags
      .add(new BoolSetting.Builder().name("can-destroy").description("Show \"CanDestroy\" tag when it's hidden.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> canPlaceOn = this.sgHideFlags
      .add(new BoolSetting.Builder().name("can-place-on").description("Show \"CanPlaceOn\" tag when it's hidden.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> additional = this.sgHideFlags
      .add(
         new BoolSetting.Builder()
            .name("additional")
            .description("Show potion effects, firework status, book author, etc when it's hidden.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   public final Setting<Boolean> dye = this.sgHideFlags
      .add(new BoolSetting.Builder().name("dye").description("Show dyed item tags when it's hidden.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> upgrades = this.sgHideFlags
      .add(new BoolSetting.Builder().name("armor-trim").description("Show armor trims when it's hidden.").defaultValue(Boolean.valueOf(false)).build());
   private boolean updateTooltips = false;
   private static final ItemStack[] ITEMS = new ItemStack[27];

   public BetterTooltips() {
      super(Categories.Render, "better-tooltips", "Displays more useful tooltips for certain items.");
   }

   @EventHandler
   private void appendTooltip(ItemStackTooltipEvent event) {
      if (!this.tooltip.get() && event.list().isEmpty()) {
         this.appendPreviewTooltipText(event, false);
      } else {
         if (this.statusEffects.get()) {
            if (event.itemStack().getItem() == Items.SUSPICIOUS_STEW) {
               SuspiciousStewEffects stewEffectsComponent = (SuspiciousStewEffects)event.itemStack().get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
               if (stewEffectsComponent != null) {
                  for (Entry effectTag : stewEffectsComponent.effects()) {
                     MobEffectInstance effect = new MobEffectInstance(effectTag.effect(), effectTag.duration(), 0);
                     event.appendStart(this.getStatusText(effect));
                  }
               }
            } else {
               FoodProperties food = (FoodProperties)event.itemStack().get(DataComponents.FOOD);
               if (food != null) {
                  food.effects().forEach(ex -> event.appendStart(this.getStatusText(ex.effect())));
               }
            }
         }

         if (this.beehive.get() && (event.itemStack().getItem() == Items.BEEHIVE || event.itemStack().getItem() == Items.BEE_NEST)) {
            BlockItemStateProperties blockStateComponent = (BlockItemStateProperties)event.itemStack().get(DataComponents.BLOCK_STATE);
            if (blockStateComponent != null) {
               String level = (String)blockStateComponent.properties().get("honey_level");
               event.appendStart(
                  Component.literal(String.format("%sHoney level: %s%s%s.", ChatFormatting.GRAY, ChatFormatting.YELLOW, level, ChatFormatting.GRAY))
               );
            }

            List<Occupant> bees = (List<Occupant>)event.itemStack().get(DataComponents.BEES);
            if (bees != null) {
               event.appendStart(
                  Component.literal(String.format("%sBees: %s%d%s.", ChatFormatting.GRAY, ChatFormatting.YELLOW, bees.size(), ChatFormatting.GRAY))
               );
            }
         }

         if (this.byteSize.get()) {
            try {
               event.itemStack().save(this.mc.player.registryAccess()).write(ByteCountDataOutput.INSTANCE);
               int byteCount = ByteCountDataOutput.INSTANCE.getCount();
               ByteCountDataOutput.INSTANCE.reset();
               String count;
               if (byteCount >= 1024) {
                  count = String.format("%.2f kb", (float)byteCount / 1024.0F);
               } else {
                  count = String.format("%d bytes", byteCount);
               }

               event.appendEnd(Component.literal(count).withStyle(ChatFormatting.GRAY));
            } catch (Exception var6) {
               event.appendEnd(Component.literal("Error getting bytes.").withStyle(ChatFormatting.RED));
            }
         }

         this.appendPreviewTooltipText(event, true);
      }
   }

   @EventHandler
   private void getTooltipData(TooltipDataEvent event) {
      if (this.previewShulkers() && Utils.hasItems(event.itemStack)) {
         Utils.getItemsInContainerItem(event.itemStack, ITEMS);
         event.tooltipData = new ContainerTooltipComponent(ITEMS, Utils.getShulkerColor(event.itemStack));
      } else if (event.itemStack.getItem() == Items.ENDER_CHEST && this.previewEChest()) {
         event.tooltipData = (TooltipComponent)(EChestMemory.isKnown()
            ? new ContainerTooltipComponent((ItemStack[])EChestMemory.ITEMS.toArray(new ItemStack[27]), ECHEST_COLOR)
            : new TextTooltipComponent(Component.literal("Unknown ender chest inventory.").withStyle(ChatFormatting.DARK_RED)));
      } else if (event.itemStack.getItem() == Items.FILLED_MAP && this.previewMaps()) {
         MapId mapIdComponent = (MapId)event.itemStack.get(DataComponents.MAP_ID);
         if (mapIdComponent != null) {
            event.tooltipData = new MapTooltipComponent(mapIdComponent.id());
         }
      } else if ((event.itemStack.getItem() == Items.WRITABLE_BOOK || event.itemStack.getItem() == Items.WRITTEN_BOOK) && this.previewBooks()) {
         Component page = this.getFirstPage(event.itemStack);
         if (page != null) {
            event.tooltipData = new BookTooltipComponent(page);
         }
      } else if (event.itemStack.getItem() instanceof BannerItem && this.previewBanners()) {
         event.tooltipData = new BannerTooltipComponent(event.itemStack);
      } else {
         if (event.itemStack.getItem() instanceof BannerPatternItem bannerPatternItem && this.previewBanners()) {
            event.tooltipData = new BannerTooltipComponent(DyeColor.GRAY, this.createBannerPatternsComponent(bannerPatternItem));
            return;
         }

         if (event.itemStack.getItem() != Items.SHIELD || !this.previewBanners()) {
            if (event.itemStack.getItem() instanceof MobBucketItem bucketItem && this.previewEntities()) {
               EntityType<?> type = ((EntityBucketItemAccessor)bucketItem).getEntityType();
               Entity entity = type.create(this.mc.level);
               if (entity != null) {
                  ((Bucketable)entity).loadFromBucketTag(((CustomData)event.itemStack.get(DataComponents.BUCKET_ENTITY_DATA)).copyTag());
                  ((EntityAccessor)entity).setInWater(true);
                  event.tooltipData = new EntityTooltipComponent(entity);
               }
            }
         } else if (event.itemStack.get(DataComponents.BASE_COLOR) != null
            || !((BannerPatternLayers)event.itemStack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY)).layers().isEmpty()) {
            event.tooltipData = this.createBannerFromShield(event.itemStack);
         }
      }
   }

   public void applyCompactShulkerTooltip(ItemStack shulkerItem, List<Component> tooltip) {
      if (shulkerItem.has(DataComponents.CONTAINER_LOOT)) {
         tooltip.add(Component.literal("???????"));
      }

      if (Utils.hasItems(shulkerItem)) {
         Utils.getItemsInContainerItem(shulkerItem, ITEMS);
         Object2IntMap<Item> counts = new Object2IntOpenHashMap();

         for (ItemStack item : ITEMS) {
            if (!item.isEmpty()) {
               int count = counts.getInt(item.getItem());
               counts.put(item.getItem(), count + item.getCount());
            }
         }

         counts.keySet().stream().sorted(Comparator.comparingInt(value -> -counts.getInt(value))).limit(5L).forEach(itemx -> {
            MutableComponent mutableText = itemx.getDescription().plainCopy();
            mutableText.append(Component.literal(" x").append(String.valueOf(counts.getInt(itemx))).withStyle(ChatFormatting.GRAY));
            tooltip.add(mutableText);
         });
         if (counts.size() > 5) {
            tooltip.add(Component.translatable("container.shulkerBox.more", new Object[]{counts.size() - 5}).withStyle(ChatFormatting.ITALIC));
         }
      }
   }

   private void appendPreviewTooltipText(ItemStackTooltipEvent event, boolean spacer) {
      if (!this.isPressed()
         && (
            this.shulkers.get() && Utils.hasItems(event.itemStack())
               || event.itemStack().getItem() == Items.ENDER_CHEST && this.echest.get()
               || event.itemStack().getItem() == Items.FILLED_MAP && this.maps.get()
               || event.itemStack().getItem() == Items.WRITABLE_BOOK && this.books.get()
               || event.itemStack().getItem() == Items.WRITTEN_BOOK && this.books.get()
               || event.itemStack().getItem() instanceof MobBucketItem && this.entitiesInBuckets.get()
               || event.itemStack().getItem() instanceof BannerItem && this.banners.get()
               || event.itemStack().getItem() instanceof BannerPatternItem && this.banners.get()
               || event.itemStack().getItem() == Items.SHIELD && this.banners.get()
         )) {
         if (spacer) {
            event.appendEnd(Component.literal(""));
         }

         event.appendEnd(Component.literal("Hold " + ChatFormatting.YELLOW + this.keybind + ChatFormatting.RESET + " to preview"));
      }
   }

   private MutableComponent getStatusText(MobEffectInstance effect) {
      MutableComponent text = Component.translatable(effect.getDescriptionId());
      if (effect.getAmplifier() != 0) {
         text.append(
            String.format(
               " %d (%s)", effect.getAmplifier() + 1, MobEffectUtil.formatDuration(effect, 1.0F, this.mc.level.tickRateManager().tickrate()).getString()
            )
         );
      } else {
         text.append(String.format(" (%s)", MobEffectUtil.formatDuration(effect, 1.0F, this.mc.level.tickRateManager().tickrate()).getString()));
      }

      return ((MobEffect)effect.getEffect().value()).isBeneficial() ? text.withStyle(ChatFormatting.BLUE) : text.withStyle(ChatFormatting.RED);
   }

   private Component getFirstPage(ItemStack bookItem) {
      if (bookItem.get(DataComponents.WRITABLE_BOOK_CONTENT) != null) {
         List<Filterable<String>> pages = ((WritableBookContent)bookItem.get(DataComponents.WRITABLE_BOOK_CONTENT)).pages();
         return pages.isEmpty() ? null : Component.literal((String)pages.getFirst().get(false));
      } else if (bookItem.get(DataComponents.WRITTEN_BOOK_CONTENT) != null) {
         List<Filterable<Component>> pages = ((WrittenBookContent)bookItem.get(DataComponents.WRITTEN_BOOK_CONTENT)).pages();
         return pages.isEmpty() ? null : (Component)pages.getFirst().get(false);
      } else {
         return null;
      }
   }

   private BannerPatternLayers createBannerPatternsComponent(BannerPatternItem item) {
      return new Builder()
         .add(this.mc.player.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(item.getBannerPattern()).get(0), DyeColor.WHITE)
         .build();
   }

   private BannerTooltipComponent createBannerFromShield(ItemStack shieldItem) {
      DyeColor dyeColor2 = (DyeColor)shieldItem.getOrDefault(DataComponents.BASE_COLOR, DyeColor.WHITE);
      BannerPatternLayers bannerPatternsComponent = (BannerPatternLayers)shieldItem.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
      return new BannerTooltipComponent(dyeColor2, bannerPatternsComponent);
   }

   public boolean middleClickOpen() {
      return this.isActive() && this.middleClickOpen.get() && (!this.pauseInCreative.get() || !this.mc.player.hasInfiniteMaterials());
   }

   public boolean previewShulkers() {
      return this.isActive() && this.isPressed() && this.shulkers.get();
   }

   public boolean shulkerCompactTooltip() {
      return this.isActive() && this.shulkerCompactTooltip.get();
   }

   private boolean previewEChest() {
      return this.isPressed() && this.echest.get();
   }

   private boolean previewMaps() {
      return this.isPressed() && this.maps.get();
   }

   private boolean previewBooks() {
      return this.isPressed() && this.books.get();
   }

   private boolean previewBanners() {
      return this.isPressed() && this.banners.get();
   }

   private boolean previewEntities() {
      return this.isPressed() && this.entitiesInBuckets.get();
   }

   private boolean isPressed() {
      return this.keybind.get().isPressed() && this.displayWhen.get() == BetterTooltips.DisplayWhen.Keybind
         || this.displayWhen.get() == BetterTooltips.DisplayWhen.Always;
   }

   public boolean updateTooltips() {
      if (this.updateTooltips && this.isActive()) {
         this.updateTooltips = false;
         return true;
      } else {
         return false;
      }
   }

   public static enum DisplayWhen {
      Keybind,
      Always;
   }
}
