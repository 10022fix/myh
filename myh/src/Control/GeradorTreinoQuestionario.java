package Control;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Monta duas sessões variadas a partir das respostas do questionário. */
public final class GeradorTreinoQuestionario {
    private static final ObjectMapper JSON = new ObjectMapper();

    /*
     * O catálogo descreve local, grupo muscular e regiões envolvidas. A lista é
     * intencionalmente conservadora: áreas marcadas como sensíveis são filtradas
     * antes de qualquer escolha. Ela não representa uma prescrição clínica.
     */
    private static final List<ExercicioCatalogo> CATALOGO = List.of(
            ex("Agachamento com peso corporal", "pernas", true, false, false, true, "joelhos"),
            ex("Agachamento até o banco", "pernas", true, true, false, true, "joelhos"),
            ex("Sentar e levantar do banco", "pernas", true, true, false, true, "joelhos"),
            ex("Step-up em degrau baixo", "pernas", true, true, false, false, "joelhos"),
            ex("Leg press em amplitude confortável", "pernas", false, true, false, true, "joelhos"),
            ex("Agachamento goblet", "pernas", false, true, true, false, "joelhos", "costas"),
            ex("Extensão de joelhos na máquina", "pernas", false, true, false, true, "joelhos"),
            ex("Levantamento terra romeno com halteres", "pernas", false, true, true, false, "costas"),
            ex("Ponte de glúteos", "gluteos", true, true, false, true, "costas"),
            ex("Abdução de quadril em pé com apoio", "gluteos", true, true, false, true),
            ex("Hip thrust", "gluteos", false, true, false, false, "costas"),
            ex("Extensão de quadril em pé com apoio", "gluteos", true, true, false, true),
            ex("Extensão de quadril no cabo", "gluteos", false, true, false, false),
            ex("Cadeira flexora", "pernas", false, true, false, true, "joelhos"),
            ex("Flexão na parede", "peitoral", true, false, false, true, "ombros", "cotovelos"),
            ex("Flexão inclinada", "peitoral", true, false, false, false, "ombros", "cotovelos"),
            ex("Flexão com joelhos apoiados", "peitoral", true, false, false, false, "ombros", "cotovelos"),
            ex("Supino máquina leve", "peitoral", false, true, false, true, "ombros", "cotovelos"),
            ex("Supino com halteres", "peitoral", false, true, true, false, "ombros", "cotovelos"),
            ex("Supino inclinado na máquina", "peitoral", false, true, false, true, "ombros", "cotovelos"),
            ex("Crucifixo com halteres no chão", "peitoral", true, false, false, false, "ombros", "cotovelos"),
            ex("Remada com elástico", "costas", true, false, false, true, "ombros", "cotovelos"),
            ex("Remada baixa com elástico", "costas", true, false, false, true, "ombros", "cotovelos"),
            ex("Puxada com elástico", "costas", true, false, false, true, "ombros", "cotovelos"),
            ex("Remada máquina com apoio do peito", "costas", false, true, false, true, "ombros", "cotovelos"),
            ex("Puxada frontal leve", "costas", false, true, false, true, "ombros", "cotovelos"),
            ex("Remada unilateral apoiada", "costas", true, true, true, false, "ombros", "cotovelos", "costas"),
            ex("Face pull com elástico", "costas", true, true, false, false, "ombros", "cotovelos"),
            ex("Rosca direta leve", "bracos", true, true, false, true, "cotovelos"),
            ex("Rosca martelo", "bracos", true, true, false, true, "cotovelos"),
            ex("Rosca com elástico", "bracos", true, false, false, true, "cotovelos"),
            ex("Tríceps na polia leve", "bracos", false, true, false, true, "ombros", "cotovelos"),
            ex("Extensão de tríceps com elástico", "bracos", true, false, false, true, "ombros", "cotovelos"),
            ex("Tríceps francês leve", "bracos", true, true, false, false, "ombros", "cotovelos"),
            ex("Elevação lateral leve", "ombros", true, true, false, false, "ombros", "cotovelos"),
            ex("Desenvolvimento na máquina leve", "ombros", false, true, false, true, "ombros", "cotovelos"),
            ex("Panturrilha em pé com apoio", "pernas", true, true, false, true),
            ex("Dead bug", "abdominais", true, true, false, true, "costas"),
            ex("Dead bug com toque do calcanhar", "abdominais", true, true, false, true, "costas"),
            ex("Bird dog", "abdominais", true, true, false, true, "costas"),
            ex("Prancha adaptada", "abdominais", true, true, true, false, "costas", "ombros", "cotovelos"),
            ex("Pallof press com elástico", "abdominais", true, true, false, true),
            ex("Prancha lateral com joelhos apoiados", "abdominais", true, true, false, false, "ombros", "cotovelos")
    );

