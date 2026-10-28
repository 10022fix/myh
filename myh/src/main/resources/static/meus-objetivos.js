const $ = id => document.getElementById(id);
let metaEmEdicaoId = null;
let possuiMetaAtiva = false;
let pesoAtualMeta = null;

function escaparHtml(valor) {
    return String(valor ?? '').replace(/[&<>"']/g, caractere => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[caractere]);
}

function formatarPeso(valor) {
    if (valor == null || valor === '') return 'Peso não definido';
    const peso = Number(valor);
    return Number.isFinite(peso)
        ? `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 }).format(peso)} kg`
        : 'Peso não definido';
}

function chaveObjetivoMeta(valor) {
    const chave = String(valor ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase().replaceAll('_', ' ');
    if (['saude', 'saude e disposicao', 'manter me saudavel'].includes(chave)) return 'saude';
    if (['manter meu peso', 'manter peso'].includes(chave)) return 'manter_peso';
    if (['definicao'].includes(chave)) return 'definicao';
    if (['ganhar massa', 'ganhar musculo'].includes(chave)) return 'ganhar_musculo';
    if (['emagrecer', 'perder peso'].includes(chave)) return 'perder_peso';
    return chave;
}

function objetivoFormulario(valor) {
    const chave = chaveObjetivoMeta(valor);
    return ({ saude: 'Saúde e disposição', manter_peso: 'Manter peso', definicao: 'Definicao',
        ganhar_musculo: 'Ganhar Massa', perder_peso: 'Emagrecer', condicionamento: 'Condicionamento' })[chave] || valor || '';
}

function faixaPesoMeta(objetivo, pesoAtual) {
    const peso = Number(pesoAtual), chave = chaveObjetivoMeta(objetivo);
    if (!Number.isFinite(peso)) return { min: 25, max: 400 };
    if (chave === 'ganhar_musculo') return { min: Math.ceil(peso * 10) / 10, max: Math.min(400, Math.round(peso * 1.2 * 10) / 10) };
    if (chave === 'perder_peso') return { min: Math.max(25, Math.round(peso * 0.8 * 10) / 10), max: Math.max(25, Math.floor((peso - 0.1) * 10) / 10) };
    if (chave === 'manter_peso') return { min: Math.max(25, Math.round(peso * 0.95 * 10) / 10), max: Math.min(400, Math.round(peso * 1.05 * 10) / 10) };
    return { min: Math.max(25, Math.round(peso * 0.9 * 10) / 10), max: Math.min(400, Math.round(peso * 1.1 * 10) / 10) };
}

function atualizarLimitesPesoMeta() {
    const input = $('pesoMeta'), objetivo = $('objetivo').value, ajuda = $('ajudaPesoMeta');
    const faixa = faixaPesoMeta(objetivo, pesoAtualMeta);
    input.min = String(faixa.min);
    input.max = String(faixa.max);
    input.setCustomValidity('');
    if (!objetivo || !Number.isFinite(pesoAtualMeta)) {
        ajuda.textContent = 'A faixa permitida será calculada com base no objetivo e no seu peso atual.';
        return;
    }
    const valor = Number(input.value);
    const valido = !input.value || (valor >= faixa.min && valor <= faixa.max
        && (chaveObjetivoMeta(objetivo) !== 'perder_peso' || valor < pesoAtualMeta));
    if (!valido) input.setCustomValidity(`Para este objetivo, informe um peso entre ${faixa.min} e ${faixa.max} kg.`);
    ajuda.textContent = `Peso atual: ${formatarPeso(pesoAtualMeta)}. Faixa para este objetivo: ${faixa.min}–${faixa.max} kg.`;
}

function calcularProgresso(pesoInicial, pesoAtual, pesoMeta) {
    if (pesoInicial == null || pesoAtual == null || pesoMeta == null
            || pesoInicial === '' || pesoAtual === '' || pesoMeta === '') return 0;
    const inicio = Number(pesoInicial);
    const atual = Number(pesoAtual);
    const meta = Number(pesoMeta);
    if (![inicio, atual, meta].every(Number.isFinite) || inicio === meta) return 0;

    // Mede quanto da distância inicial até a meta já foi percorrida, seja
    // a meta de perda ou de ganho de peso.
    const distanciaInicial = Math.abs(inicio - meta);
    const distanciaRestante = Math.abs(atual - meta);
    return Math.round(Math.max(0, Math.min(100,
        ((distanciaInicial - distanciaRestante) / distanciaInicial) * 100)));
}

function mostrarFormulario() {
    if (!metaEmEdicaoId && possuiMetaAtiva) {
        $('mensagem').textContent = 'Você já possui uma meta. Altere ou exclua a meta atual antes de cadastrar outra.';
        $('mensagem').classList.add('erro');
        return;
    }
    $('formulario').classList.remove('oculto');
    $('formulario').scrollIntoView({ behavior: 'smooth' });
}

function renderizarMeta(meta, resumo) {
    const percentual = calcularProgresso(resumo?.pesoInicial, resumo?.pesoAtual, meta.pesoMeta);
    return `<article class="objetivo-card">
        <span class="sobretitulo">Meta atual</span>
        <h2>${escaparHtml(objetivoFormulario(meta.objetivo) || 'Meta pessoal')}</h2>
        <p class="destaque">Peso-meta: <strong>${formatarPeso(meta.pesoMeta)}</strong></p>
        <div class="progresso-meta" role="progressbar" aria-label="Progresso até a meta"
             aria-valuemin="0" aria-valuemax="100" aria-valuenow="${percentual}">
            <span style="width:${percentual}%"></span>
        </div>
        <p class="legenda-progresso">${percentual}% do caminho percorrido</p>
        <div class="acoes-meta">
            <button type="button" class="botao-editar" data-acao="editar" data-id="${Number(meta.id)}">Alterar meta</button>
            <button type="button" class="botao-excluir" data-acao="excluir" data-id="${Number(meta.id)}">Excluir meta</button>
        </div>
    </article>`;
}

async function carregar() {
    const [objetivosResposta, resumoResposta] = await Promise.all([
        fetch('/api/objetivos'),
        fetch('/api/painel/resumo')
    ]);
    if (!objetivosResposta.ok) return location.href = '/login.html';

    const objetivos = await objetivosResposta.json();
    const resumo = resumoResposta.ok ? await resumoResposta.json() : null;
    pesoAtualMeta = Number.isFinite(Number(resumo?.pesoAtual)) ? Number(resumo.pesoAtual) : null;
    const metaAtual = objetivos[0];
    possuiMetaAtiva = Boolean(metaAtual);
    $('vazio').classList.toggle('oculto', Boolean(metaAtual));
    $('objetivos').innerHTML = metaAtual ? renderizarMeta(metaAtual, resumo) : '';
    if (metaAtual && !metaEmEdicaoId) $('formulario').classList.add('oculto');
}

$('abrirCadastro').onclick = mostrarFormulario;
$('cancelarEdicao').onclick = fecharFormulario;
$('objetivos').addEventListener('click', async evento => {
    const botao = evento.target.closest('button[data-acao]');
    if (!botao) return;

    const objetivoId = Number(botao.dataset.id);
    if (botao.dataset.acao === 'editar') {
        const resposta = await fetch('/api/objetivos');
        if (!resposta.ok) return;
        const objetivos = await resposta.json();
        const meta = objetivos.find(item => Number(item.id) === objetivoId);
        if (meta) iniciarEdicao(meta);
        return;
    }

    if (botao.dataset.acao === 'excluir'
            && window.confirm('Tem certeza de que deseja excluir esta meta? Esta ação não pode ser desfeita.')) {
        botao.disabled = true;
        const resposta = await fetch(`/api/objetivos/${objetivoId}`, { method: 'DELETE' });
        const dados = await resposta.json();
        $('mensagem').textContent = dados.mensagem || (resposta.ok ? 'Meta excluída.' : 'Não foi possível excluir a meta.');
        $('mensagem').classList.toggle('erro', !resposta.ok);
        if (resposta.ok) {
            fecharFormulario();
            await carregar();
        } else {
            botao.disabled = false;
        }
    }
});

function iniciarEdicao(meta) {
    metaEmEdicaoId = Number(meta.id);
    $('objetivo').value = objetivoFormulario(meta.objetivo);
    $('pesoMeta').value = meta.pesoMeta ?? '';
    $('dataMeta').value = meta.dataMeta || '';
    $('tituloFormulario').textContent = 'Alterar meta';
    $('mensagem').textContent = '';
    mostrarFormulario();
    atualizarLimitesPesoMeta();
}

function fecharFormulario() {
    metaEmEdicaoId = null;
    $('formulario').reset();
    $('pesoMeta').setCustomValidity('');
    $('formulario').classList.add('oculto');
    $('tituloFormulario').textContent = 'Nova meta';
}

$('formulario').addEventListener('submit', async evento => {
    await salvarFormulario(evento);
});
$('objetivo').addEventListener('change', atualizarLimitesPesoMeta);
$('pesoMeta').addEventListener('input', atualizarLimitesPesoMeta);

async function salvarFormulario(evento) {
    evento.preventDefault();
    const id = metaEmEdicaoId;
    if (!id && possuiMetaAtiva) {
        $('mensagem').textContent = 'Você já possui uma meta. Altere ou exclua a meta atual antes de cadastrar outra.';
        $('mensagem').classList.add('erro');
        return;
    }
    atualizarLimitesPesoMeta();
    if (!$('formulario').reportValidity()) return;
    const resposta = await fetch(id ? `/api/objetivos/${id}` : '/api/objetivos', {
        method: id ? 'PUT' : 'POST',
        body: new URLSearchParams(new FormData(evento.currentTarget))
    });
    const dados = await resposta.json();
    $('mensagem').textContent = dados.mensagem || (resposta.ok ? 'Meta salva.' : 'Não foi possível salvar a meta.');
    $('mensagem').classList.toggle('erro', !resposta.ok);
    if (resposta.ok) {
        fecharFormulario();
        await carregar();
    }
}

carregar().catch(() => {
    $('vazio').classList.remove('oculto');
    $('vazio').querySelector('h2').textContent = 'Não foi possível carregar sua meta.';
    $('vazio').querySelector('p').textContent = 'Atualize a página para tentar novamente.';
});
