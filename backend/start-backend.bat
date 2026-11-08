@echo off
REM Double-click to start the Soteria backend + ngrok.
cd /d "%~dp0"
echo Installing packages...
python -m pip install -q -r requirements.txt
start "Soteria backend" cmd /k python -m uvicorn main:app --host 0.0.0.0 --port 8000
where ngrok >nul 2>nul
if %errorlevel%==0 (
  start "ngrok" cmd /k ngrok http 8000
  echo.
  echo Copy the https://....ngrok-free.app URL from the ngrok window.
  echo Paste it into the app: Settings ^> Backend URL, and into Twilio as URL/twilio/voice
) else (
  echo ngrok is not installed. Get it from https://ngrok.com/download
  echo then run once: ngrok config add-authtoken YOUR_TOKEN
)
echo.
echo Also start LM Studio: load gpt-oss-20b, Developer tab, Start Server.
pause
