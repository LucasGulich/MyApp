package br.com.myapp.data;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Conexão com o SQLite e controle de versão do esquema.
 *
 * O banco fica em %APPDATA%\MyApp\myapp.db. A versão atual do esquema e
 * guardada no próprio arquivo (PRAGMA user_version), e cada alteração futura
 * entra como uma migração nova na lista abaixo. Isso permite o aplicativo
 * evoluir sem que você perca o que já tem gravado.
 */
public final class Database {

    private static Connection conexao;

    private Database() {
    }

    public static synchronized Connection conexao() {
        if (conexao == null) {
            abrir();
        }
        return conexao;
    }

    private static void abrir() {
        try {
            String url = "jdbc:sqlite:" + AppPaths.bancoDeDados().toAbsolutePath();
            conexao = DriverManager.getConnection(url);
            try (Statement st = conexao.createStatement()) {
                // WAL deixa leitura e escrita concorrentes bem mais tranquilas.
                st.execute("PRAGMA journal_mode = WAL");
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA synchronous = NORMAL");
            }
            migrar();
            Log.info("Banco aberto em " + AppPaths.bancoDeDados());
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível abrir o banco de dados", e);
        }
    }

    // ------------------------------------------------------------ Migrações

    /**
     * Cada posição da lista e uma versão do esquema. Para evoluir o banco,
     * acrescente um item novo no fim - nunca altere os que já existem.
     */
    private static List<String[]> migracoes() {
        List<String[]> lista = new ArrayList<>();

        // --- Versão 1: base do aplicativo ---
        lista.add(new String[]{
                """
                CREATE TABLE app_meta (
                    chave TEXT PRIMARY KEY,
                    valor TEXT NOT NULL
                )
                """,
                """
                CREATE TABLE cofre_meta (
                    id            INTEGER PRIMARY KEY CHECK (id = 1),
                    salt          BLOB    NOT NULL,
                    iteracoes     INTEGER NOT NULL,
                    dek_cifrada   BLOB    NOT NULL,
                    dica          TEXT,
                    criado_em     INTEGER NOT NULL,
                    atualizado_em INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE lembrete (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    titulo            TEXT    NOT NULL,
                    descricao         TEXT,
                    sensivel          INTEGER NOT NULL DEFAULT 0,
                    tipo_recorrencia  TEXT    NOT NULL,
                    inicio            INTEGER NOT NULL,
                    fim               INTEGER,
                    dias_semana       TEXT,
                    dia_mes           INTEGER,
                    intervalo_minutos INTEGER,
                    antecedencias     TEXT    NOT NULL DEFAULT '5',
                    ativo             INTEGER NOT NULL DEFAULT 1,
                    cor               TEXT,
                    acao              TEXT,
                    criado_em         INTEGER NOT NULL,
                    atualizado_em     INTEGER NOT NULL
                )
                """,
                "CREATE INDEX idx_lembrete_ativo ON lembrete(ativo, inicio)",
                """
                CREATE TABLE disparo (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    lembrete_id  INTEGER NOT NULL,
                    ocorrencia   INTEGER NOT NULL,
                    antecedencia INTEGER NOT NULL,
                    disparado_em INTEGER NOT NULL,
                    reconhecido  INTEGER NOT NULL DEFAULT 0,
                    UNIQUE (lembrete_id, ocorrencia, antecedencia),
                    FOREIGN KEY (lembrete_id) REFERENCES lembrete(id) ON DELETE CASCADE
                )
                """,
                "CREATE INDEX idx_disparo_pendente ON disparo(reconhecido, disparado_em)",
                """
                CREATE TABLE adiamento (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    lembrete_id INTEGER NOT NULL,
                    ocorrencia  INTEGER NOT NULL,
                    alertar_em  INTEGER NOT NULL,
                    FOREIGN KEY (lembrete_id) REFERENCES lembrete(id) ON DELETE CASCADE
                )
                """
        });

        // --- versão 2: exclusão lógica e som por lembrete ---
        //
        // Nada mais é removido de verdade do banco. Excluir passa a gravar a
        // data em data_exclusao, e as consultas do dia a dia ignoram quem tem
        // esse campo preenchido. Assim nenhuma informação se perde por engano,
        // e sempre dá para voltar atrás.
        lista.add(new String[]{
                "ALTER TABLE lembrete ADD COLUMN data_exclusao INTEGER",
                "ALTER TABLE disparo  ADD COLUMN data_exclusao INTEGER",
                "ALTER TABLE adiamento ADD COLUMN data_exclusao INTEGER",

                // O índice principal passa a considerar a exclusão lógica.
                "DROP INDEX IF EXISTS idx_lembrete_ativo",
                "CREATE INDEX idx_lembrete_vivo ON lembrete(data_exclusao, ativo, inicio)",

                // Tocar som deixa de ser uma preferência geral e passa a ser
                // escolha de cada lembrete. 1 = toca, 0 = silencioso.
                "ALTER TABLE lembrete ADD COLUMN som_ativo INTEGER NOT NULL DEFAULT 1"
        });

        // --- versão 3: módulo de notas ---
        //
        // Uma nota tem tipo (livre, credencial, link, código), e cada tipo
        // pede campos diferentes. Em vez de uma coluna por campo de cada tipo
        // — o que engessaria o modelo — os campos são linhas em nota_campo.
        // Criar um tipo novo, ou deixar o usuário inventar um campo próprio,
        // deixa de exigir migração de banco.
        lista.add(new String[]{
                """
                CREATE TABLE categoria (
                    id                INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome              TEXT    NOT NULL,
                    cor               TEXT,
                    icone             TEXT,
                    categoria_pai_id  INTEGER,
                    ordem             INTEGER NOT NULL DEFAULT 0,
                    exige_desbloqueio INTEGER NOT NULL DEFAULT 0,
                    criado_em         INTEGER NOT NULL,
                    atualizado_em     INTEGER NOT NULL,
                    data_exclusao     INTEGER,
                    FOREIGN KEY (categoria_pai_id) REFERENCES categoria(id)
                )
                """,
                "CREATE INDEX idx_categoria_viva ON categoria(data_exclusao, ordem, nome)",
                """
                CREATE TABLE nota (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    categoria_id  INTEGER,
                    tipo          TEXT    NOT NULL,
                    titulo        TEXT    NOT NULL,
                    conteudo      TEXT,
                    protegida     INTEGER NOT NULL DEFAULT 0,
                    favorita      INTEGER NOT NULL DEFAULT 0,
                    fixada        INTEGER NOT NULL DEFAULT 0,
                    criado_em     INTEGER NOT NULL,
                    atualizado_em INTEGER NOT NULL,
                    data_exclusao INTEGER,
                    FOREIGN KEY (categoria_id) REFERENCES categoria(id)
                )
                """,
                "CREATE INDEX idx_nota_viva ON nota(data_exclusao, categoria_id)",
                "CREATE INDEX idx_nota_favorita ON nota(data_exclusao, favorita)",
                """
                CREATE TABLE nota_campo (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    nota_id       INTEGER NOT NULL,
                    chave         TEXT    NOT NULL,
                    valor         TEXT,
                    sensivel      INTEGER NOT NULL DEFAULT 0,
                    ordem         INTEGER NOT NULL DEFAULT 0,
                    data_exclusao INTEGER,
                    FOREIGN KEY (nota_id) REFERENCES nota(id) ON DELETE CASCADE
                )
                """,
                "CREATE INDEX idx_nota_campo ON nota_campo(nota_id, data_exclusao, ordem)",

                // ATENÇÃO: as duas tabelas a seguir estão sem uso desde a v1.4.
                //
                // As etiquetas foram retiradas da interface: na prática, a
                // categoria dava conta de organizar, e o campo virava mais um
                // para preencher sem retorno. As tabelas continuam aqui porque
                // uma migração já aplicada nunca é alterada — mudá-la deixaria
                // bancos existentes em um estado que o código não espera.
                //
                // Ficam vazias, sem custo, e prontas caso o recurso volte.
                """
                CREATE TABLE etiqueta (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome          TEXT    NOT NULL,
                    cor           TEXT,
                    criado_em     INTEGER NOT NULL,
                    data_exclusao INTEGER,
                    UNIQUE (nome)
                )
                """,
                """
                CREATE TABLE nota_etiqueta (
                    nota_id     INTEGER NOT NULL,
                    etiqueta_id INTEGER NOT NULL,
                    PRIMARY KEY (nota_id, etiqueta_id),
                    FOREIGN KEY (nota_id)     REFERENCES nota(id)     ON DELETE CASCADE,
                    FOREIGN KEY (etiqueta_id) REFERENCES etiqueta(id) ON DELETE CASCADE
                )
                """,
                // Guarda a senha anterior quando você troca. Resolve o caso de
                // trocar, o sistema do cliente recusar, e precisar da antiga.
                """
                CREATE TABLE nota_historico (
                    id             INTEGER PRIMARY KEY AUTOINCREMENT,
                    nota_id        INTEGER NOT NULL,
                    chave          TEXT    NOT NULL,
                    valor_anterior TEXT,
                    trocado_em     INTEGER NOT NULL,
                    FOREIGN KEY (nota_id) REFERENCES nota(id) ON DELETE CASCADE
                )
                """,
                "CREATE INDEX idx_nota_historico ON nota_historico(nota_id, trocado_em DESC)"
        });

        // --- versão 4: recados da tela inicial ---
        //
        // Os post-its do painel de início. A coluna `ordem` guarda a posição
        // escolhida ao arrastar — é o que faz o arranjo sobreviver ao fechar
        // o aplicativo.
        lista.add(new String[]{
                """
                CREATE TABLE recado (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    texto         TEXT    NOT NULL,
                    cor           TEXT    NOT NULL DEFAULT 'AMARELO',
                    ordem         INTEGER NOT NULL DEFAULT 0,
                    criado_em     INTEGER NOT NULL,
                    atualizado_em INTEGER NOT NULL,
                    data_exclusao INTEGER
                )
                """,
                "CREATE INDEX idx_recado_vivo ON recado(data_exclusao, ordem)"
        });

        // --- versão 5: ordem manual em lembretes e notas ---
        //
        // As duas listas passam a aceitar arrastar. A ordenação automática
        // (por proximidade, por alteração) continua sendo a padrão; esta
        // coluna guarda o arranjo de quem preferir organizar na mão.
        //
        // Começa em -1 para distinguir "nunca foi arrastado" de "está na
        // primeira posição": a lista só entra em ordem manual depois do
        // primeiro arrasto.
        lista.add(new String[]{
                "ALTER TABLE lembrete ADD COLUMN ordem INTEGER NOT NULL DEFAULT -1",
                "ALTER TABLE nota     ADD COLUMN ordem INTEGER NOT NULL DEFAULT -1"
        });

        // --- versão 6: quadro Kanban ---
        //
        // Colunas e cartões. O cartão guarda o prazo como texto ISO
        // (aaaa-mm-dd) e não como número: é data sem hora, e comparar texto
        // ISO ordena igual a comparar data — sem o risco de fuso que um
        // carimbo de tempo traria para uma data que é só um dia do calendário.
        lista.add(new String[]{
                """
                CREATE TABLE kanban_coluna (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    nome          TEXT    NOT NULL,
                    cor           TEXT,
                    ordem         INTEGER NOT NULL DEFAULT 0,
                    criado_em     INTEGER NOT NULL,
                    atualizado_em INTEGER NOT NULL,
                    data_exclusao INTEGER
                )
                """,
                """
                CREATE TABLE kanban_card (
                    id            INTEGER PRIMARY KEY AUTOINCREMENT,
                    coluna_id     INTEGER NOT NULL REFERENCES kanban_coluna(id),
                    titulo        TEXT    NOT NULL,
                    descricao     TEXT,
                    prazo         TEXT,
                    cor           TEXT,
                    ordem         INTEGER NOT NULL DEFAULT 0,
                    arquivado     INTEGER NOT NULL DEFAULT 0,
                    protegido     INTEGER NOT NULL DEFAULT 0,
                    criado_em     INTEGER NOT NULL,
                    atualizado_em INTEGER NOT NULL,
                    data_exclusao INTEGER
                )
                """,
                "CREATE INDEX idx_kanban_coluna_viva ON kanban_coluna(data_exclusao, ordem)",
                "CREATE INDEX idx_kanban_card_coluna ON kanban_card(coluna_id, data_exclusao, ordem)"
        });

        return lista;
    }

