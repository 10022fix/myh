// Array principal que contém todas as perguntas do questionário.
// Cada objeto dentro do array representa uma pergunta.
const PERGUNTAS = [
    {
        id: "objetivo", tipo: "single", titulo: "Qual é o seu principal objetivo?", layout: "lista",
        opcoes: [
            { valor: "ganhar_musculo", label: "Ganhar músculo", icone: "💪" },
            { valor: "perder_peso", label: "Perder peso e gordura", icone: "🔥" },
            { valor: "saude", label: "Ter mais saúde e disposição", icone: "🌿" },
        ],
    },
    {
        id: "frequencia_atual", tipo: "single", titulo: "Como é a sua rotina de treino hoje?", layout: "lista",
        opcoes: [
            { valor: "nunca", label: "Nunca treinei", icone: "🌱" },
            { valor: "ocasional", label: "Treino de vez em quando", icone: "🚶" },
            { valor: "semanal", label: "Treino toda semana", icone: "🏆" },
        ],
    },
    {
        id: "frequencia_desejada", tipo: "single", titulo: "Quantos dias por semana quer treinar?",
        subtitulo: "Seja realista: é melhor começar leve e manter o ritmo.", layout: "lista-grade",
        opcoes: ["2", "3", "4", "5"].map((valor) => ({ valor, label: valor })),
    },
    {
        id: "duracao_treino", tipo: "single", titulo: "Quanto tempo você tem por treino?", layout: "lista-grade",
        opcoes: ["30", "45", "60"].map((valor) => ({ valor, label: `${valor} min` })),
    },
    {
        id: "local_treino", tipo: "single", titulo: "Onde você vai treinar?", layout: "lista",
        opcoes: [
            { valor: "casa", label: "Em casa", desc: "Equipamentos mínimos ou nenhum", icone: "🏠" },
            { valor: "academia", label: "Academia", desc: "Máquinas, polias e pesos livres", icone: "🏋️" },
            { valor: "hibrido", label: "Híbrido", desc: "Um pouco de cada", icone: "🔄" },
        ],
    },
    {
        id: "areas_sensiveis", tipo: "multi", titulo: "Alguma região precisa de cuidado?",
        subtitulo: "Escolha todas que se aplicam.", layout: "lista", exclusivo: "nenhum",
        opcoes: [
            { valor: "nenhum", label: "Nenhuma", icone: "✅" },
            { valor: "costas", label: "Costas", icone: "🔴" },
            { valor: "joelhos", label: "Joelhos", icone: "🔴" },
            { valor: "ombros", label: "Ombros", icone: "🔴" },
        ],
    },
    {
        id: "peso_alvo", tipo: "numero", titulo: "Seu peso atual e a sua meta",
        subtitulo: "Você pode ajustar isso depois em “Metas”.", layout: "numero", min: 1, max: 400, padrao: 60,
    },
    {
        id: "altura", tipo: "altura", titulo: "Qual é sua altura?",
        subtitulo: "Arraste o controle e veja a altura representada pelo boneco.", layout: "altura",
    },
];

// ==========================================================================
// Motor do quiz — a partir daqui fica a lógica que faz o questionário funcionar.
// Para adicionar novas perguntas, normalmente basta alterar o array PERGUNTAS.
// ==========================================================================

// Objeto que armazena todas as respostas dadas pelo usuário.
// Exemplo: respostas.objetivo = "perder_peso"
const respostas = {};

// Unidade de peso utilizada atualmente.
let unidadePeso = "kg";

// Guarda o histórico das perguntas que realmente foram exibidas.
// Isso permite que o botão "Voltar" funcione corretamente,
// inclusive quando existem perguntas condicionais.
let historico = [PERGUNTAS[0].id];

// Pega elementos HTML da página pelo ID.
const elContainer = document.getElementById("perguntaContainer");
const elBarra = document.getElementById("progressoBarra");
const elProgressoTexto = document.getElementById("progressoTexto");
const elVoltar = document.getElementById("btnVoltar");

// Verifica se uma pergunta pode ser exibida.
// Se ela não tiver "mostrarSe", ela sempre será exibida.
// Caso tenha, executa a função e verifica se o resultado é true.
function elegivel(pergunta) {
    return !pergunta.mostrarSe || pergunta.mostrarSe(respostas);
}

