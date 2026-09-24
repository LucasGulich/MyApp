package br.com.myapp.modules.lembretes;

import br.com.myapp.core.Config;
import br.com.myapp.core.EventBus;
import br.com.myapp.core.Log;
import br.com.myapp.core.Scheduler;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regras de negócio dos lembretes.
 *
 * A tela conversa com esta classe, nunca direto com o banco. Assim a mesma
 * regra vale para qualquer origem - hoje a interface, amanha um atalho de
 * teclado, uma linha de comando ou um módulo novo.
 */
public class LembreteService {

    /** Evento publicado quando a lista muda, para as telas se atualizarem. */
    public record ListaMudou() {
    }

    private final LembreteDao dao = new LembreteDao();

    // -------------------------------------------------------------- consulta

    public List<Lembrete> listarTodos() {
        return dao.listarTodos();
    }

    public Optional<Lembrete> porId(long id) {
        return dao.porId(id);
    }

    /** Próxima vez que este lembrete vai acontecer. */
    public Optional<LocalDateTime> proximaOcorrencia(Lembrete lembrete) {
        if (!lembrete.isAtivo()) {
            return Optional.empty();
        }
        return CalculadoraOcorrencias.proxima(lembrete, LocalDateTime.now());
    }

    /** Lembretes ordenados pelo que acontece primeiro - a visao útil do dia a dia. */
    public List<Lembrete> listarPorProximidade() {
        return dao.listarTodos().stream()
                .sorted(Comparator
                        .comparing((Lembrete l) -> !l.isAtivo())
                        .thenComparing(l -> proximaOcorrencia(l).orElse(LocalDateTime.MAX)))
                .toList();
    }

    /** Todas as ocorrências dos próximos dias, para a agenda. */
    public List<Ocorrencia> agenda(int dias) {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime ate = agora.plusDays(dias);
        return dao.listarAtivos().stream()
                .flatMap(l -> CalculadoraOcorrencias.entre(l, agora, ate).stream()
                        .map(quando -> new Ocorrencia(l, quando)))
                .sorted(Comparator.comparing(Ocorrencia::quando))
                .toList();
    }

    // -------------------------------------------------------------- escrita

    /**
     * Grava o lembrete depois de validar.
     *
     * @throws IllegalArgumentException com a mensagem pronta para a tela
     */
    public Lembrete salvar(Lembrete lembrete) {
        String erro = lembrete.validar();
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        Lembrete salvo = dao.salvar(lembrete);
        EventBus.publicar(new ListaMudou());
        Scheduler.varrerAgora();   // um lembrete para daqui a 1 minuto já conta
        return salvo;
    }

    /**
     * Reordena a lista conforme o arranjo escolhido ao arrastar.
     *
     * A partir do primeiro arrasto, a lista passa a respeitar a sua ordem em
     * vez da automática por proximidade — e continua respeitando até você
     * pedir o contrário.
     */
    public void reordenar(List<Lembrete> naNovaOrdem) {
        dao.gravarOrdem(naNovaOrdem);
        EventBus.publicar(new ListaMudou());
    }

    /** A lista está em ordem manual? */
    public boolean temOrdemManual() {
        return dao.temOrdemManual();
    }

    /**
     * Volta para a ordenação automática.
     *
     * Zera a ordem de todos: nada se perde, apenas o critério muda.
     */
    public void voltarAOrdemAutomatica() {
        List<Lembrete> todos = dao.listarTodos();
        todos.forEach(l -> l.setOrdem(-1));
        for (Lembrete l : todos) {
            dao.salvar(l);
        }
        EventBus.publicar(new ListaMudou());
    }

    /** Lembretes na ordem que você definiu arrastando. */
    public List<Lembrete> listarNaMinhaOrdem() {
        return dao.listarTodos().stream()
                .sorted(Comparator
                        // Quem nunca foi arrastado vai para o fim.
                        .comparingInt((Lembrete l) -> l.getOrdem() < 0 ? Integer.MAX_VALUE : l.getOrdem())
                        .thenComparing(Lembrete::getId))
                .toList();
    }

    /** Lembretes que estão na lixeira. */
    public List<Lembrete> listarExcluidos() {
        return dao.listarExcluidos();
    }

    /** Tira o lembrete da lixeira e o devolve à lista. */
    public void restaurar(long id) {
        dao.restaurar(id);
        EventBus.publicar(new ListaMudou());
        Scheduler.varrerAgora();
    }

    public void excluir(long id) {
        dao.excluir(id);
        EventBus.publicar(new ListaMudou());
    }

    public void alternarAtivo(Lembrete lembrete) {
        dao.definirAtivo(lembrete.getId(), !lembrete.isAtivo());
        lembrete.setAtivo(!lembrete.isAtivo());
        EventBus.publicar(new ListaMudou());
    }

