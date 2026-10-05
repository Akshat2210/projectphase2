@echo off

echo Creating database...
mysql -u root -pGurukripa -e "CREATE DATABASE akshat;"
if errorlevel 1 goto error

echo Creating user...
mysql -u root -pGurukripa -e "CREATE USER 'akshat'@'%%' IDENTIFIED BY 'gurukripa';"
if errorlevel 1 goto error

echo Granting privileges...
mysql -u root -pGurukripa -e "GRANT ALL PRIVILEGES ON akshat.* TO 'akshat'@'%%';"
if errorlevel 1 goto error

echo Importing structure...
mysql --binary-mode=1 -u root -pGurukripa akshat < structure.sql
if errorlevel 1 goto error

echo SUCCESS
exit /b 0

:error
echo FAILED
exit /b 1