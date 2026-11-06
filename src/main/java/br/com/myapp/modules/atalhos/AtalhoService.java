package br.com.myapp.modules.atalhos;

import br.com.myapp.core.EventBus;
import br.com.myapp.core.Links;
import br.com.myapp.core.Log;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Regras dos atalhos: cadastro, ordem, exemplos da primeira vez — e abrir.
 */
public class AtalhoService {

    /** Publicado a cada mudança, para a tela redesenhar. */
    public record AtalhosMudaram() {
    }

    private final AtalhoDao dao = new AtalhoDao();

    public List<GrupoAtalho> grupos() {
        return dao.grupos();
    }

    /** Os três grupos do pedido original, só na primeira abertura. */
    public void criarGruposIniciaisNaPrimeiraVez() {
        if (dao.jaTeveGrupo()) {
            return;
        }
        for (String nome : List.of("Aplicativos do dia a dia", "Sites do dia a dia", "Pastas na rede")) {
            GrupoAtalho g = new GrupoAtalho();
            g.setNome(nome);
            dao.salvarGrupo(g);
        }
        Log.info("Grupos iniciais de atalhos criados.");
    }

    public GrupoAtalho salvarGrupo(GrupoAtalho g) {
        if (g.getNome().isBlank()) {
            throw new IllegalArgumentException("Dê um nome ao grupo.");
        }
        g.setNome(g.getNome().trim());
        dao.salvarGrupo(g);
        avisar();
        return g;
    }

    public void excluirGrupo(long id) {
        dao.excluirGrupo(id);
        avisar();
    }

    public void reordenarGrupos(List<GrupoAtalho> naNovaOrdem) {
        dao.gravarOrdemDosGrupos(naNovaOrdem);
        avisar();
    }

    public Atalho salvar(Atalho a) {
        if (a.getAlvo().isBlank()) {
            throw new IllegalArgumentException("Falta dizer o que o atalho abre.");
        }
        a.setAlvo(a.getAlvo().trim());
        if (a.getNome().isBlank()) {
            a.setNome(nomeSugerido(a.getAlvo(), a.getTipo()));
        }
        a.setNome(a.getNome().trim());
        dao.salvar(a);
        avisar();
        return a;
    }

    public void excluir(long id) {
        dao.excluir(id);
        avisar();
    }

    public void reordenar(List<Atalho> naNovaOrdem) {
        dao.gravarOrdemDosAtalhos(naNovaOrdem);
        avisar();
    }

    /** Muda de grupo: entra no fim do outro. */
    public void moverParaGrupo(Atalho a, long grupoId) {
        Atalho movido = a.copia();
        movido.setId(a.getId());
        movido.setGrupoId(grupoId);
        movido.setOrdem(Integer.MAX_VALUE / 2);
        dao.salvar(movido);
        avisar();
    }

    private void avisar() {
        EventBus.publicar(new AtalhosMudaram());
    }

    // ============================================================== abrir

    /**
     * Abre o atalho. Bloqueia (uma pasta de rede pode demorar): chamar fora
     * da thread da tela.
     *
     * @throws IOException com a mensagem que vai para a tela
     */
    public void abrir(Atalho a) throws IOException {
        switch (a.getTipo()) {
            // O navegador do atalho vale mais; sem ele, a preferência do MyApp;
            // sem ela, o Windows. Quem decide é o Links, igual para o app todo.
            case SITE -> Links.abrir(normalizarSite(a.getAlvo()), a.getNavegador());
            case APLICATIVO -> {
                File arquivo = existente(a.getAlvo());
                List<String> argumentos = partirArgumentos(a.getArgumentos());
                if (argumentos.isEmpty()) {
                    Desktop.getDesktop().open(arquivo);
                } else {
                    // Com argumentos não dá para ir pelo Desktop, que só
                    // "dá dois cliques". Roda direto, na pasta do programa.
                    List<String> comando = new ArrayList<>();
                    comando.add(arquivo.getAbsolutePath());
                    comando.addAll(argumentos);
                    new ProcessBuilder(comando).directory(arquivo.getParentFile()).start();
                }
            }
            case PASTA, ARQUIVO -> Desktop.getDesktop().open(existente(a.getAlvo()));
        }
        if (a.getId() != null) {
            dao.registrarUso(a.getId());
        }
    }

