package br.com.myapp.modules.lembretes;

import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Faz o DatePicker falar português: dd/MM/yyyy na escrita e na leitura.
 *
 * Sem isto o JavaFX usaria o formato do sistema, que nem sempre e o esperado
 * e confunde na hora de digitar a data direto no campo.
 */
public final class ConversorData {

    public static final StringConverter<LocalDate> BRASILEIRO = new StringConverter<>() {

        private final DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        @Override
        public String toString(LocalDate data) {
            return data == null ? "" : formato.format(data);
        }

        @Override
        public LocalDate fromString(String texto) {
            if (texto == null || texto.isBlank()) {
                return null;
            }
            String limpo = texto.trim();
            try {
                return LocalDate.parse(limpo, formato);
            } catch (Exception e) {
                // Tolera "5/9/2026" e "05092026", digitados as pressas.
                try {
                    String[] partes = limpo.split("[/.-]");
                    if (partes.length == 3) {
                        return LocalDate.of(
                                Integer.parseInt(partes[2].trim()),
                                Integer.parseInt(partes[1].trim()),
                                Integer.parseInt(partes[0].trim()));
                    }
                    if (limpo.length() == 8) {
                        return LocalDate.of(
                                Integer.parseInt(limpo.substring(4)),
                                Integer.parseInt(limpo.substring(2, 4)),
                                Integer.parseInt(limpo.substring(0, 2)));
                    }
                } catch (Exception ignorado) {
                    // Data inválida: devolve nulo e o campo fica vazio.
                }
                return null;
            }
        }
    };

    private ConversorData() {
    }
}
