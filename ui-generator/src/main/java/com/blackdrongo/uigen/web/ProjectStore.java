package com.blackdrongo.uigen.web;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ProjectStore {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static List<ProjectEntry> list() {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, name, engine, browser, headless, base_url, " +
                             "chrome_start_maximized, chrome_incognito, chrome_disable_notifications, " +
                             "chrome_disable_popup_blocking, chrome_accept_insecure_certs, chrome_custom_args, " +
                             "firefox_private_mode, firefox_accept_insecure_certs, firefox_custom_args, " +
                             "edge_start_maximized, edge_in_private, edge_accept_insecure_certs, edge_custom_args " +
                             "FROM projects ORDER BY created_at")) {
            ResultSet rs = ps.executeQuery();
            List<ProjectEntry> entries = new ArrayList<>();
            while (rs.next()) {
                ProjectRequest request = new ProjectRequest();
                request.setProjectName(rs.getString("name"));
                request.setEngine(rs.getString("engine"));
                request.setBrowser(rs.getString("browser"));
                request.setHeadless(rs.getInt("headless") == 1);
                request.setBaseUrl(rs.getString("base_url"));
                request.setChromeStartMaximized(readBoolean(rs, "chrome_start_maximized", true));
                request.setChromeIncognito(readBoolean(rs, "chrome_incognito", true));
                request.setChromeDisableNotifications(readBoolean(rs, "chrome_disable_notifications", true));
                request.setChromeDisablePopupBlocking(readBoolean(rs, "chrome_disable_popup_blocking", true));
                request.setChromeAcceptInsecureCerts(readBoolean(rs, "chrome_accept_insecure_certs", true));
                request.setChromeCustomArgs(rs.getString("chrome_custom_args"));
                request.setFirefoxPrivateMode(readBoolean(rs, "firefox_private_mode", true));
                request.setFirefoxAcceptInsecureCerts(readBoolean(rs, "firefox_accept_insecure_certs", true));
                request.setFirefoxCustomArgs(rs.getString("firefox_custom_args"));
                request.setEdgeStartMaximized(readBoolean(rs, "edge_start_maximized", true));
                request.setEdgeInPrivate(readBoolean(rs, "edge_in_private", true));
                request.setEdgeAcceptInsecureCerts(readBoolean(rs, "edge_accept_insecure_certs", true));
                request.setEdgeCustomArgs(rs.getString("edge_custom_args"));
                entries.add(new ProjectEntry(rs.getString("id"), request));
            }
            return Collections.unmodifiableList(entries);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list projects.", e);
        }
    }

    public static int count() {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM projects")) {
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count projects.", e);
        }
    }

    public static ProjectEntry save(ProjectRequest request) {
        String id = UUID.randomUUID().toString();
        ProjectRequest copy = request.copy();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO projects (id, name, engine, browser, headless, base_url, " +
                             "chrome_start_maximized, chrome_incognito, chrome_disable_notifications, " +
                             "chrome_disable_popup_blocking, chrome_accept_insecure_certs, chrome_custom_args, " +
                             "firefox_private_mode, firefox_accept_insecure_certs, firefox_custom_args, " +
                             "edge_start_maximized, edge_in_private, edge_accept_insecure_certs, edge_custom_args, created_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, copy.getProjectName());
            ps.setString(3, copy.getEngine());
            ps.setString(4, copy.getBrowser());
            ps.setInt(5, copy.isHeadless() ? 1 : 0);
            ps.setString(6, copy.getBaseUrl());
            ps.setInt(7, copy.isChromeStartMaximized() ? 1 : 0);
            ps.setInt(8, copy.isChromeIncognito() ? 1 : 0);
            ps.setInt(9, copy.isChromeDisableNotifications() ? 1 : 0);
            ps.setInt(10, copy.isChromeDisablePopupBlocking() ? 1 : 0);
            ps.setInt(11, copy.isChromeAcceptInsecureCerts() ? 1 : 0);
            ps.setString(12, copy.getChromeCustomArgs());
            ps.setInt(13, copy.isFirefoxPrivateMode() ? 1 : 0);
            ps.setInt(14, copy.isFirefoxAcceptInsecureCerts() ? 1 : 0);
            ps.setString(15, copy.getFirefoxCustomArgs());
            ps.setInt(16, copy.isEdgeStartMaximized() ? 1 : 0);
            ps.setInt(17, copy.isEdgeInPrivate() ? 1 : 0);
            ps.setInt(18, copy.isEdgeAcceptInsecureCerts() ? 1 : 0);
            ps.setString(19, copy.getEdgeCustomArgs());
            ps.setString(20, LocalDateTime.now().format(STAMP));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save project.", e);
        }
        return new ProjectEntry(id, copy);
    }

    public static void update(String id, ProjectRequest request) {
        ProjectRequest copy = request.copy();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE projects SET name = ?, engine = ?, browser = ?, headless = ?, base_url = ?, " +
                             "chrome_start_maximized = ?, chrome_incognito = ?, chrome_disable_notifications = ?, " +
                             "chrome_disable_popup_blocking = ?, chrome_accept_insecure_certs = ?, chrome_custom_args = ?, " +
                             "firefox_private_mode = ?, firefox_accept_insecure_certs = ?, firefox_custom_args = ?, " +
                             "edge_start_maximized = ?, edge_in_private = ?, edge_accept_insecure_certs = ?, edge_custom_args = ? " +
                             "WHERE id = ?")) {
            ps.setString(1, copy.getProjectName());
            ps.setString(2, copy.getEngine());
            ps.setString(3, copy.getBrowser());
            ps.setInt(4, copy.isHeadless() ? 1 : 0);
            ps.setString(5, copy.getBaseUrl());
            ps.setInt(6, copy.isChromeStartMaximized() ? 1 : 0);
            ps.setInt(7, copy.isChromeIncognito() ? 1 : 0);
            ps.setInt(8, copy.isChromeDisableNotifications() ? 1 : 0);
            ps.setInt(9, copy.isChromeDisablePopupBlocking() ? 1 : 0);
            ps.setInt(10, copy.isChromeAcceptInsecureCerts() ? 1 : 0);
            ps.setString(11, copy.getChromeCustomArgs());
            ps.setInt(12, copy.isFirefoxPrivateMode() ? 1 : 0);
            ps.setInt(13, copy.isFirefoxAcceptInsecureCerts() ? 1 : 0);
            ps.setString(14, copy.getFirefoxCustomArgs());
            ps.setInt(15, copy.isEdgeStartMaximized() ? 1 : 0);
            ps.setInt(16, copy.isEdgeInPrivate() ? 1 : 0);
            ps.setInt(17, copy.isEdgeAcceptInsecureCerts() ? 1 : 0);
            ps.setString(18, copy.getEdgeCustomArgs());
            ps.setString(19, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update project.", e);
        }
    }

    public static Optional<ProjectEntry> findById(String id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, name, engine, browser, headless, base_url, " +
                             "chrome_start_maximized, chrome_incognito, chrome_disable_notifications, " +
                             "chrome_disable_popup_blocking, chrome_accept_insecure_certs, chrome_custom_args, " +
                             "firefox_private_mode, firefox_accept_insecure_certs, firefox_custom_args, " +
                             "edge_start_maximized, edge_in_private, edge_accept_insecure_certs, edge_custom_args " +
                             "FROM projects WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Optional.empty();
            }
            ProjectRequest request = new ProjectRequest();
            request.setProjectName(rs.getString("name"));
            request.setEngine(rs.getString("engine"));
            request.setBrowser(rs.getString("browser"));
            request.setHeadless(rs.getInt("headless") == 1);
            request.setBaseUrl(rs.getString("base_url"));
            request.setChromeStartMaximized(readBoolean(rs, "chrome_start_maximized", true));
            request.setChromeIncognito(readBoolean(rs, "chrome_incognito", true));
            request.setChromeDisableNotifications(readBoolean(rs, "chrome_disable_notifications", true));
            request.setChromeDisablePopupBlocking(readBoolean(rs, "chrome_disable_popup_blocking", true));
            request.setChromeAcceptInsecureCerts(readBoolean(rs, "chrome_accept_insecure_certs", true));
            request.setChromeCustomArgs(rs.getString("chrome_custom_args"));
            request.setFirefoxPrivateMode(readBoolean(rs, "firefox_private_mode", true));
            request.setFirefoxAcceptInsecureCerts(readBoolean(rs, "firefox_accept_insecure_certs", true));
            request.setFirefoxCustomArgs(rs.getString("firefox_custom_args"));
            request.setEdgeStartMaximized(readBoolean(rs, "edge_start_maximized", true));
            request.setEdgeInPrivate(readBoolean(rs, "edge_in_private", true));
            request.setEdgeAcceptInsecureCerts(readBoolean(rs, "edge_accept_insecure_certs", true));
            request.setEdgeCustomArgs(rs.getString("edge_custom_args"));
            return Optional.of(new ProjectEntry(rs.getString("id"), request));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find project.", e);
        }
    }

    private static boolean readBoolean(ResultSet rs, String column, boolean fallback) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static void delete(String id) {
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM scenario_runs WHERE scenario_id IN (SELECT id FROM scenarios WHERE project_id = ?)")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM scenarios WHERE project_id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM features WHERE project_id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM projects WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete project.", e);
        }
    }

    public record ProjectEntry(String id, ProjectRequest project) {}
}
