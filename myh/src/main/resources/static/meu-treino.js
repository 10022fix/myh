(() => {
    const lista = document.getElementById("lista");
    const busca = document.getElementById("busca");
    let temporizador;
    const marcados = {};

    const escapar = valor => String(valor ?? "").replace(/[&<>"']/g, caractere => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    }[caractere]));

    function chaveExercicio(treino, exercicio, indice) {
        const treinoId = treino.id ?? treino.nome ?? "treino";
        const nome = exercicio.nome ?? `exercicio-${indice + 1}`;
        return `${treinoId}:${indice}:${nome}`;
    }

    function renderizarTreino(treino, indiceTreino) {
        const exercicios = Array.isArray(treino.exercicios) ? treino.exercicios : [];
        const duracao = Number(treino.duracao);
        const titulo = escapar(treino.nome || `Treino ${indiceTreino + 1}`);
        const detalhes = exercicios.map((exercicio, indice) => {
            const chave = chaveExercicio(treino, exercicio, indice);
            const marcado = Boolean(marcados[chave]);
            const repeticoes = Number(exercicio.repeticoes);
            const prescricao = repeticoes === 0
                ? "Tempo indicado no nome do exercício"
                : `${Number(exercicio.series) || 0} séries · ${repeticoes || 0} repetições · ${Number(exercicio.descanso) || 0}s descanso`;
            const grupo = exercicio.grupoMuscular || exercicio.grupo || exercicio.musculo || "";
            return `<label class="exercise${marcado ? " is-complete" : ""}">
                <input type="checkbox" data-exercise-key="${escapar(chave)}" ${marcado ? "checked" : ""} aria-label="Marcar ${escapar(exercicio.nome)} como concluído">
                <span class="exercise-number">${String(indice + 1).padStart(2, "0")}</span>
                <span class="exercise-copy"><strong>${escapar(exercicio.nome || "Exercício")}</strong><small>${escapar(prescricao)}</small></span>
                ${grupo ? `<b>${escapar(grupo)}</b>` : ""}
            </label>`;
        }).join("");
        const completo = exercicios.filter((exercicio, indice) => marcados[chaveExercicio(treino, exercicio, indice)]).length;
        const total = exercicios.length;
        const percentual = total ? Math.round(completo / total * 100) : 0;
        const duracaoTexto = Number.isFinite(duracao) && duracao > 0 ? `${duracao} min` : "Duração não informada";
        const nivel = treino.nivel ? escapar(treino.nivel) : "PLANO PERSONALIZADO";
        const treinoId = escapar(treino.id ?? treino.nome ?? indiceTreino);

        return `<article class="training-card demo-content-card" data-training-id="${treinoId}">
            <div class="section-heading">
                <div><h2>${titulo}</h2><p>${total} ${total === 1 ? "exercício" : "exercícios"} · ${duracaoTexto}</p></div>
                <span class="sample-tag">${nivel}</span>
            </div>
            <div class="exercise-list">${detalhes || `<p class="empty-exercises">Este treino ainda não tem exercícios cadastrados.</p>`}</div>
            <div class="workout-progress" role="progressbar" aria-label="Progresso de ${titulo}" aria-valuemin="0" aria-valuemax="${total}" aria-valuenow="${completo}">
                <span style="width:${percentual}%"></span>
            </div>
            <p class="demo-feedback" data-workout-feedback>${completo} de ${total} exercícios concluídos</p>
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
    }

    async function carregar() {
        try {
            const resposta = await fetch("/api/treinos?busca=" + encodeURIComponent(busca.value));
            if (resposta.status === 401) {
                location.href = "/login.html";
                return;
            }
            if (!resposta.ok) {
                lista.innerHTML = `<section class="empty-state"><h2>Não foi possível carregar os treinos.</h2><p>O servidor retornou o erro ${resposta.status}. Tente novamente mais tarde.</p></section>`;
                return;
            }
            const treinos = await resposta.json();
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

    busca.addEventListener("input", () => {
        clearTimeout(temporizador);
        temporizador = setTimeout(carregar, 250);
    });
    carregar();
})();
