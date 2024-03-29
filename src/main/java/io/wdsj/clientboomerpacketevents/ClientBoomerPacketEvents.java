package io.wdsj.clientboomerpacketevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerExplosion;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.sqlite.SQLiteDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

import static io.wdsj.clientboomerpacketevents.Utils.getPlayerIp;

public class ClientBoomerPacketEvents extends JavaPlugin implements Listener {
    public static HashSet<Player> BoomedSet = new HashSet<>();
    public static SQLiteDataSource dataSource;
    private Boolean consoleOutPut;
    private Boolean zeroBPM;
    private Boolean banFeature;
    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false)
                .checkForUpdates(false)
                .bStats(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        createConfig();
        loadConfig();
        if (banFeature) {
            dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + this.getDataFolder().getAbsolutePath() + "/data.sqlite");
            try (Connection connection = dataSource.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("CREATE TABLE IF NOT EXISTS boomban (player VARCHAR(16) PRIMARY KEY);");
                    getLogger().info("Database setup finished.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        if (zeroBPM) {
            PacketEvents.getAPI().getEventManager().registerListener(new PacketEventsPacketListener());
            getLogger().info("ZeroBPM is enabled.");
        }
        PacketEvents.getAPI().init();
        Objects.requireNonNull(getCommand("boom")).setExecutor(this);
        Objects.requireNonNull(getCommand("boomban")).setExecutor(this);
        Objects.requireNonNull(getCommand("boomunban")).setExecutor(this);
        Objects.requireNonNull(getCommand("boombanlist")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this,this);
        int pluginId = 20164;
        Metrics metrics = new Metrics(this, pluginId);
        getLogger().info(ChatColor.GREEN+"ClientBoomer is loaded!");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, String label, String[] args) {
        //For /boombanlist command
        if (label.equalsIgnoreCase("boombanlist")) {
            if (banFeature) {
                if (args.length == 0){
                    if (sender.hasPermission("clientboomer.boombanlist.use") || sender instanceof ConsoleCommandSender) {
                        try (Connection connection = dataSource.getConnection()) {
                            try (Statement statement = connection.createStatement();
                                 ResultSet resultSet = statement.executeQuery("SELECT player FROM boomban")) {
                                StringBuilder message = new StringBuilder();
                                while (resultSet.next()) {
                                    message.append(resultSet.getString("player")).append("\n");
                                }
                                String imsg = message.toString().trim();
                                sender.sendMessage("Current banned players:\n" + imsg);
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /boombanlist");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "This feature is disabled.");
            }
        }


        //For /boomban command
        if (label.equalsIgnoreCase("boomban")) {
            if (banFeature) {
                if (args.length == 1) {
                    if (sender.hasPermission("clientboomer.boomban.use") || sender instanceof ConsoleCommandSender) {
                        Player targetPlayer = Bukkit.getPlayer(args[0]);
                        String targetName = args[0];
                        String lowerTargetName = targetName.toLowerCase();
                        if (targetPlayer == null) {
                            sender.sendMessage(ChatColor.RED + "Player not found.");
                            return true;
                        }
                        if (targetPlayer == sender) {
                            sender.sendMessage(ChatColor.RED + "You can't boomban yourself!");
                            return true;
                        }
                        try (Connection connection = dataSource.getConnection()) {
                            try (Statement statement = connection.createStatement()) {
                                statement.executeUpdate("INSERT OR IGNORE INTO boomban (player) VALUES ('" + lowerTargetName + "');");
                            }
                            if (sender instanceof Player || consoleOutPut) {
                                sender.sendMessage(ChatColor.GREEN + "Successfully boombanned " + targetName + "! (IP: " + getPlayerIp(targetPlayer) + ")");
                            }
                            sendExplosionPacket(targetPlayer);
                            BoomedSet.add(targetPlayer);
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        return true;
                    } else {
                        sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /boomban <player>");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "This feature is disabled.");
            }
        }


        //For /boomunban command
        if (label.equalsIgnoreCase("boomunban")) {
            if (banFeature) {
                if (args.length == 1) {
                    if (sender.hasPermission("clientboomer.boomunban.use") || sender instanceof ConsoleCommandSender) {
                        String targetName = args[0];
                        Player targetPlayer = Bukkit.getPlayer(args[0]);
                        String lowerTargetName = targetName.toLowerCase();
                        try (Connection connection = dataSource.getConnection()) {
                            try (Statement statement = connection.createStatement()) {
                                statement.executeUpdate("DELETE FROM boomban WHERE player = '" + lowerTargetName + "';");
                                BoomedSet.remove(targetPlayer);
                                if (sender instanceof Player || consoleOutPut) {
                                    sender.sendMessage(ChatColor.GREEN + "Successfully boomunbanned " + targetName + "!");
                                }
                            } catch (SQLException e) {
                                e.printStackTrace();
                            }
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        return true;
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /boomunban <player>");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "This feature is disabled.");
            }
            return true;
        }

        //For /boom command
        if (label.equalsIgnoreCase("boom")) {
            if (args.length == 1) {
                Player target = Bukkit.getPlayer(args[0]);
                if (target != null) {
                    if (sender instanceof ConsoleCommandSender || sender.hasPermission("clientboomer.use")) {
                        BoomedSet.add(target);
                        sendExplosionPacket(target);
                        if (sender instanceof Player || consoleOutPut) {
                            sender.sendMessage(ChatColor.GREEN + "Sent explosion packet to player " + target.getName());
                        }
                    } else {
                        sender.sendMessage(ChatColor.RED+"You don't have permission to use this command.");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Player not found.");
                }
            } else {
                sender.sendMessage(ChatColor.RED+"Usage: /boom <player>");
            }
        }
        return true;

    }
    public void sendExplosionPacket(Player player) {
        Location location = player.getLocation();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        WrapperPlayServerExplosion explosionPacket = new WrapperPlayServerExplosion(new Vector3d(x, y, z), Float.POSITIVE_INFINITY,
                new ArrayList<>(), new Vector3f(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
         //prevent packet-loss
        Bukkit.getScheduler().runTask(this, () -> {
            for (int i = 0; i < 3; i++) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, explosionPacket);
            }
        });
    }

    private void createConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            saveResource("config.yml", false);
        }
    }

    private void loadConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        consoleOutPut = config.getBoolean("consoleOutput");
        zeroBPM = config.getBoolean("zeroBPM");
        banFeature = config.getBoolean("banFeature");
        getLogger().info("Config loaded.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        BoomedSet.remove(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (banFeature) {
            Player player = event.getPlayer();
            String name = player.getName();
            String lowerName = name.toLowerCase();
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try (Connection connection = dataSource.getConnection()) {
                    try (Statement statement = connection.createStatement();
                         ResultSet rs = statement.executeQuery("SELECT * FROM boomban WHERE player = '" + lowerName + "'")){
                        if (!rs.isClosed() && rs.getString("player").equals(lowerName)) {
                            Bukkit.getScheduler().runTask(this, () -> {
                                sendExplosionPacket(player);
                            });
                            BoomedSet.add(player);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    @Override
    public void onDisable(){
        Objects.requireNonNull(getCommand("boom")).setExecutor(null);
        Objects.requireNonNull(getCommand("boomban")).setExecutor(null);
        Objects.requireNonNull(getCommand("boomunban")).setExecutor(null);
        Objects.requireNonNull(getCommand("boombanlist")).setExecutor(null);
        BoomedSet.clear();
        PacketEvents.getAPI().terminate();
        HandlerList.unregisterAll();
        if (banFeature) {
            try {
                dataSource.getConnection().close();
                getLogger().info("Database connection closed.");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        getLogger().info(ChatColor.RED+"ClientBoomer is unloaded!");
    }
}
