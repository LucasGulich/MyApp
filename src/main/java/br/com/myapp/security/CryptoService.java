package br.com.myapp.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Primitivas de criptografia do aplicativo.
 *
 * Desenho adotado:
 *
 *   senha mestra --PBKDF2-HMAC-SHA512--> KEK (chave que protege a chave)
 *                                         |
 *                                         v
 *                       DEK aleatória (AES-256) guardada cifrada no banco
 *                                         |
 *                                         v
 *                        AES-GCM em cada campo sensível, IV único por registro
 *
 * Duas chaves e não uma porque isso permite trocar a senha mestra sem
 * reescrever todo o banco: basta cifrar a mesma DEK com uma KEK nova.
 *
 * A senha nunca e gravada, nem em texto nem em forma reversivel. A validação
 * acontece por consequência: se a senha estiver errada, a DEK não decifra e o
 * AES-GCM acusa a adulteracao. Não existe "hash da senha" separado para alguém
 * atacar offline.
 */
public final class CryptoService {

    /** Iteracoes do PBKDF2. Acima da recomendacao atual da OWASP para SHA-512. */
    public static final int ITERACOES = 400_000;
    private static final String ALGORITMO_DERIVACAO = "PBKDF2WithHmacSHA512";

    private static final int TAMANHO_SALT = 16;   // 128 bits
    private static final int TAMANHO_CHAVE = 256; // bits
    private static final int TAMANHO_IV = 12;     // 96 bits, recomendado para GCM
    private static final int TAMANHO_TAG = 128;   // bits de autenticacao

    /** Marca do formato, para conseguir evoluir o esquema no futuro. */
    public static final String PREFIXO = "enc:v1:";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CryptoService() {
    }

    // ---------------------------------------------------------------- chaves

    public static byte[] novoSalt() {
        byte[] salt = new byte[TAMANHO_SALT];
        ALEATORIO.nextBytes(salt);
        return salt;
    }

    /** Gera uma chave AES-256 aleatória (usada como DEK). */
    public static SecretKey novaChave() {
        byte[] bruta = new byte[TAMANHO_CHAVE / 8];
        ALEATORIO.nextBytes(bruta);
        SecretKey chave = new SecretKeySpec(bruta, "AES");
        Arrays.fill(bruta, (byte) 0);
        return chave;
    }

    /**
     * Deriva a KEK a partir da senha mestra.
     *
     * Recebe char[] e não String de propósito: String fica na memória até o
     * coletor de lixo decidir removê-la, e não da para apagar. Com char[]
     * conseguimos zerar assim que terminamos.
     */
    public static SecretKey derivarChave(char[] senha, byte[] salt, int iteracoes) {
        PBEKeySpec spec = null;
        try {
            spec = new PBEKeySpec(senha, salt, iteracoes, TAMANHO_CHAVE);
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance(ALGORITMO_DERIVACAO);
            byte[] bruta = fabrica.generateSecret(spec).getEncoded();
            SecretKey chave = new SecretKeySpec(bruta, "AES");
            Arrays.fill(bruta, (byte) 0);
            return chave;
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao derivar a chave da senha", e);
        } finally {
            if (spec != null) {
                spec.clearPassword();
            }
        }
    }

    // ------------------------------------------------------------ cifragem

    /**
     * Cifra bytes com AES-GCM. O resultado carrega o IV na frente:
     * [ IV (12 bytes) ][ texto cifrado + tag de autenticacao ]
     */
    public static byte[] cifrar(byte[] claro, SecretKey chave) {
        try {
            byte[] iv = new byte[TAMANHO_IV];
            ALEATORIO.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG, iv));
            byte[] cifrado = cipher.doFinal(claro);

            return ByteBuffer.allocate(iv.length + cifrado.length)
                    .put(iv).put(cifrado).array();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao cifrar", e);
        }
    }

    /**
     * Decifra o formato produzido por {@link #cifrar}.
     *
     * @throws SenhaIncorretaException se a chave estiver errada ou o dado tiver sido alterado
     */
    public static byte[] decifrar(byte[] pacote, SecretKey chave) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(pacote);
            byte[] iv = new byte[TAMANHO_IV];
            buffer.get(iv);
            byte[] cifrado = new byte[buffer.remaining()];
            buffer.get(cifrado);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG, iv));
            return cipher.doFinal(cifrado);
        } catch (Exception e) {
            // GCM falha aqui tanto com senha errada quanto com dado adulterado.
            throw new SenhaIncorretaException("Não foi possível decifrar o conteúdo");
        }
    }

    // ------------------------------------------------- conveniencia em texto

    /** Cifra um texto e devolve algo como "enc:v1:BASE64...". */
    public static String cifrarTexto(String texto, SecretKey chave) {
        if (texto == null) {
            return null;
        }
        byte[] claro = texto.getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.getEncoder().encodeToString(cifrar(claro, chave));
        Arrays.fill(claro, (byte) 0);
        return PREFIXO + base64;
    }

    /** Decifra o formato acima. Texto sem o prefixo volta como veio. */
    public static String decifrarTexto(String armazenado, SecretKey chave) {
        if (armazenado == null || !estaCifrado(armazenado)) {
            return armazenado;
        }
        byte[] pacote = Base64.getDecoder().decode(armazenado.substring(PREFIXO.length()));
        return new String(decifrar(pacote, chave), StandardCharsets.UTF_8);
    }

    public static boolean estaCifrado(String valor) {
        return valor != null && valor.startsWith(PREFIXO);
    }

    // --------------------------------------------------------------- limpeza

    /** Zera um array de caracteres. Use sempre que terminar de usar uma senha. */
    public static void limpar(char[] dados) {
        if (dados != null) {
            Arrays.fill(dados, '\0');
        }
    }

    public static void limpar(byte[] dados) {
        if (dados != null) {
            Arrays.fill(dados, (byte) 0);
        }
    }

    /** Comparacao em tempo constante, para não vazar informação pelo tempo de resposta. */
    public static boolean iguais(byte[] a, byte[] b) {
        return java.security.MessageDigest.isEqual(a, b);
    }
}