// Limita o peso-alvo conforme a meta. Em saúde, mantém a escolha perto do peso atual.
function calcularLimitesPesoAlvo(objetivo, pesoAtualKg, minimoGeral = 1, maximoGeral = 400) {
    const peso = Number(pesoAtualKg);
    if (!Number.isFinite(peso)) return { min: minimoGeral, max: maximoGeral };
    if (objetivo === "ganhar_musculo") {
        return { min: Math.ceil(peso * 10) / 10, max: maximoGeral };
    }
    if (objetivo === "perder_peso") {
        return { min: minimoGeral, max: Math.max(minimoGeral, Math.floor((peso - 0.1) * 10) / 10) };
    }
    if (objetivo === "saude") {
        return {
            min: Math.max(25, Math.round(peso * 0.9 * 10) / 10),
            max: Math.min(maximoGeral, Math.round(peso * 1.1 * 10) / 10)
        };
    }
    return { min: minimoGeral, max: maximoGeral };
}

// Retorna somente as perguntas que podem aparecer para o usuário.
// Perguntas condicionais que não atendem aos requisitos são ignoradas.
function perguntasVisiveis() {
    return PERGUNTAS.filter(elegivel);
}

// Procura uma pergunta pelo seu ID.
// find() retorna o primeiro elemento que atender à condição.
function perguntaPorId(id) {
    return PERGUNTAS.find((p) => p.id === id);
}

// Descobre qual será a próxima pergunta.
function proximoId(idAtual) {
    // Encontra a posição da pergunta atual dentro do array.
    const pos = PERGUNTAS.findIndex((p) => p.id === idAtual);

    // Começa na pergunta seguinte e percorre as próximas.
    for (let i = pos + 1; i < PERGUNTAS.length; i++) {

        // Retorna a primeira pergunta que estiver liberada.
        if (elegivel(PERGUNTAS[i])) return PERGUNTAS[i].id;
    }

    // Se não encontrou nenhuma, significa que o questionário acabou.
    return null;
}

// Atualiza a barra de progresso e o botão "Voltar".
function atualizarProgresso() {
    // Quantidade total de perguntas que podem ser exibidas.
    const total = perguntasVisiveis().length;

    // Índice da pergunta atual no histórico.
    const posicaoAtual = historico.length - 1;

    // Calcula a porcentagem de progresso.
    const pct = ((posicaoAtual + 1) / total) * 100;

    // Altera a largura da barra de progresso.
    elBarra.style.width = pct + "%";
    elBarra.parentElement.setAttribute("aria-valuenow", String(posicaoAtual + 1));
    elBarra.parentElement.setAttribute("aria-valuemax", String(total));
    elProgressoTexto.textContent = `Passo ${posicaoAtual + 1} de ${total}`;

    // Desativa "Voltar" quando estamos na primeira pergunta.
    elVoltar.disabled = historico.length <= 1;
}

// Avança para a próxima pergunta.
function irParaProxima() {
    // Pega o ID da pergunta atual.
    const idAtual = historico[historico.length - 1];

    // Descobre qual pergunta vem depois dela.
    const proximo = proximoId(idAtual);

    if (proximo) {
        // Adiciona a próxima pergunta ao histórico.
        historico.push(proximo);

        // Desenha a nova pergunta na tela.
        render();
    } else {
        // Se não existe próxima pergunta, mostra a tela final.
        renderFinal();
    }
}

// Volta para a pergunta anterior.
function irParaAnterior() {
    // Só permite voltar se existir alguma pergunta anterior.
    if (historico.length > 1) {

        // Remove a pergunta atual do histórico.
        historico.pop();

        // Mostra novamente a pergunta anterior.
        render();
    }
}

// Quando o botão "Voltar" for clicado, executa irParaAnterior().
elVoltar.addEventListener("click", irParaAnterior);

// Cria o ícone de check usado nas perguntas de múltipla escolha.
// A função retorna HTML em formato de string.
function iconeCheck() {
    return `<span class="opcao-check"><svg viewBox="0 0 24 24"><polyline points="20 6 9 17 4 12"></polyline></svg></span>`;
}

