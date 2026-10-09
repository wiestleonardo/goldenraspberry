package br.com.outsera.goldenraspberry.service.application.impl;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import br.com.outsera.goldenraspberry.constants.RegexConstants;
import br.com.outsera.goldenraspberry.service.application.SeparacaoProdutorServiceApplication;
import br.com.outsera.goldenraspberry.util.StringUtil;

@Service
public class SeparacaoProdutorServiceApplicationImpl implements SeparacaoProdutorServiceApplication {

    private static final Pattern PADRAO_SEPARADOR_PRODUTOR = Pattern.compile(RegexConstants.SEPARADOR_PRODUTOR);

    @Override
    public List<String> separar(String produtores) {

        if (StringUtil.isNullOrEmpty(produtores)) {

            return List.of();
        }

        return PADRAO_SEPARADOR_PRODUTOR.splitAsStream(produtores.strip())
                .map(String::strip)
                .filter(StringUtil::notEmpty)
                .distinct()
                .toList();
    }

}
