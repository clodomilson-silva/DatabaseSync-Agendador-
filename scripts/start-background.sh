#!/bin/bash
# =============================================================
#  start-background.sh - Inicia o sync em segundo plano (daemon)
#  O processo continua mesmo após fechar o terminal/IDE.
#
#  Uso:
#    ./scripts/start-background.sh          → inicia
#    ./scripts/start-background.sh stop     → para
#    ./scripts/start-background.sh status   → verifica
#    ./scripts/start-background.sh logs     → acompanha logs ao vivo
# =============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR="$PROJECT_DIR/target/database-sync-1.0.0.jar"
PID_FILE="$PROJECT_DIR/database-sync.pid"
LOG_FILE="$PROJECT_DIR/logs/database-sync.log"

# ─── Funções ──────────────────────────────────────────────────

start() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            echo "⚠️  O Database Sync já está rodando (PID: $PID)"
            echo "   Use: ./scripts/start-background.sh status"
            exit 1
        else
            rm -f "$PID_FILE"
        fi
    fi

    if [ ! -f "$JAR" ]; then
        echo "[ERRO] JAR não encontrado. Execute primeiro: ./scripts/build.sh"
        exit 1
    fi

    mkdir -p "$PROJECT_DIR/logs"

    echo "Iniciando Database Sync em segundo plano..."

    nohup java -Xmx256m \
               -Duser.timezone=America/Sao_Paulo \
               -Dfile.encoding=UTF-8 \
               -jar "$JAR" \
               >> "$LOG_FILE" 2>&1 &

    PID=$!
    echo "$PID" > "$PID_FILE"

    sleep 2

    if kill -0 "$PID" 2>/dev/null; then
        echo "✅ Iniciado com sucesso!"
        echo "   PID     : $PID"
        echo "   Log     : $LOG_FILE"
        echo "   PID File: $PID_FILE"
        echo ""
        echo "Para acompanhar: ./scripts/start-background.sh logs"
        echo "Para parar     : ./scripts/start-background.sh stop"
    else
        echo "❌ Falhou ao iniciar. Verifique o log: $LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
}

stop() {
    if [ ! -f "$PID_FILE" ]; then
        echo "⚠️  Nenhum processo encontrado (sem arquivo PID)."
        echo "   Tentando encontrar por nome..."
        PID=$(pgrep -f "database-sync-1.0.0.jar" | head -1)
        if [ -n "$PID" ]; then
            echo "   Encontrado PID: $PID — encerrando..."
            kill "$PID"
            echo "✅ Encerrado."
        else
            echo "   Nenhum processo encontrado."
        fi
        return
    fi

    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Encerrando Database Sync (PID: $PID)..."
        kill "$PID"
        sleep 2
        if kill -0 "$PID" 2>/dev/null; then
            kill -9 "$PID"
        fi
        rm -f "$PID_FILE"
        echo "✅ Encerrado."
    else
        echo "⚠️  Processo (PID: $PID) não estava rodando."
        rm -f "$PID_FILE"
    fi
}

status() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            UPTIME=$(ps -p "$PID" -o etime= 2>/dev/null | tr -d ' ')
            echo "✅ Database Sync está RODANDO"
            echo "   PID   : $PID"
            echo "   Uptime: $UPTIME"
            echo "   Log   : $LOG_FILE"
            echo ""
            echo "Últimas 5 linhas do log:"
            tail -5 "$LOG_FILE" 2>/dev/null
        else
            echo "❌ Database Sync NÃO está rodando (PID $PID inativo)"
            rm -f "$PID_FILE"
        fi
    else
        PID=$(pgrep -f "database-sync-1.0.0.jar" | head -1)
        if [ -n "$PID" ]; then
            echo "⚠️  Processo encontrado (PID: $PID) mas sem arquivo PID."
            echo "   Crie: echo $PID > $PID_FILE"
        else
            echo "❌ Database Sync NÃO está rodando."
        fi
    fi
}

show_logs() {
    echo "Acompanhando logs em tempo real (Ctrl+C para sair):"
    echo "────────────────────────────────────────────────────"
    tail -f "$LOG_FILE"
}

# ─── Ação ─────────────────────────────────────────────────────

cd "$PROJECT_DIR"

case "${1:-start}" in
    start)   start ;;
    stop)    stop ;;
    restart) stop; sleep 1; start ;;
    status)  status ;;
    logs)    show_logs ;;
    *)
        echo "Uso: $0 {start|stop|restart|status|logs}"
        exit 1
        ;;
esac
