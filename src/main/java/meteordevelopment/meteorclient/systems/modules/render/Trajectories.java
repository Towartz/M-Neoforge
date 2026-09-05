package meteordevelopment.meteorclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.ProjectileEntitySimulator;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WindChargeItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class Trajectories extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<List<Item>> items = this.sgGeneral
      .add(
         new ItemListSetting.Builder()
            .name("items")
            .description("Items to display trajectories for.")
            .defaultValue(this.getDefaultItems())
            .filter(this::itemFilter)
            .build()
      );
   private final Setting<Boolean> otherPlayers = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("other-players").description("Calculates trajectories for other players.").defaultValue(Boolean.valueOf(true)).build()
      );
   private final Setting<Boolean> firedProjectiles = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("fired-projectiles")
            .description("Calculates trajectories for already fired projectiles.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> accurate = this.sgGeneral
      .add(new BoolSetting.Builder().name("accurate").description("Whether or not to calculate more accurate.").defaultValue(Boolean.valueOf(false)).build());
   public final Setting<Integer> simulationSteps = this.sgGeneral
      .add(
         new IntSetting.Builder()
            .name("simulation-steps")
            .description("How many steps to simulate projectiles. Zero for no limit")
            .defaultValue(Integer.valueOf(500))
            .sliderMax(5000)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         ((EnumSetting.Builder)((EnumSetting.Builder)((EnumSetting.Builder)new EnumSetting.Builder().name("shape-mode"))
                  .description("How the shapes are rendered."))
               .defaultValue(ShapeMode.Both))
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(new ColorSetting.Builder().name("side-color").description("The side color.").defaultValue(new SettingColor(255, 150, 0, 35)).build());
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(new ColorSetting.Builder().name("line-color").description("The line color.").defaultValue(new SettingColor(255, 150, 0)).build());
   private final Setting<Boolean> renderPositionBox = this.sgRender
      .add(
         new BoolSetting.Builder()
            .name("render-position-boxes")
            .description("Renders the actual position the projectile will be at each tick along it's trajectory.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Double> positionBoxSize = this.sgRender
      .add(
         new DoubleSetting.Builder()
            .name("position-box-size")
            .description("The size of the box drawn at the simulated positions.")
            .defaultValue(0.02)
            .sliderRange(0.01, 0.1)
            .visible(this.renderPositionBox::get)
            .build()
      );
   private final Setting<SettingColor> positionSideColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("position-side-color")
            .description("The side color.")
            .defaultValue(new SettingColor(255, 150, 0, 35))
            .visible(this.renderPositionBox::get)
            .build()
      );
   private final Setting<SettingColor> positionLineColor = this.sgRender
      .add(
         new ColorSetting.Builder()
            .name("position-line-color")
            .description("The line color.")
            .defaultValue(new SettingColor(255, 150, 0))
            .visible(this.renderPositionBox::get)
            .build()
      );
   private final ProjectileEntitySimulator simulator = new ProjectileEntitySimulator();
   private final Pool<Vector3d> vec3s = new Pool<>(Vector3d::new);
   private final List<Trajectories.Path> paths = new ArrayList<>();
   private static final double MULTISHOT_OFFSET = Math.toRadians(10.0);

   public Trajectories() {
      super(Categories.Render, "trajectories", "Predicts the trajectory of throwable items.");
   }

   private boolean itemFilter(Item item) {
      return item instanceof ProjectileWeaponItem
         || item instanceof FishingRodItem
         || item instanceof TridentItem
         || item instanceof SnowballItem
         || item instanceof EggItem
         || item instanceof EnderpearlItem
         || item instanceof ExperienceBottleItem
         || item instanceof ThrowablePotionItem
         || item instanceof WindChargeItem;
   }

   private List<Item> getDefaultItems() {
      List<Item> items = new ArrayList<>();

      for (Item item : BuiltInRegistries.ITEM) {
         if (this.itemFilter(item)) {
            items.add(item);
         }
      }

      return items;
   }

   private Trajectories.Path getEmptyPath() {
      for (Trajectories.Path path : this.paths) {
         if (path.points.isEmpty()) {
            return path;
         }
      }

      Trajectories.Path pathx = new Trajectories.Path();
      this.paths.add(pathx);
      return pathx;
   }

   private void calculatePath(Player player, float tickDelta) {
      for (Trajectories.Path path : this.paths) {
         path.clear();
      }

      ItemStack itemStack = player.getMainHandItem();
      if (!this.items.get().contains(itemStack.getItem())) {
         itemStack = player.getOffhandItem();
         if (!this.items.get().contains(itemStack.getItem())) {
            return;
         }
      }

      if (this.simulator.set(player, itemStack, 0.0, this.accurate.get(), tickDelta)) {
         this.getEmptyPath().setStart(player, (double)tickDelta).calculate();
         if (itemStack.getItem() instanceof CrossbowItem && Utils.hasEnchantment(itemStack, Enchantments.MULTISHOT)) {
            if (!this.simulator.set(player, itemStack, MULTISHOT_OFFSET, this.accurate.get(), tickDelta)) {
               return;
            }

            this.getEmptyPath().setStart(player, (double)tickDelta).calculate();
            if (!this.simulator.set(player, itemStack, -MULTISHOT_OFFSET, this.accurate.get(), tickDelta)) {
               return;
            }

            this.getEmptyPath().setStart(player, (double)tickDelta).calculate();
         }
      }
   }

   private void calculateFiredPath(Entity entity, double tickDelta) {
      for (Trajectories.Path path : this.paths) {
         path.clear();
      }

      if (this.simulator.set(entity, this.accurate.get())) {
         this.getEmptyPath().setStart(entity, tickDelta).calculate();
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      float tickDelta = this.mc.level.tickRateManager().isFrozen() ? 1.0F : event.tickDelta;

      for (Player player : this.mc.level.players()) {
         if (this.otherPlayers.get() || player == this.mc.player) {
            this.calculatePath(player, tickDelta);

            for (Trajectories.Path path : this.paths) {
               path.render(event);
            }
         }
      }

      if (this.firedProjectiles.get()) {
         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof Projectile) {
               this.calculateFiredPath(entity, (double)tickDelta);

               for (Trajectories.Path path : this.paths) {
                  path.render(event);
               }
            }
         }
      }
   }

   private class Path {
      private final List<Vector3d> points = new ArrayList<>();
      private boolean hitQuad;
      private boolean hitQuadHorizontal;
      private double hitQuadX1;
      private double hitQuadY1;
      private double hitQuadZ1;
      private double hitQuadX2;
      private double hitQuadY2;
      private double hitQuadZ2;
      private Entity collidingEntity;
      public Vector3d lastPoint;

      public void clear() {
         for (Vector3d point : this.points) {
            Trajectories.this.vec3s.free(point);
         }

         this.points.clear();
         this.hitQuad = false;
         this.collidingEntity = null;
         this.lastPoint = null;
      }

      public void calculate() {
         this.addPoint();

         for (int i = 0; i < (Trajectories.this.simulationSteps.get() > 0 ? Trajectories.this.simulationSteps.get() : Integer.MAX_VALUE); i++) {
            HitResult result = Trajectories.this.simulator.tick();
            if (result != null) {
               this.processHitResult(result);
               break;
            }

            this.addPoint();
         }
      }

      public Trajectories.Path setStart(Entity entity, double tickDelta) {
         Vec3 eye = entity.getEyePosition((float)tickDelta);
         this.lastPoint = new Vector3d(eye.x, eye.y, eye.z);
         return this;
      }

      private void addPoint() {
         this.points.add(Trajectories.this.vec3s.get().set(Trajectories.this.simulator.pos));
      }

      private void processHitResult(HitResult result) {
         if (result.getType() == Type.BLOCK) {
            BlockHitResult r = (BlockHitResult)result;
            this.hitQuad = true;
            this.hitQuadX1 = r.getLocation().x;
            this.hitQuadY1 = r.getLocation().y;
            this.hitQuadZ1 = r.getLocation().z;
            this.hitQuadX2 = r.getLocation().x;
            this.hitQuadY2 = r.getLocation().y;
            this.hitQuadZ2 = r.getLocation().z;
            if (r.getDirection() == Direction.UP || r.getDirection() == Direction.DOWN) {
               this.hitQuadHorizontal = true;
               this.hitQuadX1 -= 0.25;
               this.hitQuadZ1 -= 0.25;
               this.hitQuadX2 += 0.25;
               this.hitQuadZ2 += 0.25;
            } else if (r.getDirection() != Direction.NORTH && r.getDirection() != Direction.SOUTH) {
               this.hitQuadHorizontal = false;
               this.hitQuadZ1 -= 0.25;
               this.hitQuadY1 -= 0.25;
               this.hitQuadZ2 += 0.25;
               this.hitQuadY2 += 0.25;
            } else {
               this.hitQuadHorizontal = false;
               this.hitQuadX1 -= 0.25;
               this.hitQuadY1 -= 0.25;
               this.hitQuadX2 += 0.25;
               this.hitQuadY2 += 0.25;
            }

            this.points.add(Utils.set(Trajectories.this.vec3s.get(), result.getLocation()));
         } else if (result.getType() == Type.ENTITY) {
            this.collidingEntity = ((EntityHitResult)result).getEntity();
            this.points.add(Utils.set(Trajectories.this.vec3s.get(), result.getLocation()).add(0.0, (double)(this.collidingEntity.getBbHeight() / 2.0F), 0.0));
         }
      }

      public void render(Render3DEvent event) {
         Vector3d last = this.lastPoint;
         for (Vector3d point : this.points) {
            if (last != null) {
               event.renderer.line(last.x, last.y, last.z, point.x, point.y, point.z, Trajectories.this.lineColor.get());
               if (Trajectories.this.renderPositionBox.get()) {
                  event.renderer
                     .box(
                        point.x - Trajectories.this.positionBoxSize.get(),
                        point.y - Trajectories.this.positionBoxSize.get(),
                        point.z - Trajectories.this.positionBoxSize.get(),
                        point.x + Trajectories.this.positionBoxSize.get(),
                        point.y + Trajectories.this.positionBoxSize.get(),
                        point.z + Trajectories.this.positionBoxSize.get(),
                        Trajectories.this.positionSideColor.get(),
                        Trajectories.this.positionLineColor.get(),
                        Trajectories.this.shapeMode.get(),
                        0
                     );
               }
            }

            last = point;
         }

         if (this.hitQuad) {
            if (this.hitQuadHorizontal) {
               event.renderer
                  .sideHorizontal(
                     this.hitQuadX1,
                     this.hitQuadY1,
                     this.hitQuadZ1,
                     this.hitQuadX1 + 0.5,
                     this.hitQuadZ1 + 0.5,
                     Trajectories.this.sideColor.get(),
                     Trajectories.this.lineColor.get(),
                     Trajectories.this.shapeMode.get()
                  );
            } else {
               event.renderer
                  .sideVertical(
                     this.hitQuadX1,
                     this.hitQuadY1,
                     this.hitQuadZ1,
                     this.hitQuadX2,
                     this.hitQuadY2,
                     this.hitQuadZ2,
                     Trajectories.this.sideColor.get(),
                     Trajectories.this.lineColor.get(),
                     Trajectories.this.shapeMode.get()
                  );
            }
         }

         if (this.collidingEntity != null) {
            double x = (this.collidingEntity.getX() - this.collidingEntity.xo) * (double)event.tickDelta;
            double y = (this.collidingEntity.getY() - this.collidingEntity.yo) * (double)event.tickDelta;
            double z = (this.collidingEntity.getZ() - this.collidingEntity.zo) * (double)event.tickDelta;
            AABB box = this.collidingEntity.getBoundingBox();
            event.renderer
               .box(
                  x + box.minX,
                  y + box.minY,
                  z + box.minZ,
                  x + box.maxX,
                  y + box.maxY,
                  z + box.maxZ,
                  Trajectories.this.sideColor.get(),
                  Trajectories.this.lineColor.get(),
                  Trajectories.this.shapeMode.get(),
                  0
               );
         }
      }
   }
}
