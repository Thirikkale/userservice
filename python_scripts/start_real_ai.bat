@echo off
echo 🔥 Starting FastAPI AI Service with REAL AI...
echo.
echo ⚠️  WARNING: Real AI models require significant memory!
echo 📊 Expected usage: 2-4GB RAM
echo ⏱️  First startup may take 2-3 minutes for model downloads
echo.
set USE_MOCK_AI=false
d:/Backend/.venv/Scripts/python.exe fastapi_ai_service.py
pause
