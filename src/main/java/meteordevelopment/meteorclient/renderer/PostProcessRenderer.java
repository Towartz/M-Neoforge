package meteordevelopment.meteorclient.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.utils.PreInit;

public class PostProcessRenderer {
   private static Mesh mesh;
   private static final PoseStack matrices = new PoseStack();

   private PostProcessRenderer() {
   }

   @PreInit
   public static void init() {
      mesh = new Mesh(DrawMode.Triangles, Mesh.Attrib.Vec2);
      mesh.begin();
      mesh.quad(mesh.vec2(-1.0, -1.0).next(), mesh.vec2(-1.0, 1.0).next(), mesh.vec2(1.0, 1.0).next(), mesh.vec2(1.0, -1.0).next());
      mesh.end();
   }

   public static void beginRender() {
      mesh.beginRender(matrices);
   }

   public static void render() {
      mesh.render(matrices);
   }

   public static void endRender() {
      mesh.endRender();
   }
}
