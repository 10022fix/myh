const lista = document.getElementById('lista'); let temporizador;
const escapar = v => String(v ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
async function carregar() {
    const busca = document.getElementById('busca').value;
    try {
        const r = await fetch('/api/treinos?busca=' + encodeURIComponent(busca));
        if (r.status === 401) return location.href = '/login.html';
        if (!r.ok) {
            lista.innerHTML = `<section class="vazio"><h2>Não foi possível carregar os treinos.</h2><p>O servidor retornou o erro ${r.status}. Tente novamente mais tarde.</p></section>`;
            return;
        }
        const treinos = await r.json();
        lista.innerHTML = treinos.length ? treinos.map(t=>`<article class="treino"><div class="treino-topo"><div><h2>${escapar(t.nome)}</h2><p class="descricao">${escapar(t.descricao || 'Treino personalizado para você.')}</p></div><span class="selo">${escapar(t.nivel)}</span></div><div class="meta"><span>◷ ${t.duracao} min</span><span>${t.exercicios.length} exercícios</span></div><ul class="exercicios">${t.exercicios.map(e=>`<li><strong>${escapar(e.nome)}</strong><small>${e.series} séries × ${e.repeticoes} repetições · ${e.descanso}s descanso</small></li>`).join('')}</ul></article>`).join('') : `<section class="vazio"><h2>Nenhum treino encontrado</h2><p>Experimente buscar outro termo ou aguarde a liberação de um treino pelo profissional.</p></section>`;
    } catch (erro) {
        lista.innerHTML = '<section class="vazio"><h2>Falha de conexão.</h2><p>Não foi possível falar com o servidor. Atualize a página para tentar novamente.</p></section>';
    }
}
document.getElementById('busca').addEventListener('input',()=>{clearTimeout(temporizador);temporizador=setTimeout(carregar,250)});carregar();
