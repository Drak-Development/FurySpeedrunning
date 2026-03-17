package host.plas.furyspeedrunning.commands;

import host.plas.furyspeedrunning.FurySpeedrunning;
import host.plas.furyspeedrunning.config.MainConfig;
import host.plas.furyspeedrunning.world.LobbyManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommands implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (label.toLowerCase()) {
            case "heal":
                return handleHeal(sender, args);
            case "tppos":
                return handleTpPos(sender, args);
            case "tphere":
                return handleTpHere(sender, args);
            case "top":
                return handleTop(sender);
            case "jump":
                return handleJump(sender);
            case "center":
                return handleCenter(sender);
            case "setlobby":
                return handleSetLobby(sender);
            case "lobby":
                return handleLobby(sender);
            default:
                return false;
        }
    }

    private boolean handleHeal(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("\u00A7cPlayer not found.");
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("\u00A7cUsage: /heal <player>");
                return true;
            }
            target = (Player) sender;
        }

        target.setHealth(target.getMaxHealth());
        target.setFoodLevel(20);
        target.setSaturation(20f);
        target.setFireTicks(0);
        target.getActivePotionEffects().forEach(e -> target.removePotionEffect(e.getType()));

        sender.sendMessage("\u00A7aHealed \u00A7e" + target.getName() + "\u00A7a.");
        if (!target.equals(sender)) {
            target.sendMessage("\u00A7aYou have been healed.");
        }
        return true;
    }

    private boolean handleTpPos(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00A7cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length < 3) {
            sender.sendMessage("\u00A7cUsage: /tppos <x> <y> <z> (<yaw> <pitch>)");
            return true;
        }

        try {
            double x = parseCoord(args[0], player.getLocation().getX());
            double y = parseCoord(args[1], player.getLocation().getY());
            double z = parseCoord(args[2], player.getLocation().getZ());
            float yaw = args.length >= 4 ? Float.parseFloat(args[3]) : player.getLocation().getYaw();
            float pitch = args.length >= 5 ? Float.parseFloat(args[4]) : player.getLocation().getPitch();

            Location loc = new Location(player.getWorld(), x, y, z, yaw, pitch);
            player.teleport(loc);
            sender.sendMessage("\u00A7aTeleported to \u00A7e" +
                    String.format("%.1f, %.1f, %.1f", x, y, z));
        } catch (NumberFormatException e) {
            sender.sendMessage("\u00A7cInvalid coordinates.");
        }
        return true;
    }

    private double parseCoord(String input, double current) {
        if (input.startsWith("~")) {
            if (input.length() == 1) return current;
            return current + Double.parseDouble(input.substring(1));
        }
        return Double.parseDouble(input);
    }

    private boolean handleTpHere(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00A7cOnly players can use this command.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("\u00A7cUsage: /tphere <player>");
            return true;
        }

        Player player = (Player) sender;
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("\u00A7cPlayer not found.");
            return true;
        }

        target.teleport(player.getLocation());
        sender.sendMessage("\u00A7aTeleported \u00A7e" + target.getName() + "\u00A7a to you.");
        target.sendMessage("\u00A7aTeleported to \u00A7e" + player.getName() + "\u00A7a.");
        return true;
    }

    private boolean handleTop(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00A7cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        Location loc = player.getLocation();
        int highestY = player.getWorld().getHighestBlockYAt(loc);
        Location topLoc = new Location(player.getWorld(), loc.getX(), highestY + 1, loc.getZ(),
                loc.getYaw(), loc.getPitch());
        player.teleport(topLoc);
        sender.sendMessage("\u00A7aTeleported to top at Y=" + (highestY + 1) + ".");
        return true;
    }

    private boolean handleJump(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00A7cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;

        Block target = player.getTargetBlockExact(256);
        if (target == null) {
            sender.sendMessage("\u00A7cNo block in sight.");
            return true;
        }

        Location loc = target.getLocation().add(0.5, 1, 0.5);
        loc.setYaw(player.getLocation().getYaw());
        loc.setPitch(player.getLocation().getPitch());
        player.teleport(loc);
        sender.sendMessage("\u00A7aJumped to target block.");
        return true;
    }

    private boolean handleCenter(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00A7cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        Location loc = player.getLocation();
        Location centered = new Location(
                loc.getWorld(),
                Math.floor(loc.getX()) + 0.5,
                loc.getY(),
                Math.floor(loc.getZ()) + 0.5,
                loc.getYaw(),
                loc.getPitch()
        );
        player.teleport(centered);
        sender.sendMessage("\u00A7aCentered on block.");
        return true;
    }

    private boolean handleSetLobby(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00A7cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        Location loc = player.getLocation();

        MainConfig config = FurySpeedrunning.getMainConfig();
        config.write("lobby.spawn.x", loc.getX());
        config.write("lobby.spawn.y", loc.getY());
        config.write("lobby.spawn.z", loc.getZ());
        config.write("lobby.spawn.yaw", (double) loc.getYaw());
        config.write("lobby.spawn.pitch", (double) loc.getPitch());
        config.write("lobby.spawn.world", loc.getWorld().getName());

        sender.sendMessage("\u00A7aLobby spawn set to \u00A7e" +
                String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ()) +
                "\u00A7a in world \u00A7e" + loc.getWorld().getName());
        return true;
    }

    private boolean handleLobby(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("\u00A7cOnly players can use this command.");
            return true;
        }
        Player player = (Player) sender;
        LobbyManager.sendToLobby(player);
        LobbyManager.giveLobbyItems(player);
        player.sendMessage("\u00A7aTeleported to the lobby.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String label = alias.toLowerCase();

        if (label.equals("heal") && args.length == 1) {
            return getOnlinePlayerNames(args[0]);
        }
        if (label.equals("tphere") && args.length == 1) {
            return getOnlinePlayerNames(args[0]);
        }
        if (label.equals("tppos")) {
            if (args.length <= 3) return List.of("~");
        }
        return new ArrayList<>();
    }

    private List<String> getOnlinePlayerNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
