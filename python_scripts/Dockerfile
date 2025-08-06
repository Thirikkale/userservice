FROM python:3.9-slim

WORKDIR /app

RUN apt-get update && apt-get install -y build-essential libgl1-mesa-glx

COPY requirements_fastapi.txt .
RUN pip install --no-cache-dir -r requirements_fastapi.txt

COPY . .

EXPOSE 8001

CMD ["uvicorn", "fastapi_ai_service:app", "--host", "0.0.0.0", "--port", "8001"]