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
import java.util.Map;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import br.com.outsera.goldenraspberry.constants.FilmeConstants;
import br.com.outsera.goldenraspberry.enumerator.ErroEnum;
import br.com.outsera.goldenraspberry.exception.BusinessServiceException;
import br.com.outsera.goldenraspberry.service.LeituraArquivoFilmeService;
import br.com.outsera.goldenraspberry.service.application.SeparacaoProdutorServiceApplication;
import br.com.outsera.goldenraspberry.util.LongUtil;
import br.com.outsera.goldenraspberry.util.ObjectUtil;
import br.com.outsera.goldenraspberry.util.StringUtil;
import br.com.outsera.goldenraspberry.vo.FilmeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeituraArquivoFilmeServiceImpl implements LeituraArquivoFilmeService {

    // Cada linha física é interpretada isoladamente: com o arquivo inteiro num só parser, uma aspa
    // malformada aborta a leitura (padrão) ou engole as linhas seguintes (lenientEof), e trailingData
    // altera o campo sem aviso. Linha a linha, o erro fica restrito à linha, que é descartada com WARN.
    // Consequência aceita: campo entre aspas com quebra de linha não é suportado (vira linha descartada).
    private static final CSVFormat FORMATO_LINHA_ARQUIVO_FILME = CSVFormat.DEFAULT.builder()
            .setDelimiter(FilmeConstants.DELIMITADOR_ARQUIVO)
            .setIgnoreSurroundingSpaces(true)
            .get();

    @Value("${filme.arquivo-csv}")
    private final String arquivoFilme;

    private final ResourceLoader resourceLoader;

    private final SeparacaoProdutorServiceApplication separacaoProdutorServiceApplication;

    @Override
    public List<FilmeVO> ler() {

        Resource resource = this.resourceLoader.getResource(this.arquivoFilme);

        if (!resource.exists()) {

            throw new BusinessServiceException(ErroEnum.ARQUIVO_FILME_NAO_ENCONTRADO, this.arquivoFilme);
        }

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            this.validarCabecalho(bufferedReader.readLine());

            return this.converter(bufferedReader);

        } catch (IOException | UncheckedIOException excecaoLeitura) {

            throw new BusinessServiceException(ErroEnum.ARQUIVO_FILME_ILEGIVEL, excecaoLeitura, this.arquivoFilme);
        }
    }

    private void validarCabecalho(String linhaCabecalho) {

        List<String> listaCabecalho = ObjectUtil.isNull(linhaCabecalho) ? List.of()
                : this.separarColunas(this.removerMarcaOrdemByte(linhaCabecalho)).orElse(List.of());

        if (ObjectUtil.isEquals(listaCabecalho, FilmeConstants.LISTA_COLUNA_CABECALHO)) {

            return;
        }

        throw new BusinessServiceException(ErroEnum.CABECALHO_ARQUIVO_FILME_INVALIDO, this.arquivoFilme,
                FilmeConstants.LISTA_COLUNA_CABECALHO, listaCabecalho);
    }

    // Arquivo salvo pelo Excel/Windows começa com BOM UTF-8; o Reader do Java não o remove, e o U+FEFF
    // grudaria no nome da primeira coluna ("year")
    private String removerMarcaOrdemByte(String linhaCabecalho) {

        if (linhaCabecalho.startsWith(FilmeConstants.MARCA_ORDEM_BYTE)) {

            return linhaCabecalho.substring(FilmeConstants.MARCA_ORDEM_BYTE.length());
        }

        return linhaCabecalho;
    }

    private List<FilmeVO> converter(BufferedReader bufferedReader) throws IOException {

        // A chave é o próprio filme já normalizado: uma linha idêntica a outra já lida não vira um
        // segundo prêmio. A ordem de leitura é preservada.
        Map<FilmeVO, Long> mapaLinhaPorFilmeVO = new LinkedHashMap<>();
        List<Long> listaNumeroLinhaDescartada = new ArrayList<>();
        Long numeroLinha = FilmeConstants.NUMERO_LINHA_CABECALHO;

        for (String linha = bufferedReader.readLine(); ObjectUtil.isNotNull(linha); linha = bufferedReader.readLine()) {

            numeroLinha = LongUtil.soma(numeroLinha, LongUtil.UM);

            if (StringUtil.isNullOrEmpty(linha)) {

                continue;
            }

            Optional<String> motivoDescarte = this.registrarLinha(linha, numeroLinha, mapaLinhaPorFilmeVO);

            if (motivoDescarte.isPresent()) {

                log.warn("Linha {} do arquivo de filmes descartada: {}", numeroLinha, motivoDescarte.get());
                listaNumeroLinhaDescartada.add(numeroLinha);
            }
        }

        log.info("Arquivo de filmes {} lido: {} linhas válidas e {} descartadas", this.arquivoFilme, mapaLinhaPorFilmeVO.size(),
                listaNumeroLinhaDescartada.size());

        return new ArrayList<>(mapaLinhaPorFilmeVO.keySet());
    }

    private Optional<String> registrarLinha(String linha, Long numeroLinha, Map<FilmeVO, Long> mapaLinhaPorFilmeVO) {

        Optional<List<String>> listaColuna = this.separarColunas(linha);

        if (listaColuna.isEmpty()) {

            return Optional.of("aspas malformadas");
        }

        if (listaColuna.get().size() != FilmeConstants.LISTA_COLUNA_CABECALHO.size()) {

            return Optional.of("quantidade de colunas diferente do cabeçalho");
        }

        FilmeVO filmeVO = this.montarFilmeVO(listaColuna.get());
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
    private FilmeVO montarFilmeVO(List<String> listaColuna) {

        String produtores = listaColuna.get(FilmeConstants.INDICE_COLUNA_PRODUTORES).strip();

        return FilmeVO.builder()
                .ano(LongUtil.toLong(listaColuna.get(FilmeConstants.INDICE_COLUNA_ANO)))
                .titulo(listaColuna.get(FilmeConstants.INDICE_COLUNA_TITULO).strip())
                .estudios(listaColuna.get(FilmeConstants.INDICE_COLUNA_ESTUDIOS).strip())
                .produtores(produtores)
                .vencedor(this.identificarVencedor(listaColuna.get(FilmeConstants.INDICE_COLUNA_VENCEDOR)))
                .listaProdutor(this.separacaoProdutorServiceApplication.separar(produtores))
                .build();
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

        if (FilmeConstants.INDICADOR_VENCEDOR.equalsIgnoreCase(indicadorVencedor.strip())) {

            return Boolean.TRUE;
        }

        return null;
    }

}
