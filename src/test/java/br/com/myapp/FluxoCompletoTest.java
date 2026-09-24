package br.com.myapp;

import br.com.myapp.core.AppPaths;
import br.com.myapp.core.Config;
import br.com.myapp.core.EventBus;
import br.com.myapp.core.Scheduler;
import br.com.myapp.data.Database;
import br.com.myapp.modules.lembretes.Lembrete;
import br.com.myapp.modules.lembretes.LembreteDao;
import br.com.myapp.modules.lembretes.LembreteService;
import br.com.myapp.modules.lembretes.TipoRecorrencia;
import br.com.myapp.security.SecurityService;
import br.com.myapp.security.SenhaIncorretaException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de ponta a ponta, sem interface grafica.
 *
 * Exercitam a cadeia real do aplicativo - banco, senha mestra, cifragem e
 * agendador - em uma pasta temporaria isolada. Sao estes testes que
 * respondem "o alerta sai mesmo no horario?" e "o conteudo protegido fica
 * mesmo ilegivel com o app trancado?".
 */
class FluxoCompletoTest {

    @TempDir
    Path pastaTemporaria;

    private LembreteService servico;
    private LembreteDao dao;

    @BeforeEach
    void preparar() {
        // Redireciona todo o aplicativo para a pasta do teste.
        System.setProperty(AppPaths.PROPRIEDADE_RAIZ, pastaTemporaria.toString());
        AppPaths.redefinir();
        Config.redefinir();
        SecurityService.redefinir();
        Database.fechar();
        Database.conexao();   // abre e migra do zero

        servico = new LembreteService();
        dao = new LembreteDao();
    }

    @AfterEach
    void limpar() {
        SecurityService.trancar();
        Database.fechar();
        SecurityService.redefinir();
        Config.redefinir();
        AppPaths.redefinir();
        System.clearProperty(AppPaths.PROPRIEDADE_RAIZ);
    }

    // ------------------------------------------------------------- seguranca

    @Test
    @DisplayName("Primeira execucao cria o banco e nasce sem senha definida")
    void primeiraExecucao() {
        assertTrue(Files.exists(AppPaths.bancoDeDados()), "o banco deveria ter sido criado");
        assertFalse(SecurityService.estaConfigurado());
        assertFalse(SecurityService.estaDestrancado());
    }

