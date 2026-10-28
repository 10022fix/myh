(() => {
    const lista = document.getElementById("lista");
    const busca = document.getElementById("busca");
    const guiaDialogo = document.getElementById("exerciseGuideDialog");
    const recomendacoesDialogo = document.getElementById("recommendationsDialog");
    const confirmarTreinoDialogo = document.getElementById("manualWorkoutConfirmDialog");
    const botaoRecomendar = document.querySelector("[data-recommend-workout]");
    let temporizador;
    let recomendacoesAtuais = null;
    let treinoManualPendente = null;
    let acaoRecomendacao = { tipo: "adicionar", treinoId: null };
    const marcados = {};
    let concluidosHoje = new Set();
    let agendaAtual = null;
    let treinoDeHojeId = null;

    const escapar = valor => String(valor ?? "").replace(/[&<>"']/g, caractere => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    }[caractere]));

    function chaveExercicio(treino, exercicio, indice) {
        const treinoId = treino.id ?? treino.nome ?? "treino";
        const nome = exercicio.nome ?? `exercicio-${indice + 1}`;
        return `${treinoId}:${indice}:${nome}`;
    }

    function nomeExibicaoTreino(treino, agendaItem) {
        const nome = String(treino.nome || "").trim();
        // Compatibilidade com treinos que já estavam salvos com o padrão antigo.
        const diaAntigo = nome.match(/^Dia\s+(\d+)(.*)$/i);
        if (diaAntigo) return `Treino ${diaAntigo[1]}${diaAntigo[2]}`;
        const letraAntiga = nome.match(/^Treino\s+([AB])(?=\s|$)(.*)$/i);
        if (letraAntiga) return `Treino ${letraAntiga[1].toUpperCase() === "A" ? 1 : 2}${letraAntiga[2]}`;
        return nome || `Treino ${Number(agendaItem?.ordem) || 1}`;
    }

    function renderizarTreino(treino, indiceTreino) {
        const exercicios = Array.isArray(treino.exercicios) ? treino.exercicios : [];
        const duracao = Number(treino.duracao);
        const treinoId = Number(treino.id);
        const concluidoHoje = concluidosHoje.has(treinoId);
        const podeTreinarHoje = !agendaAtual?.configurada || treinoDeHojeId === treinoId;
        const agendaItem = agendaAtual?.dias?.find(item => Number(item.treinoId) === treinoId);
        const titulo = escapar(nomeExibicaoTreino(treino, agendaItem) || `Treino ${indiceTreino + 1}`);
        const detalhes = exercicios.map((exercicio, indice) => {
            const chave = chaveExercicio(treino, exercicio, indice);
            const idCheckbox = `exercicio-${indiceTreino}-${indice}`;
            const marcado = concluidoHoje || Boolean(marcados[chave]);
            const repeticoes = Number(exercicio.repeticoes);
            const prescricao = repeticoes === 0
                ? "Tempo indicado no nome do exercício"
                : `${Number(exercicio.series) || 0} séries · ${repeticoes || 0} repetições · ${Number(exercicio.descanso) || 0}s descanso`;
            const grupo = exercicio.grupoMuscular || exercicio.grupo || exercicio.musculo || "";
            return `<div class="exercise${marcado ? " is-complete" : ""}">
                <input id="${idCheckbox}" type="checkbox" data-exercise-key="${escapar(chave)}" ${marcado ? "checked" : ""} ${concluidoHoje || !podeTreinarHoje ? "disabled" : ""} aria-label="Marcar ${escapar(exercicio.nome)} como concluído">
                <span class="exercise-number">${String(indice + 1).padStart(2, "0")}</span>
                <label class="exercise-copy" for="${idCheckbox}"><strong>${escapar(exercicio.nome || "Exercício")}</strong><small>${escapar(prescricao)}</small></label>
                ${grupo ? `<b>${escapar(grupo)}</b>` : ""}
                <button type="button" class="exercise-guide-button" data-exercise-guide="${escapar(exercicio.nome || "Exercício")}" data-exercise-group="${escapar(grupo)}" aria-label="Como fazer: ${escapar(exercicio.nome || "Exercício")}">Como fazer</button>
            </div>`;
        }).join("");
        const completo = exercicios.filter((exercicio, indice) => marcados[chaveExercicio(treino, exercicio, indice)]).length;
        const total = exercicios.length;
        const percentual = total ? Math.round(completo / total * 100) : 0;
        const duracaoTexto = Number.isFinite(duracao) && duracao > 0 ? `${duracao} min` : "Duração não informada";
        const nivel = treino.nivel ? escapar(treino.nivel) : "PLANO PERSONALIZADO";
        const totalConcluidos = concluidoHoje ? total : completo;
        const todosMarcados = total > 0 && totalConcluidos === total;
        const treinoIdCard = escapar(treino.id ?? treino.nome ?? indiceTreino);

        return `<article class="training-card demo-content-card" data-training-id="${treinoIdCard}">
            <div class="section-heading">
                <div><h2>${titulo}</h2><p>${total} ${total === 1 ? "exercício" : "exercícios"} · ${duracaoTexto}</p></div>
                <span class="sample-tag">${agendaItem ? `Dia ${Number(agendaItem.ordem)} · ${["", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado", "Domingo"][Number(agendaItem.diaSemana)]}` : nivel}</span>
            </div>
            <div class="exercise-list">${detalhes || `<p class="empty-exercises">Este treino ainda não tem exercícios cadastrados.</p>`}</div>
            <div class="workout-progress" role="progressbar" aria-label="Progresso de ${titulo}" aria-valuemin="0" aria-valuemax="${total}" aria-valuenow="${completo}">
                <span style="width:${percentual}%"></span>
            </div>
            <p class="demo-feedback" data-workout-feedback>${totalConcluidos} de ${total} exercícios concluídos</p>
            <button type="button" class="finish-workout" data-finalizar-treino="${treinoIdCard}" ${todosMarcados && podeTreinarHoje ? "" : "hidden"} ${concluidoHoje || !podeTreinarHoje ? "disabled" : ""}>${concluidoHoje ? "Treino concluído hoje" : "Finalizar treino"}</button>
            <div class="training-actions">
                <button type="button" class="training-action" data-alterar-treino="${treinoIdCard}">Alterar por recomendação</button>
                <button type="button" class="training-action training-action--delete" data-excluir-treino="${treinoIdCard}">Excluir treino</button>
            </div>
        </article>`;
    }

    function atualizarProgresso(card) {
        const checks = [...card.querySelectorAll(".exercise input[type=checkbox]")];
        const concluidos = checks.filter(check => check.checked).length;
        const total = checks.length;
        const barra = card.querySelector(".workout-progress");
        const preenchimento = barra?.querySelector("span");
        const feedback = card.querySelector("[data-workout-feedback]");
        const percentual = total ? Math.round(concluidos / total * 100) : 0;
        if (barra) barra.setAttribute("aria-valuenow", String(concluidos));
        if (preenchimento) preenchimento.style.width = `${percentual}%`;
        if (feedback) feedback.textContent = `${concluidos} de ${total} exercícios concluídos`;
        const botaoFinalizar = card.querySelector("[data-finalizar-treino]");
        if (botaoFinalizar && !concluidosHoje.has(Number(card.dataset.trainingId))) {
            botaoFinalizar.hidden = total === 0 || concluidos !== total;
        }
    }

    async function carregar() {
        try {
            const [resposta, conclusoesResposta, agendaResposta] = await Promise.all([
                fetch("/api/treinos?busca=" + encodeURIComponent(busca.value)),
                fetch("/api/treinos/conclusoes-semana"),
                fetch("/api/agenda")
            ]);
            if (resposta.status === 401) {
                location.href = "/login.html";
                return;
            }
            if (!resposta.ok) {
                lista.innerHTML = `<section class="empty-state"><h2>Não foi possível carregar os treinos.</h2><p>O servidor retornou o erro ${resposta.status}. Tente novamente mais tarde.</p></section>`;
                return;
            }
            if (agendaResposta.ok) {
                agendaAtual = await agendaResposta.json();
                const hoje = new Date();
                const diaSemana = hoje.getDay() || 7;
                treinoDeHojeId = Number(agendaAtual.dias?.find(item => Number(item.diaSemana) === diaSemana)?.treinoId) || null;
            }
            if (conclusoesResposta.ok) {
                const conclusoes = await conclusoesResposta.json();
                const hoje = new Date();
                const hojeLocal = `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, "0")}-${String(hoje.getDate()).padStart(2, "0")}`;
                concluidosHoje = new Set(conclusoes.filter(item => item.data === hojeLocal).map(item => Number(item.treinoId)));
            }
            const treinosDisponiveis = await resposta.json();
            // Mostra todo o plano semanal (Dia 1, Dia 2, etc.). Só libera
            // marcação/conclusão para a sessão agendada para hoje.
            const ordemAgenda = new Map((agendaAtual?.dias || []).map(item => [Number(item.treinoId), Number(item.ordem)]));
            const treinos = agendaAtual?.configurada
                ? treinosDisponiveis.slice().sort((a, b) => {
                    const ordemA = ordemAgenda.get(Number(a.id)) ?? Number.MAX_SAFE_INTEGER;
                    const ordemB = ordemAgenda.get(Number(b.id)) ?? Number.MAX_SAFE_INTEGER;
                    return ordemA - ordemB || String(a.nome || "").localeCompare(String(b.nome || ""), "pt-BR");
                })
                : treinosDisponiveis;
            lista.innerHTML = treinos.length
                ? treinos.map(renderizarTreino).join("")
                : `<section class="empty-state"><h2>Nenhum treino encontrado</h2><p>Experimente buscar outro termo ou aguarde a liberação de um treino pelo profissional.</p></section>`;
        } catch (_) {
            lista.innerHTML = '<section class="empty-state"><h2>Falha de conexão.</h2><p>Não foi possível falar com o servidor. Atualize a página para tentar novamente.</p></section>';
        }
    }

    lista.addEventListener("change", event => {
        const checkbox = event.target.closest(".exercise input[type=checkbox]");
        if (!checkbox) return;
        const card = checkbox.closest(".training-card");
        const key = checkbox.dataset.exerciseKey;
        if (key) marcados[key] = checkbox.checked;
        if (checkbox.checked) checkbox.closest(".exercise").classList.add("is-complete");
        else checkbox.closest(".exercise").classList.remove("is-complete");

        atualizarProgresso(card);
    });

    lista.addEventListener("click", async event => {
        const botaoGuia = event.target.closest("[data-exercise-guide]");
        if (botaoGuia) {
            abrirGuia(botaoGuia.dataset.exerciseGuide, botaoGuia.dataset.exerciseGroup);
            return;
        }
        const alterar = event.target.closest("[data-alterar-treino]");
        if (alterar) {
            abrirRecomendacoes("substituir", Number(alterar.dataset.alterarTreino), alterar);
            return;
        }
        const excluir = event.target.closest("[data-excluir-treino]");
        if (excluir) {
            const id = Number(excluir.dataset.excluirTreino);
            if (!id || !window.confirm("Excluir este treino do seu plano? O histórico de conclusões será preservado.")) return;
            excluir.disabled = true;
            try {
                const resposta = await fetch(`/api/treinos/${id}`, { method: "DELETE" });
                const dados = await resposta.json();
                if (resposta.status === 401) return location.href = "/login.html";
                if (!resposta.ok) throw new Error(dados.mensagem || "Não foi possível excluir o treino.");
                await carregar();
            } catch (erro) {
                excluir.disabled = false;
                const feedback = excluir.closest(".training-card")?.querySelector("[data-workout-feedback]");
                if (feedback) feedback.textContent = erro.message;
            }
            return;
        }
        const botao = event.target.closest("[data-finalizar-treino]");
        if (!botao || botao.disabled) return;
        const treinoId = Number(botao.dataset.finalizarTreino);
        if (!treinoId) return;
        botao.disabled = true;
        try {
            const resposta = await fetch(`/api/treinos/${treinoId}/concluir`, { method: "POST" });
            const dados = await resposta.json();
            if (resposta.status === 401) {
                location.href = "/login.html";
                return;
            }
            if (!resposta.ok) throw new Error(dados.mensagem || "Não foi possível registrar o treino.");
            await carregar();
        } catch (erro) {
            botao.disabled = false;
            const feedback = botao.closest(".training-card")?.querySelector("[data-workout-feedback]");
            if (feedback) feedback.textContent = erro.message;
        }
    });

    function renderizarRecomendacoes(recomendacoes) {
        const treinos = Array.isArray(recomendacoes.treinos) ? recomendacoes.treinos : [];
        const tituloAcao = acaoRecomendacao.tipo === "substituir" ? "Substituir este treino" : "Adicionar ao meu plano";
        recomendacoesDialogo.innerHTML = `<div class="recommendations-content">
            <div class="recommendations-heading"><div><span class="sample-tag">SUGESTÕES PERSONALIZADAS</span><h2 id="recommendationsTitle">Escolha um treino</h2><p>Opções montadas pelo site com base nas respostas do seu questionário.</p></div>
                <div class="recommendations-heading-actions">${acaoRecomendacao.tipo === "adicionar" ? `<button type="button" class="manual-workout-trigger" data-montar-personalizado>Montar treino personalizado</button><button type="button" class="manual-workout-trigger" data-criar-do-meu-jeito>Criar do meu jeito</button>` : ""}<button type="button" class="exercise-guide-close" data-close-recommendations aria-label="Fechar">×</button></div></div>
            <div class="recommendation-list">${treinos.map((treino, indice) => `<article class="recommendation-card">
                <h3>${escapar(treino.nome || `Treino ${indice + 1}`)}</h3>
                <p>${escapar(treino.descricao || "Treino ajustado ao seu perfil")} · ${Number(treino.duracao) || 0} min · ${(treino.exercicios || []).length} exercícios</p>
                <ul>${(treino.exercicios || []).map(exercicio => `<li><strong>${escapar(exercicio.nome)}</strong><span>${Number(exercicio.series) || 0} séries · ${Number(exercicio.repeticoes) || 0} repetições</span></li>`).join("")}</ul>
                <button type="button" class="recommendation-select" data-selecionar-recomendacao="${indice}">${tituloAcao}</button>
            </article>`).join("") || `<p class="empty-exercises">Não há sugestões disponíveis agora.</p>`}</div>
            <p class="recommendation-message" data-recommendation-message aria-live="polite"></p>
        </div>`;
        if (!recomendacoesDialogo.open) recomendacoesDialogo.showModal();
    }

    function renderizarMontagemManual() {
        const catalogo = Array.isArray(recomendacoesAtuais?.catalogo) ? recomendacoesAtuais.catalogo : [];
        const grupos = { pernas: "Pernas", gluteos: "Glúteos", peitoral: "Peitoral", costas: "Costas", bracos: "Braços", ombros: "Ombros", abdominais: "Abdominais" };
        recomendacoesDialogo.innerHTML = `<div class="recommendations-content">
            <div class="recommendations-heading"><div><span class="sample-tag">TREINO PERSONALIZADO</span><h2 id="recommendationsTitle">Monte seu treino</h2><p>Escolha os exercícios compatíveis com seu perfil. Selecione de 3 a 12 opções.</p></div>
                <button type="button" class="exercise-guide-close" data-close-recommendations aria-label="Fechar">×</button></div>
            <form class="manual-workout-form" data-manual-workout-form>
                <label>Nome do treino<input name="nome" type="text" maxlength="60" required value="Meu treino" placeholder="Ex.: Treino de superiores"></label>
                <label>Duração aproximada<select name="duracao"><option value="30">30 minutos</option><option value="45" selected>45 minutos</option><option value="60">60 minutos</option><option value="75">75 minutos</option><option value="90">90 minutos</option></select></label>
                <fieldset><legend>Exercícios disponíveis</legend><div class="manual-exercise-list">${catalogo.map(exercicio => `<label class="manual-exercise-option"><input type="checkbox" name="exercicios" value="${escapar(exercicio.nome)}"><span><strong>${escapar(exercicio.nome)}</strong><small>${escapar(grupos[exercicio.grupo] || exercicio.grupo || "Exercício")}</small></span></label>`).join("") || `<p class="empty-exercises">Não há exercícios compatíveis disponíveis para este perfil.</p>`}</div></fieldset>
                <p class="recommendation-message" data-recommendation-message aria-live="polite"></p>
                <div class="manual-workout-actions"><button type="button" class="training-action" data-voltar-recomendacoes>Voltar às sugestões</button><button type="submit" class="recommendation-select">Adicionar meu treino</button></div>
            </form>
        </div>`;
    }

    function linhaExercicioLivre(indice) {
        return `<div class="custom-exercise-row">
            <label>Exercício<input type="text" name="exercicios" maxlength="120" required placeholder="Ex.: Agachamento" aria-label="Nome do exercício ${indice + 1}"></label>
            <label>Séries<input type="number" name="series" min="1" max="10" value="3" required aria-label="Séries do exercício ${indice + 1}"></label>
            <label>Repetições<input type="number" name="repeticoes" min="1" max="100" value="12" required aria-label="Repetições do exercício ${indice + 1}"></label>
            <label>Descanso (s)<input type="number" name="descansos" min="0" max="300" value="60" required aria-label="Descanso do exercício ${indice + 1}"></label>
            <button type="button" class="custom-exercise-remove" data-remover-exercicio aria-label="Remover exercício ${indice + 1}">×</button>
        </div>`;
    }

    function renderizarTreinoLivre() {
        recomendacoesDialogo.innerHTML = `<div class="recommendations-content">
            <div class="recommendations-heading"><div><span class="sample-tag">MONTAGEM LIVRE</span><h2 id="recommendationsTitle">Crie do seu jeito</h2><p>Escreva os exercícios na ordem que preferir e defina séries, repetições e descanso.</p></div>
                <button type="button" class="exercise-guide-close" data-close-recommendations aria-label="Fechar">×</button></div>
            <form class="manual-workout-form" data-free-workout-form>
                <label>Nome do treino<input name="nome" type="text" maxlength="60" required value="Meu treino" placeholder="Ex.: Treino de superiores"></label>
                <label>Duração aproximada<select name="duracao"><option value="30">30 minutos</option><option value="45" selected>45 minutos</option><option value="60">60 minutos</option><option value="75">75 minutos</option><option value="90">90 minutos</option></select></label>
                <fieldset><legend>Seus exercícios</legend><div class="custom-exercise-list" data-custom-exercise-list>${linhaExercicioLivre(0)}</div>
                    <button type="button" class="training-action custom-exercise-add" data-adicionar-exercicio>＋ Adicionar exercício</button></fieldset>
                <p class="manual-custom-note">Você define a ordem e a prescrição. Use cargas adequadas à sua experiência e peça orientação profissional se tiver dúvidas.</p>
                <p class="recommendation-message" data-recommendation-message aria-live="polite"></p>
                <div class="manual-workout-actions"><button type="button" class="training-action" data-voltar-recomendacoes>Voltar às sugestões</button><button type="submit" class="recommendation-select">Criar meu treino</button></div>
            </form>
        </div>`;
    }

    async function abrirRecomendacoes(tipo, treinoId, botaoOrigem) {
        acaoRecomendacao = { tipo, treinoId };
        if (botaoOrigem) botaoOrigem.disabled = true;
        if (botaoRecomendar) botaoRecomendar.disabled = true;
        try {
            const resposta = await fetch("/api/treinos/recomendados", { cache: "no-store" });
            const dados = await resposta.json();
            if (resposta.status === 401) return location.href = "/login.html";
            if (!resposta.ok) throw new Error(dados.mensagem || "Não foi possível gerar recomendações.");
            recomendacoesAtuais = dados;
            renderizarRecomendacoes(dados);
        } catch (erro) {
            if (recomendacoesDialogo.open) {
                const mensagem = recomendacoesDialogo.querySelector("[data-recommendation-message]");
                if (mensagem) mensagem.textContent = erro.message;
            } else {
                lista.insertAdjacentHTML("afterbegin", `<p class="recommendation-error" role="alert">${escapar(erro.message)}</p>`);
            }
        } finally {
            if (botaoOrigem) botaoOrigem.disabled = false;
            if (botaoRecomendar) botaoRecomendar.disabled = false;
        }
    }

    recomendacoesDialogo.addEventListener("click", async event => {
        if (event.target === recomendacoesDialogo || event.target.closest("[data-close-recommendations]")) {
            recomendacoesDialogo.close();
            return;
        }
        if (event.target.closest("[data-montar-personalizado]")) {
            renderizarMontagemManual();
            return;
        }
        if (event.target.closest("[data-criar-do-meu-jeito]")) {
            renderizarTreinoLivre();
            return;
        }
        if (event.target.closest("[data-adicionar-exercicio]")) {
            const formulario = event.target.closest("[data-free-workout-form]");
            const listaExercicios = formulario?.querySelector("[data-custom-exercise-list]");
            const quantidade = listaExercicios?.querySelectorAll(".custom-exercise-row").length || 0;
            if (listaExercicios && quantidade < 12) {
                listaExercicios.insertAdjacentHTML("beforeend", linhaExercicioLivre(quantidade));
                atualizarBotoesRemover(listaExercicios);
            }
            return;
        }
        if (event.target.closest("[data-remover-exercicio]")) {
            const listaExercicios = event.target.closest("[data-custom-exercise-list]");
            event.target.closest(".custom-exercise-row")?.remove();
            if (listaExercicios) atualizarBotoesRemover(listaExercicios);
            return;
        }
        if (event.target.closest("[data-voltar-recomendacoes]")) {
            if (recomendacoesAtuais) renderizarRecomendacoes(recomendacoesAtuais);
            return;
        }
        const botao = event.target.closest("[data-selecionar-recomendacao]");
        if (!botao || !recomendacoesAtuais) return;
        botao.disabled = true;
        const payload = new URLSearchParams({ token: recomendacoesAtuais.token, indice: botao.dataset.selecionarRecomendacao });
        const url = acaoRecomendacao.tipo === "substituir"
            ? `/api/treinos/${acaoRecomendacao.treinoId}?${payload}`
            : `/api/treinos/recomendados?${payload}`;
        try {
            const resposta = await fetch(url, { method: acaoRecomendacao.tipo === "substituir" ? "PUT" : "POST" });
            const dados = await resposta.json();
            if (resposta.status === 401) return location.href = "/login.html";
            if (!resposta.ok) throw new Error(dados.mensagem || "Não foi possível salvar a alteração.");
            recomendacoesAtuais = null;
            await carregar();
            if (acaoRecomendacao.tipo === "adicionar") {
                await abrirRecomendacoes("adicionar", null, null);
            } else {
                recomendacoesDialogo.close();
            }
        } catch (erro) {
            const mensagem = recomendacoesDialogo.querySelector("[data-recommendation-message]");
            if (mensagem) mensagem.textContent = erro.message;
            botao.disabled = false;
        }
    });

    function atualizarBotoesRemover(listaExercicios) {
        const linhas = [...listaExercicios.querySelectorAll(".custom-exercise-row")];
        linhas.forEach((linha, indice) => {
            const botao = linha.querySelector("[data-remover-exercicio]");
            if (botao) {
                botao.disabled = linhas.length <= 1;
                botao.setAttribute("aria-label", `Remover exercício ${indice + 1}`);
            }
        });
        const adicionar = recomendacoesDialogo.querySelector("[data-adicionar-exercicio]");
        if (adicionar) adicionar.disabled = linhas.length >= 12;
    }

    recomendacoesDialogo.addEventListener("submit", async event => {
        const formulario = event.target.closest("[data-manual-workout-form], [data-free-workout-form]");
        if (!formulario) return;
        event.preventDefault();
        const mensagem = formulario.querySelector("[data-recommendation-message]");
        const campos = new FormData(formulario);
        const montagemLivre = formulario.matches("[data-free-workout-form]");
        const selecionados = [...formulario.querySelectorAll('input[name="exercicios"]:checked')];
        if (!montagemLivre && (selecionados.length < 3 || selecionados.length > 12)) {
            mensagem.textContent = "Escolha entre 3 e 12 exercícios para montar o treino.";
            return;
        }
        if (montagemLivre) {
            const quantidade = formulario.querySelectorAll(".custom-exercise-row").length;
            if (quantidade < 1 || quantidade > 12) {
                mensagem.textContent = "Adicione de 1 a 12 exercícios ao seu treino.";
                return;
            }
        }
        const dados = new URLSearchParams();
        for (const [chave, valor] of campos.entries()) dados.append(chave, valor);
        dados.set("token", recomendacoesAtuais.token);
        if (!montagemLivre) {
            dados.delete("exercicios");
            selecionados.forEach(exercicio => dados.append("exercicios", exercicio.value));
        }
        treinoManualPendente = {
            url: montagemLivre ? "/api/treinos/personalizado-livre" : "/api/treinos/personalizado",
            dados
        };
        const feedbackConfirmacao = confirmarTreinoDialogo.querySelector("[data-confirm-feedback]");
        feedbackConfirmacao.textContent = "";
        confirmarTreinoDialogo.showModal();
    });

    confirmarTreinoDialogo.addEventListener("click", async event => {
        if (event.target.closest("[data-cancelar-criacao]")) {
            treinoManualPendente = null;
            confirmarTreinoDialogo.close();
            return;
        }
        if (!event.target.closest("[data-confirmar-criacao]") || !treinoManualPendente) return;
        const confirmar = event.target.closest("[data-confirmar-criacao]");
        const cancelar = confirmarTreinoDialogo.querySelector("[data-cancelar-criacao]");
        const feedback = confirmarTreinoDialogo.querySelector("[data-confirm-feedback]");
        confirmar.disabled = true;
        cancelar.disabled = true;
        feedback.textContent = "Criando seu treino…";
        try {
            const resposta = await fetch(treinoManualPendente.url, {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
                body: treinoManualPendente.dados
            });
            const retorno = await resposta.json();
            if (resposta.status === 401) return location.href = "/login.html";
            if (!resposta.ok) throw new Error(retorno.mensagem || "Não foi possível salvar seu treino.");
            treinoManualPendente = null;
            confirmarTreinoDialogo.close();
            recomendacoesAtuais = null;
            await carregar();
            await abrirRecomendacoes("adicionar", null, null);
        } catch (erro) {
            feedback.textContent = erro.message;
            confirmar.disabled = false;
            cancelar.disabled = false;
        }
    });

    confirmarTreinoDialogo.addEventListener("cancel", () => { treinoManualPendente = null; });

    botaoRecomendar?.addEventListener("click", () => abrirRecomendacoes("adicionar", null, botaoRecomendar));

    function abrirGuia(nome, grupo) {
        const guias = window.MYH_EXERCISE_GUIDES || {};
        const nomeBase = nome.replace(/\s*\([^)]*\)\s*$/, "").trim();
        const guia = guias[nome] || guias[nomeBase] || guias.default;
        const video = "https://www.youtube.com/results?search_query=" + encodeURIComponent(`como fazer ${nome} execução correta`);
        guiaDialogo.innerHTML = `<div class="exercise-guide-content">
            <div class="exercise-guide-heading">
                <div><h2 id="exerciseGuideTitle">${escapar(nome)}</h2><p>${escapar(grupo || "Orientação de execução")}</p></div>
                <button type="button" class="exercise-guide-close" data-close-guide aria-label="Fechar guia">×</button>
            </div>
            <a class="exercise-guide-video" href="${video}" target="_blank" rel="noopener noreferrer"><span aria-hidden="true">▶</span><span><strong>Assistir vídeo demonstrativo</strong><small>Abre uma busca no YouTube em nova aba</small></span></a>
            <h3>Passo a passo</h3>
            <ol>${guia.passos.map(passo => `<li>${escapar(passo)}</li>`).join("")}</ol>
            <p class="exercise-guide-tip"><strong>Dica:</strong> ${escapar(guia.dica)}</p>
            <button type="button" class="exercise-guide-done" data-close-guide>Entendi</button>
        </div>`;
        guiaDialogo.showModal();
    }

    guiaDialogo.addEventListener("click", event => {
        if (event.target === guiaDialogo || event.target.closest("[data-close-guide]")) guiaDialogo.close();
    });

    busca.addEventListener("input", () => {
        clearTimeout(temporizador);
        temporizador = setTimeout(carregar, 250);
    });
    carregar();
})();


/* Guias de execução dos exercícios, consolidados do antigo meu-treino-guias.js. */
(() => {
    const passos = (itens, dica) => ({ passos: itens, dica });
    window.MYH_EXERCISE_GUIDES = {
        "Agachamento com peso corporal": passos([
            "Fique em pé com os pés aproximadamente na largura dos ombros.",
            "Leve o quadril para trás e flexione os joelhos, mantendo-os alinhados aos pés.",
            "Desça somente até onde consiga manter o tronco firme e os calcanhares apoiados.",
            "Empurre o chão para voltar à posição inicial, sem travar os joelhos."
        ], "Faça o movimento sem pressa. Reduza a amplitude se sentir desconforto nos joelhos ou nas costas."),
        "Sentar e levantar do banco": passos([
            "Sente-se na parte da frente do banco, com os pés apoiados no chão.",
            "Incline levemente o tronco e empurre o chão para ficar em pé.",
            "Leve o quadril para trás e sente-se devagar, controlando a descida."
        ], "Use um banco firme e, se necessário, apoie as mãos para manter o equilíbrio."),
        "Leg press em amplitude confortável": passos([
            "Ajuste o banco para que as costas e o quadril permaneçam apoiados.",
            "Posicione os pés na plataforma, aproximadamente na largura dos ombros.",
            "Desça a plataforma só até onde o quadril continue apoiado.",
            "Empurre a plataforma de volta sem travar os joelhos."
        ], "Use uma carga confortável e não force a amplitude se houver dor."),
        "Agachamento goblet": passos([
            "Segure um halter junto ao peito e firme os pés no chão.",
            "Leve o quadril para trás e para baixo, mantendo o peso próximo ao corpo.",
            "Desça com os joelhos acompanhando a direção dos pés.",
            "Empurre o chão para voltar à posição inicial."
        ], "Mantenha a coluna neutra e escolha uma carga que permita controlar todas as repetições."),
        "Extensão de joelhos na máquina": passos([
            "Ajuste o banco e o rolo para que o eixo da máquina fique alinhado aos joelhos.",
            "Estenda as pernas com controle, sem dar impulso.",
            "Pause brevemente perto do final e volte devagar à posição inicial."
        ], "Evite esticar os joelhos com força ou usar uma carga que cause dor."),
        "Ponte de glúteos": passos([
            "Deite-se de costas com os joelhos flexionados e os pés apoiados no chão.",
            "Contraia o abdômen e os glúteos.",
            "Eleve o quadril até alinhar tronco e coxas, sem arquear a lombar.",
            "Desça devagar até perto do chão e repita."
        ], "O movimento deve vir do quadril; não force a lombar no topo."),
        "Hip thrust": passos([
            "Apoie a parte alta das costas em um banco firme e mantenha os pés no chão.",
            "Comece com o quadril próximo ao chão e o queixo levemente recolhido.",
            "Eleve o quadril contraindo os glúteos até formar uma linha entre ombros e joelhos.",
            "Desça com controle, mantendo os pés estáveis."
        ], "Comece sem carga até dominar a posição e não hiperestenda as costas."),
        "Extensão de quadril em pé com apoio": passos([
            "Segure em um apoio estável e fique com o tronco ereto.",
            "Leve uma perna para trás sem girar o quadril.",
            "Retorne devagar à posição inicial e complete as repetições antes de trocar de lado."
        ], "Use uma amplitude confortável e evite inclinar ou arquear a lombar."),
        "Cadeira flexora": passos([
            "Ajuste a máquina para que o eixo fique alinhado aos joelhos e o rolo fique acima dos calcanhares.",
            "Flexione os joelhos trazendo o rolo em direção às pernas.",
            "Volte devagar, sem deixar a carga bater."
        ], "Mantenha o quadril apoiado no banco e não use impulso."),
        "Flexão na parede": passos([
            "Apoie as mãos na parede na altura do peito, um pouco mais abertas que os ombros.",
            "Afaste os pés e mantenha o corpo alinhado da cabeça aos calcanhares.",
            "Flexione os cotovelos levando o peito em direção à parede.",
            "Empurre a parede para retornar, sem deixar o quadril cair."
        ], "Quanto mais perto da parede, mais leve fica o exercício."),
        "Flexão inclinada": passos([
            "Apoie as mãos em uma superfície firme, na largura dos ombros.",
            "Estenda as pernas e mantenha o corpo alinhado.",
            "Flexione os cotovelos e aproxime o peito do apoio.",
            "Empurre o apoio para voltar à posição inicial."
        ], "Use um apoio que não escorregue e mantenha o abdômen contraído."),
        "Supino máquina leve": passos([
            "Ajuste o assento para que as alças fiquem na altura do meio do peito.",
            "Apoie as costas e segure as alças com os punhos alinhados aos antebraços.",
            "Empurre as alças para a frente sem travar os cotovelos.",
            "Retorne devagar até sentir alongamento confortável no peito."
        ], "Mantenha os ombros afastados das orelhas e use carga leve no início."),
        "Supino com halteres": passos([
            "Deite-se no banco com os pés firmes no chão e os halteres sobre o peito.",
            "Mantenha os cotovelos ligeiramente abaixo da linha dos ombros.",
            "Empurre os halteres para cima sem bater um no outro.",
            "Desça devagar até uma amplitude confortável."
        ], "Mantenha as escápulas apoiadas no banco e peça ajuda para posicionar cargas pesadas."),
        "Remada com elástico": passos([
            "Prenda o elástico em um ponto firme, aproximadamente na altura do peito.",
            "Segure as pontas, afaste-se até sentir leve tensão e mantenha o tronco estável.",
            "Puxe os cotovelos para trás, aproximando as mãos do tronco.",
            "Retorne devagar sem perder a tensão do elástico."
        ], "Confira se o elástico está bem preso e não puxe em direção ao rosto."),
        "Remada máquina com apoio do peito": passos([
            "Ajuste o banco para apoiar o peito e alcance as alças sem arredondar as costas.",
            "Puxe as alças em direção ao tronco levando os cotovelos para trás.",
            "Aperte as costas brevemente e retorne com controle."
        ], "Mantenha o peito apoiado e evite encolher os ombros."),
        "Puxada frontal leve": passos([
            "Ajuste o apoio das coxas e segure a barra um pouco mais aberta que os ombros.",
            "Puxe a barra à frente do corpo em direção à parte alta do peito.",
            "Leve os cotovelos para baixo e retorne devagar com os braços quase estendidos."
        ], "Não puxe a barra atrás da nuca nem balance o tronco."),
        "Remada unilateral apoiada": passos([
            "Apoie uma mão e um joelho em um banco firme, mantendo as costas alinhadas.",
            "Segure o halter com o braço estendido e puxe o cotovelo em direção ao quadril.",
            "Desça o halter devagar sem girar o tronco.",
            "Complete as repetições e troque de lado."
        ], "Use um peso que permita manter o tronco estável."),
        "Rosca direta leve": passos([
            "Fique em pé com os pés firmes e segure a barra ou os halteres ao lado do corpo.",
            "Mantenha os cotovelos próximos ao tronco e flexione os braços.",
            "Aproxime o peso dos ombros sem mover os cotovelos para a frente.",
            "Desça devagar até quase estender os braços."
        ], "Evite balançar o corpo; reduza a carga se precisar usar impulso."),
        "Tríceps na polia leve": passos([
            "Fique de frente para a polia e segure a corda com os cotovelos junto ao tronco.",
            "Estenda os braços para baixo sem mover os ombros.",
            "Afaste as pontas da corda no final e retorne com controle."
        ], "Mantenha os punhos neutros e use uma carga confortável."),
        "Elevação lateral leve": passos([
            "Fique em pé com um halter leve em cada mão e os braços ao lado do corpo.",
            "Eleve os braços lateralmente até uma altura confortável, com os cotovelos levemente flexionados.",
            "Desça lentamente até a posição inicial."
        ], "Não balance o corpo nem eleve os ombros em direção às orelhas."),
        "Panturrilha em pé com apoio": passos([
            "Fique em pé segurando um apoio estável, com os pés paralelos.",
            "Eleve os calcanhares e suba na ponta dos pés.",
            "Pause brevemente e desça os calcanhares devagar."
        ], "Faça o movimento sem quicar e use o apoio para manter o equilíbrio."),
        "Dead bug": passos([
            "Deite-se de costas com os braços apontados para cima e os joelhos flexionados.",
            "Contraia o abdômen e mantenha a lombar confortável junto ao chão.",
            "Estenda devagar um braço e a perna oposta sem perder o controle do tronco.",
            "Volte ao centro e alterne os lados."
        ], "Diminua a amplitude ou mova apenas uma perna se a lombar sair da posição."),
        "Bird dog": passos([
            "Fique em quatro apoios com mãos abaixo dos ombros e joelhos abaixo do quadril.",
            "Contraia levemente o abdômen e estenda um braço e a perna oposta.",
            "Mantenha o tronco estável por um instante e retorne devagar.",
            "Alterne os lados."
        ], "Evite girar o quadril ou arquear a lombar."),
        "Prancha adaptada": passos([
            "Apoie os antebraços e os joelhos no chão, com os cotovelos abaixo dos ombros.",
            "Alinhe cabeça, tronco e quadril e contraia o abdômen.",
            "Respire normalmente e mantenha a posição pelo tempo indicado.",
            "Apoie o corpo no chão antes de descansar."
        ], "Pare se sentir dor nas costas ou nos ombros; não deixe o quadril afundar."),
        "Bicicleta ergométrica": passos([
            "Ajuste o banco para que o joelho fique levemente flexionado quando o pedal estiver embaixo.",
            "Comece pedalando devagar e aumente o ritmo somente até ficar confortável.",
            "Mantenha o tronco relaxado e respire continuamente durante o tempo indicado."
        ], "Reduza o ritmo ou pare se sentir dor, tontura ou falta de ar fora do normal."),
        "Cicloergômetro em ritmo leve": passos([
            "Ajuste o equipamento e sente-se com as costas apoiadas.",
            "Pedale em ritmo leve e constante pelo tempo indicado.",
            "Mantenha a respiração confortável e desacelere gradualmente ao terminar."
        ], "Interrompa se sentir dor ou mal-estar."),
        "Caminhada em ritmo confortável": passos([
            "Escolha um percurso plano e seguro e comece em ritmo leve.",
            "Caminhe com passos naturais, braços relaxados e olhar à frente.",
            "Mantenha um ritmo em que ainda consiga falar frases curtas.",
            "Desacelere aos poucos ao final do tempo indicado."
        ], "Use calçado adequado e pare se sentir dor ou tontura."),
        "Caminhada plana em ritmo confortável": passos([
            "Escolha uma superfície plana, firme e sem obstáculos.",
            "Comece devagar e caminhe com passos curtos e confortáveis.",
            "Mantenha o tronco ereto e os braços relaxados.",
            "Desacelere gradualmente ao final."
        ], "A opção plana reduz inclinações; interrompa se sentir dor nos joelhos."),
        "Caminhada intervalada leve": passos([
            "Comece com alguns minutos de caminhada tranquila.",
            "Alterne períodos curtos de ritmo um pouco mais vivo com períodos leves.",
            "Repita pelo tempo indicado e termine desacelerando."
        ], "Mantenha os intervalos moderados e confortáveis; não é necessário correr."),
        "Marcha leve sem impacto": passos([
            "Fique em pé próximo a um apoio estável, se precisar de equilíbrio.",
            "Alterne a elevação baixa dos pés, como uma caminhada no lugar.",
            "Mantenha o movimento suave e contínuo pelo tempo indicado."
        ], "Mantenha os pés próximos ao chão e reduza o ritmo se as articulações incomodarem."),
        "Marcha estacionária leve": passos([
            "Fique em pé em um local livre de obstáculos.",
            "Alterne os passos no mesmo lugar, elevando os pés apenas até uma altura confortável.",
            "Use os braços naturalmente e mantenha o ritmo leve pelo tempo indicado."
        ], "Segure em um apoio se precisar de equilíbrio e pare se sentir mal-estar."),
        "Elíptico em ritmo leve": passos([
            "Suba no equipamento parado e segure nas alças antes de começar.",
            "Inicie com passadas curtas e lentas, mantendo os pés apoiados nas plataformas.",
            "Aumente o ritmo gradualmente até ficar confortável e mantenha-o pelo tempo indicado.",
            "Reduza o ritmo antes de parar e descer."
        ], "Não desça enquanto os pedais ainda estiverem em movimento."),
        "default": passos([
            "Confira a regulagem e a estabilidade do equipamento antes de começar.",
            "Faça o movimento devagar, com uma carga confortável e sem prender a respiração.",
            "Interrompa se sentir dor e peça orientação a um profissional."
        ], "A técnica e uma amplitude confortável são mais importantes do que aumentar a carga.")
    };
})();

