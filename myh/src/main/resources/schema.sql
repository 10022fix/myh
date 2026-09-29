CREATE TABLE IF NOT EXISTS usuario (
    id INT NOT NULL AUTO_INCREMENT,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL,
    senha VARCHAR(512) NOT NULL,
    data_nascimento DATE NULL,
    sexo VARCHAR(40) NULL,
    altura DECIMAL(4,2) NOT NULL,
    peso_inicial DECIMAL(6,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuario_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
