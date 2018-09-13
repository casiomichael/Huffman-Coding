import java.util.*;

/*
 * GENERAL NOTES FOR MYSELF: 
 * - reason for magic number: this checks whether or not the file is compressed
 *   - we check this in the decompress method because we can only decompress compress files
 *     this is why we end up throwing an error when we see that the number of bits matches the magic number
 * - 
 */

/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256; take the number 1, and shift it left BITS_PER_WORD Times (8 times); 
	//what is two to that value
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200; // 0x means its in hexadecimal (base-16, which is 0-9, a, b, c, d, e, f)
	public static final int HUFF_TREE  = HUFF_NUMBER | 1; // bit-wise or it to 1; meaning change the last value to 1.
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;

	public enum Header{TREE_HEADER, COUNT_HEADER};
	public Header myHeader = Header.TREE_HEADER;

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out) {
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		TreeMap<Integer,String> codings = makeCodingsFromTree(root);
		writeHeader(root,out);
		in.reset();
		writeCompressedBits(in,codings,out);

		/*
	    //DETERMINING ALPHABET SIZE
		int alphSize = 0;
		for (int i: counts) {
			if (i != 0)
				alphSize++;
		}
		System.out.println(alphSize);
		 */
	}

	// COMPRESS HELPER METHODS
	private void writeCompressedBits(BitInputStream in, TreeMap<Integer, String> codings, BitOutputStream out) {
		// TODO Auto-generated method stub
		while (true) {
			int bit = in.readBits(BITS_PER_WORD); // want to read 8-bit chunks
			if (bit == -1) // if the value of the bit can't be read, then just get out
				break;
			else {
				String encode = codings.get(bit);
				out.writeBits(encode.length(), Integer.parseInt(encode,2)); // converts the 8 bit chunk into a decimal/int value
			}
		}
		
		String end = codings.get(256);
		out.writeBits(end.length(), Integer.parseInt(end,2)); // can't forget about the EOF
	}

	/*
	 * purpose of the header: we want to write the value of HUFF_TREE to be 32 bits so we know that something
	 * is actually compressed
	 * the writing of the tree makes it so that we can have a 0 if it's an internal node
	 * and a 1 if it is a leaf. if it is a leaf, then that is followed by 9 bits that have the value 
	 * of the leaf. that value is denoted by a number from 0 to 256.
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		// TODO Auto-generated method stub
		// first part writes the 32 bits for the huff_tree
		if (out.bitsWritten() == 0)
			out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(root, out); // this is the pre-order traversal
	}

	private void writeTree(HuffNode root, BitOutputStream out) {
		if (root.left() == null && root.right() == null) {
			out.writeBits(1,1); // since this is a leaf write 1 bit with a value of 1
			out.writeBits(BITS_PER_WORD + 1, root.value()); // the 1 bit is followed with the representation of the value of the leaf, which has 9 bits
		}
		else {
			out.writeBits(1,0); // since this is an internal node, the 1 bit has a value of 0
			if (root.left() != null)
				writeTree(root.left(), out);
			if (root.right() != null)
				writeTree(root.right(), out);	
		}
	}

	/*
	 * purpose of makeCodingsFromTree: creates the code for each value in the tree
	 * i.e. 0101 means go left, right, left, right on the tree to reach a certain value (for example, char w)
	 */
	private TreeMap<Integer, String> makeCodingsFromTree(HuffNode root) {
		// TODO Auto-generated method stub
		TreeMap<Integer, String> codings = new TreeMap<Integer, String>();
		
		return makeCodingsHelper(root, "", codings);
	}

	/*
	 * purpose of this helper method: creates the string that needs to be stored
	 * concatenates the traversal of the tree to reach a certain value
	 */
	private TreeMap<Integer, String> makeCodingsHelper(HuffNode root, String string, TreeMap<Integer, String> codings) {
		// TODO Auto-generated method stub
		if (root.left() == null && root.right() == null)
			codings.put(root.value(), string); // if you reach a leaf, then whatever was concatenated is now the string that encodes the certain value
		else {
			makeCodingsHelper(root.left(), string + "0", codings);
			makeCodingsHelper(root.right(), string + "1", codings);
		}
		
		return codings;
	}

	/*
	 * purpose of this method: creates the tree based on the frequency of the counts
	 * uses greedy algorithm
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		// TODO Auto-generated method stub
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int index = 0; index < counts.length; index++) {
			if (counts[index] == 0) continue; // we only want to make a tree from the ones that have counts
			else {
				HuffNode eBit = new HuffNode(index,counts[index]);
				pq.add(eBit); // calling pq.add for every 8-bit
			}
		}
		HuffNode end = new HuffNode (PSEUDO_EOF, 1);
		pq.add(end); // DONT FORGET TO ADD PSEUDO_EOF

		// call pq.add(new HuffNode(...) for every 8-bit
		// value that occurs one or more times, including PSEUDO_EOF!!
		// these values/counts are in the array of counts

		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, left.weight() + right.weight(), left, right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		
		return root;
	}

	/*
	 * reads the characters in a stream and takes into account 
	 * the frequency of each character to determine 
	 * weights when making the tree
	 */
	private int[] readForCounts(BitInputStream in) {
		int[] counts = new int[256];
		while(true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) break;
			else
				counts[val]++; 
		}
		
		return counts;
	}

	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		/*
		 * 1. is this a compressed file? do by checking the magic number
		 * 2. readTreeHeader: recreate tree from header
		 * 3. readCompressedBits: Parse compressed data from input 
		 */
		int magic = in.readBits(BITS_PER_INT);
		if (magic != HUFF_TREE) 
			// uh-oh
			throw new HuffException("No magic! What?!");
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
	} 

	//DECOMPRESS HELPER METHODS
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		/*
		 * basic logic behind this method:
		 * 1. read the tree bit by bit and traverse the tree from root, left then right
		 * this depends on whether you read a zero or a one.
		 * 2. you want to break when you reach PSEUDO_EOF
		 * 3. write out the bits with 8 bits and the value of that leaf when you reach a leaf that's not PSEUDO_EOF
		 * 4. add in an error in the beginning if the bit value is -1, which is when there are no more bits to read
		 * 5. bit == 0 means that you are at an internal node and so now you want to traverse left first then traverse right
		 */
		HuffNode current = root;
		while (true) {
			int bit = in.readBits(1);
			if (bit == -1)
				throw new HuffException("Haven't encountered PSEUDO_EOF!");
			else {
				if (bit == 0)
					current = current.left();
				else if (bit == 1)
					current = current.right();
				if (current.left() == null && current.right() == null) {
					if (current.value() == PSEUDO_EOF) break;
					else {
						out.writeBits(BITS_PER_WORD, current.value());
						current = root;
					}				
				}
			}
		}
	}

	private HuffNode readTreeHeader(BitInputStream in) {
		// do a pre-order traversal of the tree
		// return it...
		// if bit is 1, then read the next 9 bits and create a huffnode from that
		int bit = in.readBits(1);
		if (bit == 1)
			return new HuffNode(in.readBits(BITS_PER_WORD + 1), 1);
		// else make two recursive calls
		return new HuffNode(0, 0, readTreeHeader(in), readTreeHeader(in)); // if bit == 0 read in preorder
	}

	public void setHeader(Header header) {
		myHeader = header;
		System.out.println("header set to " + myHeader);
	}
}