// ---- Renderização por tipo de layout ----

// Cria visualmente uma lista de opções.
function renderLista(pergunta, centralizada) {
    // Cria uma div que irá conter todas as opções.
    const wrap = document.createElement("div");
    wrap.className = "opcoes";
    if (centralizada) wrap.classList.add("opcoes-centralizadas");

    // Percorre cada opção da pergunta.
    pergunta.opcoes.forEach((op) => {

        // Cria um botão para a opção.
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "opcao";

        // Se a lista deve ser centralizada, altera o alinhamento do botão.
        if (centralizada) btn.style.justifyContent = "center";

        // Verifica se essa opção já está selecionada.
        const selecionada = pergunta.tipo === "multi"
            ? (respostas[pergunta.id] || []).includes(op.valor)
            : respostas[pergunta.id] === op.valor;

        // Se estiver selecionada, adiciona a classe CSS "selecionada".
        if (selecionada) btn.classList.add("selecionada");

        // Variável que armazenará o conteúdo interno do botão.
        let miolo = "";

        // Se a pergunta ou a opção possuir descrição,
        // cria uma estrutura com título e descrição.
        if (pergunta.desc || op.desc) {
            miolo = `
                <div class="opcao-texto">
                    <span class="opcao-label">${op.label}</span>
                    ${op.desc ? `<span class="opcao-desc">${op.desc}</span>` : ""}
                </div>`;
        } else {
            // Caso não tenha descrição, mostra somente o texto.
            miolo = `<span class="opcao-label">${op.label}</span>`;
        }

        // Em perguntas múltiplas mostra um check.
        // Em perguntas simples mostra o ícone definido na opção.
        const icone = op.icone ? `<span class="opcao-icone">${op.icone}</span>` : "";
        const check = pergunta.tipo === "multi" ? iconeCheck() : "";

        // Coloca o conteúdo dentro do botão.
        btn.innerHTML = `${icone}${miolo}${check}`;

        // Quando o botão for clicado, registra a opção escolhida.
        btn.addEventListener("click", () => selecionarOpcao(pergunta, op.valor));

        // Adiciona o botão ao container.
        wrap.appendChild(btn);
    });

    // Retorna o container pronto.
    return wrap;
}

// Cria o layout de perguntas que utilizam imagens.
function renderGridImagem(pergunta) {
    const wrap = document.createElement("div");
    wrap.className = "opcoes-grid";

    // Percorre todas as opções.
    pergunta.opcoes.forEach((op) => {
        const indice = pergunta.opcoes.indexOf(op) + 1;
        const usaImagemFeminina = respostas.sexo === "Feminino"
            && (pergunta.id === "forma_atual" || pergunta.id === "forma_desejada");
        const prefixoImagem = pergunta.id === "forma_desejada"
            ? "corpo-desejado-feminino"
            : "corpo-feminino";
        const imagem = usaImagemFeminina
            ? `/images/${prefixoImagem}-${indice}.png`
            : op.imagem;

        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "opcao-img";

        // Marca a opção como selecionada se ela já tiver sido escolhida.
        if (respostas[pergunta.id] === op.valor) btn.classList.add("selecionada");

        // Cria a imagem e a legenda da opção.
        btn.innerHTML = `
            <span class="img-wrap">
                <img src="${imagem}" alt="${op.label}" onerror="this.replaceWith(Object.assign(document.createElement('span'),{textContent:'🧍', style:'font-size:40px'}))">
            </span>
            <span class="img-legenda">${op.label}</span>
        `;

        // Seleciona a opção quando o usuário clicar.
        btn.addEventListener("click", () => selecionarOpcao(pergunta, op.valor));

        wrap.appendChild(btn);
    });

    return wrap;
}

// Cria o layout baseado em cards.
function renderCard(pergunta) {
    const wrap = document.createElement("div");
    wrap.className = "opcoes-card";

    pergunta.opcoes.forEach((op) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "opcao-card";

        // Marca o card como selecionado caso essa seja a resposta atual.
        if (respostas[pergunta.id] === op.valor) btn.classList.add("selecionada");

        // Monta o conteúdo do card.
        btn.innerHTML = `
            <div class="opcao-card-texto">
                <h3>${op.label}</h3>
                <p>${op.desc}</p>
            </div>
            <span class="opcao-card-img">${op.icone || ""}</span>
        `;

        // Registra a escolha do usuário.
        btn.addEventListener("click", () => selecionarOpcao(pergunta, op.valor));

        wrap.appendChild(btn);
    });

    return wrap;
}

