import java.util.ArrayList;
import java.util.List;


/**
 * A canonical Huffman code, which only describes the code length of
 * each symbol. Immutable. Code length 0 means no code for the symbol.
 * <p>The binary codes for each symbol can be reconstructed from the length information.
 * In this implementation, lexicographically lower binary codes are assigned to symbols
 * with lower code lengths, breaking ties by lower symbol values. For example:</p>
 * <pre>  Code lengths (canonical code):
 *    Symbol A: 1
 *    Symbol B: 3
 *    Symbol C: 0 (no code)
 *    Symbol D: 2
 *    Symbol E: 3
 *  
 *  Sorted lengths and symbols:
 *    Symbol A: 1
 *    Symbol D: 2
 *    Symbol B: 3
 *    Symbol E: 3
 *    Symbol C: 0 (no code)
 *  
 *  Generated Huffman codes:
 *    Symbol A: 0
 *    Symbol D: 10
 *    Symbol B: 110
 *    Symbol E: 111
 *    Symbol C: None
 *  
 *  Huffman codes sorted by symbol:
 *    Symbol A: 0
 *    Symbol B: 110
 *    Symbol C: None
 *    Symbol D: 10
 *    Symbol E: 111</pre>
 * @see CodeTree
 */
final class CanonicalCode {
	
	private int[] codeLengths;
	
	
	
	/**
	 * Constructs a canonical Huffman code from the specified array of symbol code lengths.
	 * Each code length must be non-negative. Code length 0 means no code for the symbol.
	 * The collection of code lengths must represent a proper full Huffman code tree,
	 * but this is not checked at construction time.
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
	 * @param codeLens array of symbol code lengths
	 * @throws NullPointerException if the array is {@code null}
	 * @throws IllegalArgumentException if the array length is less than 2 or any element is negative
	 */
	public CanonicalCode(int[] codeLengths) {
		if (codeLengths == null)
			throw new NullPointerException("Argument is null");
		this.codeLengths = codeLengths.clone();
		for (int x : codeLengths) {
			if (x < 0)
				throw new IllegalArgumentException("Illegal code length");
		}
	}
	
	
	
	/**
	 * Returns the canonical code tree for this canonical Huffman code.
	 * @return the canonical code tree
	 */
	public CodeTree toCodeTree() {
		List<Node> nodes = new ArrayList<Node>();
		for (int i = max(codeLengths); i >= 0; i--) {  // Descend through code lengths
			if (nodes.size() % 2 != 0)
				throw new IllegalStateException("This canonical code does not represent a Huffman code tree");
			List<Node> newNodes = new ArrayList<Node>();
			
			// Add leaves for symbols with positive code length i
			if (i > 0) {
				for (int j = 0; j < codeLengths.length; j++) {
					if (codeLengths[j] == i)
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
		return new CodeTree((InternalNode)nodes.get(0), codeLengths.length);
	}
	
	
	// Returns the maximum value in the given array, which must have at least 1 element.
	private static int max(int[] array) {
		int result = array[0];
		for (int x : array)
			result = Math.max(x, result);
		return result;
	}
	
}
