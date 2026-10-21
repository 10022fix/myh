document.querySelectorAll(".alternar-senha").forEach(botao => {
    botao.addEventListener("click", () => {
        const campo = document.getElementById(botao.getAttribute("aria-controls"));
        if (!campo) return;

        const mostrar = campo.type === "password";
        campo.type = mostrar ? "text" : "password";
        botao.classList.toggle("senha-visivel", mostrar);
        botao.setAttribute("aria-pressed", String(mostrar));
        botao.setAttribute("aria-label", mostrar ? "Ocultar senha" : "Mostrar senha");
        const texto = botao.querySelector("span");
        if (texto) texto.textContent = mostrar ? "Ocultar" : "Mostrar";
    });
});
