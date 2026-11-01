// DatabaseRepository.java
// Data Access Object (DAO) for MySQL operations using JDBC.
// Enforces PreparedStatements, parameter binding, transactions,
// and audit logging in report_history.

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseRepository {

    // =====================================================================
    // 1. USER ACCOUNT REGISTRATION & AUTHENTICATION
    // =====================================================================

    // Registers a new user account with role-specific profile inside a transaction
    public static String registerUser(String username, String plainPassword, String email,
                                      String fullName, String employeeId, String role,
                                      String wardNumber, String deptName,
                                      String teamId, String teamName, String equipmentStr) {
        if (!DatabaseConnection.isAvailable()) {
            return null; // Signals fallback to in-memory registration
        }

        PasswordHasher.HashResult hashResult = PasswordHasher.hashPassword(plainPassword);
        String userId = "U_" + role.substring(0, Math.min(role.length(), 4)) + "_" + System.currentTimeMillis() % 100000;

        String insertUserSql = "INSERT INTO users (user_id, full_name, employee_id, email, username, "
                + "password_hash, salt, role, account_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false); // Begin transaction

            try (PreparedStatement psUser = conn.prepareStatement(insertUserSql)) {
                psUser.setString(1, userId);
                psUser.setString(2, fullName);
                psUser.setString(3, (employeeId != null && !employeeId.isEmpty()) ? employeeId : null);
                psUser.setString(4, email);
                psUser.setString(5, username);
                psUser.setString(6, hashResult.getHash());
                psUser.setString(7, hashResult.getSalt());
                psUser.setString(8, role);
                psUser.executeUpdate();
            }

            // Insert role-specific profile
            if (UserAccount.ROLE_WARD.equals(role)) {
                String sql = "INSERT INTO ward_officer_profiles (user_id, ward_number) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    ps.setString(2, (wardNumber != null && !wardNumber.isEmpty()) ? wardNumber : "W01");
                    ps.executeUpdate();
                }
            } else if (UserAccount.ROLE_ENGINEERING.equals(role)) {
                String sql = "INSERT INTO engineering_profiles (user_id, department_name) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, userId);
                    ps.setString(2, (deptName != null && !deptName.isEmpty()) ? deptName : "Civil & Road Maintenance");
                    ps.executeUpdate();
                }
            } else if (UserAccount.ROLE_MAINTENANCE.equals(role)) {
                String actualTeamId = (teamId != null && !teamId.isEmpty()) ? teamId : username;
                String actualTeamName = (teamName != null && !teamName.isEmpty()) ? teamName : fullName;
                String sql = "INSERT INTO maintenance_teams (team_id, user_id, team_name, current_location_id, availability_status) "
                        + "VALUES (?, ?, ?, 'L01', 'AVAILABLE') ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, actualTeamId);
                    ps.setString(2, userId);
                    ps.setString(3, actualTeamName);
                    ps.executeUpdate();
                }
            }

            conn.commit(); // Commit transaction
            return null; // Success (no error message)

        } catch (SQLException e) {
            if (e.getMessage().contains("uq_users_username") || e.getMessage().contains("username")) {
                return "Username '" + username + "' is already registered. Please choose another username.";
            } else if (e.getMessage().contains("uq_users_email") || e.getMessage().contains("email")) {
                return "Email address '" + email + "' is already registered.";
            } else if (e.getMessage().contains("employee_id")) {
                return "Employee ID '" + employeeId + "' is already registered.";
            }
            return "Database Error during registration: " + e.getMessage();
        }
    }

    // Authenticates a user against MySQL using salted hash verification and role matching
    public static UserAccount authenticateUser(String usernameOrEmail, String password, String expectedRole) {
        if (!DatabaseConnection.isAvailable()) {
            return null;
        }

        String sql = "SELECT u.user_id, u.full_name, u.email, u.username, u.password_hash, u.salt, u.role, u.account_status, "
                + "w.ward_number, m.team_id "
                + "FROM users u "
                + "LEFT JOIN ward_officer_profiles w ON u.user_id = w.user_id "
                + "LEFT JOIN maintenance_teams m ON u.user_id = m.user_id "
                + "WHERE (u.username = ? OR u.email = ?) AND u.role = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, usernameOrEmail);
            ps.setString(2, usernameOrEmail);
            ps.setString(3, expectedRole);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String storedSalt = rs.getString("salt");
                    String accountStatus = rs.getString("account_status");

                    if (!"ACTIVE".equals(accountStatus)) {
                        return null; // Pending approval or inactive
                    }

                    // Verify password with PBKDF2 constant-time check
                    boolean passwordMatches = PasswordHasher.verify(password, storedHash, storedSalt);
                    if (passwordMatches) {
                        String userId      = rs.getString("username"); // Use username as display userId
                        String displayName = rs.getString("full_name");
                        String role        = rs.getString("role");
                        String teamId      = rs.getString("team_id");
                        String wardNumber  = rs.getString("ward_number");

                        // For maintenance, if team_id not linked, fallback to username
                        if (UserAccount.ROLE_MAINTENANCE.equals(role) && (teamId == null || teamId.isEmpty())) {
                            teamId = userId;
                        }

                        return new UserAccount(userId, displayName, role, teamId, wardNumber);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during authentication: " + e.getMessage());
        }
        return null;
    }

    // =====================================================================
    // 2. ROAD DAMAGE REPORT OPERATIONS
    // =====================================================================

    // Inserts a new damage report into MySQL and logs submission in report_history
    public static void saveReport(RoadDamage rd) {
        if (!DatabaseConnection.isAvailable()) return;

        String sqlReport = "INSERT INTO road_damage_reports (report_id, reporting_user_id, location_id, "
                + "ward_number, road_name, landmark, damage_type, severity_rank, accident_risk_rank, "
                + "road_importance_rank, traffic_rank, field_observations, required_equipment, status, "
                + "report_order, submitted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) "
                + "ON DUPLICATE KEY UPDATE status = VALUES(status), severity_rank = VALUES(severity_rank), "
                + "accident_risk_rank = VALUES(accident_risk_rank), road_importance_rank = VALUES(road_importance_rank), "
                + "traffic_rank = VALUES(traffic_rank), field_observations = VALUES(field_observations)";

        String sqlHistory = "INSERT INTO report_history (report_id, changed_by, previous_status, new_status, "
                + "change_notes, changed_at) VALUES (?, ?, NULL, ?, 'Initial report logged', NOW())";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sqlReport)) {
                ps.setString(1, rd.getReportId());
                ps.setString(2, rd.getSubmittedBy());
                ps.setString(3, rd.getLocationId());
                ps.setString(4, rd.getWardNumber());
                ps.setString(5, rd.getRoadName());
                ps.setString(6, rd.getLandmark());
                ps.setString(7, rd.getDamageType());
                ps.setInt(8, rd.getSeverityRank());
                ps.setInt(9, rd.getAccidentRiskRank());
                ps.setInt(10, rd.getRoadImportanceRank());
                ps.setInt(11, rd.getTrafficRank());
                ps.setString(12, rd.getFieldObservations());
                ps.setString(13, rd.getRequiredEquipment());
                ps.setString(14, rd.getStatus());
                ps.setInt(15, rd.getReportOrder());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlHistory)) {
                ps.setString(1, rd.getReportId());
                ps.setString(2, rd.getSubmittedBy());
                ps.setString(3, rd.getStatus());
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            System.err.println("Error saving report to MySQL: " + e.getMessage());
        }
    }

    // Updates a report's status and logs state change in report_history
    public static void updateReportStatus(String reportId, String oldStatus, String newStatus,
                                          String changedBy, String notes) {
        if (!DatabaseConnection.isAvailable()) return;

        String sqlUpdate = "UPDATE road_damage_reports SET status = ? WHERE report_id = ?";
        String sqlHistory = "INSERT INTO report_history (report_id, changed_by, previous_status, new_status, "
                + "change_notes, changed_at) VALUES (?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sqlUpdate)) {
                ps.setString(1, newStatus);
                ps.setString(2, reportId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(sqlHistory)) {
                ps.setString(1, reportId);
                ps.setString(2, changedBy != null ? changedBy : "Engineering");
                ps.setString(3, oldStatus);
                ps.setString(4, newStatus);
                ps.setString(5, notes);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            System.err.println("Error updating report status in MySQL: " + e.getMessage());
        }
    }

    // =====================================================================
    // 3. TRANSACTIONAL REPAIR ASSIGNMENT & COMPLETION
    // =====================================================================

    // ACID transaction: Allocates a verified report to an available team
    public static boolean assignRepairTransaction(String reportId, String teamId, String assignedBy) {
        if (!DatabaseConnection.isAvailable()) return true;

        String sqlAssign = "INSERT INTO repair_assignments (report_id, team_id, assigned_by, assigned_at, "
                + "assignment_status) VALUES (?, ?, ?, NOW(), 'ASSIGNED')";
        String sqlUpdateReport = "UPDATE road_damage_reports SET status = 'ASSIGNED', assigned_team_id = ? "
                + "WHERE report_id = ? AND status = 'VERIFIED'";
        String sqlUpdateTeam = "UPDATE maintenance_teams SET availability_status = 'ASSIGNED' "
                + "WHERE team_id = ? AND availability_status = 'AVAILABLE'";
        String sqlHistory = "INSERT INTO report_history (report_id, changed_by, previous_status, new_status, "
                + "change_notes, changed_at) VALUES (?, ?, 'VERIFIED', 'ASSIGNED', ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false); // Begin transaction

            // 1. Update report status to ASSIGNED (only if currently VERIFIED)
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdateReport)) {
                ps.setString(1, teamId);
                ps.setString(2, reportId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // 2. Update team availability (only if currently AVAILABLE)
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdateTeam)) {
                ps.setString(1, teamId);
                int rows = ps.executeUpdate();
                if (rows == 0) {
                    conn.rollback();
                    return false;
                }
            }

            // 3. Create assignment record
            try (PreparedStatement ps = conn.prepareStatement(sqlAssign)) {
                ps.setString(1, reportId);
                ps.setString(2, teamId);
                ps.setString(3, assignedBy != null ? assignedBy : "Engineering");
                ps.executeUpdate();
            }

            // 4. Log in audit history
            try (PreparedStatement ps = conn.prepareStatement(sqlHistory)) {
                ps.setString(1, reportId);
                ps.setString(2, assignedBy != null ? assignedBy : "Engineering");
                ps.setString(3, "Assigned to squad " + teamId);
                ps.executeUpdate();
            }

            conn.commit(); // Success: Commit all steps atomically
            return true;

        } catch (SQLException e) {
            System.err.println("Error during assignRepairTransaction: " + e.getMessage());
            return false;
        }
    }

    // ACID transaction: Completes repair, releases team, updates location & logs history
    public static boolean completeRepairTransaction(String reportId, String teamId, String note, String newLocationId) {
        if (!DatabaseConnection.isAvailable()) return true;

        String sqlUpdateAssign = "UPDATE repair_assignments SET assignment_status = 'COMPLETED', "
                + "completion_notes = ?, completed_at = NOW() WHERE report_id = ? AND team_id = ? AND assignment_status = 'IN_PROGRESS'";
        String sqlUpdateReport = "UPDATE road_damage_reports SET status = 'COMPLETED', assigned_team_id = NULL "
                + "WHERE report_id = ?";
        String sqlUpdateTeam = "UPDATE maintenance_teams SET availability_status = 'AVAILABLE', current_location_id = ? "
                + "WHERE team_id = ?";
        String sqlHistory = "INSERT INTO report_history (report_id, changed_by, previous_status, new_status, "
                + "change_notes, changed_at) VALUES (?, ?, 'IN_PROGRESS', 'COMPLETED', ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false); // Begin transaction

            // 1. Update assignment
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdateAssign)) {
                ps.setString(1, note);
                ps.setString(2, reportId);
                ps.setString(3, teamId);
                ps.executeUpdate();
            }

            // 2. Update report status
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdateReport)) {
                ps.setString(1, reportId);
                ps.executeUpdate();
            }

            // 3. Release squad and update current location
            try (PreparedStatement ps = conn.prepareStatement(sqlUpdateTeam)) {
                ps.setString(1, newLocationId != null ? newLocationId : "L01");
                ps.setString(2, teamId);
                ps.executeUpdate();
            }

            // 4. Record audit history
            try (PreparedStatement ps = conn.prepareStatement(sqlHistory)) {
                ps.setString(1, reportId);
                ps.setString(2, teamId);
                ps.setString(3, "Work finished: " + note);
                ps.executeUpdate();
            }

            conn.commit(); // Atomic commit
            return true;

        } catch (SQLException e) {
            System.err.println("Error during completeRepairTransaction: " + e.getMessage());
            return false;
        }
    }

    // Updates road connection open/closed state in MySQL
    public static void setRoadOpen(String src, String dst, boolean isOpen) {
        if (!DatabaseConnection.isAvailable()) return;
        String sql = "UPDATE road_connections SET is_open = ? WHERE "
                + "(source_location_id = ? AND destination_location_id = ?) OR "
                + "(source_location_id = ? AND destination_location_id = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isOpen);
            ps.setString(2, src);
            ps.setString(3, dst);
            ps.setString(4, dst);
            ps.setString(5, src);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating road connection in MySQL: " + e.getMessage());
        }
    }
}
