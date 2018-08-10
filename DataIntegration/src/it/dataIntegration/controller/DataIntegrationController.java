package it.dataIntegration.controller;

import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import javax.swing.JDialog;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.StmtIterator;

import it.dataIntegration.model.DbpediaObject;
import it.dataIntegration.utility.ProcessCpuLoad;
import it.dataIntegration.utility.RestServices;
import it.dataIntegration.utility.SparqlQuery;
import it.dataIntegration.view.DataIntegrationPanel;
import it.dataIntegration.view.PleaseWaitPanel;
import it.dataIntegration.view.ResultPanel;

public class DataIntegrationController {
	private Model modelFirstUri;
	private Model modelSecondUri;
	private int iteration = 1;
	private DataIntegrationPanel view;
	private ArrayList<String> matches = new ArrayList<String>();
	private Instant start;
	private Instant end;
	private boolean monitorThread = true;

	public DataIntegrationController(final DataIntegrationPanel view, Frame frame) {
		this.view = view;

		/* Thread utilizzato in fase di test per comparare
		 * le prestazioni della ricerca in profondità
		 * e della ricerca in ampienza
		 */
		
		Thread thread = new Thread(new Runnable() {
			public void run() {
				while (monitorThread) {
					try {
						ProcessCpuLoad.getProcessCpuLoad();
						Thread.sleep(500);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}

		});
		thread.start();

		view.getBtnCerca().addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				
				JDialog dialog = new JDialog(frame, "Please Wait...", true);
				PleaseWaitPanel panelPleaseWait = new PleaseWaitPanel();
				dialog.getContentPane().add(panelPleaseWait);
				dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
				dialog.pack();
				dialog.setSize(350, 150);
				dialog.setMinimumSize(dialog.getSize());
				dialog.setMaximumSize(dialog.getSize());
				dialog.setLocationRelativeTo(frame);
				
				Thread thread = new Thread(new Runnable() {
					public void run() {
						start = Instant.now();
						String urlNotizia = view.getTxtUrl().getText();
						String urlNotizia2 = view.getTxtUrl2().getText();
						// Lista di Uri dbpedia estratti dalla prima notizia
						ArrayList<DbpediaObject> list = RestServices.getRequest(urlNotizia);
						// Lista di Uri dbpedia estratti dalla seconda notizia
						ArrayList<DbpediaObject> list2 = RestServices.getRequest(urlNotizia2);
						// cerco tutte le triple che hanno come soggetto/oggetto i vari uri estratti
						// dalla prima notizia
						modelFirstUri = SparqlQuery.QuerySparql(list);
						// cerco tutte le triple che hanno come soggetto/oggetto i vari uri estratti
						// dalla seconda notizia
						modelSecondUri = SparqlQuery.QuerySparql(list2);
						// faccio un check sui due modelli ottenuti al fine di constatare un'eventuale
						// intersezione
						boolean match = searchMatch();
						writeResult(match);
						if (!match) {
							ricercaInProfondità();
						}
						dialog.dispose();
					}
				});
				thread.start();
				dialog.setVisible(true);
			}
		});
	}
	
	/* il metodo searchMatch() se tra i subject e gli object presenti nel primo modello
	 * è presente un match con un qualche altro subject o object presente nel secondo modello 
	 */
	private boolean searchMatch() {
		//estraggo tutti subject degli statements del primo modello
		ArrayList<Resource> resources = (ArrayList<Resource>) modelFirstUri.listSubjects().toList();
		boolean match = false;
		for (int i = 0; i < resources.size(); i++) {
			/*verifico se uno di questi subject è presente in qualche tripla del secondo modello, sia come subject sia come object 
			 * valuto solo letterali o uri di tipo resource
			 */
			if (resources.get(i).toString().matches("http://it.dbpedia.org/resource/(.*)")
					|| resources.get(i).isLiteral()) {
				SimpleSelector selectSub = new SimpleSelector(resources.get(i), (Property) null, (RDFNode) null);
				StmtIterator iter = modelSecondUri.listStatements(selectSub);
				SimpleSelector selectObj = new SimpleSelector((Resource) null, (Property) null,
						(RDFNode) resources.get(i));
				StmtIterator iter2 = modelSecondUri.listStatements(selectObj);
				if (iter.hasNext() || iter2.hasNext()) {
					match = true;
					matches.add(resources.get(i).toString());
				}
			}
		}
		/* ripeto lo stesso procidemento del ciclo precednete, lavorando però questa volta con 
		 * gli object estratti dagli statements del primo modello
		 */
		ArrayList<RDFNode> nodes = (ArrayList<RDFNode>) modelFirstUri.listObjects().toList();
		for (int j = 0; j < nodes.size(); j++) {
			if (nodes.get(j).toString().matches("http://it.dbpedia.org/resource/(.*)")) {
				SimpleSelector selectSbj = new SimpleSelector((Resource) nodes.get(j), (Property) null, (RDFNode) null);
				SimpleSelector selectObj = new SimpleSelector((Resource) null, (Property) null, (RDFNode) nodes.get(j));
				StmtIterator iter = modelSecondUri.listStatements(selectSbj);
				StmtIterator iter2 = modelSecondUri.listStatements(selectObj);
				if (iter.hasNext() || iter2.hasNext()) {
					match = true;
					matches.add(nodes.get(j).toString());
				}
			} else if (nodes.get(j).isLiteral()) {
				SimpleSelector selectObj = new SimpleSelector((Resource) null, (Property) null, (RDFNode) nodes.get(j));
				StmtIterator iter = modelSecondUri.listStatements(selectObj);
				if (iter.hasNext()) {
					match = true;
					matches.add(nodes.get(j).toString());
				}
			}
		}
		return match;
	}

	 

	 /*
	  * Nel caso in cui non sia presente alcun match tra i due modelli iniziali
	  * proseguo espandendo in profondintà gli object del primo modello.
	  */
	
	private void ricercaInProfondità() {
		boolean match = false;
		//Prendo tutti gli object del primo modello
		NodeIterator iter = modelFirstUri.listObjects();
		ArrayList<RDFNode> resources = (ArrayList<RDFNode>) iter.toList();
		/*itero sulla lista di Objects estratti per e di volta in volta definisco un nuovo modello contenente
		 * tutte le triple che hanno come subject o object l'object della lista
		 */

		for (int i = 0; i < resources.size(); i++) {
			RDFNode node = resources.get(i);
			Model newModel;
			if (node.isLiteral()) {
				newModel = SparqlQuery.QuerySparql(node.toString(), true);
			} else {
				newModel = SparqlQuery.QuerySparql(node.toString(), false);
			}
			/* a questo punto sovrascrivo il precendente modello con l'attuale per poi verificare se sono presenti
			 * dei match con i nuovi subject e object estratti
			 */
			modelFirstUri = newModel;
			match = searchMatch();
			writeResult(match);
			//se non sono stati trovati match espando una sola volta gli object del modello creato sopra
			if (!match) {
				NodeIterator iter2 = newModel.listObjects();
				ArrayList<RDFNode> resources2 = (ArrayList<RDFNode>) iter2.toList();
				for (int j = 0; j < resources2.size(); j++) {
					RDFNode node2 = resources.get(i);
					Model newModel2;
					if (node2.isLiteral()) {
						// devo cercare tutte le triple cha hanno node come uri
						newModel2 = SparqlQuery.QuerySparql(node2.toString(), true);
					} else {
						// devo cercare tutte le triple cha hanno node come uri
						newModel2 = SparqlQuery.QuerySparql(node2.toString(), false);
					}
					// quindi cerco di nuovi dei match
					modelFirstUri = newModel2;
					match = searchMatch();
					writeResult(match);
					if (match) {
						// se trovo un match interrompo l'iterazione
						break;
					}
				}
			}
			// se trovo un match interrompo l'iterazione
			if (match) {
				break;
			}
		/* Giunti qui, se non si è trovato alcun match
		 * si prosegue espandendo gli Object estratti inizialmente (quelli del primo ciclo for) 
		 */
		}
	}
	
	/*
	 * Questo metodo mostra a schermo eventuali match trovati durante le varie iterazioni dell'algoritmo di ricerca
	 */
	private void writeResult(boolean match) {
		ResultPanel resPanel = new ResultPanel();
		JTextArea txtArea = resPanel.getTextArea();
		if (!match) {
			txtArea.append("Risultato iterazione n°: " + iteration);
			txtArea.append(System.lineSeparator() + "Non è stato trovato alcun match tra le due notizie");
			iteration++;
		} else {
			end = Instant.now();
			monitorThread = false;
			Duration d = Duration.between(start, end);
			txtArea.setFont(new Font("Lucida Grande", Font.BOLD, 14));
			txtArea.append("Risultato iterazione n°: " + iteration);
			txtArea.append(System.lineSeparator() + "Durante l'iterazione " + iteration
					+ " sono stati trovati match per i seguenti letterali e/o uri:" + System.lineSeparator());
			txtArea.append(System.lineSeparator());
			for (int i = 0; i < matches.size(); i++) {
				txtArea.append(matches.get(i) + System.lineSeparator());
			}
			txtArea.append(System.lineSeparator());
			txtArea.append("Tempo trascorso durante la ricerca: " + d.toMinutes() + " minuti "
					+ d.minusMinutes(d.toMinutes()).getSeconds() + " secondi.");
			txtArea.append(System.lineSeparator());
			txtArea.append(System.lineSeparator() + "Utilizzo massimo della cpu per la JVM: "
					+ ProcessCpuLoad.getMaxProcessLoad() + "%");
			txtArea.append(System.lineSeparator() + "Utilizzo medio della cpu per la JVM: "
					+ ProcessCpuLoad.getAverageProcessLoad() + "%");
		}
		view.getPanelBox().add(txtArea);
		view.getPanelBox().revalidate();
	}

}
