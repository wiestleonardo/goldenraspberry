package br.com.outsera.goldenraspberry.service.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.DuplicateHeaderMode;
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

    // Cabeçalho duplicado ou sem nome não derruba o parser: cai na comparação com o cabeçalho
    // esperado e vira o erro de cabeçalho incompatível
    private static final CSVFormat FORMATO_ARQUIVO_FILME = CSVFormat.DEFAULT.builder()
            .setDelimiter(FilmeConstants.DELIMITADOR_ARQUIVO)
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setIgnoreSurroundingSpaces(true)
            .setAllowMissingColumnNames(true)
            .setDuplicateHeaderMode(DuplicateHeaderMode.ALLOW_ALL)
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

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
                CSVParser csvParser = FORMATO_ARQUIVO_FILME.parse(reader)) {

            this.validarCabecalho(csvParser.getHeaderNames());

            return this.converter(csvParser);

        } catch (IOException | UncheckedIOException excecaoLeitura) {

            throw new BusinessServiceException(ErroEnum.ARQUIVO_FILME_ILEGIVEL, excecaoLeitura, this.arquivoFilme);
        }
    }

    private void validarCabecalho(List<String> listaCabecalho) {

        List<String> listaCabecalhoNormalizado = listaCabecalho.stream()
                .map(cabecalho -> cabecalho.replace(FilmeConstants.MARCA_ORDEM_BYTE, StringUtil.STRING_VAZIA).strip())
                .toList();

        if (ObjectUtil.isEquals(listaCabecalhoNormalizado, FilmeConstants.LISTA_COLUNA_CABECALHO)) {

            return;
        }

        throw new BusinessServiceException(ErroEnum.CABECALHO_ARQUIVO_FILME_INVALIDO, this.arquivoFilme,
                FilmeConstants.LISTA_COLUNA_CABECALHO, listaCabecalhoNormalizado);
    }

    private List<FilmeVO> converter(CSVParser csvParser) {

        List<FilmeVO> listaFilmeVO = new ArrayList<>();
        List<Long> listaNumeroLinhaDescartada = new ArrayList<>();

        for (CSVRecord csvRecord : csvParser) {

            // Linha física do arquivo (cabeçalho = 1, linhas em branco contadas); num campo entre aspas
            // com quebra de linha, é a última linha do registro
            Long numeroLinha = csvParser.getCurrentLineNumber();
            Optional<String> motivoDescarte = this.identificarMotivoDescarte(csvRecord);

            if (motivoDescarte.isPresent()) {

                log.warn("Linha {} do arquivo de filmes descartada: {}", numeroLinha, motivoDescarte.get());
                listaNumeroLinhaDescartada.add(numeroLinha);
                continue;
            }

            listaFilmeVO.add(this.montarFilmeVO(csvRecord));
        }

        log.info("Arquivo de filmes {} lido: {} linhas válidas e {} descartadas", this.arquivoFilme, listaFilmeVO.size(),
                listaNumeroLinhaDescartada.size());

        return listaFilmeVO;
    }

    private Optional<String> identificarMotivoDescarte(CSVRecord csvRecord) {

        if (csvRecord.size() != FilmeConstants.LISTA_COLUNA_CABECALHO.size()) {

            return Optional.of("quantidade de colunas diferente do cabeçalho");
        }

        Long ano = LongUtil.toLong(csvRecord.get(FilmeConstants.COLUNA_ANO));

        if (LongUtil.isNullOrZero(ano) || LongUtil.menor(ano, LongUtil.ZERO)) {

            return Optional.of("ano ausente, não numérico ou menor que 1");
        }

        if (StringUtil.isNullOrEmpty(csvRecord.get(FilmeConstants.COLUNA_TITULO))) {

            return Optional.of("título ausente");
        }

        if (this.separacaoProdutorServiceApplication.separar(csvRecord.get(FilmeConstants.COLUNA_PRODUTORES)).isEmpty()) {

            return Optional.of("nenhum produtor informado");
        }

        if (this.identificarVencedor(csvRecord.get(FilmeConstants.COLUNA_VENCEDOR)).isEmpty()) {

            return Optional.of("indicador de vencedor diferente de yes ou vazio");
        }

        return Optional.empty();
    }

    private FilmeVO montarFilmeVO(CSVRecord csvRecord) {

        String produtores = csvRecord.get(FilmeConstants.COLUNA_PRODUTORES);

        return FilmeVO.builder()
                .ano(LongUtil.toLong(csvRecord.get(FilmeConstants.COLUNA_ANO)))
                .titulo(csvRecord.get(FilmeConstants.COLUNA_TITULO).strip())
                .estudios(csvRecord.get(FilmeConstants.COLUNA_ESTUDIOS).strip())
                .produtores(produtores.strip())
                .vencedor(this.identificarVencedor(csvRecord.get(FilmeConstants.COLUNA_VENCEDOR)).orElseThrow())
                .listaProdutor(this.separacaoProdutorServiceApplication.separar(produtores))
                .build();
    }

    private Optional<Boolean> identificarVencedor(String indicadorVencedor) {

        if (StringUtil.isNullOrEmpty(indicadorVencedor)) {

            return Optional.of(Boolean.FALSE);
        }

        if (FilmeConstants.INDICADOR_VENCEDOR.equalsIgnoreCase(indicadorVencedor.strip())) {

            return Optional.of(Boolean.TRUE);
        }

        return Optional.empty();
    }

}
