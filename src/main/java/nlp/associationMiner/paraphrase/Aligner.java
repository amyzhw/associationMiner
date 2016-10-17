package nlp.associationMiner.paraphrase;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.Pair;
import nlp.associationMiner.BooleanValue;
import nlp.associationMiner.FeatureVector;
import nlp.associationMiner.Formula;
import nlp.associationMiner.LanguageInfo;
import nlp.associationMiner.LanguageInfo.WordInfo;
import nlp.associationMiner.Params;
import nlp.associationMiner.LanguageInfo.LanguageUtils;
import nlp.associationMiner.fbalignment.lexicons.LexicalEntry.BinaryLexicalEntry;
import nlp.associationMiner.fbalignment.utils.WnExpander;
import nlp.associationMiner.paraphrase.paralex.PhraseTable;
import nlp.associationMiner.paraphrase.rules.RuleApplication;
import nlp.associationMiner.paraphrase.rules.RuleApplier;
import nlp.associationMiner.paraphrase.rules.Rulebase;
import nlp.associationMiner.fig.IntPair;
import nlp.associationMiner.fig.LispTree;
import nlp.associationMiner.fig.LogInfo;
import nlp.associationMiner.fig.Option;


/**
 * Exhaustive alignment between two paraphrases
 */
public class Aligner {


  public static class Options {
    @Option(gloss="Path to file with derivations") public String derivationPath="lib/derivation.txt";
    @Option(gloss="Maximum distortion") public double distortionParam=1;
    @Option(gloss="Whether to use syntax rules") public boolean useSyntax=false; // wei: pops errors when changing to true
    @Option(gloss="Whether to use wordnet synsets") public boolean useWordnet=true;
    @Option(gloss="Whether to write feature and values into files") public boolean feaValTofile=false; // wei add
    @Option(gloss="Path to file with feature and values") public String feaValPath="lib/phrasePairs/features"; // wei add 
    @Option public int verbose=0;
  }
  public static Options opts = new Options();
  private static Aligner aligner;//
  

 private final PhraseTable phraseTable; 
  private Rulebase ruleBase;
  private WnExpander wnExpander;
  private final Map<String,Set<String>> derivations;
  public PrintWriter writer;

 private Aligner() throws IOException {
	  derivations = ParaphraseUtils.loadDerivations(opts.derivationPath);
     phraseTable = PhraseTable.getSingleton();  
    wnExpander = new WnExpander();
    if(opts.useSyntax)
      ruleBase = new Rulebase();
    
    if (opts.feaValTofile)
    	writer = new PrintWriter(opts.feaValPath, "UTF-8"); 
    
  }

  public static Aligner getSingleton() throws IOException {
    if(aligner==null) 
      aligner = new Aligner();
    return aligner;
  }

  //wei: the focus
  public Alignment align(ParaphraseExample example, Params params) {
    example.ensureAnnotated(); // wei:  inside, build language info
   // example.log(); //wei
    Alignment alignment = new Alignment(example);
    alignment.buildAlignment(example, params); 
    example.setAlignment(alignment);
    return alignment;
  }
  


  public class Alignment {

    private List<AlignmentIntervalPair> substitutions = new ArrayList<Aligner.AlignmentIntervalPair>();
    private List<AlignmentInterval> deletions = new ArrayList<Aligner.AlignmentInterval>();
    FeatureVector featureVector = new FeatureVector();

    double score = Double.NaN;
    private final String source;
    private final String target;

    public Alignment(ParaphraseExample example) {
      source = example.source;
      target = example.target; 
    }

    
    // wei add
    public double [] buildFeatureVector(Params params){
    	 double [] array = new double[params.getFeatureLength()];

    	 Map<String, Double> featureMap = featureVector.toMap();
         
         for(String key: featureMap.keySet()) {
              double value = featureMap.get(key);
             // check if the feature in params.features
              int idx = params.checkFeature(key);
              if (idx!=-1) // found
            	  array[idx]=value;
                       
        //   if (bPrint)
         //    LogInfo.logs("Printing features: feautre=%s, value=%s\n",key,value);
              
         }   // for key
    	 
    	 return array;
    }
    
    
    public void buildAlignment(ParaphraseExample example, Params params) {

      computeIdentityAlignments(example);         // wei: for lemma(token_src)=lemma(token_tgt)
      computePhraseTableAlignments(example); // wei: I think I don't need this coz I don't have phrase table. instead, i want to build one.
      computeSubstitutionsAlignment(example);  // wei: considers spans, not just tokens. include cannocial, wordnet...
      computeDerivationsAlignment(example); // wei: wordnet derivations
      //this needs to be done last
       markDeletions(example);
      if(opts.useSyntax)
        computeSyntacticAlignment(example);
      if(opts.verbose>=1) {
        printFeaturesAndWeights(params);
      }
      // score = featureVector.dotProduct(params); //wei del  the score of question pairs.
     // LogInfo.logs("score=%s",score);//wei
    }
    
