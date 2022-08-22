package net.william278.huskhomes.listener;

import de.themoep.minedown.MineDown;
import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.player.OnlineUser;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.teleport.TeleportType;
import net.william278.huskhomes.util.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A handler for when events take place
 */
public class EventListener {

    @NotNull
    protected final HuskHomes plugin;

    // Indicates if the first user has joined yet
    private boolean firstUser = true;

    protected EventListener(@NotNull HuskHomes implementor) {
        this.plugin = implementor;
    }

    /**
     * Handle when the first {@link OnlineUser} joins the server
     *
     * @param onlineUser {@link OnlineUser} joining the server
     */
    private void handleFirstPlayerJoin(@NotNull OnlineUser onlineUser) {
        // Cache the server name
        plugin.getServer(onlineUser);
    }

    /**
     * Handle when a {@link OnlineUser} joins the server
     *
     * @param onlineUser the joining {@link OnlineUser}
     */
    protected final void handlePlayerJoin(@NotNull OnlineUser onlineUser) {
        // Handle the first player joining the server
        if (firstUser) {
            handleFirstPlayerJoin(onlineUser);
            firstUser = false;
        }

        // Ensure the player is present on the database first
        plugin.getDatabase().ensureUser(onlineUser).thenRun(() -> {
            // If the server is in proxy mode, check if the player is teleporting cross-server and handle
            if (plugin.getSettings().crossServer) {
                plugin.getDatabase().getCurrentTeleport(onlineUser).thenAccept(teleport -> teleport.ifPresent(activeTeleport -> {
                            // Handle cross-server respawn
                            if (activeTeleport.type == TeleportType.RESPAWN) {
                                final Optional<Position> bedPosition = onlineUser.getBedSpawnPosition().join();
                                if (bedPosition.isEmpty()) {
                                    plugin.getServerSpawn().flatMap(spawn -> spawn.getLocation(plugin.getServer(onlineUser).join()))
                                            .ifPresent(onlineUser::teleport);
                                    onlineUser.sendMinecraftMessage("block.minecraft.spawn.not_valid");
                                } else {
                                    onlineUser.teleport(bedPosition.get()).join();
                                }
                                plugin.getDatabase().setCurrentTeleport(onlineUser, null).thenRun(() ->
                                        plugin.getDatabase().setRespawnPosition(onlineUser, bedPosition.orElse(null))
                                                .join()).join();
                                return;
                            }

                            // Teleport the player locally
                            onlineUser.teleport(activeTeleport.target).thenAccept(teleportResult -> {
                                        if (!teleportResult.successful) {
                                            plugin.getLocales().getLocale("error_invalid_on_arrival")
                                                    .ifPresent(onlineUser::sendMessage);
                                        } else {
                                            plugin.getLocales().getLocale("teleporting_complete")
                                                    .ifPresent(onlineUser::sendMessage);
                                        }
                                    }).thenRun(() -> plugin.getDatabase().setCurrentTeleport(onlineUser, null))
                                    .join();
                        }))
                        .join();
                // Update the player list
                assert plugin.getNetworkMessenger() != null;
                plugin.getCache().updateOnlinePlayerList(plugin, onlineUser);
            }

            // Get this user's tp-ignore state and set locally
            plugin.getDatabase().getUserData(onlineUser.uuid).thenAccept(userData -> {
                if (userData.isPresent()) {
                    final boolean ignoringRequests = userData.get().ignoringTeleports();
                    plugin.getRequestManager().setIgnoringRequests(onlineUser, ignoringRequests);

                    // Send a reminder message if they are still ignoring requests
                    if (ignoringRequests) {
                        plugin.getLocales().getRawLocale("tpignore_on_notification",
                                        plugin.getLocales().getRawLocale("tpignore_toggle_button").orElse(""))
                                .ifPresent(locale -> onlineUser.sendMessage(new MineDown(locale)));
                    }
                }
            }).join();

            // Cache this user's homes
            plugin.getDatabase().getHomes(onlineUser).thenAccept(homes -> plugin.getCache()
                    .homes.put(onlineUser.uuid, homes
                            .stream().map(home -> home.meta.name)
                            .collect(Collectors.toList()))).join();

            // Ensure the server has been set
            if (plugin.getOnlinePlayers().size() == 1) {
                plugin.getServer(onlineUser);
            }
        });
    }

    /**
     * Handle when a {@link OnlineUser} leaves the server
     *
     * @param onlineUser the leaving {@link OnlineUser}
     */
    protected final void handlePlayerLeave(@NotNull OnlineUser onlineUser) {
        // Remove this user's home cache
        plugin.getCache().homes.remove(onlineUser.uuid);

        // Update the player list
        if (plugin.getSettings().crossServer) {
            assert plugin.getNetworkMessenger() != null;
            plugin.getOnlinePlayers().stream().filter(
                            onlinePlayer -> !onlinePlayer.uuid.equals(onlineUser.uuid))
                    .findAny().ifPresent(updater ->
                            plugin.getCache().updateOnlinePlayerList(plugin, updater));
        }

        // Set offline position
        onlineUser.getPosition().thenAcceptAsync(position -> plugin.getDatabase()
                .setOfflinePosition(onlineUser, position));
    }

    /**
     * Handle when a {@link OnlineUser} dies
     *
     * @param onlineUser the {@link OnlineUser} who died
     */
    protected final void handlePlayerDeath(@NotNull OnlineUser onlineUser) {
        // Set the player's last position to where they died
        if (plugin.getSettings().backCommandReturnByDeath
            && onlineUser.hasPermission(Permission.COMMAND_BACK_RETURN_BY_DEATH.node)) {
            onlineUser.getPosition().thenAccept(position -> plugin.getDatabase().setLastPosition(onlineUser, position).join());
        }
    }

    /**
     * Handle when a {@link OnlineUser} respawns after dying
     *
     * @param onlineUser the respawning {@link OnlineUser}
     */
    protected final void handlePlayerRespawn(@NotNull OnlineUser onlineUser) {
        // Display the return by death via /back notification
        if (plugin.getSettings().backCommandReturnByDeath && onlineUser.hasPermission(Permission.COMMAND_BACK_RETURN_BY_DEATH.node)) {
            plugin.getLocales().getLocale("return_by_death_notification")
                    .ifPresent(onlineUser::sendMessage);
        }

        // Respawn the player cross-server if needed
        if (plugin.getSettings().crossServer && plugin.getSettings().globalRespawning) {
            plugin.getDatabase().getRespawnPosition(onlineUser).thenAccept(position -> position.ifPresent(respawnPosition -> {
                if (!respawnPosition.server.equals(plugin.getServer(onlineUser).join())) {
                    plugin.getTeleportManager().teleport(onlineUser, respawnPosition, TeleportType.RESPAWN).thenAccept(
                            result -> plugin.getTeleportManager().finishTeleport(onlineUser, result));
                }
            }));
        }
    }

    /**
     * Handle when an {@link OnlineUser}'s spawn point is updated
     *
     * @param onlineUser the {@link OnlineUser} whose spawn point was updated
     * @param position   the new spawn point
     */
    protected final void handlePlayerUpdateSpawnPoint(@NotNull OnlineUser onlineUser, @NotNull Position position) {
        if (plugin.getSettings().crossServer && plugin.getSettings().globalRespawning) {
            plugin.getDatabase().setRespawnPosition(onlineUser, position).join();
        }
    }

    /**
     * Handle when the plugin is disabling (server is shutting down)
     */
    public final void handlePluginDisable() {
        plugin.getLoggingAdapter().log(Level.INFO, "Successfully disabled HuskHomes v" + plugin.getPluginVersion());
    }

}