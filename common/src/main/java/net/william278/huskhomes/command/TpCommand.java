package net.william278.huskhomes.command;

import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.HuskHomesException;
import net.william278.huskhomes.player.OnlineUser;
import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.util.Permission;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class TpCommand extends CommandBase implements TabCompletable, ConsoleExecutable {

    protected TpCommand(@NotNull HuskHomes implementor) {
        super("tp", Permission.COMMAND_TP, implementor, "tpo");
    }

    @Override
    public void onExecute(@NotNull OnlineUser onlineUser, @NotNull String[] args) {
        String teleportingUserName = onlineUser.username;
        TeleportCommandTarget targetPosition;

        // Validate argument length
        if (args.length > 6 || args.length < 1) {
            plugin.getLocales().getLocale("error_invalid_syntax", "/tp <target> [destination]")
                    .ifPresent(onlineUser::sendMessage);
            return;
        }

        // If the first argument is a player target
        if (args.length == 1) {
            targetPosition = new TeleportCommandTarget(args[0]);
        } else {
            // Determine if a player argument has been passed before the destination argument
            int coordinatesIndex = ((args.length > 3 && (isCoordinate(args[1]) && isCoordinate(args[2]) && isCoordinate(args[3])))
                                    || (args.length == 2)) ? 1 : 0;
            if (coordinatesIndex == 1) {
                teleportingUserName = args[0];
            }

            // Determine the target position (player or coordinates)
            if (args.length == 2) {
                targetPosition = new TeleportCommandTarget(args[1]);
            } else {
                // Coordinate teleportation requires a permission node
                if (!onlineUser.hasPermission(Permission.COMMAND_TP_TO_COORDINATES.node)) {
                    plugin.getLocales().getLocale("error_no_permission").ifPresent(onlineUser::sendMessage);
                    return;
                }

                // Parse the coordinates and set the target position
                final Position userPosition = onlineUser.getPosition().join();
                final Optional<Position> parsedPosition = Position.parse(userPosition,
                        Arrays.copyOfRange(args, coordinatesIndex, args.length));
                if (parsedPosition.isEmpty()) {
                    plugin.getLocales().getLocale("error_invalid_syntax", "/tp <target> <x> <y> <z> [world] [server]")
                            .ifPresent(onlineUser::sendMessage);
                    return;
                }
                targetPosition = new TeleportCommandTarget(parsedPosition.get());
            }
        }

        // Execute the teleport
        final String playerToTeleportName = teleportingUserName;
        if (targetPosition.targetType == TeleportCommandTarget.TargetType.PLAYER) {
            // Teleport players by usernames
            assert targetPosition.targetPlayer != null;
            CompletableFuture.runAsync(() -> plugin.getTeleportManager()
                    .teleportPlayerToPlayerByName(playerToTeleportName, targetPosition.targetPlayer, onlineUser)
                    .thenAccept(resultIfPlayerExists -> resultIfPlayerExists.ifPresentOrElse(
                            result -> plugin.getTeleportManager().finishTeleport(onlineUser, result),
                            () -> plugin.getLocales().getLocale("error_player_not_found", playerToTeleportName)
                                    .ifPresent(onlineUser::sendMessage))));
            return;
        } else if (targetPosition.targetType == TeleportCommandTarget.TargetType.POSITION) {
            // Teleport players by specified position
            assert targetPosition.targetPosition != null;
            plugin.getTeleportManager().teleportPlayerByName(playerToTeleportName, targetPosition.targetPosition, onlineUser)
                    .thenAccept(resultIfPlayerExists -> resultIfPlayerExists.ifPresentOrElse(
                            result -> plugin.getTeleportManager().finishTeleport(onlineUser, result),
                            () -> plugin.getLocales().getLocale("error_player_not_found", playerToTeleportName)
                                    .ifPresent(onlineUser::sendMessage)));
            return;
        }
        throw new HuskHomesException("Attempted to execute invalid teleport command operation");
    }

    private boolean isCoordinate(@NotNull String coordinate) {
        try {
            if (coordinate.startsWith("~")) {
                coordinate = coordinate.substring(1);
            }
            if (coordinate.isBlank()) {
                return true;
            }
            Double.parseDouble(coordinate);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void onConsoleExecute(@NotNull String[] args) {
        //todo
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull OnlineUser onlineUser, @NotNull String[] args) {
        return Collections.emptyList();
    }

    /**
     * Identifies the target of a teleport command operation
     */
    private static class TeleportCommandTarget {

        @NotNull
        private final TargetType targetType;
        @Nullable
        private String targetPlayer;
        @Nullable
        private Position targetPosition;

        public TeleportCommandTarget(@NotNull String targetPlayer) {
            this.targetPlayer = targetPlayer;
            this.targetType = TargetType.PLAYER;
        }

        public TeleportCommandTarget(@NotNull Position targetPosition) {
            this.targetPosition = targetPosition;
            this.targetType = TargetType.POSITION;
        }

        public enum TargetType {
            PLAYER,
            POSITION
        }
    }
}