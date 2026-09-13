package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;

public class BundleDupe extends Module {
    private static final Constructor<?> SELECT_BUNDLE_PACKET_CONSTRUCTOR;

    static {
        Constructor<?> ctor = null;
        try {
            Class<?> clazz = Class.forName("net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket");
            ctor = clazz.getConstructor(int.class, int.class);
        } catch (Throwable ignored) {
        }
        SELECT_BUNDLE_PACKET_CONSTRUCTOR = ctor;
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> hotbarSlot = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("hotbar-slot")
            .description("Hotbar slot (1-9) containing the bundle.")
            .defaultValue(1)
            .min(1)
            .max(9)
            .sliderMin(1)
            .sliderMax(9)
            .build()
    );

    private final Setting<Integer> bundleIndex = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("bundle-index")
            .description("Target index inside the bundle (-1 for default/unspecified).")
            .defaultValue(-1)
            .min(-1)
            .max(64)
            .build()
    );

    private final Setting<Integer> packetBurstCount = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("packet-burst")
            .description("Number of bundle selection packets to send per cycle.")
            .defaultValue(20)
            .min(1)
            .max(500)
            .sliderMax(100)
            .build()
    );

    private final Setting<Integer> pickupDelay = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("pickup-delay-ms")
            .description("Delay in milliseconds after picking up the bundle.")
            .defaultValue(30)
            .min(0)
            .max(500)
            .sliderMax(200)
            .build()
    );

    private final Setting<Integer> putbackDelay = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("putback-delay-ms")
            .description("Delay in milliseconds after putting back the bundle.")
            .defaultValue(20)
            .min(0)
            .max(500)
            .sliderMax(200)
            .build()
    );

    private final Setting<Boolean> autoDrop = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-drop")
            .description("Automatically drops duplicated non-bundle items from the slot.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> dropDelay = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("drop-delay-ms")
            .description("Delay in milliseconds between item drop clicks.")
            .defaultValue(50)
            .min(0)
            .max(500)
            .sliderMax(200)
            .build()
    );

    private final Setting<Integer> maxCycles = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("max-cycles")
            .description("Maximum cycles to execute (0 for continuous until toggled).")
            .defaultValue(1)
            .min(0)
            .max(1000)
            .build()
    );

    private Thread workerThread;
    private int cyclesRun = 0;

    public BundleDupe() {
        super(Categories.Misc, "bundle-dupe", "Automates bundle packet-burst duplication exploit loops.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.getConnection() == null) {
            info("Cannot run dupe: Not connected to a world.");
            toggle();
            return;
        }

        int slotIdx = hotbarSlot.get() - 1;
        ItemStack stack = mc.player.getInventory().getItem(slotIdx);
        if (!isBundle(stack)) {
            info("Hotbar slot " + hotbarSlot.get() + " does not contain a bundle (" + BuiltInRegistries.ITEM.getKey(stack.getItem()) + ").");
            toggle();
            return;
        }

        cyclesRun = 0;
        workerThread = new Thread(this::runDupeLoop, "BundleDupe-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @Override
    public void onDeactivate() {
        if (workerThread != null && workerThread.isAlive()) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    private void runDupeLoop() {
        while (isActive() && !Thread.currentThread().isInterrupted()) {
            if (mc.player == null || mc.getConnection() == null || mc.gameMode == null) {
                break;
            }

            int slotIdx = hotbarSlot.get() - 1;
            int handlerSlot = 36 + slotIdx;

            // 1. Pickup bundle (right click / button 1)
            sendInventoryClick(handlerSlot, 1);

            // 2. Burst bundle selection packets
            sendBundleSelectBurst(handlerSlot, packetBurstCount.get());

            // 3. Delay after pickup
            if (!sleepMs(pickupDelay.get())) break;

            // 4. Putback bundle (left click / button 0)
            sendInventoryClick(handlerSlot, 0);

            // 5. Delay after putback
            if (!sleepMs(putbackDelay.get())) break;

            // 6. Auto drop if dupe produced extra items
            if (autoDrop.get() && mc.player != null) {
                ItemStack current = mc.player.getInventory().getItem(slotIdx);
                if (!current.isEmpty() && !isBundle(current)) {
                    sendInventoryClick(handlerSlot, 1);
                    if (!sleepMs(dropDelay.get())) break;
                    sendInventoryClick(-999, 0);
                    if (!sleepMs(dropDelay.get())) break;
                }
            }

            cyclesRun++;
            if (maxCycles.get() > 0 && cyclesRun >= maxCycles.get()) {
                info("Bundle dupe finished " + cyclesRun + " cycle" + (cyclesRun == 1 ? "" : "s") + ".");
                mc.execute(this::toggle);
                break;
            }
        }
    }

    private void sendInventoryClick(int slot, int button) {
        mc.execute(() -> {
            if (mc.player != null && mc.gameMode != null && mc.player.containerMenu != null) {
                mc.gameMode.handleInventoryMouseClick(
                    mc.player.containerMenu.containerId,
                    slot,
                    button,
                    ClickType.PICKUP,
                    mc.player
                );
            }
        });
    }

    private void sendBundleSelectBurst(int handlerSlot, int count) {
        if (SELECT_BUNDLE_PACKET_CONSTRUCTOR != null && mc.getConnection() != null) {
            int index = bundleIndex.get();
            for (int i = 0; i < count && isActive(); i++) {
                try {
                    Object packet = SELECT_BUNDLE_PACKET_CONSTRUCTOR.newInstance(handlerSlot, index);
                    mc.getConnection().send((Packet<?>) packet);
                } catch (Throwable ignored) {
                    break;
                }
            }
        }
    }

    private boolean sleepMs(int ms) {
        if (ms <= 0) return true;
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isBundle(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return path.contains("bundle");
    }

    @Override
    public String getInfoString() {
        return cyclesRun > 0 ? String.valueOf(cyclesRun) : null;
    }
}
