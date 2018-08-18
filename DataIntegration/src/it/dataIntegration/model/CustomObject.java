package it.dataIntegration.model;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;

public class CustomObject {

	    private RDFNode nodeValue;
	    private Model modelValue;

	    public CustomObject(RDFNode nodeValue1, Model modelValue1) {
	    	super();
	        this.nodeValue = nodeValue1;
	        this.modelValue = modelValue1;
	    }
	    
	    public RDFNode getNode() {
			return nodeValue;
		}

		public Model getModel() {
			return modelValue;
		}
}
