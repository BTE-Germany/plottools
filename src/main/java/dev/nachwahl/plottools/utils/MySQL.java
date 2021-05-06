package dev.nachwahl.plottools.utils;

import java.sql.*;

public class MySQL {
    public static Connection con;
    public static Connection DCDBcon;

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public static void connect() {
        FileBuilder fb = new FileBuilder("plugins/PlotTools", "mysql.yml");
        String host = fb.getString("mysql.host");
        String port = fb.getString("mysql.port");
        String database = fb.getString("mysql.database");
        String user = fb.getString("mysql.user");
        String password = fb.getString("mysql.password");
        if (!isConnected()) {
            try {
                con = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false", user, password);
                System.out.println("[PlotTools]" + ANSI_GREEN + " MySQL connection ok!" + ANSI_RESET);
            } catch (SQLException e) {
                e.printStackTrace();
                System.out.println("[PlotTools]" + ANSI_RED + " MySQL connection error" + ANSI_RESET);
            }
        }
    }

    public static void disconnect() {
        if (isConnected()) {
            try {
                con.close();
                DCDBcon.close();
                System.out.println("[PlotTools] MySQL connection closed");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

    public static boolean isConnected() {
        try {
            return (con != null && (!con.isClosed()));
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }


    public static Connection getConnection() {
        if (!isConnected())
            connect();
        return con;
    }

    public static ResultSet getCities() throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM cityProjects");
        ResultSet resultSet = ps.executeQuery();
        return resultSet;

    }



}

