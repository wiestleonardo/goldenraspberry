package br.com.outsera.goldenraspberry.constants;

public final class RegexConstants {

    private RegexConstants() {

    }

    // Vírgula (com ou sem "and" em seguida, cobrindo a vírgula de Oxford) ou "and" entre espaços;
    // exigir espaço em volta do "and" evita quebrar nomes como "Alexandra" ou "Anderson"
    public static final String SEPARADOR_PRODUTOR = "\\s*,\\s*(?:and\\s+)?|\\s+and\\s+";

}
