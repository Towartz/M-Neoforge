package meteordevelopment.meteorclient.systems.modules.render;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnchantmentListSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.NameProtect;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.misc.Names;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class Nametags extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPlayers = this.settings.createGroup("Players");
   private final SettingGroup sgItems = this.settings.createGroup("Items");
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
      .add(
         new EntityTypeListSetting.Builder()
            .name("entities")
            .description("Select entities to draw nametags on.")
            .defaultValue(EntityType.PLAYER, EntityType.ITEM)
            .build()
      );
   private final Setting<Double> scale = this.sgGeneral
      .add(new DoubleSetting.Builder().name("scale").description("The scale of the nametag.").defaultValue(1.1).min(0.1).build());
   private final Setting<Boolean> ignoreSelf = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ignore-self")
            .description("Ignore yourself when in third person or freecam.")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<Boolean> ignoreFriends = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-friends").description("Ignore rendering nametags for friends.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> ignoreBots = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-bots").description("Only render non-bot nametags.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> culling = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("culling")
            .description("Only render a certain number of nametags at a certain distance.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Double> maxCullRange = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("culling-range")
            .description("Only render nametags within this distance of your player.")
            .defaultValue(20.0)
            .min(0.0)
            .sliderMax(200.0)
            .visible(this.culling::get)
            .build()
      );
   private final Setting<Integer> maxCullCount = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("culling-count")
            .description("Only render this many nametags.")
            .defaultValue(Integer.valueOf(50))
            .min(1)
            .sliderRange(1, 100)
            .visible(this.culling::get)
            .build()
      );
   private final Setting<Boolean> displayHealth = this.sgPlayers
      .add(new BoolSetting.Builder().name("health").description("Shows the player's health.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> displayGameMode = this.sgPlayers
      .add(new BoolSetting.Builder().name("gamemode").description("Shows the player's GameMode.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<Boolean> displayDistance = this.sgPlayers
      .add(
         new BoolSetting.Builder().name("distance").description("Shows the distance between you and the player.").defaultValue(Boolean.valueOf(false)).build()
      );
   private final Setting<Boolean> displayPing = this.sgPlayers
      .add(new BoolSetting.Builder().name("ping").description("Shows the player's ping.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Boolean> displayItems = this.sgPlayers
      .add(
         new BoolSetting.Builder().name("items").description("Displays armor and hand items above the name tags.").defaultValue(Boolean.valueOf(true)).build()
      );
   private final Setting<Double> itemSpacing = this.sgPlayers
      .add(
         new DoubleSetting.Builder()
            .name("item-spacing")
            .description("The spacing between items.")
            .defaultValue(2.0)
            .range(0.0, 10.0)
            .visible(this.displayItems::get)
            .build()
      );
   private final Setting<Boolean> ignoreEmpty = this.sgPlayers
      .add(
         new BoolSetting.Builder()
            .name("ignore-empty-slots")
            .description("Doesn't add spacing where an empty item stack would be.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.displayItems::get)
            .build()
      );
   private final Setting<Nametags.Durability> itemDurability = this.sgPlayers
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("durability"))
                     .description("Displays item durability as either a total, percentage, or neither."))
                  .defaultValue(Nametags.Durability.None))
               .visible(this.displayItems::get))
            .build()
      );
   private final Setting<Boolean> displayEnchants = this.sgPlayers
      .add(
         new BoolSetting.Builder()
            .name("display-enchants")
            .description("Displays item enchantments on the items.")
            .defaultValue(Boolean.valueOf(false))
            .visible(this.displayItems::get)
            .build()
      );
   private final Setting<Set<ResourceKey<Enchantment>>> shownEnchantments = this.sgPlayers
      .add(
         new EnchantmentListSetting.Builder()
            .name("shown-enchantments")
            .description("The enchantments that are shown on nametags.")
            .visible(() -> this.displayItems.get() && this.displayEnchants.get())
            .defaultValue(Enchantments.PROTECTION, Enchantments.BLAST_PROTECTION, Enchantments.FIRE_PROTECTION, Enchantments.PROJECTILE_PROTECTION)
            .build()
      );
   private final Setting<Nametags.Position> enchantPos = this.sgPlayers
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("enchantment-position"))
                     .description("Where the enchantments are rendered."))
                  .defaultValue(Nametags.Position.Above))
               .visible(() -> this.displayItems.get() && this.displayEnchants.get()))
            .build()
      );
   private final Setting<Integer> enchantLength = this.sgPlayers
      .add(
         new IntSetting.Builder()
            .name("enchant-name-length")
            .description("The length enchantment names are trimmed to.")
            .defaultValue(Integer.valueOf(3))
            .range(1, 5)
            .sliderRange(1, 5)
            .visible(() -> this.displayItems.get() && this.displayEnchants.get())
            .build()
      );
   private final Setting<Double> enchantTextScale = this.sgPlayers
      .add(
         new DoubleSetting.Builder()
            .name("enchant-text-scale")
            .description("The scale of the enchantment text.")
            .defaultValue(1.0)
            .range(0.1, 2.0)
            .sliderRange(0.1, 2.0)
            .visible(() -> this.displayItems.get() && this.displayEnchants.get())
            .build()
      );
   private final Setting<Boolean> itemCount = this.sgItems
      .add(new BoolSetting.Builder().name("show-count").description("Displays the number of items in the stack.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<SettingColor> background = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("background-color")
            .description("The color of the nametag background.")
            .defaultValue(new SettingColor(0, 0, 0, 75))
            .build()
      );
   private final Setting<SettingColor> nameColor = this.sgRender
      .add(new ColorSetting.Builder().name("name-color").description("The color of the nametag names.").defaultValue(new SettingColor()).build());
   private final Setting<SettingColor> pingColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("ping-color")
            .description("The color of the nametag ping.")
            .defaultValue(new SettingColor(20, 170, 170))
            .visible(this.displayPing::get)
            .build()
      );
   private final Setting<SettingColor> gamemodeColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("gamemode-color")
            .description("The color of the nametag gamemode.")
            .defaultValue(new SettingColor(232, 185, 35))
            .visible(this.displayGameMode::get)
            .build()
      );
   private final Setting<Nametags.DistanceColorMode> distanceColorMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("distance-color-mode"))
                     .description("The mode to color the nametag distance with."))
                  .defaultValue(Nametags.DistanceColorMode.Gradient))
               .visible(this.displayDistance::get))
            .build()
      );
   private final Setting<SettingColor> distanceColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("distance-color")
            .description("The color of the nametag distance.")
            .defaultValue(new SettingColor(150, 150, 150))
            .visible(() -> this.displayDistance.get() && this.distanceColorMode.get() == Nametags.DistanceColorMode.Flat)
            .build()
      );
   private final Color WHITE = new Color(255, 255, 255);
   private final Color RED = new Color(255, 25, 25);
   private final Color AMBER = new Color(255, 105, 25);
   private final Color GREEN = new Color(25, 252, 25);
   private final Color GOLD = new Color(232, 185, 35);
   private final Vector3d pos = new Vector3d();
   private final double[] itemWidths = new double[6];
   private final List<Entity> entityList = new ArrayList<>();

   public Nametags() {
      super(Categories.Render, "nametags", "Displays customizable nametags above players, items and other entities.");
   }

   private static String ticksToTime(int ticks) {
      if (ticks > 72000) {
         int h = ticks / 20 / 3600;
         return h + " h";
      } else if (ticks > 1200) {
         int m = ticks / 20 / 60;
         return m + " m";
      } else {
         int s = ticks / 20;
         int ms = ticks % 20 / 2;
         return s + "." + ms + " s";
      }
   }

   @EventHandler
   private void onTick(TickEvent.Post event) {
      this.entityList.clear();
      boolean freecamNotActive = !Modules.get().isActive(Freecam.class);
      boolean notThirdPerson = this.mc.options.getCameraType().isFirstPerson();
      Vec3 cameraPos = this.mc.gameRenderer.getMainCamera().getPosition();

      for (Entity entity : this.mc.level.entitiesForRendering()) {
         EntityType<?> type = entity.getType();
         if (this.entities.get().contains(type)
            && (
               type != EntityType.PLAYER
                  || (!this.ignoreSelf.get() && (!freecamNotActive || !notThirdPerson) || entity != this.mc.player)
                     && (EntityUtils.getGameMode((Player)entity) != null || !this.ignoreBots.get())
                     && (!Friends.get().isFriend((Player)entity) || !this.ignoreFriends.get())
            )
            && (!this.culling.get() || PlayerUtils.isWithinCamera(entity, this.maxCullRange.get()))) {
            this.entityList.add(entity);
         }
      }

      this.entityList.sort(Comparator.comparing(e -> e.distanceToSqr(cameraPos)));
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      int count = this.getRenderCount();
      boolean shadow = Config.get().customFont.get();

      for (int i = count - 1; i > -1; i--) {
         Entity entity = this.entityList.get(i);
         Utils.set(this.pos, entity, (double)event.tickDelta);
         this.pos.add(0.0, this.getHeight(entity), 0.0);
         EntityType<?> type = entity.getType();
         if (NametagUtils.to2D(this.pos, this.scale.get())) {
            if (type == EntityType.PLAYER) {
               this.renderNametagPlayer(event, (Player)entity, shadow);
            } else if (type == EntityType.ITEM) {
               this.renderNametagItem(((ItemEntity)entity).getItem(), shadow);
            } else if (type == EntityType.ITEM_FRAME) {
               this.renderNametagItem(((ItemFrame)entity).getItem(), shadow);
            } else if (type == EntityType.TNT) {
               this.renderTntNametag(ticksToTime(((PrimedTnt)entity).getFuse()), shadow);
            } else if (type == EntityType.TNT_MINECART && ((MinecartTNT)entity).isPrimed()) {
               this.renderTntNametag(ticksToTime(((MinecartTNT)entity).getFuse()), shadow);
            } else if (entity instanceof LivingEntity) {
               this.renderGenericLivingNametag((LivingEntity)entity, shadow);
            } else {
               this.renderGenericNametag(entity, shadow);
            }
         }
      }
   }

   private int getRenderCount() {
      int count = this.culling.get() ? this.maxCullCount.get() : this.entityList.size();
      return Mth.clamp(count, 0, this.entityList.size());
   }

   @Override
   public String getInfoString() {
      return Integer.toString(this.getRenderCount());
   }

   private double getHeight(Entity entity) {
      double height = (double)entity.getEyeHeight(entity.getPose());
      if (entity.getType() != EntityType.ITEM && entity.getType() != EntityType.ITEM_FRAME) {
         height += 0.5;
      } else {
         height += 0.2;
      }

      return height;
   }

   private void renderNametagPlayer(Render2DEvent event, Player player, boolean shadow) {
      TextRenderer text = TextRenderer.get();
      NametagUtils.begin(this.pos, event.drawContext);
      GameType gm = EntityUtils.getGameMode(player);
      String gmText = "BOT";
      if (gm != null) {
         gmText = switch (gm) {
            case SPECTATOR -> "Sp";
            case SURVIVAL -> "S";
            case CREATIVE -> "C";
            case ADVENTURE -> "A";
            default -> throw new MatchException(null, null);
         };
      }

      gmText = "[" + gmText + "] ";
      Color nameColor = PlayerUtils.getPlayerColor(player, this.nameColor.get());
      String name;
      if (player == this.mc.player) {
         name = Modules.get().get(NameProtect.class).getName(player.getName().getString());
      } else {
         name = player.getName().getString();
      }

      float absorption = player.getAbsorptionAmount();
      int health = Math.round(player.getHealth() + absorption);
      double healthPercentage = (double)((float)health / (player.getMaxHealth() + absorption));
      String healthText = " " + health;
      Color healthColor;
      if (healthPercentage <= 0.333) {
         healthColor = this.RED;
      } else if (healthPercentage <= 0.666) {
         healthColor = this.AMBER;
      } else {
         healthColor = this.GREEN;
      }

      int ping = EntityUtils.getPing(player);
      String pingText = " [" + ping + "ms]";
      double dist = (double)Math.round(PlayerUtils.distanceToCamera(player) * 10.0) / 10.0;
      String distText = " " + dist + "m";
      double gmWidth = text.getWidth(gmText, shadow);
      double nameWidth = text.getWidth(name, shadow);
      double healthWidth = text.getWidth(healthText, shadow);
      double pingWidth = text.getWidth(pingText, shadow);
      double distWidth = text.getWidth(distText, shadow);
      double width = nameWidth;
      boolean renderPlayerDistance = player != this.mc.cameraEntity || Modules.get().isActive(Freecam.class);
      if (this.displayHealth.get()) {
         width = nameWidth + healthWidth;
      }

      if (this.displayGameMode.get()) {
         width += gmWidth;
      }

      if (this.displayPing.get()) {
         width += pingWidth;
      }

      if (this.displayDistance.get() && renderPlayerDistance) {
         width += distWidth;
      }

      double widthHalf = width / 2.0;
      double heightDown = text.getHeight(shadow);
      this.drawBg(-widthHalf, -heightDown, width, heightDown);
      text.beginBig();
      double hX = -widthHalf;
      double hY = -heightDown;
      if (this.displayGameMode.get()) {
         hX = text.render(gmText, hX, hY, this.gamemodeColor.get(), shadow);
      }

      hX = text.render(name, hX, hY, nameColor, shadow);
      if (this.displayHealth.get()) {
         hX = text.render(healthText, hX, hY, healthColor, shadow);
      }

      if (this.displayPing.get()) {
         hX = text.render(pingText, hX, hY, this.pingColor.get(), shadow);
      }

      if (this.displayDistance.get() && renderPlayerDistance) {
         switch ((Nametags.DistanceColorMode)this.distanceColorMode.get()) {
            case Gradient:
               text.render(distText, hX, hY, EntityUtils.getColorFromDistance(player), shadow);
               break;
            case Flat:
               text.render(distText, hX, hY, this.distanceColor.get(), shadow);
         }
      }

      text.end();
      if (this.displayItems.get()) {
         Arrays.fill(this.itemWidths, 0.0);
         boolean hasItems = false;
         int maxEnchantCount = 0;

         for (int i = 0; i < 6; i++) {
            ItemStack itemStack = this.getItem(player, i);
            if (this.itemWidths[i] == 0.0 && (!this.ignoreEmpty.get() || !itemStack.isEmpty())) {
               this.itemWidths[i] = 32.0 + this.itemSpacing.get();
            }

            if (!itemStack.isEmpty()) {
               hasItems = true;
            }

            if (this.displayEnchants.get()) {
               ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(itemStack);
               int size = 0;

               for (Holder<Enchantment> enchantment : enchantments.keySet()) {
                  if (!enchantment.unwrapKey().isPresent() || this.shownEnchantments.get().contains(enchantment.unwrapKey().get())) {
                     String enchantName = Utils.getEnchantSimpleName(enchantment, this.enchantLength.get()) + " " + enchantments.getLevel(enchantment);
                     this.itemWidths[i] = Math.max(this.itemWidths[i], text.getWidth(enchantName, shadow) / 2.0);
                     size++;
                  }
               }

               maxEnchantCount = Math.max(maxEnchantCount, size);
            }
         }

         double itemsHeight = (double)(hasItems ? 32 : 0);
         double itemWidthTotal = 0.0;

         for (double w : this.itemWidths) {
            itemWidthTotal += w;
         }

         double itemWidthHalf = itemWidthTotal / 2.0;
         double y = -heightDown - 7.0 - itemsHeight;
         double x = -itemWidthHalf;

         for (int i = 0; i < 6; i++) {
            ItemStack stack = this.getItem(player, i);
            RenderUtils.drawItem(event.drawContext, stack, (int)x, (int)y, 2.0F, true);
            if (stack.isDamageableItem() && this.itemDurability.get() != Nametags.Durability.None) {
               text.begin(0.75, false, true);

               String damageText = switch ((Nametags.Durability)this.itemDurability.get()) {
                  case Total -> Integer.toString(stack.getMaxDamage() - stack.getDamageValue());
                  case Percentage -> String.format("%.0f%%", (float)(stack.getMaxDamage() - stack.getDamageValue()) * 100.0F / (float)stack.getMaxDamage());
                  default -> "err";
               };
               Color damageColor = new Color(stack.getBarColor());
               text.render(damageText, (double)((int)x), (double)((int)y), damageColor.a(255), true);
               text.end();
            }

            if (maxEnchantCount > 0 && this.displayEnchants.get()) {
               text.begin(0.5 * this.enchantTextScale.get(), false, true);
               ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
               Object2IntMap<Holder<Enchantment>> enchantmentsToShow = new Object2IntOpenHashMap();

               for (Holder<Enchantment> enchantmentx : enchantments.keySet()) {
                  if (enchantmentx.is(this.shownEnchantments.get()::contains)) {
                     enchantmentsToShow.put(enchantmentx, enchantments.getLevel(enchantmentx));
                  }
               }

               double aW = this.itemWidths[i];
               double enchantY = 0.0;

               double addY = switch ((Nametags.Position)this.enchantPos.get()) {
                  case Above -> -((double)(enchantmentsToShow.size() + 1) * text.getHeight(shadow));
                  case OnTop -> (itemsHeight - (double)enchantmentsToShow.size() * text.getHeight(shadow)) / 2.0;
               };

               for (ObjectIterator var65 = Object2IntMaps.fastIterable(enchantmentsToShow).iterator(); var65.hasNext(); enchantY += text.getHeight(shadow)) {
                  Entry<Holder<Enchantment>> entry = (Entry<Holder<Enchantment>>)var65.next();
                  String enchantName = Utils.getEnchantSimpleName((Holder<Enchantment>)entry.getKey(), this.enchantLength.get()) + " " + entry.getIntValue();
                  Color enchantColor = this.WHITE;
                  if (((Holder)entry.getKey()).is(EnchantmentTags.CURSE)) {
                     enchantColor = this.RED;
                  }
                  double enchantX = switch ((Nametags.Position)this.enchantPos.get()) {
                     case Above -> x + aW / 2.0 - text.getWidth(enchantName, shadow) / 2.0;
                     case OnTop -> x + (aW - text.getWidth(enchantName, shadow)) / 2.0;
                  };
                  text.render(enchantName, enchantX, y + addY + enchantY, enchantColor, shadow);
               }

               text.end();
            }

            x += this.itemWidths[i];
         }
      } else if (this.displayEnchants.get()) {
         this.displayEnchants.set(false);
      }

      NametagUtils.end(event.drawContext);
   }

   private void renderNametagItem(ItemStack stack, boolean shadow) {
      if (!stack.isEmpty()) {
         TextRenderer text = TextRenderer.get();
         NametagUtils.begin(this.pos);
         String name = Names.get(stack);
         String count = " x" + stack.getCount();
         double nameWidth = text.getWidth(name, shadow);
         double countWidth = text.getWidth(count, shadow);
         double heightDown = text.getHeight(shadow);
         double width = nameWidth;
         if (this.itemCount.get()) {
            width = nameWidth + countWidth;
         }

         double widthHalf = width / 2.0;
         this.drawBg(-widthHalf, -heightDown, width, heightDown);
         text.beginBig();
         double hX = -widthHalf;
         double hY = -heightDown;
         hX = text.render(name, hX, hY, this.nameColor.get(), shadow);
         if (this.itemCount.get()) {
            text.render(count, hX, hY, this.GOLD, shadow);
         }

         text.end();
         NametagUtils.end();
      }
   }

   private void renderGenericLivingNametag(LivingEntity entity, boolean shadow) {
      TextRenderer text = TextRenderer.get();
      NametagUtils.begin(this.pos);
      String nameText = entity.getType().getDescription().getString();
      nameText = nameText + " ";
      float absorption = entity.getAbsorptionAmount();
      int health = Math.round(entity.getHealth() + absorption);
      double healthPercentage = (double)((float)health / (entity.getMaxHealth() + absorption));
      String healthText = String.valueOf(health);
      Color healthColor;
      if (healthPercentage <= 0.333) {
         healthColor = this.RED;
      } else if (healthPercentage <= 0.666) {
         healthColor = this.AMBER;
      } else {
         healthColor = this.GREEN;
      }

      double nameWidth = text.getWidth(nameText, shadow);
      double healthWidth = text.getWidth(healthText, shadow);
      double heightDown = text.getHeight(shadow);
      double width = nameWidth + healthWidth;
      double widthHalf = width / 2.0;
      this.drawBg(-widthHalf, -heightDown, width, heightDown);
      text.beginBig();
      double hX = -widthHalf;
      double hY = -heightDown;
      hX = text.render(nameText, hX, hY, this.nameColor.get(), shadow);
      text.render(healthText, hX, hY, healthColor, shadow);
      text.end();
      NametagUtils.end();
   }

   private void renderGenericNametag(Entity entity, boolean shadow) {
      TextRenderer text = TextRenderer.get();
      NametagUtils.begin(this.pos);
      String nameText = entity.getType().getDescription().getString();
      double nameWidth = text.getWidth(nameText, shadow);
      double heightDown = text.getHeight(shadow);
      double widthHalf = nameWidth / 2.0;
      this.drawBg(-widthHalf, -heightDown, nameWidth, heightDown);
      text.beginBig();
      double hX = -widthHalf;
      double hY = -heightDown;
      text.render(nameText, hX, hY, this.nameColor.get(), shadow);
      text.end();
      NametagUtils.end();
   }

   private void renderTntNametag(String fuseText, boolean shadow) {
      TextRenderer text = TextRenderer.get();
      NametagUtils.begin(this.pos);
      double width = text.getWidth(fuseText, shadow);
      double heightDown = text.getHeight(shadow);
      double widthHalf = width / 2.0;
      this.drawBg(-widthHalf, -heightDown, width, heightDown);
      text.beginBig();
      double hX = -widthHalf;
      double hY = -heightDown;
      text.render(fuseText, hX, hY, this.nameColor.get(), shadow);
      text.end();
      NametagUtils.end();
   }

   private ItemStack getItem(Player entity, int index) {
      return switch (index) {
         case 0 -> entity.getMainHandItem();
         case 1 -> (ItemStack)entity.getInventory().armor.get(3);
         case 2 -> (ItemStack)entity.getInventory().armor.get(2);
         case 3 -> (ItemStack)entity.getInventory().armor.get(1);
         case 4 -> (ItemStack)entity.getInventory().armor.get(0);
         case 5 -> entity.getOffhandItem();
         default -> ItemStack.EMPTY;
      };
   }

   private void drawBg(double x, double y, double width, double height) {
      Renderer2D.COLOR.begin();
      Renderer2D.COLOR.quad(x - 1.0, y - 1.0, width + 2.0, height + 2.0, this.background.get());
      Renderer2D.COLOR.render(null);
   }

   public boolean excludeBots() {
      return this.ignoreBots.get();
   }

   public boolean playerNametags() {
      return this.isActive() && this.entities.get().contains(EntityType.PLAYER);
   }

   public static enum DistanceColorMode {
      Gradient,
      Flat;
   }

   public static enum Durability {
      None,
      Total,
      Percentage;
   }

   public static enum Position {
      Above,
      OnTop;
   }
}
