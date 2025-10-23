package dev.lixqa.egyptiancasino.integration;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class AmethystControlHook {

    private final Logger logger;

    private JavaPlugin amethystPlugin;
    private NamespacedKey mintedCrystalKey;
    private Object mintLedger;
    private Method markRedeemedMethod;
    private Method readLedgerIdMethod;

    public AmethystControlHook(Logger logger) {
        this.logger = logger;
    }

    public boolean initialize() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("AmethystControl");
        if (!(plugin instanceof JavaPlugin javaPlugin)) {
            logger.severe("AmethystControl plugin not found or not a JavaPlugin instance.");
            return false;
        }

        try {
            Class<?> amethystClass = Class.forName("dev.lixqa.amethystControl.AmethystControl");
            Class<?> mintLedgerClass = Class.forName("dev.lixqa.amethystControl.MintLedger");
            Class<?> crystalUtilClass = Class.forName("dev.lixqa.amethystControl.util.MintedCrystalUtil");

            Method getMintedCrystalKey = amethystClass.getMethod("getMintedCrystalKey");
            Method getLedger = amethystClass.getMethod("getLedger");
            markRedeemedMethod = mintLedgerClass.getMethod("markRedeemed", UUID.class);
            readLedgerIdMethod = crystalUtilClass.getMethod("readLedgerId", ItemStack.class, NamespacedKey.class);

            amethystPlugin = javaPlugin;
            mintedCrystalKey = (NamespacedKey) getMintedCrystalKey.invoke(amethystPlugin);
            mintLedger = getLedger.invoke(amethystPlugin);

            if (mintedCrystalKey == null || mintLedger == null) {
                logger.severe("Failed to obtain AmethystControl ledger references.");
                return false;
            }
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException exception) {
            logger.severe("AmethystControl API is missing expected classes/methods: " + exception.getMessage());
        } catch (IllegalAccessException | InvocationTargetException exception) {
            logger.severe("Unable to query AmethystControl API: " + exception.getMessage());
        }
        return false;
    }

    public NamespacedKey getMintedCrystalKey() {
        return mintedCrystalKey;
    }

    public Optional<UUID> readLedgerId(ItemStack stack) {
        if (stack == null || readLedgerIdMethod == null || mintedCrystalKey == null) {
            return Optional.empty();
        }
        try {
            Object result = readLedgerIdMethod.invoke(null, stack, mintedCrystalKey);
            if (result instanceof Optional<?> optional) {
                Object value = optional.orElse(null);
                if (value instanceof UUID uuid) {
                    return Optional.of(uuid);
                }
            }
        } catch (IllegalAccessException | InvocationTargetException exception) {
            logger.warning("Failed to read minted crystal metadata: " + exception.getMessage());
        }
        return Optional.empty();
    }

    public boolean markRedeemed(UUID ledgerId) {
        if (ledgerId == null || markRedeemedMethod == null || mintLedger == null) {
            return false;
        }
        try {
            Object result = markRedeemedMethod.invoke(mintLedger, ledgerId);
            return result instanceof Boolean && (Boolean) result;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            logger.warning("Failed to mark minted crystal as redeemed: " + exception.getMessage());
            return false;
        }
    }
}
