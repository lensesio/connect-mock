FROM java:8

ADD build/libs/connect-mock-0.6.0-all.jar /

CMD "java" "-jar" "connect-mock-0.6.0-all.jar"
