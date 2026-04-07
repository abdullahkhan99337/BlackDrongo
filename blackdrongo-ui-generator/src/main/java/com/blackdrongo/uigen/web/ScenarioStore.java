package com.blackdrongo.uigen.web;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScenarioStore {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static List<ScenarioEntry> list() {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, project_id, feature_id, name, steps, tags, mvn_args, base_url FROM scenarios ORDER BY created_at")) {
            ResultSet rs = ps.executeQuery();
            List<ScenarioEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(mapEntry(rs));
            }
            return Collections.unmodifiableList(entries);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list scenarios.", e);
        }
    }

    public static List<ScenarioEntry> listByProject(String projectId) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, project_id, feature_id, name, steps, tags, mvn_args, base_url FROM scenarios WHERE project_id = ? ORDER BY created_at")) {
            ps.setString(1, projectId);
            ResultSet rs = ps.executeQuery();
            List<ScenarioEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(mapEntry(rs));
            }
            return entries;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list scenarios by project.", e);
        }
    }

    public static List<ScenarioEntry> listByFeature(String featureId) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, project_id, feature_id, name, steps, tags, mvn_args, base_url FROM scenarios WHERE feature_id = ? ORDER BY created_at")) {
            ps.setString(1, featureId);
            ResultSet rs = ps.executeQuery();
            List<ScenarioEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(mapEntry(rs));
            }
            return entries;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list scenarios by feature.", e);
        }
    }

    public static ScenarioEntry save(String projectId, String featureId, ScenarioRequest request) {
        String id = UUID.randomUUID().toString();
        ScenarioRequest copy = request.copy();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO scenarios (id, project_id, feature_id, name, steps, tags, mvn_args, base_url, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, projectId);
            ps.setString(3, featureId);
            ps.setString(4, copy.getScenarioName());
            ps.setString(5, copy.getScenarioSteps());
            ps.setString(6, copy.getScenarioTags());
            ps.setString(7, copy.getMvnArgs());
            ps.setString(8, copy.getBaseUrl());
            ps.setString(9, LocalDateTime.now().format(STAMP));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save scenario.", e);
        }
        return new ScenarioEntry(id, projectId, featureId, copy);
    }

    public static void update(String id, ScenarioRequest request) {
        ScenarioRequest copy = request.copy();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE scenarios SET name = ?, steps = ?, tags = ?, mvn_args = ?, base_url = ? WHERE id = ?")) {
            ps.setString(1, copy.getScenarioName());
            ps.setString(2, copy.getScenarioSteps());
            ps.setString(3, copy.getScenarioTags());
            ps.setString(4, copy.getMvnArgs());
            ps.setString(5, copy.getBaseUrl());
            ps.setString(6, id);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Scenario not found: " + id);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update scenario.", e);
        }
    }

    public static void delete(String id) {
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM scenario_runs WHERE scenario_id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM scenarios WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete scenario.", e);
        }
    }

    public static void deleteByFeature(String featureId) {
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM scenario_runs WHERE scenario_id IN (SELECT id FROM scenarios WHERE feature_id = ?)")) {
                ps.setString(1, featureId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM scenarios WHERE feature_id = ?")) {
                ps.setString(1, featureId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete scenarios by feature.", e);
        }
    }

    public static Optional<ScenarioEntry> findById(String id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT id, project_id, feature_id, name, steps, tags, mvn_args, base_url FROM scenarios WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapEntry(rs));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find scenario.", e);
        }
    }

    public static void clear() {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps1 = connection.prepareStatement("DELETE FROM scenario_runs");
             PreparedStatement ps2 = connection.prepareStatement("DELETE FROM scenarios")) {
            ps1.executeUpdate();
            ps2.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear scenarios.", e);
        }
    }

    public static void updateRunStatus(String id, String state, String message) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO scenario_runs (scenario_id, state, message, updated_at) VALUES (?, ?, ?, ?) " +
                             "ON CONFLICT(scenario_id) DO UPDATE SET state = excluded.state, message = excluded.message, updated_at = excluded.updated_at")) {
            ps.setString(1, id);
            ps.setString(2, state);
            ps.setString(3, message);
            ps.setString(4, LocalDateTime.now().format(STAMP));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update run status.", e);
        }
    }

    public static RunStatus getRunStatus(String id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT state, message, updated_at FROM scenario_runs WHERE scenario_id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return new RunStatus(rs.getString("state"), rs.getString("message"), rs.getString("updated_at"));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get run status.", e);
        }
    }

    public static Map<String, RunStatus> listRunStatuses() {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT scenario_id, state, message, updated_at FROM scenario_runs")) {
            ResultSet rs = ps.executeQuery();
            Map<String, RunStatus> statuses = new HashMap<>();
            while (rs.next()) {
                statuses.put(
                        rs.getString("scenario_id"),
                        new RunStatus(rs.getString("state"), rs.getString("message"), rs.getString("updated_at"))
                );
            }
            return statuses;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list run statuses.", e);
        }
    }

    public static int markStaleRunningAsFailed(long staleAfterSeconds) {
        String modifier = "-" + Math.max(1, staleAfterSeconds) + " seconds";
        String now = LocalDateTime.now().format(STAMP);
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE scenario_runs " +
                             "SET state = 'FAILURE', " +
                             "    message = CASE " +
                             "        WHEN message IS NULL OR trim(message) = '' THEN ? " +
                             "        ELSE message || ' (stale run closed)' " +
                             "    END, " +
                             "    updated_at = ? " +
                             "WHERE upper(state) = 'RUNNING' " +
                             "  AND (updated_at IS NULL OR trim(updated_at) = '' OR datetime(updated_at) <= datetime('now', ?))")) {
            ps.setString(1, "Run was interrupted or became stale");
            ps.setString(2, now);
            ps.setString(3, modifier);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to reconcile stale running statuses.", e);
        }
    }

    private static ScenarioEntry mapEntry(ResultSet rs) throws SQLException {
        ScenarioRequest request = new ScenarioRequest();
        request.setScenarioName(rs.getString("name"));
        request.setScenarioSteps(rs.getString("steps"));
        request.setScenarioTags(rs.getString("tags"));
        request.setMvnArgs(rs.getString("mvn_args"));
        request.setBaseUrl(rs.getString("base_url"));
        return new ScenarioEntry(rs.getString("id"), rs.getString("project_id"), rs.getString("feature_id"), request);
    }

    public record ScenarioEntry(String id, String projectId, String featureId, ScenarioRequest request) {
    }

    public record RunStatus(String state, String message, String updatedAt) {
    }
}