  //wei add compute score (association feature value)
    public double computeScore(Params params) {  //wei: params contains the learned weights
         return  featureVector.dotProduct(params); 	 
      }

    private void printFeaturesAndWeights(Params params) {  //wei: params contains the learned weights
      for(String key: featureVector.toMap().keySet()) {
        double value = featureVector.toMap().get(key);
        LogInfo.logs("Printing features: feautre=%s\n, value=%s, weight=%s,product=\t%s",key,value,
            params.getWeight(key),value * params.getWeight(key));
      }   
    }
    
    
    
   
    private void computeSyntacticAlignment(ParaphraseExample example) {
      for(RuleApplier rule: ruleBase.getRules()) {
        for(RuleApplication application: rule.apply(example.sourceInfo, example.targetInfo)) {
          List<WordInfo> wordInfos = new ArrayList<WordInfo>();
          for(int i = 0; i < application.consequent.numTokens(); ++i)
            wordInfos.add(application.consequent.getWordInfo(i));
          if(example.targetInfo.matchLemmas(wordInfos)) {
            featureVector.add("SyntAlign",rule.toString());
          }
        }
      }
    }

    private void computeDerivationsAlignment(ParaphraseExample example) {
      for(int i = 0 ; i < example.sourceInfo.numTokens(); ++i) {
        for(int j = 0; j < example.targetInfo.numTokens(); ++j) {
          String sourceLemma = example.sourceInfo.lemmaTokens.get(i);
          String targetLemma = example.targetInfo.lemmaTokens.get(j);
          if(!sourceLemma.equals(targetLemma)) {
            if(derivations.containsKey(sourceLemma) &&
                derivations.get(sourceLemma).contains(targetLemma)) {
              AlignmentInterval sourceInterval = 
                  new AlignmentInterval(sourceLemma, new Interval(i, i+1));
              AlignmentInterval targetInterval = 
                  new AlignmentInterval(targetLemma, new Interval(j, j+1));
              substitutions.add(new AlignmentIntervalPair(sourceInterval, targetInterval));
              featureVector.add("Subst", "Deriv");
              featureVector.add("Subst", "l="+sourceLemma+",r="+targetLemma);
            }
          }
        }
      }
    }

    private void markDeletions(ParaphraseExample example) {
     markDeletion(example.sourceInfo,true);
     markDeletion(example.targetInfo,false);
    }

   private void markDeletion(LanguageInfo lInfo, boolean isSource) {

      for(int i = 0; i < lInfo.numTokens(); ++i) {
        boolean aligned=false;
        for(AlignmentIntervalPair alignmentIntervalPair: substitutions) {
          AlignmentInterval alignmentInterval = isSource ? alignmentIntervalPair.sourceInterval
              : alignmentIntervalPair.targetInterval;
          if(alignmentInterval.interval.contains(i)) {
            aligned=true;
            break;
          }
        }
        if(!aligned) {
          AlignmentInterval deletedToken = new AlignmentInterval(lInfo.lemmaTokens.get(i), new Interval(i, i+1));
          deletions.add(deletedToken);
          if(ParaphraseFeatureMatcher.containsDomain("Del")) {
            featureVector.add("Del", "lemma="+deletedToken.phrase);
            featureVector.add("Del", "pos="+LanguageUtils.getCanonicalPos(lInfo.posTags.get(i)));
          }
        }
      }  
    }  

