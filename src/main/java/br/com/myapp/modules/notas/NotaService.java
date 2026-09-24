package br.com.myapp.modules.notas;

import br.com.myapp.core.EventBus;
import br.com.myapp.core.Log;
import br.com.myapp.core.Texto;

import java.awt.Desktop;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Regras de negócio das notas.
 *
 * A tela conversa com esta classe, nunca direto com o banco.
 */
public class NotaService {

    /** Publicado quando a lista muda, para as telas se atualizarem. */
    public record ListaMudou() {
    }

    /** Publicado quando as categorias mudam. */
    public record CategoriasMudaram() {
    }

    private final NotaDao dao = new NotaDao();
    private final CategoriaDao categoriaDao = new CategoriaDao();

    // ------------------------------------------------------------ categorias

    public List<Categoria> listarCategorias() {
        return categoriaDao.listar();
    }

    public Optional<Categoria> categoriaPorId(long id) {
        return categoriaDao.porId(id);
    }

    public Categoria salvarCategoria(Categoria categoria) {
        String erro = categoria.validar();
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        if (categoriaDao.existeComNome(categoria.getNome(), categoria.getId())) {
            throw new IllegalArgumentException("Já existe uma categoria com esse nome.");
        }
        if (categoria.getId() == null) {
            categoria.setOrdem(categoriaDao.proximaOrdem());
        }
        Categoria salva = categoriaDao.salvar(categoria);
        EventBus.publicar(new CategoriasMudaram());
        return salva;
    }

    /**
     * Exclui a categoria.
     *
     * As notas de dentro não são excluídas junto: ficam sem categoria, e
     * seguem acessíveis. Apagar em cascata seria a forma mais rápida de alguém
     * perder trabalho por um clique errado.
     */
    public void excluirCategoria(long id) {
        for (Nota nota : dao.listarPorCategoria(id)) {
            nota.setCategoriaId(null);
            dao.salvar(nota);
        }
        categoriaDao.excluir(id);
        EventBus.publicar(new CategoriasMudaram());
        EventBus.publicar(new ListaMudou());
    }

    /**
     * Reordena as categorias conforme o arranjo escolhido ao arrastar.
     *
     * Aqui não existe "voltar à ordem automática", como em lembretes e notas:
     * a lista é curta e foi você quem a montou. Ordem alfabética não diz nada
     * sobre a importância de cada categoria, então a sua ordem é a única.
     */
    public void reordenarCategorias(List<Categoria> naNovaOrdem) {
        categoriaDao.gravarOrdem(naNovaOrdem);
        EventBus.publicar(new CategoriasMudaram());
    }

    /** Cria as categorias iniciais na primeira abertura do módulo. */
    public void criarCategoriasIniciaisSeVazio() {
        if (!categoriaDao.listar().isEmpty()) {
            return;
        }
        criarCategoria("Clientes", "#4C8DFF", "empresa");
        criarCategoria("Servidores", "#3ECF8E", "servidor");
        criarCategoria("Acessos internos", "#FFB648", "chave");
        criarCategoria("Comandos e SQL", "#7B5CFF", "codigo");
        criarCategoria("Pessoal", "#FF5D5D", "estrela");
        Log.info("Categorias iniciais criadas.");
    }

    private void criarCategoria(String nome, String cor, String icone) {
        Categoria c = new Categoria();
        c.setNome(nome);
        c.setCor(cor);
        c.setIcone(icone);
        c.setOrdem(categoriaDao.proximaOrdem());
        categoriaDao.salvar(c);
    }

    // ---------------------------------------------------------------- notas

    public List<Nota> listarTodas() {
        return dao.listar();
    }

    public List<Nota> listarPorCategoria(long categoriaId) {
        return dao.listarPorCategoria(categoriaId);
    }

    public List<Nota> listarSemCategoria() {
        return dao.listarSemCategoria();
    }

    public List<Nota> listarFavoritas() {
        return dao.listarFavoritas();
    }

    public List<Nota> listarExcluidas() {
        return dao.listarExcluidas();
    }

    public Optional<Nota> porId(long id) {
        return dao.porId(id);
    }

    public Nota salvar(Nota nota) {
        String erro = nota.validar();
        if (erro != null) {
            throw new IllegalArgumentException(erro);
        }
        Nota salva = dao.salvar(nota);
        EventBus.publicar(new ListaMudou());
        EventBus.publicar(new CategoriasMudaram());   // muda a contagem
        return salva;
    }

    public void excluir(long id) {
        dao.excluir(id);
        EventBus.publicar(new ListaMudou());
        EventBus.publicar(new CategoriasMudaram());
    }

    public void restaurar(long id) {
        dao.restaurar(id);
        EventBus.publicar(new ListaMudou());
        EventBus.publicar(new CategoriasMudaram());
    }

    public void alternarFavorita(Nota nota) {
        dao.definirFavorita(nota.getId(), !nota.isFavorita());
        nota.setFavorita(!nota.isFavorita());
        EventBus.publicar(new ListaMudou());
    }

