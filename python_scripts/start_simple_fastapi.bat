@echo off
echo 🚀 Starting Simple FastAPI AI Service (Fixed Version)
echo ================================================

cd "d:\Backend\userservice\python_scripts"

echo Setting environment for REAL AI mode...
set USE_MOCK_AI=false

echo Starting FastAPI service on port 8001...
d:/Backend/.venv/Scripts/uvicorn.exe simple_fastapi_ai_service:app --host 0.0.0.0 --port 8001

pause
