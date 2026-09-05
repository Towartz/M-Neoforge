package meteordevelopment.meteorclient.gui.screens.settings;

import java.util.List;
import java.util.Optional;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.misc.Names;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public class StatusEffectListSettingScreen extends RegistryListSettingScreen<MobEffect> {
   public StatusEffectListSettingScreen(GuiTheme theme, Setting<List<MobEffect>> setting) {
      super(theme, "Select Effects", setting, setting.get(), BuiltInRegistries.MOB_EFFECT);
   }

   protected WWidget getValueWidget(MobEffect value) {
      return this.theme.itemWithLabel(this.getPotionStack(value), this.getValueName(value));
   }

   protected String getValueName(MobEffect value) {
      return Names.get(value);
   }

   private ItemStack getPotionStack(MobEffect effect) {
      ItemStack potion = Items.POTION.getDefaultInstance();
      potion.set(
         DataComponents.POTION_CONTENTS,
         new PotionContents(
            ((PotionContents)potion.get(DataComponents.POTION_CONTENTS)).potion(),
            Optional.of(effect.getColor()),
            ((PotionContents)potion.get(DataComponents.POTION_CONTENTS)).customEffects()
         )
      );
      return potion;
   }
}
