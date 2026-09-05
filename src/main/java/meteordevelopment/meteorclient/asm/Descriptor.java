package meteordevelopment.meteorclient.asm;

public class Descriptor {
   private final String[] components;

   public Descriptor(String... components) {
      this.components = components;
   }

   public String toString(boolean method, boolean map) {
      StringBuilder sb = new StringBuilder();
      if (method) {
         sb.append('(');
      }

      for (int i = 0; i < this.components.length; i++) {
         if (method && i == this.components.length - 1) {
            sb.append(')');
         }
         sb.append(this.components[i]);
      }

      return sb.toString();
   }
}