    // for lemma(token_src)=lemma(token_tgt)
    private void computeIdentityAlignments(ParaphraseExample example) {
    	
      for(int i = 0 ; i < example.sourceInfo.numTokens(); ++i) {
        for(int j = 0; j < example.targetInfo.numTokens(); ++j) {
        	
          if(example.sourceInfo.lemmaTokens.get(i).equals(example.targetInfo.lemmaTokens.get(j))) {

            AlignmentInterval sourceInterval = 
                new AlignmentInterval(example.sourceInfo.lemmaTokens.get(i), new Interval(i, i+1));
            AlignmentInterval targetInterval = 
                new AlignmentInterval(example.targetInfo.lemmaTokens.get(j), new Interval(j, j+1));
            
            substitutions.add(new AlignmentIntervalPair(sourceInterval, targetInterval));            
            featureVector.add("Subst", "Identity", 1);
          } // end if
          
        } // for j
      } // for i
      
    }

    /**
     * We consider all tokens and phrases with two nouns and allow substitutions if canonical pos matches.
     * @param example
     */
   private void computeSubstitutionsAlignment(ParaphraseExample example) {

      List<Interval> sourceIntervals = getLanguageInfoCandidates(example.sourceInfo);
      List<Interval> targetIntervals = getLanguageInfoCandidates(example.targetInfo);
      for(Interval sourceInterval: sourceIntervals) {
        for(Interval targetInterval: targetIntervals) {
          String sourcePhrase = example.sourceInfo.lemmaPhrase(sourceInterval.start, sourceInterval.end);
          String targetPhrase = example.targetInfo.lemmaPhrase(targetInterval.start, targetInterval.end);
          if(!sourcePhrase.equals(targetPhrase) &&
              ParaphraseUtils.posCompatible(example.sourceInfo.posTags.get(sourceInterval.start),
                      example.targetInfo.posTags.get(targetInterval.start))) {

            AlignmentInterval sourceAlignmentInterval = new AlignmentInterval(sourcePhrase, sourceInterval);
            AlignmentInterval targetAlignmentInterval = new AlignmentInterval(targetPhrase, targetInterval);
            if(validDistortion(sourceAlignmentInterval, example.sourceInfo,
                targetAlignmentInterval,example.targetInfo)) {
              substitutions.add(new AlignmentIntervalPair(sourceAlignmentInterval, targetAlignmentInterval));
              //features
              String lPos = example.sourceInfo.canonicalPosSeq(sourceInterval.start, sourceInterval.end);
              String rPos = example.targetInfo.posSeq(targetInterval.start, targetInterval.end);
              if(!lPos.equals(rPos))
                featureVector.add("Subst", "l_pos="+lPos+",r_pos="+rPos);
              else
                featureVector.add("Subst", "pos_identity");
              featureVector.add("Subst", "l="+sourcePhrase+",r="+targetPhrase);
              if(opts.useWordnet) {
                if(wnExpander.getSynonyms(sourcePhrase).contains(targetPhrase))
                 featureVector.add("Subst", "synonym");
             }
            } // end if validDistortion
          } // end if
          
        }
      }
      
    }

    private boolean validDistortion(AlignmentInterval sourceAlignmentInterval,
        LanguageInfo sourceInfo, AlignmentInterval targetAlignmentInterval, LanguageInfo targetInfo) {

      double sourcePosition = sourceAlignmentInterval.interval.middle() / sourceInfo.numTokens();
      double targetPosition = targetAlignmentInterval.interval.middle() / targetInfo.numTokens();
      int min = Math.min(sourceInfo.numTokens(), targetInfo.numTokens());
      return (Math.abs(sourcePosition-targetPosition)<Math.max(opts.distortionParam,
          (double) 2*(1/min)));

    }

    //wei: originally only considers the phrase with two nouns
    //        I add phrase with verb+noun( NN+VB)  and noun+verb (VB+NN)
    //       or particle   RP
    private List<Interval> getLanguageInfoCandidates(LanguageInfo lInfo) {
      List<Interval> res = new ArrayList<Interval>();
      for(int i = 0; i < lInfo.numTokens(); ++i) {
        res.add(new Interval(i, i+1));
        if(i < lInfo.numTokens()-1 &&
            (( LanguageUtils.isNN(lInfo.posTags.get(i)) &&
            LanguageUtils.isNN(lInfo.posTags.get(i+1))) 
           || (LanguageUtils.isNN(lInfo.posTags.get(i)) &&
            LanguageUtils.isVerb(lInfo.posTags.get(i+1)))
           || (LanguageUtils.isVerb(lInfo.posTags.get(i)) &&
            LanguageUtils.isNN(lInfo.posTags.get(i+1)))            
            ) )
        {
          res.add(new Interval(i,i+2));
        }
         
      }
      return res;
    }

