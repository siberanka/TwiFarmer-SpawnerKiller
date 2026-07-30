package xyz.geik.farmer.modules.spawnerkiller.handlers;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import xyz.geik.farmer.api.handlers.FarmerModuleGuiCreateEvent;
import xyz.geik.farmer.Main;
import xyz.geik.farmer.helpers.gui.GuiHelper;
import xyz.geik.farmer.model.Farmer;
import xyz.geik.farmer.model.FarmerLevel;
import xyz.geik.farmer.modules.spawnerkiller.SpawnerKiller;
import xyz.geik.glib.chat.ChatUtils;
import xyz.geik.glib.chat.Placeholder;
import xyz.geik.glib.shades.inventorygui.DynamicGuiElement;
import xyz.geik.glib.shades.inventorygui.StaticGuiElement;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gui create event for spawner killer module
 * @author Geik
 * @author siberanka
 */
public class SpawnerKillerGuiCreateEvent implements Listener {

    private static final long CLICK_COOLDOWN_MS = 150L;
    private final Map<UUID, Long> lastClicks = new ConcurrentHashMap<>();
    private final Set<UUID> openMenus = ConcurrentHashMap.newKeySet();

    /**
     * Constructor of class
     */
    public SpawnerKillerGuiCreateEvent() {}

    /**
     * Creates the GUI element for the farmer GUI for the module
     *
     * @param e of farmer module gui create event
     */
    @EventHandler
    public void onGuiCreateEvent(@NotNull FarmerModuleGuiCreateEvent e) {
        SpawnerKiller module = SpawnerKiller.getInstance();
        if (module == null || !module.isOperational()) {
            return;
        }
        openMenus.add(e.getPlayer().getUniqueId());
        String configuredIcon = module.getLang().getString("moduleGui.icon.guiInterface");
        char icon = configuredIcon == null || configuredIcon.isEmpty() ? 'k' : configuredIcon.charAt(0);
        e.getGui().addElement(
                new DynamicGuiElement(icon, (viewer) ->
                    new StaticGuiElement(
                        icon,
                        // Item here
                        getGuiItem(e.getFarmer()),
                        1,
                        // Event written bottom
                        click -> {
                            if (!module.isOperational() || module != SpawnerKiller.getInstance()) {
                                e.getPlayer().closeInventory();
                                return true;
                            }
                            long now = System.currentTimeMillis();
                            Long previous = lastClicks.put(e.getPlayer().getUniqueId(), now);
                            if (previous != null && now - previous < CLICK_COOLDOWN_MS)
                                return true;
                            if (!module.isAvailableFor(e.getFarmer())) {
                                sendLevelRequired(e.getFarmer(), e.getPlayer(), module);
                                return true;
                            }
                            // If player don't have permission do nothing
                            if (!e.getPlayer().hasPermission(module.getCustomPerm()))
                                return true;
                            // Change attribute
                            synchronized (e.getFarmer()) {
                                e.getFarmer().changeAttribute("spawnerkiller");
                            }
                            e.getGui().draw();
                            return true;
                    })
                )
        );
    }

    /**
     * Gets item of gui
     *
     * @param farmer of gui
     * @return ItemStack of gui item
     */
    private @NotNull ItemStack getGuiItem(@NotNull Farmer farmer) {
        SpawnerKiller module = SpawnerKiller.getInstance();
        ItemStack item = GuiHelper.getItem("moduleGui.icon", module.getLang());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        int currentLevel = FarmerLevel.getLevelNumber(farmer.getLevel());
        boolean available = module.isAvailableFor(farmer);
        String status = available
                ? (farmer.getAttributeStatus("spawnerkiller")
                    ? module.getLang().getString("enabled")
                    : module.getLang().getString("disabled"))
                : module.getLang().getString("locked");
        String action = available
                ? module.getLang().getString("moduleGui.click-to-toggle")
                : ChatUtils.replacePlaceholders(
                        module.getLang().getString("moduleGui.upgrade-to-unlock"),
                        new Placeholder("{required_level}", String.valueOf(module.getRequiredFarmerLevel())));
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = Collections.emptyList();
        }
        meta.setLore(lore.stream().map(line -> ChatUtils.color(ChatUtils.replacePlaceholders(
                        line,
                        new Placeholder("{status}", status),
                        new Placeholder("{required_level}", String.valueOf(module.getRequiredFarmerLevel())),
                        new Placeholder("{current_level}", String.valueOf(currentLevel)),
                        new Placeholder("{action}", action))))
                .collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private void sendLevelRequired(
            @NotNull Farmer farmer,
            org.bukkit.entity.Player player,
            @NotNull SpawnerKiller module
    ) {
        ChatUtils.sendMessage(player, ChatUtils.replacePlaceholders(
                module.getLang().getString("level-required"),
                new Placeholder("{required_level}", String.valueOf(module.getRequiredFarmerLevel())),
                new Placeholder("{current_level}", String.valueOf(FarmerLevel.getLevelNumber(farmer.getLevel())))));
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        lastClicks.remove(event.getPlayer().getUniqueId());
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClose(@NotNull InventoryCloseEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }

    public void closeOpenMenus() {
        for (UUID playerId : openMenus) {
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.getScheduler().execute(Main.getInstance(), player::closeInventory, null, 0L);
            }
        }
        openMenus.clear();
        lastClicks.clear();
    }
}
