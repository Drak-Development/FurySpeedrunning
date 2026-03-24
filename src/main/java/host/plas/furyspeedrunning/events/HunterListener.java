package host.plas.furyspeedrunning.events;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.data.GameManager;
import host.plas.furyspeedrunning.data.PlayerData;
import host.plas.furyspeedrunning.data.PlayerManager;
import host.plas.furyspeedrunning.enums.GameState;
import host.plas.furyspeedrunning.enums.PlayerRole;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class HunterListener extends AbstractConglomerate {
    private BukkitTask trackingTask;
    private static BossBar trackerBar;

    private static final double MAX_TRACK_DISTANCE = 500.0;

    // Direction arrows: N, NE, E, SE, S, SW, W, NW
    private static final String[] ARROWS = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};

    public HunterListener() {
        super();
        startTracking();
    }

    private void startTracking() {
        trackingTask = Bukkit.getScheduler().runTaskTimer(FurySpeedrunning.getInstance(), () -> {
            if (GameManager.getState() != GameState.PLAYING) {
                removeBossBar();
                return;
            }

            for (PlayerData data : PlayerManager.getOnlinePlayers()) {
                if (data.getRole() != PlayerRole.HUNTER) continue;
                Player hunter = data.getPlayer();
                if (hunter == null) continue;

                Player nearest = findNearestSpeedrunner(hunter);
                if (nearest == null) {
                    updateBossBar(hunter, "\u00A7cNo players found", 0.0);
                    continue;
                }

                boolean sameWorld = hunter.getWorld().equals(nearest.getWorld());
                if (!sameWorld) {
                    updateBossBar(hunter, "\u00A7cNearest player in another dimension", 0.5);
                    continue;
                }

                double distance = hunter.getLocation().distance(nearest.getLocation());
                String arrow = getDirectionArrow(hunter, nearest);
                int distInt = (int) Math.round(distance);

                String title = "\u00A7cNearest Player: \u00A7f" + distInt + " blocks \u00A7e" + arrow;
                double progress = Math.max(0.0, Math.min(1.0, 1.0 - (distance / MAX_TRACK_DISTANCE)));
                updateBossBar(hunter, title, progress);
            }
        }, 20L, 5L); // Every 5 ticks (250ms) for smooth updates
    }

    private void updateBossBar(Player hunter, String title, double progress) {
        if (trackerBar == null) {
            trackerBar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID);
            trackerBar.setVisible(true);
        }

        trackerBar.setTitle(title);
        trackerBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

        if (!trackerBar.getPlayers().contains(hunter)) {
            trackerBar.addPlayer(hunter);
        }
    }

    public static void removeBossBar() {
        if (trackerBar != null) {
            trackerBar.removeAll();
            trackerBar.setVisible(false);
            trackerBar = null;
        }
    }

    private String getDirectionArrow(Player hunter, Player target) {
        Location hunterLoc = hunter.getLocation();
        Location targetLoc = target.getLocation();

        double dx = targetLoc.getX() - hunterLoc.getX();
        double dz = targetLoc.getZ() - hunterLoc.getZ();

        // Angle from hunter to target in degrees, matching Minecraft yaw convention
        // (0 = south, 90 = west, 180 = north, 270 = east)
        // Negate dx because Minecraft yaw increases clockwise (south→west) but atan2 increases counter-clockwise
        double angleToTarget = Math.toDegrees(Math.atan2(-dx, dz));

        // Hunter's yaw (0 = south, clockwise)
        double hunterYaw = hunterLoc.getYaw() % 360;
        if (hunterYaw < 0) hunterYaw += 360;

        // Relative angle: how far the target is from where the hunter is looking
        double relative = angleToTarget - hunterYaw;
        while (relative < 0) relative += 360;
        while (relative >= 360) relative -= 360;

        // Map to 8 directions (each 45 degrees)
        // 0 = ahead (up arrow), 90 = right, 180 = behind, 270 = left
        int index = (int) Math.round(relative / 45.0) % 8;
        return ARROWS[index];
    }

    private Player findNearestSpeedrunner(Player hunter) {
        List<Player> speedrunners = PlayerManager.getOnlineBukkitPlayersByRole(PlayerRole.PLAYER);
        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Player runner : speedrunners) {
            if (!runner.getWorld().equals(hunter.getWorld())) continue;
            double dist = runner.getLocation().distanceSquared(hunter.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = runner;
            }
        }

        // If no one in same world, return any online speedrunner
        if (nearest == null && !speedrunners.isEmpty()) {
            nearest = speedrunners.get(0);
        }

        return nearest;
    }
}
