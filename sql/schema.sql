-- Fitness Center database schema (SQLite)
-- Dates are stored as ISO text: 'YYYY-MM-DD' and 'YYYY-MM-DDTHH:MM',
-- so they sort and compare correctly as plain strings.

-- ---------------------------------------------------------------- People

CREATE TABLE IF NOT EXISTS membership_plans (
    plan_id      INTEGER PRIMARY KEY,
    name         TEXT    NOT NULL UNIQUE,
    monthly_fee  REAL    NOT NULL CHECK (monthly_fee >= 0),
    description  TEXT
);

CREATE TABLE IF NOT EXISTS members (
    member_id          INTEGER PRIMARY KEY,
    first_name         TEXT    NOT NULL,
    last_name          TEXT    NOT NULL,
    email              TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    phone              TEXT,
    date_of_birth      TEXT,
    emergency_contact  TEXT,
    plan_id            INTEGER NOT NULL REFERENCES membership_plans (plan_id),
    join_date          TEXT    NOT NULL DEFAULT (date('now', 'localtime')),
    status             TEXT    NOT NULL DEFAULT 'active'
                       CHECK (status IN ('active', 'frozen', 'cancelled'))
);

CREATE TABLE IF NOT EXISTS trainers (
    trainer_id  INTEGER PRIMARY KEY,
    first_name  TEXT NOT NULL,
    last_name   TEXT NOT NULL,
    email       TEXT NOT NULL UNIQUE COLLATE NOCASE,
    phone       TEXT,
    specialty   TEXT
);

-- ------------------------------------------------------------ Facilities

CREATE TABLE IF NOT EXISTS rooms (
    room_id    INTEGER PRIMARY KEY,
    name       TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    room_type  TEXT    NOT NULL,
    capacity   INTEGER NOT NULL CHECK (capacity > 0)
);

CREATE TABLE IF NOT EXISTS equipment (
    equipment_id       INTEGER PRIMARY KEY,
    name               TEXT NOT NULL,
    category           TEXT NOT NULL,
    serial_number      TEXT UNIQUE,
    room_id            INTEGER REFERENCES rooms (room_id) ON DELETE SET NULL,
    purchase_date      TEXT,
    last_service_date  TEXT,
    status             TEXT NOT NULL DEFAULT 'operational'
                       CHECK (status IN ('operational', 'out_of_order', 'under_maintenance', 'retired'))
);

CREATE TABLE IF NOT EXISTS maintenance_logs (
    log_id          INTEGER PRIMARY KEY,
    equipment_id    INTEGER NOT NULL REFERENCES equipment (equipment_id) ON DELETE CASCADE,
    type            TEXT    NOT NULL DEFAULT 'repair' CHECK (type IN ('repair', 'routine')),
    description     TEXT    NOT NULL,
    status          TEXT    NOT NULL DEFAULT 'open'
                    CHECK (status IN ('open', 'in_progress', 'completed')),
    reported_date   TEXT    NOT NULL DEFAULT (date('now', 'localtime')),
    completed_date  TEXT,
    technician      TEXT,
    cost            REAL CHECK (cost IS NULL OR cost >= 0),
    notes           TEXT,
    CHECK (completed_date IS NULL OR completed_date >= reported_date)
);

-- ------------------------------------------------------------ Scheduling

CREATE TABLE IF NOT EXISTS classes (
    class_id     INTEGER PRIMARY KEY,
    name         TEXT    NOT NULL,
    description  TEXT,
    trainer_id   INTEGER NOT NULL REFERENCES trainers (trainer_id),
    room_id      INTEGER NOT NULL REFERENCES rooms (room_id),
    start_time   TEXT    NOT NULL,
    end_time     TEXT    NOT NULL,
    capacity     INTEGER NOT NULL CHECK (capacity > 0),
    status       TEXT    NOT NULL DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'cancelled')),
    CHECK (end_time > start_time)
);

CREATE TABLE IF NOT EXISTS class_enrollments (
    class_id     INTEGER NOT NULL REFERENCES classes (class_id) ON DELETE CASCADE,
    member_id    INTEGER NOT NULL REFERENCES members (member_id) ON DELETE CASCADE,
    enrolled_at  TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M', 'now', 'localtime')),
    PRIMARY KEY (class_id, member_id)
);

CREATE TABLE IF NOT EXISTS training_sessions (
    session_id  INTEGER PRIMARY KEY,
    member_id   INTEGER NOT NULL REFERENCES members (member_id) ON DELETE CASCADE,
    trainer_id  INTEGER NOT NULL REFERENCES trainers (trainer_id),
    room_id     INTEGER REFERENCES rooms (room_id),
    start_time  TEXT    NOT NULL,
    end_time    TEXT    NOT NULL,
    status      TEXT    NOT NULL DEFAULT 'scheduled'
                CHECK (status IN ('scheduled', 'completed', 'cancelled')),
    notes       TEXT,
    CHECK (end_time > start_time)
);

CREATE TABLE IF NOT EXISTS room_bookings (
    booking_id  INTEGER PRIMARY KEY,
    room_id     INTEGER NOT NULL REFERENCES rooms (room_id),
    member_id   INTEGER REFERENCES members (member_id) ON DELETE CASCADE,
    purpose     TEXT    NOT NULL,
    start_time  TEXT    NOT NULL,
    end_time    TEXT    NOT NULL,
    CHECK (end_time > start_time)
);

-- One place that answers "what is happening where, when, and with which trainer".
-- Classes, personal training and ad-hoc bookings all compete for the same rooms,
-- so every double-booking check and every calendar screen reads from this view.
CREATE VIEW IF NOT EXISTS schedule AS
    SELECT 'class' AS kind, c.class_id AS ref_id, c.name AS title,
           c.room_id, c.trainer_id, c.start_time, c.end_time
    FROM classes c
    WHERE c.status <> 'cancelled'
    UNION ALL
    SELECT 'training', s.session_id, 'Personal training: ' || m.first_name || ' ' || m.last_name,
           s.room_id, s.trainer_id, s.start_time, s.end_time
    FROM training_sessions s
    JOIN members m ON m.member_id = s.member_id
    WHERE s.status <> 'cancelled'
    UNION ALL
    SELECT 'booking', b.booking_id, b.purpose,
           b.room_id, NULL, b.start_time, b.end_time
    FROM room_bookings b;

CREATE INDEX IF NOT EXISTS idx_members_plan        ON members (plan_id);
CREATE INDEX IF NOT EXISTS idx_classes_room_time   ON classes (room_id, start_time);
CREATE INDEX IF NOT EXISTS idx_classes_trainer     ON classes (trainer_id, start_time);
CREATE INDEX IF NOT EXISTS idx_enrollments_member  ON class_enrollments (member_id);
CREATE INDEX IF NOT EXISTS idx_sessions_trainer    ON training_sessions (trainer_id, start_time);
CREATE INDEX IF NOT EXISTS idx_sessions_room       ON training_sessions (room_id, start_time);
CREATE INDEX IF NOT EXISTS idx_sessions_member     ON training_sessions (member_id);
CREATE INDEX IF NOT EXISTS idx_bookings_room_time  ON room_bookings (room_id, start_time);
CREATE INDEX IF NOT EXISTS idx_equipment_room      ON equipment (room_id);
CREATE INDEX IF NOT EXISTS idx_maintenance_equip   ON maintenance_logs (equipment_id, status);
