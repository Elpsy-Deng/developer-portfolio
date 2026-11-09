ARG JAVA_IMAGE=eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
FROM ${JAVA_IMAGE}
WORKDIR /app
COPY backend/target/developer-portfolio-0.2.0.jar /app/portfolio.jar
RUN chmod 0444 /app/portfolio.jar
USER 10001:10001
ENTRYPOINT ["java", "-Xmx256m", "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=2", "-jar", "/app/portfolio.jar"]
CMD []
