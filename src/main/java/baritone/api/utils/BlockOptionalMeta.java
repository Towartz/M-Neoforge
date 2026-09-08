package baritone.api.utils;

import baritone.api.utils.accessor.IItemStack;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import meteordevelopment.meteorclient.utils.world.OreDropHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class BlockOptionalMeta {
   private static final Pattern PATTERN = Pattern.compile("^(?<id>.+?)(?:\\[(?<properties>.+?)?\\])?$");
   private final Block block;
   private final String propertiesDescription;
   private final Set<BlockState> blockstates;
   private final ImmutableSet<Integer> stateHashes;
   private final ImmutableSet<Integer> stackHashes;

   public BlockOptionalMeta(@Nonnull Block block) {
      this.block = block;
      this.propertiesDescription = "{}";
      this.blockstates = getStates(block, Collections.emptyMap());
      this.stateHashes = getStateHashes(this.blockstates);
      this.stackHashes = getStackHashes(this.blockstates, this.block);
   }

   public BlockOptionalMeta(@Nonnull String selector) {
      Matcher matcher = PATTERN.matcher(selector);
      if (!matcher.find()) {
         throw new IllegalArgumentException("invalid block selector");
      } else {
         this.block = BlockUtils.stringToBlockRequired(matcher.group("id"));
         String props = matcher.group("properties");
         Map<Property<?>, ?> properties = props != null && !props.isEmpty() ? parseProperties(this.block, props) : Collections.emptyMap();
         this.propertiesDescription = props == null ? "{}" : "{" + props.replace("=", ":") + "}";
         this.blockstates = getStates(this.block, properties);
         this.stateHashes = getStateHashes(this.blockstates);
         this.stackHashes = getStackHashes(this.blockstates, this.block);
      }
   }

   @SuppressWarnings("unchecked")
   private static <C extends Comparable<C>, P extends Property<C>> P castToIProperty(Object value) {
      return (P)value;
   }

   private static Map<Property<?>, ?> parseProperties(Block block, String raw) {
      Builder<Property<?>, Object> builder = ImmutableMap.builder();

      for (String pair : raw.split(",")) {
         String[] parts = pair.split("=");
         if (parts.length != 2) {
            throw new IllegalArgumentException(String.format("\"%s\" is not a valid property-value pair", pair));
         }

         String rawKey = parts[0];
         String rawValue = parts[1];
         Property<?> key = block.getStateDefinition().getProperty(rawKey);
         if (key == null) {
            throw new IllegalArgumentException(String.format("Property \"%s\" does not exist on %s", rawKey, block));
         }

         Comparable<?> value = (Comparable<?>)castToIProperty(key)
            .getValue(rawValue)
            .orElseThrow(() -> new IllegalArgumentException(String.format("\"%s\" is not a valid value for %s on %s", rawValue, key, block)));
         builder.put(key, value);
      }

      return builder.build();
   }

   private static Set<BlockState> getStates(@Nonnull Block block, @Nonnull Map<Property<?>, ?> properties) {
      return block.getStateDefinition()
         .getPossibleStates()
         .stream()
         .filter(blockstate -> properties.entrySet().stream().allMatch(entry -> blockstate.getValue(entry.getKey()) == entry.getValue()))
         .collect(Collectors.toSet());
   }

   private static ImmutableSet<Integer> getStateHashes(Set<BlockState> blockstates) {
      return ImmutableSet.copyOf(blockstates.stream().map(Object::hashCode).toArray(Integer[]::new));
   }

   private static ImmutableSet<Integer> getStackHashes(Set<BlockState> blockstates, Block block) {
      Set<Integer> hashes = new HashSet<>();

      // 1. Block's direct item (e.g. Silk Touch)
      Item blockItem = block.asItem();
      if (blockItem != null && blockItem != Items.AIR) {
         ItemStack stack = new ItemStack(blockItem, 1);
         hashes.add(((IItemStack)(Object)stack).getBaritoneHash());
      }

      // 2. All items dropped by this block (Vanilla table + Modded heuristics + learned drops)
      for (Item droppedItem : drops(block)) {
         if (droppedItem != null && droppedItem != Items.AIR) {
            ItemStack stack = new ItemStack(droppedItem, 1);
            hashes.add(((IItemStack)(Object)stack).getBaritoneHash());
         }
      }

      return ImmutableSet.copyOf(hashes);
   }

   public Block getBlock() {
      return this.block;
   }

   public boolean matches(@Nonnull Block block) {
      return block == this.block;
   }

   public boolean matches(@Nonnull BlockState blockstate) {
      Block block = blockstate.getBlock();
      return block == this.block && this.stateHashes.contains(blockstate.hashCode());
   }

   public boolean matches(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return false;
      int hash = ((IItemStack)(Object)stack).getBaritoneHash();
      hash -= stack.getDamageValue();
      return this.stackHashes.contains(hash);
   }

   @Override
   public String toString() {
      return String.format("BlockOptionalMeta{block=%s,properties=%s}", this.block, this.propertiesDescription);
   }

   public BlockState getAnyBlockState() {
      return !this.blockstates.isEmpty() ? this.blockstates.iterator().next() : null;
   }

   public Set<BlockState> getAllBlockStates() {
      return this.blockstates;
   }

   public Set<Integer> stackHashes() {
      return this.stackHashes;
   }

   public static synchronized List<Item> drops(Block b) {
      if (b == null) return Collections.emptyList();
      Set<Item> set = OreDropHelper.getPossibleDrops(b);
      return new ArrayList<>(set);
   }
}
