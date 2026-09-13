package meteordevelopment.meteorclient.utils.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Central NeoForge API compatibility bridge for Meteor Client.
 * Provides unified, safe methods for NeoForge ItemAbilities, IItemExtension,
 * IBlockExtension, FluidType, Capabilities, and defensive Holder queries.
 */
public final class NeoForgeUtils {
    private NeoForgeUtils() {}

    // ==========================================
    // Tool & Mining Abilities
    // ==========================================

    public static boolean canPerformAction(ItemStack stack, ItemAbility ability) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            return stack.canPerformAction(ability);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isPickaxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return canPerformAction(stack, ItemAbilities.PICKAXE_DIG) || stack.is(ItemTags.PICKAXES);
    }

    public static boolean isAxe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return canPerformAction(stack, ItemAbilities.AXE_DIG) 
            || stack.getItem() instanceof AxeItem 
            || stack.is(ItemTags.AXES);
    }

    public static boolean isShovel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return canPerformAction(stack, ItemAbilities.SHOVEL_DIG) || stack.is(ItemTags.SHOVELS);
    }

    public static boolean isHoe(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return canPerformAction(stack, ItemAbilities.HOE_DIG) || stack.is(ItemTags.HOES);
    }

    public static boolean isShears(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return canPerformAction(stack, ItemAbilities.SHEARS_DIG) 
            || stack.getItem() instanceof ShearsItem 
            || stack.is(Items.SHEARS)
            || stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_SHEAR);
    }

    public static boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        if (item instanceof TieredItem || item instanceof ShearsItem) return true;
        if (stack.getComponents().has(DataComponents.TOOL)) return true;
        return isPickaxe(stack) || isAxe(stack) || isShovel(stack) || isHoe(stack) || isShears(stack);
    }

    public static boolean isTool(Item item) {
        if (item == null) return false;
        return item instanceof TieredItem || item instanceof ShearsItem || item.components().has(DataComponents.TOOL);
    }

    // ==========================================
    // Weapon & Combat Abilities
    // ==========================================

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return canPerformAction(stack, ItemAbilities.SWORD_DIG) 
            || canPerformAction(stack, ItemAbilities.SWORD_SWEEP) 
            || stack.getItem() instanceof SwordItem 
            || stack.is(ItemTags.SWORDS);
    }

    public static boolean isMace(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof MaceItem 
            || stack.is(Items.MACE)
            || stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_MACE);
    }

    public static boolean isShield(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return canPerformAction(stack, ItemAbilities.SHIELD_BLOCK) || stack.getItem() instanceof ShieldItem;
    }

    public static boolean isWeapon(ItemStack stack) {
        return isSword(stack) || isAxe(stack) || isMace(stack);
    }

    // ==========================================
    // Elytra & Flight
    // ==========================================

    public static boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.ELYTRA)) return true;
        try {
            return stack.canElytraFly(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ==========================================
    // Food & Consumables
    // ==========================================

    public static FoodProperties getFoodProperties(ItemStack stack, LivingEntity entity) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            FoodProperties props = stack.getFoodProperties(entity);
            if (props != null) return props;
        } catch (Throwable ignored) {}
        return stack.get(DataComponents.FOOD);
    }

    public static boolean isEdible(ItemStack stack, LivingEntity entity) {
        return getFoodProperties(stack, entity) != null;
    }

    // ==========================================
    // Block Harvest & Destroy Speed
    // ==========================================

    public static boolean canHarvestBlock(ItemStack stack, BlockState state, BlockGetter level, BlockPos pos, Player player) {
        if (state == null) return false;
        try {
            if (player != null && level != null && pos != null) {
                return state.canHarvestBlock(level, pos, player);
            }
        } catch (Throwable ignored) {}
        if (stack != null && !stack.isEmpty()) {
            try {
                return stack.isCorrectToolForDrops(state);
            } catch (Throwable ignored) {}
        }
        return !state.requiresCorrectToolForDrops();
    }

    public static float getDestroySpeed(ItemStack stack, BlockState state, Level level, BlockPos pos) {
        if (stack == null || stack.isEmpty() || state == null) return 1.0F;
        try {
            return stack.getDestroySpeed(state);
        } catch (Throwable ignored) {
            return 1.0F;
        }
    }

    // ==========================================
    // Fluids & Liquids
    // ==========================================

    public static boolean isFluid(BlockState state) {
        if (state == null) return false;
        return !state.getFluidState().isEmpty();
    }

    public static FluidType getFluidType(FluidState state) {
        if (state == null) return null;
        try {
            return state.getFluidType();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ==========================================
    // Capabilities (ItemHandler)
    // ==========================================

    public static IItemHandler getItemHandler(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            return stack.getCapability(Capabilities.ItemHandler.ITEM);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static IItemHandler getBlockItemHandler(Level level, BlockPos pos, Direction side) {
        if (level == null || pos == null) return null;
        try {
            return level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ==========================================
    // Defensive Registry / Holder Utilities
    // ==========================================

    public static boolean isBound(Holder<?> holder) {
        if (holder == null) return false;
        try {
            return holder.isBound();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static <T> T getValueSafe(Holder<T> holder) {
        if (!isBound(holder)) return null;
        try {
            return holder.value();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ==========================================
    // Mod Detection
    // ==========================================

    public static boolean isModLoaded(String modId) {
        if (modId == null || modId.isEmpty()) return false;
        try {
            return LoadingModList.get() != null && LoadingModList.get().getModFileById(modId) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
