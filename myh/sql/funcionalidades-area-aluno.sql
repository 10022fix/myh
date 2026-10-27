-- Execute este complemento apos criar as tabelas do banco principal.
-- A tabela medidasC precisa existir antes de progressao, pois progressao a referencia.
-- Se o CREATE TABLE progressao do script principal falhar por essa ordem, execute-o novamente apos criar medidasC.

CREATE INDEX idx_usuario_treinos_ativo ON usuario_treinos (usuario_id, ativo);
CREATE INDEX idx_objetivos_usuario ON objetivos (usuario_id);
CREATE INDEX idx_progressao_usuario_data ON progressao (usuario_id, data_registro);

CREATE TABLE IF NOT EXISTS agenda_treino_semana (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    vigente_desde DATE NOT NULL,
    frequencia_semanal TINYINT NOT NULL,
    criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agenda_usuario_inicio (usuario_id, vigente_desde),
    KEY idx_agenda_usuario_inicio (usuario_id, vigente_desde),
    CONSTRAINT fk_agenda_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agenda_treino_dia (
    agenda_id BIGINT NOT NULL,
    dia_semana TINYINT NOT NULL,
    ordem_treino TINYINT NOT NULL,
    treino_id INT NOT NULL,
    PRIMARY KEY (agenda_id, dia_semana),
    UNIQUE KEY uk_agenda_sessao (agenda_id, treino_id),
    CONSTRAINT fk_agenda_dia_agenda FOREIGN KEY (agenda_id) REFERENCES agenda_treino_semana(id) ON DELETE CASCADE,
    CONSTRAINT fk_agenda_dia_treino FOREIGN KEY (treino_id) REFERENCES treinos(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cargas_exercicio (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    treino_id INT NOT NULL,
    exercicio_id INT NOT NULL,
    data_registro DATE NOT NULL,
    carga_kg DECIMAL(7,2) NOT NULL,
    series TINYINT NULL,
    repeticoes SMALLINT NULL,
    criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_carga_usuario_treino_exercicio_data (usuario_id, treino_id, exercicio_id, data_registro),
    KEY idx_cargas_usuario_data (usuario_id, data_registro),
    CONSTRAINT fk_cargas_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_cargas_treino FOREIGN KEY (treino_id) REFERENCES treinos(id),
    CONSTRAINT fk_cargas_exercicio FOREIGN KEY (exercicio_id) REFERENCES exercicios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
