FROM gcr.io/distroless/java21-debian12

COPY server/build/libs/*.jar /security-champions-stats/

WORKDIR /security-champions-stats

CMD ["security-champions-stats.jar"]