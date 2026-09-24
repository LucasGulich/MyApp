package br.com.myapp.modules.notas;

import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colore um trecho de código para leitura.
 *
 * É um destacador simples, por expressão regular: reconhece comentários,
 * textos entre aspas, números e palavras reservadas. Não entende a linguagem
 * de verdade — e não precisa. O objetivo é que o olho encontre a estrutura do
 * trecho rapidamente, não compilar nada.
 *
 * Optei por isso em vez de trazer uma biblioteca de editor de código: seriam
 * megabytes de dependência, e mais um ponto de falha no instalador, para um
 * ganho que estas poucas regras já entregam.
 *
 * As cores saem do CSS (classes {@code cod-*}), então acompanham o tema claro
 * e o escuro sem nenhum ajuste aqui.
 */
public final class DestacadorSintaxe {

    /** Linguagens oferecidas no editor. */
    public static final List<String> LINGUAGENS = List.of(
            "Texto", "SQL", "Java", "JavaScript", "JSON", "XML / HTML",
            "PowerShell", "Batch / CMD", "Shell", "Python", "CSS");

    private DestacadorSintaxe() {
    }

    /** Monta o texto colorido. */
    public static TextFlow destacar(String codigo, String linguagem) {
        TextFlow fluxo = new TextFlow();
        fluxo.getStyleClass().add("codigo-visual");

        if (codigo == null || codigo.isEmpty()) {
            return fluxo;
        }

        Regras regras = regrasDe(linguagem);
        if (regras == null) {
            fluxo.getChildren().add(pedaco(codigo, "cod-normal"));
            return fluxo;
        }

        Matcher m = regras.padrao().matcher(codigo);
        int ultimo = 0;

        while (m.find()) {
            if (m.start() > ultimo) {
                fluxo.getChildren().add(pedaco(codigo.substring(ultimo, m.start()), "cod-normal"));
            }
            fluxo.getChildren().add(pedaco(m.group(), classeDoGrupo(m)));
            ultimo = m.end();
        }
        if (ultimo < codigo.length()) {
            fluxo.getChildren().add(pedaco(codigo.substring(ultimo), "cod-normal"));
        }
        return fluxo;
    }

    private static Text pedaco(String texto, String classe) {
        Text t = new Text(texto);
        t.getStyleClass().addAll("cod", classe);
        return t;
    }

    /** Descobre qual tipo de trecho casou, para escolher a cor. */
    private static String classeDoGrupo(Matcher m) {
        if (m.group("comentario") != null) {
            return "cod-comentario";
        }
        if (m.group("texto") != null) {
            return "cod-texto";
        }
        if (m.group("numero") != null) {
            return "cod-numero";
        }
        if (m.group("palavra") != null) {
            return "cod-palavra";
        }
        return "cod-normal";
    }

    // ------------------------------------------------------------- regras

    private record Regras(Pattern padrao) {
    }

    private static Regras regrasDe(String linguagem) {
        String nome = linguagem == null ? "" : linguagem.trim().toLowerCase();

        String palavras = switch (nome) {
            case "sql" -> "SELECT|FROM|WHERE|INSERT|INTO|VALUES|UPDATE|SET|DELETE|CREATE|TABLE|"
                    + "ALTER|DROP|INDEX|JOIN|INNER|LEFT|RIGHT|FULL|OUTER|ON|AND|OR|NOT|NULL|"
                    + "GROUP|BY|ORDER|HAVING|LIMIT|OFFSET|DISTINCT|AS|CASE|WHEN|THEN|ELSE|END|"
                    + "UNION|ALL|EXISTS|IN|BETWEEN|LIKE|IS|PRIMARY|KEY|FOREIGN|REFERENCES|"
                    + "DEFAULT|CONSTRAINT|UNIQUE|COUNT|SUM|AVG|MIN|MAX|COALESCE|CAST|BEGIN|"
                    + "COMMIT|ROLLBACK|TRANSACTION|WITH|RETURNING";
            case "java" -> "public|private|protected|class|interface|enum|record|extends|implements|"
                    + "new|return|if|else|for|while|do|switch|case|break|continue|try|catch|"
                    + "finally|throw|throws|static|final|void|int|long|double|float|boolean|"
                    + "char|byte|short|String|var|this|super|null|true|false|import|package|"
                    + "abstract|synchronized|volatile|transient|instanceof|default";
            case "javascript" -> "const|let|var|function|return|if|else|for|while|do|switch|case|"
                    + "break|continue|try|catch|finally|throw|new|class|extends|export|import|"
                    + "from|async|await|typeof|instanceof|null|undefined|true|false|this";
            case "json" -> "true|false|null";
            case "python" -> "def|class|return|if|elif|else|for|while|try|except|finally|raise|"
                    + "import|from|as|with|lambda|None|True|False|and|or|not|in|is|pass|"
                    + "break|continue|global|nonlocal|yield|async|await";
            case "powershell" -> "function|param|if|else|elseif|foreach|for|while|do|switch|"
                    + "try|catch|finally|throw|return|break|continue|begin|process|end|"
                    + "Write-Host|Write-Output|Get-\\w+|Set-\\w+|New-\\w+|Remove-\\w+|"
                    + "Select-Object|Where-Object|ForEach-Object|True|False|Null";
            case "batch / cmd", "batch", "cmd" -> "echo|set|if|else|for|goto|call|exit|rem|"
                    + "pause|cls|start|del|copy|move|md|mkdir|rd|cd|type|findstr|errorlevel|"
                    + "not|exist|defined|setlocal|endlocal";
            case "shell" -> "if|then|else|elif|fi|for|while|do|done|case|esac|function|return|"
                    + "export|local|echo|read|exit|source|alias|unset";
            case "css" -> "important|inherit|initial|unset|none|auto|block|flex|grid|absolute|"
                    + "relative|fixed|sticky|hidden|visible";
            case "xml / html", "xml", "html" -> null;   // tratado pelas tags
            default -> null;
        };

        if (nome.startsWith("xml") || nome.startsWith("html")) {
            return new Regras(Pattern.compile(
                    "(?<comentario><!--[\\s\\S]*?-->)"
                            + "|(?<texto>\"[^\"]*\"|'[^']*')"
                            + "|(?<palavra></?[A-Za-z][\\w:.-]*)"
                            + "|(?<numero>\\b\\d+(\\.\\d+)?\\b)"));
        }

        if (palavras == null) {
            return null;   // texto puro: sem destaque
        }

        String comentario = switch (nome) {
            case "sql" -> "--[^\\n]*|/\\*[\\s\\S]*?\\*/";
            case "python", "shell" -> "#[^\\n]*";
            case "powershell" -> "#[^\\n]*|<#[\\s\\S]*?#>";
            case "batch / cmd", "batch", "cmd" -> "(?i)^\\s*(rem|::)[^\\n]*";
            case "css" -> "/\\*[\\s\\S]*?\\*/";
            default -> "//[^\\n]*|/\\*[\\s\\S]*?\\*/";
        };

        String expressao = "(?<comentario>" + comentario + ")"
                + "|(?<texto>\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*')"
                + "|(?<numero>\\b\\d+(\\.\\d+)?\\b)"
                + "|(?<palavra>\\b(" + palavras + ")\\b)";

        int opcoes = Pattern.MULTILINE;
        // SQL e Batch não distinguem maiúsculas de minúsculas nas palavras.
        if (nome.equals("sql") || nome.startsWith("batch") || nome.equals("cmd")) {
            opcoes |= Pattern.CASE_INSENSITIVE;
        }
        return new Regras(Pattern.compile(expressao, opcoes));
    }
}
