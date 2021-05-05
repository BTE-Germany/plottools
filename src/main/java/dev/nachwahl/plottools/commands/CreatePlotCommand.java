package dev.nachwahl.plottools.commands;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.sk89q.worldedit.BlockVector2D;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitUtil;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import dev.arantes.inventorymenulib.PaginatedGUIBuilder;
import dev.arantes.inventorymenulib.buttons.ItemButton;
import dev.arantes.inventorymenulib.menus.PaginatedGUI;
import dev.nachwahl.plottools.PlotTools;
import dev.nachwahl.plottools.utils.FileBuilder;
import dev.nachwahl.plottools.utils.MySQL;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class CreatePlotCommand implements CommandExecutor {
    private PlotTools plugin;

    public CreatePlotCommand(PlotTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            System.out.println("This command can only be executed by a player.");
            return false;
        }
        Player p = (Player) sender;
        if (!p.hasPermission("plottools.createplot")) {
            p.sendMessage("§7§l>> §cYou have no permission to execute this command.");
            return false;
        }

        List<ItemButton> cities = new ArrayList<ItemButton>();

        ResultSet citiesRS = null;
        try {
            citiesRS = MySQL.getCities();
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        while (true) {
            try {
                if (!citiesRS.next()) break;
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            try {
                Integer city = citiesRS.getInt("idcityProject");
                cities.add(new ItemButton(Material.GOLD_BLOCK, "§7» " + citiesRS.getString("name"), " ", "§aClick to use this city", " ").setDefaultAction((InventoryClickEvent e) -> {
                    e.setCancelled(true);
                    Bukkit.getScheduler().runTaskAsynchronously(PlotTools.getInstance(), new Runnable() {
                        @Override
                        public void run() {
                            createPlot((Player) e.getWhoClicked(), city);
                        }
                    });
                    p.sendMessage("§7§l>> §aOne moment please...");

                    e.getWhoClicked().closeInventory();

                }));
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }

        PaginatedGUI gui = new PaginatedGUIBuilder(
                "§6§lSelect city",
                "xxxxxxxxx" +
                        "x#######x" +
                        "<#######>" +
                        "x#######x" +
                        "xxxxxxxxx"
        )


                .setBorder(new ItemButton(Material.STAINED_GLASS_PANE, 1, "§r", "").setDefaultAction((InventoryClickEvent e) -> {
                    e.setCancelled(true);

                }))

                .setHotbarButton(
                        (byte) 4,
                        new ItemButton(Material.COMPASS, 1, "§c§lClose")
                                .addAction(ClickType.LEFT, (InventoryClickEvent e) ->
                                        e.getWhoClicked().closeInventory())
                )


                .setNextPageItem(Material.ARROW, 1, "§6Next page")

                .setPreviousPageItem(Material.ARROW, 1, "§6Previous page")


                .setContent(cities)

                // Build and return the PaginatedGUI
                .build();

        // Show the first page to a Player.
        gui.show(p);


        return true;
    }

    private int plotID = 1;

    public void createPlot(Player player, int cityID) {
        FileBuilder fb = new FileBuilder("plugins/PlotTools", "config.yml");
        Region plotRegion;

        // Get WorldEdit selection of player
        try {
            plotRegion = WorldEdit.getInstance().getSessionManager().findByName(player.getName()).getSelection(WorldEdit.getInstance().getSessionManager().findByName(player.getName()).getSelectionWorld());
        } catch (NullPointerException | IncompleteRegionException ex) {
            ex.printStackTrace();
            player.sendMessage("§7§l>> §cPlease select a WorldEdit selection!");
            return;
        }

        Polygonal2DRegion polyRegion;
        try {
            // Check if WorldEdit selection is polygonal
            if (plotRegion instanceof Polygonal2DRegion) {
                // Cast WorldEdit region to polygonal region
                polyRegion = (Polygonal2DRegion) plotRegion;

                if (polyRegion.getLength() > 100 || polyRegion.getWidth() > 100 || polyRegion.getHeight() > 30) {
                    player.sendMessage("§7§l>> §cPlease adjust your selection size!");
                    return;
                }

                // Set minimum selection height under player location
                polyRegion.setMinimumY((int) player.getLocation().getY() - 5);

                if (polyRegion.getMaximumY() <= player.getLocation().getY() + 1) {
                    polyRegion.setMaximumY((int) player.getLocation().getY() + 1);
                }
            } else {
                player.sendMessage("§7§l>> §cPlease use poly selection to create a new plot!");
                return;
            }
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "An error occurred while creating a new plot!", ex);
            player.sendMessage("§7§l>> §cAn error occurred while creating plot!");
            return;
        }
        try (Connection connection = MySQL.getConnection()) {
            ResultSet rs = connection.createStatement().executeQuery("SELECT (t1.idplot + 1) as firstID FROM plots t1 " +
                    "WHERE NOT EXISTS (SELECT t2.idplot FROM plots t2 WHERE t2.idplot = t1.idplot + 1)");
            if (rs.next()) {
                plotID = rs.getInt(1);
            }

            String filePath = Paths.get(System.getProperty("user.dir"), "schem", String.valueOf(cityID), plotID + ".schematic").toString();
            File schematic = new File(filePath);

            boolean createdDirectory = schematic.getParentFile().mkdirs();
            Bukkit.getLogger().log(Level.INFO, "Created new Directory (" + schematic.getParentFile().getName() + "): " + createdDirectory);

            boolean createdFile = schematic.createNewFile();
            Bukkit.getLogger().log(Level.INFO, "Created new File (" + schematic.getName() + "): " + createdFile);

            WorldEditPlugin worldEdit = (WorldEditPlugin) Bukkit.getServer().getPluginManager().getPlugin("WorldEdit");

            Clipboard cb = new BlockArrayClipboard(polyRegion);
            cb.setOrigin(cb.getRegion().getCenter());
            LocalSession playerSession = WorldEdit.getInstance().getSessionManager().findByName(player.getName());
            ForwardExtentCopy copy = new ForwardExtentCopy(playerSession.createEditSession(worldEdit.wrapPlayer(player)), polyRegion, cb, polyRegion.getMinimumPoint());
            Operations.completeLegacy(copy);

            try (ClipboardWriter writer = ClipboardFormat.SCHEMATIC.getWriter(new FileOutputStream(schematic, false))) {
                writer.write(cb, polyRegion.getWorld().getWorldData());
            }
            rs.close();

            // Upload via SFTP
            JSch jsch = new JSch();
            jsch.setKnownHosts(Paths.get(System.getProperty("user.dir"), "known_hosts").toString());
            Session jschSession = jsch.getSession(fb.getString("sftp.username"), fb.getString("sftp.host"));
            jschSession.setPassword(fb.getString("sftp.password"));
            jschSession.connect();
            ChannelSftp channelSftp = (ChannelSftp) jschSession.openChannel("sftp");
            channelSftp.connect();
            String uploadPath = Paths.get(fb.getString("sftp.location"), String.valueOf(cityID)).toString();
            uploadPath = uploadPath.substring(1);
            String[] uploadPathSplit = uploadPath.split(Pattern.quote(System.getProperty("file.separator")));

            channelSftp.cd("/");
            for (String path : uploadPathSplit) {

                try {
                    channelSftp.cd(path);
                } catch (SftpException e2) {
                    channelSftp.mkdir(path);
                    channelSftp.cd(path);
                }


            }
            channelSftp.put(filePath, plotID + ".schematic");
            channelSftp.disconnect();
            jschSession.disconnect();

            /*SSHClient ssh = new SSHClient();
            ssh.loadKnownHosts();
            ssh.addHostKeyVerifier("c1:94:01:00:56:2e:c8:35:5a:87:16:c4:6a:8c:82:c5");
            ssh.connect(fb.getString("sftp.host"));
            try {
                ssh.authPassword(fb.getString("sftp.username"), fb.getString("sftp.password"));
                SFTPClient sftp = ssh.newSFTPClient();
                try {
                    sftp.mkdirs(schematic.getParentFile().getName());
                    System.out.println(filePath);
                    System.out.println("to");
                    System.out.println( Paths.get(fb.getString("sftp.location"), String.valueOf(cityID), plotID + ".schematic").toString());
                    sftp.put(new FileSystemFile(filePath), Paths.get(fb.getString("sftp.location"), String.valueOf(cityID), plotID + ".schematic").toString());
                } finally {
                    sftp.close();
                }
            } finally {
                ssh.disconnect();
            }*/


        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "An error occurred while saving new plot to a schematic!", ex);
            player.sendMessage("§7§l>> §cAn error occurred while creating plot!");
            return;
        }

        // Save to database
        try (Connection connection = MySQL.getConnection()) {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO plots (idplot, idcity, mcCoordinates, iddifficulty) VALUES (?, ?, ?, ?)");

            statement.setInt(1, plotID);
            statement.setInt(2, cityID);
            statement.setString(3, player.getLocation().getX() + "," + player.getLocation().getY() + "," + player.getLocation().getZ());
            statement.setInt(4, 1);

            statement.execute();

            player.sendMessage("§7§l>> §aSuccessfully created new plot!§7 (City: " + cityID + " | ID: " + plotID + ")");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);

            statement.close();
        } catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "An error occurred while saving new plot to database!", ex);
            player.sendMessage("§7§l>> §cAn error occurred while creating plot!");

            try {
                Files.deleteIfExists(Paths.get("./schem"));
            } catch (IOException e) {
                Bukkit.getLogger().log(Level.SEVERE, "An error occurred while deleting schematic!", ex);
            }
        }


    }
}