    @Test
    @DisplayName("Define a senha, tranca e destranca com a senha certa")
    void cicloDeBloqueio() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), "time de sistemas");

        assertTrue(SecurityService.estaConfigurado());
        assertTrue(SecurityService.estaDestrancado(), "apos definir a senha, ja entra destrancado");
        assertEquals("time de sistemas", SecurityService.dica());

        SecurityService.trancar();
        assertFalse(SecurityService.estaDestrancado());

        SecurityService.destrancar("MinhaSenha2026".toCharArray());
        assertTrue(SecurityService.estaDestrancado());
    }

    @Test
    @DisplayName("Senha errada nao destranca")
    void senhaErrada() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);
        SecurityService.trancar();

        assertThrows(SenhaIncorretaException.class,
                () -> SecurityService.destrancar("SenhaErrada2026".toCharArray()));
        assertFalse(SecurityService.estaDestrancado());
    }

    @Test
    @DisplayName("Troca de senha mantem o conteudo protegido acessivel")
    void trocaDeSenhaPreservaOsDados() {
        SecurityService.definirSenha("SenhaAntiga2026".toCharArray(), null);

        Lembrete secreto = novoLembrete("Acesso ao servidor de producao", true);
        servico.salvar(secreto);
        Long id = secreto.getId();

        SecurityService.trocarSenha("SenhaAntiga2026".toCharArray(), "SenhaNova2026".toCharArray(), null);
        SecurityService.trancar();
        SecurityService.destrancar("SenhaNova2026".toCharArray());

        Lembrete lido = servico.porId(id).orElseThrow();
        assertEquals("Acesso ao servidor de producao", lido.getTitulo(),
                "a troca de senha nao pode tornar o conteudo ilegivel");
    }

    // ---------------------------------------------------------- protecao

    @Test
    @DisplayName("Lembrete protegido fica ilegivel no banco e na leitura com o app trancado")
    void conteudoProtegido() throws Exception {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        String segredo = "Trocar a senha do banco XPTO";
        Lembrete protegido = novoLembrete(segredo, true);
        servico.salvar(protegido);

        // 1) No banco, o titulo nao pode aparecer em texto claro.
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT titulo FROM lembrete WHERE id = ?")) {
            ps.setLong(1, protegido.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                String gravado = rs.getString(1);
                assertFalse(gravado.contains(segredo), "o titulo vazou em texto claro no banco");
                assertTrue(gravado.startsWith("enc:v1:"), "o titulo deveria estar cifrado");
            }
        }

        // 2) Com o app destrancado, volta legivel.
        assertEquals(segredo, servico.porId(protegido.getId()).orElseThrow().getTitulo());

        // 3) Com o app trancado, some da tela.
        SecurityService.trancar();
        assertEquals(LembreteDao.PROTEGIDO,
                servico.porId(protegido.getId()).orElseThrow().getTitulo());
    }

    @Test
    @DisplayName("Lembrete comum continua legivel com o app trancado")
    void conteudoComumNaoEAfetado() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);
        Lembrete comum = novoLembrete("Reuniao de alinhamento", false);
        servico.salvar(comum);

        SecurityService.trancar();

        assertEquals("Reuniao de alinhamento",
                servico.porId(comum.getId()).orElseThrow().getTitulo());
    }

    // ---------------------------------------------------------- agendamento

    @Test
    @DisplayName("Ao reabrir, o agendador recupera o aviso que venceu com o app fechado")
    void agendadorRecuperaAvisoPerdido() throws Exception {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        List<Scheduler.AlertaDisparado> recebidos = new CopyOnWriteArrayList<>();
        EventBus.ouvir(Scheduler.AlertaDisparado.class, recebidos::add);

        // Simula um aplicativo que rodou ate 30 minutos atras e foi fechado.
        // Sem isso o agendador trataria esta como a primeira execucao e, por
        // desenho, nao inventaria historico nenhum.
        Config config = Config.get();
        config.ultimaVarreduraMillis = System.currentTimeMillis() - 30 * 60_000L;
        config.salvar();

        // Lembrete que venceu ha dez minutos, enquanto o app estava fechado.
        Lembrete lembrete = novoLembrete("Backup do servidor", false);
        lembrete.setInicio(LocalDateTime.now().minusMinutes(10));
        lembrete.setAntecedencias(List.of(0));
        servico.salvar(lembrete);

        Scheduler.iniciar();
        try {
            esperarAte(() -> !recebidos.isEmpty(), 15_000);
        } finally {
            Scheduler.parar();
        }

        assertFalse(recebidos.isEmpty(), "o agendador nao recuperou o aviso perdido");
        Scheduler.AlertaDisparado alerta = recebidos.get(0);
        assertEquals(lembrete.getId(), alerta.lembrete().getId());
        assertTrue(alerta.atrasado(), "um aviso de dez minutos atras deve vir marcado como atrasado");
    }

    @Test
    @DisplayName("Na primeira execucao o agendador nao inventa avisos antigos")
    void primeiraExecucaoNaoRecuperaHistorico() throws Exception {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        List<Scheduler.AlertaDisparado> recebidos = new CopyOnWriteArrayList<>();
        EventBus.ouvir(Scheduler.AlertaDisparado.class, recebidos::add);

        // Banco novo, sem varredura anterior registrada.
        assertEquals(0L, Config.get().ultimaVarreduraMillis);

        Lembrete antigo = novoLembrete("Compromisso de ontem", false);
        antigo.setInicio(LocalDateTime.now().minusDays(1));
        antigo.setAntecedencias(List.of(0));
        servico.salvar(antigo);

        Scheduler.iniciar();
        try {
            // Tempo suficiente para duas varreduras completas.
            Thread.sleep(3_000);
        } finally {
            Scheduler.parar();
        }

        assertTrue(recebidos.isEmpty(),
                "a primeira execucao nao deveria disparar avisos de antes da instalacao");
    }

    @Test
    @DisplayName("Um lembrete que vence agora dispara na varredura seguinte")
    void agendadorDisparaAvisoDoMomento() throws Exception {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        List<Scheduler.AlertaDisparado> recebidos = new CopyOnWriteArrayList<>();
        EventBus.ouvir(Scheduler.AlertaDisparado.class, recebidos::add);

        Lembrete lembrete = novoLembrete("Entrar na reuniao", false);
        lembrete.setInicio(LocalDateTime.now().minusSeconds(5));
        lembrete.setAntecedencias(List.of(0));
        servico.salvar(lembrete);

        Scheduler.iniciar();
        try {
            esperarAte(() -> !recebidos.isEmpty(), 15_000);
        } finally {
            Scheduler.parar();
        }

        assertFalse(recebidos.isEmpty(), "o aviso do momento nao saiu");
        assertFalse(recebidos.get(0).atrasado(), "um aviso de segundos atras nao e atraso");
    }

    @Test
    @DisplayName("O mesmo aviso nao dispara duas vezes")
    void naoRepeteOAviso() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete lembrete = novoLembrete("Conferir a fila", false);
        servico.salvar(lembrete);
        LocalDateTime ocorrencia = lembrete.getInicio();

        assertTrue(dao.registrarDisparo(lembrete.getId(), ocorrencia, 5), "primeiro registro deve passar");
        assertFalse(dao.registrarDisparo(lembrete.getId(), ocorrencia, 5), "o segundo deve ser recusado");
        assertTrue(dao.jaDisparou(lembrete.getId(), ocorrencia, 5));
    }

    @Test
    @DisplayName("Adiar reapresenta o aviso depois do tempo pedido")
    void adiamento() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete lembrete = novoLembrete("Ligar para o cliente", false);
        servico.salvar(lembrete);
        LocalDateTime ocorrencia = lembrete.getInicio();

        // Adia para um instante ja vencido, para o teste nao precisar esperar.
        dao.adiar(lembrete.getId(), ocorrencia, LocalDateTime.now().minusSeconds(1));

        List<LembreteDao.Disparo> vencidos = dao.adiamentosVencidos();
        assertEquals(1, vencidos.size());
        assertEquals(lembrete.getId(), vencidos.get(0).lembreteId());

        // Depois de retornado, o adiamento sai da fila e nao volta.
        assertTrue(dao.adiamentosVencidos().isEmpty());
    }

    // ----------------------------------------------------------- persistencia

    @Test
    @DisplayName("O lembrete volta do banco com todos os campos preservados")
    void persistenciaCompleta() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete original = new Lembrete();
        original.setTitulo("Daily do time");
        original.setDescricao("Sala 3 - pauta no board");
        original.setTipo(TipoRecorrencia.DIAS_UTEIS);
        original.setInicio(LocalDateTime.of(2026, 10, 1, 9, 30));
        original.setFim(LocalDateTime.of(2026, 12, 31, 23, 59));
        original.setAntecedencias(List.of(0, 5, 60));
        original.setCor("#FF8800");
        original.setAcao("https://meet.exemplo.com/daily");
        servico.salvar(original);

        Lembrete lido = servico.porId(original.getId()).orElseThrow();

        assertEquals("Daily do time", lido.getTitulo());
        assertEquals("Sala 3 - pauta no board", lido.getDescricao());
        assertEquals(TipoRecorrencia.DIAS_UTEIS, lido.getTipo());
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 30), lido.getInicio());
        assertEquals(LocalDateTime.of(2026, 12, 31, 23, 59), lido.getFim());
        assertEquals(List.of(0, 5, 60), lido.getAntecedencias());
        assertEquals("#FF8800", lido.getCor());
        assertEquals("https://meet.exemplo.com/daily", lido.getAcao());
        assertTrue(lido.isAtivo());
    }

    @Test
    @DisplayName("Pausar e reativar um lembrete")
    void pausarEReativar() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);
        Lembrete lembrete = novoLembrete("Alongar a coluna", false);
        servico.salvar(lembrete);

        servico.alternarAtivo(lembrete);
        assertFalse(servico.porId(lembrete.getId()).orElseThrow().isAtivo());

        servico.alternarAtivo(lembrete);
        assertTrue(servico.porId(lembrete.getId()).orElseThrow().isAtivo());
    }

    @Test
    @DisplayName("Excluir remove tambem o historico de disparos")
    void exclusaoNaoApagaNada() throws Exception {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);
        Lembrete lembrete = novoLembrete("Temporario", false);
        servico.salvar(lembrete);
        dao.registrarDisparo(lembrete.getId(), lembrete.getInicio(), 0);

        servico.excluir(lembrete.getId());

        // Sai das listas do dia a dia...
        assertTrue(servico.listarTodos().stream()
                        .noneMatch(l -> l.getId().equals(lembrete.getId())),
                "o lembrete excluido nao deveria aparecer na lista");

        // ...mas a linha continua no banco, com a data marcada.
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT data_exclusao FROM lembrete WHERE id = ?")) {
            ps.setLong(1, lembrete.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "a linha do lembrete sumiu do banco");
                rs.getLong(1);
                assertFalse(rs.wasNull(), "data_exclusao deveria estar preenchida");
            }
        }

        // E o historico de disparos tambem permanece.
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT COUNT(*) FROM disparo WHERE lembrete_id = ?")) {
            ps.setLong(1, lembrete.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "o historico de disparos nao pode ser apagado");
            }
        }
    }

    @Test
    @DisplayName("Lembrete excluido para de alertar")
    void excluidoNaoAlerta() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete lembrete = novoLembrete("Nao deve alertar", false);
        servico.salvar(lembrete);
        servico.excluir(lembrete.getId());

        // listarAtivos alimenta o agendador: o excluido nao pode estar la.
        assertTrue(dao.listarAtivos().stream()
                        .noneMatch(l -> l.getId().equals(lembrete.getId())),
                "o agendador nao deveria enxergar um lembrete excluido");
    }

    @Test
    @DisplayName("Excluir e restaurar devolve o lembrete intacto")
    void restauracao() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete lembrete = novoLembrete("Vai e volta", false);
        lembrete.setSomAtivo(false);
        servico.salvar(lembrete);
        Long id = lembrete.getId();

        servico.excluir(id);
        assertEquals(1, servico.listarExcluidos().size(), "deveria estar na lixeira");

        servico.restaurar(id);

        assertTrue(servico.listarExcluidos().isEmpty(), "a lixeira deveria ter esvaziado");
        Lembrete voltou = servico.porId(id).orElseThrow();
        assertFalse(voltou.isExcluido());
        assertEquals("Vai e volta", voltou.getTitulo());
        assertFalse(voltou.isSomAtivo(), "as preferencias do lembrete devem sobreviver");
    }

    @Test
    @DisplayName("A opcao de som e gravada por lembrete")
    void somPorLembrete() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete comSom = novoLembrete("Reuniao importante", false);
        comSom.setSomAtivo(true);
        servico.salvar(comSom);

        Lembrete silencioso = novoLembrete("Rotina discreta", false);
        silencioso.setSomAtivo(false);
        servico.salvar(silencioso);

        assertTrue(servico.porId(comSom.getId()).orElseThrow().isSomAtivo());
        assertFalse(servico.porId(silencioso.getId()).orElseThrow().isSomAtivo());
    }

    @Test
    @DisplayName("Lembrete novo nasce com som ligado")
    void somLigadoPorPadrao() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);
        Lembrete novo = new Lembrete();
        assertTrue(novo.isSomAtivo(), "o padrao deve ser avisar com som");
    }

    @Test
    @DisplayName("Adiamento consumido e marcado, nao apagado")
    void adiamentoNaoEApagado() throws Exception {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete lembrete = novoLembrete("Adiado", false);
        servico.salvar(lembrete);
        dao.adiar(lembrete.getId(), lembrete.getInicio(), LocalDateTime.now().minusSeconds(1));

        assertEquals(1, dao.adiamentosVencidos().size());
        assertTrue(dao.adiamentosVencidos().isEmpty(), "nao pode voltar duas vezes");

        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT COUNT(*) FROM adiamento WHERE lembrete_id = ?")) {
            ps.setLong(1, lembrete.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "o registro do adiamento deveria continuar no banco");
            }
        }
    }

    @Test
    @DisplayName("A agenda lista as proximas ocorrencias em ordem")
    void agendaOrdenada() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete daquiATresHoras = novoLembrete("Depois", false);
        daquiATresHoras.setInicio(LocalDateTime.now().plusHours(3));
        servico.salvar(daquiATresHoras);

        Lembrete daquiAUmaHora = novoLembrete("Antes", false);
        daquiAUmaHora.setInicio(LocalDateTime.now().plusHours(1));
        servico.salvar(daquiAUmaHora);

        List<LembreteService.Ocorrencia> agenda = servico.agenda(1);

        assertTrue(agenda.size() >= 2);
        assertEquals("Antes", agenda.get(0).lembrete().getTitulo());
        assertEquals("Depois", agenda.get(1).lembrete().getTitulo());
    }

    @Test
    @DisplayName("Gravar conteudo protegido com o app trancado e recusado")
    void naoGravaProtegidoTrancado() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);
        SecurityService.trancar();

        Lembrete protegido = novoLembrete("Nao deveria entrar", true);

        assertThrows(IllegalStateException.class, () -> servico.salvar(protegido));
    }

    // ------------------------------------------------------------- apoio

    private Lembrete novoLembrete(String titulo, boolean sensivel) {
        Lembrete l = new Lembrete();
        l.setTitulo(titulo);
        l.setSensivel(sensivel);
        l.setTipo(TipoRecorrencia.UNICO);
        l.setInicio(LocalDateTime.now().plusHours(1));
        l.setAntecedencias(List.of(5));
        return l;
    }

    /** Espera a condicao virar verdadeira, sem travar o teste para sempre. */
    private void esperarAte(java.util.function.BooleanSupplier condicao, long limiteMillis)
            throws InterruptedException {
        long fim = System.currentTimeMillis() + limiteMillis;
        while (System.currentTimeMillis() < fim) {
            if (condicao.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
    }

    @Test
    @DisplayName("Os caminhos do aplicativo respeitam a pasta configurada")
    void pastaConfiguravel() {
        assertEquals(pastaTemporaria.toAbsolutePath(), AppPaths.raiz().toAbsolutePath());
        assertNotNull(AppPaths.pastaLogs());
        assertNotNull(AppPaths.pastaBackups());
    }

    // ------------------------------------------- arquivamento ao confirmar

    @Test
    @DisplayName("Confirmar um lembrete único arquiva ele sozinho")
    void confirmarUnicoArquiva() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete lembrete = novoLembrete("Ligar para o contador", false);
        lembrete.setTipo(TipoRecorrencia.UNICO);
        lembrete.setInicio(LocalDateTime.now().minusMinutes(1));
        lembrete.setAntecedencias(List.of(0));
        servico.salvar(lembrete);

        // Simula o aviso tendo saido.
        dao.registrarDisparo(lembrete.getId(), lembrete.getInicio(), 0);

        boolean arquivou = servico.reconhecer(lembrete.getId(), lembrete.getInicio());

        assertTrue(arquivou, "um lembrete unico confirmado deveria se arquivar");
        assertTrue(servico.porId(lembrete.getId()).orElseThrow().isExcluido());
        assertEquals(1, servico.listarExcluidos().size(), "deveria estar na lixeira");
    }

    @Test
    @DisplayName("Confirmar um lembrete diário não arquiva nada")
    void confirmarRepetidoNaoArquiva() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete lembrete = novoLembrete("Backup diario", false);
        lembrete.setTipo(TipoRecorrencia.DIARIO);
        lembrete.setInicio(LocalDateTime.now().minusMinutes(1));
        lembrete.setAntecedencias(List.of(0));
        servico.salvar(lembrete);
        dao.registrarDisparo(lembrete.getId(), lembrete.getInicio(), 0);

        boolean arquivou = servico.reconhecer(lembrete.getId(), lembrete.getInicio());

        assertFalse(arquivou, "um lembrete que ainda vai repetir nao pode sumir");
        assertFalse(servico.porId(lembrete.getId()).orElseThrow().isExcluido());
    }

    @Test
    @DisplayName("Repetido com término vencido também se arquiva ao confirmar")
    void confirmarRepetidoEncerradoArquiva() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        LocalDateTime ocorrencia = LocalDateTime.now().minusMinutes(5);

        Lembrete lembrete = novoLembrete("Treinamento da semana passada", false);
        lembrete.setTipo(TipoRecorrencia.DIARIO);
        lembrete.setInicio(ocorrencia);
        // O termino ja passou: nao ha mais nenhuma ocorrencia pela frente.
        lembrete.setFim(LocalDateTime.now().minusMinutes(1));
        lembrete.setAntecedencias(List.of(0));
        servico.salvar(lembrete);
        dao.registrarDisparo(lembrete.getId(), ocorrencia, 0);

        boolean arquivou = servico.reconhecer(lembrete.getId(), ocorrencia);

        assertTrue(arquivou, "sem proximas datas, deveria se arquivar");
    }

    @Test
    @DisplayName("Confirmar o aviso de véspera não arquiva: ainda falta o da hora")
    void confirmarAvisoDeVesperaNaoArquiva() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        LocalDateTime ocorrencia = LocalDateTime.now().plusHours(2);

        Lembrete lembrete = novoLembrete("Reuniao com o cliente", false);
        lembrete.setTipo(TipoRecorrencia.UNICO);
        lembrete.setInicio(ocorrencia);
        lembrete.setAntecedencias(List.of(0, 60));   // na hora e 1 h antes
        servico.salvar(lembrete);

        // So o aviso de 1 h antes saiu ate agora.
        dao.registrarDisparo(lembrete.getId(), ocorrencia, 60);

        boolean arquivou = servico.reconhecer(lembrete.getId(), ocorrencia);

        assertFalse(arquivou, "o aviso da hora ainda vai sair; o lembrete nao pode sumir");
        assertFalse(servico.porId(lembrete.getId()).orElseThrow().isExcluido());
    }

    @Test
    @DisplayName("Lembrete adiado não se arquiva ao confirmar")
    void adiadoNaoArquiva() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        LocalDateTime ocorrencia = LocalDateTime.now().minusMinutes(1);

        Lembrete lembrete = novoLembrete("Conferir a fila", false);
        lembrete.setTipo(TipoRecorrencia.UNICO);
        lembrete.setInicio(ocorrencia);
        lembrete.setAntecedencias(List.of(0));
        servico.salvar(lembrete);
        dao.registrarDisparo(lembrete.getId(), ocorrencia, 0);

        // Adiado para daqui a pouco: o aviso ainda vai voltar.
        dao.adiar(lembrete.getId(), ocorrencia, LocalDateTime.now().plusMinutes(10));

        boolean arquivou = servico.reconhecer(lembrete.getId(), ocorrencia);

        assertFalse(arquivou, "com adiamento pendente, o lembrete precisa continuar");
    }

    @Test
    @DisplayName("Lembrete pausado não se arquiva ao confirmar")
    void pausadoNaoArquiva() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        LocalDateTime ocorrencia = LocalDateTime.now().minusMinutes(1);

        Lembrete lembrete = novoLembrete("Rotina pausada", false);
        lembrete.setTipo(TipoRecorrencia.UNICO);
        lembrete.setInicio(ocorrencia);
        lembrete.setAntecedencias(List.of(0));
        servico.salvar(lembrete);
        dao.registrarDisparo(lembrete.getId(), ocorrencia, 0);
        servico.alternarAtivo(lembrete);

        boolean arquivou = servico.reconhecer(lembrete.getId(), ocorrencia);

        assertFalse(arquivou, "lembrete pausado e uma escolha do usuario; nao se mexe nele");
    }

    @Test
    @DisplayName("Um lembrete arquivado pode voltar da lixeira")
    void arquivadoPodeVoltar() {
        SecurityService.definirSenha("MinhaSenha2026".toCharArray(), null);

        Lembrete lembrete = novoLembrete("Confirmado por engano", false);
        lembrete.setTipo(TipoRecorrencia.UNICO);
        lembrete.setInicio(LocalDateTime.now().minusMinutes(1));
        lembrete.setAntecedencias(List.of(0));
        servico.salvar(lembrete);
        dao.registrarDisparo(lembrete.getId(), lembrete.getInicio(), 0);

        servico.reconhecer(lembrete.getId(), lembrete.getInicio());
        assertEquals(1, servico.listarExcluidos().size());

        servico.restaurar(lembrete.getId());

        assertFalse(servico.porId(lembrete.getId()).orElseThrow().isExcluido());
        assertEquals("Confirmado por engano",
                servico.porId(lembrete.getId()).orElseThrow().getTitulo());
    }
}