  private  void computePhraseTableAlignments(ParaphraseExample example) {
      //go over all lhs spans
      for(int i = 0; i < example.sourceInfo.numTokens(); ++i) {
        for(int j = i+1; j <= example.sourceInfo.numTokens(); ++j) {
          String lhsPhrase = example.sourceInfo.lemmaPhrase(i, j);
          if(phraseTable.containsKey(lhsPhrase)) {
            Map<String,AlignmentStats> rhsCandidates = phraseTable.get(lhsPhrase);
            //get rhs
            Map<String,IntPair> rhsLemmaSpans = example.targetInfo.getLemmaSpans();
            for(String rhsLemmaSpan: rhsLemmaSpans.keySet()) {
              if(rhsCandidates.containsKey(rhsLemmaSpan)) {
                int targetStart = rhsLemmaSpans.get(rhsLemmaSpan).first;
                int targetEnd = rhsLemmaSpans.get(rhsLemmaSpan).second;
                AlignmentStats aStats = rhsCandidates.get(rhsLemmaSpan);

                //now we have the source phrase the target phrase and the intervals
                AlignmentInterval sourceInterval = new AlignmentInterval(lhsPhrase, new Interval(i, j));
                AlignmentInterval targetInterval = new AlignmentInterval(rhsLemmaSpan, new Interval(targetStart, targetEnd));
                if(validDistortion(sourceInterval, example.sourceInfo,
                    targetInterval,example.targetInfo)) {
                  substitutions.add(new AlignmentIntervalPair(sourceInterval, targetInterval));
                  //features
                  featureVector.add("Subst", "l="+lhsPhrase+",r="+rhsLemmaSpan);
                  if(ParaphraseFeatureMatcher.containsDomain("Pt")) {
                    featureVector.add("Pt","cooccurCount="+binCount(aStats.cooccurrenceCount));
                    featureVector.add("Pt","phraseCount="+binCount(aStats.phrase1Count));
                    featureVector.add("Pt","phraseCount="+binCount(aStats.phrase2Count));
                  }
                  String lPos = example.sourceInfo.canonicalPosSeq(i, j);
                  String rPos = example.targetInfo.canonicalPosSeq(targetStart, targetEnd);
                  if(lPos.equals(rPos))
                    featureVector.add("Subst","pos_identity");
                  else
                    featureVector.add("Subst", "l_pos="+lPos+",r_pos="+rPos);
                }
              }
            }
          }
        }
      }
    }  

    private String binCount(double count) {
      if(count<=50)
        return "<=50";
      if(count<=100)
        return "<=100";
      if(count<=500)
        return "<=500";
      if(count<=1000)
        return "<=1000";
      if(count<=5000)
        return "<=5000";
      if(count<=10000)
        return "<=10000";
      if(count<=50000)
        return "<=50000";
      if(count<=100000)
        return "<=100000";
      if(count<=1000000)
        return "<=1000000";
      return ">1000000";
    }

    public void clear() {
      featureVector.clear();
      substitutions.clear();
      deletions.clear();
    }

    public LispTree toLispTree() {
      LispTree tree = LispTree.proto.newList();
      tree.addChild("alignment");
      tree.addChild(LispTree.proto.newList("source", ""+source));
      tree.addChild(LispTree.proto.newList("target", ""+target));
      tree.addChild(LispTree.proto.newList("alignment_score", ""+score));
      return tree;
    }

    public String toString() {
      StringBuilder sb = new StringBuilder();
      for(String key: featureVector.toMap().keySet()) {
        sb.append(key+"\t"+featureVector.toMap().get(key)+"\n");
      }
      return sb.toString();
    }
  }

