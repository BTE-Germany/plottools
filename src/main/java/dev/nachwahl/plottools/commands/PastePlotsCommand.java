package dev.nachwahl.plottools.commands;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.schematic.SchematicFormat;
import com.sk89q.worldedit.world.DataException;
import dev.nachwahl.plottools.PlotTools;
import dev.nachwahl.plottools.utils.FileBuilder;
import dev.nachwahl.plottools.utils.MySQL;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.*;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class PastePlotsCommand implements CommandExecutor {
    PlotTools plugin;

    public PastePlotsCommand(PlotTools plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            System.out.println("This command can only be executed by a player.");
            return false;
        }
        Player player = (Player) sender;


        if (!player.hasPermission("plottools.createplot")) {
            player.sendMessage("§7§l>> §cYou have no permission to execute this command.");
            return false;
        }

        player.sendMessage("§7§l>> §aOne moment please...");
        FileBuilder fb = new FileBuilder("plugins/PlotTools", "config.yml");
        JSch jsch = new JSch();
        Session jschSession;
        ChannelSftp channelSftp;

        try (Connection connection = MySQL.getConnection()) {



            jsch.setKnownHosts(Paths.get(System.getProperty("user.dir"), "known_hosts").toString());
            jschSession = jsch.getSession(fb.getString("sftp.username"), fb.getString("sftp.host"));
            jschSession.setPassword(fb.getString("sftp.password"));
            jschSession.connect();
            channelSftp = (ChannelSftp) jschSession.openChannel("sftp");
            channelSftp.connect();



            for(Integer city : fb.getIntegerList("citiesOnTheServer")) {
                uploadPlot(1, player, channelSftp);
            }



            channelSftp.disconnect();
            jschSession.disconnect();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return false;
    }
    public static void pastePlotSchematic(int plotID, int cityID, Vector mcCoordinates) throws IOException, DataException, MaxChangedBlocksException {
        File file = Paths.get(System.getProperty("user.dir"), "schem", "finishedPlots", String.valueOf(cityID), plotID + ".schematic").toFile();

        EditSession editSession = new EditSession(new BukkitWorld(Bukkit.getWorld("world")), -1);
        editSession.enableQueue();

        SchematicFormat schematicFormat = SchematicFormat.getFormat(file);
        CuboidClipboard clipboard = schematicFormat.load(file);

        clipboard.paste(editSession, mcCoordinates, true);
        editSession.flushQueue();

        try (Connection connection = MySQL.getConnection()){
            PreparedStatement ps = connection.prepareStatement("UPDATE plots SET isPasted = '1' WHERE idplot = '" + plotID + "'");
            ps.executeUpdate();
        } catch (SQLException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
        }
    }


    public Boolean uploadPlot(Integer city, Player player, ChannelSftp channelSftp) throws SQLException, SftpException, DataException, IOException, MaxChangedBlocksException {
        FileBuilder fb = new FileBuilder("plugins/PlotTools", "config.yml");

        PreparedStatement ps;
        ps = MySQL.getConnection().prepareStatement("SELECT COUNT(*) AS count FROM plots WHERE idcity = ? AND isPasted = 0  AND `status` = 'complete';");
        ps.setInt(1, city);
        ResultSet rs = ps.executeQuery();
        Integer count = 0;
        while (rs.next()) {
            count = rs.getInt("count");
        }

        if(count == 0) {
            player.sendMessage("§7§l>> §cThere are no plots to be pasted.");
            return false;
        }
        player.sendMessage("§7§l>> §aStart copying §6" + count + " §aplots.");

        ps = MySQL.getConnection().prepareStatement("SELECT * FROM plots WHERE idcity = ? AND isPasted = 0 AND status = 'complete';");
        ps.setInt(1, city);
        rs = ps.executeQuery();

        while (rs.next()) {
            channelSftp.cd(fb.getString("sftp.location"));
            channelSftp.cd("./finishedPlots");
            channelSftp.cd("./" + city);
            String filePath = Paths.get(System.getProperty("user.dir"), "schem", "finishedPlots", String.valueOf(city), rs.getInt("idplot") + ".schematic").toString();
            File schematic = new File(filePath);
            boolean createdDirectory = schematic.getParentFile().mkdirs();
            channelSftp.get(String.valueOf(rs.getInt("idplot")) + ".schematic", filePath);
            player.sendMessage("§7§l>> §aSuccessfully copied plot §6#" + rs.getInt("idplot") + "§a!");
            String[] splitCoordinates = rs.getString("mcCoordinates").split(",");
            if (splitCoordinates.length == 3) {
                Vector mcCoordinates = Vector.toBlockPoint(
                        Float.parseFloat(splitCoordinates[0]),
                        Float.parseFloat(splitCoordinates[1]),
                        Float.parseFloat(splitCoordinates[2])
                );
                pastePlotSchematic(rs.getInt("idplot"), city, mcCoordinates);
            } else {
                player.sendMessage("§7§l>> §cThis plot doesn't have a Y coordinate!");
            }

        }
        return true;
    }

}
