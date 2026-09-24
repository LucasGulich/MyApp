package br.com.myapp.security;

import br.com.myapp.core.EventBus;
import br.com.myapp.core.Log;
import br.com.myapp.data.Database;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;

/**
 * Guarda de acesso do aplicativo: cria a senha mestra, destranca, tranca e
 * mantem a chave de dados viva apenas enquanto o app esta destrancado.
 *
 * A chave (DEK) só existe na memória. Ao trancar, a referência e descartada e
 * qualquer conteúdo protegido volta a ser ilegível até você digitar a senha
 * de novo - inclusive para quem tenha acesso ao arquivo do banco.
 */
public final class SecurityService {

    /** Evento publicado sempre que o estado de bloqueio muda. */
    public record EstadoMudou(boolean destrancado) {
    }

    private static SecretKey chaveDados;   // DEK em memória; null = trancado
    private static boolean configurado;
    private static boolean configuradoLido;

    private SecurityService() {
    }

    // --------------------------------------------------------------- estado

    /** Já existe senha mestra definida? */
    public static synchronized boolean estaConfigurado() {
        if (!configuradoLido) {
            try (PreparedStatement ps = Database.conexao()
                    .prepareStatement("SELECT COUNT(*) FROM cofre_meta WHERE id = 1");
                 ResultSet rs = ps.executeQuery()) {
                configurado = rs.next() && rs.getInt(1) > 0;
                configuradoLido = true;
            } catch (SQLException e) {
                throw new IllegalStateException("Falha ao consultar o cofre", e);
            }
        }
        return configurado;
    }

    public static synchronized boolean estaDestrancado() {
        return chaveDados != null;
    }

    /**
     * Chave usada para cifrar e decifrar conteúdo protegido.
     *
     * @throws IllegalStateException se o aplicativo estiver trancado
     */
    public static synchronized SecretKey chave() {
        if (chaveDados == null) {
            throw new IllegalStateException("O aplicativo está trancado");
        }
        return chaveDados;
    }

    // ------------------------------------------------------------- operacoes

    /**
     * Define a senha mestra pela primeira vez.
     *
     * Gera uma DEK aleatória, deriva a KEK da senha e grava apenas a DEK
     * cifrada. A senha em si não vai para lugar nenhum.
     *
     * @param senha limpa dentro do método; Não reutilize o array depois
     * @param dica  lembrete opcional mostrado na tela de bloqueio
     */
    public static synchronized void definirSenha(char[] senha, String dica) {
        if (estaConfigurado()) {
            throw new IllegalStateException("A senha mestra já foi definida");
        }
        validarForca(senha);
        try {
            byte[] salt = CryptoService.novoSalt();
            SecretKey kek = CryptoService.derivarChave(senha, salt, CryptoService.ITERACOES);
            SecretKey dek = CryptoService.novaChave();
            byte[] dekCifrada = CryptoService.cifrar(dek.getEncoded(), kek);

            long agora = Instant.now().toEpochMilli();
            try (PreparedStatement ps = Database.conexao().prepareStatement(
                    "INSERT INTO cofre_meta(id, salt, iteracoes, dek_cifrada, dica, criado_em, atualizado_em) "
                            + "VALUES(1, ?, ?, ?, ?, ?, ?)")) {
                ps.setBytes(1, salt);
                ps.setInt(2, CryptoService.ITERACOES);
                ps.setBytes(3, dekCifrada);
                ps.setString(4, dica);
                ps.setLong(5, agora);
                ps.setLong(6, agora);
                ps.executeUpdate();
            }

            configurado = true;
            configuradoLido = true;
            chaveDados = dek;
            Log.info("Senha mestra definida.");
            EventBus.publicar(new EstadoMudou(true));
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao gravar a configuração de segurança", e);
        } finally {
            CryptoService.limpar(senha);
        }
    }

    /**
     * Destranca o aplicativo.
     *
     * @param senha limpa dentro do método
     * @throws SenhaIncorretaException se a senha não abrir o cofre
     */
    public static synchronized void destrancar(char[] senha) {
        try (PreparedStatement ps = Database.conexao().prepareStatement(
                "SELECT salt, iteracoes, dek_cifrada FROM cofre_meta WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                throw new IllegalStateException("Nenhuma senha mestra definida");
            }
            byte[] salt = rs.getBytes("salt");
            int iteracoes = rs.getInt("iteracoes");
            byte[] dekCifrada = rs.getBytes("dek_cifrada");

            SecretKey kek = CryptoService.derivarChave(senha, salt, iteracoes);
            // Senha errada estoura SenhaIncorretaException exatamente aqui.
            byte[] dekBruta = CryptoService.decifrar(dekCifrada, kek);
            chaveDados = new SecretKeySpec(dekBruta, "AES");
            CryptoService.limpar(dekBruta);

            Log.info("Aplicativo destrancado.");
            EventBus.publicar(new EstadoMudou(true));
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao ler a configuração de segurança", e);
        } finally {
            CryptoService.limpar(senha);
        }
    }

