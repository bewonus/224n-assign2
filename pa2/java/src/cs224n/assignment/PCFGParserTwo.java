package cs224n.assignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import cs224n.assignment.Grammar.BinaryRule;
import cs224n.assignment.Grammar.UnaryRule;
import cs224n.ling.Constituent;
import cs224n.ling.Tree;
import cs224n.util.Counter;
import cs224n.util.Pair;
import cs224n.util.Triplet;

public class PCFGParserTwo implements Parser{
	private Grammar grammar;
    private Lexicon lexicon;

  /**
   * Build a lexicon and grammar from training trees.
   * @param trainTrees list of annotated trees to train on
   */
    public void train(List<Tree<String>> trainTrees) {

      // Set the vertical and horizontal markovization orders (default is v = 1, h = -1)
      int v = 1;
      int h = -1;

      // Binarize (and markovize) the training trees
    	for (int i = 0; i < trainTrees.size(); i++) {
    		trainTrees.set(i, TreeAnnotations.annotateTree(trainTrees.get(i), v, h));
    	}
      // Build lexicon and grammar
    	lexicon = new Lexicon(trainTrees);
    	grammar = new Grammar(trainTrees);
    }
    
    public void getPretermRules(List<String> sentence,int i,HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack,HashMap<Pair<Integer,Integer>,HashSet<String>> seen){
    	for(String A:lexicon.getAllTags()){
    		double score = lexicon.scoreTagging(sentence.get(i), A);
//    		scores.setCount(new Constituent<String>(A,i,i+1), score);
    		addSeen(seen,i,i+1,A);
    		scoreBack.put(new Constituent<String>(A,i,i+1), new Triplet<Constituent<String>,Constituent<String>,Double>(new Constituent<String>(sentence.get(i),i,i),null, score));
    	}
    }
    
    public void handleUnaries(int begin,int end,HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack,boolean onlyUnary,HashMap<Pair<Integer,Integer>,HashSet<String>> seen){
    	boolean added = true;
    	HashSet<String> cands = new HashSet<String>();
    	HashSet<String> toAdd = seen.get(new Pair<Integer,Integer>(begin,end));
		
    	while(added){
    		added = false;
    		if(toAdd == null) break;
    		cands.addAll(toAdd);
    		for(String child : cands){
    			for(UnaryRule uRule : grammar.getUnaryRulesByChild(child) ){
    				Constituent<String> childConstituent = new Constituent<String>(uRule.getChild(),begin,end);
    				Constituent<String> parentConstituent = new Constituent<String>(uRule.getParent(),begin,end);
            Triplet<Constituent<String>, Constituent<String>, Double> scoreTriplet = scoreBack.get(childConstituent);
            double childScores = scoreTriplet == null ? 0 : scoreTriplet.getThird();
    				if(!onlyUnary || childScores > 0 ){
    					double prob = childScores * uRule.getScore();
              scoreTriplet = scoreBack.get(parentConstituent);
              double parentScore = scoreTriplet == null ? 0 : scoreTriplet.getThird();
    					if(prob > parentScore){
    						added = true;
    						addSeen(seen,begin,end,parentConstituent.getLabel());
    						scoreBack.put(parentConstituent, new Triplet<Constituent<String>,Constituent<String>, Double>(childConstituent,null,prob));
    					}
    				} 
    			}
    		}		
    	}
    }
    
    public void getBinaryRules(int span,int begin,HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack,HashMap<Pair<Integer,Integer>,HashSet<String>> seen){
    	int end = begin + span;
    	for(int split = begin + 1 ; split < end ; split ++){
    		HashSet<String> seenStrings = seen.get(new Pair<Integer,Integer>(begin,split));
    		HashSet<String> seenStringsEnd = seen.get(new Pair<Integer,Integer>(split,end));
    		if(seenStringsEnd == null) continue;
    		if(seenStrings == null) continue;
    		HashSet<String> first = seenStrings;
    		HashSet<String> second = seenStringsEnd;
    		boolean isRightChild = false;
    		
    		if(seenStringsEnd.size() < seenStrings.size()){
    			isRightChild = true;
    			first = seenStringsEnd;
    			second = seenStrings;
    		}
    		
    		for(String B : first ){
    			List<BinaryRule>rules;
    			if(isRightChild){
    				rules = grammar.getBinaryRulesByRightChild(B);
    				
    			}else{
    				rules = grammar.getBinaryRulesByLeftChild(B);
    			}
    			for(BinaryRule bRule : rules ){
    				if(isRightChild && !second.contains(bRule.getLeftChild())){
    					continue;
    				}
    				else if(!isRightChild && !second.contains(bRule.getRightChild())) {
    					continue;
    				}
    				
    				
    				Constituent<String> bConstituent = new Constituent<String>(bRule.getLeftChild(),begin,split);
    				Constituent<String> cConstituent = new Constituent<String>(bRule.getRightChild(),split,end);
    				Constituent<String> aConstituent = new Constituent<String>(bRule.getParent(),begin,end);

    				double bScore = scoreBack.get(bConstituent).getThird();
    				double prob =  bScore * scoreBack.get(cConstituent).getThird();

        		//prior on rule
        		prob *= bRule.getScore();

            Triplet<Constituent<String>, Constituent<String>, Double> aTriplet = scoreBack.get(aConstituent);
            double aScore = aTriplet == null ? 0 : aTriplet.getThird();
        		if( prob > aScore){
        			addSeen(seen,aConstituent.getStart(),aConstituent.getEnd(),aConstituent.getLabel());
        			scoreBack.put(aConstituent,new Triplet<Constituent<String>,Constituent<String>, Double>(bConstituent,cConstituent,prob));
        		}
        	}
    		}
    	}
    }
    
