@echo off
chcp 65001 >nul
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_OPTS=-Dfile.encoding=UTF-8 -Dproject.build.sourceEncoding=UTF-8"
echo Running Yjs Java Tests...
mvn exec:java -Dexec.mainClass="com.triibiotech.yjs.test.YjsTest" -Dfile.encoding=UTF-8