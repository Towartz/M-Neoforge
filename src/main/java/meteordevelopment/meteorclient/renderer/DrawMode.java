package meteordevelopment.meteorclient.renderer;

public enum DrawMode {
   Lines(2),
   Triangles(3);

   public final int indicesCount;

   private DrawMode(int indicesCount) {
      this.indicesCount = indicesCount;
   }

   public int getGL() {
      return switch (this) {
         case Lines -> 1;
         case Triangles -> 4;
      };
   }
}
