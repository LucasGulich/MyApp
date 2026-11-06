package br.com.myapp.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Preferências do aplicativo, gravadas em config.json.
 *
 * Aqui só entra coisa não sensível: tema, intervalos, caminhos. Qualquer dado
 * que precise de sigilo vai para o banco, criptografado.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {

    // ----- Aparência -----
    /** "escuro" ou "claro". */
    public String tema = "escuro";

    /**
     * Barra lateral recolhida, mostrando só os ícones.
     *
     * Fica guardado porque é preferência de quem usa, e não estado de uma
     * sessão: quem trabalha com a janela pequena recolhe uma vez e espera
     * encontrar assim na próxima abertura.
     */
    public boolean menuRecolhido = false;

    /** Como a agenda da tela inicial é vista: DIA, SEMANA, MES ou ANO. */
    public String visaoAgenda = "DIA";

    // ----- Exibição -----
    /**
     * Módulos tirados do menu lateral, pelo id ("kanban", "notas"...).
     *
     * Guarda os escondidos, e não os visíveis, para que um módulo novo
     * apareça sozinho depois de uma atualização: quem nunca ouviu falar dele
     * não teve como escolher escondê-lo.
     */
    public java.util.Set<String> modulosOcultos = new java.util.LinkedHashSet<>();

    /** Mostrar o mural de recados na tela inicial. */
    public boolean mostrarRecados = true;

    /** Úteis: mostrar o bloco da área de transferência. */
    public boolean mostrarAreaTransferencia = true;
    /** Úteis: mostrar o bloco do gerador de senha. */
    public boolean mostrarGeradorSenha = true;
    /** Úteis: mostrar o bloco do formatador de SQL. */
    public boolean mostrarFormatarSql = true;
    /** Úteis: mostrar o bloco do baixador do YouTube. */
    public boolean mostrarYoutube = true;
    /** Úteis: mostrar o bloco da régua de leitura. */
    public boolean mostrarRegua = true;

    // ----- Régua de leitura (Úteis) -----
    /** Altura da faixa, em pixels. */
    public int reguaAltura = 36;
    /** Largura da faixa, em pixels. */
    public int reguaLargura = 900;
    /** Opacidade do preenchimento, de 0.05 a 0.9. */
    public double reguaOpacidade = 0.35;
    /** Cor da faixa: AMARELO, VERDE, AZUL, ROSA ou LARANJA. */
    public String reguaCor = "AMARELO";
    /** Se a faixa já tem posição guardada; falso = centralizar na primeira vez. */
    public boolean reguaPosicionada = false;
    /** Onde a faixa ficou da última vez (pode ser negativo com mais de um monitor). */
    public double reguaX = 0;
    public double reguaY = 0;

    // ----- Segurança -----
    /** Minutos de inatividade até bloquear sozinho. 0 desliga o recurso. */
    public int minutosParaBloquear = 15;
    /** Bloquear também ao minimizar para a bandeja. */
    public boolean bloquearAoMinimizar = false;

    // ----- Lembretes -----
    /** Antecedências padrão de um lembrete novo, em minutos antes do evento. */
    public int[] antecedenciasPadrao = {5};
    /** Caminho de um .wav para o alerta. Vazio usa o som padrão do Windows. */
    public String somAlerta = "";
    /** Quantos dias para trás o app procura alertas perdidos ao abrir. */
    public int diasDeCatchUp = 7;
    /** Minutos que o botao "Adiar" empurra o alerta. */
    public int minutosSnooze = 10;

    // ----- Navegador -----
    /**
     * O .exe do navegador em que o MyApp abre os links. Vazio é o padrão do
     * Windows. Um atalho de site com navegador próprio vale mais que isto.
     */
    public String navegadorPadrao = "";

    // ----- Janela / sistema -----
    /** Iniciar junto com o Windows. */
    public boolean iniciarComWindows = false;
    /** Começar minimizado na bandeja. */
    public boolean iniciarMinimizado = false;
    /** Fechar no X apenas esconde na bandeja, em vez de encerrar. */
    public boolean fecharVaiParaBandeja = true;

    // ----- Backup -----
    /** Backup automático ligado. */
    public boolean backupAutomatico = true;
    /** Pasta de destino (normalmente dentro do OneDrive ou Google Drive). */
    public String pastaBackupNuvem = "";
    /** De quantas em quantas horas gerar um backup. */
    public int horasEntreBackups = 24;
    /** Quantos arquivos de backup manter antes de apagar os mais velhos. */
    public int backupsParaManter = 15;
    /** Momento do último backup (epoch millis). */
    public long ultimoBackupMillis = 0L;

    // ----- Úteis -----
    /** Pasta fixa dos vídeos e áudios baixados. Vazio usa a pasta Downloads do Windows. */
    public String pastaDownloads = "";
    /** Última escolha no baixador: VIDEO ou AUDIO. */
    public String ytTipo = "VIDEO";
    /** Altura máxima do vídeo (1080, 720...). 0 é a melhor disponível. */
    public int ytAlturaVideo = 0;
    /** MP3 ou M4A. */
    public String ytFormatoAudio = "MP3";
    /** Qualidade do áudio no formato do yt-dlp: "0" (máxima), "192K", "128K". */
    public String ytQualidadeAudio = "0";
    /** Link de playlist baixa a lista inteira, e não só o vídeo aberto. */
    public boolean ytPlaylist = false;
    /** Guardar capa, título e canal dentro do arquivo. */
    public boolean ytEmbutir = true;

    /** Formatar SQL: o banco (POSTGRESQL, MYSQL, ORACLE, SQL_SERVER, PADRAO). */
    public String sqlBanco = "POSTGRESQL";
    /** Formatar SQL: MAIUSCULAS, MINUSCULAS ou COMO_VEIO, só nas palavras-chave. */
    public String sqlCaixa = "MAIUSCULAS";
    /** Formatar SQL: o recuo — 4 espaços, 2 espaços ou tab. */
    public String sqlRecuo = "    ";

    // ----- Agendador -----
    /** última vez que o agendador varreu a agenda (epoch millis). */
    public long ultimaVarreduraMillis = 0L;

    // ================== infraestrutura ==================

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static Config instancia;

    public static synchronized Config get() {
        if (instancia == null) {
            instancia = carregar();
        }
        return instancia;
    }

    private static Config carregar() {
        Path arquivo = AppPaths.arquivoConfig();
        if (Files.exists(arquivo)) {
            try {
                return JSON.readValue(arquivo.toFile(), Config.class);
            } catch (IOException e) {
                Log.aviso("config.json ilegível, recriando com os padrões: " + e.getMessage());
            }
        }
        Config novo = new Config();
        novo.salvar();
        return novo;
    }

    /**
     * Esquece a instância carregada, para que a próxima chamada releia o
     * arquivo. Usado pelos testes ao trocar de pasta de dados.
     */
    public static synchronized void redefinir() {
        instancia = null;
    }

    public synchronized void salvar() {
        try {
            JSON.writeValue(AppPaths.arquivoConfig().toFile(), this);
        } catch (IOException e) {
            Log.erro("Não foi possível salvar config.json", e);
        }
    }
}
