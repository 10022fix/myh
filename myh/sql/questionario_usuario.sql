-- Execute este arquivo no banco TCC antes de utilizar o questionario.
CREATE TABLE IF NOT EXISTS questionario_usuario (
    usuario_id INT NOT NULL PRIMARY KEY,
    forma_atual VARCHAR(30) NOT NULL,
    forma_desejada VARCHAR(30) NOT NULL,
    frequencia_semanal TINYINT NOT NULL,
    local_treino VARCHAR(20) NOT NULL,
    duracao_preferida SMALLINT NOT NULL,
    respostas JSON NOT NULL,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_questionario_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
);
