import java.util.ArrayList;
import java.util.List;


/**
 * A canonical Huffman code. Immutable. Code length 0 means no code.
 */
/*
 * A canonical Huffman code only describes the code length of each symbol. The codes can be reconstructed from this information. In this implementation, symbols with lower code lengths, breaking ties by lower symbols, are assigned lexicographically lower codes.
 * Example:
 *   Code lengths (canonical code):
 *     Symbol A: 1
 *     Symbol B: 3
 *     Symbol C: 0 (no code)
 *     Symbol D: 2
 *     Symbol E: 3
 *   Huffman codes (generated from canonical code):
 *     Symbol A: 0
 *     Symbol B: 110
 *     Symbol C: None
 *     Symbol D: 10
 *     Symbol E: 111
 */
final class CanonicalCode {
	
	private int[] codeLengths;
	
	
	
	// The constructor does not check that the array of code lengths results in a complete Huffman tree, being neither underfilled nor overfilled.
	public CanonicalCode(int[] codeLengths) {
		if (codeLengths == null)
			throw new NullPointerException("Argument is null");
		this.codeLengths = codeLengths.clone();
		for (int x : codeLengths) {
			if (x < 0)
				throw new IllegalArgumentException("Illegal code length");
		}
	}
	
	
	
	public CodeTree toCodeTree() {
		List<Node> nodes = new ArrayList<Node>();
		for (int i = max(codeLengths); i >= 1; i--) {  // Descend through positive code lengths
			List<Node> newNodes = new ArrayList<Node>();
			
			// Add leaves for symbols with code length i
			for (int j = 0; j < codeLengths.length; j++) {
				if (codeLengths[j] == i)
					newNodes.add(new Leaf(j));
			}
			
			// Merge nodes from the previous deeper layer
			for (int j = 0; j < nodes.size(); j += 2)
				newNodes.add(new InternalNode(nodes.get(j), nodes.get(j + 1)));
			
			nodes = newNodes;
			if (nodes.size() % 2 != 0)
				throw new IllegalStateException("This canonical code does not represent a Huffman code tree");
		}
		
		if (nodes.size() != 2)
			throw new IllegalStateException("This canonical code does not represent a Huffman code tree");
		return new CodeTree(new InternalNode(nodes.get(0), nodes.get(1)), codeLengths.length);
	}
	
	
	private static int max(int[] array) {
		int result = array[0];
		for (int x : array)
			result = Math.max(x, result);
		return result;
	}
	
}