    private GeradorTreinoQuestionario() { }

    public static List<TreinoGerado> gerar(String respostasJson, String objetivo, String local, int duracao) {
        return gerar(respostasJson, objetivo, local, duracao, Set.of());
    }

    /** Favorece opções diferentes das sessões ativas, mantendo os filtros e prioridades do questionário. */
    public static List<TreinoGerado> gerar(String respostasJson, String objetivo, String local, int duracao,
            Set<String> exerciciosAtivos) {
        return gerar(respostasJson, objetivo, local, duracao, 2, exerciciosAtivos);
    }

    /** Cria uma sessão personalizada para cada dia semanal escolhido no questionário. */
    public static List<TreinoGerado> gerar(String respostasJson, String objetivo, String local, int duracao,
            int quantidadeSessoes, Set<String> exerciciosAtivos) {
        if (quantidadeSessoes < 1 || quantidadeSessoes > 7) {
            throw new IllegalArgumentException("A frequência semanal deve estar entre 1 e 7 dias.");
        }
        final JsonNode respostas;
        try {
            respostas = JSON.readTree(respostasJson);
        } catch (Exception erro) {
            throw new IllegalArgumentException("Não foi possível interpretar as respostas do questionário.", erro);
        }

        int idade = idade(respostas.path("dados_pessoais").path("dataNascimento"));
        String identificacaoGenero = identificacaoGenero(respostas.path("sexo").asText());
        boolean iniciante = List.of("nunca", "ocasional", "no_passado", "varias_mes")
                .contains(respostas.path("frequencia_atual").asText());
        boolean senior = idade >= 60;
        String objetivoQuiz = chaveObjetivo(objetivo);
        Set<String> restricoes = valores(respostas.path("areas_sensiveis"));
        if (restricoes.contains("nenhum")) restricoes.clear();
        Set<String> prioridades = valores(respostas.path("musculos_importantes"));
        if (prioridades.contains("todos")) prioridades.clear();

        String tipoAcademia = respostas.path("tipo_academia").asText();
        boolean podeTreinarEmCasa = "casa".equals(local) || "hibrido".equals(local) || "em_casa".equals(tipoAcademia);
        boolean podeTreinarNaAcademia = "academia".equals(local) || "hibrido".equals(local);
        Contexto contexto = new Contexto(objetivoQuiz, iniciante, senior, podeTreinarEmCasa,
                podeTreinarNaAcademia, restricoes, prioridades, duracao,
                exerciciosAtivos == null ? Set.of() : Set.copyOf(exerciciosAtivos));

        // As duas primeiras sessões evitam repetir exercícios. As demais mantêm
        // variações próprias para que planos de maior frequência também tenham N dias.
        Set<String> usadosNasPrimeirasSessoes = new HashSet<>();
        String descricao = descricao(contexto, idade);
        List<TreinoGerado> sessoes = new ArrayList<>();
        for (int indice = 0; indice < quantidadeSessoes; indice++) {
            Set<String> usados = indice < 2 ? usadosNasPrimeirasSessoes : new HashSet<>();
            List<ExercicioGerado> exercicios = montarSessao(contexto, usados, indice % 2 == 1);
            String nome = quantidadeSessoes == 2
                    ? (indice == 0 ? "Treino A" : "Treino B") + identificacaoGenero
                    : "Dia " + (indice + 1) + identificacaoGenero;
            sessoes.add(new TreinoGerado(nome, descricao, duracao, exercicios));
        }
        return List.copyOf(sessoes);
    }

