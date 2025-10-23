package dev.lixqa.egyptiancasino;

import dev.lixqa.amethystControl.AmethystControl;
import dev.lixqa.amethystControl.MintLedger;
import dev.lixqa.amethystControl.util.MintedCrystalUtil;
import dev.lixqa.egyptiancasino.commands.EgyptianCasinoCommand;
import dev.lixqa.egyptiancasino.exchange.CrystalExchangeListener;
import dev.lixqa.egyptiancasino.exchange.PriestOfRaManager;
import dev.lixqa.egyptiancasino.game.BlackjackGame;
import dev.lixqa.egyptiancasino.game.CasinoGame;
import dev.lixqa.egyptiancasino.game.DiceGame;
import dev.lixqa.egyptiancasino.game.GameRegistry;
import dev.lixqa.egyptiancasino.game.SlotsGame;
import dev.lixqa.egyptiancasino.slotmachine.SlotMachineManager;
import dev.lixqa.egyptiancasino.tokens.TokenManager;
import dev.lixqa.egyptiancasino.tokens.storage.SqliteTokenStorage;
import dev.lixqa.egyptiancasino.tokens.storage.TokenStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class EgyptianCasinoPlugin extends JavaPlugin {

    private AmethystControl amethystControl;
    private MintLedger mintLedger;
    private NamespacedKey mintedCrystalKey;
    private TokenManager tokenManager;
    private PriestOfRaManager priestManager;
    private GameRegistry gameRegistry;
    private CasinoConfig casinoConfig;
    private NumberFormat numberFormat;
    private SlotMachineManager slotMachineManager;
    private NamespacedKey slotMachineItemKey;
    private NamespacedKey slotMachineEntityKey;
    private NamespacedKey slotMachineOwnerKey;

    @Override
    public void onEnable() {
        if (!ensureAmethystControl()) {
            getLogger().severe("AmethystControl must be installed and enabled. EgyptianCasino will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadConfiguration();

        if (!setupTokenManager()) {
            return;
        }
        setupGames();
        priestManager = new PriestOfRaManager(this);
        slotMachineItemKey = new NamespacedKey(this, "slot_machine_item");
        slotMachineEntityKey = new NamespacedKey(this, "slot_machine_id");
        slotMachineOwnerKey = new NamespacedKey(this, "slot_machine_owner");
        slotMachineManager = new SlotMachineManager(this);

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new CrystalExchangeListener(this, priestManager), this);
        pluginManager.registerEvents(slotMachineManager, this);

        EgyptianCasinoCommand command = new EgyptianCasinoCommand(this);
        if (getCommand("egyptiancasino") != null) {
            getCommand("egyptiancasino").setExecutor(command);
            getCommand("egyptiancasino").setTabCompleter(command);
        }

        if (getCommand("giveslot") != null) {
            getCommand("giveslot").setExecutor(slotMachineManager);
            getCommand("giveslot").setTabCompleter(slotMachineManager);
        }
    }

    @Override
    public void onDisable() {
        if (tokenManager != null) {
            tokenManager.shutdown();
        }
        if (slotMachineManager != null) {
            slotMachineManager.shutdown();
        }
    }

    private boolean ensureAmethystControl() {
        if (!Bukkit.getPluginManager().isPluginEnabled("AmethystControl")) {
            return false;
        }

        amethystControl = JavaPlugin.getPlugin(AmethystControl.class);
        mintLedger = amethystControl.getLedger();
        mintedCrystalKey = amethystControl.getMintedCrystalKey();
        return amethystControl != null && mintLedger != null && mintedCrystalKey != null;
    }

    private boolean setupTokenManager() {
        try {
            TokenStorage storage = new SqliteTokenStorage(new File(getDataFolder(), "tokens.db"));
            tokenManager = new TokenManager(storage);
            tokenManager.initialize();
            return true;
        } catch (SQLException exception) {
            getLogger().severe("Failed to initialize the token storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void setupGames() {
        gameRegistry = new GameRegistry();
        registerGame(new SlotsGame());
        registerGame(new BlackjackGame());
        registerGame(new DiceGame());
    }

    private void registerGame(CasinoGame game) {
        gameRegistry.register(game);
    }

    private void loadConfiguration() {
        reloadConfig();
        FileConfiguration config = getConfig();
        casinoConfig = new CasinoConfig(config);
        numberFormat = NumberFormat.getIntegerInstance(Locale.US);
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    public PriestOfRaManager getPriestManager() {
        return priestManager;
    }

    public GameRegistry getGameRegistry() {
        return gameRegistry;
    }

    public CasinoConfig getCasinoConfig() {
        return casinoConfig;
    }

    public MintLedger getMintLedger() {
        return mintLedger;
    }

    public NamespacedKey getMintedCrystalKey() {
        return mintedCrystalKey;
    }

    public NamespacedKey getSlotMachineItemKey() {
        return slotMachineItemKey;
    }

    public NamespacedKey getSlotMachineEntityKey() {
        return slotMachineEntityKey;
    }

    public NamespacedKey getSlotMachineOwnerKey() {
        return slotMachineOwnerKey;
    }

    public SlotMachineManager getSlotMachineManager() {
        return slotMachineManager;
    }

    public Component formatTokens(long amount) {
        return Component.text(numberFormat.format(amount) + " Egyptian Tokens").color(NamedTextColor.GOLD);
    }

    public long convertCrystals(Player player) {
        Inventory inventory = player.getInventory();
        long totalCrystals = 0;

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getAmount() <= 0) {
                continue;
            }

            Optional<UUID> ledgerId = MintedCrystalUtil.readLedgerId(stack, mintedCrystalKey);
            if (ledgerId.isEmpty()) {
                continue;
            }

            UUID crystalId = ledgerId.get();
            boolean redeemed = mintLedger.markRedeemed(crystalId);
            if (!redeemed) {
                continue;
            }

            totalCrystals += stack.getAmount();
            inventory.clear(slot);
        }

        long tokensPerCrystal = casinoConfig.tokensPerCrystal();
        long tokensAwarded = totalCrystals * tokensPerCrystal;
        if (tokensAwarded > 0) {
            tokenManager.deposit(player.getUniqueId(), tokensAwarded);
        }

        return tokensAwarded;
    }

    public void sendMessage(Player player, Component message) {
        player.sendMessage(Component.text("[EgyptianCasino] ", NamedTextColor.GOLD).append(message));
    }

    public ItemStack createEgyptianTokenItem(int amount) {
        ItemStack itemStack = new ItemStack(org.bukkit.Material.GOLD_NUGGET, Math.max(1, amount));
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(Component.text("Egyptian Token", NamedTextColor.GOLD));
        meta.setCustomModelData(1010);
        itemStack.setItemMeta(meta);
        return itemStack;
    }
}
