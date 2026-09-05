package meteordevelopment.meteorclient.systems.modules.render;

import java.util.Set;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.render.postprocess.PostProcessShaders;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public class Chams extends Module {
   private final SettingGroup sgThroughWalls = this.settings.createGroup("Through Walls");
   private final SettingGroup sgPlayers = this.settings.createGroup("Players");
   private final SettingGroup sgCrystals = this.settings.createGroup("Crystals");
   private final SettingGroup sgHand = this.settings.createGroup("Hand");
   public final Setting<Set<EntityType<?>>> entities = this.sgThroughWalls
      .add(new EntityTypeListSetting.Builder().name("entities").description("Select entities to show through walls.").build());
   public final Setting<Chams.Shader> shader = this.sgThroughWalls
      .add(
         new EnumSetting.Builder<Chams.Shader>()
            .name("shader")
            .description("Renders a shader over of the entities.")
            .defaultValue(Chams.Shader.Image)
            .onModuleActivated(setting -> this.updateShader(setting.get()))
            .onChanged(this::updateShader)
            .build()
      );
   public final Setting<SettingColor> shaderColor = this.sgThroughWalls
      .add(
         new ColorSetting.Builder()
            .name("color")
            .description("The color that the shader is drawn with.")
            .defaultValue(new SettingColor(255, 255, 255, 150))
            .visible(() -> this.shader.get() != Chams.Shader.None)
            .build()
      );
   public final Setting<Boolean> ignoreSelfDepth = this.sgThroughWalls
      .add(new BoolSetting.Builder().name("ignore-self").description("Ignores yourself drawing the player.").defaultValue(Boolean.valueOf(true)).build());
   public final Setting<Boolean> players = this.sgPlayers
      .add(new BoolSetting.Builder().name("players").description("Enables model tweaks for players.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> ignoreSelf = this.sgPlayers
      .add(
         new BoolSetting.Builder()
            .name("ignore-self")
            .description("Ignores yourself when tweaking player models.")
            .defaultValue(Boolean.valueOf(false))
            .visible(this.players::get)
            .build()
      );
   public final Setting<Boolean> playersTexture = this.sgPlayers
      .add(
         new BoolSetting.Builder()
            .name("texture")
            .description("Enables player model textures.")
            .defaultValue(Boolean.valueOf(false))
            .visible(this.players::get)
            .build()
      );
   public final Setting<SettingColor> playersColor = this.sgPlayers
      .add(
         new ColorSetting.Builder()
            .name("color")
            .description("The color of player models.")
            .defaultValue(new SettingColor(198, 135, 254, 150))
            .visible(this.players::get)
            .build()
      );
   public final Setting<Double> playersScale = this.sgPlayers
      .add(new DoubleSetting.Builder().name("scale").description("Players scale.").defaultValue(1.0).min(0.0).visible(this.players::get).build());
   public final Setting<Boolean> crystals = this.sgCrystals
      .add(new BoolSetting.Builder().name("crystals").description("Enables model tweaks for end crystals.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Double> crystalsScale = this.sgCrystals
      .add(new DoubleSetting.Builder().name("scale").description("Crystal scale.").defaultValue(0.6).min(0.0).visible(this.crystals::get).build());
   public final Setting<Double> crystalsBounce = this.sgCrystals
      .add(new DoubleSetting.Builder().name("bounce").description("How high crystals bounce.").defaultValue(0.6).min(0.0).visible(this.crystals::get).build());
   public final Setting<Double> crystalsRotationSpeed = this.sgCrystals
      .add(
         new DoubleSetting.Builder()
            .name("rotation-speed")
            .description("Multiplies the rotation speed of the crystal.")
            .defaultValue(0.3)
            .min(0.0)
            .visible(this.crystals::get)
            .build()
      );
   public final Setting<Boolean> crystalsTexture = this.sgCrystals
      .add(
         new BoolSetting.Builder()
            .name("texture")
            .description("Whether to render crystal model textures.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.crystals::get)
            .build()
      );
   public final Setting<Boolean> renderCore = this.sgCrystals
      .add(
         new BoolSetting.Builder()
            .name("render-core")
            .description("Enables rendering of the core of the crystal.")
            .defaultValue(Boolean.valueOf(false))
            .visible(this.crystals::get)
            .build()
      );
   public final Setting<SettingColor> crystalsCoreColor = this.sgCrystals
      .add(
         new ColorSetting.Builder()
            .name("core-color")
            .description("The color of the core of the crystal.")
            .defaultValue(new SettingColor(198, 135, 254, 255))
            .visible(() -> this.crystals.get() && this.renderCore.get())
            .build()
      );
   public final Setting<Boolean> renderFrame1 = this.sgCrystals
      .add(
         new BoolSetting.Builder()
            .name("render-inner-frame")
            .description("Enables rendering of the inner frame of the crystal.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.crystals::get)
            .build()
      );
   public final Setting<SettingColor> crystalsFrame1Color = this.sgCrystals
      .add(
         new ColorSetting.Builder()
            .name("inner-frame-color")
            .description("The color of the inner frame of the crystal.")
            .defaultValue(new SettingColor(198, 135, 254, 255))
            .visible(() -> this.crystals.get() && this.renderFrame1.get())
            .build()
      );
   public final Setting<Boolean> renderFrame2 = this.sgCrystals
      .add(
         new BoolSetting.Builder()
            .name("render-outer-frame")
            .description("Enables rendering of the outer frame of the crystal.")
            .defaultValue(Boolean.valueOf(true))
            .visible(this.crystals::get)
            .build()
      );
   public final Setting<SettingColor> crystalsFrame2Color = this.sgCrystals
      .add(
         new ColorSetting.Builder()
            .name("outer-frame-color")
            .description("The color of the outer frame of the crystal.")
            .defaultValue(new SettingColor(198, 135, 254, 255))
            .visible(() -> this.crystals.get() && this.renderFrame2.get())
            .build()
      );
   public final Setting<Boolean> hand = this.sgHand
      .add(new BoolSetting.Builder().name("enabled").description("Enables tweaks of hand rendering.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> handTexture = this.sgHand
      .add(
         new BoolSetting.Builder()
            .name("texture")
            .description("Whether to render hand textures.")
            .defaultValue(Boolean.valueOf(false))
            .visible(this.hand::get)
            .build()
      );
   public final Setting<SettingColor> handColor = this.sgHand
      .add(
         new ColorSetting.Builder()
            .name("hand-color")
            .description("The color of your hand.")
            .defaultValue(new SettingColor(198, 135, 254, 150))
            .visible(this.hand::get)
            .build()
      );
   public static final ResourceLocation BLANK = MeteorClient.identifier("textures/blank.png");

   public Chams() {
      super(Categories.Render, "chams", "Tweaks rendering of entities.");
   }

   public boolean shouldRender(Entity entity) {
      if (!this.isActive() || this.isShader()) {
         return false;
      } else if (entity == this.mc.cameraEntity && this.mc.options.getCameraType().isFirstPerson()) {
         return false;
      } else {
         return this.entities.get().contains(entity.getType()) && (entity != this.mc.player || !this.ignoreSelfDepth.get());
      }
   }

   public boolean isShader() {
      return this.isActive() && this.shader.get() != Chams.Shader.None;
   }

   public void updateShader(Chams.Shader value) {
      if (value != Chams.Shader.None) {
         PostProcessShaders.CHAMS.init(Utils.titleToName(value.name()));
      }
   }

   public static enum Shader {
      Image,
      None;
   }
}
