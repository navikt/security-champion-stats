FROM gcr.io/distroless/java21-debian12

USER nonroot

COPY ./build/libs/*.jar /app/

WORKDIR /app

CMD ["java", "-cp", "/app/lib/*", "navikt.appsec.securitychampionstats.AppStarterKt"]