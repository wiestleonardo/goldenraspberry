package br.com.outsera.goldenraspberry.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.client.RestTestClient;

import br.com.outsera.goldenraspberry.Application;
import br.com.outsera.goldenraspberry.constants.RotaConstants;
import br.com.outsera.goldenraspberry.enumerator.ErroEnum;
import br.com.outsera.goldenraspberry.exception.BusinessServiceException;

/**
 * Testes de integração da importação na inicialização: cada teste sobe a aplicação completa (porta
 * HTTP aleatória, H2 em memória exclusivo) com o arquivo informado e a encerra ao final.
 */
@ExtendWith(OutputCaptureExtension.class)
class ImportacaoFilmeJobTest {

    private static final String ARQUIVO_DATASET = "classpath:dataset/";

    @Test
    void deveInterromperInicializacaoQuandoArquivoNaoExistir() {

        assertThatThrownBy(() -> iniciarAplicacao(ARQUIVO_DATASET + "inexistente.csv"))
                .isInstanceOf(BusinessServiceException.class)
                .extracting(excecao -> ((BusinessServiceException) excecao).getErroEnum())
                .isEqualTo(ErroEnum.ARQUIVO_FILME_NAO_ENCONTRADO);
    }

    @Test
    void deveInterromperInicializacaoQuandoCabecalhoForIncompativel() {

        assertThatThrownBy(() -> iniciarAplicacao(ARQUIVO_DATASET + "cabecalho-invalido.csv"))
                .isInstanceOf(BusinessServiceException.class)
                .extracting(excecao -> ((BusinessServiceException) excecao).getErroEnum())
                .isEqualTo(ErroEnum.CABECALHO_ARQUIVO_FILME_INVALIDO);
    }

    @Test
    void deveRegistrarAvisoComLinhaEMotivoQuandoLinhaForInvalida(CapturedOutput capturedOutput) {

        try (ConfigurableApplicationContext configurableApplicationContext = iniciarAplicacao(ARQUIVO_DATASET + "linhas-invalidas.csv")) {

            assertThat(capturedOutput.getOut())
                    .contains("Linha 3 do arquivo de filmes descartada: ano ausente, não numérico ou menor que 1")
                    .contains("Linha 4 do arquivo de filmes descartada: quantidade de colunas diferente do cabeçalho")
                    .contains("Linha 5 do arquivo de filmes descartada: título ausente")
                    .contains("Linha 6 do arquivo de filmes descartada: nenhum produtor informado")
                    .contains("Linha 7 do arquivo de filmes descartada: indicador de vencedor diferente de yes ou vazio")
                    .contains("Linha 9 do arquivo de filmes descartada: ano ausente, não numérico ou menor que 1")
                    .contains("Linha 10 do arquivo de filmes descartada: quantidade de colunas diferente do cabeçalho")
                    .contains("4 linhas válidas e 7 descartadas");
        }
    }

    @Test
    void deveRegistrarAvisoQuandoLinhaTiverAspasMalformadasOuForDuplicata(CapturedOutput capturedOutput) {

        try (ConfigurableApplicationContext configurableApplicationContextAspas = iniciarAplicacao(ARQUIVO_DATASET + "aspas-malformadas.csv");
                ConfigurableApplicationContext configurableApplicationContextDuplicatas = iniciarAplicacao(ARQUIVO_DATASET + "duplicatas.csv")) {

            assertThat(capturedOutput.getOut())
                    .contains("Linha 2 do arquivo de filmes descartada: aspas malformadas")
                    .contains("Linha 4 do arquivo de filmes descartada: aspas malformadas")
                    .contains("4 linhas válidas e 2 descartadas")
                    .contains("Linha 3 do arquivo de filmes descartada: duplicata exata da linha 2")
                    .contains("Linha 7 do arquivo de filmes descartada: duplicata exata da linha 4");
        }
    }

    // Arquivo fora do classpath, informado como file:. A: 1950-1951 (1) e 1951-1971 (20); nada do
    // arquivo padrão (Joel Silver, Matthew Vaughn) pode aparecer.
    @Test
    void deveImportarSomenteArquivoExternoQuandoCaminhoFileForConfigurado(@TempDir Path diretorioTemporario) throws IOException {

        Path arquivoExterno = diretorioTemporario.resolve("filmes-externos.csv");
        Files.writeString(arquivoExterno, """
                year;title;studios;producers;winner
                1951;Filme A2;Studio X;Producer A;yes
                1950;Filme A1;Studio X;Producer A;yes
                1971;Filme A3;Studio X;Producer A;yes
                """, StandardCharsets.UTF_8);

        try (ConfigurableApplicationContext configurableApplicationContext = iniciarAplicacao(arquivoExterno.toUri().toString())) {

            RestTestClient.bindToServer()
                    .baseUrl("http://localhost:" + configurableApplicationContext.getEnvironment().getProperty("local.server.port"))
                    .build()
                    .get()
                    .uri(RotaConstants.Produtor.BASE + RotaConstants.Produtor.INTERVALOS_PREMIOS)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType(MediaType.APPLICATION_JSON)
                    .expectBody().json("""
                            {
                              "min": [ { "producer": "Producer A", "interval": 1, "previousWin": 1950, "followingWin": 1951 } ],
                              "max": [ { "producer": "Producer A", "interval": 20, "previousWin": 1951, "followingWin": 1971 } ]
                            }
                            """, JsonCompareMode.STRICT);
        }
    }

    private static ConfigurableApplicationContext iniciarAplicacao(String arquivoFilme) {

        // Argumentos de linha de comando, como o operador faria; properties() do builder viraria
        // propriedade padrão, de precedência menor que o application.properties
        return new SpringApplicationBuilder(Application.class)
                .run("--filme.arquivo-csv=" + arquivoFilme, "--server.port=0");
    }

}
