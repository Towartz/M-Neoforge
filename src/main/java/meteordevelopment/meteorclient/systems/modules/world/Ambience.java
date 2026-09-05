package meteordevelopment.meteorclient.systems.modules.world;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.DimensionSpecialEffects.SkyType;
import net.minecraft.world.phys.Vec3;

public class Ambience extends Module {
   private final SettingGroup sgSky = this.settings.createGroup("Sky");
   private final SettingGroup sgWorld = this.settings.createGroup("World");
   public final Setting<Boolean> endSky = this.sgSky
      .add(new BoolSetting.Builder().name("end-sky").description("Makes the sky like the end.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> customSkyColor = this.sgSky
      .add(
         new BoolSetting.Builder()
            .name("custom-sky-color")
            .description("Whether the sky color should be changed.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   public final Setting<SettingColor> overworldSkyColor = this.sgSky
      .add(
         new ColorSetting.Builder()
            .name("overworld-sky-color")
            .description("The color of the overworld sky.")
            .defaultValue(new SettingColor(0, 125, 255))
            .visible(this.customSkyColor::get)
            .build()
      );
   public final Setting<SettingColor> netherSkyColor = this.sgSky
      .add(
         new ColorSetting.Builder()
            .name("nether-sky-color")
            .description("The color of the nether sky.")
            .defaultValue(new SettingColor(102, 0, 0))
            .visible(this.customSkyColor::get)
            .build()
      );
   public final Setting<SettingColor> endSkyColor = this.sgSky
      .add(
         new ColorSetting.Builder()
            .name("end-sky-color")
            .description("The color of the end sky.")
            .defaultValue(new SettingColor(65, 30, 90))
            .visible(this.customSkyColor::get)
            .build()
      );
   public final Setting<Boolean> customCloudColor = this.sgSky
      .add(
         new BoolSetting.Builder()
            .name("custom-cloud-color")
            .description("Whether the clouds color should be changed.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   public final Setting<SettingColor> cloudColor = this.sgSky
      .add(
         new ColorSetting.Builder()
            .name("cloud-color")
            .description("The color of the clouds.")
            .defaultValue(new SettingColor(102, 0, 0))
            .visible(this.customCloudColor::get)
            .build()
      );
   public final Setting<Boolean> changeLightningColor = this.sgSky
      .add(
         new BoolSetting.Builder()
            .name("custom-lightning-color")
            .description("Whether the lightning color should be changed.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   public final Setting<SettingColor> lightningColor = this.sgSky
      .add(
         new ColorSetting.Builder()
            .name("lightning-color")
            .description("The color of the lightning.")
            .defaultValue(new SettingColor(102, 0, 0))
            .visible(this.changeLightningColor::get)
            .build()
      );
   public final Setting<Boolean> customGrassColor = this.sgWorld
      .add(
         new BoolSetting.Builder()
            .name("custom-grass-color")
            .description("Whether the grass color should be changed.")
            .defaultValue(Boolean.valueOf(false))
            .onChanged(val -> this.reload())
            .build()
      );
   public final Setting<SettingColor> grassColor = this.sgWorld
      .add(
         new ColorSetting.Builder()
            .name("grass-color")
            .description("The color of the grass.")
            .defaultValue(new SettingColor(102, 0, 0))
            .visible(this.customGrassColor::get)
            .onChanged(val -> this.reload())
            .build()
      );
   public final Setting<Boolean> customFoliageColor = this.sgWorld
      .add(
         new BoolSetting.Builder()
            .name("custom-foliage-color")
            .description("Whether the foliage color should be changed.")
            .defaultValue(Boolean.valueOf(false))
            .onChanged(val -> this.reload())
            .build()
      );
   public final Setting<SettingColor> foliageColor = this.sgWorld
      .add(
         new ColorSetting.Builder()
            .name("foliage-color")
            .description("The color of the foliage.")
            .defaultValue(new SettingColor(102, 0, 0))
            .visible(this.customFoliageColor::get)
            .onChanged(val -> this.reload())
            .build()
      );
   public final Setting<Boolean> customWaterColor = this.sgWorld
      .add(
         new BoolSetting.Builder()
            .name("custom-water-color")
            .description("Whether the water color should be changed.")
            .defaultValue(Boolean.valueOf(false))
            .onChanged(val -> this.reload())
            .build()
      );
   public final Setting<SettingColor> waterColor = this.sgWorld
      .add(
         new ColorSetting.Builder()
            .name("water-color")
            .description("The color of the water.")
            .defaultValue(new SettingColor(102, 0, 0))
            .visible(this.customWaterColor::get)
            .onChanged(val -> this.reload())
            .build()
      );
   public final Setting<Boolean> customLavaColor = this.sgWorld
      .add(
         new BoolSetting.Builder()
            .name("custom-lava-color")
            .description("Whether the lava color should be changed.")
            .defaultValue(Boolean.valueOf(false))
            .onChanged(val -> this.reload())
            .build()
      );
   public final Setting<SettingColor> lavaColor = this.sgWorld
      .add(
         new ColorSetting.Builder()
            .name("lava-color")
            .description("The color of the lava.")
            .defaultValue(new SettingColor(102, 0, 0))
            .visible(this.customLavaColor::get)
            .onChanged(val -> this.reload())
            .build()
      );

   public Ambience() {
      super(Categories.World, "ambience", "Change the color of various pieces of the environment.");
   }

   @Override
   public void onActivate() {
      this.reload();
   }

   @Override
   public void onDeactivate() {
      this.reload();
   }

   private void reload() {
      if (this.mc.levelRenderer != null && this.isActive()) {
         this.mc.levelRenderer.allChanged();
      }
   }

   public SettingColor skyColor() {
      switch (PlayerUtils.getDimension()) {
         case Overworld:
            return this.overworldSkyColor.get();
         case Nether:
            return this.netherSkyColor.get();
         case End:
            return this.endSkyColor.get();
         default:
            return null;
      }
   }

   public static class Custom extends DimensionSpecialEffects {
      public Custom() {
         super(Float.NaN, true, SkyType.END, true, false);
      }

      public Vec3 getBrightnessDependentFogColor(Vec3 color, float sunHeight) {
         return color.scale(0.15F);
      }

      public boolean isFoggyAt(int camX, int camY) {
         return false;
      }

      public float[] getSunriseColor(float skyAngle, float tickDelta) {
         return null;
      }
   }
}
