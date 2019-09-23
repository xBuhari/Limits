package bentobox.addon.limits.listeners;

import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.PermissionAttachmentInfo;

import bentobox.addon.limits.Limits;
import bentobox.addon.limits.Settings;
import bentobox.addon.limits.commands.LimitPanel;
import bentobox.addon.limits.objects.IslandBlockCount;
import world.bentobox.bentobox.api.events.island.IslandEvent;
import world.bentobox.bentobox.api.events.island.IslandEvent.Reason;
import world.bentobox.bentobox.api.events.team.TeamEvent.TeamSetownerEvent;
import world.bentobox.bentobox.database.objects.Island;

/**
 * Sets block limits based on player permission
 * @author tastybento
 *
 */
public class JoinListener implements Listener {

    private final Limits addon;

    public JoinListener(Limits addon) {
        this.addon = addon;
    }

    private void checkPerms(Player player, String permissionPrefix, String islandId, String gameMode) {
        IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(islandId);
        if (ibc == null) {
            ibc = new IslandBlockCount(islandId, gameMode);
        }
        for (PermissionAttachmentInfo perms : player.getEffectivePermissions()) {
            if (perms.getPermission().startsWith(permissionPrefix)) {
                // No wildcards
                if (perms.getPermission().contains(permissionPrefix + "*")) {
                    logError(player.getName(), perms.getPermission(), "Wildcards are not allowed.");
                    return;
                }
                // Get the Material
                String[] split = perms.getPermission().split("\\.");
                if (split.length != 5) {
                    logError(player.getName(), perms.getPermission(), "format must be '" + permissionPrefix + "MATERIAL/ENTITY-TYPE.NUMBER'");
                    return;
                }
                // Get the value
                if (!NumberUtils.isDigits(split[4])) {
                    logError(player.getName(), perms.getPermission(), "the last part MUST be a number!");
                    return;
                }
                try {
                    int limit = Integer.valueOf(split[4]);
                    String key = split[3].toUpperCase(Locale.ENGLISH);
                    Material m = Material.getMaterial(key);
                    if (m == null) {
                        EntityType type = Settings.getType(key);
                        if (type != null) {
                            if (!type.equals(EntityType.PAINTING) &&
                                    !type.equals(EntityType.ITEM_FRAME) &&
                                    (!type.isSpawnable() || (LimitPanel.E2M.containsKey(type) && LimitPanel.E2M.get(type) == null))) {
                                addon.logError("Entity type: " + key + " is not supported - skipping...");
                            } else {
                                ibc.setEntityLimit(type, Math.max(ibc.getEntityLimit(type), limit));
                                return;
                            }
                        } else {
                            logError(player.getName(), perms.getPermission(), split[3].toUpperCase(Locale.ENGLISH) + " is not a valid material or entity type.");
                            return;
                        }
                    }
                    // Set the limit
                    ibc.setBlockLimit(m, Math.max(ibc.getBlockLimit(m), limit));
                } catch (Exception e) {
                    logError(player.getName(), perms.getPermission(), "the last part MUST be a number!");
                    return; 
                }
            }
        }
        // If any changes have been made then store it
        if (ibc != null) {
            addon.getBlockLimitListener().setIsland(islandId, ibc);
        }

    }


    private void logError(String name, String perm, String error) {
        addon.logError("Player " + name + " has permission: '" + perm + "' but " + error + " Ignoring...");
    }

    /*
     * Event handling
     */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onNewIsland(IslandEvent e) {
        if (!e.getReason().equals(Reason.CREATED)
                && !e.getReason().equals(Reason.RESETTED)
                && !e.getReason().equals(Reason.REGISTERED)) {
            return;
        }
        setOwnerPerms(e.getIsland(), e.getOwner());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerChange(TeamSetownerEvent e) {
        removeOwnerPerms(e.getIsland());
        setOwnerPerms(e.getIsland(), e.getNewOwner());
    }


    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent e) {
        // Check if player has any islands in the game modes
        addon.getGameModes().forEach(gm -> {
            if (addon.getIslands().hasIsland(gm.getOverWorld(), e.getPlayer().getUniqueId())) {
                String islandId = addon.getIslands().getIsland(gm.getOverWorld(), e.getPlayer().getUniqueId()).getUniqueId();
                checkPerms(e.getPlayer(), gm.getPermissionPrefix() + "island.limit.", islandId, gm.getDescription().getName());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUnregisterIsland(IslandEvent e) {
        if (!e.getReason().equals(Reason.UNREGISTERED)) {
            return;
        }
        removeOwnerPerms(e.getIsland());
    }

    /*
     * Utility methods
     */

    private void removeOwnerPerms(Island island) {
        World world = island.getWorld();
        if (addon.inGameModeWorld(world)) {
            IslandBlockCount ibc = addon.getBlockLimitListener().getIsland(island.getUniqueId());
            if (ibc != null) {
                ibc.getBlockLimits().clear();
            }
        }
    }

    private void setOwnerPerms(Island island, UUID ownerUUID) {
        World world = island.getWorld();
        if (addon.inGameModeWorld(world)) {
            // Check if owner is online
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
            if (owner.isOnline()) {
                // Set perm-based limits
                String prefix = addon.getGameModePermPrefix(world);
                String name = addon.getGameModeName(world);
                if (!prefix.isEmpty() && !name.isEmpty()) {
                    checkPerms(owner.getPlayer(), prefix + "island.limit.", island.getUniqueId(), name);
                }
            }
        }
    }

}
