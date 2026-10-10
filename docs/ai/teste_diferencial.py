"""Teste diferencial da API contra um oráculo independente, com arquivos de filmes gerados ao acaso.

Para cada semente, gera um CSV com cenário aleatório, sobe o jar real com ele, chama
GET /api/produtores/intervalosPremios e compara a resposta com o resultado esperado.

O oráculo não lê o CSV: o resultado esperado sai dos próprios dados que o gerador sorteou (ano,
produtores, vencedor). Assim, um erro de leitura do arquivo (separador de produtores, aspas, BOM,
ordem das colunas) aparece como diferença, em vez de ser repetido pelo oráculo.

Cenários sorteados em cada arquivo:
- 0 a 60 filmes, 1 a 4 produtores por filme, anos em faixas de 3 a 120 anos e fora de ordem;
- empates forçados no intervalo (um segundo produtor repete o intervalo de outro);
- dois filmes vencedores do mesmo produtor no mesmo ano (intervalo 0, regra adotada na spec);
- produtores separados por ",", " and ", ", and ", " AND ", " And ", com espaços variados;
- nomes parecidos com o separador ("Anderson Paak", "Sandy Andrews", "Ed Grand") e acentuados;
- campo de produtores entre aspas, "winner" como "yes", "Yes" ou " yes ";
- colunas em ordem aleatória, cabeçalho em minúsculas, maiúsculas ou capitalizado, colunas extras
  (inclusive sem nome, como o ";" no fim da linha), BOM UTF-8 e quebra de linha LF ou CRLF.

Fica fora da suíte do Maven de propósito: o enunciado pede somente testes de integração com dados
conhecidos, e um segundo cálculo em Java dentro do projeto seria código duplicado.

Uso, na raiz do projeto, depois de ./mvnw package (JAVA_HOME apontando para um JDK 21, ou java 21 no PATH):

    python docs/ai/teste_diferencial.py [quantidade=60] [paralelismo=6]
"""
import glob
import json
import os
import random
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

RAIZ_PROJETO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ROTA = "/api/produtores/intervalosPremios"
PORTA_INICIAL = 9100

NOMES = ["Joel Silver", "Matthew Vaughn", "Anderson Paak", "Sandy Andrews", "Brandon Lee", "Andy Andrade",
         "Bo", "Ana Maria", "Allan Carr", "Producer 1", "Producer 2", "Producer 10", "Mary-Jane O'Neil",
         "José Ângelo", "Bob Anderson", "Randall Ford", "Al", "Dan", "Ed Grand", "Zé"]

COLUNAS_OBRIGATORIAS = ["year", "title", "studios", "producers", "winner"]
COLUNAS_EXTRAS = ["id", "rating", "country", ""]


def localizar_java():
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        return os.path.join(java_home, "bin", "java")
    return shutil.which("java") or sys.exit("java não encontrado: defina JAVA_HOME ou coloque o java no PATH")


def localizar_jar():
    jars = glob.glob(os.path.join(RAIZ_PROJETO, "target", "golden-raspberry-api-*.jar"))
    return jars[0] if jars else sys.exit("jar não encontrado: execute ./mvnw package antes")


def gerar_filmes(rnd, semente):
    nomes = rnd.sample(NOMES, rnd.randint(2, len(NOMES)))
    ano_base = rnd.choice([1900, 1980, 2000])
    filmes = []

    for indice in range(rnd.randint(0, 60)):
        produtores = rnd.sample(nomes, rnd.randint(1, min(4, len(nomes))))
        ano = ano_base + rnd.randint(0, rnd.choice([3, 20, 120]))
        filmes.append((ano, f"Filme {semente}-{indice}", produtores, rnd.random() < 0.5))

    if rnd.random() < 0.4:
        produtor_a, produtor_b = rnd.sample(nomes, 2)
        ano = rnd.randint(1950, 2000)
        intervalo = rnd.randint(0, 30)
        filmes += [(ano, f"Empate {semente}-1", [produtor_a], True),
                   (ano + intervalo, f"Empate {semente}-2", [produtor_a], True),
                   (ano + 50, f"Empate {semente}-3", [produtor_b], True),
                   (ano + 50 + intervalo, f"Empate {semente}-4", [produtor_b], True)]

    rnd.shuffle(filmes)
    return filmes


def juntar_produtores(rnd, produtores):
    if len(produtores) == 1:
        return produtores[0]
    separador = rnd.choice([", ", ",", " , "])
    separador_final = rnd.choice([" and ", ", and ", " AND ", ", ", " And "])
    return separador.join(produtores[:-1]) + separador_final + produtores[-1]


