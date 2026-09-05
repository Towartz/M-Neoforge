package meteordevelopment.meteorclient.utils.render.postprocess;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.ESP;
import net.minecraft.world.entity.Entity;

public class EntityOutlineShader extends EntityShader {
   private static ESP esp;

   public EntityOutlineShader() {
      this.init("outline");
   }

   @Override
   protected boolean shouldDraw() {
      if (esp == null) {
         esp = Modules.get().get(ESP.class);
      }

      return esp.isShader();
   }

   @Override
   public boolean shouldDraw(Entity entity) {
      return !this.shouldDraw() ? false : !esp.shouldSkip(entity);
   }

   @Override
   protected void setUniforms() {
      this.shader.set("u_Width", esp.outlineWidth.get());
      this.shader.set("u_FillOpacity", esp.fillOpacity.get());
      this.shader.set("u_ShapeMode", esp.shapeMode.get().ordinal());
      this.shader.set("u_GlowMultiplier", esp.glowMultiplier.get());
   }
}
