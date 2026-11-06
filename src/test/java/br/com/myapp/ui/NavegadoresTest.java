package br.com.myapp.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Ler os navegadores da saída do reg query, como ela vem de verdade. */
class NavegadoresTest {

    /** Trecho real de uma máquina em português, com HKLM e a parte de 32 bits repetindo. */
    private static final String SAIDA = """

            HKEY_LOCAL_MACHINE\\SOFTWARE\\Clients\\StartMenuInternet\\Google Chrome
                (padrão)    REG_SZ    Google Chrome

            HKEY_LOCAL_MACHINE\\SOFTWARE\\Clients\\StartMenuInternet\\Google Chrome\\Capabilities
                ApplicationName    REG_SZ    Google Chrome

            HKEY_LOCAL_MACHINE\\SOFTWARE\\Clients\\StartMenuInternet\\Google Chrome\\shell\\open\\command
                (padrão)    REG_SZ    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"

            HKEY_LOCAL_MACHINE\\SOFTWARE\\Clients\\StartMenuInternet\\IEXPLORE.EXE
                (padrão)    REG_SZ    Internet Explorer

            HKEY_LOCAL_MACHINE\\SOFTWARE\\Clients\\StartMenuInternet\\IEXPLORE.EXE\\shell\\open\\command
                (padrão)    REG_SZ    C:\\Program Files\\Internet Explorer\\iexplore.exe

            HKEY_CURRENT_USER\\SOFTWARE\\Clients\\StartMenuInternet\\Opera GXStable
                (Default)    REG_SZ    Opera GX Stable

            HKEY_CURRENT_USER\\SOFTWARE\\Clients\\StartMenuInternet\\Opera GXStable\\shell\\open\\command
                (Default)    REG_SZ    "C:\\Users\\x\\AppData\\Local\\Programs\\Opera GX\\opera.exe" --single-argument

            HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\Clients\\StartMenuInternet\\Google Chrome
                (padrão)    REG_SZ    Google Chrome

            HKEY_LOCAL_MACHINE\\SOFTWARE\\WOW6432Node\\Clients\\StartMenuInternet\\Google Chrome\\shell\\open\\command
                (padrão)    REG_SZ    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
            """;

    @Test
    @DisplayName("Nome e .exe de cada navegador, sem o Internet Explorer e sem repetir o Chrome")
    void lerRegistro() {
        List<Navegadores.Navegador> lidos = Navegadores.doRegistro(SAIDA);
        assertEquals(List.of("Google Chrome", "Opera GX Stable"),
                lidos.stream().map(Navegadores.Navegador::nome).toList());
        assertEquals("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe", lidos.get(0).exe());
        assertEquals("C:\\Users\\x\\AppData\\Local\\Programs\\Opera GX\\opera.exe", lidos.get(1).exe());
    }

    @Test
    @DisplayName("Do comando de abrir, só o executável")
    void executavel() {
        assertEquals("C:\\a b\\x.exe", Navegadores.executavel("\"C:\\a b\\x.exe\" -- \"%1\""));
        assertEquals("C:\\x\\y.exe", Navegadores.executavel("C:\\x\\y.exe --flag"));
    }
}
