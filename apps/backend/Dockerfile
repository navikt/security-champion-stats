FROM gcr.io/distroless/java21-debian12

USER nonroot

WORKDIR /app

COPY build/install/app/ /app/

ENTRYPOINT ["java", "-cp", "/app/lib/*", "navikt.appsec.securitychampionstats.AppStarterKt"]