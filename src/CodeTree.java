/* 
 * Simple DEFLATE decompressor
 * Copyright (c) Project Nayuki
 * 
 * https://www.nayuki.io/page/simple-deflate-decompressor
 * https://github.com/nayuki/Simple-DEFLATE-decompressor
 */

import java.util.ArrayList;
import java.util.List;


/**
 * A binary tree that represents a mapping between symbols and binary strings.
 * A code tree is constructed from given code lengths as a canonical code.
 * Thereafter, the tree structure can be walked through to extract the desired information.
 * This data structure is immutable.
 * <p>Illustrated example:</p>
 * <pre>  Code lengths (canonical code):
 *    Symbol A: 1
 *    Symbol B: 3
 *    Symbol C: 0 (no code)
 *    Symbol D: 2
 *    Symbol E: 3
 *  
 *  Generated Huffman codes:
 *    Symbol A: 0
 *    Symbol B: 110
 *    Symbol C: None
 *    Symbol D: 10
 *    Symbol E: 111
 *  
 *  Huffman code tree:
 *      .
 *     / \
 *    A   .
 *       / \
 *      D   .
 *         / \
 *        B   E</pre>
 */
final class CodeTree {
	
	/**
	 * The root node of this code tree (not {@code null}).
	 */
	public final InternalNode root;
	
	// Stores the code for each symbol, or null if the symbol has no code.
	// For example, if symbol 5 has code 10011, then codes.get(5) is the list [1,0,0,1,1].
	private List<List<Integer>> codes;
	
	
	
	/**
	 * Constructs a canonical Huffman code tree from the specified array of symbol code lengths.
	 * Each code length must be non-negative. Code length 0 means no code for the symbol.
	 * The collection of code lengths must represent a proper full Huffman code tree.
	 * <p>Examples of code lengths that result in under-full Huffman code trees:</p>
	 * <ul>
	 *   <li>[1]</li>
	 *   <li>[3, 0, 3]</li>
	 *   <li>[1, 2, 3]</li>
	 * </ul>
	 * <p>Examples of code lengths that result in correct full Huffman code trees:</p>
	 * <ul>
	 *   <li>[1, 1]</li>
	 *   <li>[2, 2, 1, 0, 0, 0]</li>
	 *   <li>[3, 3, 3, 3, 3, 3, 3, 3]</li>
	 * </ul>
	 * <p>Examples of code lengths that result in over-full Huffman code trees:</p>
	 * <ul>
	 *   <li>[1, 1, 1]</li>
	 *   <li>[1, 1, 2, 2, 3, 3, 3, 3]</li>
	 * </ul>
	 * @param canonicalCodeLengths array of symbol code lengths
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if the array length is less than 2, any element is negative,
	 * or the collection of code lengths would yield an under-full or over-full Huffman code tree
	 */
	public CodeTree(int[] canonicalCodeLengths) {
		// Check basic validity
		if (canonicalCodeLengths == null)
			throw new NullPointerException();
		if (canonicalCodeLengths.length < 2)
			throw new IllegalArgumentException("At least 2 symbols needed");
		for (int cl : canonicalCodeLengths) {
			if (cl < 0)
				throw new IllegalArgumentException("Illegal code length");
		}
		
		// Convert code lengths to code tree
		List<Node> nodes = new ArrayList<Node>();
		for (int i = 15; i >= 0; i--) {  // Descend through code lengths (maximum 15 for DEFLATE)
			if (nodes.size() % 2 != 0)
				throw new IllegalStateException("This canonical code does not represent a Huffman code tree");
			List<Node> newNodes = new ArrayList<Node>();
			
			// Add leaves for symbols with positive code length i
			if (i > 0) {
				for (int j = 0; j < canonicalCodeLengths.length; j++) {
					if (canonicalCodeLengths[j] == i)
						newNodes.add(new Leaf(j));
				}
			}
			
			// Merge pairs of nodes from the previous deeper layer
			for (int j = 0; j < nodes.size(); j += 2)
				newNodes.add(new InternalNode(nodes.get(j), nodes.get(j + 1)));
			nodes = newNodes;
		}
		
		if (nodes.size() != 1)
			throw new IllegalStateException("This canonical code does not represent a Huffman code tree");
		root = (InternalNode)nodes.get(0);
		codes = new ArrayList<List<Integer>>();
		for (int i = 0; i < canonicalCodeLengths.length; i++)
			codes.add(null);
		buildCodeList(root, new ArrayList<Integer>());  // Fill 'codes' with appropriate data
	}
	
	
	// Recursive helper function for the constructor
	private void buildCodeList(Node node, List<Integer> prefix) {
		if (node instanceof InternalNode) {
			InternalNode internalNode = (InternalNode)node;
			
			prefix.add(0);
			buildCodeList(internalNode.leftChild , prefix);
			prefix.remove(prefix.size() - 1);
			
			prefix.add(1);
			buildCodeList(internalNode.rightChild, prefix);
			prefix.remove(prefix.size() - 1);
			
		} else if (node instanceof Leaf) {
			Leaf leaf = (Leaf)node;
			if (leaf.symbol >= codes.size())
				throw new IllegalArgumentException("Symbol exceeds symbol limit");
			if (codes.get(leaf.symbol) != null)
				throw new IllegalArgumentException("Symbol has more than one code");
			codes.set(leaf.symbol, new ArrayList<Integer>(prefix));
			
		} else {
			throw new AssertionError("Illegal node type");
		}
	}
	
	
	
	/**
	 * Returns a string representation of this code tree,
	 * useful for debugging only, and the format is subject to change.
	 * @return a string representation of this code tree
	 */
	public String toString() {
		StringBuilder sb = new StringBuilder();
		toString("", root, sb);
		return sb.toString();
	}
	
	
	// Recursive helper function for toString()
	private static void toString(String prefix, Node node, StringBuilder sb) {
		if (node instanceof InternalNode) {
			InternalNode internalNode = (InternalNode)node;
			toString(prefix + "0", internalNode.leftChild , sb);
			toString(prefix + "1", internalNode.rightChild, sb);
		} else if (node instanceof Leaf) {
			sb.append(String.format("Code %s: Symbol %d%n", prefix, ((Leaf)node).symbol));
		} else {
			throw new AssertionError("Illegal node type");
		}
	}
	
}



/*---- Helper structures ----*/

/**
 * A node in a code tree. This class has exactly two subclasses: InternalNode, Leaf.
 */
abstract class Node {
	
	public Node() {}
}


/**
 * An internal node in a code tree. It has two nodes as children. Immutable.
 */
final class InternalNode extends Node {
	
	public final Node leftChild;  // Not null
	public final Node rightChild;  // Not null
	
	public InternalNode(Node left, Node right) {
		if (left == null || right == null)
			throw new NullPointerException();
		leftChild = left;
		rightChild = right;
	}
}


/**
 * A leaf node in a code tree. It has a symbol value. Immutable.
 */
final class Leaf extends Node {
	
	public final int symbol;  // Always non-negative
	
	public Leaf(int sym) {
		if (sym < 0)
			throw new IllegalArgumentException("Symbol value must be non-negative");
		symbol = sym;
	}
}