    /** Duplica um lembrete, útil para criar variacoes de uma reunião. */
    public Lembrete duplicar(Lembrete original) {
        Lembrete copia = new Lembrete();
        copia.setTitulo(original.getTitulo() + " (cópia)");
        copia.setDescricao(original.getDescricao());
        copia.setSensivel(original.isSensivel());
        copia.setTipo(original.getTipo());
        copia.setInicio(original.getInicio());
        copia.setFim(original.getFim());
        copia.setDiasSemana(new java.util.LinkedHashSet<>(original.getDiasSemana()));
        copia.setDiaMes(original.getDiaMes());
        copia.setIntervaloMinutos(original.getIntervaloMinutos());
        copia.setAntecedencias(new java.util.ArrayList<>(original.getAntecedencias()));
        copia.setCor(original.getCor());
        copia.setAcao(original.getAcao());
        copia.setSomAtivo(original.isSomAtivo());
        return salvar(copia);
    }

    // ------------------------------------------------------ alertas na tela

    /** Marca o alerta como visto. */
    /**
     * Marca o aviso como visto e, se o lembrete acabou, arquiva-o.
     *
     * "Acabou" significa que não existe mais nenhuma ocorrência pela frente:
     * é o caso de um lembrete único depois de confirmado, e de um repetido
     * cuja data de término já passou. Sem isso, a lista vai acumulando
     * compromissos resolvidos que nunca mais vão disparar.
     *
     * Três situações impedem o arquivamento, e todas importam:
     *
     *  - ainda há ocorrência futura (um lembrete diário, por exemplo);
     *  - ainda falta um aviso desta mesma ocorrência — confirmar o "1 dia
     *    antes" não pode fazer sumir o lembrete que ainda vai avisar na hora;
     *  - existe um adiamento esperando para reaparecer.
     *
     * Arquivar aqui é exclusão lógica: o lembrete vai para a lixeira e pode
     * ser restaurado. Nada sai do banco.
     *
     * @return true se o lembrete foi arquivado
     */
    public boolean reconhecer(long lembreteId, LocalDateTime ocorrencia) {
        dao.reconhecer(lembreteId, ocorrencia);

        Optional<Lembrete> encontrado = dao.porId(lembreteId);
        if (encontrado.isEmpty()) {
            return false;
        }
        Lembrete lembrete = encontrado.get();

        if (!concluiu(lembrete, ocorrencia)) {
            return false;
        }

        dao.excluir(lembreteId);
        Log.info("Lembrete " + lembreteId + " concluido e arquivado.");
        EventBus.publicar(new ListaMudou());
        return true;
    }

    /** O lembrete cumpriu o seu papel e não tem mais nada pela frente? */
    private boolean concluiu(Lembrete lembrete, LocalDateTime ocorrenciaConfirmada) {
        if (lembrete.isExcluido() || !lembrete.isAtivo()) {
            return false;
        }
        if (dao.temAdiamentoPendente(lembrete.getId())) {
            return false;
        }
        // Sobrou algum aviso desta mesma ocorrência para sair?
        for (int antecedencia : lembrete.getAntecedencias()) {
            if (!dao.jaDisparou(lembrete.getId(), ocorrenciaConfirmada, antecedencia)) {
                return false;
            }
        }
        // E alguma ocorrência futura?
        return CalculadoraOcorrencias.proxima(lembrete, LocalDateTime.now()).isEmpty();
    }

    /** Adia o alerta pelos minutos configurados. */
    public void adiar(long lembreteId, LocalDateTime ocorrencia, int minutos) {
        dao.reconhecer(lembreteId, ocorrencia);
        dao.adiar(lembreteId, ocorrencia, LocalDateTime.now().plusMinutes(minutos));
        Log.info("Lembrete " + lembreteId + " adiado por " + minutos + " min.");
    }

    /** Avisos que dispararam enquanto você estava fora. */
    public List<LembreteDao.Disparo> pendentes() {
        return dao.pendentes(50);
    }

    /**
     * Abre a ação associada ao lembrete: um link, uma pasta, um arquivo ou um
     * programa. E o que transforma "Reunião às 14h" em um botao que já abre a
     * sala, ou "rodar deploy" em um atalho para o script.
     */
    public void executarAcao(Lembrete lembrete) {
        String acao = lembrete.getAcao();
        if (acao == null || acao.isBlank()) {
            return;
        }
        try {
            if (acao.startsWith("http://") || acao.startsWith("https://")) {
                Desktop.getDesktop().browse(new URI(acao.trim()));
            } else {
                File alvo = new File(acao.trim());
                if (alvo.exists()) {
                    Desktop.getDesktop().open(alvo);
                } else {
                    Log.aviso("A ação do lembrete aponta para algo que não existe.");
                }
            }
        } catch (Exception e) {
            Log.erro("Falha ao executar a ação do lembrete " + lembrete.getId(), e);
        }
    }

    /** Faxina periodica do histórico de disparos. */
    public void limpar() {
        dao.limparDisparosAntigos(Math.max(30, Config.get().diasDeCatchUp));
    }

    /** Um lembrete em um horário específico - a linha da agenda. */
    public record Ocorrencia(Lembrete lembrete, LocalDateTime quando) {
    }
}
