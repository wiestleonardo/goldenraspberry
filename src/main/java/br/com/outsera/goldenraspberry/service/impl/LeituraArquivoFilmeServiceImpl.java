package br.com.outsera.goldenraspberry.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import br.com.outsera.goldenraspberry.enumerator.ErroEnum;
import br.com.outsera.goldenraspberry.exception.BusinessServiceException;
import br.com.outsera.goldenraspberry.service.LeituraArquivoFilmeService;
import br.com.outsera.goldenraspberry.service.application.SeparacaoProdutorServiceApplication;
import br.com.outsera.goldenraspberry.util.LongUtil;
import br.com.outsera.goldenraspberry.util.ObjectUtil;
import br.com.outsera.goldenraspberry.util.StringUtil;
import br.com.outsera.goldenraspberry.vo.FilmeVO;

@Service
public class LeituraArquivoFilmeServiceImpl implements LeituraArquivoFilmeService {

    private static final Logger log = LoggerFactory.getLogger(LeituraArquivoFilmeServiceImpl.class);

    private static final char DELIMITADOR_ARQUIVO = ';';
    private static final String MARCA_ORDEM_BYTE = String.valueOf((char) 0xFEFF);
    private static final Long NUMERO_LINHA_CABECALHO = 1L;

    private static final String COLUNA_ANO = "year";
    private static final String COLUNA_TITULO = "title";
    private static final String COLUNA_ESTUDIOS = "studios";
    private static final String COLUNA_PRODUTORES = "producers";
    private static final String COLUNA_VENCEDOR = "winner";
    private static final List<String> LISTA_COLUNA_OBRIGATORIA = List.of(COLUNA_ANO, COLUNA_TITULO, COLUNA_ESTUDIOS,
            COLUNA_PRODUTORES, COLUNA_VENCEDOR);

    private static final String INDICADOR_VENCEDOR = "yes";

    // Cada linha física é interpretada isoladamente: com o arquivo inteiro num só parser, uma aspa
    // malformada aborta a leitura (padrão) ou engole as linhas seguintes (lenientEof), e trailingData
    // altera o campo sem aviso. Linha a linha, o erro fica restrito à linha, que é descartada com WARN.
    // Consequência aceita: campo entre aspas com quebra de linha não é suportado (vira linha descartada).
    private static final CSVFormat FORMATO_LINHA_ARQUIVO_FILME = CSVFormat.DEFAULT.builder()
            .setDelimiter(DELIMITADOR_ARQUIVO)
            .setIgnoreSurroundingSpaces(true)
            .get();

    private final String arquivoFilme;

    private final ResourceLoader resourceLoader;

    private final SeparacaoProdutorServiceApplication separacaoProdutorServiceApplication;

    public LeituraArquivoFilmeServiceImpl(@Value("${filme.arquivo-csv}") String arquivoFilme, ResourceLoader resourceLoader,
            SeparacaoProdutorServiceApplication separacaoProdutorServiceApplication) {

        this.arquivoFilme = arquivoFilme;
        this.resourceLoader = resourceLoader;
        this.separacaoProdutorServiceApplication = separacaoProdutorServiceApplication;
    }

