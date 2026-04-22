@echo off
cd /d "%~dp0"
echo ============================================
echo   FakeShield - Phishing Database Importer
echo ============================================
echo.

SET MYSQL_JAR=C:\Users\shiva\.m2\repository\com\mysql\mysql-connector-j\8.1.0\mysql-connector-j-8.1.0.jar

echo [1/2] Compiling...
javac -cp "%MYSQL_JAR%" PhishingImporter.java
if %errorlevel% neq 0 (
    echo ERROR: Compilation failed!
    pause
    exit /b 1
)
echo       Done!
echo.

echo [2/2] Running importer...
echo       DO NOT close this window!
echo.
java -cp ".;%MYSQL_JAR%" PhishingImporter

echo.
echo ============================================
echo   Done! Check MySQL Workbench to verify.
echo ============================================
pause
```

Save the file and double-click it again!

---

## If the JAR is not in that exact path

Open **File Explorer** and search for:
```
mysql-connector-j