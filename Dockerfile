FROM java:8

ADD build/libs/connect-mock-0.5.5-all.jar /

CMD "java" "-jar" "connect-mock-0.5.5-all.jar"
