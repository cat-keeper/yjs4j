@echo off
chcp 65001 >nul
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dproject.build.sourceEncoding=UTF-8"
echo Using Java 21 from: %JAVA_HOME%
java -version
echo.
echo Compiling with Maven...
mvn compile -Dfile.encoding=UTF-8
