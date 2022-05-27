package net.william278.huskhomes.teleport;

import net.william278.huskhomes.position.Position;
import net.william278.huskhomes.position.World;

/**
 * Represents the result of a completed {@link Teleport}
 */
public enum TeleportResult {

    /**
     * Returns if the {@link Teleport} was completed locally, successfully
     */
    COMPLETED_LOCALLY(true),

    /**
     * Returns if the {@link Teleport} was completed cross-server
     */
    COMPLETED_CROSS_SERVER(true),

    /**
     * Returns if the {@link Teleport} failed because the {@link World} of the
     * target {@link Position} could not be found
     * <p>
     */
    FAILED_INVALID_WORLD(false),

    /**
     * Returns if the {@link Teleport} failed because the server of the target
     * {@link Position} was invalid or not online
     * <p>
     */
    FAILED_INVALID_SERVER(false),

    /**
     * Returns if the {@link Teleport} failed because the coordinates of the
     * target {@link Position} were outside the world border limits
     * <p>
     */
    FAILED_ILLEGAL_COORDINATES(false),

    /**
     * Returns if the {@link Teleport} failed because the coordinates of the
     * target {@link Position} were in an unsafe spot
     * <p>
     */
    FAILED_UNSAFE(false);

    /**
     * Is {@code true} if the teleport was a success
     */
    public final boolean successful;

    TeleportResult(boolean successful) {
        this.successful = successful;
    }

}