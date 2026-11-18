# Fitness Center Manager: container image for Render (or any Docker host).

# ---- build: compile the Java sources
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY lib ./lib
COPY src ./src
RUN javac -d out -cp "lib/*" $(find src -name "*.java")

# ---- run: a smaller JRE-only image
FROM eclipse-temurin:21-jre
# tzdata lets SQLite's 'localtime' match Java's clock, so "today" is the same day for both.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/out ./out
COPY lib ./lib
COPY sql ./sql
COPY web ./web

# Listen on all interfaces so the platform's router can reach the app.
# Render injects PORT (10000 by default); 8080 is used when it doesn't.
ENV HOST=0.0.0.0 \
    PORT=8080 \
    TZ=America/Toronto \
    GYM_DB=/app/data/fitness_center.db
RUN mkdir -p /app/data
EXPOSE 8080
CMD ["java", "-XX:MaxRAMPercentage=75", "-cp", "out:lib/*", "gym.Main"]
