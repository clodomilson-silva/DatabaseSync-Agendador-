#!/bin/bash
# =============================================================
#  build.sh - Compila o projeto e gera o JAR executável
#  Uso: ./scripts/build.sh
# =============================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

# Procura Maven local ou no PATH
MVN=""
if command -v mvn &>/dev/null; then
    MVN="mvn"
elif [ -f "$HOME/apache-maven-3.9.6/bin/mvn" ]; then
    MVN="$HOME/apache-maven-3.9.6/bin/mvn"
elif [ -f "$HOME/.local/bin/mvn" ]; then
    MVN="$HOME/.local/bin/mvn"
else
    echo "[ERRO] Maven não encontrado."
    echo "Instale com: sudo apt install maven"
    echo "Ou baixe de: https://maven.apache.org/download.cgi"
    exit 1
fi

echo "========================================================"
echo "  Database Sync - Build"
echo "========================================================"
echo "Maven : $($MVN -version 2>&1 | head -1)"
echo "Java  : $(java -version 2>&1 | head -1)"
echo ""

cd "$PROJECT_DIR"
$MVN clean package -q

if [ $? -eq 0 ]; then
    JAR_SIZE=$(du -sh target/database-sync-1.0.0.jar 2>/dev/null | cut -f1)
    echo ""
    echo "✅ Build concluído com sucesso!"
    echo "   JAR: target/database-sync-1.0.0.jar ($JAR_SIZE)"
    echo ""
    echo "Para executar:"
    echo "   ./scripts/run.sh"
else
    echo ""
    echo "❌ Build falhou. Verifique os erros acima."
    exit 1
fi
