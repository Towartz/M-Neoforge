package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class InventoryHud extends HudElement {
   public static final HudElementInfo<InventoryHud> INFO = new HudElementInfo<>(Hud.GROUP, "inventory", "Displays your inventory.", InventoryHud::new);
   private static final ResourceLocation TEXTURE = MeteorClient.identifier("textures/container.png");
   private static final ResourceLocation TEXTURE_TRANSPARENT = MeteorClient.identifier("textures/container-transparent.png");
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> containers = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("containers")
            .description("Shows the contents of a container when holding them.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Double> scale = this.sgGeneral
      .add(
         new DoubleSetting.Builder()
            .name("scale")
            .description("The scale.")
            .defaultValue(2.0)
            .min(1.0)
            .sliderRange(1.0, 5.0)
            .onChanged(aDouble -> this.calculateSize())
            .build()
      );
   private final Setting<InventoryHud.Background> background = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("background"))
                     .description("Background of inventory viewer."))
                  .defaultValue(InventoryHud.Background.Texture))
               .onChanged(bg -> this.calculateSize()))
            .build()
      );
   private final Setting<SettingColor> color = this.sgGeneral
      .add(
         new ColorSetting.Builder()
            .name("background-color")
            .description("Color of the background.")
            .defaultValue(new SettingColor(255, 255, 255))
            .visible(() -> this.background.get() != InventoryHud.Background.None)
            .build()
      );
   private final ItemStack[] containerItems = new ItemStack[27];

   private InventoryHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void render(HudRenderer renderer) {
      double x = (double)this.x;
      double y = (double)this.y;
      ItemStack container = this.getContainer();
      boolean hasContainer = this.containers.get() && container != null;
      if (hasContainer) {
         Utils.getItemsInContainerItem(container, this.containerItems);
      }

      Color drawColor = hasContainer ? Utils.getShulkerColor(container) : this.color.get();
      if (this.background.get() != InventoryHud.Background.None) {
         this.drawBackground(renderer, (int)x, (int)y, drawColor);
      }

      if (MeteorClient.mc.player != null) {
         renderer.post(
            () -> {
               for (int row = 0; row < 3; row++) {
                  for (int i = 0; i < 9; i++) {
                     int index = row * 9 + i;
                     ItemStack stack = hasContainer ? this.containerItems[index] : MeteorClient.mc.player.getInventory().getItem(index + 9);
                     if (stack != null) {
                        int itemX = this.background.get() == InventoryHud.Background.Texture
                           ? (int)(x + (double)(8 + i * 18) * this.scale.get())
                           : (int)(x + (double)(1 + i * 18) * this.scale.get());
                        int itemY = this.background.get() == InventoryHud.Background.Texture
                           ? (int)(y + (double)(7 + row * 18) * this.scale.get())
                           : (int)(y + (double)(1 + row * 18) * this.scale.get());
                        renderer.item(stack, itemX, itemY, this.scale.get().floatValue(), true);
                     }
                  }
               }
            }
         );
      }
   }

   private void calculateSize() {
      this.setSize((double)this.background.get().width * this.scale.get(), (double)this.background.get().height * this.scale.get());
   }

   private void drawBackground(HudRenderer renderer, int x, int y, Color color) {
      int w = this.getWidth();
      int h = this.getHeight();
      switch ((InventoryHud.Background)this.background.get()) {
         case Texture:
         case Outline:
            renderer.texture(
               this.background.get() == InventoryHud.Background.Texture ? TEXTURE : TEXTURE_TRANSPARENT, (double)x, (double)y, (double)w, (double)h, color
            );
            break;
         case Flat:
            renderer.quad((double)x, (double)y, (double)w, (double)h, color);
      }
   }

   private ItemStack getContainer() {
      if (!this.isInEditor() && MeteorClient.mc.player != null) {
         ItemStack stack = MeteorClient.mc.player.getOffhandItem();
         if (!Utils.hasItems(stack) && stack.getItem() != Items.ENDER_CHEST) {
            stack = MeteorClient.mc.player.getMainHandItem();
            return !Utils.hasItems(stack) && stack.getItem() != Items.ENDER_CHEST ? null : stack;
         } else {
            return stack;
         }
      } else {
         return null;
      }
   }

   public static enum Background {
      None(162, 54),
      Texture(176, 67),
      Outline(162, 54),
      Flat(162, 54);

      private final int width;
      private final int height;

      private Background(int width, int height) {
         this.width = width;
         this.height = height;
      }
   }
}
