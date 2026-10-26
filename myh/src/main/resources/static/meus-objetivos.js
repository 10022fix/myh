const $ = id => document.getElementById(id);

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
    $('formulario').classList.remove('oculto');
    $('formulario').scrollIntoView({ behavior: 'smooth' });
}

function renderizarMeta(meta, resumo) {
    const percentual = calcularProgresso(resumo?.pesoInicial, resumo?.pesoAtual, meta.pesoMeta);
    return `<article class="objetivo-card">
        <span class="sobretitulo">Meta atual</span>
        <h2>${escaparHtml(meta.objetivo || 'Meta pessoal')}</h2>
        <p class="destaque">Peso-meta: <strong>${formatarPeso(meta.pesoMeta)}</strong></p>
        <div class="progresso-meta" role="progressbar" aria-label="Progresso até a meta"
             aria-valuemin="0" aria-valuemax="100" aria-valuenow="${percentual}">
            <span style="width:${percentual}%"></span>
        </div>
        <p class="legenda-progresso">${percentual}% do caminho percorrido</p>
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
    const metaAtual = objetivos[0];
    $('vazio').classList.toggle('oculto', Boolean(metaAtual));
    $('novo').classList.toggle('oculto', !metaAtual);
    $('objetivos').innerHTML = metaAtual ? renderizarMeta(metaAtual, resumo) : '';
}

$('abrirCadastro').onclick = mostrarFormulario;
$('novo').onclick = mostrarFormulario;
$('formulario').addEventListener('submit', async evento => {
    evento.preventDefault();
    const resposta = await fetch('/api/objetivos', {
        method: 'POST',
        body: new URLSearchParams(new FormData(evento.currentTarget))
    });
    const dados = await resposta.json();
    $('mensagem').textContent = dados.mensagem || (resposta.ok ? 'Meta salva.' : 'Não foi possível salvar a meta.');
    $('mensagem').classList.toggle('erro', !resposta.ok);
    if (resposta.ok) {
        evento.currentTarget.reset();
        $('formulario').classList.add('oculto');
        await carregar();
    }
});

carregar().catch(() => {
    $('vazio').classList.remove('oculto');
    $('vazio').querySelector('h2').textContent = 'Não foi possível carregar sua meta.';
    $('vazio').querySelector('p').textContent = 'Atualize a página para tentar novamente.';
});
