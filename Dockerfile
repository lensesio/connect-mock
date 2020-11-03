FROM java:8

ADD build/libs/connect-mock-1.0-SNAPSHOT-all.jar /

CMD "java" "-jar" "connect-mock-1.0-SNAPSHOT-all.jar"
