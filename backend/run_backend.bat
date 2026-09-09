@echo off
set DB_URL=jdbc:postgresql://localhost:5432/flowdesk
set DB_USERNAME=flowdesk_app
set /p DB_PASSWORD="Enter DB_PASSWORD for flowdesk_app securely: "

echo.
echo ==================================================
echo Running Maven Tests
echo ==================================================
call .\mvnw.cmd clean test
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Backend tests failed. Please check the errors above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ==================================================
echo Starting Spring Boot Application
echo ==================================================
call .\mvnw.cmd spring-boot:run
