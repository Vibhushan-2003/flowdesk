@echo off
set /p FLOWDESK_PASSWORD="Enter a new password for flowdesk_app: "

echo.
echo Running PostgreSQL setup. You may be prompted for the postgres superuser password.
echo.

psql -U postgres -c "CREATE ROLE flowdesk_app WITH LOGIN PASSWORD '%FLOWDESK_PASSWORD%';"
psql -U postgres -c "CREATE DATABASE flowdesk OWNER flowdesk_app;"

echo.
echo Setup complete. Testing connection...
psql -U flowdesk_app -h localhost -p 5432 -d flowdesk -c "SELECT current_user, current_database();"

echo.
echo Please update the DB_PASSWORD in your .env file with the password you just entered.
set FLOWDESK_PASSWORD=
