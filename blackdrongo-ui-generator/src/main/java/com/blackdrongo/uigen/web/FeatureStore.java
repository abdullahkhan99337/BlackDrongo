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

public class FeatureStore {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static List<FeatureEntry> list() {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, project_id, name, description, base_url, jira_stories FROM features ORDER BY created_at")) {
            ResultSet rs = ps.executeQuery();
            List<FeatureEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(mapEntry(rs));
            }
            return Collections.unmodifiableList(entries);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list features.", e);
        }
    }

    public static List<FeatureEntry> listByProject(String projectId) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, project_id, name, description, base_url, jira_stories FROM features WHERE project_id = ? ORDER BY created_at")) {
            ps.setString(1, projectId);
            ResultSet rs = ps.executeQuery();
            List<FeatureEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(mapEntry(rs));
            }
            return entries;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list features by project.", e);
        }
    }

    public static FeatureEntry save(String projectId, FeatureRequest request) {
        String id = UUID.randomUUID().toString();
        FeatureRequest copy = request.copy();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO features (id, project_id, name, description, base_url, jira_stories, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, projectId);
            ps.setString(3, copy.getFeatureName());
            ps.setString(4, copy.getFeatureDescription());
            ps.setString(5, copy.getBaseUrl());
            ps.setString(6, copy.getJiraUserStories());
            ps.setString(7, LocalDateTime.now().format(STAMP));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save feature.", e);
        }
        return new FeatureEntry(id, projectId, copy);
    }

    public static void update(String id, FeatureRequest request) {
        FeatureRequest copy = request.copy();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE features SET name = ?, description = ?, base_url = ?, jira_stories = ? WHERE id = ?")) {
            ps.setString(1, copy.getFeatureName());
            ps.setString(2, copy.getFeatureDescription());
            ps.setString(3, copy.getBaseUrl());
            ps.setString(4, copy.getJiraUserStories());
            ps.setString(5, id);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Feature not found: " + id);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update feature.", e);
        }
    }

    public static void delete(String id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM features WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete feature.", e);
        }
    }

    public static Optional<FeatureEntry> findById(String id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, project_id, name, description, base_url, jira_stories FROM features WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapEntry(rs));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find feature.", e);
        }
    }

    private static FeatureEntry mapEntry(ResultSet rs) throws SQLException {
        FeatureRequest request = new FeatureRequest();
        request.setFeatureName(rs.getString("name"));
        request.setFeatureDescription(rs.getString("description"));
        request.setBaseUrl(rs.getString("base_url"));
        request.setJiraUserStories(rs.getString("jira_stories"));
        return new FeatureEntry(rs.getString("id"), rs.getString("project_id"), request);
    }

    public record FeatureEntry(String id, String projectId, FeatureRequest request) {
    }
}
