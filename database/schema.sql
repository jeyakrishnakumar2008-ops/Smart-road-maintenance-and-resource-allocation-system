-- =====================================================================
-- DATABASE SCHEMA: Smart Municipal Road Maintenance & Resource Allocation
-- Database Name: smart_municipal_maintenance
-- Municipality: Senthamizh Nagar
-- Storage Engine: InnoDB (supports ACID transactions and foreign keys)
-- Charset: utf8mb4 (supports full Unicode)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS smart_municipal_maintenance
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE smart_municipal_maintenance;

-- ---------------------------------------------------------------------
-- 1. Table: users
-- Stores all registered user accounts for all three municipal roles.
-- Passwords are NEVER stored in plaintext (PBKDF2 salted hashes only).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id         VARCHAR(50)   NOT NULL,
    full_name       VARCHAR(100)  NOT NULL,
    employee_id     VARCHAR(50)   NULL,
    email           VARCHAR(100)  NOT NULL,
    username        VARCHAR(50)   NOT NULL,
    password_hash   VARCHAR(255)  NOT NULL,
    salt            VARCHAR(64)   NOT NULL,
    role            ENUM('WARD_OFFICER', 'ENGINEERING', 'MAINTENANCE') NOT NULL,
    account_status  ENUM('PENDING_APPROVAL', 'ACTIVE', 'INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email),
    UNIQUE KEY uq_users_employee_id (employee_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 2. Table: ward_officer_profiles
-- Profile details specific to Ward Officers (assigned ward number).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ward_officer_profiles (
    user_id     VARCHAR(50) NOT NULL,
    ward_number VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_ward_profile_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 3. Table: engineering_profiles
-- Profile details specific to Engineering Section officers.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS engineering_profiles (
    user_id         VARCHAR(50)  NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_eng_profile_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 4. Table: road_locations
-- 15 surveyed landmark locations across Senthamizh Nagar.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS road_locations (
    location_id   VARCHAR(10)  NOT NULL,
    location_name VARCHAR(100) NOT NULL,
    description   VARCHAR(255) NULL,
    PRIMARY KEY (location_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 5. Table: maintenance_teams
-- Road maintenance squads (Alpha, Beta, Gamma) stationed at locations.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS maintenance_teams (
    team_id             VARCHAR(20)  NOT NULL,
    user_id             VARCHAR(50)  NULL,
    team_name           VARCHAR(100) NOT NULL,
    current_location_id VARCHAR(10)  NOT NULL,
    availability_status ENUM('AVAILABLE', 'ASSIGNED', 'IN_PROGRESS', 'OFF_DUTY') NOT NULL DEFAULT 'AVAILABLE',
    PRIMARY KEY (team_id),
    KEY idx_maint_user (user_id),
    CONSTRAINT fk_maint_location FOREIGN KEY (current_location_id)
        REFERENCES road_locations (location_id) ON UPDATE CASCADE,
    CONSTRAINT fk_maint_user FOREIGN KEY (user_id)
        REFERENCES users (user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 6. Table: equipment
-- Catalog of repair machinery and tools (Pothole Filler, Road Roller, etc.).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS equipment (
    equipment_id   VARCHAR(30)  NOT NULL,
    equipment_name VARCHAR(100) NOT NULL,
    description    VARCHAR(255) NULL,
    PRIMARY KEY (equipment_id),
    UNIQUE KEY uq_equipment_name (equipment_name)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 7. Table: team_equipment
-- Many-to-many relationship mapping maintenance teams to their equipment.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS team_equipment (
    team_id      VARCHAR(20) NOT NULL,
    equipment_id VARCHAR(30) NOT NULL,
    PRIMARY KEY (team_id, equipment_id),
    CONSTRAINT fk_te_team FOREIGN KEY (team_id)
        REFERENCES maintenance_teams (team_id) ON DELETE CASCADE,
    CONSTRAINT fk_te_equip FOREIGN KEY (equipment_id)
        REFERENCES equipment (equipment_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 8. Table: road_connections
-- Weighted directed edges representing road segments with travel times.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS road_connections (
    connection_id          INT AUTO_INCREMENT NOT NULL,
    source_location_id      VARCHAR(10) NOT NULL,
    destination_location_id VARCHAR(10) NOT NULL,
    travel_time            INT NOT NULL CHECK (travel_time > 0),
    is_open                BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (connection_id),
    UNIQUE KEY uq_road_edge (source_location_id, destination_location_id),
    CONSTRAINT fk_road_src FOREIGN KEY (source_location_id)
        REFERENCES road_locations (location_id) ON DELETE CASCADE,
    CONSTRAINT fk_road_dst FOREIGN KEY (destination_location_id)
        REFERENCES road_locations (location_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 9. Table: road_damage_reports
-- Road damage inspection reports logged by Ward Officers.
-- Includes 4 priority ranks (1-3) and engineering verification tracking.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS road_damage_reports (
    report_id            VARCHAR(30)  NOT NULL,
    reporting_user_id    VARCHAR(50)  NOT NULL,
    location_id          VARCHAR(10)  NOT NULL,
    ward_number          VARCHAR(20)  NOT NULL,
    road_name            VARCHAR(150) NOT NULL,
    landmark             VARCHAR(150) NULL,
    damage_type          VARCHAR(50)  NOT NULL,
    severity_rank        TINYINT NOT NULL CHECK (severity_rank BETWEEN 1 AND 3),
    accident_risk_rank   TINYINT NOT NULL CHECK (accident_risk_rank BETWEEN 1 AND 3),
    road_importance_rank TINYINT NOT NULL CHECK (road_importance_rank BETWEEN 1 AND 3),
    traffic_rank         TINYINT NOT NULL CHECK (traffic_rank BETWEEN 1 AND 3),
    field_observations   TEXT         NULL,
    required_equipment   VARCHAR(100) NOT NULL,
    status               ENUM('SUBMITTED', 'RETURNED', 'VERIFIED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED')
                         NOT NULL DEFAULT 'SUBMITTED',
    report_order         INT NOT NULL,
    submitted_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_by          VARCHAR(50)  NULL,
    verified_at          DATETIME     NULL,
    engineering_notes    TEXT         NULL,
    assigned_team_id     VARCHAR(20)  NULL,
    PRIMARY KEY (report_id),
    KEY idx_reports_status (status),
    KEY idx_reports_location (location_id),
    CONSTRAINT fk_report_user FOREIGN KEY (reporting_user_id)
        REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_report_loc FOREIGN KEY (location_id)
        REFERENCES road_locations (location_id) ON UPDATE CASCADE,
    CONSTRAINT fk_report_team FOREIGN KEY (assigned_team_id)
        REFERENCES maintenance_teams (team_id) ON DELETE SET NULL,
    CONSTRAINT fk_report_verifier FOREIGN KEY (verified_by)
        REFERENCES users (user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 10. Table: repair_assignments
-- Work allocations dispatched by Engineering to maintenance teams.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS repair_assignments (
    assignment_id     INT AUTO_INCREMENT NOT NULL,
    report_id         VARCHAR(30) NOT NULL,
    team_id           VARCHAR(20) NOT NULL,
    assigned_by       VARCHAR(50) NOT NULL,
    assigned_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at        DATETIME    NULL,
    completed_at      DATETIME    NULL,
    completion_notes  TEXT        NULL,
    repair_outcome    VARCHAR(100) NULL,
    assignment_status ENUM('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
                      NOT NULL DEFAULT 'ASSIGNED',
    PRIMARY KEY (assignment_id),
    KEY idx_assign_report (report_id),
    KEY idx_assign_team (team_id),
    CONSTRAINT fk_assign_report FOREIGN KEY (report_id)
        REFERENCES road_damage_reports (report_id) ON DELETE CASCADE,
    CONSTRAINT fk_assign_team FOREIGN KEY (team_id)
        REFERENCES maintenance_teams (team_id) ON UPDATE CASCADE,
    CONSTRAINT fk_assign_user FOREIGN KEY (assigned_by)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 11. Table: report_history
-- Audit log recording state transitions for full municipal traceability.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS report_history (
    history_id      INT AUTO_INCREMENT NOT NULL,
    report_id       VARCHAR(30) NOT NULL,
    changed_by      VARCHAR(50) NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status      VARCHAR(30) NOT NULL,
    change_notes    TEXT        NULL,
    changed_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (history_id),
    KEY idx_history_report (report_id),
    CONSTRAINT fk_history_report FOREIGN KEY (report_id)
        REFERENCES road_damage_reports (report_id) ON DELETE CASCADE
) ENGINE=InnoDB;
