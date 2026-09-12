package com.cope.meteormcp.minecraft.tools;

import com.cope.meteormcp.minecraft.MinecraftToolContext;
import com.cope.meteormcp.minecraft.MinecraftToolContext.ToolParam;
import com.cope.meteormcp.minecraft.MinecraftToolHandler;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class InventoryTools implements MinecraftToolHandler {

   @Override
   public List<Tool> getTools() {
      List<Tool> list = new ArrayList<>();

      list.add(MinecraftToolContext.defineTool("get_inventory", "Lists all items in player inventory, hotbar, armor, and offhand.", Map.of()));

      Map<String, ToolParam> equipParams = new LinkedHashMap<>();
      equipParams.put("item_name", new ToolParam("string", "Item name to equip", true));
      equipParams.put("slot", new ToolParam("string", "Target slot: 'mainhand', 'offhand', 'head', 'chest', 'legs', 'feet'", false));
      list.add(MinecraftToolContext.defineTool("equip_item", "Equips an item from inventory into main hand, offhand, or armor.", equipParams));

      Map<String, ToolParam> dropParams = new LinkedHashMap<>();
      dropParams.put("item_name", new ToolParam("string", "Item name to drop", true));
      dropParams.put("all", new ToolParam("boolean", "Whether to drop the entire stack (default false)", false));
      list.add(MinecraftToolContext.defineTool("drop_item", "Drops specified item from player inventory.", dropParams));

      Map<String, ToolParam> useParams = new LinkedHashMap<>();
      useParams.put("hand", new ToolParam("string", "Hand to use: 'mainhand' or 'offhand' (default 'mainhand')", false));
      list.add(MinecraftToolContext.defineTool("use_item", "Uses the item held in the specified hand (right click).", useParams));

      list.add(MinecraftToolContext.defineTool("eat_food", "Finds food in player inventory, equips it, and eats.", Map.of()));

      return list;
   }

   @Override
   public CallToolResult execute(String toolName, Map<String, Object> arguments) {
      switch (toolName) {
         case "get_inventory": {
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.player == null) return MinecraftToolContext.error("Player not available.");
               Map<String, Object> invData = new LinkedHashMap<>();

               List<Map<String, Object>> mainInv = new ArrayList<>();
               for (int i = 0; i < mc.player.getInventory().items.size(); i++) {
                  ItemStack stack = mc.player.getInventory().items.get(i);
                  if (!stack.isEmpty()) {
                     Map<String, Object> itemData = new LinkedHashMap<>();
                     itemData.put("slot", i);
                     itemData.put("isHotbar", i < 9);
                     itemData.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                     itemData.put("name", stack.getHoverName().getString());
                     itemData.put("count", stack.getCount());
                     if (stack.isDamageableItem()) {
                        itemData.put("damage", stack.getDamageValue());
                        itemData.put("maxDamage", stack.getMaxDamage());
                     }
                     mainInv.add(itemData);
                  }
               }
               invData.put("inventory", mainInv);

               List<Map<String, Object>> armor = new ArrayList<>();
               for (int i = 0; i < mc.player.getInventory().armor.size(); i++) {
                  ItemStack stack = mc.player.getInventory().armor.get(i);
                  if (!stack.isEmpty()) {
                     Map<String, Object> itemData = new LinkedHashMap<>();
                     itemData.put("slot", i);
                     itemData.put("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                     itemData.put("name", stack.getHoverName().getString());
                     armor.add(itemData);
                  }
               }
               invData.put("armor", armor);

               ItemStack offhand = mc.player.getOffhandItem();
               if (!offhand.isEmpty()) {
                  Map<String, Object> itemData = new LinkedHashMap<>();
                  itemData.put("id", BuiltInRegistries.ITEM.getKey(offhand.getItem()).toString());
                  itemData.put("name", offhand.getHoverName().getString());
                  itemData.put("count", offhand.getCount());
                  invData.put("offhand", itemData);
               } else {
                  invData.put("offhand", null);
               }

               return MinecraftToolContext.json(invData);
            });
         }
         case "equip_item": {
            String itemName = MinecraftToolContext.getString(arguments, "item_name", "");
            if (itemName.isBlank()) return MinecraftToolContext.error("item_name is required.");
            String slot = MinecraftToolContext.getString(arguments, "slot", "mainhand").toLowerCase();
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.player == null) return MinecraftToolContext.error("Player not available.");
               ResourceLocation rl = itemName.contains(":") ? ResourceLocation.parse(itemName) : ResourceLocation.withDefaultNamespace(itemName);
               Item item = BuiltInRegistries.ITEM.get(rl);
               if (item == Items.AIR) return MinecraftToolContext.error("Unknown item: " + itemName);
               FindItemResult res = InvUtils.find(item);
               if (!res.found()) return MinecraftToolContext.error("Item " + itemName + " not found in inventory.");

               if (slot.equals("offhand")) {
                  InvUtils.move().from(res.slot()).toOffhand();
                  return MinecraftToolContext.result("Equipped " + itemName + " into offhand.");
               } else if (res.isHotbar()) {
                  InvUtils.swap(res.slot(), false);
                  return MinecraftToolContext.result("Selected " + itemName + " in hotbar slot " + res.slot());
               } else {
                  InvUtils.swap(res.slot(), true);
                  return MinecraftToolContext.result("Swapped " + itemName + " to hotbar.");
               }
            });
         }
         case "drop_item": {
            String itemName = MinecraftToolContext.getString(arguments, "item_name", "");
            if (itemName.isBlank()) return MinecraftToolContext.error("item_name is required.");
            boolean dropAll = MinecraftToolContext.getBoolean(arguments, "all", false);
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.player == null) return MinecraftToolContext.error("Player not available.");
               ResourceLocation rl = itemName.contains(":") ? ResourceLocation.parse(itemName) : ResourceLocation.withDefaultNamespace(itemName);
               Item item = BuiltInRegistries.ITEM.get(rl);
               if (item == Items.AIR) return MinecraftToolContext.error("Unknown item: " + itemName);
               FindItemResult res = InvUtils.find(item);
               if (!res.found()) return MinecraftToolContext.error("Item " + itemName + " not found in inventory.");
               if (res.isHotbar()) {
                  InvUtils.swap(res.slot(), false);
                  mc.player.drop(dropAll);
                  return MinecraftToolContext.result("Dropped " + (dropAll ? "stack of " : "1x ") + itemName);
               } else {
                  InvUtils.drop().slot(res.slot());
                  return MinecraftToolContext.result("Dropped slot " + res.slot() + " (" + itemName + ")");
               }
            });
         }
         case "use_item": {
            String handStr = MinecraftToolContext.getString(arguments, "hand", "mainhand");
            InteractionHand hand = handStr.equalsIgnoreCase("offhand") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.player == null || mc.gameMode == null) return MinecraftToolContext.error("Player/gameMode not available.");
               mc.gameMode.useItem(mc.player, hand);
               return MinecraftToolContext.result("Used item in " + handStr);
            });
         }
         case "eat_food": {
            return MinecraftToolContext.runOnClientSync(() -> {
               Minecraft mc = MinecraftToolContext.mc();
               if (mc.player == null || mc.gameMode == null) return MinecraftToolContext.error("Player not available.");
               FindItemResult food = InvUtils.find(stack -> stack.getComponents().has(DataComponents.FOOD));
               if (!food.found()) return MinecraftToolContext.error("No food found in inventory.");
               if (food.isHotbar()) {
                  InvUtils.swap(food.slot(), false);
               } else {
                  InvUtils.swap(food.slot(), true);
               }
               mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
               return MinecraftToolContext.result("Eating food from slot " + food.slot());
            });
         }
         default:
            return MinecraftToolContext.error("Unknown inventory tool: " + toolName);
      }
   }
}
