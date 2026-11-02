FROM python:3.13-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY api.py models.py ./
RUN useradd --create-home coletai
USER coletai
CMD ["uvicorn", "api:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000"]
