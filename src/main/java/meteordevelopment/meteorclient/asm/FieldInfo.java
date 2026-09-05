package meteordevelopment.meteorclient.asm;

import org.objectweb.asm.tree.FieldInsnNode;

public class FieldInfo {
   private String owner;
   private String name;
   private String descriptor;

   public FieldInfo(String owner, String name, Descriptor descriptor, boolean map) {
      this.owner = owner;
      this.name = name;
      if (descriptor != null) {
         this.descriptor = descriptor.toString(false, map);
      }
   }

   public boolean equals(FieldInsnNode insn) {
      return (this.owner == null || insn.owner.equals(this.owner))
         && (this.name == null || insn.name.equals(this.name))
         && (this.descriptor == null || insn.desc.equals(this.descriptor));
   }
}
