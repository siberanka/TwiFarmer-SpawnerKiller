package xyz.geik.farmer.modules.spawnerkiller;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.modules.FarmerModule;
import xyz.geik.farmer.modules.spawnerkiller.configuration.ConfigFile;
import xyz.geik.farmer.modules.spawnerkiller.handlers.SpawnerKillerEvent;
import xyz.geik.farmer.modules.spawnerkiller.handlers.SpawnerKillerGuiCreateEvent;
import xyz.geik.farmer.modules.spawnerkiller.handlers.SpawnerMetaEvent;
import xyz.geik.farmer.shades.storage.Config;
import xyz.geik.glib.GLib;
import xyz.geik.glib.chat.ChatUtils;
import xyz.geik.glib.shades.okaeri.configs.ConfigManager;
import xyz.geik.glib.shades.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Getter
public class SpawnerKiller extends FarmerModule {

    /**
     * Constructor of class
     */
    public SpawnerKiller() {}

    @Getter
    private static SpawnerKiller instance;

    private static SpawnerKillerEvent spawnerKillerEvent;

    private static SpawnerKillerGuiCreateEvent spawnerKillerGuiCreateEvent;

    private final List<String> whitelist = new ArrayList<>();

    private final List<String> blacklist = new ArrayList<>();

    private boolean requireFarmer = false, cookFoods = false, removeMob = true, defaultStatus = false;

    private String customPerm = "farmer.spawnerkiller";

    private Config langFile;

    private ConfigFile configFile;

    /**
     * onEnable method of module
     */
    public void onEnable() {
        instance = this;
        this.setLang(Main.getConfigFile().getSettings().getLang(), this.getClass());
        this.setHasGui(true);
        setupFile();

        if (configFile.isStatus()) {
            spawnerKillerEvent = new SpawnerKillerEvent();
            spawnerKillerGuiCreateEvent = new SpawnerKillerGuiCreateEvent();
            if (Bukkit.getPluginManager().getPlugin("SpawnerMeta") != null)
                new SpawnerMetaEvent();
            else
                Bukkit.getPluginManager().registerEvents(spawnerKillerEvent, Main.getInstance());
            Bukkit.getPluginManager().registerEvents(spawnerKillerGuiCreateEvent, Main.getInstance());
            customPerm = configFile.getCustomPerm();
            removeMob = configFile.isRemoveMob();
            cookFoods = configFile.isCookFoods();
            requireFarmer = configFile.isRequireFarmer();
            if (configFile.getMode().equals("whitelist"))
                whitelist.addAll(configFile.getWhitelist());
            if (configFile.getMode().equals("blacklist"))
                blacklist.addAll(configFile.getBlacklist());
            setDefaultState(configFile.isDefaultStatus());
            String messagex = "&3[" + GLib.getInstance().getName() + "] &a" + getName() + " enabled.";
            ChatUtils.sendMessage(Bukkit.getConsoleSender(), messagex);
        }
        else {
            String messagex = "&3[" + GLib.getInstance().getName() + "] &c" + getName() + " is not loaded.";
            ChatUtils.sendMessage(Bukkit.getConsoleSender(), messagex);
        }

    }

    public void onReload() {
        if (!this.isEnabled())
            return;
        customPerm = configFile.getCustomPerm();
        removeMob = configFile.isRemoveMob();
        cookFoods = configFile.isCookFoods();
        requireFarmer = configFile.isRequireFarmer();
        customPerm = configFile.getCustomPerm();
        setDefaultState(configFile.isDefaultStatus());
    }

    /**
     * onDisable method of module
     */
    @Override
    public void onDisable() {
        HandlerList.unregisterAll(spawnerKillerEvent);
        HandlerList.unregisterAll(spawnerKillerGuiCreateEvent);
    }

    public void setupFile() {
        configFile = ConfigManager.create(ConfigFile.class, (it) -> {
            it.withConfigurer(new YamlBukkitConfigurer());
            it.withBindFile(new File(Main.getInstance().getDataFolder(), String.format("/modules/%s/config.yml", getName().toLowerCase())));
            it.saveDefaults();
            it.load(true);
        });
    }

}