// Cria o layout utilizado para perguntas numéricas.
function renderNumero(pergunta) {
    const wrap = document.createElement("div");
    wrap.className = "passo-numero passo-peso-completo";

    const anterior = respostas[pergunta.id] || {};
    const dadosAtuais = respostas.dados_pessoais || {};
    const pesoInicial = Number(dadosAtuais.pesoInicial) || 70;
    unidadePeso = anterior.unidade || "kg";
    const paraUnidade = (kg, unidade) => unidade === "lbs" ? kg * 2.2046226218 : kg;
    const paraKg = (valor, unidade) => unidade === "lbs" ? valor / 2.2046226218 : valor;
    const arredondar = (valor) => Math.round(valor * 10) / 10;
    const limiteMinimoNaUnidade = (kg) => unidadePeso === "lbs"
        ? Math.ceil(paraUnidade(kg, unidadePeso) * 10) / 10
        : arredondar(kg);
    const limiteMaximoNaUnidade = (kg) => unidadePeso === "lbs"
        ? Math.floor(paraUnidade(kg, unidadePeso) * 10) / 10
        : arredondar(kg);
    const objetivo = respostas.objetivo;
    const ganharMusculo = objetivo === "ganhar_musculo";
    const perderPeso = objetivo === "perder_peso";
    const limitesIniciaisKg = calcularLimitesPesoAlvo(objetivo, pesoInicial, pergunta.min, pergunta.max);
    const minimo = limiteMinimoNaUnidade(limitesIniciaisKg.min);
    const maximo = limiteMaximoNaUnidade(limitesIniciaisKg.max);
    const padraoKg = ganharMusculo ? Math.min(400, pesoInicial + 5) : perderPeso ? Math.max(1, pesoInicial - 5) : pesoInicial;
    const valorSalvo = anterior.valor != null ? arredondar(paraUnidade(paraKg(Number(anterior.valor), anterior.unidade || "kg"), unidadePeso)) : arredondar(paraUnidade(padraoKg, unidadePeso));
    const valorInicial = Math.min(maximo, Math.max(minimo, valorSalvo));

    wrap.innerHTML = `
        <label class="peso-atual-campo" for="pesoAtualQuiz">Peso atual (kg)
            <input id="pesoAtualQuiz" type="number" min="25" max="400" step="0.1" inputmode="decimal" value="${pesoInicial}" required>
        </label>
        <p class="peso-meta-legenda">Peso-alvo</p>
        <div class="unidade-toggle">
            <button type="button" data-unidade="lbs" class="${unidadePeso === "lbs" ? "ativa" : ""}">lbs</button>
            <button type="button" data-unidade="kg" class="${unidadePeso === "kg" ? "ativa" : ""}">kg</button>
        </div>
        <div class="valor-peso"><span id="valorPesoNum">${valorInicial}</span><span>${unidadePeso}</span></div>
        <input type="range" class="slider-peso" id="sliderPeso" min="${minimo}" max="${maximo}" step="0.1" value="${valorInicial}" aria-label="Peso-alvo, de acordo com seu objetivo">
        <p class="aviso-peso" id="avisoPeso">${ganharMusculo
            ? `Para ganhar músculo, escolha uma meta igual ou maior que seu peso atual (${pesoInicial} kg).`
            : perderPeso
                ? `Para emagrecer, a meta precisa ser menor que seu peso atual (${pesoInicial} kg).`
                : `Para saúde e disposição, escolha uma meta próxima do seu peso atual (faixa de até 10%: ${limitesIniciaisKg.min}–${limitesIniciaisKg.max} kg).`}</p>
        <p class="erro-peso" id="erroPeso" role="alert"></p>
        <button type="button" class="btn-continuar" id="btnContinuarNumero">Continuar</button>
    `;

    const inputRange = wrap.querySelector("#sliderPeso");
    const valorTexto = wrap.querySelector("#valorPesoNum");
    const inputPesoAtual = wrap.querySelector("#pesoAtualQuiz");
    const botoesUnidade = wrap.querySelectorAll(".unidade-toggle button");
    const atualizarLimites = (manterPesoAlvo = true) => {
        const atualKg = Number(inputPesoAtual.value);
        if (!Number.isFinite(atualKg) || atualKg < 25 || atualKg > 400) return;
        const limitesKg = calcularLimitesPesoAlvo(objetivo, atualKg, pergunta.min, pergunta.max);
        const novoMinimo = limiteMinimoNaUnidade(limitesKg.min);
        const novoMaximo = limiteMaximoNaUnidade(limitesKg.max);
        const metaAnteriorKg = paraKg(Number(inputRange.value), unidadePeso);
        inputRange.min = String(novoMinimo);
        inputRange.max = String(novoMaximo);
        const sugeridoKg = ganharMusculo ? atualKg + 5 : perderPeso ? atualKg - 5 : atualKg;
        const novoValor = manterPesoAlvo ? paraUnidade(metaAnteriorKg, unidadePeso) : paraUnidade(sugeridoKg, unidadePeso);
        inputRange.value = String(Math.min(novoMaximo, Math.max(novoMinimo, arredondar(novoValor))));
        valorTexto.textContent = inputRange.value;
        wrap.querySelector("#avisoPeso").textContent = ganharMusculo
            ? `Para ganhar músculo, escolha uma meta igual ou maior que seu peso atual (${atualKg} kg).`
            : perderPeso
                ? `Para emagrecer, a meta precisa ser menor que seu peso atual (${atualKg} kg).`
                : `Para saúde e disposição, escolha uma meta próxima do seu peso atual (faixa de até 10%: ${limitesKg.min}–${limitesKg.max} kg).`;
    };

    inputRange.addEventListener("input", () => { valorTexto.textContent = inputRange.value; });
    inputPesoAtual.addEventListener("input", () => atualizarLimites(true));
    botoesUnidade.forEach((botao) => botao.addEventListener("click", () => {
        const pesoMetaKg = paraKg(Number(inputRange.value), unidadePeso);
        unidadePeso = botao.dataset.unidade;
        botoesUnidade.forEach((item) => item.classList.toggle("ativa", item === botao));
        atualizarLimites(true);
        inputRange.value = String(Math.min(Number(inputRange.max), Math.max(Number(inputRange.min), arredondar(paraUnidade(pesoMetaKg, unidadePeso)))));
        valorTexto.textContent = inputRange.value;
        wrap.querySelector(".valor-peso span:last-child").textContent = unidadePeso;
    }));

    wrap.querySelector("#btnContinuarNumero").addEventListener("click", () => {
        const atualKg = Number(inputPesoAtual.value);
        const metaKg = paraKg(Number(inputRange.value), unidadePeso);
        if (!Number.isFinite(atualKg) || atualKg < 25 || atualKg > 400) {
            wrap.querySelector("#erroPeso").textContent = "Informe seu peso atual entre 25 e 400 kg.";
            inputPesoAtual.focus();
            return;
        }
        const limitesMetaKg = calcularLimitesPesoAlvo(objetivo, atualKg, pergunta.min, pergunta.max);
        if (metaKg < limitesMetaKg.min || metaKg > limitesMetaKg.max) {
            wrap.querySelector("#erroPeso").textContent = perderPeso
                ? "Para perder peso, a meta precisa ser menor que o peso atual."
                : ganharMusculo
                    ? "Para ganhar músculo, a meta precisa ser igual ou maior que o peso atual."
                    : "Para saúde e disposição, escolha uma meta dentro da faixa indicada.";
            return;
        }
        respostas.dados_pessoais = { ...(respostas.dados_pessoais || {}), pesoInicial: atualKg };
        respostas.peso_alvo = { valor: Number(inputRange.value), unidade: unidadePeso };
        irParaProxima();
    });
    return wrap;
}

