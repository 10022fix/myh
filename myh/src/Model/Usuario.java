package Model;
import java.time.LocalDate;

public class Usuario {
	// Tabela Usuário
    private int id;
    private String nome;
    private String apelidoAcesso;
    private String email;
    private String senha;
    private LocalDate dataNascimento;
    private String sexo;
    private float altura;
    private float peso_inicial;
    // Tabela Objetivos
    private String objetivo;
    private float peso_meta;
    private LocalDate data_meta;
    // Tabela imc
    private float resultado;
    private String classificacao;
    private LocalDate data_registro;
    // Tabela Progressao
    private float peso_atual;
    private float percentual_gordura;
    private int medidasC_id;
   
    public Usuario(){

    }
    public int getId(){
        return id;
    }
    public void setId(int id){
        this.id=id;
    }
    public String getNome(){
        return nome;
    }
    public void setNome(String nome){
        this.nome=nome;
    }
    public String getApelidoAcesso(){
        return apelidoAcesso;
    }
    public void setApelidoAcesso(String apelidoAcesso){
        this.apelidoAcesso=apelidoAcesso;
    }
    public String getSenha(){
        return senha;
    }
    public void setSenha(String senha){
        this.senha=senha;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

	public LocalDate getDataNascimento() {
		return dataNascimento;
	}
	public void setDataNascimento(LocalDate dataNascimento) {
		this.dataNascimento = dataNascimento;
	}
	public String getSexo() {
		return sexo;
	}
	public void setSexo(String sexo) {
		this.sexo = sexo;
	}
	public float getAltura() {
		return altura;
	}
	public void setAltura(float altura) {
		this.altura = altura;
	}
	public float getPeso_inicial() {
		return peso_inicial;
	}
	public void setPeso_inicial(float peso_inicial) {
		this.peso_inicial = peso_inicial;
	}
    public String toString(){
        return id + " - " + nome + " - " + email;
    }
	public String getObjetivo() {
		return objetivo;
	}
	public void setObjetivo(String objetivo) {
		this.objetivo = objetivo;
	}
	public float getPeso_meta() {
		return peso_meta;
	}
	public void setPeso_meta(float peso_meta) {
		this.peso_meta = peso_meta;
	}
	public LocalDate getData_meta() {
		return data_meta;
	}
	public void setData_meta(LocalDate data_meta) {
		this.data_meta = data_meta;
	}
	public float getResultado() {
		return resultado;
	}
	public void setResultado(float resultado) {
		this.resultado = resultado;
	}
	public String getClassificacao() {
		return classificacao;
	}
	public void setClassificacao(String classificacao) {
		this.classificacao = classificacao;
	}
	public LocalDate getData_registro() {
		return data_registro;
	}
	public void setData_registro(LocalDate data_registro) {
		this.data_registro = data_registro;
	}
	public float getPeso_atual() {
		return peso_atual;
	}
	public void setPeso_atual(float peso_atual) {
		this.peso_atual = peso_atual;
	}
	public float getPercentual_gordura() {
		return percentual_gordura;
	}
	public void setPercentual_gordura(float percentual_gordura) {
		this.percentual_gordura = percentual_gordura;
	}
	public int getMedidasC_id() {
		return medidasC_id;
	}
	public void setMedidasC_id(int medidasC_id) {
		this.medidasC_id = medidasC_id;
	}
}
