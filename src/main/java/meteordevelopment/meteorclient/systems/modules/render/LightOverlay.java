package meteordevelopment.meteorclient.systems.modules.render;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.DrawMode;
import meteordevelopment.meteorclient.renderer.Mesh;
import meteordevelopment.meteorclient.renderer.ShaderMesh;
import meteordevelopment.meteorclient.renderer.Shaders;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Pool;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;

public class LightOverlay extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgColors = this.settings.createGroup("Colors");
   private final Setting<Integer> horizontalRange = this.sgGeneral
      .add(new IntSetting.Builder().name("horizontal-range").description("Horizontal range in blocks.").defaultValue(Integer.valueOf(8)).min(0).build());
   private final Setting<Integer> verticalRange = this.sgGeneral
      .add(new IntSetting.Builder().name("vertical-range").description("Vertical range in blocks.").defaultValue(Integer.valueOf(4)).min(0).build());
   private final Setting<Boolean> seeThroughBlocks = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("see-through-blocks")
            .description("Allows you to see the lines through blocks.")
            .defaultValue(Boolean.valueOf(false))
            .build()
      );
   private final Setting<Boolean> newMobSpawnLightLevel = this.sgGeneral
      .add(
         new BoolSetting.Builder()
            .name("new-mob-spawn-light-level")
            .description("Use the new (1.18+) mob spawn behavior")
            .defaultValue(Boolean.valueOf(true))
            .build()
      );
   private final Setting<SettingColor> color = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("color")
            .description("Color of places where mobs can currently spawn.")
            .defaultValue(new SettingColor(225, 25, 25))
            .build()
      );
   private final Setting<SettingColor> potentialColor = this.sgColors
      .add(
         new ColorSetting.Builder()
            .name("potential-color")
            .description("Color of places where mobs can potentially spawn (eg at night).")
            .defaultValue(new SettingColor(225, 225, 25))
            .build()
      );
   private final Pool<LightOverlay.Cross> crossPool = new Pool<>(() -> new LightOverlay.Cross());
   private final List<LightOverlay.Cross> crosses = new ArrayList<>();
   private final Mesh mesh = new ShaderMesh(Shaders.POS_COLOR, DrawMode.Lines, Mesh.Attrib.Vec3, Mesh.Attrib.Color);

   public LightOverlay() {
      super(Categories.Render, "light-overlay", "Shows blocks where mobs can spawn.");
   }

   @EventHandler
   private void onTick(TickEvent.Pre event) {
      for (LightOverlay.Cross cross : this.crosses) {
         this.crossPool.free(cross);
      }

      this.crosses.clear();
      int spawnLightLevel = this.newMobSpawnLightLevel.get() ? 0 : 7;
      BlockIterator.register(this.horizontalRange.get(), this.verticalRange.get(), (blockPos, blockState) -> {
         switch (BlockUtils.isValidMobSpawn(blockPos, blockState, spawnLightLevel)) {
            case Potential:
               this.crosses.add(this.crossPool.get().set(blockPos, true));
               break;
            case Always:
               this.crosses.add(this.crossPool.get().set(blockPos, false));
         }
      });
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (!this.crosses.isEmpty()) {
         this.mesh.depthTest = !this.seeThroughBlocks.get();
         this.mesh.begin();

         for (LightOverlay.Cross cross : this.crosses) {
            cross.render();
         }

         this.mesh.end();
         this.mesh.render(event.matrices);
      }
   }

   private void line(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
      this.mesh.line(this.mesh.vec3(x1, y1, z1).color(color).next(), this.mesh.vec3(x2, y2, z2).color(color).next());
   }

   private class Cross {
      private double x;
      private double y;
      private double z;
      private boolean potential;

      public LightOverlay.Cross set(BlockPos blockPos, boolean potential) {
         this.x = (double)blockPos.getX();
         this.y = (double)blockPos.getY() + 0.0075;
         this.z = (double)blockPos.getZ();
         this.potential = potential;
         return this;
      }

      public void render() {
         Color c = this.potential ? LightOverlay.this.potentialColor.get() : LightOverlay.this.color.get();
         LightOverlay.this.line(this.x, this.y, this.z, this.x + 1.0, this.y, this.z + 1.0, c);
         LightOverlay.this.line(this.x + 1.0, this.y, this.z, this.x, this.y, this.z + 1.0, c);
      }
   }

   public static enum Spawn {
      Never,
      Potential,
      Always;
   }
}
