// Array principal que contém todas as perguntas do questionário.
// Cada objeto dentro do array representa uma pergunta.
const PERGUNTAS = [
    {
        // Identificador único da pergunta.
        // É usado pelo JavaScript para guardar e recuperar a resposta.
        id: "objetivo",

        // Define o tipo de pergunta.
        // "single" = o usuário pode escolher apenas uma opção.
        tipo: "single",

        // Texto que será mostrado como título da pergunta.
        titulo: "Qual é seu principal objetivo?",

        // Define como as opções serão organizadas visualmente.
        layout: "lista",

        // Lista de opções disponíveis para o usuário.
        opcoes: [
            // "valor" é o valor que será salvo no objeto respostas.
            // "label" é o texto mostrado para o usuário.
            // "icone" é o emoji mostrado ao lado da opção.
            { valor: "ganhar_musculo", label: "Ganhar músculo", icone: "💪" },
            { valor: "perder_peso", label: "Perder peso & gordura", icone: "🔥" },
        ],
    },

    {
        id: "forma_atual",
        tipo: "single",
        titulo: "Escolha sua forma corporal atual",

        // Usa imagens em uma grade.
        layout: "grid-imagem",

        opcoes: [
            // "imagem" indica o caminho da imagem que será exibida.
            { valor: "magro", label: "1", imagem: "/images/1-atual.png" },
            { valor: "media", label: "2", imagem: "/images/2-atual.png" },
            { valor: "pesado", label: "3", imagem: "/images/3-atual.png" },
            { valor: "muito_pesado", label: "4", imagem: "/images/4-atual.png" },
        ],
    },

    {
        id: "forma_desejada",
        tipo: "single",
        titulo: "Escolha o corpo que você deseja",
        layout: "grid-imagem",

        opcoes: [
            { valor: "esbelto", label: "Esbelto", imagem: "/images/1-alvo.png" },
            { valor: "em_forma", label: "Em forma", imagem: "/images/2-alvo.png" },
            { valor: "musculoso", label: "Musculoso", imagem: "/images/3-alvo.png" },
            { valor: "fisiculturismo", label: "Fisiculturismo", imagem: "/images/4-alvo.png" },
        ],
    },

    {
        id: "frequencia_atual",
        tipo: "single",
        titulo: "Com que frequência você se exercita?",
        layout: "lista",

        opcoes: [
            { valor: "quase_todos_dias", label: "Quase todos os dias", icone: "🏆" },
            { valor: "varias_semana", label: "Várias vezes por semana", icone: "💪💪" },
            { valor: "varias_mes", label: "Várias vezes por mês", icone: "💪" },
            { valor: "no_passado", label: "No passado", icone: "😔" },
            { valor: "nunca", label: "Nunca", icone: "❌" },
        ],
    },

    {
        id: "variacao_peso",
        tipo: "single",
        titulo: "Como seu peso normalmente muda?",
        layout: "lista",

        opcoes: [
            { valor: "ganha_rapido_perde_lento", label: "Eu ganho peso rapidamente, mas perco lentamente", icone: "📈" },
            { valor: "ganha_perde_facil", label: "Eu ganho e perco peso facilmente", icone: "📊" },
            { valor: "dificil_ganhar", label: "Tenho dificuldade em ganhar peso ou músculo", icone: "📉" },
        ],
    },

    {
        id: "frequencia_desejada",
        tipo: "single",
        titulo: "Com que frequência você quer treinar?",
        layout: "lista-centralizada",

        opcoes: [
            { valor: "1", label: "1x por semana" },
            { valor: "2", label: "2x por semana" },
            { valor: "3", label: "3x por semana" },
            { valor: "4", label: "4x por semana" },
            { valor: "5", label: "5x por semana" },
            { valor: "6", label: "6x por semana" },
        ],
    },

    {
        id: "duracao_treino",
        tipo: "single",
        titulo: "Quanto tempo você quer que seus treinos durem?",
        layout: "lista-centralizada",

        opcoes: [
            { valor: "30", label: "30 min" },
            { valor: "45", label: "45 min" },
            { valor: "60", label: "60 min" },
            { valor: "75", label: "75 min" },
            { valor: "90", label: "90 min" },
            { valor: "120", label: "120 min" },
        ],
    },

    {
        id: "local_treino",
        tipo: "single",
        titulo: "Onde você quer treinar?",
        layout: "card",

        opcoes: [
            // "desc" é uma descrição adicional que aparece no card.
            { valor: "casa", label: "Em Casa", desc: "Treinamento com equipamentos mínimos ou sem equipamentos", icone: "🏠" },
            { valor: "academia", label: "Academia", desc: "Treinamento com máquinas, polias de cabo e pesos livres", icone: "🏋️" },
            { valor: "hibrido", label: "Híbrido", desc: "Combinação de treinos na academia e em casa", icone: "🔄" },
        ],
    },

    {
        id: "tipo_academia",
        tipo: "single",
        titulo: "Qual tipo de academia você treina?",
        layout: "card",

        // Função que determina se essa pergunta deve aparecer.
        // A pergunta só aparece se local_treino for "academia" ou "hibrido".
        mostrarSe: (r) => r.local_treino === "academia" || r.local_treino === "hibrido",

        opcoes: [
            { valor: "grande", label: "Academia Grande", desc: "Clube de fitness totalmente equipado como Smart Fit, BlueFit, Fitness Hut, Bodytech, Bio Ritmo, etc.", icone: "🏢" },
            { valor: "pequena", label: "Academia Pequena", desc: "Academia pública compacta com equipamentos limitados", icone: "🏘️" },
            { valor: "em_casa", label: "Academia em Casa", desc: "Barra, rack de agachamento, banco, halteres & barra de puxar", icone: "🏠" },
        ],
    },

    {
        id: "musculos_importantes",

        // "multi" significa que o usuário pode selecionar várias opções.
        tipo: "multi",

        titulo: "Quais músculos são mais importantes para você?",
        subtitulo: "Escolha todas as que se aplicam",
        layout: "lista",

        // Define uma opção que possui comportamento especial.
        // Nesse caso, "todos" não pode ficar selecionado junto com outras opções.
        exclusivo: "todos",

        opcoes: [
            { valor: "peitoral", label: "Peitoral", icone: "🫁" },
            { valor: "ombros", label: "Ombros", icone: "🤸" },
            { valor: "bracos", label: "Braços", icone: "💪" },
            { valor: "abdominais", label: "Abdominais", icone: "🎯" },
            { valor: "costas", label: "Costas", icone: "🔙" },
            { valor: "pernas", label: "Pernas", icone: "🦵" },
            { valor: "todos", label: "Todos igualmente importantes", icone: "⚖️" },
        ],
    },

    {
        id: "areas_sensiveis",
        tipo: "multi",
        titulo: "Você tem problemas em alguma das áreas a seguir?",
        subtitulo: "Escolha todas as que se aplicam",
        layout: "lista",

        // "nenhum" também é uma opção exclusiva.
        // Se "nenhum" for selecionado, as outras opções serão removidas.
        exclusivo: "nenhum",

        opcoes: [
            { valor: "nenhum", label: "Sem problemas", icone: "❌" },
            { valor: "costas", label: "Costas sensíveis", icone: "🔴" },
            { valor: "joelhos", label: "Joelhos sensíveis", icone: "🔴" },
            { valor: "ombros", label: "Ombros sensíveis", icone: "🔴" },
            { valor: "cotovelos", label: "Cotovelos sensíveis", icone: "🔴" },
        ],
    },

    {
        id: "dia_tipico",
        tipo: "single",
        titulo: "Como você descreveria seu dia típico?",
        layout: "lista",

        opcoes: [
            { valor: "sentado", label: "Passo a maior parte do dia sentado", icone: "💻" },
            { valor: "ativo_intervalos", label: "Sou ativo durante meus intervalos", icone: "🚶" },
            { valor: "em_pe", label: "Fico em pé o dia todo", icone: "🏃" },
        ],
    },

    {
        id: "nivel_energia",
        tipo: "single",
        titulo: "Como está seu nível de energia durante o dia?",
        layout: "lista",

        opcoes: [
            { valor: "baixo", label: "Baixo, sinto-me cansado ao longo do dia", icone: "🔋" },
            { valor: "queda_almoco", label: "Queda de energia após o almoço", icone: "🔋" },
            { valor: "arrastando", label: "Arrastando-se antes das refeições", icone: "🔋" },
            { valor: "alto_estavel", label: "Alto e estável", icone: "🔋" },
        ],
    },

    {
        id: "horas_sono",
        tipo: "single",
        titulo: "Quanto tempo você costuma dormir?",
        layout: "lista",

        opcoes: [
            { valor: "menos_6", label: "Menos de 6 horas", icone: "😴" },
            { valor: "6_7", label: "6-7 horas", icone: "😴" },
            { valor: "7_8", label: "7-8 horas", icone: "😴" },
            { valor: "mais_8", label: "Mais de 8 horas", icone: "😴" },
        ],
    },

    {
        id: "agua_dia",
        tipo: "single",
        titulo: "Quanto de água você bebe por dia?",
        layout: "lista",

        opcoes: [
            { valor: "menos_1", label: "Menos de 1 litro", desc: "Menos de 4 copos", icone: "💧" },
            { valor: "1_2", label: "1-2 litros", desc: "4-8 copos", icone: "💧💧" },
            { valor: "2_3", label: "2-3 litros", desc: "8-12 copos", icone: "💧💧💧" },
            { valor: "mais_3", label: "Mais de 3 litros", desc: "Mais de 12 copos", icone: "🐳" },
        ],
    },

    {
        id: "dados_pessoais",
        tipo: "dados",
        titulo: "Conte um pouco sobre você",
        subtitulo: "Esses dados ajudam a registrar seu ponto de partida.",
        layout: "dados",
    },

    {
        id: "peso_alvo",
        tipo: "numero",
        titulo: "Qual é o seu seu peso alvo?",

        // Layout especial para perguntas numéricas.
        layout: "numero",

        // Valores mínimo e máximo permitidos pelo slider.
        min: 30,
        max: 200,

        // Valor utilizado inicialmente pelo slider.
        padrao: 70,
    },
];

