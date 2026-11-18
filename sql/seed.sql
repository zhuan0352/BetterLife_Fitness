-- Sample data, loaded once when the database is first created.
-- Schedule times are relative to today so the demo always looks current.

INSERT INTO membership_plans (plan_id, name, monthly_fee, description) VALUES
    (1, 'Basic',   29.99, 'Gym floor access during staffed hours'),
    (2, 'Premium', 49.99, 'Gym floor, all group classes and room booking'),
    (3, 'Student', 19.99, 'Basic access with a valid student ID'),
    (4, 'Family',  89.99, 'Premium access for up to four people');

INSERT INTO members (member_id, first_name, last_name, email, phone, date_of_birth, emergency_contact, plan_id, join_date, status) VALUES
    (1,  'Ava',     'Thompson', 'ava.thompson@example.com',  '555-0101', '1992-04-18', 'Mark Thompson 555-0191', 2, date('now', 'localtime', '-420 days'), 'active'),
    (2,  'Liam',    'Nguyen',   'liam.nguyen@example.com',   '555-0102', '1988-11-02', 'Hoa Nguyen 555-0192',    2, date('now', 'localtime', '-300 days'), 'active'),
    (3,  'Priya',   'Patel',    'priya.patel@example.com',   '555-0103', '1995-07-23', 'Raj Patel 555-0193',     2, date('now', 'localtime', '-210 days'), 'active'),
    (4,  'Noah',    'Garcia',   'noah.garcia@example.com',   '555-0104', '2001-01-30', 'Elena Garcia 555-0194',  3, date('now', 'localtime', '-150 days'), 'active'),
    (5,  'Emma',    'Schmidt',  'emma.schmidt@example.com',  '555-0105', '1979-09-12', 'Jonas Schmidt 555-0195', 1, date('now', 'localtime', '-600 days'), 'active'),
    (6,  'Jamal',   'Williams', 'jamal.williams@example.com','555-0106', '1990-03-05', 'Tia Williams 555-0196',  2, date('now', 'localtime', '-95 days'),  'active'),
    (7,  'Sophie',  'Martin',   'sophie.martin@example.com', '555-0107', '1998-12-14', 'Luc Martin 555-0197',    4, date('now', 'localtime', '-60 days'),  'active'),
    (8,  'Kenji',   'Sato',     'kenji.sato@example.com',    '555-0108', '1985-06-27', 'Yuki Sato 555-0198',     1, date('now', 'localtime', '-45 days'),  'active'),
    (9,  'Olivia',  'Brown',    'olivia.brown@example.com',  '555-0109', '2003-08-09', 'Dana Brown 555-0199',    3, date('now', 'localtime', '-30 days'),  'frozen'),
    (10, 'Mateo',   'Rossi',    'mateo.rossi@example.com',   '555-0110', '1975-02-21', 'Carla Rossi 555-0190',   1, date('now', 'localtime', '-800 days'), 'cancelled'),
    (11, 'Hannah',  'Kim',      'hannah.kim@example.com',    '555-0111', '1993-10-03', 'Daniel Kim 555-0189',    2, date('now', 'localtime', '-12 days'),  'active'),
    (12, 'Ethan',   'O''Brien', 'ethan.obrien@example.com',  '555-0112', '1987-05-16', 'Maeve O''Brien 555-0188',2, date('now', 'localtime', '-5 days'),   'active');

INSERT INTO trainers (trainer_id, first_name, last_name, email, phone, specialty) VALUES
    (1, 'Maya',   'Chen',    'maya.chen@fitcenter.example',    '555-0201', 'Yoga & Pilates'),
    (2, 'Marcus', 'Johnson', 'marcus.johnson@fitcenter.example','555-0202', 'Strength & Conditioning'),
    (3, 'Sofia',  'Ramirez', 'sofia.ramirez@fitcenter.example','555-0203', 'Cycling & HIIT'),
    (4, 'Daniel', 'Okafor',  'daniel.okafor@fitcenter.example','555-0204', 'Boxing & Functional Training');

INSERT INTO rooms (room_id, name, room_type, capacity) VALUES
    (1, 'Studio A',           'Group studio',   25),
    (2, 'Studio B',           'Group studio',   15),
    (3, 'Spin Room',          'Cycling studio', 20),
    (4, 'Weight Room',        'Gym floor',      40),
    (5, 'PT Room',            'Private training', 4),
    (6, 'Multipurpose Court', 'Court',          30);

