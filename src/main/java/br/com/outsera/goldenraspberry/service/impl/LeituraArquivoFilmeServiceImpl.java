package br.com.outsera.goldenraspberry.service.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
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

    private static final int FIM_ARQUIVO = -1;

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

        try (Reader reader = this.abrirLeitorSemMarcaOrdemByte(resource);
                CSVParser csvParser = FORMATO_ARQUIVO_FILME.parse(reader)) {

            this.validarCabecalho(csvParser.getHeaderNames());

            return this.converter(csvParser);

        } catch (IOException | UncheckedIOException excecaoLeitura) {

            throw new BusinessServiceException(ErroEnum.ARQUIVO_FILME_ILEGIVEL, excecaoLeitura, this.arquivoFilme);
        }
    }

    // Arquivo salvo pelo Excel/Windows começa com BOM UTF-8; o Reader do Java não o remove, e o U+FEFF
    // grudaria no nome da primeira coluna ("year"), quebrando o cabeçalho e o acesso à coluna
    private Reader abrirLeitorSemMarcaOrdemByte(Resource resource) throws IOException {

        PushbackReader pushbackReader = new PushbackReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
        int primeiroCaractere = pushbackReader.read();

        if (primeiroCaractere != FIM_ARQUIVO && primeiroCaractere != FilmeConstants.MARCA_ORDEM_BYTE) {

            pushbackReader.unread(primeiroCaractere);
        }

        return pushbackReader;
    }

    private void validarCabecalho(List<String> listaCabecalho) {

        if (ObjectUtil.isEquals(listaCabecalho, FilmeConstants.LISTA_COLUNA_CABECALHO)) {

            return;
        }

        throw new BusinessServiceException(ErroEnum.CABECALHO_ARQUIVO_FILME_INVALIDO, this.arquivoFilme,
                FilmeConstants.LISTA_COLUNA_CABECALHO, listaCabecalho);
    }

    private List<FilmeVO> converter(CSVParser csvParser) {

        List<FilmeVO> listaFilmeVO = new ArrayList<>();
        List<Long> listaNumeroLinhaDescartada = new ArrayList<>();

        for (CSVRecord csvRecord : csvParser) {

            // Linha física do arquivo (cabeçalho = 1, linhas em branco contadas); num campo entre aspas
            // com quebra de linha, é a última linha do registro
            Long numeroLinha = csvParser.getCurrentLineNumber();

            if (csvRecord.size() != FilmeConstants.LISTA_COLUNA_CABECALHO.size()) {

                this.registrarDescarte(listaNumeroLinhaDescartada, numeroLinha, "quantidade de colunas diferente do cabeçalho");
                continue;
            }

            FilmeVO filmeVO = this.montarFilmeVO(csvRecord);
            Optional<String> motivoDescarte = this.identificarMotivoDescarte(filmeVO);

            if (motivoDescarte.isPresent()) {

                this.registrarDescarte(listaNumeroLinhaDescartada, numeroLinha, motivoDescarte.get());
                continue;
            }

            listaFilmeVO.add(filmeVO);
        }

        log.info("Arquivo de filmes {} lido: {} linhas válidas e {} descartadas", this.arquivoFilme, listaFilmeVO.size(),
                listaNumeroLinhaDescartada.size());

        return listaFilmeVO;
    }

    private void registrarDescarte(List<Long> listaNumeroLinhaDescartada, Long numeroLinha, String motivoDescarte) {

        log.warn("Linha {} do arquivo de filmes descartada: {}", numeroLinha, motivoDescarte);

        listaNumeroLinhaDescartada.add(numeroLinha);
    }

    // Monta o VO com o que a linha trouxer; campo ausente ou inválido fica nulo (ou lista vazia) e é
    // rejeitado logo em seguida por identificarMotivoDescarte — o VO incompleto nunca sai desta classe
    private FilmeVO montarFilmeVO(CSVRecord csvRecord) {

        String produtores = csvRecord.get(FilmeConstants.COLUNA_PRODUTORES).strip();

        return FilmeVO.builder()
                .ano(LongUtil.toLong(csvRecord.get(FilmeConstants.COLUNA_ANO)))
                .titulo(csvRecord.get(FilmeConstants.COLUNA_TITULO).strip())
                .estudios(csvRecord.get(FilmeConstants.COLUNA_ESTUDIOS).strip())
                .produtores(produtores)
                .vencedor(this.identificarVencedor(csvRecord.get(FilmeConstants.COLUNA_VENCEDOR)))
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
