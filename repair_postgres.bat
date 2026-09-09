@echo off
setlocal EnableDelayedExpansion
NET SESSION >nul 2>&1
IF %ERRORLEVEL% NEQ 0 (
    echo This script requires Administrator privileges.
    echo Please right-click this file and select "Run as administrator".
    pause
    exit /b 1
)

set PGDATADIR=C:\Program Files\PostgreSQL\18\data
set WIN_PG_SERVICE=postgresql-x64-18
set PSQL_CMD="C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -p 5432 -U postgres

echo [1] Backing up pg_hba.conf safely...
IF NOT EXIST "%PGDATADIR%\pg_hba.conf.original" (
    copy /Y "%PGDATADIR%\pg_hba.conf" "%PGDATADIR%\pg_hba.conf.original" >nul
)
copy /Y "%PGDATADIR%\pg_hba.conf" "%PGDATADIR%\pg_hba.conf.bak" >nul

echo [2] Temporarily changing local auth to trust...
powershell -Command "(Get-Content '%PGDATADIR%\pg_hba.conf') -replace '127\.0\.0\.1/32\s+(?:scram-sha-256|md5|password|trust)', '127.0.0.1/32            trust' -replace '::1/128\s+(?:scram-sha-256|md5|password|trust)', '::1/128                 trust' | Set-Content '%PGDATADIR%\pg_hba.conf'"

echo [3] Restarting Windows PostgreSQL service...
net stop %WIN_PG_SERVICE%
net start %WIN_PG_SERVICE%
IF %ERRORLEVEL% NEQ 0 goto ERROR_RESTART

echo [4] Verifying connection and fixing flowdesk_app role...
%PSQL_CMD% -d postgres -c "SELECT 1;" >nul
IF %ERRORLEVEL% NEQ 0 goto ERROR_PSQL

%PSQL_CMD% -d postgres -c "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'flowdesk_app') THEN CREATE ROLE flowdesk_app; END IF; END $$;"
IF %ERRORLEVEL% NEQ 0 goto ERROR_PSQL

%PSQL_CMD% -d postgres -c "ALTER ROLE flowdesk_app WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE INHERIT;"
IF %ERRORLEVEL% NEQ 0 goto ERROR_PSQL

echo [5] Setting flowdesk_app password interactively...
%PSQL_CMD% -d postgres -c "\password flowdesk_app"
IF %ERRORLEVEL% NEQ 0 goto ERROR_PSQL

echo [6] Checking and fixing database flowdesk...
%PSQL_CMD% -d postgres -c "SELECT 1 FROM pg_database WHERE datname='flowdesk'" | findstr "1" >nul
IF ERRORLEVEL 1 (
    %PSQL_CMD% -d postgres -c "CREATE DATABASE flowdesk OWNER flowdesk_app;"
) ELSE (
    %PSQL_CMD% -d postgres -c "ALTER DATABASE flowdesk OWNER TO flowdesk_app;"
)
IF %ERRORLEVEL% NEQ 0 goto ERROR_PSQL

echo [7] Restoring ORIGINAL pg_hba.conf (with scram-sha-256)...
powershell -Command "(Get-Content '%PGDATADIR%\pg_hba.conf') -replace '127\.0\.0\.1/32\s+trust', '127.0.0.1/32            scram-sha-256' -replace '::1/128\s+trust', '::1/128                 scram-sha-256' | Set-Content '%PGDATADIR%\pg_hba.conf'"

echo [8] Restarting Windows PostgreSQL service...
net stop %WIN_PG_SERVICE%
net start %WIN_PG_SERVICE%

echo [9] Verifying normal password authentication is active...
echo Testing login with new password. You will be prompted to enter it:
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U flowdesk_app -h localhost -p 5432 -d flowdesk -c "SELECT current_user, current_database();"
IF %ERRORLEVEL% NEQ 0 (
    echo Password login failed.
    goto ERROR_PSQL
)

echo Verifying role attributes...
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U flowdesk_app -h localhost -p 5432 -d flowdesk -c "SELECT rolcanlogin, rolsuper, rolcreatedb, rolcreaterole FROM pg_roles WHERE rolname = 'flowdesk_app';"

echo Done. PostgreSQL repair complete successfully.
pause
exit /b 0

:ERROR_RESTART
echo =========================================
echo CRITICAL ERROR: Failed to restart PostgreSQL.
echo Restoring pg_hba.conf to safe defaults...
powershell -Command "(Get-Content '%PGDATADIR%\pg_hba.conf') -replace '127\.0\.0\.1/32\s+trust', '127.0.0.1/32            scram-sha-256' -replace '::1/128\s+trust', '::1/128                 scram-sha-256' | Set-Content '%PGDATADIR%\pg_hba.conf'"
net stop %WIN_PG_SERVICE%
net start %WIN_PG_SERVICE%
pause
exit /b 1

:ERROR_PSQL
echo =========================================
echo CRITICAL ERROR: A psql command failed.
echo Restoring pg_hba.conf to safe defaults...
powershell -Command "(Get-Content '%PGDATADIR%\pg_hba.conf') -replace '127\.0\.0\.1/32\s+trust', '127.0.0.1/32            scram-sha-256' -replace '::1/128\s+trust', '::1/128                 scram-sha-256' | Set-Content '%PGDATADIR%\pg_hba.conf'"
net stop %WIN_PG_SERVICE%
net start %WIN_PG_SERVICE%
pause
exit /b 1