INSERT INTO classes (class_id, name, description, trainer_id, room_id, start_time, end_time, capacity, status) VALUES
    (1, 'HIIT Blast',          'High-intensity intervals, all levels',  3, 1, strftime('%Y-%m-%dT12:00', 'now', 'localtime', '-1 day'),  strftime('%Y-%m-%dT12:45', 'now', 'localtime', '-1 day'),  25, 'scheduled'),
    (2, 'Morning Yoga',        'Gentle vinyasa flow to start the day',  1, 1, strftime('%Y-%m-%dT07:00', 'now', 'localtime'),            strftime('%Y-%m-%dT08:00', 'now', 'localtime'),            20, 'scheduled'),
    (3, 'Spin Express',        '45 minutes of climbs and sprints',      3, 3, strftime('%Y-%m-%dT18:00', 'now', 'localtime'),            strftime('%Y-%m-%dT18:45', 'now', 'localtime'),            20, 'scheduled'),
    (4, 'HIIT Blast',          'High-intensity intervals, all levels',  3, 1, strftime('%Y-%m-%dT12:00', 'now', 'localtime', '+1 day'),  strftime('%Y-%m-%dT12:45', 'now', 'localtime', '+1 day'),  25, 'scheduled'),
    (5, 'Pilates Core',        'Mat pilates focused on core strength',  1, 2, strftime('%Y-%m-%dT17:30', 'now', 'localtime', '+1 day'),  strftime('%Y-%m-%dT18:30', 'now', 'localtime', '+1 day'),  4,  'scheduled'),
    (6, 'Strength 101',        'Barbell basics for beginners',          2, 4, strftime('%Y-%m-%dT10:00', 'now', 'localtime', '+2 days'), strftime('%Y-%m-%dT11:00', 'now', 'localtime', '+2 days'), 12, 'scheduled'),
    (7, 'Boxing Fundamentals', 'Footwork, combinations and bag work',   4, 6, strftime('%Y-%m-%dT19:00', 'now', 'localtime', '+2 days'), strftime('%Y-%m-%dT20:00', 'now', 'localtime', '+2 days'), 16, 'scheduled'),
    (8, 'Morning Yoga',        'Gentle vinyasa flow to start the day',  1, 1, strftime('%Y-%m-%dT07:00', 'now', 'localtime', '+3 days'), strftime('%Y-%m-%dT08:00', 'now', 'localtime', '+3 days'), 20, 'scheduled'),
    (9, 'Spin Express',        '45 minutes of climbs and sprints',      3, 3, strftime('%Y-%m-%dT18:00', 'now', 'localtime', '+4 days'), strftime('%Y-%m-%dT18:45', 'now', 'localtime', '+4 days'), 20, 'scheduled');

INSERT INTO class_enrollments (class_id, member_id) VALUES
    (1, 1), (1, 3), (1, 6),
    (2, 1), (2, 3), (2, 7), (2, 11),
    (3, 2), (3, 6), (3, 12),
    (4, 1), (4, 2), (4, 6), (4, 11), (4, 12),
    (5, 3), (5, 7), (5, 11), (5, 1),
    (6, 4), (6, 8), (6, 12),
    (7, 2), (7, 6), (7, 4),
    (8, 3), (8, 7),
    (9, 2);

INSERT INTO training_sessions (session_id, member_id, trainer_id, room_id, start_time, end_time, status, notes) VALUES
    (1, 6, 2, 5, strftime('%Y-%m-%dT16:00', 'now', 'localtime', '-2 days'), strftime('%Y-%m-%dT17:00', 'now', 'localtime', '-2 days'), 'completed', 'Deadlift form check, added 10 lb'),
    (2, 1, 2, 5, strftime('%Y-%m-%dT16:00', 'now', 'localtime'),            strftime('%Y-%m-%dT17:00', 'now', 'localtime'),            'scheduled', 'Upper body, week 3 of program'),
    (3, 4, 4, 5, strftime('%Y-%m-%dT09:00', 'now', 'localtime', '+1 day'),  strftime('%Y-%m-%dT10:00', 'now', 'localtime', '+1 day'),  'scheduled', 'Intro boxing assessment'),
    (4, 2, 1, 5, strftime('%Y-%m-%dT14:00', 'now', 'localtime', '+2 days'), strftime('%Y-%m-%dT15:00', 'now', 'localtime', '+2 days'), 'scheduled', 'Mobility work for lower back'),
    (5, 11, 3, NULL, strftime('%Y-%m-%dT08:00', 'now', 'localtime', '+3 days'), strftime('%Y-%m-%dT08:45', 'now', 'localtime', '+3 days'), 'scheduled', 'Outdoor run coaching');

