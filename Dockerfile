# Build
FROM gradle:jdk21 AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
RUN gradle build --no-daemon --stacktrace -x test

# Package
FROM openjdk:21-jdk
COPY --from=build /home/gradle/src/service/build/libs/*.jar app.jar
ARG PROFILE
ENTRYPOINT ["java","-jar","-Dspring.profiles.active=${PROFILE}","app.jar"]