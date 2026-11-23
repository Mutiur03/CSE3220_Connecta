FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# GeoIP database for the session location. Blank GEOIP_DB_URL downloads DB-IP City Lite (CC BY 4.0, db-ip.com);
# a link uses your own file; "none" skips it. A failed download only means no location, the build goes on.
ARG GEOIP_DB_URL=
COPY fetch-geoip.sh /tmp/fetch-geoip.sh
RUN mkdir -p /opt/geoip && sh /tmp/fetch-geoip.sh /opt/geoip/GeoLite2-City.mmdb "$GEOIP_DB_URL" && rm /tmp/fetch-geoip.sh
USER app
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
