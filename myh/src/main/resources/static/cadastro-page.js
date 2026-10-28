(() => {
    const formCadastro = document.getElementById("formCadastro");
    const chaveRascunhoCadastro = "myhCadastroRascunhoTermos";

    // Guarda os campos apenas durante a navegação para os documentos legais.
    // O rascunho é apagado assim que o usuário retorna ao cadastro.
    if (formCadastro) {
        formCadastro.addEventListener("click", evento => {
            const linkLegal = evento.target.closest(
                'a[href*="termos-responsabilidade"], a[href*="politica-privacidade"]'
            );
            if (!linkLegal) return;

            const controles = [...formCadastro.elements].filter(controle => controle.name);
            const rascunho = controles.map((controle, indice) => ({
                name: controle.name,
                indice,
                type: controle.type,
                value: controle.value,
                checked: controle.type === "checkbox" || controle.type === "radio"
                    ? controle.checked
                    : undefined
            }));

            try {
                sessionStorage.setItem(chaveRascunhoCadastro, JSON.stringify(rascunho));
            } catch (_) {
                // Se o armazenamento da sessão estiver indisponível, a navegação continua normalmente.
            }
        });

        window.addEventListener("pageshow", () => {
            let rascunho;
            try {
                rascunho = JSON.parse(sessionStorage.getItem(chaveRascunhoCadastro) || "null");
                sessionStorage.removeItem(chaveRascunhoCadastro);
            } catch (_) {
                return;
            }
            if (!Array.isArray(rascunho)) return;

            const controles = [...formCadastro.elements].filter(controle => controle.name);
            for (const item of rascunho) {
                const controle = controles[item.indice];
                if (!controle || controle.name !== item.name || controle.type !== item.type) continue;
                if (controle.type === "checkbox" || controle.type === "radio") {
                    controle.checked = Boolean(item.checked);
                } else {
                    controle.value = item.value;
                }
            }
            campo?.dispatchEvent(new Event("input", { bubbles: true }));
        });
    }

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
