"""Exporta a sessão do Claude Code (JSONL) para Markdown sanitizado em docs/ai/.

Uso: python exportar_sessao.py <sessao.jsonl> <diretorio-docs-ai> [arquivo-saida]

- Sem arquivo de saída, grava sessao-claude-code.md (sessão principal). Com ele, a sessão é tratada
  como continuação: grava no arquivo informado e nenhum pedido é marcado como prompt inicial.

- Mensagens do usuário: texto integral, em ordem; o primeiro pedido colado é marcado como prompt inicial.
- Respostas às perguntas do clarify (AskUserQuestion): texto integral.
- Mensagens do assistente: texto integral; chamadas de ferramenta resumidas (nome + descrição).
- Omitidos: raciocínio interno, saídas das ferramentas, instruções de skills carregadas,
  lembretes do sistema. Removidos: e-mail, caminho do perfil e login do usuário.
"""
import json
import re
import sys
from pathlib import Path

ARQUIVO_SESSAO = Path(sys.argv[1])
DIRETORIO_SAIDA = Path(sys.argv[2])
ARQUIVO_SAIDA = sys.argv[3] if len(sys.argv) > 3 else "sessao-claude-code.md"
SESSAO_CONTINUACAO = len(sys.argv) > 3

# O perfil do usuário é descoberto em tempo de execução, para o nome dele não ficar versionado aqui
PERFIL = Path.home()
SUBSTITUICOES = [
    (re.compile(r"[\w.+-]+@[\w-]+\.[\w.]+"), "<e-mail removido>"),
    (re.compile(re.escape(str(PERFIL)).replace(r"\\", r"\\\\?"), re.I), "~"),
    (re.compile(re.escape(PERFIL.as_posix()), re.I), "~"),
    (re.compile(re.escape("/" + PERFIL.as_posix().replace(":", "", 1)), re.I), "~"),
    # Login do sistema fora de caminho (ex.: "started by <login>" num log colado)
    (re.compile(r"\b" + re.escape(PERFIL.name) + r"\b", re.I), "<usuário>"),
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
    prompt_inicial_exportado = SESSAO_CONTINUACAO
    # Só o resultado de uma pergunta feita ao usuário é resposta dele; saída de outras ferramentas não
    ids_pergunta_usuario = set()

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
                        # Pedidos colados ficam na íntegra, sem as tags; o primeiro é o prompt inicial
                        rotulo = "## 👤 Usuário (prompt inicial)" if CONTEUDO_COLADO.search(texto) and not prompt_inicial_exportado \
                            else "## 👤 Usuário"
                        prompt_inicial_exportado = prompt_inicial_exportado or rotulo.endswith("(prompt inicial)")
                        texto = sanitizar(CONTEUDO_COLADO.sub(lambda correspondencia: correspondencia.group(1), texto))
                        if texto:
                            linhas += [rotulo, "", texto, ""]
                    elif bloco.get("type") == "tool_result":
                        resultado = bloco.get("content")
                        resultado = resultado if isinstance(resultado, str) else " ".join(
                            parte.get("text", "") for parte in (resultado or []) if isinstance(parte, dict))
                        if bloco.get("tool_use_id") in ids_pergunta_usuario:
                            # Frases fixas que a ferramenta acrescenta à resposta; não foram escritas pelo usuário
                            resultado = re.sub(r"(Your questions have been answered: |The user answered: |"
                                               r" You can now continue with these answers in mind\.|"
                                               r" Read the answers carefully.*$)", "", resultado, flags=re.S)
                            linhas += ["## 👤 Usuário (resposta ao clarify — pergunta=\"resposta\")", "", sanitizar(resultado), ""]

            elif tipo == "attachment" and (registro.get("attachment") or {}).get("type") == "queued_command":
                # Mensagem que o usuário enviou enquanto o agente executava; o log a guarda como anexo
                prompt = registro["attachment"].get("prompt")
                texto = prompt if isinstance(prompt, str) else " ".join(
                    parte.get("text", "") for parte in (prompt or []) if isinstance(parte, dict))
                texto = sanitizar(CONTEUDO_COLADO.sub(lambda correspondencia: correspondencia.group(1), texto))
                if texto:
                    linhas += ["## 👤 Usuário (mensagem enviada durante a execução)", "", texto, ""]

            elif tipo == "assistant":
                for bloco in conteudo or []:
                    if bloco.get("type") == "text" and bloco.get("text", "").strip():
                        linhas += ["## 🤖 Claude", "", sanitizar(bloco["text"]), ""]
                    elif bloco.get("type") == "tool_use":
                        linhas.append(resumir_ferramenta(bloco))
                        if bloco.get("name") == "AskUserQuestion":
                            ids_pergunta_usuario.add(bloco.get("id"))
                linhas.append("")

    DIRETORIO_SAIDA.mkdir(parents=True, exist_ok=True)
    texto_sessao = re.sub(r"\n{3,}", "\n\n", "\n".join(linhas)).strip() + "\n"
    (DIRETORIO_SAIDA / ARQUIVO_SAIDA).write_text(texto_sessao, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
