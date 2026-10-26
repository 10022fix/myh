(() => {
    const campo = document.getElementById("senha");
    const medidor = document.querySelector(".password-strength");
    const segmentos = [...document.querySelectorAll(".password-strength-bars i")];
    const texto = document.getElementById("passwordStrengthText");
    if (!campo || !medidor || !texto) return;

    const mensagemInicial = "Use 8 ou mais caracteres, misturando letras e números.";
    const rotulos = ["Muito fraca", "Fraca", "Boa", "Forte", "Excelente"];

    function avaliarSenha() {
        const senha = campo.value;
        if (!senha) {
            medidor.dataset.strength = "0";
            medidor.setAttribute("aria-valuenow", "0");
            medidor.setAttribute("aria-valuetext", mensagemInicial);
            segmentos.forEach(segmento => segmento.classList.remove("is-active"));
            texto.textContent = mensagemInicial;
            return;
        }

        const criterios = [
            senha.length >= 8,
            /[a-z]/i.test(senha) && /\d/.test(senha),
            /[a-z]/.test(senha) && /[A-Z]/.test(senha),
            /[^A-Za-z0-9]/.test(senha) || senha.length >= 12
        ];
        const pontos = criterios.filter(Boolean).length;
        medidor.dataset.strength = String(pontos);
        medidor.setAttribute("aria-valuenow", String(pontos));
        segmentos.forEach((segmento, indice) => segmento.classList.toggle("is-active", indice < pontos));
        texto.textContent = rotulos[pontos];
        medidor.setAttribute("aria-valuetext", rotulos[pontos]);
    }

    campo.addEventListener("input", avaliarSenha);
    campo.form?.addEventListener("reset", () => requestAnimationFrame(avaliarSenha));
})();
