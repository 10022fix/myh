package Control;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Gera duas sessões de treino usando as respostas salvas do questionário. */
public final class GeradorTreinoQuestionario {
    private static final ObjectMapper JSON = new ObjectMapper();

    private GeradorTreinoQuestionario() { }

    public static List<TreinoGerado> gerar(String respostasJson, String objetivo, String local, int duracao) {
        final JsonNode respostas;
        try {
            respostas = JSON.readTree(respostasJson);
        } catch (Exception erro) {
            throw new IllegalArgumentException("Não foi possível interpretar as respostas do questionário.", erro);
        }

        int idade = idade(respostas.path("dados_pessoais").path("dataNascimento").asText());
        String objetivoQuiz = respostas.path("objetivo").asText();
        if (objetivoQuiz.isBlank()) {
            objetivoQuiz = "Emagrecer".equalsIgnoreCase(objetivo) ? "perder_peso" : "ganhar_musculo";
        }
        String frequenciaAtual = respostas.path("frequencia_atual").asText();
        boolean iniciante = List.of("nunca", "no_passado", "varias_mes").contains(frequenciaAtual);
        boolean senior = idade >= 60;
        boolean emCasa = "casa".equals(local)
                || "em_casa".equals(respostas.path("tipo_academia").asText());
        List<String> restricoes = new ArrayList<>();
        respostas.path("areas_sensiveis").forEach(item -> restricoes.add(item.asText()));
        if (restricoes.contains("nenhum")) restricoes.clear();

        List<ExercicioGerado> treinoA = exerciciosA(emCasa, iniciante, senior, restricoes, objetivoQuiz, duracao);
        List<ExercicioGerado> treinoB = exerciciosB(emCasa, iniciante, senior, restricoes);
        String descricao = descricao(idade, iniciante, objetivoQuiz, restricoes);

        return List.of(
                new TreinoGerado("Treino A", descricao, duracao, treinoA),
                new TreinoGerado("Treino B", descricao, duracao, treinoB)
        );
    }

    private static int idade(String nascimento) {
        try {
            return Period.between(LocalDate.parse(nascimento), LocalDate.now()).getYears();
        } catch (Exception ignorado) {
            return 30;
        }
    }

    private static String descricao(int idade, boolean iniciante, String objetivo, List<String> restricoes) {
        String foco = idade >= 60
                ? "Foco em força funcional, equilíbrio, mobilidade e proteção articular."
                : iniciante
                    ? "Foco em técnica, volume gradual e recuperação entre sessões."
                    : "Foco em força e condicionamento com volume sustentável.";
        if ("perder_peso".equals(objetivo)) foco += " O cardio complementa o treino de força.";
        if (!restricoes.isEmpty()) {
            foco += " Ajuste carga e amplitude à sua tolerância; pare se sentir dor e procure orientação profissional.";
        }
        return foco + " Antes, faça 5 min de caminhada ou bicicleta leve e mobilidade confortável. "
                + "Finalize com 3–5 min leves. Se um movimento provocar dor, interrompa e procure orientação profissional.";
    }

    private static List<ExercicioGerado> exerciciosA(boolean casa, boolean iniciante, boolean senior,
            List<String> restricoes, String objetivo, int minutos) {
        List<ExercicioGerado> exercicios = new ArrayList<>();
        boolean joelhos = restricoes.contains("joelhos");
        boolean ombros = restricoes.contains("ombros");

        if (senior) {
            adicionar(exercicios, joelhos ? "Ponte de glúteos" : casa ? "Agachamento para banco" : "Leg press em amplitude confortável", 2, 10, 90);
            adicionar(exercicios, casa || ombros ? "Flexão na parede" : "Supino máquina", 2, 10, 90);
            adicionar(exercicios, casa ? "Remada com elástico" : "Puxada frontal leve", 2, 10, 90);
            adicionar(exercicios, casa ? "Ponte de glúteos" : "Cadeira flexora", 2, 12, 75);
            adicionar(exercicios, casa ? "Remada apoiada com elástico" : "Remada apoiada", 2, 10, 90);
            adicionar(exercicios, casa ? "Panturrilha em pé com apoio" : "Panturrilha sentada", 2, 12, 60);
        } else if (iniciante) {
            adicionar(exercicios, joelhos ? "Sentar e levantar do banco" : casa ? "Agachamento com peso corporal" : "Agachamento livre ou no Smith", 3, 10, 90);
            adicionar(exercicios, casa || ombros ? "Flexão inclinada" : "Supino com halteres", 3, 10, 90);
            adicionar(exercicios, casa ? "Remada com elástico" : "Puxada frontal", 3, 10, 90);
            adicionar(exercicios, joelhos ? "Ponte de glúteos" : casa ? "Agachamento para banco" : "Leg press", 3, 11, 90);
            adicionar(exercicios, casa ? "Remada unilateral com halter" : "Remada baixa", 3, 11, 90);
            if (!ombros) adicionar(exercicios, casa ? "Elevação lateral leve" : "Elevação lateral", 2, 13, 60);
        } else {
            adicionar(exercicios, joelhos ? casa ? "Agachamento para banco em amplitude confortável" : "Leg press em amplitude confortável" : casa ? "Agachamento para banco" : "Leg press", 3, 10, 90);
            adicionar(exercicios, casa || ombros ? "Flexão inclinada" : "Supino máquina", 3, 10, 90);
            adicionar(exercicios, casa ? "Remada com elástico" : "Puxada frontal", 3, 10, 90);
            adicionar(exercicios, casa ? "Ponte de glúteos" : "Cadeira flexora", 3, 12, 75);
            adicionar(exercicios, casa ? "Remada apoiada com elástico" : "Remada apoiada", 3, 11, 90);
            adicionar(exercicios, casa ? "Panturrilha em pé com apoio" : "Panturrilha sentada", 3, 13, 60);
        }

        if ("perder_peso".equals(objetivo)) {
            exercicios.add(ExercicioGerado.tempo(casa ? "Caminhada" : "Bicicleta ergométrica", minutos >= 60 ? "10–15 min" : "8–12 min"));
        }
        return exercicios;
    }