    /** Lista o catálogo compatível com o questionário para a montagem manual do aluno. */
    public static List<ExercicioDisponivel> exerciciosDisponiveis(String respostasJson, String objetivo,
            String local, int duracao) {
        final JsonNode respostas;
        try {
            respostas = JSON.readTree(respostasJson);
        } catch (Exception erro) {
            throw new IllegalArgumentException("Não foi possível interpretar as respostas do questionário.", erro);
        }
        int idade = idade(respostas.path("dados_pessoais").path("dataNascimento"));
        boolean iniciante = List.of("nunca", "ocasional", "no_passado", "varias_mes").contains(respostas.path("frequencia_atual").asText());
        Set<String> restricoes = valores(respostas.path("areas_sensiveis"));
        if (restricoes.contains("nenhum")) restricoes.clear();
        Set<String> prioridades = valores(respostas.path("musculos_importantes"));
        if (prioridades.contains("todos")) prioridades.clear();
        String tipoAcademia = respostas.path("tipo_academia").asText();
        boolean casa = "casa".equals(local) || "hibrido".equals(local) || "em_casa".equals(tipoAcademia);
        boolean academia = "academia".equals(local) || "hibrido".equals(local);
        String objetivoQuiz = chaveObjetivo(objetivo);
        Contexto contexto = new Contexto(objetivoQuiz, iniciante, idade >= 60, casa, academia,
                restricoes, prioridades, duracao, Set.of());
        return CATALOGO.stream().filter(exercicio -> elegivel(exercicio, contexto))
                .map(exercicio -> new ExercicioDisponivel(exercicio.nome, exercicio.grupo)).toList();
    }

    /** Identifica o plano pelo gênero informado sem mudar a seleção de exercícios. */
    private static String identificacaoGenero(String sexo) {
        if ("Feminino".equalsIgnoreCase(sexo)) return " (Feminino)";
        if ("Masculino".equalsIgnoreCase(sexo)) return " (Masculino)";
        return "";
    }

    private static String chaveObjetivo(String objetivo) {
        if ("Manter peso".equalsIgnoreCase(objetivo) || "manter_peso".equalsIgnoreCase(objetivo)) return "manter_peso";
        if ("Ganhar Massa".equalsIgnoreCase(objetivo)) return "ganhar_musculo";
        if ("Emagrecer".equalsIgnoreCase(objetivo)) return "perder_peso";
        if ("Condicionamento".equalsIgnoreCase(objetivo)) return "condicionamento";
        if ("Definicao".equalsIgnoreCase(objetivo) || "Definição".equalsIgnoreCase(objetivo)) return "definicao";
        return "saude";
    }

    private static List<ExercicioGerado> montarSessao(Contexto contexto, Set<String> usados, boolean variacaoB) {
        List<ExercicioCatalogo> escolhidos = new ArrayList<>();
        int quantidade = contexto.duracao <= 30 ? 3 : contexto.duracao <= 45 ? 4
                : contexto.duracao <= 60 ? 5 : contexto.duracao <= 90 ? 6 : 7;

        // Tenta incluir os músculos que a pessoa priorizou sem quebrar os filtros.
        for (String prioridade : prioridadesOrdenadas(contexto.prioridades, variacaoB)) {
            if (escolhidos.size() >= Math.min(2, quantidade)) break;
            escolher(contexto, usados, escolhidos, prioridade, true);
        }

        // Distribui a sessão entre padrões diferentes; a ordem alterna entre A e B.
        List<String> gruposBase = new ArrayList<>(List.of("pernas", "gluteos", "peitoral", "costas"));
        Collections.shuffle(gruposBase);
        if (variacaoB) Collections.rotate(gruposBase, 1);
        for (String grupo : gruposBase) {
            if (escolhidos.size() >= quantidade) break;
            escolher(contexto, usados, escolhidos, grupo, false);
        }

        // Completa com acessórios compatíveis para que a duração também influencie o plano.
        List<String> acessorios = new ArrayList<>(List.of("bracos", "ombros", "abdominais", "pernas", "gluteos", "costas", "peitoral"));
        Collections.shuffle(acessorios);
        for (String grupo : acessorios) {
            if (escolhidos.size() >= quantidade) break;
            escolher(contexto, usados, escolhidos, grupo, false);
        }

        List<ExercicioGerado> sessao = new ArrayList<>();
        for (ExercicioCatalogo exercicio : escolhidos) {
            sessao.add(prescrever(exercicio, contexto));
        }
        // A ordem muda entre as recomendações para que uma nova sugestão não pareça apenas uma cópia.
        Collections.shuffle(sessao);

        if ("perder_peso".equals(contexto.objetivo) || "condicionamento".equals(contexto.objetivo)
                || "definicao".equals(contexto.objetivo) || "manter_peso".equals(contexto.objetivo)) {
            sessao.add(ExercicioGerado.tempo(cardio(contexto, usados), minutosCardio(contexto.duracao)));
        }
        return sessao;
    }