// Cria a etapa final de altura e coleta os dados de perfil que continuam necessários ao gerador de treinos.
function renderAltura(pergunta) {
    const atual = respostas.dados_pessoais || {};
    const alturaCmInicial = Math.min(220, Math.max(130, Math.round((Number(atual.altura) || 1.7) * 100)));
    const sexoInicial = respostas.sexo || atual.sexo || "";
    const nascimentoInicial = atual.dataNascimento || "";
    const pesoInicial = Number(atual.pesoInicial) || 70;
    const escalaInicial = alturaCmInicial / 170;
    const deslocamentoInicial = 260 - 260 * escalaInicial;
    const hoje = new Date().toISOString().slice(0, 10);
    const wrap = document.createElement("div");
    wrap.className = "altura-interativa";
    wrap.innerHTML = `
        <div class="altura-visual" role="img" aria-label="Representação ajustável da altura">
            <svg viewBox="0 0 120 280" aria-hidden="true" focusable="false">
                <line x1="14" y1="266" x2="106" y2="266" class="altura-chao" />
                <g id="personagemAltura" transform="translate(0 ${deslocamentoInicial}) scale(1 ${escalaInicial})">
                    <circle cx="60" cy="34" r="16" class="altura-figura" />
                    <path d="M60 54 L60 150 M60 78 L28 125 M60 78 L92 125 M60 150 L34 258 M60 150 L86 258" class="altura-figura-traco" />
                </g>
            </svg>
        </div>
        <output class="altura-valor" id="alturaValor">${(alturaCmInicial / 100).toFixed(2).replace(".", ",")} m</output>
        <label class="altura-slider-label" for="alturaSlider">Altura: <span id="alturaCmTexto">${alturaCmInicial} cm</span></label>
        <input id="alturaSlider" class="altura-slider" type="range" min="130" max="220" step="1" value="${alturaCmInicial}" aria-label="Ajustar altura em centímetros">
        <div class="altura-dados-adicionais">
            <label for="nascimentoAltura">Data de nascimento
                <input id="nascimentoAltura" type="date" max="${hoje}" value="${nascimentoInicial}" required>
            </label>
            <fieldset class="sexo-altura">
                <legend>Sexo</legend>
                <div class="sexo-altura-opcoes" role="radiogroup" aria-label="Sexo para personalizar a identificação do treino">
                    <button type="button" class="sexo-altura-opcao ${sexoInicial === "Masculino" ? "selecionada" : ""}" data-sexo="Masculino" role="radio" aria-checked="${sexoInicial === "Masculino"}">Masculino</button>
                    <button type="button" class="sexo-altura-opcao ${sexoInicial === "Feminino" ? "selecionada" : ""}" data-sexo="Feminino" role="radio" aria-checked="${sexoInicial === "Feminino"}">Feminino</button>
                </div>
            </fieldset>
        </div>
        <p class="erro-altura" id="erroAltura" role="alert"></p>
        <button type="button" class="btn-continuar" id="btnContinuarAltura">Continuar</button>`;

    const slider = wrap.querySelector("#alturaSlider");
    const grupoPersonagem = wrap.querySelector("#personagemAltura");
    const atualizarAltura = () => {
        const centimetros = Number(slider.value);
        const escala = centimetros / 170;
        grupoPersonagem.setAttribute("transform", `translate(0 ${260 - 260 * escala}) scale(1 ${escala})`);
        wrap.querySelector("#alturaValor").textContent = `${(centimetros / 100).toFixed(2).replace(".", ",")} m`;
        wrap.querySelector("#alturaCmTexto").textContent = `${centimetros} cm`;
    };
    slider.addEventListener("input", atualizarAltura);
    wrap.querySelectorAll("[data-sexo]").forEach((botao) => botao.addEventListener("click", () => {
        respostas.sexo = botao.dataset.sexo;
        wrap.querySelectorAll("[data-sexo]").forEach((opcao) => {
            const selecionada = opcao === botao;
            opcao.classList.toggle("selecionada", selecionada);
            opcao.setAttribute("aria-checked", String(selecionada));
        });
    }));
    wrap.querySelector("#btnContinuarAltura").addEventListener("click", () => {
        const nascimento = wrap.querySelector("#nascimentoAltura").value;
        const sexo = respostas.sexo || sexoInicial;
        const altura = Number(slider.value) / 100;
        if (!nascimento || !sexo) {
            wrap.querySelector("#erroAltura").textContent = "Informe sua data de nascimento e selecione o sexo para personalizarmos o treino.";
            return;
        }
        const dataNascimento = new Date(`${nascimento}T00:00:00`);
        const idade = (Date.now() - dataNascimento.getTime()) / 31557600000;
        if (!Number.isFinite(dataNascimento.getTime()) || idade < 0 || idade > 120) {
            wrap.querySelector("#erroAltura").textContent = "Confira sua data de nascimento.";
            return;
        }
        respostas.sexo = sexo;
        respostas.dados_pessoais = { ...atual, dataNascimento: nascimento, sexo, altura, pesoInicial };
        irParaProxima();
    });
    return wrap;
}

