package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;

public abstract class DynamicRegistryListSettingScreen<E> extends WindowScreen {
   protected final Setting<?> setting;
   protected final Collection<ResourceKey<E>> collection;
   private final ResourceKey<Registry<E>> registryKey;
   private final Optional<Registry<E>> registry;
   private WTextBox filter;
   private String filterText = "";
   private WTable table;

   public DynamicRegistryListSettingScreen(
      GuiTheme theme, String title, Setting<?> setting, Collection<ResourceKey<E>> collection, ResourceKey<Registry<E>> registryKey
   ) {
      super(theme, title);
      this.registryKey = registryKey;
      this.registry = Optional.ofNullable(Minecraft.getInstance().getConnection())
         .flatMap(networkHandler -> networkHandler.registryAccess().registry(registryKey));
      this.setting = setting;
      this.collection = collection;
   }

   @Override
   public void initWidgets() {
      this.filter = this.add(this.theme.textBox("")).minWidth(400.0).expandX().widget();
      this.filter.setFocused(true);
      this.filter.action = () -> {
         this.filterText = this.filter.get().trim();
         this.table.clear();
         this.generateWidgets();
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.generateWidgets();
   }

   private void generateWidgets() {
      WTable left = this.abc(
         pairs -> this.registry
               .ifPresent(registry -> registry.holders().<Optional>map(Reference::unwrapKey).filter(Optional::isPresent).map(Optional::get).forEach(t -> {
                     if (!this.skipValue((ResourceKey<E>)t) && !this.collection.contains(t)) {
                        int words = Utils.searchInWords(this.getValueName((ResourceKey<E>)t), this.filterText);
                        int diff = Utils.searchLevenshteinDefault(this.getValueName((ResourceKey<E>)t), this.filterText, false);
                        if (words > 0 || diff <= this.getValueName((ResourceKey<E>)t).length() / 2) {
                           pairs.add(new Tuple(t, -diff));
                        }
                     }
                  })),
         true,
         t -> {
            this.addValue(t);
            ResourceKey<E> v = this.getAdditionalValue(t);
            if (v != null) {
               this.addValue(v);
            }
         }
      );
      if (!left.cells.isEmpty()) {
         left.add(this.theme.horizontalSeparator()).expandX();
         left.row();
      }

      WHorizontalList manualEntry = left.add(this.theme.horizontalList()).expandX().widget();
      WTextBox textBox = manualEntry.add(this.theme.textBox("minecraft:")).expandX().minWidth(120.0).widget();
      manualEntry.add(this.theme.plus()).expandCellX().right().widget().action = () -> {
         String entry = textBox.get().trim();

         try {
            ResourceLocation id = entry.contains(":") ? ResourceLocation.parse(entry) : ResourceLocation.withDefaultNamespace(entry);
            this.addValue(ResourceKey.create(this.registryKey, id));
         } catch (ResourceLocationException var4) {
         }
      };
      this.table.add(this.theme.verticalSeparator()).expandWidgetY();
      this.abc(pairs -> {
         for (ResourceKey<E> value : this.collection) {
            if (!this.skipValue(value)) {
               int words = Utils.searchInWords(this.getValueName(value), this.filterText);
               int diff = Utils.searchLevenshteinDefault(this.getValueName(value), this.filterText, false);
               if (words > 0 || diff <= this.getValueName(value).length() / 2) {
                  pairs.add(new Tuple(value, -diff));
               }
            }
         }
      }, false, t -> {
         this.removeValue(t);
         ResourceKey<E> v = this.getAdditionalValue(t);
         if (v != null) {
            this.removeValue(v);
         }
      });
   }

   private void addValue(ResourceKey<E> value) {
      if (!this.collection.contains(value)) {
         this.collection.add(value);
         this.setting.onChanged();
         this.table.clear();
         this.generateWidgets();
      }
   }

   private void removeValue(ResourceKey<E> value) {
      if (this.collection.remove(value)) {
         this.setting.onChanged();
         this.table.clear();
         this.generateWidgets();
      }
   }

   private WTable abc(Consumer<List<Tuple<ResourceKey<E>, Integer>>> addValues, boolean isLeft, Consumer<ResourceKey<E>> buttonAction) {
      Cell<WTable> cell = this.table.add(this.theme.table()).top();
      WTable table = cell.widget();
      Consumer<ResourceKey<E>> forEach = t -> {
         if (this.includeValue(t)) {
            table.add(this.getValueWidget(t));
            WPressable button = table.add((WPressable)(isLeft ? this.theme.plus() : this.theme.minus())).expandCellX().right().widget();
            button.action = () -> buttonAction.accept(t);
            table.row();
         }
      };
      List<Tuple<ResourceKey<E>, Integer>> values = new ArrayList<>();
      addValues.accept(values);
      if (!this.filterText.isEmpty()) {
         values.sort(Comparator.comparingInt(value -> -(Integer)value.getB()));
      }

      for (Tuple<ResourceKey<E>, Integer> pair : values) {
         forEach.accept((ResourceKey<E>)pair.getA());
      }

      if (!table.cells.isEmpty()) {
         cell.expandX();
      }

      return table;
   }

   protected boolean includeValue(ResourceKey<E> value) {
      return true;
   }

   protected abstract WWidget getValueWidget(ResourceKey<E> var1);

   protected abstract String getValueName(ResourceKey<E> var1);

   protected boolean skipValue(ResourceKey<E> value) {
      return false;
   }

   protected ResourceKey<E> getAdditionalValue(ResourceKey<E> value) {
      return null;
   }
}
