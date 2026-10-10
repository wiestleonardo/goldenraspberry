package br.com.outsera.goldenraspberry.api;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import br.com.outsera.goldenraspberry.constants.RotaConstants;

/**
 * Testes de integração ponta a ponta: cada cenário sobe a aplicação com um arquivo de filmes próprio
 * (H2 em memória exclusivo do contexto), e a resposta HTTP real é comparada em modo estrito — campos,
 * tipos e ordem dos itens — com o resultado calculado à mão a partir do arquivo, descrito no
 * comentário de cada cenário.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class ProdutorControllerTest {

    private static final String ROTA_INTERVALOS_PREMIOS = RotaConstants.Produtor.BASE + RotaConstants.Produtor.INTERVALOS_PREMIOS;

    private static final String PROPRIEDADE_ARQUIVO_FILME = "filme.arquivo-csv=classpath:dataset/";

    @Autowired
    private RestTestClient restTestClient;

    // Movielist.csv original: Joel Silver vence em 1990 e 1991 (menor intervalo, 1); Matthew Vaughn
    // vence em 2002 e 2015 (maior intervalo, 13). Nenhum outro produtor empata nesses valores.
    @Test
    void deveRetornarIntervalosDoArquivoOriginalQuandoNenhumArquivoForConfigurado() {

        verificarIntervalos(this.restTestClient, """
                {
                  "min": [
                    { "producer": "Joel Silver", "interval": 1, "previousWin": 1990, "followingWin": 1991 }
                  ],
                  "max": [
                    { "producer": "Matthew Vaughn", "interval": 13, "previousWin": 2002, "followingWin": 2015 }
                  ]
                }
                """);
    }

    @Test
    void deveResponderJsonComCamposDoContratoQuandoConsultarIntervalos() {

        this.restTestClient.get()
                .uri(ROTA_INTERVALOS_PREMIOS)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$.min[0].length()").isEqualTo(4)
                .jsonPath("$.min[0].producer").isEqualTo("Joel Silver")
                .jsonPath("$.min[0].interval").isEqualTo(1)
                .jsonPath("$.min[0].previousWin").isEqualTo(1990)
                .jsonPath("$.min[0].followingWin").isEqualTo(1991)
                .jsonPath("$.max[0].length()").isEqualTo(4);
    }

    @Test
    void deveResponderNaoEncontradoQuandoRotaNaoExistir() {

        this.restTestClient.get()
                .uri(RotaConstants.Produtor.BASE + "/inexistente")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().json("""
                        { "codigo": "RECURSO_NAO_ENCONTRADO", "mensagem": "Recurso não encontrado." }
                        """, JsonCompareMode.STRICT);
    }

    @Test
    void deveResponderMetodoNaoSuportadoQuandoVerboNaoForGet() {

        this.restTestClient.method(HttpMethod.POST)
                .uri(ROTA_INTERVALOS_PREMIOS)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED)
                .expectHeader().valueEquals(HttpHeaders.ALLOW, HttpMethod.GET.name())
                .expectBody().json("""
                        { "codigo": "METODO_NAO_SUPORTADO", "mensagem": "Método HTTP não suportado para este recurso." }
                        """, JsonCompareMode.STRICT);
    }

    private static void verificarIntervalos(RestTestClient restTestClient, String jsonEsperado) {

        restTestClient.get()
                .uri(ROTA_INTERVALOS_PREMIOS)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody().json(jsonEsperado, JsonCompareMode.STRICT);
    }

    // A: 2000 e 2001 (1). B: 2005 e 2010 (5). C vence uma vez e não gera intervalo.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "consecutivas.csv")
    class ArquivoVitoriasConsecutivas {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveRetornarIntervaloDeUmAnoQuandoProdutorVencerEmAnosConsecutivos() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer A", "interval": 1, "previousWin": 2000, "followingWin": 2001 } ],
                      "max": [ { "producer": "Producer B", "interval": 5, "previousWin": 2005, "followingWin": 2010 } ]
                    }
                    """);
        }

    }

    // A: 1990, 1995 e 2010 -> pares consecutivos 1990-1995 (5) e 1995-2010 (15); o par 1990-2010
    // (20) não é consecutivo e não pode aparecer. B: 2000 e 2003 (3).
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "tres-vitorias.csv")
    class ArquivoTresVitoriasMesmoProdutor {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveConsiderarSomenteVitoriasConsecutivasQuandoProdutorVencerTresVezes() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer B", "interval": 3, "previousWin": 2000, "followingWin": 2003 } ],
                      "max": [ { "producer": "Producer A", "interval": 15, "previousWin": 1995, "followingWin": 2010 } ]
                    }
                    """);
        }

    }

    // C: 1980-1990 (10). B: 2010-2012 (2). A: 2000-2002 (2). Empate no mínimo entre A e B; o arquivo
    // traz B antes de A, e a resposta ordena por produtor.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "empate-minimo.csv")
    class ArquivoEmpateMinimo {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveRetornarTodosOsProdutoresQuandoHouverEmpateNoMenorIntervalo() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [
                        { "producer": "Producer A", "interval": 2, "previousWin": 2000, "followingWin": 2002 },
                        { "producer": "Producer B", "interval": 2, "previousWin": 2010, "followingWin": 2012 }
                      ],
                      "max": [ { "producer": "Producer C", "interval": 10, "previousWin": 1980, "followingWin": 1990 } ]
                    }
                    """);
        }

    }

    // B: 2000-2010 (10). A: 1980-1990 (10). C: 2015-2016 (1). Empate no máximo entre A e B.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "empate-maximo.csv")
    class ArquivoEmpateMaximo {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveRetornarTodosOsProdutoresQuandoHouverEmpateNoMaiorIntervalo() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer C", "interval": 1, "previousWin": 2015, "followingWin": 2016 } ],
                      "max": [
                        { "producer": "Producer A", "interval": 10, "previousWin": 1980, "followingWin": 1990 },
                        { "producer": "Producer B", "interval": 10, "previousWin": 2000, "followingWin": 2010 }
                      ]
                    }
                    """);
        }

    }

    // A: 2000, 2001, 2002, 2012, 2022 -> 1, 1, 10, 10. B: 1990-1991 (1). O mesmo produtor aparece
    // duas vezes no mínimo e duas vezes no máximo.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "empates-mesmo-produtor.csv")
    class ArquivoEmpatesMesmoProdutor {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveRetornarCadaIntervaloEmpatadoQuandoMesmoProdutorTiverVariosEmpates() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [
                        { "producer": "Producer A", "interval": 1, "previousWin": 2000, "followingWin": 2001 },
                        { "producer": "Producer A", "interval": 1, "previousWin": 2001, "followingWin": 2002 },
                        { "producer": "Producer B", "interval": 1, "previousWin": 1990, "followingWin": 1991 }
                      ],
                      "max": [
                        { "producer": "Producer A", "interval": 10, "previousWin": 2002, "followingWin": 2012 },
                        { "producer": "Producer A", "interval": 10, "previousWin": 2012, "followingWin": 2022 }
                      ]
                    }
                    """);
        }

    }

    // Separadores: "A, B and C" (entre aspas), "A, B, and C", "  A   and   B  ", "A,B , B" (repetido).
    // Ana Souza: 1990, 1997, 2000 -> 7, 3. Bruno Lima: 1990, 1992 -> 2. Carla Dias: 1990, 1992, 2000
    // -> 2, 8. Davi Rocha: 1992, 1997 -> 5. Alexandra Andrade (contém "and" sem espaços, não é
    // separada): 2001, 2003 -> 2. Se Ana Souza repetida no filme de 2000 contasse duas vezes, haveria
    // um intervalo 0 indevido.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "multiplos-produtores.csv")
    class ArquivoMultiplosProdutoresSeparadoresMistos {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveSepararProdutoresIndividuaisQuandoFilmeTiverSeparadoresMistos() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [
                        { "producer": "Alexandra Andrade", "interval": 2, "previousWin": 2001, "followingWin": 2003 },
                        { "producer": "Bruno Lima", "interval": 2, "previousWin": 1990, "followingWin": 1992 },
                        { "producer": "Carla Dias", "interval": 2, "previousWin": 1990, "followingWin": 1992 }
                      ],
                      "max": [ { "producer": "Carla Dias", "interval": 8, "previousWin": 1992, "followingWin": 2000 } ]
                    }
                    """);
        }

    }

    // Vitórias (winner "yes", "Yes", "YES", " yes "): A 2000 e 2010 (10); B 1990 e 1993 (3). As
    // indicações não vencedoras de A (2002, 2005), de B (1991) e de C não contam.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "nao-vencedores.csv")
    class ArquivoFilmesNaoVencedores {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveIgnorarFilmesNaoVencedoresQuandoCalcularIntervalos() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer B", "interval": 3, "previousWin": 1990, "followingWin": 1993 } ],
                      "max": [ { "producer": "Producer A", "interval": 10, "previousWin": 2000, "followingWin": 2010 } ]
                    }
                    """);
        }

    }

    // A vence com dois filmes em 2005 e um em 2007 -> 2005-2005 (0) e 2005-2007 (2). B: 1990-2000 (10).
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "anos-repetidos.csv")
    class ArquivoAnosRepetidos {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveRetornarIntervaloZeroQuandoProdutorVencerDuasVezesNoMesmoAno() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer A", "interval": 0, "previousWin": 2005, "followingWin": 2005 } ],
                      "max": [ { "producer": "Producer B", "interval": 10, "previousWin": 1990, "followingWin": 2000 } ]
                    }
                    """);
        }

    }

    // Nenhum produtor vence duas vezes (A tem uma vitória e uma indicação; C e D dividem um prêmio).
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "sem-intervalos.csv")
    class ArquivoSemIntervalos {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveRetornarListasVaziasQuandoNenhumProdutorVencerDuasVezes() {

            verificarIntervalos(this.restTestClient, """
                    { "min": [], "max": [] }
                    """);
        }

    }

    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "somente-cabecalho.csv")
    class ArquivoSomenteCabecalho {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveRetornarListasVaziasQuandoArquivoNaoTiverFilmes() {

            verificarIntervalos(this.restTestClient, """
                    { "min": [], "max": [] }
                    """);
        }

    }

    // Linhas embaralhadas. A: 1990, 1995, 2010 -> 5, 15. B: 2000, 2003 -> 3. C: 2008, 2018 -> 10.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "ordem-aleatoria.csv")
    class ArquivoOrdemAleatoria {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveOrdenarVitoriasPorAnoQuandoArquivoEstiverForaDeOrdem() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer B", "interval": 3, "previousWin": 2000, "followingWin": 2003 } ],
                      "max": [ { "producer": "Producer A", "interval": 15, "previousWin": 1995, "followingWin": 2010 } ]
                    }
                    """);
        }

    }

    // "Joel Silver" (1990, 2000 -> 10) e "joel silver" (1991, 2003 -> 12) são produtores distintos.
    // Se fossem unificados, as vitórias 1990, 1991, 2000, 2003 dariam mínimo 1 e máximo 9.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "caixa-diferente.csv")
    class ArquivoNomesComCaixaDiferente {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveTratarComoProdutoresDistintosQuandoNomesDiferiremSomenteNaCaixa() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Joel Silver", "interval": 10, "previousWin": 1990, "followingWin": 2000 } ],
                      "max": [ { "producer": "joel silver", "interval": 12, "previousWin": 1991, "followingWin": 2003 } ]
                    }
                    """);
        }

    }

    // Um único intervalo no arquivo (A: 2000-2004): ele é ao mesmo tempo o menor e o maior.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "intervalo-unico.csv")
    class ArquivoIntervaloUnico {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveRetornarMesmoIntervaloEmMinimoEMaximoQuandoHouverUmUnicoIntervalo() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer A", "interval": 4, "previousWin": 2000, "followingWin": 2004 } ],
                      "max": [ { "producer": "Producer A", "interval": 4, "previousWin": 2000, "followingWin": 2004 } ]
                    }
                    """);
        }

    }

    // Arquivo gerado no Windows: BOM UTF-8 antes do cabeçalho e quebras CRLF. A: 1980-1984 (4).
    // B: 1990-1991 (1). Producer C (entre aspas em 1980, "and" em 1991): 1980-1991 (11).
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "windows-crlf-bom.csv")
    class ArquivoWindowsComBom {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveImportarArquivoQuandoTiverQuebraDeLinhaWindowsEMarcaDeOrdemDeByte() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer B", "interval": 1, "previousWin": 1990, "followingWin": 1991 } ],
                      "max": [ { "producer": "Producer C", "interval": 11, "previousWin": 1980, "followingWin": 1991 } ]
                    }
                    """);
        }

    }

    // Descartadas: linha 2 (texto depois da aspa de fechamento) e linha 4 (aspa sem fechar). A leitura
    // segue nas linhas seguintes. Válidas: A 1991 e 2000 (9); B 2003 e 2004 (1), com aspas no meio do
    // título (literal) e título entre aspas corretas. Se a linha 2 contasse, A teria 1990-1991 (1)
    // empatando no mínimo; se a linha 4 contasse, A teria 1991-1995 (4) e 1995-2000 (5).
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "aspas-malformadas.csv")
    class ArquivoComAspasMalformadas {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveDescartarSomenteLinhasComAspasMalformadasQuandoContinuarImportacao() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer B", "interval": 1, "previousWin": 2003, "followingWin": 2004 } ],
                      "max": [ { "producer": "Producer A", "interval": 9, "previousWin": 1991, "followingWin": 2000 } ]
                    }
                    """);
        }

    }

    // "AND", "And" e ", AND" separam produtores como "and". Producer A: 1990, 1999 (9). Producer B:
    // 1990, 1992 (2). Producer C: 1992, 1999 (7). "Anderson Andrade" não é quebrado e "producer a"
    // (minúsculo) continua distinto de "Producer A" — se fossem unificados, A teria 1999-2001 (2)
    // empatando no mínimo.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "and-maiusculas.csv")
    class ArquivoSeparadorAndEmMaiusculas {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveSepararProdutoresQuandoAndEstiverEmQualquerCaixa() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer B", "interval": 2, "previousWin": 1990, "followingWin": 1992 } ],
                      "max": [ { "producer": "Producer A", "interval": 9, "previousWin": 1990, "followingWin": 1999 } ]
                    }
                    """);
        }

    }

    // Linha 3 repete a linha 2 e a linha 7 repete a linha 4 (só espaços nas extremidades e caixa do
    // winner diferem): são descartadas, então A tem 1990 e 2000 (10), e não um intervalo 0 artificial.
    // B vence com dois filmes distintos em 2005: o intervalo 0 é legítimo e se mantém.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "duplicatas.csv")
    class ArquivoComLinhasDuplicadas {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveIgnorarDuplicataExataEManterIntervaloZeroDeFilmesDistintosQuandoHouverLinhasRepetidas() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer B", "interval": 0, "previousWin": 2005, "followingWin": 2005 } ],
                      "max": [ { "producer": "Producer A", "interval": 10, "previousWin": 1990, "followingWin": 2000 } ]
                    }
                    """);
        }

    }

    // Válidas: A 2000 e 2010 (10); B 2012 e 2015 (3). Descartadas (ano "abc", colunas a menos, sem
    // título, sem produtor, winner "no", ano 0, colunas a mais) teriam dado a A vitórias em 2001-2005
    // e um mínimo de 1.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "linhas-invalidas.csv")
    class ArquivoComLinhasInvalidas {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveConsiderarSomenteLinhasValidasQuandoArquivoTiverLinhasInvalidas() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer B", "interval": 3, "previousWin": 2012, "followingWin": 2015 } ],
                      "max": [ { "producer": "Producer A", "interval": 10, "previousWin": 2000, "followingWin": 2010 } ]
                    }
                    """);
        }

    }

    // Colunas em outra ordem, com iniciais maiúsculas, uma coluna extra (ID) e ";" no fim (coluna sem
    // nome): cada campo é localizado pelo nome. A tem 1990 e 1995 (5) — o filme de 1991 não venceu;
    // B tem 2000 e 2001 (1). A linha de 2002 tem mais colunas que o cabeçalho e é descartada — se
    // fosse aceita, B teria 2001-2002 (1) empatando no mínimo.
    @Nested
    @TestPropertySource(properties = PROPRIEDADE_ARQUIVO_FILME + "colunas-outra-ordem.csv")
    class ArquivoColunasEmOutraOrdem {

        @Autowired
        private RestTestClient restTestClient;

        @Test
        void deveLocalizarColunasPeloNomeQuandoCabecalhoTiverOutraOrdemCaixaEColunasExtras() {

            verificarIntervalos(this.restTestClient, """
                    {
                      "min": [ { "producer": "Producer B", "interval": 1, "previousWin": 2000, "followingWin": 2001 } ],
                      "max": [ { "producer": "Producer A", "interval": 5, "previousWin": 1990, "followingWin": 1995 } ]
                    }
                    """);
        }

    }

}
