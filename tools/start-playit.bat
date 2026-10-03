@echo off
chcp 65001 >nul
title KCD — playit.gg (вход из интернета)
cd /d "%~dp0..\server\playit"
echo Туннель playit.gg для сервера KCD. Не закрывайте это окно, пока играете.
echo При первом запуске откройте ссылку ниже, войдите на playit.gg и создайте туннель "Minecraft Java" на 127.0.0.1:25565.
playit.exe
pause
