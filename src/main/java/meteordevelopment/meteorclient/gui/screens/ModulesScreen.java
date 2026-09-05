package meteordevelopment.meteorclient.gui.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import net.minecraft.world.item.Items;

public class ModulesScreen extends TabScreen {
   private ModulesScreen.WCategoryController controller;

   public ModulesScreen(GuiTheme theme) {
      super(theme, Tabs.get().getFirst());
   }

   @Override
   public void initWidgets() {
      this.controller = this.add(new ModulesScreen.WCategoryController()).widget();
      WVerticalList help = this.add(this.theme.verticalList()).pad(4.0).bottom().widget();
      help.add(this.theme.label("Left click - Toggle module"));
      help.add(this.theme.label("Right click - Open module settings"));
   }

   @Override
   protected void init() {
      super.init();
      this.controller.refresh();
   }

   protected WWindow createCategory(WContainer c, Category category) {
      WWindow w = this.theme.window(category.name);
      w.id = category.name;
      w.padding = 0.0;
      w.spacing = 0.0;
      if (this.theme.categoryIcons()) {
         w.beforeHeaderInit = wContainer -> wContainer.add(this.theme.item(category.icon)).pad(2.0);
      }

      c.add(w);
      w.view.scrollOnlyWhenMouseOver = true;
      w.view.hasScrollBar = false;
      w.view.spacing = 0.0;

      for (Module module : Modules.get().getGroup(category)) {
         w.add(this.theme.module(module)).expandX();
      }

      return w;
   }

   protected void createSearchW(WContainer w, String text) {
      if (!text.isEmpty()) {
         Set<Module> modules = Modules.get().searchTitles(text);
         if (!modules.isEmpty()) {
            WSection section = w.add(this.theme.section("Modules")).expandX().widget();
            section.spacing = 0.0;
            int count = 0;

            for (Module module : modules) {
               if (count >= Config.get().moduleSearchCount.get() || count >= modules.size()) {
                  break;
               }

               section.add(this.theme.module(module)).expandX();
               count++;
            }
         }

         modules = Modules.get().searchSettingTitles(text);
         if (!modules.isEmpty()) {
            WSection section = w.add(this.theme.section("Settings")).expandX().widget();
            section.spacing = 0.0;
            int count = 0;

            for (Module module : modules) {
               if (count >= Config.get().moduleSearchCount.get() || count >= modules.size()) {
                  break;
               }

               section.add(this.theme.module(module)).expandX();
               count++;
            }
         }
      }
   }

   protected WWindow createSearch(WContainer c) {
      WWindow w = this.theme.window("Search");
      w.id = "search";
      if (this.theme.categoryIcons()) {
         w.beforeHeaderInit = wContainer -> wContainer.add(this.theme.item(Items.COMPASS.getDefaultInstance())).pad(2.0);
      }

      c.add(w);
      w.view.scrollOnlyWhenMouseOver = true;
      w.view.hasScrollBar = false;
      w.view.maxHeight -= 20.0;
      WVerticalList l = this.theme.verticalList();
      WTextBox text = w.add(this.theme.textBox("")).minWidth(140.0).expandX().widget();
      text.setFocused(true);
      text.action = () -> {
         l.clear();
         this.createSearchW(l, text.get());
      };
      w.add(l).expandX();
      this.createSearchW(l, text.get());
      return w;
   }

   protected Cell<WWindow> createFavorites(WContainer c) {
      boolean hasFavorites = Modules.get().getAll().stream().anyMatch(module -> module.favorite);
      if (!hasFavorites) {
         return null;
      } else {
         WWindow w = this.theme.window("Favorites");
         w.id = "favorites";
         w.padding = 0.0;
         w.spacing = 0.0;
         if (this.theme.categoryIcons()) {
            w.beforeHeaderInit = wContainer -> wContainer.add(this.theme.item(Items.NETHER_STAR.getDefaultInstance())).pad(2.0);
         }

         Cell<WWindow> cell = c.add(w);
         w.view.scrollOnlyWhenMouseOver = true;
         w.view.hasScrollBar = false;
         w.view.spacing = 0.0;
         this.createFavoritesW(w);
         return cell;
      }
   }

   protected boolean createFavoritesW(WWindow w) {
      List<Module> modules = new ArrayList<>();

      for (Module module : Modules.get().getAll()) {
         if (module.favorite) {
            modules.add(module);
         }
      }

      modules.sort((o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.name, o2.name));

      for (Module modulex : modules) {
         w.add(this.theme.module(modulex)).expandX();
      }

      return !modules.isEmpty();
   }

   @Override
   public boolean toClipboard() {
      return NbtUtils.toClipboard(Modules.get());
   }

   @Override
   public boolean fromClipboard() {
      return NbtUtils.fromClipboard(Modules.get());
   }

   @Override
   public void reload() {
   }

   protected class WCategoryController extends WContainer {
      public final List<WWindow> windows = new ArrayList<>();
      private Cell<WWindow> favorites;

      @Override
      public void init() {
         for (Category category : Modules.loopCategories()) {
            this.windows.add(ModulesScreen.this.createCategory(this, category));
         }

         this.windows.add(ModulesScreen.this.createSearch(this));
         this.refresh();
      }

      protected void refresh() {
         if (this.favorites == null) {
            this.favorites = ModulesScreen.this.createFavorites(this);
            if (this.favorites != null) {
               this.windows.add(this.favorites.widget());
            }
         } else {
            this.favorites.widget().clear();
            if (!ModulesScreen.this.createFavoritesW(this.favorites.widget())) {
               this.remove(this.favorites);
               this.windows.remove(this.favorites.widget());
               this.favorites = null;
            }
         }
      }

      @Override
      protected void onCalculateWidgetPositions() {
         double pad = this.theme.scale(4.0);
         double h = this.theme.scale(40.0);
         double x = this.x + pad;
         double y = this.y;

         for (Cell<?> cell : this.cells) {
            double windowWidth = (double)Utils.getWindowWidth();
            double windowHeight = (double)Utils.getWindowHeight();
            if (x + cell.width > windowWidth) {
               x += pad;
               y += h;
            }

            if (x > windowWidth) {
               x = windowWidth / 2.0 - cell.width / 2.0;
               if (x < 0.0) {
                  x = 0.0;
               }
            }

            if (y > windowHeight) {
               y = windowHeight / 2.0 - cell.height / 2.0;
               if (y < 0.0) {
                  y = 0.0;
               }
            }

            cell.x = x;
            cell.y = y;
            cell.width = cell.widget().width;
            cell.height = cell.widget().height;
            cell.alignWidget();
            x += cell.width + pad;
         }
      }
   }
}