def escrever_csv(rnd, filmes, caminho):
    colunas = COLUNAS_OBRIGATORIAS + rnd.sample(COLUNAS_EXTRAS, rnd.randint(0, 3))
    rnd.shuffle(colunas)
    caixa = rnd.choice([str.lower, str.upper, str.capitalize])
    linhas = [";".join(caixa(coluna) for coluna in colunas)]

    for indice, (ano, titulo, produtores, vencedor) in enumerate(filmes):
        texto_produtores = juntar_produtores(rnd, produtores)
        if rnd.random() < 0.2:
            texto_produtores = f'"{texto_produtores}"'
        valores = {"year": str(ano), "title": titulo, "studios": "Studio X, Studio Y",
                   "producers": texto_produtores, "winner": rnd.choice(["yes", "Yes", " yes "]) if vencedor else "",
                   "id": str(indice), "rating": "7.5", "country": "US", "": ""}
        linhas.append(";".join(valores[coluna] for coluna in colunas))

    quebra = rnd.choice(["\n", "\r\n"])
    bom = "﻿" if rnd.random() < 0.2 else ""
    with open(caminho, "w", encoding="utf-8", newline="") as arquivo:
        arquivo.write(bom + quebra.join(linhas) + quebra)


def calcular_esperado(filmes):
    anos_por_produtor = defaultdict(list)
    for ano, _, produtores, vencedor in filmes:
        if vencedor:
            for produtor in dict.fromkeys(produtores):
                anos_por_produtor[produtor].append(ano)

    intervalos = []
    for produtor, anos in anos_por_produtor.items():
        anos.sort()
        for anterior, seguinte in zip(anos, anos[1:]):
            intervalos.append({"producer": produtor, "interval": seguinte - anterior,
                               "previousWin": anterior, "followingWin": seguinte})

    if not intervalos:
        return {"min": [], "max": []}

    minimo = min(item["interval"] for item in intervalos)
    maximo = max(item["interval"] for item in intervalos)
    return ordenar({"min": [item for item in intervalos if item["interval"] == minimo],
                    "max": [item for item in intervalos if item["interval"] == maximo]})


def ordenar(resposta):
    chave = lambda item: (item["producer"], item["previousWin"], item["followingWin"])
    return {"min": sorted(resposta["min"], key=chave), "max": sorted(resposta["max"], key=chave)}


def consultar_api(java, jar, caminho_csv, porta):
    with open(caminho_csv + ".log", "w", encoding="utf-8") as log:
        processo = subprocess.Popen([java, "-jar", jar, f"--server.port={porta}",
                                     "--filme.arquivo-csv=file:" + caminho_csv.replace(os.sep, "/")],
                                    stdout=log, stderr=log)
        try:
            for _ in range(120):
                if processo.poll() is not None:
                    return None
                try:
                    with urllib.request.urlopen(f"http://localhost:{porta}{ROTA}", timeout=2) as resposta:
                        return json.loads(resposta.read())
                except OSError:
                    time.sleep(0.5)
            return None
        finally:
            processo.kill()
            processo.wait()


def executar(semente, java, jar, pasta):
    rnd = random.Random(semente)
    filmes = gerar_filmes(rnd, semente)
    caminho = os.path.join(pasta, f"filmes_{semente}.csv")
    escrever_csv(rnd, filmes, caminho)

    obtido = consultar_api(java, jar, caminho, PORTA_INICIAL + semente)
    if obtido is None:
        return semente, f"a aplicação não respondeu (log em {caminho}.log)"

    esperado = calcular_esperado(filmes)
    if ordenar(obtido) != esperado:
        return semente, f"esperado={json.dumps(esperado)}\n  obtido={json.dumps(obtido)}"
    if obtido != ordenar(obtido):
        return semente, "itens fora da ordem produtor/previousWin/followingWin"
    return semente, None


def main():
    quantidade = int(sys.argv[1]) if len(sys.argv) > 1 else 60
    paralelismo = int(sys.argv[2]) if len(sys.argv) > 2 else 6
    java, jar = localizar_java(), localizar_jar()
    pasta = tempfile.mkdtemp(prefix="teste-diferencial-")

    falhas = 0
    with ThreadPoolExecutor(max_workers=paralelismo) as executor:
        for semente, erro in executor.map(lambda s: executar(s, java, jar, pasta), range(quantidade)):
            if erro:
                falhas += 1
                print(f"FALHA semente {semente}: {erro}")

    print(f"{quantidade} arquivos, {falhas} falhas (arquivos gerados em {pasta})")
    sys.exit(1 if falhas else 0)


if __name__ == "__main__":
    main()