  public class AlignmentInterval {
    public final String phrase;
    public final Interval interval;
    public AlignmentInterval(String phrase, Interval interval) {
      super();
      this.phrase = phrase;
      this.interval = interval;
    }
    public String toString() {
      return phrase+","+interval;
    }
  }
  public class AlignmentIntervalPair {
    public final AlignmentInterval sourceInterval;
    public final AlignmentInterval targetInterval;
    public AlignmentIntervalPair(AlignmentInterval sourceInterval,
        AlignmentInterval targetInterval) {
      super();
      this.sourceInterval = sourceInterval;
      this.targetInterval = targetInterval;
    }
    public String toString() {
      return sourceInterval+"\t"+targetInterval;
    }
  }

  public static class AlignmentStats {
    public final double cooccurrenceCount;
    public final double phrase1Count;
    public final double phrase2Count;

    public AlignmentStats(double cooccurrence, double phrase1Freq, double phrase2Freq) {
      this.cooccurrenceCount=cooccurrence; //wei: phrase-table.counts column meaning.
      this.phrase1Count=phrase1Freq;
      this.phrase2Count=phrase2Freq;
    }
  }
  
  // wei add
  private void recordFeatures(FeatureVector featureVector,PrintWriter fv_writter,boolean bPrint ) { 
	    
	 // HashMap<String, Double> featureValueMap = new HashMap<String, Double>(); 
	   
	  Map<String, Double> featureMap = featureVector.toMap();
      int count=0;
      
      for(String key: featureMap.keySet()) {
        double value = featureMap.get(key);
        //featureValueMap.put(key, value);         
        
        if(opts.feaValTofile){
      	  fv_writter.println(key + '\t' + value); 
        }
        //bPrint=true;
        if (bPrint)
          LogInfo.logs("Printing features: feautre=%s, value=%s\n",key,value);
        
        count=count+1;
         
        if ((count  %  3000)==0)
             System.out.println(count + " features are recorded" );    
        
      }   // for key
            
    }

  