    public void alternarFixada(Nota nota) {
        dao.definirFixada(nota.getId(), !nota.isFixada());
        nota.setFixada(!nota.isFixada());
        EventBus.publicar(new ListaMudou());
    }

    /**
     * Reordena as notas conforme o arranjo escolhido ao arrastar.
     *
     * A partir do primeiro arrasto, a lista passa a respeitar a sua ordem em
     * vez da automática por última alteração.
     */
    public void reordenar(List<Nota> naNovaOrdem) {
        dao.gravarOrdem(naNovaOrdem);
        EventBus.publicar(new ListaMudou());
    }

    /** A lista está em ordem manual? */
    public boolean temOrdemManual() {
        return dao.temOrdemManual();
    }

    /** Volta para a ordenação automática, zerando a ordem de todas. */
    public void voltarAOrdemAutomatica() {
        List<Nota> todas = dao.listar();
        for (Nota n : todas) {
            n.setOrdem(-1);
            dao.salvar(n);
        }
        EventBus.publicar(new ListaMudou());
    }

    /**
     * Ordena a lista recebida conforme a ordem manual.
     *
     * <p>As fixadas vêm antes de tudo, cada grupo na sua ordem arrastada:
     * fixar é "fica no topo", e não pode depender de qual ordenação está
     * escolhida. Até a v1.8 esta ordem ignorava a marca, e o botão parecia
     * não fazer nada.
     */
    public List<Nota> naMinhaOrdem(List<Nota> notas) {
        return notas.stream()
                .sorted(Comparator
                        .comparing((Nota n) -> !n.isFixada())
                        // Quem nunca foi arrastada vai para o fim do seu grupo.
                        .thenComparingInt((Nota n) -> n.getOrdem() < 0 ? Integer.MAX_VALUE : n.getOrdem())
                        .thenComparing(Nota::getId))
                .toList();
    }

    /** Duplica a nota, inclusive os campos. */
    public Nota duplicar(Nota original) {
        Nota copia = new Nota();
        copia.setTitulo(original.getTitulo() + " (cópia)");
        copia.setTipo(original.getTipo());
        copia.setCategoriaId(original.getCategoriaId());
        copia.setCorpo(original.getCorpo());
        copia.setProtegida(original.isProtegida());
        for (CampoNota campo : original.getCampos()) {
            copia.definirCampo(campo.getChave(), campo.getValor(), campo.isSensivel());
        }
        return salvar(copia);
    }

    // ---------------------------------------------------------------- busca

    /**
     * Busca por texto, sem ligar para acento nem maiúscula: "agua" acha
     * "Água". Ver {@link Texto}.
     *
     * Nota protegida com o aplicativo trancado fica de fora: o título nem
     * sequer foi decifrado, então procurar nele não faria sentido.
     */
    public List<Nota> buscar(String termo, List<Nota> universo) {
        if (termo == null || termo.isBlank()) {
            return universo;
        }
        String alvo = termo.trim();
        return universo.stream()
                .filter(n -> combina(n, alvo))
                .sorted(Comparator
                        .comparing(Nota::isFixada).reversed()
                        .thenComparing(Comparator.comparing(Nota::isFavorita).reversed())
                        .thenComparing(Nota::getAtualizadoEm, Comparator.reverseOrder()))
                .toList();
    }

    private boolean combina(Nota nota, String alvo) {
        if (contem(nota.getTitulo(), alvo) || contem(nota.getCorpo(), alvo)) {
            return true;
        }
        // O valor de um segredo nunca entra na busca: digitar parte de uma
        // senha e ver a nota aparecer já seria um vazamento.
        return nota.getCampos().stream()
                .filter(c -> !c.isSensivel())
                .anyMatch(c -> contem(c.getValor(), alvo) || contem(c.getChave(), alvo));
    }

    private boolean contem(String texto, String alvo) {
        return texto != null && Texto.contem(texto, alvo);
    }

    /** Valores anteriores de um segredo. */
    public List<NotaDao.ValorAnterior> historicoDoCampo(long notaId, String chave) {
        return dao.historico(notaId, chave);
    }

    // --------------------------------------------------------------- ações

    /** Abre o endereço de uma nota do tipo Link no navegador. */
    public void abrirLink(Nota nota) {
        String url = nota.valor("URL").orElse("").trim();
        if (url.isEmpty()) {
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            Log.erro("Falha ao abrir o link da nota " + nota.getId(), e);
            throw new IllegalStateException("Não foi possível abrir este endereço.");
        }
    }

    /** Abre o endereço de uma credencial, quando ela tiver um. */
    public void abrirEnderecoDaCredencial(Nota nota) {
        String url = nota.valor("Endereço").orElse("").trim();
        if (url.isEmpty()) {
            return;
        }
        try {
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            Log.erro("Falha ao abrir o endereço da nota " + nota.getId(), e);
        }
    }
}