    private static List<ExercicioGerado> exerciciosB(boolean casa, boolean iniciante, boolean senior,
            List<String> restricoes) {
        List<ExercicioGerado> exercicios = new ArrayList<>();
        boolean joelhos = restricoes.contains("joelhos");
        boolean ombros = restricoes.contains("ombros");
        boolean costas = restricoes.contains("costas");

        if (senior) {
            adicionar(exercicios, "Sentar e levantar do banco", 3, 10, 90);
            adicionar(exercicios, casa ? "Remada com elástico" : "Remada máquina", 3, 11, 90);
            adicionar(exercicios, casa || ombros ? "Flexão na parede" : "Supino máquina", 3, 10, 90);
            adicionar(exercicios, joelhos ? "Ponte de glúteos" : "Step-up baixo", 2, 8, 60);
            exercicios.add(ExercicioGerado.tempo("Prancha adaptada", "20–30 s"));
            exercicios.add(ExercicioGerado.tempo("Caminhada", "15–30 min"));
        } else if (iniciante) {
            adicionar(exercicios, joelhos ? "Sentar e levantar do banco" : "Agachamento com peso corporal", 3, 12, 75);
            adicionar(exercicios, casa || ombros ? "Flexão inclinada" : "Supino máquina", 3, 10, 75);
            adicionar(exercicios, casa ? "Remada com elástico" : "Remada na máquina", 3, 12, 75);
            adicionar(exercicios, joelhos ? "Ponte de glúteos" : "Step-up baixo", 2, 9, 60);
            exercicios.add(ExercicioGerado.tempo("Prancha adaptada", "20–40 s"));
            exercicios.add(ExercicioGerado.tempo(casa ? "Caminhada" : "Bicicleta ergométrica", "15–20 min"));
        } else {
            adicionar(exercicios, joelhos ? "Agachamento para banco em amplitude confortável" : "Agachamento para banco", 3, 11, 90);
            adicionar(exercicios, costas ? "Ponte de glúteos" : "Hip thrust", 3, 11, 90);
            adicionar(exercicios, casa ? "Remada com elástico" : "Remada apoiada", 3, 11, 90);
            adicionar(exercicios, joelhos ? casa ? "Ponte de glúteos" : "Cadeira flexora" : casa ? "Agachamento para banco" : "Leg press", 3, 11, 90);
            exercicios.add(ExercicioGerado.tempo("Prancha adaptada", "20–40 s"));
            exercicios.add(ExercicioGerado.tempo(casa ? "Caminhada" : "Bicicleta ergométrica", "15–25 min"));
        }
        return exercicios;
    }

    private static void adicionar(List<ExercicioGerado> lista, String nome, int series, int repeticoes, int descanso) {
        lista.add(new ExercicioGerado(nome, series, repeticoes, descanso));
    }

    public record TreinoGerado(String nome, String descricao, int duracao, List<ExercicioGerado> exercicios) { }
    public record ExercicioGerado(String nome, int series, int repeticoes, int descanso) {
        static ExercicioGerado tempo(String nome, String prescricao) {
            return new ExercicioGerado(nome + " (" + prescricao + ")", 1, 0, 0);
        }
    }
}
