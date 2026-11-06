package br.com.myapp.modules.uteis.sql;

import br.com.myapp.modules.uteis.sql.FormatadorSql.Banco;
import br.com.myapp.modules.uteis.sql.FormatadorSql.Caixa;
import br.com.myapp.modules.uteis.sql.FormatadorSql.Resultado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Formatar SQL: caixa, aspas intocadas, log do Hibernate, preencher os ?, compactar. */
class FormatadorSqlTest {

    private static final String GRUDADA = "select c.id,c.nome,p.total from cliente c inner join pedido p "
            + "on p.cliente_id=c.id where p.status='Aberto, select from' order by p.total desc";

    @Test
    @DisplayName("Formata em linhas, com MAIÚSCULAS só nas palavras-chave e o texto entre aspas intacto")
    void formataMaiusculas() {
        String sql = FormatadorSql.formatar(GRUDADA, "", Banco.POSTGRESQL, Caixa.MAIUSCULAS, "    ").sql();
        assertTrue(sql.startsWith("SELECT\n    c.id,\n    c.nome,"), sql);
        assertTrue(sql.contains("INNER JOIN pedido p ON p.cliente_id = c.id"), sql);
        assertTrue(sql.contains("'Aberto, select from'"), "o texto entre aspas não pode mudar: " + sql);
        assertTrue(sql.contains("ORDER BY"), sql);
    }

    @Test
    @DisplayName("minúsculas: palavras-chave em minúsculas, mesmo vindo em maiúsculas; nomes não mudam")
    void formataMinusculas() {
        String sql = FormatadorSql.formatar("SELECT NomeCliente FROM Cliente WHERE Ativo = 'SIM'", "",
                Banco.POSTGRESQL, Caixa.MINUSCULAS, "  ").sql();
        assertTrue(sql.contains("select"), sql);
        assertTrue(sql.contains("from"), sql);
        assertTrue(sql.contains("where"), sql);
        assertTrue(sql.contains("NomeCliente"), "nome de coluna mantém a caixa: " + sql);
        assertTrue(sql.contains("Cliente"), sql);
        assertTrue(sql.contains("'SIM'"), sql);
        assertFalse(sql.contains("SELECT"), sql);
    }

    @Test
    @DisplayName("Como veio: não mexe na caixa de nada")
    void comoVeio() {
        String sql = FormatadorSql.formatar("Select a From b", "", Banco.POSTGRESQL, Caixa.COMO_VEIO, "  ").sql();
        assertTrue(sql.contains("Select") && sql.contains("From"), sql);
    }

    @Test
    @DisplayName("PostgreSQL: id::text continua junto")
    void cast() {
        String sql = FormatadorSql.formatar("select id::text from t", "", Banco.POSTGRESQL, Caixa.MAIUSCULAS, "  ").sql();
        assertTrue(sql.contains("id::TEXT"), sql);
    }

    @Test
    @DisplayName("Log do Hibernate 5 colado inteiro: consulta e valores separados, com aspas onde precisa")
    void logHibernate5() {
        String log = """
                Hibernate: select * from pedido where cliente_id=? and status=? and criado_em>=?
                binding parameter [1] as [BIGINT] - [1532]
                binding parameter [2] as [VARCHAR] - [ABERTO]
                binding parameter [3] as [TIMESTAMP] - [2026-10-01 00:00:00.0]
                """;
        Resultado r = FormatadorSql.compactar(log, "", Banco.POSTGRESQL, Caixa.MAIUSCULAS);
        assertEquals("SELECT * FROM pedido WHERE cliente_id = 1532 AND status = 'ABERTO' "
                + "AND criado_em >= '2026-10-01 00:00:00.0'", r.sql());
        assertEquals(3, r.preenchidos());
        assertEquals(0, r.semValor());
    }

    @Test
    @DisplayName("Log do Spring/Hibernate 6: prefixo do log fora, formato (1:BIGINT) <- [x]")
    void logHibernate6() {
        String log = "2026-10-16 10:00:00 DEBUG 123 --- [main] org.hibernate.SQL : select nome from cliente where id=?\n"
                + "2026-10-16 10:00:00 TRACE 123 --- [main] org.hibernate.orm.jdbc.bind : binding parameter (1:BIGINT) <- [7]";
        Resultado r = FormatadorSql.compactar(log, "", Banco.POSTGRESQL, Caixa.MAIUSCULAS);
        assertEquals("SELECT nome FROM cliente WHERE id = 7", r.sql());
    }

    @Test
    @DisplayName("Valores à mão: vírgula ou um por linha; número sem aspas, texto com, NULL, aspa escapada")
    void valoresAMao() {
        assertEquals(List.of("1532", "'ABERTO'", "NULL", "'D''Ávila'"),
                FormatadorSql.lerValores("1532, ABERTO, null, D'Ávila"));
        assertEquals(List.of("1532", "'já com aspas'", "true"),
                FormatadorSql.lerValores("[1532]\n'já com aspas'\ntrue"));
    }

    @Test
    @DisplayName("? dentro de aspas ou comentário não é parâmetro; falta e sobra são contadas")
    void preencherRespeitaAspas() {
        Resultado r = FormatadorSql.compactar("select '?' as q, a from t -- e agora?\nwhere b = ? and c = ?",
                "10", Banco.POSTGRESQL, Caixa.MAIUSCULAS);
        assertTrue(r.sql().contains("'?' AS q"), r.sql());
        assertTrue(r.sql().contains("/* e agora? */"), "comentário de linha vira de bloco: " + r.sql());
        assertTrue(r.sql().contains("b = 10"), r.sql());
        assertEquals(1, r.preenchidos());
        assertEquals(1, r.semValor());

        Resultado sobra = FormatadorSql.compactar("select * from t where a = ?", "1, 2",
                Banco.POSTGRESQL, Caixa.MAIUSCULAS);
        assertEquals(1, sobra.valoresSobrando());
    }

    @Test
    @DisplayName("PostgreSQL $1, $2: cada um pelo número")
    void parametrosNumerados() {
        Resultado r = FormatadorSql.compactar("select * from t where a = $2 and b = $1", "10, X",
                Banco.POSTGRESQL, Caixa.MAIUSCULAS);
        assertEquals("SELECT * FROM t WHERE a = 'X' AND b = 10", r.sql());
    }

    @Test
    @DisplayName("Compactar: numa linha, sem espaço sobrando em parênteses")
    void compactar() {
        Resultado r = FormatadorSql.compactar("select a\n  from t\n where x in (select y from z)", "",
                Banco.POSTGRESQL, Caixa.MAIUSCULAS);
        assertEquals("SELECT a FROM t WHERE x IN (SELECT y FROM z)", r.sql());
    }
}
