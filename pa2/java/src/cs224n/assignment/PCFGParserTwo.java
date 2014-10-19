package cs224n.assignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import cs224n.assignment.Grammar.BinaryRule;
import cs224n.assignment.Grammar.UnaryRule;
import cs224n.ling.Constituent;
import cs224n.ling.Tree;
import cs224n.util.Counter;
import cs224n.util.Pair;

public class PCFGParserTwo implements Parser{
	private Grammar grammar;
    private Lexicon lexicon;

  /**
   * Build a lexicon and grammar from training trees.
   * @param trainTrees list of annotated trees to train on
   */
    public void train(List<Tree<String>> trainTrees) {
      // Binarize the training trees
    	for (int i = 0; i < trainTrees.size(); i++) {
    		trainTrees.set(i, TreeAnnotations.annotateTree(trainTrees.get(i)));
    	}
      // Build lexicon and grammar
    	lexicon = new Lexicon(trainTrees);
    	grammar = new Grammar(trainTrees);
    	System.out.println(grammar);
    }
    
    public void getPretermRules(List<String> sentence,int i,Counter<Constituent<String>> scores,HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back){
    	for(String A:lexicon.tagCounter.keySet()){
    		double score = lexicon.scoreTagging(sentence.get(i), A);
    		scores.setCount(new Constituent<String>(A,i,i+1), score);
    		back.put(new Constituent<String>(A,i,i+1), new Pair<Constituent<String>,Constituent<String>>(new Constituent<String>(sentence.get(i),i,i),null));
    	}
    }
    
    
    public void handleUnaries(List<String> sentence,int begin,int end,Counter<Constituent<String>> scores,HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back,boolean onlyUnary){
    	boolean added = true;
    	while(added){
    		added = false;
    		for(String child : grammar.unaryRulesByChild.keySet()){
    			for(UnaryRule uRule : grammar.getUnaryRulesByChild(child) ){
    				Constituent<String> childConstituent = new Constituent<String>(uRule.getChild(),begin,end);
    				Constituent<String> parentConstituent = new Constituent<String>(uRule.getParent(),begin,end);   					
    				if(!onlyUnary || scores.getCount(childConstituent) > 0 ){
    					double prob = scores.getCount(childConstituent) * uRule.getScore();
    					if(prob > scores.getCount(parentConstituent)){
    						scores.setCount(parentConstituent, prob);
    						back.put(parentConstituent, new Pair<Constituent<String>,Constituent<String>>(childConstituent,null));
    					}
    				} 
    			}
    		}		
    	}
    }
    
    public void getBinaryRules(List<String> sentence,int span,int begin,Counter<Constituent<String>> scores,HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back){
    	int end = begin + span;
    	for(int split = begin + 1 ; split < end ; split ++){
    		for(String B : grammar.binaryRulesByLeftChild.keySet()){
    			for(BinaryRule bRule : grammar.getBinaryRulesByLeftChild(B)){
    				Constituent<String> bConstituent = new Constituent<String>(bRule.getLeftChild(),begin,split);
    				Constituent<String> cConstituent = new Constituent<String>(bRule.getRightChild(),split,end);
    				Constituent<String> aConstituent = new Constituent<String>(bRule.getParent(),begin,end);
        			
    				double prob = scores.getCount(bConstituent) * scores.getCount(cConstituent) ;
        			//prior on rule
        			prob *= bRule.getScore();
        			if( prob > scores.getCount(aConstituent) ){
        				scores.setCount(aConstituent, prob);
        				back.put(aConstituent,new Pair<Constituent<String>,Constituent<String>>(bConstituent,cConstituent)); 
        			}
        		}		
    		}
    	}
    }
    
    private  Constituent<String> getBestRoot(Counter<Constituent<String>> scores,int numWords){
    	double max = -1;
    	Constituent<String> bestRoot = null;
    	for(Constituent<String> constituent : scores.keySet()){
    		System.out.println("START: "+constituent.getStart() + "   END: "+ constituent.getEnd() );
    		if(scores.getCount(constituent) > max && constituent.getEnd() == numWords && constituent.getStart() == 0){
    			max = scores.getCount(constituent);
    			bestRoot = constituent;
    		}
    	}
    	System.out.println("NUM WORDS : " + numWords);
    	return bestRoot;
    }
    
    private Tree<String> merge( Tree<String> t1, Tree<String> t2,Constituent<String> currentNode){
    	List<Tree<String>> children = new ArrayList<Tree<String>>();
        if(t1 != null) children.add(t1);
        if(t2 != null) children.add(t1);
        return new Tree<String>(currentNode.getLabel(), children);
    }
    
    private Tree<String> recursiveBuildTree(Constituent<String> currentNode, HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back){
    	if(currentNode == null) return null;
    	Pair<Constituent<String>,Constituent<String>> children = back.get(currentNode);
    	//at a terminal, I hope!
    	if(children == null) return new Tree<String>(currentNode.getLabel());
    	
    	//else, not not a terminal
    	Constituent<String> child1 = children.getFirst(); 
    	Constituent<String> child2 = children.getSecond();
    	
    	Tree<String> subTree1 = recursiveBuildTree(child1,back);
    	Tree<String> subTree2 = recursiveBuildTree(child2,back);
    	return merge(subTree1,subTree2,currentNode);
    	
    }
    
    private Tree<String> buildTree(List<String> sentence,Counter<Constituent<String>> scores,HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back){
    	Constituent<String> bestRoot = getBestRoot(scores,sentence.size());
    	return recursiveBuildTree(bestRoot,back);
    }
  /**
   * Parse a sentence according to the CKY algorithm.
   * @param sentence sentence to parse
   * @return most probable parse tree of sentence
   */
    public Tree<String> getBestParse(List<String> sentence) {
    	 Counter<Constituent<String>> scores = new Counter<Constituent<String>>();	
    	 HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back = new HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>>();	
    	 
    	 //populate lowest layer of the parse tree
    	 for(int i = 0; i < sentence.size() ; i++){
    		 getPretermRules(sentence,i,scores,back);
    		 
    		 handleUnaries(sentence,i,i+1,scores,back,true);
    	 }
    	 
    	 for(int span = 2; span <= sentence.size() ; span++){
    		 for(int begin = 0; begin <= sentence.size() - span; begin++){
    			getBinaryRules(sentence,span,begin,scores,back);
    			handleUnaries(sentence,begin,begin+span,scores,back,false);
    		 }
    	 }
    	 //System.out.println("BUILDING TREE. . . ");
    	 //System.out.println(back);
    	 
    	 Tree<String> bestParse = buildTree(sentence,scores,back);
    	 System.out.println(bestParse);
    	 return bestParse;
    }
}
