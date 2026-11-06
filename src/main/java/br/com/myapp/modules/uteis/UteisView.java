package br.com.myapp.modules.uteis;

import br.com.myapp.core.Config;
import br.com.myapp.core.EventBus;
import br.com.myapp.modules.ModuleRegistry;
import br.com.myapp.modules.uteis.sql.PainelFormatarSql;
import br.com.myapp.modules.uteis.youtube.PainelYoutube;
import br.com.myapp.ui.EstadoVazio;
import br.com.myapp.ui.Icone;
import br.com.myapp.ui.Shell;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Tela do módulo Úteis: uma ferramenta por bloco, um embaixo do outro.
 *
 * <p>Cada ferramenta é uma {@link br.com.myapp.ui.Secao} independente e pode
 * ser desligada em Configurações &gt; Exibição, pendurada sob o Úteis. O bloco
 * só é criado na primeira vez que aparece: com o baixador desligado, o MyApp
 * nem procura o yt-dlp.
 *
 * <p>Para acrescentar uma ferramenta: o painel, uma chave em {@code Config}
 * e uma linha em {@link #aplicarExibicao()}.
 */
public class UteisView extends BorderPane {

    private final VBox ferramentas = new VBox(22);
    private PainelAreaTransferencia areaTransferencia;
    private PainelGeradorSenha geradorSenha;
    private PainelFormatarSql formatarSql;
    private PainelRegua regua;
    private PainelYoutube youtube;

    public UteisView() {
        getStyleClass().add("conteudo");

        setTop(Shell.cabecalho("Úteis"));

        ferramentas.setPadding(new Insets(0, 8, 24, 0));
        ScrollPane rolagem = new ScrollPane(ferramentas);
        rolagem.setFitToWidth(true);
        rolagem.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rolagem.getStyleClass().add("rolagem-formulario");
        setCenter(rolagem);

        EventBus.ouvir(ModuleRegistry.ExibicaoMudou.class,
                e -> Platform.runLater(this::aplicarExibicao));
        aplicarExibicao();
    }

    /** Mostra os blocos ligados, na ordem de sempre; nenhum ligado, o vazio. */
    private void aplicarExibicao() {
        Config config = Config.get();
        ferramentas.getChildren().clear();

        if (config.mostrarAreaTransferencia) {
            if (areaTransferencia == null) {
                areaTransferencia = new PainelAreaTransferencia();
            }
            ferramentas.getChildren().add(areaTransferencia);
        }
        if (config.mostrarGeradorSenha) {
            if (geradorSenha == null) {
                geradorSenha = new PainelGeradorSenha();
            }
            ferramentas.getChildren().add(geradorSenha);
        }
        if (config.mostrarFormatarSql) {
            if (formatarSql == null) {
                formatarSql = new PainelFormatarSql();
            }
            ferramentas.getChildren().add(formatarSql);
        }
        if (config.mostrarRegua) {
            if (regua == null) {
                regua = new PainelRegua();
            }
            ferramentas.getChildren().add(regua);
        }
        if (config.mostrarYoutube) {
            if (youtube == null) {
                youtube = new PainelYoutube();
            }
            ferramentas.getChildren().add(youtube);
        }

        if (ferramentas.getChildren().isEmpty()) {
            ferramentas.getChildren().add(
                    new EstadoVazio(Icone.Simbolo.FERRAMENTA, "Todas as ferramentas estão desligadas.")
                            .comExplicacao("Ligue as que você usa em Configurações → Exibição, "
                                    + "logo abaixo do Úteis.")
                            .emCartao());
        }
    }
}
