package meteordevelopment.meteorclient.systems.modules;

import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import net.minecraft.world.item.Items;

public class Categories {
   public static final Category Combat = new Category("Combat", Items.GOLDEN_SWORD.getDefaultInstance());
   public static final Category Player = new Category("Player", Items.ARMOR_STAND.getDefaultInstance());
   public static final Category Movement = new Category("Movement", Items.DIAMOND_BOOTS.getDefaultInstance());
   public static final Category Render = new Category("Render", Items.GLASS.getDefaultInstance());
   public static final Category World = new Category("World", Items.GRASS_BLOCK.getDefaultInstance());
   public static final Category Misc = new Category("Misc", Items.LAVA_BUCKET.getDefaultInstance());
   public static final Category PlusCombat = new Category("Plus Combat", Items.NETHERITE_SWORD.getDefaultInstance());
   public static final Category PlusMovement = new Category("Plus Movement", Items.NETHERITE_BOOTS.getDefaultInstance());
   public static final Category PlusWorld = new Category("Plus World", Items.NETHERITE_PICKAXE.getDefaultInstance());
   public static final Category PlusRender = new Category("Plus Render", Items.ENDER_EYE.getDefaultInstance());
   public static final Category PlusPlayer = new Category("Plus Player", Items.CRAFTING_TABLE.getDefaultInstance());
   public static boolean REGISTERING;

   public static void init() {
      REGISTERING = true;
      Modules.registerCategory(Combat);
      Modules.registerCategory(Player);
      Modules.registerCategory(Movement);
      Modules.registerCategory(Render);
      Modules.registerCategory(World);
      Modules.registerCategory(Misc);
      Modules.registerCategory(PlusCombat);
      Modules.registerCategory(PlusMovement);
      Modules.registerCategory(PlusWorld);
      Modules.registerCategory(PlusRender);
      Modules.registerCategory(PlusPlayer);
      AddonManager.ADDONS.forEach(MeteorAddon::onRegisterCategories);
      REGISTERING = false;
   }
}