// ---- Seleção ----

// Função responsável por registrar a opção escolhida pelo usuário.
function selecionarOpcao(pergunta, valor) {

    // Verifica se a pergunta permite somente uma opção.
    if (pergunta.tipo === "single") {

        // Salva diretamente o valor escolhido.
        respostas[pergunta.id] = valor;
        if (pergunta.id === "sexo") {
            respostas.dados_pessoais = {
                ...(respostas.dados_pessoais || {}),
                sexo: valor,
            };
        }

        // Atualiza a tela para mostrar visualmente a seleção.
        render();

        // Aguarda 250 milissegundos antes de avançar.
        // Isso permite que o usuário veja a opção selecionada.
        setTimeout(irParaProxima, 250);

        return;
    }

    // A partir daqui estamos tratando perguntas de múltipla escolha.

    // Recupera as opções já selecionadas.
    // Se ainda não existir nenhuma, começa com um array vazio.
    const atual = respostas[pergunta.id] || [];

    let novo;

    // Verifica se o usuário clicou na opção exclusiva.
    // Exemplo: "Todos" ou "Sem problemas".
    if (pergunta.exclusivo && valor === pergunta.exclusivo) {

        // Se a opção já estava selecionada, remove ela.
        // Caso contrário, seleciona somente ela.
        novo = atual.includes(valor) ? [] : [valor];

    } else {

        // Se a opção já estiver selecionada, remove ela.
        // Caso contrário, adiciona ela ao array.
        novo = atual.includes(valor)
            ? atual.filter((v) => v !== valor)
            : [...atual, valor];

        // Remove a opção exclusiva caso ela estivesse selecionada.
        if (pergunta.exclusivo) {
            novo = novo.filter((v) => v !== pergunta.exclusivo);
        }
    }

    // Salva o novo conjunto de respostas.
    respostas[pergunta.id] = novo;

    // Atualiza a interface para refletir a seleção.
    render();
}