    private void addSeen(HashMap<Pair<Integer,Integer>,HashSet<String>> seen, int begin,int end, String label){
    	if(seen.get(new Pair<Integer,Integer>(begin,end)) == null) {
    		HashSet<String> toAdd = new HashSet<String>();
    		toAdd.add(label);
    		seen.put(new Pair<Integer,Integer>(begin,end),toAdd);
    	}else{
    		seen.get(new Pair<Integer,Integer>(begin,end)).add(label);
    	}
    }

    private Tree<String> merge( Tree<String> t1, Tree<String> t2,Constituent<String> currentNode){
    	List<Tree<String>> children = new ArrayList<Tree<String>>();
        if(t1 != null) children.add(t1);
        if(t2 != null) children.add(t2);
        return new Tree<String>(currentNode.getLabel(), children);
    }
    
    private void print_back(HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back){
    	System.out.println("HERE!!!!!!!!");
    	for(Constituent<String> c : back.keySet()){
    		System.out.println("!!" + c.getLabel() + " START: " +c.getStart() +" END: " + c.getEnd() + " --> " +  (back.get(c).getFirst() != null? back.get(c).getFirst().getLabel():"") + "  " + (back.get(c).getSecond() != null ? back.get(c).getSecond().getLabel(): ""));
    		
    	}
    }
    
    private Tree<String> recursiveBuildTree(Constituent<String> currentNode, HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack){
    	if(currentNode == null) return null;
    	Triplet<Constituent<String>,Constituent<String>,Double> children = scoreBack.get(currentNode);
    	
    	//at a terminal, I hope!
    	if(children == null) return new Tree<String>(currentNode.getLabel());
    	
    	//else, not not a terminal
    	Constituent<String> child1 = children.getFirst(); 
    	Constituent<String> child2 = children.getSecond();

    	//call recursive build to remove unary rules, as long as child1 is a nonterminal.
    	Tree<String> subTree1 = recursiveBuildTree(child1,scoreBack);
    	Tree<String> subTree2 = recursiveBuildTree(child2,scoreBack);
    	return merge(subTree1,subTree2,currentNode);
    	
    }
    
    private Tree<String> buildTree(int len, HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack){
    	Constituent<String> bestRoot = new Constituent<String>("ROOT",0,len);
    	return recursiveBuildTree(bestRoot,scoreBack);
    }
  /**
   * Parse a sentence according to the CKY algorithm.
   * @param sentence sentence to parse
   * @return most probable parse tree of sentence
   */
    public Tree<String> getBestParse(List<String> sentence) {
//    	Counter<Constituent<String>> scores = new Counter<Constituent<String>>();
//    	HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back = new HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>>();

      HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>> scoreBack = new HashMap<Constituent<String>, Triplet<Constituent<String>, Constituent<String>, Double>>();

      HashMap<Pair<Integer,Integer>,HashSet<String>> seen = new HashMap<Pair<Integer,Integer>,HashSet<String>>();

      int len = sentence.size();

    	 //populate lowest layer of the parse tree
    	for(int i = 0; i < len; i++){
        getPretermRules(sentence,i,scoreBack,seen);
        handleUnaries(i,i+1,scoreBack,true,seen);
    	}
      
    	for(int span = 2; span <= len; span++){
    	  for(int begin = 0; begin <= len - span; begin++){
    			getBinaryRules(span,begin,scoreBack,seen);
    			handleUnaries(begin,begin+span,scoreBack,false,seen);
    		}
    	}
    	Tree<String> bestParse = buildTree(len, scoreBack);
    	return TreeAnnotations.unAnnotateTree(bestParse);
    }
}
