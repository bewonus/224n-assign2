package cs224n.assignment;

import cs224n.assignment.Grammar.BinaryRule;
import cs224n.assignment.Grammar.UnaryRule;
import cs224n.ling.Tree;
import cs224n.util.Counter;
import cs224n.util.Pair;
import cs224n.util.Triplet;

import java.util.*;

/**
 * The CKY PCFG Parser you will implement.
 */
public class PCFGParser implements Parser {

    private Grammar grammar;
    private Lexicon lexicon;

  /**
   * Build a lexicon and grammar from training trees.
   * @param trainTrees list of annotated trees to train on
   */
    public void train(List<Tree<String>> trainTrees) {

      // Binarize the training trees
      for (int i = 0; i < trainTrees.size(); i++) {
        trainTrees.set(i, TreeAnnotations.annotateTree(trainTrees.get(i), 1, -1));
      }

      // Build lexicon and grammar
      lexicon = new Lexicon(trainTrees);
      grammar = new Grammar(trainTrees);
      System.out.println(grammar);
    }

  /**
   * Parse a sentence according to the CKY algorithm.
   * @param sentence sentence to parse
   * @return most probable parse tree of sentence
   */
    public Tree<String> getBestParse(List<String> sentence) {

    	
    	
      // Stores log-probability scores associated with a given span and tag (triple)
      Counter<Triplet<Integer, Integer, String>> scores =
        new Counter<Triplet<Integer, Integer, String>>();

      	  				    
      // Stores set of tags based on their span (i.e. which
      // tags are in a specific square of the parse triangle)
      HashMap<Pair<Integer, Integer>, Set<String>> spanToTags = new HashMap<Pair<Integer, Integer>, Set<String>>();

      //MAP START,END,TAG  --> SPLIT, LEFT TAG, RIGHT TAG
      HashMap<Triplet<Integer,Integer,String>,Triplet<Integer,String,String> > back = new HashMap<Triplet<Integer,Integer,String>,Triplet<Integer,String,String> >();
      
      
      // Iterate through words and lexicon and add first layer of scores to parse triangle
      int numWords = sentence.size();
      for (int i = 0; i < numWords; i++) {
        for (String tag : lexicon.tagCounter.keySet()) {
          scores.incrementCount(
            new Triplet<Integer, Integer, String>(i, i+1, tag),
            Math.log(lexicon.scoreTagging(sentence.get(i), tag)));

          // Add tag to the span map
          Pair<Integer, Integer> span = new Pair<Integer, Integer>(i, i+1);
          Set<String> tags = spanToTags.get(span);
          if (tags == null) {
            tags = new HashSet<String>();
          }
          tags.add(tag);
          spanToTags.put(span, tags);
          System.out.println(spanToTags);
        }

        // Handle unaries
        boolean added = true;
        while (added) {
          added = false;

          // Iterate over all grammar rules (uses both for loops)
          for (String child : grammar.unaryRulesByChild.keySet()) {
            for (UnaryRule unaryRule : grammar.getUnaryRulesByChild(child)) {
              
              //if(!scores.containsKey(new Triplet<Integer, Integer, String>(i, i+1, child))) continue;
              
              double prob = Math.log(unaryRule.getScore()) +
                scores.getCount(new Triplet<Integer, Integer, String>(i, i+1, child));
              Triplet<Integer, Integer, String> parentTriplet =
                new Triplet<Integer, Integer, String>(i, i+1, unaryRule.getParent());

              // Update scores if we've found a better unary promotion rule
              if (!scores.containsKey(parentTriplet) || prob > scores.getCount(parentTriplet) ) {
                scores.setCount(parentTriplet, prob);

                // Add tag to the span map
                Pair<Integer, Integer> spanKey = new Pair<Integer, Integer>(i, i+1);
                Set<String> tags = spanToTags.get(spanKey);
                if (tags == null) {
                  tags = new HashSet<String>();
                }
                tags.add(unaryRule.getParent());
                spanToTags.put(spanKey, tags);
                System.out.println(spanToTags);
                Triplet<Integer, String, String> childTriplet =
                        new Triplet<Integer, String, String>(i,null,child);
                
                back.put(parentTriplet, childTriplet);
                // TODO: update back pointers

                added = true;
              }
            }
          }
        }
      }

      // Fill in the rest of the parse triangle scores
      for (int span = 2; span <= numWords; span++) {
        for (int begin = 0; begin <= numWords - span; begin++) {
          int end = begin + span;
          for (int split = begin + 1; split <= end - 1; split++) {
        	  System.out.println("BEGIN : "+ begin);
        	  System.out.println("END : "+ end);	
            // Get left children and right children, and all potential binary rules from these
            Set<String> leftChildren = spanToTags.get(new Pair<Integer, Integer>(begin, split));
            Set<String> rightChildren = spanToTags.get(new Pair<Integer, Integer>(split, end));
            Set<BinaryRule> leftRules = new HashSet<BinaryRule>();
            Set<BinaryRule> rightRules = new HashSet<BinaryRule>();
            for (String leftChild : leftChildren) {
              leftRules.addAll(grammar.getBinaryRulesByLeftChild(leftChild));
            }
            
            for (String rightChild : rightChildren) {
              rightRules.addAll(grammar.getBinaryRulesByRightChild(rightChild));
            }

            // Keep all rules in leftRules intersect rightRules
            // (these are the relevant rules to check)
            leftRules.retainAll(rightRules);

            System.out.println("leftRules: " + leftRules);
            // Iterate through relevant rules and update scores
            for (BinaryRule binaryRule : leftRules) {
            	
            	double prob = scores.getCount(new Triplet<Integer, Integer, String>(
                begin, split, binaryRule.getLeftChild())) +
                scores.getCount(new Triplet<Integer, Integer, String>(
                  split, end, binaryRule.getRightChild()));

              // Update scores if we've found a more probable parse
              Triplet<Integer, Integer, String> parentTriple = new Triplet<Integer, Integer, String>(begin, end, binaryRule.getParent());
              if (!scores.containsKey(parentTriple) || prob > scores.getCount(parentTriple)) {
                scores.setCount(parentTriple, prob);

                // Add tag to the span map
                Pair<Integer, Integer> spanKey = new Pair<Integer, Integer>(begin, end);
                Set<String> tags = spanToTags.get(spanKey);
                if (tags == null) {
                  tags = new HashSet<String>();
                }
                tags.add(binaryRule.getParent());
                spanToTags.put(spanKey, tags);

                // TODO: update back pointers
                Triplet<Integer, String, String> childTriplet =
                        new Triplet<Integer, String, String>(split,binaryRule.getLeftChild(),binaryRule.getRightChild());
                
                back.put(parentTriple, childTriplet);
                // TODO: update back pointers
              }
            }
          }

          // Handle unaries
          boolean added = true;
          while (added) {
            added = false;

            // Iterate over all grammar rules (uses both for loops)
            for (String child : grammar.unaryRulesByChild.keySet()) {
              for (UnaryRule unaryRule : grammar.getUnaryRulesByChild(child)) {
            	Triplet<Integer, Integer, String> childTriplet = new Triplet<Integer, Integer, String>(begin, end, child);
                double prob = Math.log(unaryRule.getScore()) +
                  scores.getCount(childTriplet);
                Triplet<Integer, Integer, String> parentTriplet =
                  new Triplet<Integer, Integer, String>(begin, end, unaryRule.getParent());

                // Update scores if we've found a better unary promotion rule
                if (scores.containsKey(childTriplet) && prob > scores.getCount(parentTriplet) ) {
                  scores.setCount(parentTriplet, prob);
                  
                  // TODO: update back pointers
                  Triplet<Integer, String, String> childTripletValue =
                          new Triplet<Integer, String, String>(begin,null,child);                  
                  back.put(parentTriplet, childTripletValue);
                  added = true;
                }
              }
            }
          }
        }
      }
      System.out.println("++++++++++++++++++++++++++++++++++++++++++");
      System.out.println("++++++++++++++++++++++++++++++++++++++++++");
      System.out.println("++++++++++++++++++++++++++++++++++++++++++");
      

      System.out.println(back);
      return buildTree(scores,back,numWords);
    }
    
