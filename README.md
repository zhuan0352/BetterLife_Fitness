# Fitness Center Manager

A database application for running a gym: member registration, class and personal-training
scheduling, room booking, and equipment maintenance. Staff use it through a menu-driven web
page; the data lives in a SQLite database reached from Java over JDBC.

## Run it

Requires Java 17 or newer (built and tested on Java 22). Nothing else to install.

```bash
./run.sh
```

Then open <http://localhost:8080>. The first run creates `fitness_center.db` and fills it with
sample data dated around today.

| Setting | Default | Example |
| --- | --- | --- |
| `PORT` | `8080` | `PORT=9000 ./run.sh` |
| `GYM_DB` | `fitness_center.db` | `GYM_DB=/tmp/demo.db ./run.sh` |

To start over with fresh sample data, stop the app and delete `fitness_center.db`.

## Deploy to Render (public demo link)

The repository includes a `Dockerfile` and a `render.yaml` blueprint, so Render can build and host
it with no extra configuration.

1. Push this repository to GitHub.
2. In the [Render dashboard](https://dashboard.render.com), choose **New > Blueprint**, connect
   GitHub, and pick this repository. Render reads `render.yaml` and proposes one free web service.
3. Click **Apply**. The first build takes a few minutes; the service then gets a public URL like
   `https://betterlife-fitness.onrender.com`.

After that, every push to `main` redeploys automatically.

What to expect on the free plan:

- **Cold starts.** The service sleeps after 15 minutes without visitors. The next visit takes
  about a minute to wake it, then it is fast. Opening the link yourself shortly before sharing it helps.
- **Data resets.** Free services have no persistent disk, so the database is rebuilt from the sample
  data on every restart, redeploy and wake-up. For a public demo that is a feature: whatever visitors
  change, the next person starts clean. A banner at the top of the page tells visitors this.
- **Times** are shown in the `TZ` set in `render.yaml` (America/Toronto). Change it there if needed.

Deployment settings (all already set by the `Dockerfile` and `render.yaml`):

| Variable | Value on Render | Why |
| --- | --- | --- |
| `HOST` | `0.0.0.0` | Accept outside traffic. Locally it defaults to localhost only. |
| `PORT` | set by Render | The port Render routes to. |
| `DEMO_MODE` | `true` | Shows the "live demo" banner. |
| `TZ` | `America/Toronto` | The gym's local time for "today" and the schedule. |

## The menu

| Menu item | What staff can do |
| --- | --- |
| Dashboard | Headline numbers, today's schedule, open maintenance tickets |
| Members | Register, search, edit, freeze/cancel, view a member's classes and sessions |
| Trainers | Add, edit and remove trainers |
| Classes | Schedule group classes, manage the roster (enroll / remove members) |
| Personal Training | Book one-on-one sessions, mark them completed or cancelled |
| Room Booking | Day calendar per room, ad-hoc bookings, manage the rooms themselves |
| Equipment | Inventory with location and status, report an issue |
| Maintenance | Work tickets: open, start work, complete with cost and notes |

## Rules the application enforces

- **No double-booking.** A room can hold one thing at a time, whether that is a class, a training
  session or an ad-hoc booking. A trainer can't be in two places at once either. Back-to-back
  bookings are fine.
- **Capacity.** A class can't be larger than its room, and enrollment stops when the class is full.
  The spot check and the enrollment happen in one transaction.
- **Membership status.** Only active members can be enrolled, booked for training, or given a room.
- **Maintenance drives equipment status.** An open repair ticket marks the item out of order, work in
  progress marks it under maintenance, and completing the last ticket puts it back in service and
  records the service date. Routine tickets don't take equipment off the floor until work starts.
- **Safe deletes.** Trainers and rooms that are still referenced can't be deleted. Deleting a member
  or a piece of equipment removes its dependent records (`ON DELETE CASCADE`), and the page says so
  before you confirm.

## Database

Schema: [sql/schema.sql](sql/schema.sql). Sample data: [sql/seed.sql](sql/seed.sql).

```
membership_plans 1───* members *───* classes          (through class_enrollments)
                       members 1───* training_sessions *───1 trainers
                       members 1───* room_bookings     *───1 rooms
trainers 1───* classes *───1 rooms
rooms    1───* equipment 1───* maintenance_logs
```

Ten tables, with primary and foreign keys, `UNIQUE` and `CHECK` constraints, and indexes on the
columns used for schedule lookups. The `schedule` view unions classes, training sessions and room
bookings into one calendar; every conflict check and every calendar screen reads from it, so the
"is this room free?" logic exists in exactly one place.

Dates are stored as ISO text (`YYYY-MM-DD`, `YYYY-MM-DDTHH:MM`), which sorts and compares correctly
as plain strings in SQLite.

## Project layout

```
run.sh            compile and start locally (downloads the JDBC driver if missing)
Dockerfile        container image used by Render
render.yaml       Render blueprint (free web service, health check, env vars)
sql/              schema.sql, seed.sql
src/gym/
  Main.java       entry point: opens the database, registers routes, starts the server
  Db.java         JDBC wrapper: prepared statements, transactions, rows as maps
  WebServer.java  HTTP routing on the JDK's built-in server, static files, error mapping
  Request.java    input parsing and validation
  Json.java       small JSON reader/writer
  Rules.java      shared business rules (room/trainer availability, active members)
  Members.java  Trainers.java  Classes.java  Training.java
  Rooms.java  Equipment.java  Dashboard.java     one file per menu area
web/              index.html, style.css, app.js  (the menu interface, no frameworks)
lib/              sqlite-jdbc driver (the only dependency)
```

## API

The web page talks to a JSON API under `/api`, which can also be used directly:

```bash
curl http://localhost:8080/api/members?q=kim
```

Resources: `plans`, `members`, `trainers`, `rooms`, `classes` (plus `classes/{id}/enrollments`),
`training`, `bookings`, `schedule?date=YYYY-MM-DD`, `equipment`, `maintenance`, `dashboard`.
Each supports `GET`, and where it makes sense `POST`, `PUT /{id}` and `DELETE /{id}`. Errors come
back as `{"error": "..."}` with a 4xx status.

## Scope

This is a staff tool with no login. Run locally, the server listens on `localhost` only; the public
demo is open to anyone with the link, which is why it holds only sample data and resets on restart.
All SQL uses bound parameters, the page writes database text with `textContent` (never as HTML),
request bodies are capped at 64 KB and text fields at 500 characters.
