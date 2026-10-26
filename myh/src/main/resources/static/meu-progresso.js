(() => {
    const $ = id => document.getElementById(id);
    const form = $("formulario");
    const peso = $("pesoRegistro");
    const data = $("dataRegistro");
    const svgNS = "http://www.w3.org/2000/svg";

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
            return `<div class="history-entry"><div><strong>${formatarPeso(item.peso)}</strong>${medidas ? `<small>${medidas}</small>` : ""}</div><span>${formatarData(item.data)}</span></div>`;
        }).join("");
    }

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
    data.max = dataLocalISO();
            await carregar();
        } catch (erro) {
            $("mensagem").textContent = erro.message || "Falha de conexão ao salvar o registro.";
            $("mensagem").classList.add("erro");
        } finally {
            botao.disabled = false;
        }
    });

    carregar();
})();