package meteordevelopment.meteorclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3d;

public class ESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sg2D = this.settings.createGroup("2D ESP");
   private final SettingGroup sgColors = this.settings.createGroup("Colors");

   public final Setting<ESP.Mode> mode = this.sgGeneral
      .add(new EnumSetting.Builder<ESP.Mode>().name("mode").description("Rendering mode.").defaultValue(ESP.Mode.Shader).build());

   public final Setting<Double> maxDistance = this.sgGeneral
      .add(new DoubleSetting.Builder().name("max-distance").description("Maximum distance to render ESP (0 for unlimited).").defaultValue(128.0).min(0.0).sliderRange(0.0, 256.0).build());

   public final Setting<Boolean> smoothInterpolation = this.sgGeneral
      .add(new BoolSetting.Builder().name("smooth-interpolation").description("Smoothly interpolates bounding boxes using tick delta to eliminate jitter.").defaultValue(true).visible(() -> this.mode.get() == ESP.Mode.Box || this.mode.get() == ESP.Mode._2D).build());

   public final Setting<Integer> outlineWidth = this.sgGeneral
      .add(new IntSetting.Builder().name("outline-width").description("The width of the shader outline.").visible(() -> this.mode.get() == ESP.Mode.Shader).defaultValue(2).range(1, 10).sliderRange(1, 5).build());

   public final Setting<Double> glowMultiplier = this.sgGeneral
      .add(new DoubleSetting.Builder().name("glow-multiplier").description("Multiplier for glow effect.").visible(() -> this.mode.get() == ESP.Mode.Shader).decimalPlaces(3).defaultValue(3.5).min(0.0).sliderMax(10.0).build());

   public final Setting<Boolean> shaderRainbow = this.sgGeneral
      .add(new BoolSetting.Builder().name("shader-rainbow").description("Enables rainbow chroma cycling on shader outline.").defaultValue(false).visible(() -> this.mode.get() == ESP.Mode.Shader).build());

   public final Setting<Double> shaderRainbowSpeed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("shader-rainbow-speed").description("Speed of rainbow chroma cycling.").defaultValue(1.0).range(0.1, 5.0).sliderRange(0.1, 3.0).visible(() -> this.mode.get() == ESP.Mode.Shader && this.shaderRainbow.get()).build());

   public final Setting<Boolean> shaderPulse = this.sgGeneral
      .add(new BoolSetting.Builder().name("shader-pulse").description("Enables breathing/pulsing animation on shader outline.").defaultValue(false).visible(() -> this.mode.get() == ESP.Mode.Shader).build());

   public final Setting<Double> shaderPulseSpeed = this.sgGeneral
      .add(new DoubleSetting.Builder().name("shader-pulse-speed").description("Speed of the breathing/pulse animation.").defaultValue(2.0).range(0.5, 10.0).sliderRange(1.0, 5.0).visible(() -> this.mode.get() == ESP.Mode.Shader && this.shaderPulse.get()).build());

   public final Setting<Boolean> ignoreSelf = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-self").description("Ignores yourself drawing the shader.").defaultValue(true).build());

   public final Setting<ShapeMode> shapeMode = this.sgGeneral
      .add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").description("How the shapes are rendered.").visible(() -> this.mode.get() != ESP.Mode.Glow).defaultValue(ShapeMode.Both).build());

   public final Setting<Double> fillOpacity = this.sgGeneral
      .add(new DoubleSetting.Builder().name("fill-opacity").description("The opacity of the shape fill.").visible(() -> this.shapeMode.get() != ShapeMode.Lines && this.mode.get() != ESP.Mode.Glow).defaultValue(0.3).range(0.0, 1.0).sliderMax(1.0).build());

   private final Setting<Double> fadeDistance = this.sgGeneral
      .add(new DoubleSetting.Builder().name("fade-distance").description("The distance from an entity where the color begins to fade.").defaultValue(3.0).min(0.0).sliderMax(12.0).build());

   private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
      .add(new EntityTypeListSetting.Builder().name("entities").description("Select specific entities.").defaultValue(EntityType.PLAYER).build());

   // 2D ESP Settings
   public final Setting<Box2DStyle> box2DStyle = this.sg2D
      .add(new EnumSetting.Builder<Box2DStyle>().name("box-style").description("Visual style for 2D boxes.").defaultValue(Box2DStyle.Corners).visible(() -> this.mode.get() == ESP.Mode._2D).build());

   public final Setting<Double> cornerLength = this.sg2D
      .add(new DoubleSetting.Builder().name("corner-length").description("Corner bracket length relative to box dimensions.").defaultValue(0.25).range(0.1, 0.5).sliderRange(0.1, 0.5).visible(() -> this.mode.get() == ESP.Mode._2D && this.box2DStyle.get() == Box2DStyle.Corners).build());

   public final Setting<Boolean> healthBar = this.sg2D
      .add(new BoolSetting.Builder().name("health-bar").description("Displays a tactical health bar beside the 2D box.").defaultValue(true).visible(() -> this.mode.get() == ESP.Mode._2D).build());

   public final Setting<Boolean> renderNametag = this.sg2D
      .add(new BoolSetting.Builder().name("nametag").description("Displays entity display name on 2D ESP.").defaultValue(false).visible(() -> this.mode.get() == ESP.Mode._2D).build());

   public final Setting<Boolean> renderDistance = this.sg2D
      .add(new BoolSetting.Builder().name("distance-tag").description("Displays distance tag on 2D ESP.").defaultValue(false).visible(() -> this.mode.get() == ESP.Mode._2D).build());

   public final Setting<Boolean> renderItem = this.sg2D
      .add(new BoolSetting.Builder().name("held-item").description("Displays held item name on 2D ESP.").defaultValue(false).visible(() -> this.mode.get() == ESP.Mode._2D).build());

   // Color Settings
   public final Setting<Boolean> rainbow = this.sgColors
      .add(new BoolSetting.Builder().name("rainbow").description("Applies animated rainbow cycling colors.").defaultValue(false).build());

   public final Setting<Double> rainbowSpeed = this.sgColors
      .add(new DoubleSetting.Builder().name("rainbow-speed").description("Speed of rainbow color cycling.").defaultValue(1.0).range(0.1, 5.0).sliderRange(0.1, 3.0).visible(this.rainbow::get).build());

   public final Setting<Boolean> healthColor = this.sgColors
      .add(new BoolSetting.Builder().name("health-color").description("Dynamically alters color from green to red based on entity health percentage.").defaultValue(false).build());

   public final Setting<Boolean> distance = this.sgColors
      .add(new BoolSetting.Builder().name("distance-colors").description("Changes the color of tracers depending on distance.").defaultValue(false).build());

   public final Setting<Boolean> friendOverride = this.sgColors
      .add(new BoolSetting.Builder().name("show-friend-colors").description("Whether or not to override the distance color of friends with the friend color.").defaultValue(true).visible(this.distance::get).build());

   private final Setting<SettingColor> playersColor = this.sgColors
      .add(new ColorSetting.Builder().name("players-color").description("The other player's color.").defaultValue(new SettingColor(255, 255, 255)).visible(() -> !this.distance.get() && !this.rainbow.get()).build());

   private final Setting<SettingColor> animalsColor = this.sgColors
      .add(new ColorSetting.Builder().name("animals-color").description("The animal's color.").defaultValue(new SettingColor(25, 255, 25, 255)).visible(() -> !this.distance.get() && !this.rainbow.get()).build());

   private final Setting<SettingColor> waterAnimalsColor = this.sgColors
      .add(new ColorSetting.Builder().name("water-animals-color").description("The water animal's color.").defaultValue(new SettingColor(25, 25, 255, 255)).visible(() -> !this.distance.get() && !this.rainbow.get()).build());

   private final Setting<SettingColor> monstersColor = this.sgColors
      .add(new ColorSetting.Builder().name("monsters-color").description("The monster's color.").defaultValue(new SettingColor(255, 25, 25, 255)).visible(() -> !this.distance.get() && !this.rainbow.get()).build());

   private final Setting<SettingColor> ambientColor = this.sgColors
      .add(new ColorSetting.Builder().name("ambient-color").description("The ambient's color.").defaultValue(new SettingColor(25, 25, 25, 255)).visible(() -> !this.distance.get() && !this.rainbow.get()).build());

   private final Setting<SettingColor> miscColor = this.sgColors
      .add(new ColorSetting.Builder().name("misc-color").description("The misc color.").defaultValue(new SettingColor(175, 175, 175, 255)).visible(() -> !this.distance.get() && !this.rainbow.get()).build());

   private final Color lineColor = new Color();
   private final Color sideColor = new Color();
   private final Color baseColor = new Color();
   private final Color tempColor = new Color();
   private final Color blackColor = new Color(0, 0, 0, 200);
   private final Color textBgColor = new Color(0, 0, 0, 160);
   private final Color textColor = new Color(255, 255, 255, 255);

   private final Vector3d pos1 = new Vector3d();
   private final Vector3d pos2 = new Vector3d();
   private final Vector3d pos = new Vector3d();
   private int count;

   private static class Draw2DEntry {
      Entity entity;
      double minX, minY, maxX, maxY;
      Color color;
   }
   private final List<Draw2DEntry> pending2DText = new ArrayList<>();

   public ESP() {
      super(Categories.Render, "esp", "Renders entities through walls with advanced shader, glow, 2D corner brackets, and 3D wireframes.");
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mode.get() != ESP.Mode._2D) {
         this.count = 0;

         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (!this.shouldSkip(entity)) {
               if (this.mode.get() == ESP.Mode.Box || this.mode.get() == ESP.Mode.Wireframe) {
                  this.drawBoundingBox(event, entity);
               }
               this.count++;
            }
         }
      }
   }

   private void drawBoundingBox(Render3DEvent event, Entity entity) {
      Color color = this.getColor(entity);
      if (color != null) {
         this.lineColor.set(color);
         this.sideColor.set(color).a((int)((double)this.sideColor.a * this.fillOpacity.get()));
      }

      if (this.mode.get() == ESP.Mode.Box) {
         double x = 0, y = 0, z = 0;
         if (this.smoothInterpolation.get()) {
            x = Mth.lerp((double)event.tickDelta, entity.xOld, entity.getX()) - entity.getX();
            y = Mth.lerp((double)event.tickDelta, entity.yOld, entity.getY()) - entity.getY();
            z = Mth.lerp((double)event.tickDelta, entity.zOld, entity.getZ()) - entity.getZ();
         }
         AABB box = entity.getBoundingBox();
         event.renderer.box(
            x + box.minX, y + box.minY, z + box.minZ,
            x + box.maxX, y + box.maxY, z + box.maxZ,
            this.sideColor, this.lineColor, this.shapeMode.get(), 0
         );
      } else {
         WireframeEntityRenderer.render(event, entity, 1.0, this.sideColor, this.lineColor, this.shapeMode.get());
      }
   }

   @EventHandler
   private void onRender2D(Render2DEvent event) {
      if (this.mode.get() == ESP.Mode._2D) {
         this.count = 0;
         this.pending2DText.clear();

         Renderer2D.COLOR.begin();

         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (!this.shouldSkip(entity)) {
               AABB box = entity.getBoundingBox();
               double x = 0, y = 0, z = 0;
               if (this.smoothInterpolation.get()) {
                  x = Mth.lerp((double)event.tickDelta, entity.xOld, entity.getX()) - entity.getX();
                  y = Mth.lerp((double)event.tickDelta, entity.yOld, entity.getY()) - entity.getY();
                  z = Mth.lerp((double)event.tickDelta, entity.zOld, entity.getZ()) - entity.getZ();
               }

               this.pos1.set(Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
               this.pos2.set(0.0, 0.0, 0.0);

               if (!this.checkCorner(box.minX + x, box.minY + y, box.minZ + z, this.pos1, this.pos2)
                  && !this.checkCorner(box.maxX + x, box.minY + y, box.minZ + z, this.pos1, this.pos2)
                  && !this.checkCorner(box.minX + x, box.minY + y, box.maxZ + z, this.pos1, this.pos2)
                  && !this.checkCorner(box.maxX + x, box.minY + y, box.maxZ + z, this.pos1, this.pos2)
                  && !this.checkCorner(box.minX + x, box.maxY + y, box.minZ + z, this.pos1, this.pos2)
                  && !this.checkCorner(box.maxX + x, box.maxY + y, box.minZ + z, this.pos1, this.pos2)
                  && !this.checkCorner(box.minX + x, box.maxY + y, box.maxZ + z, this.pos1, this.pos2)
                  && !this.checkCorner(box.maxX + x, box.maxY + y, box.maxZ + z, this.pos1, this.pos2)) {

                  Color color = this.getColor(entity);
                  if (color != null) {
                     this.lineColor.set(color);
                     this.sideColor.set(color).a((int)((double)this.sideColor.a * this.fillOpacity.get()));
                  }

                  double w = this.pos2.x - this.pos1.x;
                  double h = this.pos2.y - this.pos1.y;

                  // Fill
                  if (this.shapeMode.get() != ShapeMode.Lines && this.sideColor.a > 0) {
                     Renderer2D.COLOR.quad(this.pos1.x, this.pos1.y, w, h, this.sideColor);
                  }

                  // Outlines
                  if (this.shapeMode.get() != ShapeMode.Sides) {
                     if (this.box2DStyle.get() == Box2DStyle.Corners) {
                        double lenX = w * this.cornerLength.get();
                        double lenY = h * this.cornerLength.get();

                        // Top-left
                        Renderer2D.COLOR.line(this.pos1.x, this.pos1.y, this.pos1.x + lenX, this.pos1.y, this.lineColor);
                        Renderer2D.COLOR.line(this.pos1.x, this.pos1.y, this.pos1.x, this.pos1.y + lenY, this.lineColor);

                        // Top-right
                        Renderer2D.COLOR.line(this.pos2.x - lenX, this.pos1.y, this.pos2.x, this.pos1.y, this.lineColor);
                        Renderer2D.COLOR.line(this.pos2.x, this.pos1.y, this.pos2.x, this.pos1.y + lenY, this.lineColor);

                        // Bottom-left
                        Renderer2D.COLOR.line(this.pos1.x, this.pos2.y, this.pos1.x + lenX, this.pos2.y, this.lineColor);
                        Renderer2D.COLOR.line(this.pos1.x, this.pos2.y - lenY, this.pos1.x, this.pos2.y, this.lineColor);

                        // Bottom-right
                        Renderer2D.COLOR.line(this.pos2.x - lenX, this.pos2.y, this.pos2.x, this.pos2.y, this.lineColor);
                        Renderer2D.COLOR.line(this.pos2.x, this.pos2.y - lenY, this.pos2.x, this.pos2.y, this.lineColor);
                     } else {
                        // Full rectangular box
                        Renderer2D.COLOR.line(this.pos1.x, this.pos1.y, this.pos1.x, this.pos2.y, this.lineColor);
                        Renderer2D.COLOR.line(this.pos2.x, this.pos1.y, this.pos2.x, this.pos2.y, this.lineColor);
                        Renderer2D.COLOR.line(this.pos1.x, this.pos1.y, this.pos2.x, this.pos1.y, this.lineColor);
                        Renderer2D.COLOR.line(this.pos1.x, this.pos2.y, this.pos2.x, this.pos2.y, this.lineColor);
                     }
                  }

                  // Tactical Health Bar
                  if (this.healthBar.get() && entity instanceof LivingEntity living) {
                     float health = living.getHealth();
                     float maxHealth = living.getMaxHealth();
                     float hpPercent = Mth.clamp(health / maxHealth, 0.0f, 1.0f);

                     double barX = this.pos1.x - 5.0;
                     double barWidth = 2.0;

                     // Black border
                     Renderer2D.COLOR.quad(barX - 1.0, this.pos1.y - 1.0, barWidth + 2.0, h + 2.0, this.blackColor);

                     // Health fill
                     double filledHeight = h * (double)hpPercent;
                     double fillY = this.pos2.y - filledHeight;
                     Color hpCol = this.calculateHealthColor(hpPercent);
                     Renderer2D.COLOR.quad(barX, fillY, barWidth, filledHeight, hpCol);
                  }

                  if (this.renderNametag.get() || this.renderDistance.get() || this.renderItem.get()) {
                     Draw2DEntry entry = new Draw2DEntry();
                     entry.entity = entity;
                     entry.minX = this.pos1.x;
                     entry.minY = this.pos1.y;
                     entry.maxX = this.pos2.x;
                     entry.maxY = this.pos2.y;
                     entry.color = new Color(this.lineColor);
                     this.pending2DText.add(entry);
                  }

                  this.count++;
               }
            }
         }

         Renderer2D.COLOR.render(null);

         // Render Text Overlays
         if (!this.pending2DText.isEmpty()) {
            TextRenderer text = TextRenderer.get();
            text.begin(1.0);

            for (Draw2DEntry entry : this.pending2DText) {
               double boxCenterX = (entry.minX + entry.maxX) / 2.0;

               // Top text: Name & Distance
               StringBuilder topText = new StringBuilder();
               if (this.renderNametag.get()) {
                  topText.append(entry.entity.getName().getString());
               }
               if (this.renderDistance.get()) {
                  double d = Math.sqrt(PlayerUtils.squaredDistanceToCamera(entry.entity));
                  if (!topText.isEmpty()) topText.append(" ");
                  topText.append(String.format("[%.0fm]", d));
               }

               if (!topText.isEmpty()) {
                  String s = topText.toString();
                  double tw = text.getWidth(s);
                  double th = text.getHeight();
                  text.render(s, boxCenterX - tw / 2.0, entry.minY - th - 2.0, this.textColor, true);
               }

               // Bottom text: Held Item
               if (this.renderItem.get() && entry.entity instanceof LivingEntity living) {
                  ItemStack held = living.getMainHandItem();
                  if (!held.isEmpty()) {
                     String itemName = held.getHoverName().getString();
                     double iw = text.getWidth(itemName);
                     text.render(itemName, boxCenterX - iw / 2.0, entry.maxY + 2.0, this.textColor, true);
                  }
               }
            }

            text.end();
         }
      }
   }

   private boolean checkCorner(double x, double y, double z, Vector3d min, Vector3d max) {
      this.pos.set(x, y, z);
      if (!NametagUtils.to2D(this.pos, 1.0)) {
         return true;
      } else {
         if (this.pos.x < min.x) min.x = this.pos.x;
         if (this.pos.y < min.y) min.y = this.pos.y;
         if (this.pos.z < min.z) min.z = this.pos.z;
         if (this.pos.x > max.x) max.x = this.pos.x;
         if (this.pos.y > max.y) max.y = this.pos.y;
         if (this.pos.z > max.z) max.z = this.pos.z;
         return false;
      }
   }

   public boolean shouldSkip(Entity entity) {
      if (!this.entities.get().contains(entity.getType())) {
         return true;
      } else if (entity == this.mc.player && this.ignoreSelf.get()) {
         return true;
      } else if (entity == this.mc.cameraEntity && this.mc.options.getCameraType().isFirstPerson()) {
         return true;
      } else {
         if (this.maxDistance.get() > 0.0) {
            double distSq = PlayerUtils.squaredDistanceToCamera(entity);
            if (distSq > this.maxDistance.get() * this.maxDistance.get()) {
               return true;
            }
         }
         return !EntityUtils.isInRenderDistance(entity);
      }
   }

   public Color getColor(Entity entity) {
      if (!this.entities.get().contains(entity.getType())) {
         return null;
      } else {
         double alpha = this.getFadeAlpha(entity);
         if (alpha == 0.0) {
            return null;
         } else {
            if (this.rainbow.get()) {
               Color rainbowCol = this.getRainbowColor();
               return this.baseColor.set(rainbowCol.r, rainbowCol.g, rainbowCol.b, (int)((double)rainbowCol.a * alpha));
            }
            if (this.healthColor.get() && entity instanceof LivingEntity living) {
               float hp = living.getHealth();
               float maxHp = living.getMaxHealth();
               float pct = Mth.clamp(hp / maxHp, 0.0f, 1.0f);
               Color hpCol = this.calculateHealthColor(pct);
               return this.baseColor.set(hpCol.r, hpCol.g, hpCol.b, (int)((double)hpCol.a * alpha));
            }
            Color color = this.getEntityTypeColor(entity);
            return this.baseColor.set(color.r, color.g, color.b, (int)((double)color.a * alpha));
         }
      }
   }

   private Color calculateHealthColor(float pct) {
      int r, g, b = 0;
      if (pct > 0.5f) {
         float f = (pct - 0.5f) * 2.0f;
         r = (int)(255 * (1.0f - f));
         g = 255;
      } else {
         float f = pct * 2.0f;
         r = 255;
         g = (int)(255 * f);
      }
      return this.tempColor.set(r, g, b, 255);
   }

   private Color getRainbowColor() {
      double time = (System.currentTimeMillis() % 100000L) / 1000.0;
      float hue = (float)((time * this.rainbowSpeed.get()) % 1.0);
      int rgb = hsvToRgb(hue, 0.85f, 1.0f);
      return this.tempColor.set((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
   }

   private static int hsvToRgb(float h, float s, float v) {
      float r = 0, g = 0, b = 0;
      float i = (float)Math.floor(h * 6);
      float f = h * 6 - i;
      float p = v * (1 - s);
      float q = v * (1 - f * s);
      float t = v * (1 - (1 - f) * s);
      switch ((int)i % 6) {
         case 0 -> { r = v; g = t; b = p; }
         case 1 -> { r = q; g = v; b = p; }
         case 2 -> { r = p; g = v; b = t; }
         case 3 -> { r = p; g = q; b = v; }
         case 4 -> { r = t; g = p; b = v; }
         case 5 -> { r = v; g = p; b = q; }
      }
      return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
   }

   private double getFadeAlpha(Entity entity) {
      double dist = PlayerUtils.squaredDistanceToCamera(
         entity.getX() + (double)(entity.getBbWidth() / 2.0F),
         entity.getY() + (double)entity.getEyeHeight(entity.getPose()),
         entity.getZ() + (double)(entity.getBbWidth() / 2.0F)
      );
      double fadeDist = this.fadeDistance.get();
      double fadeDistSq = fadeDist * fadeDist;
      double alpha = 1.0;
      if (dist <= fadeDistSq) {
         alpha = Math.sqrt(dist) / fadeDist;
      }

      if (alpha <= 0.075) {
         alpha = 0.0;
      }

      return alpha;
   }

   public Color getEntityTypeColor(Entity entity) {
      if (this.distance.get()) {
         return this.friendOverride.get() && entity instanceof Player && Friends.get().isFriend((Player)entity)
            ? Config.get().friendColor.get()
            : EntityUtils.getColorFromDistance(entity);
      } else if (entity instanceof Player) {
         return PlayerUtils.getPlayerColor((Player)entity, this.playersColor.get());
      } else {
         return switch (entity.getType().getCategory()) {
            case CREATURE -> (SettingColor)this.animalsColor.get();
            case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> (SettingColor)this.waterAnimalsColor.get();
            case MONSTER -> (SettingColor)this.monstersColor.get();
            case AMBIENT -> (SettingColor)this.ambientColor.get();
            default -> (SettingColor)this.miscColor.get();
         };
      }
   }

   @Override
   public String getInfoString() {
      return Integer.toString(this.count);
   }

   public boolean isShader() {
      return this.isActive() && this.mode.get() == ESP.Mode.Shader;
   }

   public boolean isGlow() {
      return this.isActive() && this.mode.get() == ESP.Mode.Glow;
   }

   public static enum Mode {
      Box,
      Wireframe,
      _2D,
      Shader,
      Glow;

      @Override
      public String toString() {
         return this == _2D ? "2D" : super.toString();
      }
   }

   public static enum Box2DStyle {
      Full,
      Corners;

      @Override
      public String toString() {
         return super.toString();
      }
   }
}
