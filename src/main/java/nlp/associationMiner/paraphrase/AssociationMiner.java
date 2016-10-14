package nlp.associationMiner.paraphrase;

import nlp.associationMiner.fig.*;
import nlp.associationMiner.*;
import java.io.IOException;

//import nlp.associationMiner.paraphrase.Aligner.Alignment;

public class AssociationMiner {
		
	public static void main(String[] args) throws IOException {
       System.out.print("hello");
       ParaphraseExample paraExample =new ParaphraseExample("what type of music did richard wagner play ?",
    	        "what is the musical genres of richard wagner ?",new BooleanValue(true));
	  }
//	  Aligner aligner = new Aligner(); // static?  getSingleton	    
//	  Params params = new Params();
//	 // params.read("/Users/jonathanberant/Research/temp/params");
//	  Alignment alignment = aligner.align(paraExample, params);
//	  Alignment alignment = aligner.align(paraExample, params);
}
