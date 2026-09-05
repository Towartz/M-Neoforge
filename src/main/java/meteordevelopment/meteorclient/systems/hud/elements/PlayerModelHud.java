package meteordevelopment.meteorclient.systems.hud.elements;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlayerModelHud extends HudElement {
   public static final HudElementInfo<PlayerModelHud> INFO = new HudElementInfo<>(
      Hud.GROUP, "player-model", "Displays a model of your player.", PlayerModelHud::new
   );
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBackground = this.settings.createGroup("Background");
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
   private final Setting<Boolean> copyYaw = this.sgGeneral
      .add(new BoolSetting.Builder().name("copy-yaw").description("Makes the player model's yaw equal to yours.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Integer> customYaw = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("custom-yaw")
            .description("Custom yaw for when copy yaw is off.")
            .defaultValue(Integer.valueOf(0))
            .range(-180, 180)
            .sliderRange(-180, 180)
            .visible(() -> !this.copyYaw.get())
            .build()
      );
   private final Setting<Boolean> copyPitch = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("copy-pitch").description("Makes the player model's pitch equal to yours.").defaultValue(Boolean.valueOf(true)).build()
      );
   private final Setting<Integer> customPitch = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("custom-pitch")
            .description("Custom pitch for when copy pitch is off.")
            .defaultValue(Integer.valueOf(0))
            .range(-90, 90)
            .sliderRange(-90, 90)
            .visible(() -> !this.copyPitch.get())
            .build()
      );
   private final Setting<PlayerModelHud.CenterOrientation> centerOrientation = this.sgGeneral
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("center-orientation"))
                  .description("Which direction the player faces when the HUD model faces directly forward."))
               .defaultValue(PlayerModelHud.CenterOrientation.South))
            .build()
      );
   private final Setting<Boolean> background = this.sgBackground
      .add(new BoolSetting.Builder().name("background").description("Displays background.").defaultValue(Boolean.valueOf(false)).build());
   private final Setting<SettingColor> backgroundColor = this.sgBackground
      .add(
         new ColorSetting.Builder()
            .name("background-color")
            .description("Color used for the background.")
            .visible(this.background::get)
            .defaultValue(new SettingColor(25, 25, 25, 50))
            .build()
      );

   public PlayerModelHud() {
      super(INFO);
      this.calculateSize();
   }

   @Override
   public void render(HudRenderer renderer) {
      renderer.post(
         () -> {
            Player player = MeteorClient.mc.player;
            if (player != null) {
               float offset = this.centerOrientation.get() == PlayerModelHud.CenterOrientation.North ? 180.0F : 0.0F;
               float yaw = this.copyYaw.get()
                  ? Mth.wrapDegrees(player.yRotO + (player.getYRot() - player.yRotO) * MeteorClient.mc.getTimer().getGameTimeDeltaPartialTick(true) + offset)
                  : (float)this.customYaw.get().intValue();
               float pitch = this.copyPitch.get() ? player.getXRot() : (float)this.customPitch.get().intValue();
               this.drawEntity(renderer.drawContext, this.x, this.y, (int)(30.0 * this.scale.get()), -yaw, -pitch, player);
            }
         }
      );
      if (this.background.get()) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), this.backgroundColor.get());
      } else if (MeteorClient.mc.player == null) {
         renderer.quad((double)this.x, (double)this.y, (double)this.getWidth(), (double)this.getHeight(), this.backgroundColor.get());
         renderer.line((double)this.x, (double)this.y, (double)(this.x + this.getWidth()), (double)(this.y + this.getHeight()), Color.GRAY);
         renderer.line((double)(this.x + this.getWidth()), (double)this.y, (double)this.x, (double)(this.y + this.getHeight()), Color.GRAY);
      }
   }

   private void calculateSize() {
      this.setSize(50.0 * this.scale.get(), 75.0 * this.scale.get());
   }

   private void drawEntity(GuiGraphics context, int x, int y, int size, float yaw, float pitch, LivingEntity entity) {
      float tanYaw = (float)Math.atan((double)(yaw / 40.0F));
      float tanPitch = (float)Math.atan((double)(pitch / 40.0F));
      Quaternionf quaternion = new Quaternionf().rotateZ((float) Math.PI);
      float previousBodyYaw = entity.yBodyRot;
      float previousYaw = entity.getYRot();
      float previousPitch = entity.getXRot();
      float previousPrevHeadYaw = entity.yHeadRotO;
      float prevHeadYaw = entity.yHeadRot;
      entity.yBodyRot = 180.0F + tanYaw * 20.0F;
      entity.setYRot(180.0F + tanYaw * 40.0F);
      entity.setXRot(-tanPitch * 20.0F);
      entity.yHeadRot = entity.getYRot();
      entity.yHeadRotO = entity.getYRot();
      InventoryScreen.renderEntityInInventory(
         context, (float)(x + this.getWidth() / 2), (float)y + (float)this.getHeight() * 0.9F, (float)size, new Vector3f(), quaternion, null, entity
      );
      entity.yBodyRot = previousBodyYaw;
      entity.setYRot(previousYaw);
      entity.setXRot(previousPitch);
      entity.yHeadRotO = previousPrevHeadYaw;
      entity.yHeadRot = prevHeadYaw;
   }

   private static enum CenterOrientation {
      North,
      South;
   }
}
