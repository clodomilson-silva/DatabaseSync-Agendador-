#!/bin/bash
# =============================================================
#  restore.sh - Restaura um backup específico no diverteduc_dev
#  Uso: ./scripts/restore.sh backups/neondb_20240726_0000.dump
# =============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
CONFIG="$PROJECT_DIR/config/sync.properties"

# ─── Verifica argumentos ──────────────────────────────────────
if [ -z "$1" ]; then
    echo "Uso: $0 <arquivo.dump>"
    echo ""
    echo "Backups disponíveis:"
    ls "$PROJECT_DIR/backups/"*.dump 2>/dev/null || echo "  Nenhum backup encontrado."
    exit 1
fi

DUMP_FILE="$1"
if [ ! -f "$DUMP_FILE" ]; then
    DUMP_FILE="$PROJECT_DIR/$1"
fi

if [ ! -f "$DUMP_FILE" ]; then
    echo "[ERRO] Arquivo não encontrado: $1"
    exit 1
fi

# ─── Lê configurações ─────────────────────────────────────────
if [ ! -f "$CONFIG" ]; then
    echo "[ERRO] sync.properties não encontrado em $CONFIG"
    exit 1
fi

TARGET_HOST=$(grep '^target.host'     "$CONFIG" | cut -d= -f2 | tr -d ' ')
TARGET_PORT=$(grep '^target.port'     "$CONFIG" | cut -d= -f2 | tr -d ' ')
TARGET_DB=$(grep   '^target.db'       "$CONFIG" | cut -d= -f2 | tr -d ' ')
TARGET_USER=$(grep '^target.user'     "$CONFIG" | cut -d= -f2 | tr -d ' ')
TARGET_PASS=$(grep '^target.password' "$CONFIG" | cut -d= -f2 | tr -d ' ')

echo "========================================================"
echo "  Restauração Manual"
echo "  Arquivo : $(basename $DUMP_FILE)"
echo "  Destino : $TARGET_HOST:$TARGET_PORT/$TARGET_DB"
echo "========================================================"
echo ""
echo "⚠️  ATENÇÃO: O banco '$TARGET_DB' será APAGADO e recriado!"
read -p "Confirma? (s/N): " confirm

if [[ "$confirm" != "s" && "$confirm" != "S" ]]; then
    echo "Operação cancelada."
    exit 0
fi

export PGPASSWORD="$TARGET_PASS"

# Encerra conexões ativas
echo ""
echo "1/3 Encerrando conexões ativas..."
psql -h "$TARGET_HOST" -p "$TARGET_PORT" -U "$TARGET_USER" -d postgres \
     -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$TARGET_DB' AND pid <> pg_backend_pid()" \
     -q 2>/dev/null

# Dropa e recria
echo "2/3 Recriando banco '$TARGET_DB'..."
psql -h "$TARGET_HOST" -p "$TARGET_PORT" -U "$TARGET_USER" -d postgres \
     -c "DROP DATABASE IF EXISTS \"$TARGET_DB\"" -q
psql -h "$TARGET_HOST" -p "$TARGET_PORT" -U "$TARGET_USER" -d postgres \
     -c "CREATE DATABASE \"$TARGET_DB\"" -q

# Restaura
echo "3/3 Restaurando dump..."
pg_restore --host="$TARGET_HOST" \
           --port="$TARGET_PORT" \
           --username="$TARGET_USER" \
           --dbname="$TARGET_DB" \
           --no-owner \
           --no-acl \
           "$DUMP_FILE"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Restauração concluída com sucesso!"
else
    echo ""
    echo "❌ Restauração falhou. Verifique os erros acima."
    exit 1
fi

unset PGPASSWORD