    /**
     * Devolve o serviço ao estado inicial: sem chave e sem memória de que
     * existe senha cadastrada. Usado pelos testes ao trocar de banco.
     */
    public static synchronized void redefinir() {
        chaveDados = null;
        configurado = false;
        configuradoLido = false;
    }

    /** Tranca o aplicativo e descarta a chave da memória. */
    public static synchronized void trancar() {
        if (chaveDados != null) {
            chaveDados = null;
            Log.info("Aplicativo trancado.");
            EventBus.publicar(new EstadoMudou(false));
        }
    }

    /**
     * Troca a senha mestra.
     *
     * Como a DEK não muda, nenhum dado precisa ser reescrito: apenas a copia
     * cifrada da DEK e regravada, agora protegida pela chave nova.
     */
    public static synchronized void trocarSenha(char[] senhaAtual, char[] senhaNova, String novaDica) {
        try {
            validarForca(senhaNova);
            // Valida a senha atual e recarrega a DEK. Passa uma copia porque
            // destrancar() limpa o array que recebe.
            destrancar(senhaAtual.clone());

            byte[] salt = CryptoService.novoSalt();
            SecretKey kekNova = CryptoService.derivarChave(senhaNova, salt, CryptoService.ITERACOES);
            byte[] dekCifrada = CryptoService.cifrar(chaveDados.getEncoded(), kekNova);

            try (PreparedStatement ps = Database.conexao().prepareStatement(
                    "UPDATE cofre_meta SET salt = ?, iteracoes = ?, dek_cifrada = ?, dica = ?, "
                            + "atualizado_em = ? WHERE id = 1")) {
                ps.setBytes(1, salt);
                ps.setInt(2, CryptoService.ITERACOES);
                ps.setBytes(3, dekCifrada);
                ps.setString(4, novaDica);
                ps.setLong(5, Instant.now().toEpochMilli());
                ps.executeUpdate();
            }
            Log.info("Senha mestra trocada.");
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao trocar a senha", e);
        } finally {
            CryptoService.limpar(senhaAtual);
            CryptoService.limpar(senhaNova);
        }
    }

    /** Dica cadastrada, mostrada na tela de bloqueio. Pode ser nula. */
    public static String dica() {
        try (PreparedStatement ps = Database.conexao()
                .prepareStatement("SELECT dica FROM cofre_meta WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            return null;
        }
    }

    // ----------------------------------------------------------- utilitarios

    /**
     * Regras minimas de forca. São propositalmente simples: senha longa vale
     * mais que senha cheia de símbolos que você não consegue lembrar.
     */
    public static void validarForca(char[] senha) {
        if (senha == null || senha.length < 8) {
            throw new IllegalArgumentException("A senha mestra precisa ter pelo menos 8 caracteres.");
        }
        boolean temLetra = false;
        boolean temOutro = false;
        for (char c : senha) {
            if (Character.isLetter(c)) {
                temLetra = true;
            } else {
                temOutro = true;
            }
        }
        if (!temLetra || !temOutro) {
            throw new IllegalArgumentException(
                    "Misture letras com números ou símbolos na senha mestra.");
        }
    }

    /** Nota de 0 a 4 para a barra de forca na tela de cadastro. */
    public static int forca(char[] senha) {
        if (senha == null || senha.length == 0) {
            return 0;
        }
        int nota = 0;
        if (senha.length >= 8) {
            nota++;
        }
        if (senha.length >= 12) {
            nota++;
        }
        if (senha.length >= 16) {
            nota++;
        }

        boolean minuscula = false;
        boolean maiuscula = false;
        boolean digito = false;
        boolean simbolo = false;
        for (char c : senha) {
            if (Character.isLowerCase(c)) {
                minuscula = true;
            } else if (Character.isUpperCase(c)) {
                maiuscula = true;
            } else if (Character.isDigit(c)) {
                digito = true;
            } else {
                simbolo = true;
            }
        }
        int variedade = (minuscula ? 1 : 0) + (maiuscula ? 1 : 0)
                + (digito ? 1 : 0) + (simbolo ? 1 : 0);
        if (variedade >= 3) {
            nota++;
        }

        return Math.min(nota, 4);
    }
}