    @Override
    public List<FilmeVO> ler() {

        Resource resource = this.resourceLoader.getResource(this.arquivoFilme);

        if (!resource.exists()) {

            throw new BusinessServiceException(ErroEnum.ARQUIVO_FILME_NAO_ENCONTRADO, this.arquivoFilme);
        }

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            List<String> listaCabecalho = this.lerCabecalho(bufferedReader.readLine());

            return this.converter(bufferedReader, listaCabecalho);

        } catch (IOException | UncheckedIOException excecaoLeitura) {

            throw new BusinessServiceException(ErroEnum.ARQUIVO_FILME_ILEGIVEL, excecaoLeitura, this.arquivoFilme);
        }
    }

    // As colunas são localizadas pelo nome, sem distinção de caixa: a ordem não importa e colunas a
    // mais são ignoradas. Falta de coluna obrigatória interrompe a importação.
    private List<String> lerCabecalho(String linhaCabecalho) {

        List<String> listaCabecalho = ObjectUtil.isNull(linhaCabecalho) ? List.of()
                : this.separarColunas(this.removerMarcaOrdemByte(linhaCabecalho)).orElse(List.of()).stream()
                        .map(coluna -> coluna.toLowerCase(Locale.ROOT))
                        .toList();

        if (listaCabecalho.containsAll(LISTA_COLUNA_OBRIGATORIA)) {

            return listaCabecalho;
        }

        throw new BusinessServiceException(ErroEnum.CABECALHO_ARQUIVO_FILME_INVALIDO, this.arquivoFilme,
                LISTA_COLUNA_OBRIGATORIA, listaCabecalho);
    }

    // Arquivo salvo pelo Excel/Windows começa com BOM UTF-8; o Reader do Java não o remove, e o U+FEFF
    // grudaria no nome da primeira coluna ("year")
    private String removerMarcaOrdemByte(String linhaCabecalho) {

        if (linhaCabecalho.startsWith(MARCA_ORDEM_BYTE)) {

            return linhaCabecalho.substring(MARCA_ORDEM_BYTE.length());
        }

        return linhaCabecalho;
    }

    private List<FilmeVO> converter(BufferedReader bufferedReader, List<String> listaCabecalho) throws IOException {

        // A chave é o próprio filme já normalizado: uma linha idêntica a outra já lida não vira um
        // segundo prêmio. A ordem de leitura é preservada.
        Map<FilmeVO, Long> mapaLinhaPorFilmeVO = new LinkedHashMap<>();
        List<Long> listaNumeroLinhaDescartada = new ArrayList<>();
        Long numeroLinha = NUMERO_LINHA_CABECALHO;

        for (String linha = bufferedReader.readLine(); ObjectUtil.isNotNull(linha); linha = bufferedReader.readLine()) {

            numeroLinha = LongUtil.soma(numeroLinha, LongUtil.UM);

            if (StringUtil.isNullOrEmpty(linha)) {

                continue;
            }

            Optional<String> motivoDescarte = this.registrarLinha(linha, numeroLinha, listaCabecalho, mapaLinhaPorFilmeVO);

            if (motivoDescarte.isPresent()) {

                log.warn("Linha {} do arquivo de filmes descartada: {}", numeroLinha, motivoDescarte.get());
                listaNumeroLinhaDescartada.add(numeroLinha);
            }
        }

        log.info("Arquivo de filmes {} lido: {} linhas válidas e {} descartadas", this.arquivoFilme, mapaLinhaPorFilmeVO.size(),
                listaNumeroLinhaDescartada.size());

        return new ArrayList<>(mapaLinhaPorFilmeVO.keySet());
    }

    private Optional<String> registrarLinha(String linha, Long numeroLinha, List<String> listaCabecalho,
            Map<FilmeVO, Long> mapaLinhaPorFilmeVO) {

        Optional<List<String>> listaColuna = this.separarColunas(linha);

        if (listaColuna.isEmpty()) {

            return Optional.of("aspas malformadas");
        }

        if (listaColuna.get().size() != listaCabecalho.size()) {

            return Optional.of("quantidade de colunas diferente do cabeçalho");
        }

        FilmeVO filmeVO = this.montarFilmeVO(listaColuna.get(), listaCabecalho);
        Optional<String> motivoDescarte = this.identificarMotivoDescarte(filmeVO);

        if (motivoDescarte.isPresent()) {

            return motivoDescarte;
        }

        Long numeroLinhaOriginal = mapaLinhaPorFilmeVO.putIfAbsent(filmeVO, numeroLinha);

        if (ObjectUtil.isNotNull(numeroLinhaOriginal)) {

            return Optional.of("duplicata exata da linha " + numeroLinhaOriginal);
        }

        return Optional.empty();
    }

    private Optional<List<String>> separarColunas(String linha) {

        try (CSVParser csvParser = FORMATO_LINHA_ARQUIVO_FILME.parse(new StringReader(linha))) {

            return Optional.of(csvParser.getRecords().getFirst().toList());

        } catch (IOException | UncheckedIOException excecaoAspas) {

            // A linha isolada vem de uma String, sem I/O real: a única falha possível é a sintaxe de aspas
            return Optional.empty();
        }
    }

    // Monta o VO com o que a linha trouxer; campo ausente ou inválido fica nulo (ou lista vazia) e é
    // rejeitado logo em seguida por identificarMotivoDescarte — o VO incompleto nunca sai desta classe
    private FilmeVO montarFilmeVO(List<String> listaColuna, List<String> listaCabecalho) {

        String produtores = this.obterColuna(listaColuna, listaCabecalho, COLUNA_PRODUTORES).strip();

        return new FilmeVO(LongUtil.toLong(this.obterColuna(listaColuna, listaCabecalho, COLUNA_ANO)),
                this.obterColuna(listaColuna, listaCabecalho, COLUNA_TITULO).strip(),
                this.obterColuna(listaColuna, listaCabecalho, COLUNA_ESTUDIOS).strip(),
                produtores,
                this.identificarVencedor(this.obterColuna(listaColuna, listaCabecalho, COLUNA_VENCEDOR)),
                this.separacaoProdutorServiceApplication.separar(produtores));
    }

    private String obterColuna(List<String> listaColuna, List<String> listaCabecalho, String nomeColuna) {

        return listaColuna.get(listaCabecalho.indexOf(nomeColuna));
    }

    private Optional<String> identificarMotivoDescarte(FilmeVO filmeVO) {

        if (LongUtil.isNullOrZero(filmeVO.ano()) || LongUtil.menor(filmeVO.ano(), LongUtil.ZERO)) {

            return Optional.of("ano ausente, não numérico ou menor que 1");
        }

        if (StringUtil.isNullOrEmpty(filmeVO.titulo())) {

            return Optional.of("título ausente");
        }

        if (filmeVO.listaProdutor().isEmpty()) {

            return Optional.of("nenhum produtor informado");
        }

        if (ObjectUtil.isNull(filmeVO.vencedor())) {

            return Optional.of("indicador de vencedor diferente de yes ou vazio");
        }

        return Optional.empty();
    }

    private Boolean identificarVencedor(String indicadorVencedor) {

        if (StringUtil.isNullOrEmpty(indicadorVencedor)) {

            return Boolean.FALSE;
        }

        if (INDICADOR_VENCEDOR.equalsIgnoreCase(indicadorVencedor.strip())) {

            return Boolean.TRUE;
        }

        return null;
    }

}
