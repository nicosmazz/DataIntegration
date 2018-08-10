package it.dataIntegration.model;
/*
 * classe utilizzata per modella uri dbpedia estratti dalle notizie
 */
public class DbpediaObject {
	
	private String nome;
	private String uriDbpedia;
	
	public DbpediaObject(String nome, String uriDbpedia) {
		super();
		this.nome = nome;
		this.uriDbpedia = uriDbpedia;
	}

	public String getNome() {
		return nome;
	}

	public String getUriDbpedia() {
		return uriDbpedia;
	}
	
	
	

}
