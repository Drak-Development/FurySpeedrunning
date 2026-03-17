package host.plas.furyspeedrunning;

import host.plas.bou.BetterPlugin;
import host.plas.furyspeedrunning.commands.AdminCommands;
import host.plas.furyspeedrunning.commands.ManageGameCommand;
import host.plas.furyspeedrunning.commands.PlayAsCommand;
import host.plas.furyspeedrunning.config.MainConfig;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.events.*;
import host.plas.furyspeedrunning.world.LobbyManager;
import host.plas.furyspeedrunning.world.WorldManager;
import host.plas.furyspeedrunning.world.WorldTemplateManager;
import lombok.Getter;
import lombok.Setter;
import mc.obliviate.inventory.InventoryAPI;
import org.bukkit.command.PluginCommand;

@Getter @Setter
public final class FurySpeedrunning extends BetterPlugin {
    @Getter @Setter
    private static FurySpeedrunning instance;
    @Getter @Setter
    private static MainConfig mainConfig;

    @Getter @Setter
    private static MainListener mainListener;
    @Getter @Setter
    private static ItemProtectionListener itemProtectionListener;
    @Getter @Setter
    private static SharedInventoryListener sharedInventoryListener;
    @Getter @Setter
    private static SharedHealthListener sharedHealthListener;
    @Getter @Setter
    private static SpectatorListener spectatorListener;
    @Getter @Setter
    private static PortalListener portalListener;
    @Getter @Setter
    private static DragonListener dragonListener;

    public FurySpeedrunning() {
        super();
    }

    @Override
    public void onBaseEnabled() {
        setInstance(this);

        // Initialize ObliviateInvs GUI API
        new InventoryAPI(this).init();

        // Config
        setMainConfig(new MainConfig());

        // Clean up ALL stale worlds from previous runs / crashes
        WorldManager.cleanupStaleWorlds();
        WorldTemplateManager.cleanupInterruptedGenerations();

        // Create lobby world
        LobbyManager.createLobbyWorld();

        // Register listeners
        setMainListener(new MainListener());
        setItemProtectionListener(new ItemProtectionListener());
        setSharedInventoryListener(new SharedInventoryListener());
        setSharedHealthListener(new SharedHealthListener());
        setSpectatorListener(new SpectatorListener());
        setPortalListener(new PortalListener());
        setDragonListener(new DragonListener());

        // Register commands
        registerCommand("managegame", new ManageGameCommand());
        registerCommand("playas", new PlayAsCommand());

        AdminCommands adminCommands = new AdminCommands();
        for (String cmd : new String[]{"heal", "tppos", "tphere", "top", "jump", "center", "setlobby", "lobby"}) {
            registerCommand(cmd, adminCommands);
        }

        logInfo("&aFurySpeedrunning enabled!");

        // Start pre-generating templates for all configured seeds
        // Runs in the background across ticks — does not block startup
        WorldTemplateManager.generateMissingTemplates(null);
    }

    @Override
    public void onBaseDisable() {
        // Cancel any active Chunky template generation
        WorldTemplateManager.cancelActiveGeneration();

        // Stop any running game and clean up worlds
        if (GameManager.getState() == GameState.PLAYING) {
            GameManager.stopGame();
        }

        logInfo("&cFurySpeedrunning disabled.");
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor((org.bukkit.command.CommandExecutor) executor);
            if (executor instanceof org.bukkit.command.TabCompleter) {
                cmd.setTabCompleter((org.bukkit.command.TabCompleter) executor);
            }
        } else {
            logWarning("Command '" + name + "' not found in plugin.yml!");
        }
    }
}