  public static void main(String[] args) throws IOException, InterruptedException {
	// examples.
  //  ParaphraseExample paraExample =new ParaphraseExample("what type of music did richard wagner play ?",
   //     "what is the musical genres of richard wagner ?",new BooleanValue(true));
    Aligner aligner = new Aligner();
    Params params = new Params();
    //params.read("/Users/jonathanberant/Research/temp/params"); // wei: we skip all params which contains the learned weights of each features.
    //Alignment alignment = aligner.align(paraExample, params); 
    // alignment.printFeaturesAndWeights(params); 

  /* //********************1. read pairs and create paraExample for each pair
    FeatureVector featureVectorAll = new FeatureVector();
        
   try {
			//File fileDir = new File("C:\\wamp64\\tmp\\allDupPairs");
			File fileDir = new File("C:\\wamp64\\tmp\\java\\5000\\master_dup_titles");
			BufferedReader in = new BufferedReader(new InputStreamReader( new FileInputStream(fileDir), "UTF8"));
	
			int cnt=0;
			String line;
			String tgtLine;
			while ((line = in.readLine()) != null) {
			  //  System.out.println(line);
			   // Pair<String, String> pair = new Pair<>(line, in.readLine());		
			   // dupPairs.add(pair);
			    
			  //********************2. get features for each pair
				tgtLine = in.readLine();
			    ParaphraseExample paraExample =new ParaphraseExample(line,tgtLine,new BooleanValue(true));
			    //System.out.println("Source question: "+ line);
			    //System.out.println("Target question: "+ tgtLine);
			    Alignment alignment = aligner.align(paraExample, params); 

			    featureVectorAll.add(alignment.featureVector);
			    
			    cnt=cnt+1;
			    if ( (cnt % 1000)==0 )
			    	System.out.println(cnt + " question pairs are processed.");
			}
			
			aligner.recordFeatures(featureVectorAll,aligner.writer,false);
			
	       in.close();
	       aligner.writer.close();
	    }
	   catch (UnsupportedEncodingException e)
	   {
			System.out.println(e.getMessage());
       }
	    catch (IOException e)
	    {
			System.out.println(e.getMessage());
	    }
	    catch (Exception e)
	    {
			System.out.println(e.getMessage());
	    }
  
   
   //******************** Test 2. generate features for 5000 (4:1 train and test) and  5000 non-dup pairs 
    // 2.1 load feature file 
    params.readFeatures(opts.feaValPath);   
    
   // File fileDir = new File("C:\\wamp64\\tmp\\java\\5000\\dup_pair_titles");
    File fileDir = new File("C:\\wamp64\\tmp\\java\\5000\\nondup_pair_titles");
    
    // print out to feature file
    //PrintWriter featwriter = new PrintWriter("C:\\wamp64\\tmp\\java\\5000\\dup_pair_titles.longfeat", "UTF-8"); 
    PrintWriter featwriter = new PrintWriter("C:\\wamp64\\tmp\\java\\5000\\nondup_pair_titles.longfeat", "UTF-8"); 
    try {
		
			BufferedReader in = new BufferedReader(new InputStreamReader( new FileInputStream(fileDir), "UTF8"));
	
			int cnt=0;
			String line;
			String tgtLine;
			while ((line = in.readLine()) != null) {
			    
			  //********************2.2 get features for each pair
				tgtLine = in.readLine();
			    ParaphraseExample paraExample =new ParaphraseExample(line,tgtLine,new BooleanValue(true));
			    //System.out.println("Source question: "+ line);
			    //System.out.println("Target question: "+ tgtLine);
			    Alignment alignment = aligner.align(paraExample, params); 
			    
			    double [] myarray;
			    //System.out.println(myarray[1000]);			    
			     myarray=alignment.buildFeatureVector(params); // on alignment.featureVector
			     
			     for (int i=0; i< myarray.length; i++){
			    	 featwriter.print(myarray[i]); 
			    	 featwriter.print(" "); 
			     }
			     featwriter.print("\n"); 

			    // Thread.sleep(50000);			    
			    cnt=cnt+1;
			    if ( (cnt % 1000)==0 )
			    	System.out.println(cnt + " question pairs are processed.");
			}
			
	       in.close();
	       featwriter.close();
	    }
	   catch (UnsupportedEncodingException e)
	   {
			System.out.println(e.getMessage());
       }
	    catch (IOException e)
	    {
			System.out.println(e.getMessage());
	    }
	    catch (Exception e)
	    {
			System.out.println(e.getMessage());
	    }
   */

   
   //******************** Test 3. compute scores (association feature) for 5000 (4:1 train and test) and 5000 non-dup pairs 
     // 3.1 load feature file 
     params.read("lib/phrasePairs/feature_weights.percpt"); // contain weights of features
     params.readFeatures(opts.feaValPath);   
       
     //File fileDir = new File("C:\\wamp64\\tmp\\java\\5000\\dup_pair_titles");
     File fileDir = new File("C:\\wamp64\\tmp\\java\\5000\\nondup_pair_titles");
     
     // print out to association feature file
     //PrintWriter featwriter = new PrintWriter("C:\\wamp64\\tmp\\java\\5000\\dup_pair_titles.assfeat", "UTF-8"); 
     PrintWriter featwriter = new PrintWriter("C:\\wamp64\\tmp\\java\\5000\\nondup_pair_titles.assfeat", "UTF-8"); 
     try {
 		
 			BufferedReader in = new BufferedReader(new InputStreamReader( new FileInputStream(fileDir), "UTF8"));
 	
 			int cnt=0;
 			String line;
 			String tgtLine;
 			while ((line = in.readLine()) != null) {
 			    
 			  //********************2.2 get features for each pair
 				tgtLine = in.readLine();
 			    ParaphraseExample paraExample =new ParaphraseExample(line,tgtLine,new BooleanValue(true));
 			    //System.out.println("Source question: "+ line);
 			    //System.out.println("Target question: "+ tgtLine);
 			    Alignment alignment = aligner.align(paraExample, params); 
 			    double score=    alignment.computeScore(params); 
 			   // System.out.println("score from computeScore:  "+score);
 			    
 			  // Thread.sleep(50000);	
 			   featwriter.println(score); 
 			   
 			    cnt=cnt+1;
 			    if ( (cnt % 1000)==0 )
 			    	System.out.println(cnt + " question pairs are processed.");
 			}
 			
 	       in.close();
 	       featwriter.close();
 	    }
 	   catch (UnsupportedEncodingException e)
 	   {
 			System.out.println(e.getMessage());
        }
 	    catch (IOException e)
 	    {
 			System.out.println(e.getMessage());
 	    }
 	    catch (Exception e)
 	    {
 			System.out.println(e.getMessage());
 	    }
   
    System.out.println("Done!" );   
   
  
  }  // end of main
  
  
  
}
