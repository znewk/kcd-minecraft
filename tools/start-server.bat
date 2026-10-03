@echo off
chcp 65001 >nul
title KCD — сервер
cd /d "%~dp0..\server"
echo Запуск сервера KCD... Чтобы остановить, введите: stop
java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.252/win_args.txt nogui
pause