    private static void escolher(Contexto contexto, Set<String> usados, List<ExercicioCatalogo> escolhidos,
            String grupo, boolean usarPrioridade) {
        List<ExercicioCatalogo> candidatos = CATALOGO.stream()
                .filter(e -> e.grupo.equals(grupo))
                .filter(e -> elegivel(e, contexto))
                .filter(e -> !usados.contains(e.nome))
                .filter(e -> escolhidos.stream().noneMatch(atual -> atual.nome.equals(e.nome)))
                .toList();
        if (candidatos.isEmpty()) return;

        List<ExercicioCatalogo> diferentesDoPlano = candidatos.stream()
                .filter(exercicio -> !contexto.exerciciosAtivos.contains(exercicio.nome))
                .toList();
        // Prefere variar em relação ao plano ativo, mas usa exercícios já presentes
        // quando o filtro deixaria a nova sessão incompleta (por exemplo, em casa ou
        // com várias regiões sensíveis).
        if (!diferentesDoPlano.isEmpty()) candidatos = diferentesDoPlano;

        List<ExercicioCatalogo> priorizados = candidatos.stream()
                .filter(e -> contexto.prioridades.contains(e.grupo))
                .toList();
        List<ExercicioCatalogo> opcoes = usarPrioridade && !priorizados.isEmpty()
                ? priorizados
                : (!priorizados.isEmpty() && ThreadLocalRandom.current().nextInt(100) < 65 ? priorizados : candidatos);
        ExercicioCatalogo escolhido = opcoes.get(ThreadLocalRandom.current().nextInt(opcoes.size()));
        escolhidos.add(escolhido);
        usados.add(escolhido.nome);
    }

    private static boolean elegivel(ExercicioCatalogo exercicio, Contexto contexto) {
        boolean localValido = (contexto.casa && exercicio.casa) || (contexto.academia && exercicio.academia);
        if (!localValido || (contexto.iniciante && exercicio.avancado)) return false;
        if (contexto.senior && !exercicio.adequadoSenior) return false;
        return Collections.disjoint(exercicio.areasEnvolvidas, contexto.restricoes);
    }

    private static ExercicioGerado prescrever(ExercicioCatalogo exercicio, Contexto contexto) {
        int series = contexto.senior || contexto.iniciante ? 2
                : "ganhar_musculo".equals(contexto.objetivo) ? 4 : 3;
        int repeticoes;
        if ("ganhar_musculo".equals(contexto.objetivo)) {
            repeticoes = 8 + ThreadLocalRandom.current().nextInt(5);
        } else if ("condicionamento".equals(contexto.objetivo) || "definicao".equals(contexto.objetivo)) {
            repeticoes = 12 + ThreadLocalRandom.current().nextInt(4);
        } else {
            repeticoes = contexto.senior ? 8 + ThreadLocalRandom.current().nextInt(5)
                    : 10 + ThreadLocalRandom.current().nextInt(5);
        }
        int descanso = contexto.senior ? 90
                : "ganhar_musculo".equals(contexto.objetivo) ? 90
                : "condicionamento".equals(contexto.objetivo) || "definicao".equals(contexto.objetivo) ? 45 : 60;
        if (contexto.iniciante && !contexto.senior) descanso = 75;
        return new ExercicioGerado(exercicio.nome, series, repeticoes, descanso);
    }

    private static List<String> prioridadesOrdenadas(Set<String> prioridades, boolean variacaoB) {
        List<String> lista = new ArrayList<>(prioridades);
        Collections.shuffle(lista);
        if (variacaoB) Collections.rotate(lista, 1);
        return lista;
    }

