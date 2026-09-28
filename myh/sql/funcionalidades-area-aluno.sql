-- Execute este complemento apos criar as tabelas do banco principal.
-- A tabela medidasC precisa existir antes de progressao, pois progressao a referencia.
-- Se o CREATE TABLE progressao do script principal falhar por essa ordem, execute-o novamente apos criar medidasC.

CREATE INDEX idx_usuario_treinos_ativo ON usuario_treinos (usuario_id, ativo);
CREATE INDEX idx_objetivos_usuario ON objetivos (usuario_id);
CREATE INDEX idx_progressao_usuario_data ON progressao (usuario_id, data_registro);
