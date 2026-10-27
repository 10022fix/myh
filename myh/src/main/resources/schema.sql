CREATE TABLE IF NOT EXISTS usuario (
    id INT NOT NULL AUTO_INCREMENT,
    nome VARCHAR(150) NOT NULL,
    apelido_acesso VARCHAR(30) NULL,
    email VARCHAR(254) NOT NULL,
    senha VARCHAR(512) NOT NULL,
    data_nascimento DATE NULL,
    sexo VARCHAR(40) NULL,
    altura DECIMAL(4,2) NOT NULL,
    peso_inicial DECIMAL(6,2) NOT NULL,
    email_verificado BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuario_apelido_acesso (apelido_acesso),
    UNIQUE KEY uk_usuario_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS usuario_email_verificacao (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expira_em DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuario_email_verificacao_token (token_hash),
    KEY ix_usuario_email_verificacao_usuario (usuario_id),
    CONSTRAINT fk_usuario_email_verificacao_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS usuario_aceite_legal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    versao_termos VARCHAR(30) NOT NULL,
    versao_privacidade VARCHAR(30) NOT NULL,
    consentimento_dados_saude BOOLEAN NOT NULL,
    aceito_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY ix_usuario_aceite_legal_usuario (usuario_id),
    CONSTRAINT fk_usuario_aceite_legal_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS senha_redefinicao (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expira_em DATETIME NOT NULL,
    criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_senha_redefinicao_token (token_hash),
    KEY ix_senha_redefinicao_usuario (usuario_id),
    CONSTRAINT fk_senha_redefinicao_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS objetivos (
    id INT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    objetivo VARCHAR(40) NOT NULL,
    peso_meta DECIMAL(6,2) NULL,
    data_meta DATE NULL,
    PRIMARY KEY (id),
    KEY idx_objetivos_usuario (usuario_id),
    CONSTRAINT fk_objetivos_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS questionario_usuario (
    usuario_id INT NOT NULL,
    forma_atual VARCHAR(30) NOT NULL,
    forma_desejada VARCHAR(30) NOT NULL,
    frequencia_semanal TINYINT NOT NULL,
    local_treino VARCHAR(20) NOT NULL,
    duracao_preferida SMALLINT NOT NULL,
    respostas JSON NOT NULL,
    atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (usuario_id),
    CONSTRAINT fk_questionario_usuario_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS treinos (
    id INT NOT NULL AUTO_INCREMENT,
    nome_treino VARCHAR(150) NOT NULL,
    descricao TEXT NULL,
    nivel VARCHAR(30) NOT NULL,
    duracao_treino SMALLINT NOT NULL,
    objetivo VARCHAR(40) NOT NULL,
    local_treino VARCHAR(20) NOT NULL,
    frequencia_semanal TINYINT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS medidasC (
    id INT NOT NULL AUTO_INCREMENT,
    braco DECIMAL(6,2) NULL,
    antebraco DECIMAL(6,2) NULL,
    cintura DECIMAL(6,2) NULL,
    peito DECIMAL(6,2) NULL,
    quadril DECIMAL(6,2) NULL,
    coxa DECIMAL(6,2) NULL,
    panturrilha DECIMAL(6,2) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS progressao (
    id INT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    peso_atual DECIMAL(6,2) NOT NULL,
    percentual_gordura DECIMAL(5,2) NULL,
    medidasC_id INT NOT NULL,
    data_registro DATE NOT NULL,
    PRIMARY KEY (id),
    KEY idx_progressao_usuario_data (usuario_id, data_registro),
    CONSTRAINT fk_progressao_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_progressao_medidas FOREIGN KEY (medidasC_id)
        REFERENCES medidasC(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exercicios (
    id INT NOT NULL AUTO_INCREMENT,
    nome VARCHAR(150) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS treino_exercicios (
    id INT NOT NULL AUTO_INCREMENT,
    treino_id INT NOT NULL,
    exercicio_id INT NOT NULL,
    series TINYINT NOT NULL,
    repeticoes SMALLINT NOT NULL,
    tempo_descanso SMALLINT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_treino_exercicios_treino (treino_id),
    CONSTRAINT fk_treino_exercicios_treino FOREIGN KEY (treino_id)
        REFERENCES treinos(id) ON DELETE CASCADE,
    CONSTRAINT fk_treino_exercicios_exercicio FOREIGN KEY (exercicio_id)
        REFERENCES exercicios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS usuario_treinos (
    id INT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    treino_id INT NOT NULL,
    data_inicio DATE NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    KEY idx_usuario_treinos_ativo (usuario_id, ativo),
    CONSTRAINT fk_usuario_treinos_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_usuario_treinos_treino FOREIGN KEY (treino_id)
        REFERENCES treinos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS treinos_concluidos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    usuario_id INT NOT NULL,
    treino_id INT NOT NULL,
    data_conclusao DATE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_treino_concluido_dia (usuario_id, treino_id, data_conclusao),
    KEY idx_treinos_concluidos_data (usuario_id, data_conclusao),
    CONSTRAINT fk_treino_concluido_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuario(id) ON DELETE CASCADE,
    CONSTRAINT fk_treino_concluido_treino FOREIGN KEY (treino_id)
        REFERENCES treinos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
