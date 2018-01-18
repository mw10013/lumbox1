FROM java:8-alpine
MAINTAINER Your Name <you@example.com>

ADD target/uberjar/lumbox1.jar /lumbox1/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/lumbox1/app.jar"]
