# Database Sync — NeonDB → diverteduc_dev

Ferramenta Java para sincronização automática do banco de dados de produção (**NeonDB** no Neon.tech) para o banco local de desenvolvimento (**diverteduc_dev**).

## Como funciona

1. Ao iniciar, executa um **sync imediato** da produção para o local.
2. Agenda um sync automático **todo dia à meia-noite** (configurável).
3. Mantém os **últimos 7 backups** (arquivos `.dump`) na pasta `backups/`.
4. Registra tudo em **logs com rotação diária** na pasta `logs/`.

## Pré-requisitos

| Ferramenta | Versão mínima | Como verificar         |
|------------|---------------|------------------------|
| Java (JDK) | 21+           | `java -version`        |
| Maven      | 3.9+          | `mvn -version`         |
| pg_dump    | 16+           | `pg_dump --version`    |
| pg_restore | 16+           | `pg_restore --version` |

## Configuração

1. **Preencha o arquivo de configuração:**

```bash
# Edite com suas credenciais reais
nano config/sync.properties
```

Preencha os campos:

```properties
# Produção (NeonDB - dados do painel Neon.tech > Connection Details)
source.host=ep-XXXX.us-east-2.aws.neon.tech
source.port=5432
source.db=neondb
source.user=seu_usuario
source.password=sua_senha

# Local
target.host=localhost
target.port=5432
target.db=diverteduc_dev
target.user=postgres
target.password=sua_senha_local
```

## Compilar

```bash
chmod +x scripts/build.sh
./scripts/build.sh
```

## Executar

```bash
chmod +x scripts/run.sh
./scripts/run.sh
```

Ou diretamente:
```bash
java -jar target/database-sync-1.0.0.jar
```

## Restaurar um backup manualmente

```bash
chmod +x scripts/restore.sh

# Lista os backups disponíveis
./scripts/restore.sh

# Restaura um backup específico
./scripts/restore.sh backups/neondb_20240726_0000.dump
```

## Agendamento (Cron)

Modifique `sync.cron` no `config/sync.properties`:

| Cron Expression        | Quando executa               |
|------------------------|------------------------------|
| `0 0 0 * * ?`          | Todo dia à meia-noite        |
| `0 0 6 * * ?`          | Todo dia às 06:00            |
| `0 0 6,18 * * ?`       | Às 06:00 e às 18:00          |
| `0 0 * * * ?`          | Toda hora em ponto           |
| `0 0 0 ? * MON-FRI`    | Dias úteis à meia-noite      |

## Estrutura do projeto

```
database-sync/
├── backups/              ← Dumps gerados (ignorado pelo git)
├── config/
│   └── sync.properties   ← Suas credenciais (NÃO versionar!)
├── logs/                 ← Logs com rotação diária
├── scripts/
│   ├── build.sh          ← Compila o JAR
│   ├── run.sh            ← Inicia a aplicação
│   └── restore.sh        ← Restaura um backup manualmente
├── src/
│   └── main/java/com/diverteduc/sync/
│       ├── Main.java
│       ├── config/SyncConfig.java
│       ├── service/
│       │   ├── DumpService.java
│       │   ├── RestoreService.java
│       │   └── SyncOrchestrator.java
│       └── scheduler/SyncScheduler.java
└── pom.xml
```

## ⚠️ Segurança

- O arquivo `config/sync.properties` contém senhas e está no `.gitignore`.
- **Nunca** versione este arquivo com credenciais reais.
- A senha do banco é passada via variável de ambiente `PGPASSWORD` (não aparece nos processos do sistema).
- O sistema tem uma **verificação de segurança**: impede que o banco de destino seja igual ao de origem.