    private static void migrar() throws SQLException {
        int versaoAtual = versaoEsquema();
        List<String[]> todas = migracoes();

        if (versaoAtual > todas.size()) {
            throw new IllegalStateException(
                    "O banco está na versão " + versaoAtual + ", mais nova que este aplicativo ("
                            + todas.size() + "). Atualize o MyApp antes de continuar.");
        }

        for (int versao = versaoAtual; versao < todas.size(); versao++) {
            Log.info("Aplicando migração do banco para a versão " + (versao + 1));
            conexao.setAutoCommit(false);
            try (Statement st = conexao.createStatement()) {
                for (String comando : todas.get(versao)) {
                    st.execute(comando);
                }
                st.execute("PRAGMA user_version = " + (versao + 1));
                conexao.commit();
            } catch (SQLException e) {
                conexao.rollback();
                throw e;
            } finally {
                conexao.setAutoCommit(true);
            }
        }
    }

    private static int versaoEsquema() throws SQLException {
        try (Statement st = conexao.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // --------------------------------------------------------------- apoio

    /** Le um valor da tabela app_meta. */
    public static String lerMeta(String chave, String padrao) {
        try (var ps = conexao().prepareStatement("SELECT valor FROM app_meta WHERE chave = ?")) {
            ps.setString(1, chave);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : padrao;
            }
        } catch (SQLException e) {
            Log.erro("Falha ao ler app_meta: " + chave, e);
            return padrao;
        }
    }

    /** Grava um valor na tabela app_meta. */
    public static void gravarMeta(String chave, String valor) {
        try (var ps = conexao().prepareStatement(
                "INSERT INTO app_meta(chave, valor) VALUES(?, ?) "
                        + "ON CONFLICT(chave) DO UPDATE SET valor = excluded.valor")) {
            ps.setString(1, chave);
            ps.setString(2, valor);
            ps.executeUpdate();
        } catch (SQLException e) {
            Log.erro("Falha ao gravar app_meta: " + chave, e);
        }
    }

    public static synchronized void fechar() {
        if (conexao != null) {
            try {
                conexao.close();
            } catch (SQLException e) {
                Log.aviso("Erro ao fechar o banco: " + e.getMessage());
            }
            conexao = null;
        }
    }
}
