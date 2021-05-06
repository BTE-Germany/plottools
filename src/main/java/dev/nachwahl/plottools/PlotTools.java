package dev.nachwahl.plottools;

import dev.arantes.inventorymenulib.listeners.InventoryListener;
import dev.nachwahl.plottools.commands.CreatePlotCommand;
import dev.nachwahl.plottools.commands.PastePlotsCommand;
import dev.nachwahl.plottools.utils.FileBuilder;
import dev.nachwahl.plottools.utils.MySQL;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;

public final class PlotTools extends JavaPlugin {

    private static PlotTools plugin;
    @Override
    public void onEnable() {
        plugin = this;

        // Register commands

        this.getCommand("createplot").setExecutor(new CreatePlotCommand(this));
        this.getCommand("pasteplots").setExecutor(new PastePlotsCommand(this));

        new InventoryListener(this);
        List example = new ArrayList<Integer>();
        example.add(1);
        example.add(2);
        new FileBuilder("plugins/PlotTools", "mysql.yml")
                .addDefault("mysql.host", "localhost")
                .addDefault("mysql.port", "3306")
                .addDefault("mysql.database", "buildevent")
                .addDefault("mysql.user", "root")
                .addDefault("mysql.password", "")
                .copyDefaults(true).save();
        new FileBuilder("plugins/PlotTools", "config.yml")
                .addDefault("sftp.host", "localhost")
                .addDefault("sftp.username", "root")
                .addDefault("sftp.password", "foo")
                .addDefault("sftp.location", "/home/mc/server/plugins/AlpsBTE-PlotSystem/plots/")
                .addDefault("citiesOnTheServer", example)
                .copyDefaults(true).save();
        if(!MySQL.isConnected()) {
            MySQL.connect();
        }
    }

    @Override
    public void onDisable() {
        MySQL.disconnect();
    }



    public static PlotTools getInstance() {
        return plugin;
    }
}
