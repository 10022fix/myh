(() => {
    const stateKey = "myh-demo-state-v1";
    const defaultState = {
        objective: "Emagrecer",
        targetWeight: 75,
        currentWeight: 80.6,
        initialWeight: 82.4,
        history: [
            { weight: 80.6, label: "Hoje" },
            { weight: 81.2, label: "Há 7 dias" },
            { weight: 82.4, label: "Início" }
        ]
    };

    let state = { ...defaultState, ...readState() };
    const panels = [...document.querySelectorAll("[data-panel]")];
    const navLinks = [...document.querySelectorAll(".demo-nav-links [data-view]")];

    function readState() {
        try {
            return JSON.parse(localStorage.getItem(stateKey) || "{}");
        } catch (_) {
            return {};
        }
    }

    function saveState() {
        try { localStorage.setItem(stateKey, JSON.stringify(state)); } catch (_) {}
    }

    function formatWeight(weight) {
        return `${Number(weight).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} kg`;
    }

    function setView(view) {
        const known = panels.some(panel => panel.dataset.panel === view);
        const selected = known ? view : "inicio";
        panels.forEach(panel => {
            const active = panel.dataset.panel === selected;
            panel.hidden = !active;
            panel.classList.toggle("active", active);
        });
        navLinks.forEach(link => {
            const active = link.dataset.view === selected;
            link.classList.toggle("active", active);
            if (active) link.setAttribute("aria-current", "page");
            else link.removeAttribute("aria-current");
        });
        if (location.hash !== `#${selected}`) history.replaceState(null, "", `#${selected}`);
        window.scrollTo({ top: 0, behavior: "smooth" });
    }

    function renderGoal() {
        document.querySelector(".goal-summary-heading h2").textContent = `Rumo à meta · ${state.objective}`;
        document.getElementById("demo-goal-name").textContent = state.objective;
        document.getElementById("demo-goal-weight").textContent = formatWeight(state.targetWeight);
        document.getElementById("target-weight").textContent = formatWeight(state.targetWeight);
        const span = state.initialWeight - state.targetWeight;
        const moved = state.initialWeight - state.currentWeight;
        const progress = span > 0 ? Math.min(100, Math.max(0, Math.round(moved / span * 100))) : 0;
        document.getElementById("goal-percent").textContent = `${progress}%`;
        document.getElementById("goal-progress-bar").style.width = `${progress}%`;
    }

    function renderProgress() {
        document.getElementById("current-weight").textContent = formatWeight(state.currentWeight);
        document.getElementById("progress-current").textContent = formatWeight(state.currentWeight);
        document.getElementById("progress-initial").textContent = formatWeight(state.initialWeight);
        document.getElementById("initial-weight").textContent = formatWeight(state.initialWeight);
        const history = document.getElementById("demo-history");
        const heading = history.querySelector("h3");
        history.replaceChildren(heading);
        state.history.forEach(item => {
            const row = document.createElement("p");
            row.append(document.createTextNode(formatWeight(item.weight)));
            const date = document.createElement("span");
            date.textContent = item.label;
            row.append(date);
            history.append(row);
        });
        document.getElementById("progress-date").textContent = state.history[0]?.label || "—";
    }

    document.querySelectorAll("[data-go]").forEach(button => {
        button.addEventListener("click", () => setView(button.dataset.go));
    });
    navLinks.forEach(link => link.addEventListener("click", event => {
        event.preventDefault();
        setView(link.dataset.view);
    }));
    window.addEventListener("hashchange", () => setView(location.hash.slice(1)));

    const exerciseChecks = [...document.querySelectorAll(".exercise input")];
    exerciseChecks.forEach(check => check.addEventListener("change", () => {
        const complete = exerciseChecks.filter(item => item.checked).length;
        document.getElementById("workout-feedback").textContent = `${complete} de 4 exercícios concluídos`;
        const today = document.querySelector(".week-day.today");
        today.classList.toggle("done", complete === exerciseChecks.length);
        today.querySelector("b").textContent = complete === exerciseChecks.length ? "✓" : "";
        const completedDays = document.querySelectorAll(".week-day.done").length;
        document.getElementById("week-summary").textContent = `${completedDays} treinos concluídos nesta semana`;
        saveState();
    }));

    document.getElementById("demo-goal-form").addEventListener("submit", event => {
        event.preventDefault();
        const objective = document.getElementById("demo-goal-select").value;
        const target = Number(document.getElementById("demo-target-input").value);
        if (!Number.isFinite(target) || target <= 0 || target >= state.currentWeight) {
            document.getElementById("goal-feedback").textContent = "Para este exemplo, a meta de peso deve ser menor que o peso atual.";
            return;
        }
        state.objective = objective;
        state.targetWeight = target;
        document.querySelector(".goal-summary-heading h2").textContent = `Rumo à meta · ${objective}`;
        document.getElementById("goal-feedback").textContent = "Meta de demonstração atualizada neste navegador.";
        renderGoal();
        saveState();
    });

    document.getElementById("demo-progress-form").addEventListener("submit", event => {
        event.preventDefault();
        const input = document.getElementById("demo-weight-input");
        const weight = Number(input.value);
        if (!Number.isFinite(weight) || weight <= 0) return;
        state.currentWeight = weight;
        state.history.unshift({ weight, label: "Agora" });
        state.history = state.history.slice(0, 10);
        document.getElementById("progress-feedback").textContent = "Registro de exemplo adicionado neste navegador.";
        renderProgress();
        renderGoal();
        input.value = "";
        saveState();
    });

    document.getElementById("demo-goal-select").value = state.objective;
    document.getElementById("demo-target-input").value = state.targetWeight;
    renderGoal();
    renderProgress();
    setView(location.hash.slice(1) || "inicio");
})();
