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
