@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul

where javac >nul 2>nul
if errorlevel 1 (
  echo Erro: Java JDK - javac - nao encontrado. Instale o JDK 8 ou superior, ex.: https://adoptium.net
  exit /b 1
)

if not exist out mkdir out
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 exit /b 1

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Djava.net.preferIPv4Stack=true -cp out Main %*