    private static File existente(String alvo) throws IOException {
        File f = new File(alvo.trim().replace("\"", ""));
        if (!f.exists()) {
            throw new IOException(alvo.startsWith("\\\\")
                    ? "Não consegui chegar em " + alvo + ". A rede ou o servidor estão acessíveis?"
                    : "Não achei " + alvo + ". Ele foi movido, renomeado ou desinstalado?");
        }
        return f;
    }

    // ===================================================== deduzir do alvo

    /**
     * O tipo mais provável para o que foi colado ou arrastado: link é site,
     * .exe/.lnk é aplicativo, pasta é pasta, o resto é arquivo.
     */
    public static TipoAtalho deduzirTipo(String alvo) {
        if (alvo == null || alvo.isBlank()) {
            return TipoAtalho.APLICATIVO;
        }
        String a = alvo.trim().toLowerCase(Locale.ROOT);
        if (a.startsWith("http://") || a.startsWith("https://") || a.startsWith("www.")) {
            return TipoAtalho.SITE;
        }
        if (a.endsWith(".exe") || a.endsWith(".lnk") || a.endsWith(".msc") || a.endsWith(".appref-ms")) {
            return TipoAtalho.APLICATIVO;
        }
        try {
            if (Files.isDirectory(Path.of(alvo.trim()))) {
                return TipoAtalho.PASTA;
            }
        } catch (Exception ignorado) {
            // caminho inválido: cai na regra do nome
        }
        // "\\servidor\compartilhamento" sem extensão no fim é pasta de rede.
        String ultimo = a.substring(Math.max(a.lastIndexOf('\\'), a.lastIndexOf('/')) + 1);
        if (a.startsWith("\\\\") && !ultimo.contains(".")) {
            return TipoAtalho.PASTA;
        }
        return TipoAtalho.ARQUIVO;
    }

    /** "C:\...\Teams.lnk" → "Teams"; "https://www.github.com/x" → "github.com". */
    public static String nomeSugerido(String alvo, TipoAtalho tipo) {
        if (alvo == null || alvo.isBlank()) {
            return "";
        }
        String a = alvo.trim();
        if (tipo == TipoAtalho.SITE) {
            try {
                String host = URI.create(normalizarSite(a)).getHost();
                if (host != null) {
                    return host.startsWith("www.") ? host.substring(4) : host;
                }
            } catch (Exception ignorado) {
                // devolve o texto como veio
            }
            return a;
        }
        while (a.endsWith("\\") || a.endsWith("/")) {
            a = a.substring(0, a.length() - 1);
        }
        String nome = a.substring(Math.max(a.lastIndexOf('\\'), a.lastIndexOf('/')) + 1);
        if (tipo == TipoAtalho.APLICATIVO || nome.toLowerCase(Locale.ROOT).endsWith(".lnk")) {
            int ponto = nome.lastIndexOf('.');
            if (ponto > 0) {
                nome = nome.substring(0, ponto);
            }
        }
        return nome.isBlank() ? a : nome;
    }

    /** "github.com" → "https://github.com". */
    public static String normalizarSite(String alvo) {
        String a = alvo.trim();
        String minusculo = a.toLowerCase(Locale.ROOT);
        if (minusculo.startsWith("http://") || minusculo.startsWith("https://")) {
            return a;
        }
        return "https://" + a;
    }

    /**
     * Separa os argumentos como o Windows: por espaço, menos dentro de aspas.
     * {@code -perfil "Minha Empresa" -x} → [-perfil, Minha Empresa, -x].
     */
    public static List<String> partirArgumentos(String texto) {
        List<String> partes = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return partes;
        }
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        boolean temAlgo = false;
        for (char c : texto.trim().toCharArray()) {
            if (c == '"') {
                entreAspas = !entreAspas;
                temAlgo = true;
            } else if (Character.isWhitespace(c) && !entreAspas) {
                if (temAlgo) {
                    partes.add(atual.toString());
                    atual.setLength(0);
                    temAlgo = false;
                }
            } else {
                atual.append(c);
                temAlgo = true;
            }
        }
        if (temAlgo) {
            partes.add(atual.toString());
        }
        return partes;
    }
}
