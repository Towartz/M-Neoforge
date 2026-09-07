package meteordevelopment.meteorclient.systems.modules.movement.plus.noslow;

import meteordevelopment.meteorclient.events.entity.player.PlayerUseMultiplierEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;

public class NoSlowMode {
    protected final Minecraft mc;
    protected final NoSlowPlus settings = (NoSlowPlus) Modules.get().get(NoSlowPlus.class);
    private final NoSlowModes type;

    public NoSlowMode(NoSlowModes type) {
        this.mc = Minecraft.getInstance();
        this.type = type;
    }

    public void onUse(PlayerUseMultiplierEvent event) {}
    public void onTickEventPre(TickEvent.Pre event) {}
    public void onActivate() {}
    public void onDeactivate() {}
}