    private static String cardio(Contexto contexto, Set<String> usados) {
        List<String> opcoes;
        if (contexto.academia && !contexto.restricoes.contains("joelhos")) {
            opcoes = List.of("Bicicleta ergométrica", "Caminhada em ritmo confortável", "Elíptico em ritmo leve");
        } else if (contexto.academia) {
            opcoes = List.of("Bicicleta ergométrica", "Cicloergômetro em ritmo leve");
        } else if (contexto.restricoes.contains("joelhos")) {
            opcoes = List.of("Caminhada plana em ritmo confortável", "Marcha leve sem impacto");
        } else {
            opcoes = List.of("Caminhada em ritmo confortável", "Marcha estacionária leve", "Caminhada intervalada leve");
        }
        List<String> novas = opcoes.stream()
                .filter(nome -> !usados.contains(nome))
                .filter(nome -> contexto.exerciciosAtivos.stream()
                        .noneMatch(antigo -> antigo.equals(nome) || antigo.startsWith(nome + " (")))
                .toList();
        List<String> disponiveis = novas.isEmpty() ? opcoes : novas;
        String escolhido = disponiveis.get(ThreadLocalRandom.current().nextInt(disponiveis.size()));
        usados.add(escolhido);
        return escolhido;
    }

    private static String minutosCardio(int duracao) {
        if (duracao <= 30) return "5–8 min em ritmo confortável";
        if (duracao <= 45) return "8–12 min em ritmo confortável";
        if (duracao <= 60) return "12–18 min em ritmo confortável";
        return "15–25 min em ritmo confortável";
    }

    private static String descricao(Contexto contexto, int idade) {
        String foco;
        if ("ganhar_musculo".equals(contexto.objetivo)) {
            foco = "Foco em força e hipertrofia, com volume ajustado ao nível informado.";
        } else if ("condicionamento".equals(contexto.objetivo)) {
            foco = "Foco em condicionamento físico, com exercícios de força e complemento aeróbico.";
        } else if ("definicao".equals(contexto.objetivo)) {
            foco = "Foco em definição e composição corporal, com volume e ritmo ajustados ao nível informado.";
        } else if ("manter_peso".equals(contexto.objetivo)) {
            foco = "Foco em manter o peso com força equilibrada e complemento aeróbico moderado.";
        } else if ("saude".equals(contexto.objetivo)) {
            foco = "Foco em saúde geral, força funcional e movimentos controlados.";
        } else {
            foco = "Foco em força e condicionamento; o cardio complementa o objetivo de emagrecimento.";
        }
        if (contexto.senior) foco += " Volume moderado considerando a faixa etária informada.";
        else if (contexto.iniciante) foco += " Priorize técnica, carga confortável e progressão gradual.";
        else foco += " As sessões alternam exercícios para variar os estímulos.";
        if (!contexto.prioridades.isEmpty()) foco += " O plano dá preferência aos grupos musculares escolhidos.";
        if (!contexto.restricoes.isEmpty()) {
            foco += " Movimentos que envolvem as áreas marcadas como sensíveis foram filtrados; interrompa se sentir dor e procure orientação profissional.";
        }
        return foco + " Faça aquecimento leve antes da sessão e ajuste a carga para manter a execução controlada.";
    }

    private static int idade(JsonNode nascimento) {
        try {
            return Period.between(LocalDate.parse(nascimento.asText()), LocalDate.now()).getYears();
        } catch (Exception ignorado) {
            return 30;
        }
    }

    private static Set<String> valores(JsonNode node) {
        Set<String> valores = new HashSet<>();
        if (node != null && node.isArray()) node.forEach(valor -> valores.add(valor.asText()));
        return valores;
    }

    private static ExercicioCatalogo ex(String nome, String grupo, boolean casa, boolean academia,
            boolean avancado, boolean adequadoSenior, String... areasEnvolvidas) {
        return new ExercicioCatalogo(nome, grupo, casa, academia, avancado, adequadoSenior, Set.of(areasEnvolvidas));
    }

    private record Contexto(String objetivo, boolean iniciante, boolean senior, boolean casa,
            boolean academia, Set<String> restricoes, Set<String> prioridades, int duracao,
            Set<String> exerciciosAtivos) { }

    private record ExercicioCatalogo(String nome, String grupo, boolean casa, boolean academia,
            boolean avancado, boolean adequadoSenior, Set<String> areasEnvolvidas) { }

    public record TreinoGerado(String nome, String descricao, int duracao, List<ExercicioGerado> exercicios) { }

    public record ExercicioDisponivel(String nome, String grupo) { }

    public record ExercicioGerado(String nome, int series, int repeticoes, int descanso) {
        static ExercicioGerado tempo(String nome, String prescricao) {
            return new ExercicioGerado(nome + " (" + prescricao + ")", 1, 0, 0);
        }
    }
}
