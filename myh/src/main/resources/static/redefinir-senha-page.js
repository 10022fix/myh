(() => {
    const senha = document.getElementById("novaSenha");
    const confirmar = document.getElementById("confirmarSenha");
    const matchText = document.getElementById("matchText");
    if (!senha || !confirmar) return;

    const rotulos = ["Muito fraca", "Fraca", "Boa", "Forte", "Excelente"];
    const avaliadores = [
        { campo: senha, medidor: document.querySelector("#strengthTextNova")?.closest(".password-strength"), texto: document.getElementById("strengthTextNova"), inicial: "Use 8 ou mais caracteres, misturando letras e números." },
        { campo: confirmar, medidor: document.querySelector("#strengthTextConfirmar")?.closest(".password-strength"), texto: document.getElementById("strengthTextConfirmar"), inicial: "Digite novamente a senha para avaliar." }
    ];

    function avaliar({ campo, medidor, texto, inicial }) {
        if (!medidor || !texto) return;
        const valor = campo.value;
        if (!valor) {
            medidor.dataset.strength = "0";
            medidor.setAttribute("aria-valuenow", "0");
            medidor.setAttribute("aria-valuetext", inicial);
            medidor.querySelectorAll(".password-strength-bars i").forEach(barra => barra.classList.remove("is-active"));
            texto.textContent = inicial;
            return;
        }
        const criterios = [valor.length >= 8, /[a-z]/i.test(valor) && /\d/.test(valor), /[a-z]/.test(valor) && /[A-Z]/.test(valor), /[^A-Za-z0-9]/.test(valor) || valor.length >= 12];
        const pontos = criterios.filter(Boolean).length;
        medidor.dataset.strength = String(pontos);
        medidor.setAttribute("aria-valuenow", String(pontos));
        medidor.setAttribute("aria-valuetext", rotulos[pontos]);
        medidor.querySelectorAll(".password-strength-bars i").forEach((barra, indice) => barra.classList.toggle("is-active", indice < pontos));
        texto.textContent = rotulos[pontos];
    }

    function avaliarCorrespondencia() {
        if (!matchText) return;
        if (!confirmar.value) {
            matchText.textContent = "";
            matchText.removeAttribute("data-match");
            return;
        }
        const coincide = senha.value === confirmar.value;
        matchText.textContent = coincide ? "As senhas coincidem." : "As senhas não coincidem.";
        matchText.dataset.match = String(coincide);
    }

    avaliadores.forEach(item => item.campo.addEventListener("input", () => avaliar(item)));
    senha.addEventListener("input", avaliarCorrespondencia);
    confirmar.addEventListener("input", avaliarCorrespondencia);
})();
