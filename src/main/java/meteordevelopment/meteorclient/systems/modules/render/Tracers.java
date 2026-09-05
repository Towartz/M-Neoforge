package meteordevelopment.meteorclient.systems.modules.render;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
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
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.Target;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class Tracers extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgAppearance = this.settings.createGroup("Appearance");
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final Setting<Set<EntityType<?>>> entities = this.sgGeneral
      .add(new EntityTypeListSetting.Builder().name("entities").description("Select specific entities.").defaultValue(EntityType.PLAYER).build());
   private final Setting<Boolean> ignoreSelf = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("ignore-self")
            .description("Doesn't draw tracers to yourself when in third person or freecam.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   public final Setting<Boolean> ignoreFriends = this.sgGeneral
      .add(new BoolSetting.Builder().name("ignore-friends").description("Doesn't draw tracers to friends.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Boolean> showInvis = this.sgGeneral
      .add(new BoolSetting.Builder().name("show-invisible").description("Shows invisible entities.").defaultValue(Boolean.valueOf(true)).build());
   private final Setting<Tracers.TracerStyle> style = this.sgAppearance
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("style"))
                  .description("What display mode should be used"))
               .defaultValue(Tracers.TracerStyle.Lines))
            .build()
      );
   private final Setting<Target> target = this.sgAppearance
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("target"))
                     .description("What part of the entity to target."))
                  .defaultValue(Target.Body))
               .visible(() -> this.style.get() == Tracers.TracerStyle.Lines))
            .build()
      );
   private final Setting<Tracers.TracerOrigin> origin = this.sgAppearance
      .add(
         new EnumSetting.Builder<Tracers.TracerOrigin>()
            .name("origin")
            .description("Where the tracer lines originate from.")
            .defaultValue(Tracers.TracerOrigin.Crosshair)
            .visible(() -> this.style.get() == Tracers.TracerStyle.Lines)
            .build()
      );
   private final Setting<Boolean> stem = this.sgAppearance
      .add(
         new BoolSetting.Builder()
            .name("stem")
            .description("Draw a line through the center of the tracer target.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.style.get() == Tracers.TracerStyle.Lines)
            .build()
      );
   private final Setting<Integer> maxDist = this.sgAppearance
      .add(
         new IntSetting.Builder()
            .name("max-distance")
            .description("Maximum distance for tracers to show.")
            .defaultValue(Integer.valueOf(256))
            .min(0)
            .sliderMax(256)
            .build()
      );
   private final Setting<Integer> distanceOffscreen = this.sgAppearance
      .add(
         new IntSetting.Builder()
            .name("distance-offscreen")
            .description("Offscreen's distance from center.")
            .defaultValue(Integer.valueOf(200))
            .min(0)
            .sliderMax(500)
            .visible(() -> this.style.get() == Tracers.TracerStyle.Offscreen)
            .build()
      );
   private final Setting<Integer> sizeOffscreen = this.sgAppearance
      .add(
         new IntSetting.Builder()
            .name("size-offscreen")
            .description("Offscreen's size.")
            .defaultValue(Integer.valueOf(10))
            .min(2)
            .sliderMax(50)
            .visible(() -> this.style.get() == Tracers.TracerStyle.Offscreen)
            .build()
      );
   private final Setting<Boolean> blinkOffscreen = this.sgAppearance
      .add(
         new BoolSetting.Builder()
            .name("blink-offscreen")
            .description("Make offscreen Blink.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.style.get() == Tracers.TracerStyle.Offscreen)
            .build()
      );
   private final Setting<Double> blinkOffscreenSpeed = this.sgAppearance
      .add(
         new DoubleSetting.Builder()
            .name("blink-offscreen-speed")
            .description("Offscreen's blink speed.")
            .defaultValue(4.0)
            .min(1.0)
            .sliderMax(15.0)
            .visible(() -> this.style.get() == Tracers.TracerStyle.Offscreen && this.blinkOffscreen.get())
            .build()
      );
   public final Setting<Boolean> distance = this.sgColors
      .add(
         new BoolSetting.Builder()
            .name("distance-colors")
            .description("Changes the color of tracers depending on distance.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   public final Setting<Boolean> friendOverride = this.sgColors
      .add(
         new BoolSetting.Builder()
            .name("show-friend-colors")
            .description("Whether or not to override the distance color of friends with the friend color.")
            .defaultValue(Boolean.valueOf(true))
            .visible(() -> this.distance.get() && !this.ignoreFriends.get())
            .build()
      );
   private final Setting<SettingColor> playersColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("players-colors")
            .description("The player's color.")
            .defaultValue(new SettingColor(205, 205, 205, 127))
            .visible(() -> !this.distance.get())
            .build()
      );
   private final Setting<SettingColor> animalsColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("animals-color")
            .description("The animal's color.")
            .defaultValue(new SettingColor(145, 255, 145, 127))
            .visible(() -> !this.distance.get())
            .build()
      );
   private final Setting<SettingColor> waterAnimalsColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("water-animals-color")
            .description("The water animal's color.")
            .defaultValue(new SettingColor(145, 145, 255, 127))
            .visible(() -> !this.distance.get())
            .build()
      );
   private final Setting<SettingColor> monstersColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("monsters-color")
            .description("The monster's color.")
            .defaultValue(new SettingColor(255, 145, 145, 127))
            .visible(() -> !this.distance.get())
            .build()
      );
   private final Setting<SettingColor> ambientColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("ambient-color")
            .description("The ambient color.")
            .defaultValue(new SettingColor(75, 75, 75, 127))
            .visible(() -> !this.distance.get())
            .build()
      );
   private final Setting<SettingColor> miscColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("misc-color")
            .description("The misc color.")
            .defaultValue(new SettingColor(145, 145, 145, 127))
            .visible(() -> !this.distance.get())
            .build()
      );
   private int count;
   private Instant initTimer = Instant.now();

   public Tracers() {
      super(Categories.Render, "tracers", "Displays tracer lines to specified entities.");
   }

   private boolean shouldBeIgnored(Entity entity) {
      if (entity == this.mc.player) {
         if (this.ignoreSelf.get() || (this.mc.options.getCameraType().isFirstPerson() && !Modules.get().isActive(Freecam.class))) {
            return true;
         }
      }

      return !PlayerUtils.isWithin(entity, (double)this.maxDist.get().intValue())
         || !this.entities.get().contains(entity.getType())
         || (this.ignoreFriends.get() && entity instanceof Player && Friends.get().isFriend((Player)entity))
         || (!this.showInvis.get() && entity.isInvisible())
         || !EntityUtils.isInRenderDistance(entity);
   }

   private Color getEntityColor(Entity entity) {
      Color color;
      if (this.distance.get()) {
         if (this.friendOverride.get() && entity instanceof Player && Friends.get().isFriend((Player)entity)) {
            color = Config.get().friendColor.get();
         } else {
            color = EntityUtils.getColorFromDistance(entity);
         }
      } else if (entity instanceof Player) {
         color = PlayerUtils.getPlayerColor((Player)entity, this.playersColor.get());
      } else {
         color = switch (entity.getType().getCategory()) {
            case CREATURE -> (SettingColor)this.animalsColor.get();
            case WATER_AMBIENT, WATER_CREATURE, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> (SettingColor)this.waterAnimalsColor.get();
            case MONSTER -> (SettingColor)this.monstersColor.get();
            case AMBIENT -> (SettingColor)this.ambientColor.get();
            default -> (SettingColor)this.miscColor.get();
         };
      }

      return new Color(color);
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (!this.mc.options.hideGui && this.style.get() != Tracers.TracerStyle.Offscreen) {
         this.count = 0;

         double startX;
         double startY;
         double startZ;

         if (this.origin.get() == Tracers.TracerOrigin.Head && this.mc.player != null) {
            Vec3 eyePos = this.mc.player.getEyePosition(event.tickDelta);
            startX = eyePos.x;
            startY = eyePos.y;
            startZ = eyePos.z;
         } else if (this.origin.get() == Tracers.TracerOrigin.Feet && this.mc.player != null) {
            startX = Mth.lerp((double)event.tickDelta, this.mc.player.xo, this.mc.player.getX());
            startY = Mth.lerp((double)event.tickDelta, this.mc.player.yo, this.mc.player.getY());
            startZ = Mth.lerp((double)event.tickDelta, this.mc.player.zo, this.mc.player.getZ());
         } else {
            startX = RenderUtils.center.x;
            startY = RenderUtils.center.y;
            startZ = RenderUtils.center.z;
         }

         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (!this.shouldBeIgnored(entity)) {
               Color color = this.getEntityColor(entity);
               double x = entity.xo + (entity.getX() - entity.xo) * (double)event.tickDelta;
               double baseY = entity.yo + (entity.getY() - entity.yo) * (double)event.tickDelta;
               double z = entity.zo + (entity.getZ() - entity.zo) * (double)event.tickDelta;
               double height = entity.getBoundingBox().maxY - entity.getBoundingBox().minY;
               double y = baseY;
               if (this.target.get() == Target.Head) {
                  y += (double)entity.getEyeHeight(entity.getPose());
               } else if (this.target.get() == Target.Body) {
                  y += height / 2.0;
               }

               event.renderer.line(startX, startY, startZ, x, y, z, color);
               if (this.stem.get()) {
                  event.renderer.line(x, baseY, z, x, baseY + height, z, color);
               }

               this.count++;
            }
         }
      }
   }

   @EventHandler
   public void onRender2D(Render2DEvent event) {
      if (!this.mc.options.hideGui && this.style.get() == Tracers.TracerStyle.Offscreen) {
         this.count = 0;
         Renderer2D.COLOR.begin();

         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (!this.shouldBeIgnored(entity)) {
               Color color = this.getEntityColor(entity);
               if (this.blinkOffscreen.get()) {
                  color.a = (int)((float)color.a * this.getAlpha());
               }

               Vec2 screenCenter = new Vec2((float)this.mc.getWindow().getWidth() / 2.0F, (float)this.mc.getWindow().getHeight() / 2.0F);
               double x = entity.xo + (entity.getX() - entity.xo) * (double)event.tickDelta;
               double baseY = entity.yo + (entity.getY() - entity.yo) * (double)event.tickDelta;
               double z = entity.zo + (entity.getZ() - entity.zo) * (double)event.tickDelta;
               double height = entity.getBoundingBox().maxY - entity.getBoundingBox().minY;
               double y = baseY;
               if (this.target.get() == Target.Head) {
                  y += (double)entity.getEyeHeight(entity.getPose());
               } else if (this.target.get() == Target.Body) {
                  y += height / 2.0;
               }

               Vector3d projection = new Vector3d(x, y, z);
               boolean projSucceeded = NametagUtils.to2D(projection, 1.0, false, false);
               if (!projSucceeded
                  || !(projection.x > 0.0)
                  || !(projection.x < (double)this.mc.getWindow().getWidth())
                  || !(projection.y > 0.0)
                  || !(projection.y < (double)this.mc.getWindow().getHeight())) {
                  projection = new Vector3d(x, y, z);
                  NametagUtils.to2D(projection, 1.0, false, true);
                  Vector2f angle = this.vectorAngles(new Vector3d((double)screenCenter.x - projection.x, (double)screenCenter.y - projection.y, 0.0));
                  angle.y += 180.0F;
                  float angleYawRad = (float)Math.toRadians((double)angle.y);
                  Vector2f newPoint = new Vector2f(
                     screenCenter.x + (float)this.distanceOffscreen.get().intValue() * (float)Math.cos((double)angleYawRad),
                     screenCenter.y + (float)this.distanceOffscreen.get().intValue() * (float)Math.sin((double)angleYawRad)
                  );
                  Vector2f[] trianglePoints = new Vector2f[]{
                     new Vector2f(newPoint.x - (float)this.sizeOffscreen.get().intValue(), newPoint.y - (float)this.sizeOffscreen.get().intValue()),
                     new Vector2f(newPoint.x + (float)this.sizeOffscreen.get().intValue() * 0.73205F, newPoint.y),
                     new Vector2f(newPoint.x - (float)this.sizeOffscreen.get().intValue(), newPoint.y + (float)this.sizeOffscreen.get().intValue())
                  };
                  this.rotateTriangle(trianglePoints, angle.y);
                  Renderer2D.COLOR
                     .triangle(
                        (double)trianglePoints[0].x,
                        (double)trianglePoints[0].y,
                        (double)trianglePoints[1].x,
                        (double)trianglePoints[1].y,
                        (double)trianglePoints[2].x,
                        (double)trianglePoints[2].y,
                        color
                     );
                  this.count++;
               }
            }
         }

         Renderer2D.COLOR.render(null);
      }
   }

   private void rotateTriangle(Vector2f[] points, float ang) {
      Vector2f triangleCenter = new Vector2f(0.0F, 0.0F);
      triangleCenter.add(points[0]).add(points[1]).add(points[2]).div(3.0F);
      float theta = (float)Math.toRadians((double)ang);
      float cos = (float)Math.cos((double)theta);
      float sin = (float)Math.sin((double)theta);

      for (int i = 0; i < 3; i++) {
         Vector2f point = new Vector2f(points[i].x, points[i].y).sub(triangleCenter);
         Vector2f newPoint = new Vector2f(point.x * cos - point.y * sin, point.x * sin + point.y * cos);
         newPoint.add(triangleCenter);
         points[i] = newPoint;
      }
   }

   private Vector2f vectorAngles(Vector3d forward) {
      float yaw;
      float pitch;
      if (forward.x == 0.0 && forward.y == 0.0) {
         yaw = 0.0F;
         if (forward.z > 0.0) {
            pitch = 270.0F;
         } else {
            pitch = 90.0F;
         }
      } else {
         yaw = (float)(Math.atan2(forward.y, forward.x) * 180.0 / Math.PI);
         if (yaw < 0.0F) {
            yaw += 360.0F;
         }

         float tmp = (float)Math.sqrt(forward.x * forward.x + forward.y * forward.y);
         pitch = (float)(Math.atan2(-forward.z, (double)tmp) * 180.0 / Math.PI);
         if (pitch < 0.0F) {
            pitch += 360.0F;
         }
      }

      return new Vector2f(pitch, yaw);
   }

   private float getAlpha() {
      double speed = this.blinkOffscreenSpeed.get() / 4.0;
      double duration = (double)Math.abs(Duration.between(Instant.now(), this.initTimer).toMillis()) * speed;
      return (float)Math.abs(duration % 1000.0 - 500.0) / 500.0F;
   }

   @Override
   public String getInfoString() {
      return Integer.toString(this.count);
   }

   public static enum TracerStyle {
      Lines,
      Offscreen;
   }

   public static enum TracerOrigin {
      Crosshair,
      Camera,
      Head,
      Feet;
   }
}
