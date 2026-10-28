(() => {
    const $ = id => document.getElementById(id);
    const form = $("formulario");
    const peso = $("pesoRegistro");
    const data = $("dataRegistro");
    const svgNS = "http://www.w3.org/2000/svg";
    let datasRegistradas = new Set();

    function dataLocalISO() {
        const hoje = new Date();
        hoje.setMinutes(hoje.getMinutes() - hoje.getTimezoneOffset());
        return hoje.toISOString().slice(0, 10);
    }

    function formatarPeso(valor) {
        if (valor == null || !Number.isFinite(Number(valor))) return "—";
        return `${Number(valor).toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })} kg`;
    }

    function formatarData(valor) {
        if (!valor) return "Data não informada";
        const dataRegistro = new Date(`${valor}T12:00:00`);
        return Number.isNaN(dataRegistro.getTime()) ? valor : dataRegistro.toLocaleDateString("pt-BR");
    }

    function desenharGrafico(registros) {
        const pontos = $("pontosPeso");
        const linha = $("linhaPeso");
        const vazio = $("graficoVazio");
        pontos.replaceChildren();
        linha.setAttribute("points", "");
        vazio.hidden = registros.length > 0;
        if (!registros.length) return;

        const cronologicos = [...registros].reverse();
        const valores = cronologicos.map(item => Number(item.peso)).filter(Number.isFinite);
        if (!valores.length) {
            vazio.hidden = false;
            return;
        }
        const minimo = Math.min(...valores);
        const maximo = Math.max(...valores);
        const amplitude = maximo - minimo || 1;
        const xInicial = valores.length === 1 ? 380 : 28;
        const xFinal = valores.length === 1 ? 380 : 732;
        const pontosXY = valores.map((valor, indice) => {
            const x = valores.length === 1 ? xInicial : xInicial + (xFinal - xInicial) * indice / (valores.length - 1);
            const y = 174 - ((valor - minimo) / amplitude) * 94;
            return { x, y, valor, registro: cronologicos[indice] };
        });
        linha.setAttribute("points", pontosXY.map(ponto => `${ponto.x},${ponto.y}`).join(" "));
        pontosXY.forEach(ponto => {
            const circulo = document.createElementNS(svgNS, "circle");
            circulo.setAttribute("cx", String(ponto.x));
            circulo.setAttribute("cy", String(ponto.y));
            circulo.setAttribute("r", "10");
            const titulo = document.createElementNS(svgNS, "title");
            titulo.textContent = `${formatarPeso(ponto.valor)} · ${formatarData(ponto.registro.data)}`;
            circulo.append(titulo);
            pontos.append(circulo);
        });
        $("graficoPeso").setAttribute("aria-label", `Evolução do peso em ${valores.length} ${valores.length === 1 ? "registro" : "registros"}`);
    }

    function renderizar(registros) {
        const itens = Array.isArray(registros) ? registros : [];
        datasRegistradas = new Set(itens.map(item => item.data));
        const maisRecente = itens[0];
        const maisAntigo = itens[itens.length - 1];
        $("ultimoPeso").textContent = maisRecente ? formatarPeso(maisRecente.peso) : "—";
        $("totalRegistros").textContent = String(itens.length);
        if (maisRecente && maisAntigo) {
            const variacao = Number(maisRecente.peso) - Number(maisAntigo.peso);
            const prefixo = variacao > 0 ? "+" : "";
            $("variacaoPeso").textContent = `${prefixo}${variacao.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })} kg`;
        } else {
            $("variacaoPeso").textContent = "—";
        }
        desenharGrafico(itens);

        const historico = $("historico");
        historico.hidden = itens.length === 0;
        $("registros").innerHTML = itens.map(item => {
            const medidas = [["Gordura", item.gordura, "%"], ["Braço", item.braco, " cm"], ["Antebraço", item.antebraco, " cm"], ["Cintura", item.cintura, " cm"], ["Peito", item.peito, " cm"], ["Quadril", item.quadril, " cm"], ["Coxa", item.coxa, " cm"], ["Panturrilha", item.panturrilha, " cm"]]
                .filter(([, valor]) => valor != null)
                .map(([nome, valor, unidade]) => `${nome}: ${valor}${unidade}`).join(" · ");
            return `<div class="history-entry"><div class="history-entry-details"><strong>${formatarPeso(item.peso)}</strong>${medidas ? `<small>${medidas}</small>` : ""}</div><span class="history-entry-date">${formatarData(item.data)}</span><button class="history-delete" type="button" data-excluir-registro="${Number(item.id)}" aria-label="Excluir registro de ${formatarData(item.data)}">Excluir</button></div>`;
        }).join("");
    }

    $("registros").addEventListener("click", async event => {
        const botao = event.target.closest("[data-excluir-registro]");
        if (!botao) return;
        const registroId = Number(botao.dataset.excluirRegistro);
        if (!Number.isInteger(registroId) || registroId <= 0) return;
        if (!window.confirm("Tem certeza de que deseja excluir este registro de progresso?")) return;

        botao.disabled = true;
        $("mensagem").textContent = "Excluindo registro…";
        $("mensagem").classList.remove("erro");
        try {
            const resposta = await fetch(`/api/progresso/${registroId}`, { method: "DELETE" });
            const retorno = await resposta.json().catch(() => ({}));
            if (!resposta.ok) throw new Error(retorno.mensagem || "Não foi possível excluir o registro.");
            $("mensagem").textContent = retorno.mensagem || "Registro de progresso excluído.";
            await carregar();
        } catch (erro) {
            $("mensagem").textContent = erro.message || "Falha de conexão ao excluir o registro.";
            $("mensagem").classList.add("erro");
        } finally {
            botao.disabled = false;
        }
    });

    async function carregar() {
        try {
            const resposta = await fetch("/api/progresso");
            if (resposta.status === 401) {
                location.href = "/login.html";
                return;
            }
            if (!resposta.ok) throw new Error(`O servidor retornou o erro ${resposta.status}.`);
            renderizar(await resposta.json());
        } catch (erro) {
            $("graficoVazio").hidden = false;
            $("graficoVazio").textContent = erro.message || "Não foi possível carregar seus registros.";
            $("mensagem").textContent = "Falha ao carregar o progresso. Você ainda pode tentar salvar um novo registro.";
            $("mensagem").classList.add("erro");
        }
    }

    data.value = dataLocalISO();
    data.max = dataLocalISO();
    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (datasRegistradas.has(data.value)) {
            $("mensagem").textContent = "Você já tem um registro nesta data. Exclua-o no histórico se quiser corrigir o peso.";
            $("mensagem").classList.add("erro");
            return;
        }
        const botao = form.querySelector('button[type="submit"]');
        botao.disabled = true;
        $("mensagem").textContent = "Salvando registro…";
        $("mensagem").classList.remove("erro");
        try {
            const resposta = await fetch("/api/progresso", {
                method: "POST",
                body: new URLSearchParams(new FormData(form))
            });
            const retorno = await resposta.json();
            if (!resposta.ok) throw new Error(retorno.mensagem || "Não foi possível salvar o registro.");
            $("mensagem").textContent = retorno.mensagem || "Registro salvo com sucesso.";
            form.reset();
            data.value = dataLocalISO();
            data.max = dataLocalISO();
            await carregar();
        } catch (erro) {
            $("mensagem").textContent = erro.message || "Falha de conexão ao salvar o registro.";
            $("mensagem").classList.add("erro");
        } finally {
            botao.disabled = false;
        }
    });

    const formularioCarga = $("formularioCarga");
    const dataCarga = $("dataCarga");
    const treinoCarga = $("treinoCarga");
    const exercicioCarga = $("exercicioCarga");
    const historicoCargas = $("historicoCargas");
    const mensagemCarga = $("mensagemCarga");
    const hojeISO = dataLocalISO();
    dataCarga.max = hojeISO;
    dataCarga.value = hojeISO;
    $("mesCargas").value = hojeISO.slice(0, 7);

    function escaparHTML(valor) {
        return String(valor ?? "").replace(/[&<>"']/g, caractere => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[caractere]));
    }
    function nomeExibicaoTreino(nome) {
        const texto = String(nome ?? "").trim();
        const diaAntigo = texto.match(/^Dia\s+(\d+)(.*)$/i);
        if (diaAntigo) return `Treino ${diaAntigo[1]}${diaAntigo[2]}`;
        const letraAntiga = texto.match(/^Treino\s+([AB])(?=\s|$)(.*)$/i);
        if (letraAntiga) return `Treino ${letraAntiga[1].toUpperCase() === "A" ? 1 : 2}${letraAntiga[2]}`;
        return texto || "Treino";
    }
    async function carregarOpcoesCarga() {
        treinoCarga.disabled = true;
        exercicioCarga.disabled = true;
        treinoCarga.innerHTML = '<option value="">Carregando treinos…</option>';
        exercicioCarga.innerHTML = '<option value="">Selecione o treino</option>';
        if (!dataCarga.value || dataCarga.value > hojeISO) return;
        try {
            const resposta = await fetch(`/api/progresso/cargas/opcoes?data=${encodeURIComponent(dataCarga.value)}`);
            const opcoes = await resposta.json();
            if (!resposta.ok) throw new Error(opcoes.mensagem || "Não foi possível localizar treinos nessa data.");
            treinoCarga.innerHTML = opcoes.length
                ? '<option value="">Selecione um treino</option>' + opcoes.map(treino => `<option value="${Number(treino.treinoId)}">${escaparHTML(nomeExibicaoTreino(treino.treino))}</option>`).join("")
                : '<option value="">Nenhum treino nessa data</option>';
            treinoCarga.disabled = opcoes.length === 0;
            treinoCarga.dataset.opcoes = JSON.stringify(opcoes);
        } catch (erro) {
            treinoCarga.innerHTML = '<option value="">Erro ao carregar</option>';
            mensagemCarga.textContent = erro.message;
            mensagemCarga.classList.add("erro");
        }
    }
    function selecionarExerciciosCarga() {
        const opcoes = JSON.parse(treinoCarga.dataset.opcoes || "[]");
        const treino = opcoes.find(item => Number(item.treinoId) === Number(treinoCarga.value));
        const exercicios = treino?.exercicios || [];
        exercicioCarga.innerHTML = exercicios.length
            ? '<option value="">Selecione um exercício</option>' + exercicios.map(item => `<option value="${Number(item.exercicioId)}">${escaparHTML(item.exercicio)}</option>`).join("")
            : '<option value="">Nenhum exercício disponível</option>';
        exercicioCarga.disabled = exercicios.length === 0;
    }
    async function carregarHistoricoCargas() {
        const [ano, mes] = $("mesCargas").value.split("-").map(Number);
        if (!ano || !mes) return;
        historicoCargas.innerHTML = '<p class="progress-help">Carregando cargas…</p>';
        try {
            const resposta = await fetch(`/api/progresso/cargas?ano=${ano}&mes=${mes}`);
            const cargas = await resposta.json();
            if (!resposta.ok) throw new Error(cargas.mensagem || "Não foi possível carregar as cargas.");
            historicoCargas.innerHTML = cargas.length ? cargas.map(item => `<article class="load-history-entry">
                <div><strong>${escaparHTML(item.exercicio)}</strong><span>${escaparHTML(nomeExibicaoTreino(item.treino))} · ${formatarData(item.data)}</span></div>
                <div class="load-history-value">${formatarPeso(item.cargaKg)}${item.series ? `<small>${Number(item.series)} séries${item.repeticoes ? ` × ${Number(item.repeticoes)} reps` : ""}</small>` : ""}</div>
                <button type="button" class="history-delete" data-excluir-carga="${Number(item.id)}">Excluir</button>
            </article>`).join("") : '<p class="progress-help">Nenhuma carga registrada neste mês.</p>';
        } catch (erro) { historicoCargas.innerHTML = `<p class="progress-message erro">${escaparHTML(erro.message)}</p>`; }
    }
    dataCarga.addEventListener("change", carregarOpcoesCarga);
    treinoCarga.addEventListener("change", selecionarExerciciosCarga);
    $("mesCargas").addEventListener("change", carregarHistoricoCargas);
    formularioCarga.addEventListener("submit", async evento => {
        evento.preventDefault();
        const botao = formularioCarga.querySelector('[type="submit"]');
        const dados = new URLSearchParams();
        ["data", "treinoId", "exercicioId", "cargaKg", "series", "repeticoes"].forEach(nome => {
            const valor = formularioCarga.elements[nome]?.value;
            if (valor !== undefined && valor !== "") dados.set(nome, valor);
        });
        botao.disabled = true; mensagemCarga.textContent = "Salvando carga…"; mensagemCarga.classList.remove("erro");
        try {
            const resposta = await fetch("/api/progresso/cargas", { method: "POST", body: dados });
            const retorno = await resposta.json();
            if (!resposta.ok) throw new Error(retorno.mensagem || "Não foi possível salvar a carga.");
            mensagemCarga.textContent = retorno.mensagem || "Carga salva para este exercício.";
            formularioCarga.elements.cargaKg.value = "";
            formularioCarga.elements.series.value = "";
            formularioCarga.elements.repeticoes.value = "";
            await carregarHistoricoCargas();
        } catch (erro) { mensagemCarga.textContent = erro.message; mensagemCarga.classList.add("erro"); }
        finally { botao.disabled = false; }
    });
    historicoCargas.addEventListener("click", async evento => {
        const botao = evento.target.closest("[data-excluir-carga]");
        if (!botao || !window.confirm("Excluir este registro de carga?")) return;
        botao.disabled = true;
        try {
            const resposta = await fetch(`/api/progresso/cargas/${Number(botao.dataset.excluirCarga)}`, { method: "DELETE" });
            const retorno = await resposta.json();
            if (!resposta.ok) throw new Error(retorno.mensagem || "Não foi possível excluir a carga.");
            mensagemCarga.textContent = retorno.mensagem || "Carga removida.";
            await carregarHistoricoCargas();
        } catch (erro) { mensagemCarga.textContent = erro.message; mensagemCarga.classList.add("erro"); botao.disabled = false; }
    });
    carregarOpcoesCarga();
    carregarHistoricoCargas();

    carregar();
})();
