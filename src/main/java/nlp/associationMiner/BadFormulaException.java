package nlp.associationMiner;

public class BadFormulaException extends Exception {
	  public static final long serialVersionUID = 86586128316354597L;
	  String message;
	  public BadFormulaException(String message) { this.message = message; }
	  @Override
	  public String toString() { return message; }
	}
