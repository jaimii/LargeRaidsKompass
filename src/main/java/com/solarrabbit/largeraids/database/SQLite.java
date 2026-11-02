package com.solarrabbit.largeraids.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public class SQLite extends Database {
    private static final String SQLITE_CREATE_VILLAGES_TABLE_STATEMENT = "CREATE TABLE IF NOT EXISTS "
            + VILLAGES_TABLE_NAME
            + " (`name` STRING NOT NULL, `world` STRING NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL, `z` REAL NOT NULL, "
            + "PRIMARY KEY (`name`));";

    public SQLite(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public CompletableFuture<Connection> getSQLConnection() {
        return CompletableFuture.supplyAsync(this::createOrOpenDataFile).thenApply(dataFile -> {
            try {
                Class.forName("org.sqlite.JDBC");
                return DriverManager.getConnection("jdbc:sqlite:" + dataFile);
            } catch (SQLException | ClassNotFoundException ex) {
                ex.printStackTrace();
                return null;
            }
        });
    }

    @Override
    public void load() {
        getSQLConnection().thenAccept(connection -> {
            if (connection == null) return;
            try (Statement s = connection.createStatement()) {
                s.executeUpdate(SQLITE_CREATE_VILLAGES_TABLE_STATEMENT);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            testConnection(connection);
        });
    }

    @Override
    public CompletableFuture<Map<String, Location>> getCentres() {
        Map<String, Location> centres = new HashMap<>();
        return getSQLConnection().thenApply(connection -> {
            if (connection == null) return centres;
            try (Statement createStmt = connection.createStatement()) {
                createStmt.executeUpdate(SQLITE_CREATE_VILLAGES_TABLE_STATEMENT);
            } catch (SQLException ignored) {
            }
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + VILLAGES_TABLE_NAME + ";");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    World world = this.plugin.getServer().getWorld(rs.getString("world"));
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    Location location = new Location(world, x, y, z);
                    centres.put(name, location);
                }
                return centres;
            } catch (SQLException ex) {
                ex.printStackTrace();
                return null;
            } finally {
                close(null, connection);
            }
        });
    }

    @Override
    public CompletableFuture<Void> addCentre(Location location, String name) {
        return getSQLConnection().thenAccept(connection -> {
            if (connection == null) return;
            String sql = "REPLACE INTO " + VILLAGES_TABLE_NAME + " (name, world, x, y, z) VALUES(?, ?, ?, ?, ?);";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, location.getWorld().getName());
                ps.setDouble(3, location.getX());
                ps.setDouble(4, location.getY());
                ps.setDouble(5, location.getZ());
                ps.executeUpdate();
            } catch (SQLException ex) {
                ex.printStackTrace();
                throw new RuntimeException(ex);
            } finally {
                close(null, connection);
            }
        });
    }

    @Override
    public CompletableFuture<Void> removeCentre(String name) {
        return getSQLConnection().thenAccept(connection -> {
            if (connection == null) return;
            String sql = "DELETE FROM " + VILLAGES_TABLE_NAME + " WHERE name = ?;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.executeUpdate();
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            } finally {
                close(null, connection);
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public CompletableFuture<Location> getCentre(String name) {
        return getSQLConnection().thenApply(connection -> {
            if (connection == null) return null;
            String sql = "SELECT * FROM " + VILLAGES_TABLE_NAME + " WHERE name = ?;";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        World world = this.plugin.getServer().getWorld(rs.getString("world"));
                        double x = rs.getDouble("x");
                        double y = rs.getDouble("y");
                        double z = rs.getDouble("z");
                        return new Location(world, x, y, z);
                    }
                }
                return null;
            } catch (SQLException ex) {
                return null;
            } finally {
                close(null, connection);
            }
        });
    }
}