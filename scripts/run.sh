#!/bin/bash
# =============================================================
#  run.sh - Inicia o Database Sync
#  Uso: ./scripts/run.sh
# =============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR="$PROJECT_DIR/target/database-sync-1.0.0.jar"

echo "========================================================"
echo "  Database Sync - NeonDB → diverteduc_dev"
echo "========================================================"

# Verifica se o JAR existe
if [ ! -f "$JAR" ]; then
    echo "[ERRO] JAR não encontrado: $JAR"
    echo "Execute primeiro: mvn clean package"
    exit 1
fi

# Verifica se sync.properties existe
if [ ! -f "$PROJECT_DIR/config/sync.properties" ]; then
    echo "[ERRO] Arquivo de configuração não encontrado!"
    echo "Crie e preencha: $PROJECT_DIR/config/sync.properties"
    exit 1
fi

# Verifica pg_dump
if ! command -v pg_dump &>/dev/null; then
    echo "[AVISO] pg_dump não encontrado no PATH."
    echo "Certifique-se de que o PostgreSQL client está instalado."
fi

cd "$PROJECT_DIR"

echo "Iniciando... (Ctrl+C para parar)"
echo ""

java -Xmx256m \
     -Duser.timezone=America/Sao_Paulo \
     -Dfile.encoding=UTF-8 \
     -jar "$JAR"
