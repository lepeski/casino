package dev.lixqa.egyptiancasino.slotmachine;

import dev.lixqa.egyptiancasino.EgyptianCasinoPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SlotMachineInstance {

    private final EgyptianCasinoPlugin plugin;
    private final SlotMachineManager manager;
    private final UUID owner;
    private final Location baseLocation;
    private final UUID machineId = UUID.randomUUID();
    private BlockDisplay bodyDisplay;
    private ItemDisplay[] reelDisplays;
    private ItemDisplay leverDisplay;
    private boolean spinning;
    private BukkitTask spinTask;
    private BukkitTask leverResetTask;
    private Transformation leverRestTransformation;
    private Transformation leverPulledTransformation;

    public SlotMachineInstance(EgyptianCasinoPlugin plugin, SlotMachineManager manager, UUID owner, Location baseLocation) {
        this.plugin = plugin;
        this.manager = manager;
        this.owner = owner;
        this.baseLocation = baseLocation;
    }

    public UUID getMachineId() {
        return machineId;
    }

    public UUID getOwner() {
        return owner;
    }

    public Location getBaseLocation() {
        return baseLocation.clone();
    }

    public Location getVisualLocation() {
        return baseLocation.clone().add(0.5, 1.2, 0.5);
    }

    public boolean spawn() {
        try {
            World world = baseLocation.getWorld();
            Location bodyLocation = baseLocation.clone().add(0.5, 1.0, 0.5);
            bodyDisplay = world.spawn(bodyLocation, BlockDisplay.class, display -> {
                display.setBlock(Material.SMOOTH_SANDSTONE.createBlockData());
                display.setInterpolationDuration(5);
                markDisplay(display);
            });
            bodyDisplay.setBrightness(new Display.Brightness(15, 15));

            reelDisplays = new ItemDisplay[3];
            for (int i = 0; i < reelDisplays.length; i++) {
                double offsetX = -0.35 + (0.35 * i);
                Location reelLocation = baseLocation.clone().add(0.5 + offsetX, 1.25, 0.25);
                int symbolModel = manager.randomSymbolModel();
                reelDisplays[i] = world.spawn(reelLocation, ItemDisplay.class, display -> {
                    display.setItemStack(manager.createReelItem(symbolModel));
                    display.setBillboard(Display.Billboard.FIXED);
                    display.setInterpolationDuration(2);
                    markDisplay(display);
                });
            }

            Location leverLocation = baseLocation.clone().add(0.95, 1.2, 0.2);
            leverDisplay = world.spawn(leverLocation, ItemDisplay.class, display -> {
                display.setItemStack(manager.createLeverItem());
                display.setBillboard(Display.Billboard.FIXED);
                display.setInterpolationDuration(5);
                markDisplay(display);
            });
            leverRestTransformation = leverDisplay.getTransformation();
            leverPulledTransformation = new Transformation(new Vector3f(0.0f, -0.25f, 0.05f), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf());
            leverDisplay.setTransformation(leverRestTransformation);
            return true;
        } catch (Exception exception) {
            plugin.getLogger().severe("Unable to spawn Slot Machine: " + exception.getMessage());
            return false;
        }
    }

    private void markDisplay(Display display) {
        display.getPersistentDataContainer().set(plugin.getSlotMachineEntityKey(), PersistentDataType.STRING, machineId.toString());
        display.getPersistentDataContainer().set(plugin.getSlotMachineOwnerKey(), PersistentDataType.STRING, owner.toString());
    }

    public void handleUse(Player player) {
        if (player.isSneaking() && player.getUniqueId().equals(owner)) {
            plugin.sendMessage(player, Component.text("Sneak-left click to break the machine.", NamedTextColor.YELLOW));
            return;
        }
        manager.startSpin(this, player);
    }

    public boolean isSpinning() {
        return spinning;
    }

    public void spin(Player player, Consumer<SlotOutcome> callback) {
        spinning = true;
        World world = baseLocation.getWorld();
        Location visual = getVisualLocation();
        world.playSound(visual, Sound.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.8f, 0.9f);
        pullLever();

        List<Integer> symbols = manager.getReelSymbolModels();
        int[] indices = new int[reelDisplays.length];
        Arrays.setAll(indices, i -> world.getRandom().nextInt(symbols.size()));
        int[] finalModels = new int[reelDisplays.length];

        spinTask = new BukkitRunnable() {
            int tick = 0;
            boolean firstStopped = false;
            boolean secondStopped = false;
            boolean thirdStopped = false;

            @Override
            public void run() {
                tick++;

                if (tick % 4 == 0) {
                    for (int i = 0; i < reelDisplays.length; i++) {
                        if ((i == 0 && firstStopped) || (i == 1 && secondStopped) || (i == 2 && thirdStopped)) {
                            continue;
                        }
                        indices[i] = (indices[i] + 1) % symbols.size();
                        reelDisplays[i].setItemStack(manager.createReelItem(symbols.get(indices[i])));
                    }
                }

                if (tick == 60 && !firstStopped) {
                    firstStopped = true;
                    finalModels[0] = symbols.get(indices[0]);
                    world.playSound(visual, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.BLOCKS, 0.6f, 1.2f);
                }
                if (tick == 80 && !secondStopped) {
                    secondStopped = true;
                    finalModels[1] = symbols.get(indices[1]);
                    world.playSound(visual, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.BLOCKS, 0.6f, 1.4f);
                }
                if (tick == 100 && !thirdStopped) {
                    thirdStopped = true;
                    finalModels[2] = symbols.get(indices[2]);
                    world.playSound(visual, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.BLOCKS, 0.6f, 1.6f);
                    complete();
                }
            }

            private void complete() {
                cancel();
                finishSpin(player, finalModels, callback);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void pullLever() {
        leverDisplay.setTransformation(leverPulledTransformation);
        if (leverResetTask != null) {
            leverResetTask.cancel();
        }
        leverResetTask = new BukkitRunnable() {
            @Override
            public void run() {
                leverDisplay.setTransformation(leverRestTransformation);
            }
        }.runTaskLater(plugin, 15L);
    }

    private void finishSpin(Player player, int[] finalModels, Consumer<SlotOutcome> callback) {
        spinning = false;
        spinTask = null;
        List<Integer> results = new ArrayList<>();
        for (int model : finalModels) {
            results.add(model);
        }
        int matches = calculateMatches(results);
        callback.accept(new SlotOutcome(results, matches));
    }

    private int calculateMatches(List<Integer> results) {
        if (results.get(0).equals(results.get(1)) && results.get(1).equals(results.get(2))) {
            return 3;
        }
        if (results.get(0).equals(results.get(1)) || results.get(0).equals(results.get(2)) || results.get(1).equals(results.get(2))) {
            return 2;
        }
        return 0;
    }

    public void despawn() {
        if (spinTask != null) {
            spinTask.cancel();
        }
        if (leverResetTask != null) {
            leverResetTask.cancel();
        }
        if (bodyDisplay != null) {
            bodyDisplay.remove();
        }
        if (reelDisplays != null) {
            for (ItemDisplay display : reelDisplays) {
                if (display != null) {
                    display.remove();
                }
            }
        }
        if (leverDisplay != null) {
            leverDisplay.remove();
        }
    }
}
