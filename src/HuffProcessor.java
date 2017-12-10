import java.util.*;
/**
 *	Interface that all compression suites must implement. That is they must be
 *	able to compress a file and also reverse/decompress that process.
 * 
 *	@author Brian Lavallee
 *	@since 5 November 2015
 *  @author Owen Atrachan
 *  @since December 1, 2016
 *  
 *  Project 5: Huffman Coding
 *  @author Avishek Khan
 *  @author Brian Jordan
 *  December 5, 2017
 */
public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); // or 256
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;
	public static final int HUFF_COUNTS = HUFF_NUMBER | 2;
	public int count = 0;

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
	public void compress(BitInputStream in, BitOutputStream out){
	    int[] counts = readForCounts(in); // counts occurrence of each character in file
//	    for (int i : counts){
//	    	if (counts[i] != 0){
//	    		hasValues = true;
//	    		break;
//	    	}
//	    }
//	    if (hasValues == false){
//	    	throw new NullPointerException("Empty File");
//	    }
	    HuffNode root = makeTreeFromCounts(counts); // creates Huffman Tree based on occurrence array
	    String[] codings = makeCodingsFromTree(root); // creates an array maping integer values to string values
	    writeHeader(root, out); // writes out the Huffman tree so that it can be passed on to the decompresser 
	    
	    in.reset(); //you reset this because you read it once to make the data structures, need to read it again
	    
	    writeCompressedBits(in,codings,out);
	    System.out.println(count);
	}
	// Compression Helper Methods
	// Method readForCounts counts occurrence of each character in file
	public int[] readForCounts(BitInputStream in){
		int[] ret = new int[ALPH_SIZE]; 
		
		while (true){
			int val = in.readBits(BITS_PER_WORD); //reading 8 bits gives an integer between 0 and 256
			if (val == -1){ //if you run out bits, then it breaks out of the loop
				break;
			}
			
			ret[val]++; //increment that by 1
			} 
		//in.reset();
		return ret; //return the counting int array
	}
	// Method makeTreeFromCounts creates Huffman Tree based on occurrence array created by readForCounts
	public HuffNode makeTreeFromCounts(int[] counts){
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();//create a new priority queue
		for (int i = 0; i < counts.length; i++){ //for every item in the counts array
			if (counts[i] != 0){ //if the count of that item is 1 or greater
				pq.add(new HuffNode(i,counts[i], null, null)); //add a new HuffNode with the value and weight, that has no children, to the pq
				count ++;
			}
		}
		pq.add(new HuffNode(PSEUDO_EOF,1,null,null)); //at the end, add to the pq PSEUDO_EOF
		while (pq.size() > 1){
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode (-1, left.weight() + right.weight(), left, right);
			pq.add(t);

		}
		HuffNode root = pq.remove();
		return root;
	}
	// Method makeCodingsFromTree creates an array mapping integer values to string values
	public String[] makeCodingsFromTree(HuffNode root){
		//LeafTrails APT
		String[] paths_array = new String[257]; //create an array for all the nodes in the tree: 0-255, and PSEUDO_EOF
		makeCodingsHelper(root,paths_array,"");
		return paths_array;
	}
	public void makeCodingsHelper(HuffNode tree, String[] paths_array,String path) { //helper method for LeafTrails APT
		if (tree == null) return;
		if (tree.left() == null && tree.right() == null) { //reached a leaf
			paths_array[tree.value()] = path; //for that value in the tree, use it as a index in the array, and store the string path you took
//			return;
		}
		makeCodingsHelper(tree.left(),paths_array,path+"0");
		makeCodingsHelper(tree.right(),paths_array,path+"1");
	}

	// Method writeHeader writes out the Huffman tree so that it can be passed on to the decompresser 
	public void writeHeader(HuffNode root, BitOutputStream out){
		out.writeBits(BITS_PER_INT, HUFF_TREE); //write the HUFF_TREE first
		writeTree(root,out);
	}
	public void writeTree(HuffNode root,BitOutputStream out){
		HuffNode current = root;
		if (current.left() == null && current.right() == null){ //means you've reached a leaf
			out.writeBits(1, 1); //put a 1 because leaves have a bit value of 1 first
			out.writeBits(BITS_PER_WORD +1, current.value()); //write the value of the leaf
			return;
		}
		else{ //internal node
			out.writeBits(1, 0); //write an internal node which always has value of 0
			writeTree(current.left(), out); //recursion on the left
			writeTree(current.right(),out); //recursion on the right
		}
		
		
	}
	// Method writeCompressedBits writes out file compressed using Huffman Coding
	public void writeCompressedBits(BitInputStream in, String[] codings, BitOutputStream out){
		while(true){
			int ch = in.readBits(BITS_PER_WORD); //read the chunks for each character
			if (ch == -1){
				break;
				}
			String encode = codings[ch];
			out.writeBits(encode.length(), Integer.parseInt(encode,2));
			}
		out.writeBits(codings[PSEUDO_EOF].length(), Integer.parseInt(codings[PSEUDO_EOF],2));
	
		
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
		int id = in.readBits(BITS_PER_INT);
		if (id != HUFF_TREE){
			throw new HuffException("Error");
		}
	    HuffNode root = readTreeHeader(in);
	    readCompressedBits(root, in, out);
	}
	
	// Decompression Helper Methods
	// Method readTreeHeader creates Huffman Tree from header of input 
	public HuffNode readTreeHeader(BitInputStream in){
		int bit = in.readBits(1);
		//while (true){
			if (bit == 0){ //This means that it is an internal node
				HuffNode left = readTreeHeader(in); //create the left HuffNode
				HuffNode right = readTreeHeader(in); //create the right HuffNode
				HuffNode newNode = new HuffNode(-1,-1,left,right);
				return newNode; //return the new node
			}
			else{ //when bit == 1, so you've reached a leaf
				int value = in.readBits(BITS_PER_WORD + 1); //read the bits per word, and 1 extra because it is a 1 (leaf)
//				if (value == PSEUDO_EOF){ //check to see if it is the end of the header
//					return new HuffNode(PSEUDO_EOF,0,null,null);
//				}
				HuffNode newNode = new HuffNode(value, -1, null, null); //make the new node 
				return newNode; // return it
			}
		//}
	}
	// Method readCompressedBits reads input based on Huffman Tree and converts bits back to Strings
	public void readCompressedBits(HuffNode node, BitInputStream in, BitOutputStream out){
		HuffNode current = node;
		while (true){
			int bit = in.readBits(1); //read the bits 1 at a time
			if (bit == -1){
				throw new HuffException ("bad input, no PSEUDO_EOF");
//				break;
			}
			//else { //can either be a 0 or a 1
				if (bit == 0){
					current = current.left(); //read a 0, so you go to the left		
				}
				else{ //read a 1, so you go to the right
					current = current.right();
				}
				if (current.left() == null && current.right() == null){ //checking that you got to a leaf
					if (current.value() == PSEUDO_EOF){
						break; //This means that you reached the end of the file
					}
					else{ //you've reached a letter
						out.writeBits(BITS_PER_WORD, current.value());
						current = node;
						}
					}
				//}
			}
		//return out;
		
	}
	
	public void setHeader(Header header) {
        myHeader = header;
        //System.out.println("header set to "+myHeader);
    }
	
}
