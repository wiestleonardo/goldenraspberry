package br.com.outsera.goldenraspberry.constants;

public final class RegexConstants {

    private RegexConstants() {

    }

    // Vírgula (com ou sem "and" em seguida, cobrindo a vírgula de Oxford) ou "and" entre espaços. O
    // "and" é palavra inteira e não diferencia caixa ("AND", "And"); exigir espaço em volta evita
    // quebrar nomes como "Alexandra" ou "Anderson". Só o separador ignora caixa: os nomes não mudam.
    public static final String SEPARADOR_PRODUTOR = "\\s*,\\s*(?:(?i:and)\\s+)?|\\s+(?i:and)\\s+";

}
