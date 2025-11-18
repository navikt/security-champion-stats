FROM gcr.io/distroless/java21-debian12

COPY server/build/libs/*.jar /security-champions-stats/

WORKDIR /security-champions-stats

EXPOSE 8080

CMD ["security-champions-stats.jar"]