// ---- Render principal ----

// Função responsável por desenhar a pergunta atual na página.
function render() {

    // Descobre qual é o ID da pergunta atual.
    const idAtual = historico[historico.length - 1];

    // Busca o objeto completo da pergunta.
    const pergunta = perguntaPorId(idAtual);

    // Cria o container principal da pergunta.
    const bloco = document.createElement("div");
    bloco.className = "pergunta";

    // Cria o título.
    const titulo = document.createElement("h1");
    titulo.className = "pergunta-titulo";
    titulo.textContent = pergunta.titulo;

    // Coloca o título dentro do bloco.
    bloco.appendChild(titulo);

    // Se existir subtítulo, cria e adiciona ele.
    if (pergunta.subtitulo) {
        const sub = document.createElement("p");
        sub.className = "pergunta-subtitulo";
        sub.textContent = pergunta.subtitulo;
        bloco.appendChild(sub);
    }

    // Variável que receberá o conteúdo visual da pergunta.
    let corpo;

    // Escolhe qual função de renderização deve ser utilizada
    // dependendo do layout definido na pergunta.
    switch (pergunta.layout) {

        // Pergunta com imagens em grade.
        case "grid-imagem":
            corpo = renderGridImagem(pergunta);
            break;

        // Pergunta usando cards.
        case "card":
            corpo = renderCard(pergunta);
            break;

        // Lista com opções centralizadas.
        case "lista-centralizada":
            corpo = renderLista(pergunta, true);
            break;

        case "lista-grade":
            corpo = renderLista(pergunta, true);
            corpo.classList.add("opcoes-grade");
            break;

        // Pergunta numérica com slider.
        case "numero":
            corpo = renderNumero(pergunta);
            break;

        case "altura":
            corpo = renderAltura(pergunta);
            break;

        // Caso não tenha nenhum layout especial,
        // utiliza uma lista normal.
        default:
            corpo = renderLista(pergunta, false);
    }

    // Adiciona o conteúdo da pergunta ao bloco.
    bloco.appendChild(corpo);

    // Perguntas de múltipla escolha precisam de um botão "Continuar",
    // pois clicar em uma opção não deve avançar automaticamente.
    if (pergunta.tipo === "multi") {

        // Cria o botão.
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "btn-continuar";
        btn.textContent = "Continuar";

        // Verifica se existe pelo menos uma opção selecionada.
        const temSelecao = (respostas[pergunta.id] || []).length > 0;

        // Desativa o botão caso nenhuma opção tenha sido escolhida.
        btn.disabled = !temSelecao;

        // Quando clicar, vai para a próxima pergunta.
        btn.addEventListener("click", irParaProxima);

        // Adiciona o botão à pergunta.
        bloco.appendChild(btn);
    }

    // Apaga o conteúdo anterior do container.
    elContainer.innerHTML = "";

    // Coloca a nova pergunta na página.
    elContainer.appendChild(bloco);

    // Atualiza a barra de progresso.
    atualizarProgresso();
}

