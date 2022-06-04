FROM openjdk:17.0.2

ARG sproxy_jar
COPY ${sproxy_jar} /sp.jar
COPY tc_authorized_keys tc.p12 /

ENTRYPOINT ["/usr/bin/java", "-jar", "/sp.jar", "client"]
CMD ["-h", "0.0.0.0", "-p", "8888", "-H", "localhost", "-P", "9999"]

