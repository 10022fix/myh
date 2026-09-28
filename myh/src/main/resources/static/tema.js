(() => {
    const storageKey = "myh-theme-v2";
    const validThemes = ["dark", "light"];
    let savedTheme = "light";

    try {
        const stored = localStorage.getItem(storageKey);
        if (validThemes.includes(stored)) savedTheme = stored;
    } catch (_) {}

    document.documentElement.dataset.theme = savedTheme;

    const sunIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="4"></circle><path d="M12 2v2m0 16v2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M2 12h2m16 0h2M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42"></path></svg>';
    const moonIcon = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20.2 15.5A8.5 8.5 0 0 1 8.5 3.8 8.5 8.5 0 1 0 20.2 15.5Z"></path></svg>';

    function updateButton(button, theme) {
        const isDark = theme === "dark";
        button.innerHTML = isDark ? moonIcon : sunIcon;
        button.title = isDark ? "Tema escuro — clique para usar o claro" : "Tema claro — clique para usar o escuro";
        button.setAttribute("aria-label", button.title);
        button.setAttribute("aria-pressed", String(!isDark));
    }

    function installThemeButtons() {
        document.querySelectorAll("nav.navbar").forEach(nav => {
            let button = nav.querySelector(".theme-toggle");
            if (!button) {
                button = document.createElement("button");
                button.type = "button";
                button.className = "theme-toggle";
                button.addEventListener("click", () => {
                    const nextTheme = document.documentElement.dataset.theme === "dark" ? "light" : "dark";
                    document.documentElement.dataset.theme = nextTheme;
                    try { localStorage.setItem(storageKey, nextTheme); } catch (_) {}
                    document.querySelectorAll(".theme-toggle").forEach(item => updateButton(item, nextTheme));
                });

                const alunoButton = nav.querySelector(".btn-aluno");
                if (alunoButton) nav.insertBefore(button, alunoButton);
                else nav.appendChild(button);
            }
            updateButton(button, document.documentElement.dataset.theme || "dark");
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", installThemeButtons, { once: true });
    } else {
        installThemeButtons();
    }
})();