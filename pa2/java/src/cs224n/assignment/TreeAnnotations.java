package cs224n.assignment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cs224n.ling.Tree;
import cs224n.ling.Trees;
import cs224n.ling.Trees.MarkovizationAnnotationStripper;
import cs224n.util.Filter;

/**
 * Class which contains code for annotating and binarizing trees for
 * the parser's use, and debinarizing and unannotating them for
 * scoring.
 */
public class TreeAnnotations {

	public static Tree<String> annotateTree(Tree<String> unAnnotatedTree) {

		// Currently, the only annotation done is a lossless binarization

		// TODO: change the annotation from a lossless binarization to a
		// finite-order markov process (try at least 1st and 2nd order)

		// TODO : mark nodes with the label of their parent nodes, giving a second
		// order vertical markov process

    Tree<String> verticalTree = verticalMarkovization(unAnnotatedTree.deepCopy(), null);
    Tree<String> thirdVerticalTree = thirdVerticalMarkovization(unAnnotatedTree.deepCopy(), null, null);
    System.out.println("original tree: ");
    System.out.println("" + unAnnotatedTree);
    System.out.println("second order vertical tree: ");
    System.out.println("" + verticalTree);
    System.out.println("third order vertical tree: ");
    System.out.println("" + thirdVerticalTree);

		return binarizeTree(unAnnotatedTree);

	}

  /**
   * Second order vertical markovization. Marks all non-terminal nodes (including preterminals,
   * but not the ROOT or terminals) with the label of their parent tag.
   */
  private static Tree<String> verticalMarkovization(Tree<String> tree, String parentTag) {
    if (tree.getChildren().isEmpty()) {
      return new Tree<String>(tree.getLabel());
    }
    List<Tree<String>> children = new ArrayList<Tree<String>>();
    for (Tree<String> child : tree.getChildren()) {
      children.add(verticalMarkovization(child, tree.getLabel()));
    }
    if (parentTag != null) {
      tree.setLabel(tree.getLabel() + "^" + parentTag);
    }

    return new Tree<String>(tree.getLabel(), children);
  }

  /**
   * Second order vertical markovization. Marks all non-terminals (including preterminals,
   * but not ROOT or terminals) with their parent tag as well.
   */
  private static Tree<String> thirdVerticalMarkovization(Tree<String> tree, String parentTag, String grandParentTag) {
    if (tree.getChildren().isEmpty()) {
      return new Tree<String>(tree.getLabel());
    }
    List<Tree<String>> children = new ArrayList<Tree<String>>();
    for (Tree<String> child : tree.getChildren()) {
      children.add(thirdVerticalMarkovization(child, tree.getLabel(), parentTag));
    }
    if (parentTag != null) {
      tree.setLabel(tree.getLabel() + "^" + parentTag);
    }
    if (grandParentTag != null) {
      tree.setLabel(tree.getLabel() + "^" + grandParentTag);
    }

    return new Tree<String>(tree.getLabel(), children);
  }


	private static Tree<String> binarizeTree(Tree<String> tree) {
		String label = tree.getLabel();
		if (tree.isLeaf())
			return new Tree<String>(label);
		if (tree.getChildren().size() == 1) {
			return new Tree<String>
			(label, 
					Collections.singletonList(binarizeTree(tree.getChildren().get(0))));
		}
		// otherwise, it's a binary-or-more local tree, 
		// so decompose it into a sequence of binary and unary trees.
		String intermediateLabel = "@"+label+"->";
		Tree<String> intermediateTree =
				binarizeTreeHelper(tree, 0, intermediateLabel);
		return new Tree<String>(label, intermediateTree.getChildren());
	}

	private static Tree<String> binarizeTreeHelper(Tree<String> tree,
			int numChildrenGenerated, 
			String intermediateLabel) {
		Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		children.add(binarizeTree(leftTree));
		if (numChildrenGenerated < tree.getChildren().size() - 1) {
			Tree<String> rightTree = 
					binarizeTreeHelper(tree, numChildrenGenerated + 1, 
							intermediateLabel + "_" + leftTree.getLabel());
			children.add(rightTree);
		}
		return new Tree<String>(intermediateLabel, children);
	} 

	public static Tree<String> unAnnotateTree(Tree<String> annotatedTree) {

		// Remove intermediate nodes (labels beginning with "@"
		// Remove all material on node labels which follow their base symbol
		// (cuts at the leftmost - or ^ character)
		// Examples: a node with label @NP->DT_JJ will be spliced out, 
		// and a node with label NP^S will be reduced to NP

		Tree<String> debinarizedTree =
				Trees.spliceNodes(annotatedTree, new Filter<String>() {
					public boolean accept(String s) {
						return s.startsWith("@");
					}
				});
		Tree<String> unAnnotatedTree = 
				(new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
    Tree<String> unMarkovizedTree =
        (new Trees.MarkovizationAnnotationStripper()).transformTree(unAnnotatedTree);
		return unMarkovizedTree;
	}
}