// Mostra a tela final quando todas as perguntas terminarem.
function renderFinal() {

    // Completa a barra de progresso.
    elBarra.style.width = "100%";
    elBarra.parentElement.setAttribute("aria-valuenow", elBarra.parentElement.getAttribute("aria-valuemax") || "100");
    elProgressoTexto.textContent = "Questionário concluído";

    // Desativa o botão voltar.
    elVoltar.disabled = true;

    // Substitui o conteúdo pelo resultado final.
    elContainer.innerHTML = `
        <div class="pergunta final">
            <div class="final-icone">✅</div>
            <h2>Tudo pronto!</h2>
            <p>Usamos suas respostas para montar o treino ideal pra você.</p>
            <button type="button" class="btn-continuar" id="btnFinalizar">Ver meu treino</button>
        </div>
    `;

    document.getElementById("btnFinalizar").addEventListener("click", () => {
        finalizarQuestionario();
    });
}

// Envia as respostas ao servidor. O banco, e nao o navegador, passa a ser a fonte dos dados do perfil.
async function finalizarQuestionario() {
    const botao = document.getElementById("btnFinalizar");
    botao.disabled = true;
    botao.textContent = "Preparando seu treino...";
    const peso = respostas.peso_alvo;
    const dadosPessoais = respostas.dados_pessoais || {};
    const pesoAlvoKg = peso?.unidade === "lbs" ? Number(peso.valor) / 2.2046226218 : Number(peso?.valor);
    const formulario = new URLSearchParams({
        objetivo: respostas.objetivo,
        formaAtual: "nao_informado",
        formaDesejada: "nao_informado",
        frequencia: respostas.frequencia_desejada,
        duracao: respostas.duracao_treino,
        local: respostas.local_treino,
        pesoAlvo: String(pesoAlvoKg || ""),
        dataNascimento: dadosPessoais.dataNascimento || "",
        sexo: dadosPessoais.sexo || "",
        altura: String(dadosPessoais.altura || ""),
        pesoInicial: String(dadosPessoais.pesoInicial || ""),
        respostas: JSON.stringify(respostas)
    });
    try {
        const resposta = await fetch("/api/questionario/finalizar", { method: "POST", body: formulario });
        const dados = await resposta.json();
        if (!resposta.ok) throw new Error(dados.mensagem || "Não foi possível concluir.");
        window.location.href = "/meu-treino.html";
    } catch (erro) {
        botao.disabled = false;
        botao.textContent = "Tentar novamente";
        alert(erro.message);
    }
}

// ---- Início ----

// Confirma que existe uma sessao antes de permitir o preenchimento do perfil.
(async () => {
    const resposta = await fetch("/api/sessao");
    if (!resposta.ok) {
        window.location.href = "/login.html";
        return;
    }
    render();
})();
