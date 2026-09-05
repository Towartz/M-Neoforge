package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.core.Registry;
import net.minecraft.util.Tuple;

public abstract class RegistryListSettingScreen<T> extends WindowScreen {
   protected final Setting<?> setting;
   protected final Collection<T> collection;
   private final Registry<T> registry;
   private WTextBox filter;
   private String filterText = "";
   private WTable table;

   public RegistryListSettingScreen(GuiTheme theme, String title, Setting<?> setting, Collection<T> collection, Registry<T> registry) {
      super(theme, title);
      this.registry = registry;
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
         this.initWidgets(this.registry);
      };
      this.table = this.add(this.theme.table()).expandX().widget();
      this.initWidgets(this.registry);
   }

   private int calculateMatchScore(Registry<T> registry, T value, String filter) {
      if (filter == null || filter.isEmpty()) {
         return 0;
      }
      String cleanFilter = filter.toLowerCase(java.util.Locale.ROOT);
      net.minecraft.resources.ResourceLocation id = registry.getKey(value);
      if (id != null) {
         if (cleanFilter.startsWith("@")) {
            String mod = cleanFilter.substring(1);
            if (id.getNamespace().toLowerCase(java.util.Locale.ROOT).contains(mod)) {
               return 0;
            }
         }
         if (cleanFilter.contains(":")) {
            if (id.toString().toLowerCase(java.util.Locale.ROOT).contains(cleanFilter)) {
               return 0;
            }
         }
         if (id.getPath().toLowerCase(java.util.Locale.ROOT).contains(cleanFilter)) {
            return 0;
         }
      }

      String name = this.getValueName(value);
      if (name != null) {
         int words = Utils.searchInWords(name, filter);
         int diff = Utils.searchLevenshteinDefault(name, filter, false);
         if (words > 0 || diff <= name.length() / 2) {
            return diff;
         }
      }

      return -1;
   }

   private void initWidgets(Registry<T> registry) {
      WTable left = this.abc(pairs -> registry.forEach(t -> {
            if (!this.skipValue((T)t) && !this.collection.contains(t)) {
               int score = this.calculateMatchScore(registry, (T)t, this.filterText);
               if (score >= 0) {
                  pairs.add(new Tuple(t, -score));
               }
            }
         }), true, t -> {
         this.addValue(registry, t);
         T v = this.getAdditionalValue(t);
         if (v != null) {
            this.addValue(registry, v);
         }
      });
      if (!left.cells.isEmpty()) {
         this.table.add(this.theme.verticalSeparator()).expandWidgetY();
      }

      this.abc(pairs -> {
         for (T value : this.collection) {
            if (!this.skipValue(value)) {
               int score = this.calculateMatchScore(registry, value, this.filterText);
               if (score >= 0) {
                  pairs.add(new Tuple(value, -score));
               }
            }
         }
      }, false, t -> {
         this.removeValue(registry, t);
         T v = this.getAdditionalValue(t);
         if (v != null) {
            this.removeValue(registry, v);
         }
      });
   }

   private void addValue(Registry<T> registry, T value) {
      if (!this.collection.contains(value)) {
         this.collection.add(value);
         this.setting.onChanged();
         this.table.clear();
         this.initWidgets(registry);
      }
   }

   private void removeValue(Registry<T> registry, T value) {
      if (this.collection.remove(value)) {
         this.setting.onChanged();
         this.table.clear();
         this.initWidgets(registry);
      }
   }

   private WTable abc(Consumer<List<Tuple<T, Integer>>> addValues, boolean isLeft, Consumer<T> buttonAction) {
      Cell<WTable> cell = this.table.add(this.theme.table()).top();
      WTable table = cell.widget();
      Consumer<T> forEach = t -> {
         if (this.includeValue(t)) {
            table.add(this.getValueWidget(t));
            WPressable button = table.add((WPressable)(isLeft ? this.theme.plus() : this.theme.minus())).expandCellX().right().widget();
            button.action = () -> buttonAction.accept((T)t);
            table.row();
         }
      };
      List<Tuple<T, Integer>> values = new ArrayList<>();
      addValues.accept(values);
      if (!this.filterText.isEmpty()) {
         values.sort(Comparator.comparingInt(value -> -(Integer)value.getB()));
      }

      for (Tuple<T, Integer> pair : values) {
         forEach.accept((T)pair.getA());
      }

      if (!table.cells.isEmpty()) {
         cell.expandX();
      }

      return table;
   }

   protected boolean includeValue(T value) {
      return true;
   }

   protected abstract WWidget getValueWidget(T var1);

   protected abstract String getValueName(T var1);

   protected boolean skipValue(T value) {
      return false;
   }

   protected T getAdditionalValue(T value) {
      return null;
   }
}
