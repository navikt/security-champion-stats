FROM gcr.io/distroless/java21-debian12

COPY build/libs/*.jar /security-champions-stats/

WORKDIR /security-champions-stats

CMD ["security-champions-stats.jar"]