INSERT INTO room_bookings (booking_id, room_id, member_id, purpose, start_time, end_time) VALUES
    (1, 6, 5,    'Pickup basketball',        strftime('%Y-%m-%dT20:00', 'now', 'localtime'),            strftime('%Y-%m-%dT21:00', 'now', 'localtime')),
    (2, 6, NULL, 'Corporate wellness event', strftime('%Y-%m-%dT15:00', 'now', 'localtime', '+1 day'),  strftime('%Y-%m-%dT17:00', 'now', 'localtime', '+1 day')),
    (3, 2, 3,    'Private stretching group', strftime('%Y-%m-%dT10:00', 'now', 'localtime', '+3 days'), strftime('%Y-%m-%dT11:00', 'now', 'localtime', '+3 days'));

INSERT INTO equipment (equipment_id, name, category, serial_number, room_id, purchase_date, last_service_date, status) VALUES
    (1,  'Treadmill #1',       'Cardio',   'TM-2291-A', 4, '2023-02-10', date('now', 'localtime', '-70 days'), 'operational'),
    (2,  'Treadmill #2',       'Cardio',   'TM-2291-B', 4, '2023-02-10', date('now', 'localtime', '-70 days'), 'out_of_order'),
    (3,  'Elliptical #1',      'Cardio',   'EL-1047',   4, '2022-08-01', date('now', 'localtime', '-30 days'), 'operational'),
    (4,  'Rowing Machine',     'Cardio',   'RW-5530',   4, '2024-01-15', NULL,                                 'operational'),
    (5,  'Spin Bike #7',       'Cardio',   'SB-0707',   3, '2023-06-20', NULL,                                 'under_maintenance'),
    (6,  'Squat Rack A',       'Strength', 'SR-3301',   4, '2021-11-05', NULL,                                 'operational'),
    (7,  'Cable Crossover',    'Strength', 'CC-8812',   4, '2021-11-05', date('now', 'localtime', '-10 days'), 'operational'),
    (8,  'Leg Press',          'Strength', 'LP-4420',   4, '2022-03-18', NULL,                                 'operational'),
    (9,  'Dumbbell Set 5-50',  'Free weights', NULL,    4, '2021-11-05', NULL,                                 'operational'),
    (10, 'Heavy Bag',          'Boxing',   'HB-0093',   6, '2024-05-02', NULL,                                 'operational'),
    (11, 'Yoga Mat Set (25)',  'Studio',   NULL,        1, '2024-09-12', NULL,                                 'operational'),
    (12, 'Stair Climber',      'Cardio',   'SC-1180',   NULL, '2019-04-22', NULL,                              'retired');

INSERT INTO maintenance_logs (log_id, equipment_id, type, description, status, reported_date, completed_date, technician, cost, notes) VALUES
    (1, 3, 'repair',  'Left pedal arm squeaking under load',   'completed',   date('now', 'localtime', '-34 days'), date('now', 'localtime', '-30 days'), 'FitFix Services', 140.00, 'Replaced bearing'),
    (2, 7, 'routine', 'Quarterly cable and pulley inspection', 'completed',   date('now', 'localtime', '-10 days'), date('now', 'localtime', '-10 days'), 'In-house',         85.00, 'Cables in good condition'),
    (3, 2, 'repair',  'Belt slipping at speeds above 8 mph',   'open',        date('now', 'localtime', '-2 days'),  NULL,                                 NULL,               NULL,  NULL),
    (4, 5, 'repair',  'Resistance knob stuck at one level',    'in_progress', date('now', 'localtime', '-4 days'),  NULL,                                 'FitFix Services',  NULL,  'Part on order'),
    (5, 8, 'routine', 'Lubricate guide rails',                 'open',        date('now', 'localtime', '-1 day'),   NULL,                                 'In-house',         NULL,  NULL)
