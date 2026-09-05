package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.function.ToIntFunction;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.commands.arguments.RegistryEntryReferenceArgumentType;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

public class EnchantCommand extends Command {
   private static final SimpleCommandExceptionType NOT_IN_CREATIVE = new SimpleCommandExceptionType(
      Component.literal("You must be in creative mode to use this.")
   );
   private static final SimpleCommandExceptionType NOT_HOLDING_ITEM = new SimpleCommandExceptionType(
      Component.literal("You need to hold some item to enchant.")
   );

   public EnchantCommand() {
      super("enchant", "Enchants the item in your hand. REQUIRES Creative mode.");
   }

   @Override
   public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
      builder.then(
         literal("one")
            .then(
               ((RequiredArgumentBuilder)argument("enchantment", RegistryEntryReferenceArgumentType.enchantment())
                     .then(literal("level").then(argument("level", IntegerArgumentType.integer()).executes(context -> {
                        this.one(context, enchantment -> (Integer)context.getArgument("level", Integer.class));
                        return 1;
                     }))))
                  .then(literal("max").executes(context -> {
                     this.one(context, Enchantment::getMaxLevel);
                     return 1;
                  }))
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)literal("all_possible").then(literal("level").then(argument("level", IntegerArgumentType.integer()).executes(context -> {
            this.all(true, enchantment -> (Integer)context.getArgument("level", Integer.class));
            return 1;
         })))).then(literal("max").executes(context -> {
            this.all(true, Enchantment::getMaxLevel);
            return 1;
         }))
      );
      builder.then(((LiteralArgumentBuilder)literal("all").then(literal("level").then(argument("level", IntegerArgumentType.integer()).executes(context -> {
         this.all(false, enchantment -> (Integer)context.getArgument("level", Integer.class));
         return 1;
      })))).then(literal("max").executes(context -> {
         this.all(false, Enchantment::getMaxLevel);
         return 1;
      })));
      builder.then(literal("clear").executes(context -> {
         ItemStack itemStack = this.tryGetItemStack();
         Utils.clearEnchantments(itemStack);
         this.syncItem();
         return 1;
      }));
      builder.then(literal("remove").then(argument("enchantment", RegistryEntryReferenceArgumentType.enchantment()).executes(context -> {
         ItemStack itemStack = this.tryGetItemStack();
         Reference<Enchantment> enchantment = RegistryEntryReferenceArgumentType.getEnchantment(context, "enchantment");
         Utils.removeEnchantment(itemStack, (Enchantment)enchantment.value());
         this.syncItem();
         return 1;
      })));
   }

   private void one(CommandContext<SharedSuggestionProvider> context, ToIntFunction<Enchantment> level) throws CommandSyntaxException {
      ItemStack itemStack = this.tryGetItemStack();
      Reference<Enchantment> enchantment = RegistryEntryReferenceArgumentType.getEnchantment(context, "enchantment");
      Utils.addEnchantment(itemStack, enchantment, level.applyAsInt((Enchantment)enchantment.value()));
      this.syncItem();
   }

   private void all(boolean onlyPossible, ToIntFunction<Enchantment> level) throws CommandSyntaxException {
      ItemStack itemStack = this.tryGetItemStack();
      mc.getConnection().registryAccess().lookup(Registries.ENCHANTMENT).ifPresent(registry -> registry.listElements().forEach(enchantment -> {
            if (!onlyPossible || ((Enchantment)enchantment.value()).canEnchant(itemStack)) {
               Utils.addEnchantment(itemStack, enchantment, level.applyAsInt((Enchantment)enchantment.value()));
            }
         }));
      this.syncItem();
   }

   private void syncItem() {
      mc.setScreen(new InventoryScreen(mc.player));
      mc.setScreen(null);
   }

   private ItemStack tryGetItemStack() throws CommandSyntaxException {
      if (!mc.player.isCreative()) {
         throw NOT_IN_CREATIVE.create();
      } else {
         ItemStack itemStack = this.getItemStack();
         if (itemStack == null) {
            throw NOT_HOLDING_ITEM.create();
         } else {
            return itemStack;
         }
      }
   }

   private ItemStack getItemStack() {
      ItemStack itemStack = mc.player.getMainHandItem();
      if (itemStack == null) {
         itemStack = mc.player.getOffhandItem();
      }

      return itemStack.isEmpty() ? null : itemStack;
   }
}