    private Triplet<Integer, Integer, String> getBestRoot(Counter<Triplet<Integer, Integer, String>> scores, int numWords){
    	Set<Triplet<Integer, Integer, String>> keys = scores.keySet();
    	String bestRootTag = null;
    	double max = 0.0;
    	for(Triplet<Integer, Integer, String> key : keys){
    		if(key.getFirst() == 0 && key.getSecond() == numWords){
    			if(bestRootTag == null || scores.getCount(key) > max){
    				max = scores.getCount(key);
    				bestRootTag = key.getThird();
    			}
    		}
    	}
    	return new Triplet<Integer, Integer, String>(0,numWords,bestRootTag);
    }
    
    private Tree<String> merge(Tree<String> leftTree, Tree<String> rightTree,String currentTag) {
        List<Tree<String>> children = new ArrayList<Tree<String>>();
        if(leftTree != null) children.add(leftTree);
        if(rightTree != null) children.add(rightTree);
        return new Tree<String>(currentTag, children);
    }

    private Tree<String> recursiveBuildTree(HashMap<Triplet<Integer,Integer,String>,Triplet<Integer,String,String> > back,String currentTag,int begin,int end){
    	if(currentTag == null) return null;
    	System.out.println(currentTag); 
    	Triplet<Integer,Integer,String> key = new Triplet<Integer,Integer,String>(begin,end,currentTag);
    	Triplet<Integer,String,String> bestRules = back.get(key);
    	//if at preterminal
    	if(bestRules == null){
    		System.out.println("NO BEST RULE FOR THIS KEY: " + key ); 
    		return new Tree<String>(currentTag,new ArrayList<Tree<String>>());
    	} 	
    	Tree<String> leftTree = recursiveBuildTree(back, bestRules.getSecond(), begin,bestRules.getFirst());
    	Tree<String> rightTree = recursiveBuildTree(back, bestRules.getThird(), bestRules.getFirst(),end);
    	
    	return merge(leftTree,rightTree,currentTag);
    }
    
    private Tree<String> buildTree(Counter<Triplet<Integer, Integer, String>> scores, HashMap<Triplet<Integer,Integer,String>,Triplet<Integer,String,String> > back, int numWords){
    	Triplet<Integer, Integer, String> bestRoot = getBestRoot(scores,numWords);
    	System.out.println(bestRoot);  	
    	Tree<String> parseTree = recursiveBuildTree(back,bestRoot.getThird(),0,numWords);
    	System.out.println("PARSE TREE: " + parseTree);
    	return parseTree;
    }
    
}
