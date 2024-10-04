package io.starseed.nerfElytra;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.UUID;

public class ElytraNerfPlugin extends JavaPlugin implements Listener {

    private PlayerManager playerManager;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();

        playerManager = new PlayerManager(this);

        getServer().getPluginManager().registerEvents(this, this);

        // Register command executor
        getCommand("elytranerf").setExecutor((CommandExecutor) new ElytraNerfCommand(this));

        getLogger().info("ElytraNerfPlugin has been enabled!");
    }

    @Override
    public void onDisable() {
        playerManager.saveAllPlayerData();
        getLogger().info("ElytraNerfPlugin has been disabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerManager.loadPlayerData(event.getPlayer());
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.isGliding() && !playerManager.hasReachedEnd(player)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "You cannot use Elytra until you reach the End!");
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.isGliding() && !playerManager.hasReachedEnd(player)) {
            double maxDistance = config.getDouble("max-flight-distance", 100);
            if (event.getFrom().distance(player.getLocation()) > maxDistance) {
                player.setGliding(false);
                player.sendMessage(ChatColor.RED + "You've reached the maximum flight distance!");
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!playerManager.hasReachedEnd(player) && event.getItem() != null && event.getItem().getType() == Material.FIREWORK_ROCKET) {
            if (player.isGliding()) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot use rockets with Elytra until you reach the End!");
            }
        }
    }

    @EventHandler
    public void onShulkerTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Shulker && event.getTarget() instanceof Player) {
            Player player = (Player) event.getTarget();
            Shulker shulker = (Shulker) event.getEntity();

            if (!playerManager.hasReachedEnd(player)) {
                double aggressiveness = config.getDouble("shulker-aggressiveness", 1.5);
                shulker.setTarget(player);

                // Increase Shulker movement speed
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!shulker.isDead() && shulker.getTarget() == player) {
                            Location playerLoc = player.getLocation();
                            Location shulkerLoc = shulker.getLocation();
                            Vector direction = playerLoc.toVector().subtract(shulkerLoc.toVector()).normalize();
                            shulker.teleport(shulkerLoc.add(direction.multiply(aggressiveness)));
                        } else {
                            this.cancel();
                        }
                    }
                }.runTaskTimer(this, 0L, 20L);
            } else {
                // Make Shulker more tame
                if (Math.random() < 0.5) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getEnvironment() == World.Environment.THE_END && !playerManager.hasReachedEnd(player)) {
            playerManager.setReachedEnd(player, true);
            player.sendMessage(ChatColor.GREEN + "You've reached the End! Full Elytra functionality unlocked!");
        }
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }
}

class PlayerManager {
    private final HashMap<UUID, Boolean> playerEndStates;
    private final ElytraNerfPlugin plugin;

    public PlayerManager(ElytraNerfPlugin plugin) {
        this.plugin = plugin;
        this.playerEndStates = new HashMap<>();
        loadAllPlayerData();
    }

    public boolean hasReachedEnd(Player player) {
        return playerEndStates.getOrDefault(player.getUniqueId(), false);
    }

    public void setReachedEnd(Player player, boolean reached) {
        playerEndStates.put(player.getUniqueId(), reached);
        savePlayerData(player);
    }

    public void loadPlayerData(Player player) {
        boolean reachedEnd = plugin.getConfig().getBoolean("players." + player.getUniqueId() + ".reachedEnd", false);
        playerEndStates.put(player.getUniqueId(), reachedEnd);
    }

    public void savePlayerData(Player player) {
        plugin.getConfig().set("players." + player.getUniqueId() + ".reachedEnd", hasReachedEnd(player));
        plugin.saveConfig();
    }

    public void loadAllPlayerData() {
        if (plugin.getConfig().getConfigurationSection("players") != null) {
            for (String uuidString : plugin.getConfig().getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                boolean reachedEnd = plugin.getConfig().getBoolean("players." + uuidString + ".reachedEnd", false);
                playerEndStates.put(uuid, reachedEnd);
            }
        }
    }

    public void saveAllPlayerData() {
        for (UUID uuid : playerEndStates.keySet()) {
            plugin.getConfig().set("players." + uuid + ".reachedEnd", playerEndStates.get(uuid));
        }
        plugin.saveConfig();
    }
}

class ElytraNerfCommand implements CommandExecutor {
    private final ElytraNerfPlugin plugin;

    public ElytraNerfCommand(ElytraNerfPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("elytranerfplugin.admin")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /elytranerf <player> <true|false>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }

        boolean reachedEnd = Boolean.parseBoolean(args[1]);
        plugin.getPlayerManager().setReachedEnd(target, reachedEnd);

        player.sendMessage(ChatColor.GREEN + "Set " + target.getName() + "'s End state to: " + reachedEnd);
        return true;
    }

}