package br.com.myapp.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes da criptografia.
 *
 * Verificam as garantias que o desenho promete: o conteúdo volta igual com a
 * chave certa, não volta com a chave errada, não vaza em texto claro e o
 * mesmo texto nunca produz dois resultados iguais.
 *
 * O número de iteracoes do PBKDF2 e alto de propósito, entao estes testes
 * levam alguns segundos - e esse custo e justamente a proteção contra quem
 * tentar adivinhar a senha por forca bruta.
 */
class CryptoServiceTest {

    @Test
    @DisplayName("Texto cifrado volta identico com a chave certa")
    void idaEVolta() {
        SecretKey chave = CryptoService.novaChave();
        String original = "Senha do servidor de homologacao: Não deve vazar";

        String cifrado = CryptoService.cifrarTexto(original, chave);
        String recuperado = CryptoService.decifrarTexto(cifrado, chave);

        assertEquals(original, recuperado);
    }

    @Test
    @DisplayName("O texto original não aparece no conteúdo cifrado")
    void naoVazaTextoClaro() {
        SecretKey chave = CryptoService.novaChave();
        String segredo = "ADMIN-2026";

        String cifrado = CryptoService.cifrarTexto(segredo, chave);

        assertFalse(cifrado.contains(segredo));
        assertTrue(cifrado.startsWith(CryptoService.PREFIXO));
    }

    @Test
    @DisplayName("Chave errada não decifra")
    void chaveErradaFalha() {
        SecretKey certa = CryptoService.novaChave();
        SecretKey errada = CryptoService.novaChave();

        String cifrado = CryptoService.cifrarTexto("Conteúdo protegido", certa);

        assertThrows(SenhaIncorretaException.class,
                () -> CryptoService.decifrarTexto(cifrado, errada));
    }

    @Test
    @DisplayName("O mesmo texto cifrado duas vezes gera resultados diferentes")
    void ivAleatorioPorRegistro() {
        SecretKey chave = CryptoService.novaChave();

        String a = CryptoService.cifrarTexto("mesmo texto", chave);
        String b = CryptoService.cifrarTexto("mesmo texto", chave);

        // Se fossem iguais, daria para descobrir quais registros tem o mesmo
        // conteúdo apenas olhando o banco.
        assertNotEquals(a, b);
        assertEquals("mesmo texto", CryptoService.decifrarTexto(a, chave));
        assertEquals("mesmo texto", CryptoService.decifrarTexto(b, chave));
    }

    @Test
    @DisplayName("Conteúdo adulterado e recusado em vez de devolver lixo")
    void detectaAdulteracao() {
        SecretKey chave = CryptoService.novaChave();
        String cifrado = CryptoService.cifrarTexto("valor original", chave);

        // Troca um caractere do meio do Base64.
        int meio = cifrado.length() / 2;
        char trocado = cifrado.charAt(meio) == 'A' ? 'B' : 'A';
        String adulterado = cifrado.substring(0, meio) + trocado + cifrado.substring(meio + 1);

        assertThrows(SenhaIncorretaException.class,
                () -> CryptoService.decifrarTexto(adulterado, chave));
    }

    @Test
    @DisplayName("A mesma senha com o mesmo salt gera sempre a mesma chave")
    void derivacaoDeterministica() {
        byte[] salt = CryptoService.novoSalt();

        SecretKey a = CryptoService.derivarChave("senha-de-teste-123".toCharArray(), salt, 10_000);
        SecretKey b = CryptoService.derivarChave("senha-de-teste-123".toCharArray(), salt, 10_000);

        assertArrayEquals(a.getEncoded(), b.getEncoded());
    }

    @Test
    @DisplayName("Salts diferentes geram chaves diferentes para a mesma senha")
    void saltMudaAChave() {
        char[] senha = "senha-de-teste-123".toCharArray();

        SecretKey a = CryptoService.derivarChave(senha.clone(), CryptoService.novoSalt(), 10_000);
        SecretKey b = CryptoService.derivarChave(senha.clone(), CryptoService.novoSalt(), 10_000);

        assertFalse(Arrays.equals(a.getEncoded(), b.getEncoded()));
    }

    @Test
    @DisplayName("Bytes grandes sobrevivem a ida e volta")
    void cifraBinarioGrande() {
        SecretKey chave = CryptoService.novaChave();
        byte[] original = new byte[200_000];
        new java.util.Random(42).nextBytes(original);

        byte[] recuperado = CryptoService.decifrar(CryptoService.cifrar(original, chave), chave);

        assertArrayEquals(original, recuperado);
    }

    @Test
    @DisplayName("Acentos e emojis voltam intactos")
    void suportaUnicode() {
        SecretKey chave = CryptoService.novaChave();
        String original = "Reunião de producao as 14h 🔔 - ambiente não homologado";

        String recuperado = CryptoService.decifrarTexto(
                CryptoService.cifrarTexto(original, chave), chave);

        assertEquals(original, recuperado);
        assertArrayEquals(original.getBytes(StandardCharsets.UTF_8),
                recuperado.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Limpeza zera o array da senha")
    void limpezaDeMemoria() {
        char[] senha = "minha-senha".toCharArray();
        CryptoService.limpar(senha);

        for (char c : senha) {
            assertEquals('\0', c);
        }
    }

    @Test
    @DisplayName("Senha curta ou sem variedade e recusada")
    void validacaoDeForca() {
        assertThrows(IllegalArgumentException.class,
                () -> SecurityService.validarForca("abc".toCharArray()));
        assertThrows(IllegalArgumentException.class,
                () -> SecurityService.validarForca("somenteletras".toCharArray()));

        // Esta passa: comprimento suficiente e mistura de tipos.
        SecurityService.validarForca("minhaSenha2026".toCharArray());
    }
}
