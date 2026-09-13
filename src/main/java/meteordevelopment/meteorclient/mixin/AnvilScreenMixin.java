package meteordevelopment.meteorclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.ExploitPreventer;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AnvilScreen.class)
public abstract class AnvilScreenMixin {
    @ModifyExpressionValue(
        method = "slotChanged",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;")
    )
    private Component onGetHoverName(Component original) {
        if (Modules.get() != null) {
            ExploitPreventer ep = Modules.get().get(ExploitPreventer.class);
            if (ep != null && ep.isActive() && ep.antiAnvilLeak.get()) {
                return ExploitPreventer.sanitizeComponent(original);
            }
        }
        return original;
    }
}
