"""Exporta a sessão do Claude Code (JSONL) para Markdown sanitizado em docs/ai/.

Uso: python exportar_sessao.py <sessao.jsonl> <diretorio-docs-ai>

- Mensagens do usuário: texto integral (o prompt inicial vai para prompt-inicial.md).
- Respostas às perguntas do clarify (AskUserQuestion): texto integral.
- Mensagens do assistente: texto integral; chamadas de ferramenta resumidas (nome + descrição).
- Omitidos: raciocínio interno, saídas das ferramentas, instruções de skills carregadas,
  lembretes do sistema. Removidos: e-mail e caminho do perfil do usuário.
"""
import json
import re
import sys
from pathlib import Path

ARQUIVO_SESSAO = Path(sys.argv[1])
DIRETORIO_SAIDA = Path(sys.argv[2])

SUBSTITUICOES = [
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+"), "<e-mail removido>"),
    (re.compile(r"C:\\\\?Users\\\\?s269", re.I), "~"),
    (re.compile(r"C:/Users/s269", re.I), "~"),
    (re.compile(r"/c/Users/s269", re.I), "~"),
]
LEMBRETE_SISTEMA = re.compile(r"<system-reminder>.*?</system-reminder>", re.S)
CONTEUDO_COLADO = re.compile(r"<pasted_content[^>]*>(.*?)</pasted_content[^>]*>", re.S)


def sanitizar(texto):

    texto = LEMBRETE_SISTEMA.sub("", texto)
    for padrao, substituto in SUBSTITUICOES:
        texto = padrao.sub(substituto, texto)
    return texto.strip()


def resumir_ferramenta(bloco):

    nome = bloco.get("name", "")
    entrada = bloco.get("input", {}) or {}
    detalhe = (entrada.get("description") or entrada.get("file_path") or entrada.get("skill")
               or entrada.get("pattern") or entrada.get("query") or "")
    if nome == "Skill" and entrada.get("args"):
        detalhe = f"{entrada.get('skill')} — args: {entrada.get('args')}"
    if nome == "AskUserQuestion":
        perguntas = [q.get("question", "") for q in entrada.get("questions", [])]
        detalhe = " | ".join(perguntas)
    return f"- 🔧 `{nome}` — {sanitizar(str(detalhe))}"


def main():

    linhas = ["# Sessão Claude Code — exportação sanitizada", "",
              f"Fonte: log local do Claude Code (`{ARQUIVO_SESSAO.name}`), convertido por "
              "`docs/ai/exportar_sessao.py`. Ver regras de omissão no cabeçalho do script.", ""]
    prompt_inicial = None

    with ARQUIVO_SESSAO.open(encoding="utf-8") as arquivo:
        for linha in arquivo:
            registro = json.loads(linha)
            tipo = registro.get("type")
            mensagem = registro.get("message") or {}
            conteudo = mensagem.get("content")

            if tipo == "user":
                blocos = [{"type": "text", "text": conteudo}] if isinstance(conteudo, str) else (conteudo or [])
                for bloco in blocos:
                    if bloco.get("type") == "text":
                        texto = bloco.get("text", "")
                        if texto.startswith("Base directory for this skill") or "skill instructions were previously loaded" in texto:
                            nome_skill = re.search(r"skills[\\/]+([\w-]+)", texto)
                            linhas += [f"> _[instruções da skill `{nome_skill.group(1) if nome_skill else '?'}` carregadas — omitidas]_", ""]
                            continue
                        if "<command-name>" in texto:
                            comando = re.search(r"<command-name>(.*?)</command-name>", texto)
                            linhas += [f"> _comando local do usuário: `{comando.group(1)}`_", ""]
                            continue
                        if "<local-command" in texto or "<command-message>" in texto:
                            continue
                        colado = CONTEUDO_COLADO.search(texto)
                        if colado and prompt_inicial is None:
                            prompt_inicial = sanitizar(colado.group(1))
                            linhas += ["## 👤 Usuário", "", "_[prompt inicial — texto integral em `prompt-inicial.md`]_", ""]
                            continue
                        texto = sanitizar(texto)
                        if texto:
                            linhas += ["## 👤 Usuário", "", texto, ""]
                    elif bloco.get("type") == "tool_result":
                        resultado = bloco.get("content")
                        resultado = resultado if isinstance(resultado, str) else " ".join(
                            parte.get("text", "") for parte in (resultado or []) if isinstance(parte, dict))
                        if "questions have been answered" in resultado or "The user answered" in resultado:
                            # Frases fixas que a ferramenta acrescenta à resposta; não foram escritas pelo usuário
                            resultado = re.sub(r"(Your questions have been answered: |The user answered: |"
                                               r" You can now continue with these answers in mind\.|"
                                               r" Read the answers carefully.*$)", "", resultado, flags=re.S)
                            linhas += ["## 👤 Usuário (resposta ao clarify — pergunta=\"resposta\")", "", sanitizar(resultado), ""]

            elif tipo == "assistant":
                for bloco in conteudo or []:
                    if bloco.get("type") == "text" and bloco.get("text", "").strip():
                        linhas += ["## 🤖 Claude", "", sanitizar(bloco["text"]), ""]
                    elif bloco.get("type") == "tool_use":
                        linhas.append(resumir_ferramenta(bloco))
                linhas.append("")

    DIRETORIO_SAIDA.mkdir(parents=True, exist_ok=True)
    texto_sessao = re.sub(r"\n{3,}", "\n\n", "\n".join(linhas)).strip() + "\n"
    (DIRETORIO_SAIDA / "sessao-claude-code.md").write_text(texto_sessao, encoding="utf-8", newline="\n")
    if prompt_inicial:
        (DIRETORIO_SAIDA / "prompt-inicial.md").write_text(
            "# Prompt inicial (texto integral enviado pelo usuário)\n\n" + prompt_inicial + "\n",
            encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
