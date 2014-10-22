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

    	//System.out.println(grammar);
      //System.out.println(grammar);
      //System.out.println("-----------------------");
    }
    
    public void getPretermRules(List<String> sentence,int i,Counter<Constituent<String>> scores,HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back,HashMap<Pair<Integer,Integer>,HashSet<String>> seen){
    	for(String A:lexicon.tagCounter.keySet()){
    		double score = lexicon.scoreTagging(sentence.get(i), A);
    		scores.setCount(new Constituent<String>(A,i,i+1), score);
    		addSeen(seen,i,i+1,A);
    		back.put(new Constituent<String>(A,i,i+1), new Pair<Constituent<String>,Constituent<String>>(new Constituent<String>(sentence.get(i),i,i),null));
    	}
    }
    
    
    public void handleUnaries(List<String> sentence,int begin,int end,Counter<Constituent<String>> scores,HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back,boolean onlyUnary,HashMap<Pair<Integer,Integer>,HashSet<String>> seen){
    	boolean added = true;
    	HashSet<String> cands = new HashSet<String>();
    	HashSet<String> toAdd = seen.get(new Pair<Integer,Integer>(begin,end));
		
    	while(added){
    		added = false;
    		//HashSet<String> cands = new HashSet<String>();
    		if(toAdd == null) break;
    		//cands.clear();
    		cands.addAll(toAdd);// grammar.unaryRulesByChild.keySet()
    		toAdd = new HashSet<String>();
    		for(String child : cands){
    			for(UnaryRule uRule : grammar.getUnaryRulesByChild(child) ){
    				//if(!updated.contains(uRule.getChild())) continue;
    				//if(uRule.getParent().equals("ROOT")) System.out.println("ROOT FOUND!");
    				Constituent<String> childConstituent = new Constituent<String>(uRule.getChild(),begin,end);
    				Constituent<String> parentConstituent = new Constituent<String>(uRule.getParent(),begin,end);   					
    				double scoreChild = scores.getCount(childConstituent);
    				if(!onlyUnary || scoreChild > 0 ){
    					double prob = scoreChild * uRule.getScore();
    					//if(uRule.getParent().equals("ROOT")) System.out.println("ROOT CONSIDERED! WITH PROB = "+ prob + " AND CHILD = " + uRule.getChild() + "AND OLD SCORE = " + scores.getCount(parentConstituent));
    					if(prob > scores.getCount(parentConstituent)){	
    						added = true;
    						//if(uRule.getParent().equals("ROOT")) System.out.println("ROOT ADDED!");
    						toAdd.add(parentConstituent.getLabel());
    						scores.setCount(parentConstituent, prob);
    						addSeen(seen,begin,end,parentConstituent.getLabel());
    						back.put(parentConstituent, new Pair<Constituent<String>,Constituent<String>>(childConstituent,null));
    					}
    				} 
    			}
    		}		
    	}
    }
    
    public void getBinaryRules(List<String> sentence,int span,int begin,Counter<Constituent<String>> scores,HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back,HashMap<Pair<Integer,Integer>,HashSet<String>> seen){
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

    				double bScore = scores.getCount(bConstituent);
    				double prob =  bScore * scores.getCount(cConstituent) ;
        			//prior on rule
        			prob *= bRule.getScore();
        			if( prob > scores.getCount(aConstituent) ){
        				scores.setCount(aConstituent, prob);
        				addSeen(seen,aConstituent.getStart(),aConstituent.getEnd(),aConstituent.getLabel());
        				back.put(aConstituent,new Pair<Constituent<String>,Constituent<String>>(bConstituent,cConstituent)); 
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
    
    private  Constituent<String> getBestRoot(Counter<Constituent<String>> scores,int numWords){
    	return new Constituent<String>("ROOT",0,numWords);
    	
    	/*double max = -1;
    	Constituent<String> bestRoot = null;
    	for(Constituent<String> constituent : scores.keySet()){
    		if(scores.getCount(constituent) > max && constituent.getEnd() == numWords && constituent.getStart() == 0){
    			max = scores.getCount(constituent);
    			bestRoot = constituent;
    		}
    	}
    	return bestRoot;*/
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
    
    private Tree<String> recursiveBuildTree(Constituent<String> currentNode, HashMap<Constituent<String>,Pair<Constituent<String>,Constituent<String>>> back){
    	if(currentNode == null) return null;
    	Pair<Constituent<String>,Constituent<String>> children = back.get(currentNode);
    	
    	//at a terminal, I hope!
    	if(children == null) return new Tree<String>(currentNode.getLabel());
    	
    	//else, not not a terminal
    	Constituent<String> child1 = children.getFirst(); 
    	Constituent<String> child2 = children.getSecond();
    	//call recursive build to remove unary rules, as long as child1 is a nonterminal.
    	//if(child2!=null)System.out.println("CHILLD 2 :" + child2.getLabel());
    	
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
    	 HashMap<Pair<Integer,Integer>,HashSet<String>> seen = new HashMap<Pair<Integer,Integer>,HashSet<String>>();
    	 //populate lowest layer of the parse tree
    	 for(int i = 0; i < sentence.size() ; i++){
    		 getPretermRules(sentence,i,scores,back,seen);
    		 handleUnaries(sentence,i,i+1,scores,back,true,seen);
    	 }
    	 
    	 for(int span = 2; span <= sentence.size() ; span++){
    		 for(int begin = 0; begin <= sentence.size() - span; begin++){
    			getBinaryRules(sentence,span,begin,scores,back,seen);
    			handleUnaries(sentence,begin,begin+span,scores,back,false,seen);
    		 }
    	 }
    	 Tree<String> bestParse = buildTree(sentence,scores,back);
    	 return TreeAnnotations.unAnnotateTree(bestParse);
    }
}