// ==========================================================================
// Motor do quiz — a partir daqui fica a lógica que faz o questionário funcionar.
// Para adicionar novas perguntas, normalmente basta alterar o array PERGUNTAS.
// ==========================================================================

// Objeto que armazena todas as respostas dadas pelo usuário.
// Exemplo: respostas.objetivo = "ganhar_musculo"
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
const elVoltar = document.getElementById("btnVoltar");

// Verifica se uma pergunta pode ser exibida.
// Se ela não tiver "mostrarSe", ela sempre será exibida.
// Caso tenha, executa a função e verifica se o resultado é true.
function elegivel(pergunta) {
    return !pergunta.mostrarSe || pergunta.mostrarSe(respostas);
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
    const pct = (posicaoAtual / total) * 100;

    // Altera a largura da barra de progresso.
    elBarra.style.width = pct + "%";

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
        const direita = pergunta.tipo === "multi"
            ? iconeCheck()
            : (op.icone ? `<span class="opcao-icone">${op.icone}</span>` : "");

        // Coloca o conteúdo dentro do botão.
        btn.innerHTML = `${miolo}${direita}`;

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
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "opcao-img";

        // Marca a opção como selecionada se ela já tiver sido escolhida.
        if (respostas[pergunta.id] === op.valor) btn.classList.add("selecionada");

        // Cria a imagem e a legenda da opção.
        btn.innerHTML = `
            <span class="img-wrap">
                <img src="${op.imagem}" alt="${op.label}" onerror="this.replaceWith(Object.assign(document.createElement('span'),{textContent:'🧍', style:'font-size:40px'}))">
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
    wrap.className = "passo-numero";

    // Recupera a resposta anterior, caso exista.
    // Se não existir, utiliza o valor padrão da pergunta.
    const valorAtual = respostas[pergunta.id]?.valor || pergunta.padrao;

    // Recupera a unidade anterior ou usa kg como padrão.
    unidadePeso = respostas[pergunta.id]?.unidade || "kg";

    // Cria os botões de unidade, valor, slider e botão continuar.
    wrap.innerHTML = `
        <div class="unidade-toggle">
            <button type="button" data-unidade="lbs" class="${unidadePeso === "lbs" ? "ativa" : ""}">lbs</button>
            <button type="button" data-unidade="kg" class="${unidadePeso === "kg" ? "ativa" : ""}">kg</button>
        </div>
        <div class="valor-peso"><span id="valorPesoNum">${valorAtual}</span><span>${unidadePeso}</span></div>
        <input type="range" class="slider-peso" id="sliderPeso"
            min="${pergunta.min}" max="${pergunta.max}" value="${valorAtual}">
        <button type="button" class="btn-continuar" id="btnContinuarNumero">Continuar</button>
    `;

    // Pega o slider criado acima.
    const inputRange = wrap.querySelector("#sliderPeso");

    // Pega o elemento que mostra o número do peso.
    const valorTexto = wrap.querySelector("#valorPesoNum");

    // Pega os botões de kg e lbs.
    const botoesUnidade = wrap.querySelectorAll(".unidade-toggle button");

    // Executado enquanto o usuário movimenta o slider.
    inputRange.addEventListener("input", () => {

        // Atualiza o número mostrado na tela.
        valorTexto.textContent = inputRange.value;
    });

    // Adiciona evento aos botões kg/lbs.
    botoesUnidade.forEach((b) => {
        b.addEventListener("click", () => {

            // Remove a classe "ativa" de todos os botões.
            botoesUnidade.forEach((x) => x.classList.remove("ativa"));

            // Marca o botão clicado como ativo.
            b.classList.add("ativa");

            // Salva a unidade escolhida.
            unidadePeso = b.dataset.unidade;

            // Atualiza a unidade mostrada ao lado do peso.
            wrap.querySelector(".valor-peso span:last-child").textContent = unidadePeso;
        });
    });

    // Evento do botão "Continuar".
    wrap.querySelector("#btnContinuarNumero").addEventListener("click", () => {

        // Salva o peso como número e também salva a unidade utilizada.
        respostas[pergunta.id] = {
            valor: Number(inputRange.value),
            unidade: unidadePeso
        };

        // Avança para a próxima pergunta.
        irParaProxima();
    });

    return wrap;
}

// Cria a etapa que reúne os dados pessoais que antes ficavam no cadastro.
function renderDados(pergunta) {
    const atual = respostas[pergunta.id] || {};
    const wrap = document.createElement("div");
    wrap.className = "dados-pessoais";
    wrap.innerHTML = `
        <label>Data de nascimento<input id="nascimento" type="date" value="${atual.dataNascimento || ""}" required></label>
        <label>Sexo<select id="sexo" required><option value="">Selecione</option><option value="Masculino">Masculino</option><option value="Feminino">Feminino</option><option value="Prefiro não Informar">Prefiro não informar</option></select></label>
        <label>Altura (m)<input id="altura" type="text" inputmode="decimal" autocomplete="off" placeholder="Ex.: 170 ou 1,70" value="${atual.altura || ""}" required></label>
        <label>Peso atual (kg)<input id="pesoInicial" type="number" min="25" max="400" step="0.1" value="${atual.pesoInicial || ""}" required></label>
        <button type="button" class="btn-continuar" id="btnContinuarDados">Continuar</button>`;
    wrap.querySelector("#sexo").value = atual.sexo || "";
    wrap.querySelector("#btnContinuarDados").addEventListener("click", () => {
        const nascimento = wrap.querySelector("#nascimento").value;
        const sexo = wrap.querySelector("#sexo").value;
        const alturaTexto = wrap.querySelector("#altura").value.trim().replace(",", ".");
        const pesoTexto = wrap.querySelector("#pesoInicial").value.trim();
        if (!nascimento || !sexo || !alturaTexto || !pesoTexto) {
            alert("Preencha data de nascimento, sexo, altura e peso atual para continuar.");
            return;
        }
        let altura = Number(alturaTexto);
        // Aceita centímetros (170) ou metros (1,70 / 1.70), convertendo para metros.
        if (Number.isFinite(altura) && altura >= 100 && altura <= 250) altura /= 100;
        const dados = {
            dataNascimento: nascimento,
            sexo,
            altura,
            pesoInicial: Number(pesoTexto)
        };
        if (!Number.isFinite(dados.altura) || dados.altura < 1 || dados.altura > 2.5
                || !Number.isFinite(dados.pesoInicial) || dados.pesoInicial < 25 || dados.pesoInicial > 400) {
            alert("Confira a altura (de 1 a 2,5 m) e o peso atual (de 25 a 400 kg).");
            return;
        }
        respostas[pergunta.id] = dados;
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

        // Pergunta numérica com slider.
        case "numero":
            corpo = renderNumero(pergunta);
            break;

        case "dados":
            corpo = renderDados(pergunta);
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
    const formulario = new URLSearchParams({
        objetivo: respostas.objetivo,
        formaAtual: respostas.forma_atual,
        formaDesejada: respostas.forma_desejada,
        frequencia: respostas.frequencia_desejada,
        duracao: respostas.duracao_treino,
        local: respostas.local_treino,
        pesoAlvo: String(peso?.valor || ""),
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
        window.location.href = "/areadoaluno.html";
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
