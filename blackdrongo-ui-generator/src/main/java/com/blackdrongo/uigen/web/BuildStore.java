package com.blackdrongo.uigen.web;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class BuildStore {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BuildStore() {
    }

    public static BuildRunEntry createRun(String projectId,
                                          String projectName,
                                          String buildName,
                                          String selectionType,
                                          String targetSummary,
                                          String tags,
                                          boolean parallel,
                                          String headlessMode,
                                          String mvnArgs,
                                          String triggerType,
                                          String scheduleId) {
        String id = UUID.randomUUID().toString();
        String createdAt = nowStamp();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO build_runs (id, project_id, project_name, build_name, selection_type, target_summary, tags, parallel, headless_mode, mvn_args, trigger_type, status, message, created_at, schedule_id) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, projectId);
            ps.setString(3, projectName);
            ps.setString(4, buildName);
            ps.setString(5, selectionType);
            ps.setString(6, targetSummary);
            ps.setString(7, tags);
            ps.setInt(8, parallel ? 1 : 0);
            ps.setString(9, headlessMode);
            ps.setString(10, mvnArgs);
            ps.setString(11, triggerType);
            ps.setString(12, "QUEUED");
            ps.setString(13, "Build queued.");
            ps.setString(14, createdAt);
            ps.setString(15, scheduleId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create build run.", e);
        }
        return new BuildRunEntry(id, projectId, projectName, buildName, selectionType, targetSummary, tags,
                parallel, headlessMode, mvnArgs, triggerType, "QUEUED", "Build queued.", createdAt, "", "", scheduleId);
    }

    public static void updateRunState(String id, String status, String message, String startedAt, String completedAt) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE build_runs SET status = ?, message = ?, started_at = COALESCE(?, started_at), completed_at = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, message);
            ps.setString(3, blankToNull(startedAt));
            ps.setString(4, blankToNull(completedAt));
            ps.setString(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update build run state.", e);
        }
    }

    public static List<BuildRunEntry> listRuns(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM build_runs ORDER BY datetime(created_at) DESC LIMIT ?")) {
            ps.setInt(1, safeLimit);
            ResultSet rs = ps.executeQuery();
            List<BuildRunEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(mapRun(rs));
            }
            return entries;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list build runs.", e);
        }
    }

    public static BuildScheduleEntry createSchedule(BuildLaunchRequest request,
                                                    String projectName,
                                                    String targetSummary,
                                                    String recurrence,
                                                    String nextRunAt,
                                                    String timezone) {
        String id = UUID.randomUUID().toString();
        String createdAt = nowStamp();
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO build_schedules (id, project_id, project_name, build_name, selection_type, feature_ids, scenario_ids, tags, parallel, headless_mode, mvn_args, recurrence, next_run_at, timezone, status, target_summary, created_at) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setString(2, request.getProjectId());
            ps.setString(3, projectName);
            ps.setString(4, request.getBuildName());
            ps.setString(5, request.getTargetType());
            ps.setString(6, join(request.getFeatureIds()));
            ps.setString(7, join(request.getScenarioIds()));
            ps.setString(8, request.getTags());
            ps.setInt(9, request.isParallel() ? 1 : 0);
            ps.setString(10, request.getHeadlessMode());
            ps.setString(11, request.getMvnArgs());
            ps.setString(12, recurrence);
            ps.setString(13, nextRunAt);
            ps.setString(14, timezone);
            ps.setString(15, "ACTIVE");
            ps.setString(16, targetSummary);
            ps.setString(17, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create build schedule.", e);
        }
        return new BuildScheduleEntry(id, request.getProjectId(), projectName, request.getBuildName(), request.getTargetType(),
                request.getFeatureIds(), request.getScenarioIds(), request.getTags(), request.isParallel(),
                request.getHeadlessMode(), request.getMvnArgs(), recurrence, nextRunAt, timezone, "ACTIVE",
                targetSummary, createdAt, "");
    }

    public static List<BuildScheduleEntry> listSchedules() {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM build_schedules ORDER BY CASE WHEN status = 'ACTIVE' THEN 0 ELSE 1 END, datetime(next_run_at) ASC, datetime(created_at) DESC")) {
            ResultSet rs = ps.executeQuery();
            List<BuildScheduleEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(mapSchedule(rs));
            }
            return entries;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list build schedules.", e);
        }
    }

    public static Optional<BuildScheduleEntry> findSchedule(String id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT * FROM build_schedules WHERE id = ?")) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(mapSchedule(rs));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find build schedule.", e);
        }
    }

    public static List<BuildScheduleEntry> dueSchedules(String nowStamp) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM build_schedules WHERE status = 'ACTIVE' AND datetime(next_run_at) <= datetime(?) ORDER BY datetime(next_run_at) ASC")) {
            ps.setString(1, nowStamp);
            ResultSet rs = ps.executeQuery();
            List<BuildScheduleEntry> entries = new ArrayList<>();
            while (rs.next()) {
                entries.add(mapSchedule(rs));
            }
            return entries;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list due build schedules.", e);
        }
    }

    public static void updateScheduleAfterRun(String id, String lastRunAt, String nextRunAt, String status) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE build_schedules SET last_run_at = ?, next_run_at = ?, status = ? WHERE id = ?")) {
            ps.setString(1, lastRunAt);
            ps.setString(2, blankToNull(nextRunAt));
            ps.setString(3, status);
            ps.setString(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update build schedule.", e);
        }
    }

    public static void cancelSchedule(String id) {
        try (Connection connection = Database.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE build_schedules SET status = 'CANCELLED' WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to cancel build schedule.", e);
        }
    }

    public static String nowStamp() {
        return LocalDateTime.now().format(STAMP);
    }

    private static BuildRunEntry mapRun(ResultSet rs) throws SQLException {
        return new BuildRunEntry(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("project_name"),
                rs.getString("build_name"),
                rs.getString("selection_type"),
                rs.getString("target_summary"),
                rs.getString("tags"),
                rs.getInt("parallel") == 1,
                rs.getString("headless_mode"),
                rs.getString("mvn_args"),
                rs.getString("trigger_type"),
                rs.getString("status"),
                rs.getString("message"),
                rs.getString("created_at"),
                rs.getString("started_at"),
                rs.getString("completed_at"),
                rs.getString("schedule_id")
        );
    }

    private static BuildScheduleEntry mapSchedule(ResultSet rs) throws SQLException {
        return new BuildScheduleEntry(
                rs.getString("id"),
                rs.getString("project_id"),
                rs.getString("project_name"),
                rs.getString("build_name"),
                rs.getString("selection_type"),
                split(rs.getString("feature_ids")),
                split(rs.getString("scenario_ids")),
                rs.getString("tags"),
                rs.getInt("parallel") == 1,
                rs.getString("headless_mode"),
                rs.getString("mvn_args"),
                rs.getString("recurrence"),
                rs.getString("next_run_at"),
                rs.getString("timezone"),
                rs.getString("status"),
                rs.getString("target_summary"),
                rs.getString("created_at"),
                rs.getString("last_run_at")
        );
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join("\n", values);
    }

    private static List<String> split(String value) {
        List<String> values = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return values;
        }
        String[] parts = value.replace("\r", "\n").split("\n");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record BuildRunEntry(String id,
                                String projectId,
                                String projectName,
                                String buildName,
                                String selectionType,
                                String targetSummary,
                                String tags,
                                boolean parallel,
                                String headlessMode,
                                String mvnArgs,
                                String triggerType,
                                String status,
                                String message,
                                String createdAt,
                                String startedAt,
                                String completedAt,
                                String scheduleId) {
    }

    public record BuildScheduleEntry(String id,
                                     String projectId,
                                     String projectName,
                                     String buildName,
                                     String selectionType,
                                     List<String> featureIds,
                                     List<String> scenarioIds,
                                     String tags,
                                     boolean parallel,
                                     String headlessMode,
                                     String mvnArgs,
                                     String recurrence,
                                     String nextRunAt,
                                     String timezone,
                                     String status,
                                     String targetSummary,
                                     String createdAt,
                                     String lastRunAt) {
    }
}
