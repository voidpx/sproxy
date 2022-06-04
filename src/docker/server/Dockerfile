FROM openjdk:17.0.2

ARG sproxy_jar
COPY ${sproxy_jar} /sp.jar
COPY ts_authorized_keys ts.p12 /

ENTRYPOINT ["/usr/bin/java", "-jar", "/sp.jar", "server"]
CMD ["-h", "0.0.0.0", "-p", "9999"]
