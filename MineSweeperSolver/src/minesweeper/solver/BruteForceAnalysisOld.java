package minesweeper.solver;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import minesweeper.gamestate.GameStateModel;
import minesweeper.gamestate.MoveMethod;
import minesweeper.solver.utility.BigDecimalCache;
import minesweeper.structure.Action;
import minesweeper.structure.Location;

public class BruteForceAnalysisOld extends BruteForceAnalysisModel{
	
	/*
	private static final BigDecimal[] INVERTS = {null, BigDecimal.ONE,
			BigDecimal.ONE.divide(BigDecimal.valueOf(2), BruteForceAnalysis.DP, RoundingMode.HALF_UP),  // a half
			BigDecimal.ONE.divide(BigDecimal.valueOf(3), BruteForceAnalysis.DP, RoundingMode.HALF_UP),  // a third
			BigDecimal.ONE.divide(BigDecimal.valueOf(4), BruteForceAnalysis.DP, RoundingMode.HALF_UP),  // a quarter
			BigDecimal.ONE.divide(BigDecimal.valueOf(5), BruteForceAnalysis.DP, RoundingMode.HALF_UP),  // a fifth
			BigDecimal.ONE.divide(BigDecimal.valueOf(6), BruteForceAnalysis.DP, RoundingMode.HALF_UP)};  // a sixth
	*/
	// used to hold all the solutions left in the game
	private class SolutionTable {
		
		private final byte[][] solutions;
		private int size = 0;

		private SolutionTable(int maxSize) {
			solutions = new byte[maxSize][];
		}
		
		private void addSolution(byte[] solution) {
			solutions[size] = solution;
			size++;
		};
		
		private int size() {
			return size;
		}
		
		private byte[] get(int index) {
			return solutions[index];
		}
		
		private void sortSolutions(int start, int end, int index) {

			Arrays.sort(solutions, start, end, sorters[index]);
			
		}
		
	}
	
	/**
	 * This sorts solutions by the value of a position
	 */
	private class SortSolutions implements Comparator<byte[]> {

		private final int sortIndex;
		
		public SortSolutions(int index) {
			sortIndex = index;
		}
		
		@Override
		public int compare(byte[] o1, byte[] o2) {
			return o1[sortIndex] - o2[sortIndex];
		}
		
	}
	
	/**
	 * A key to uniquely identify a position
	 */
	private class Position {
		
		private final byte[] position;
		private int hash;
		
		private Position() {
			position = new byte[locations.size()];
			for (int i=0; i < position.length; i++) {
				position[i] = 15;
			}
		}
		
		private Position(Position p, int index, int value) {
			// copy and update to reflect the new position
			position = Arrays.copyOf(p.position, p.position.length);
			position[index] = (byte) (value + 50);			
		}
		
		@Override
		// copied from String hash
		public int hashCode() {
	        int h = hash;
	        if (h == 0 && position.length > 0) {
	            for (int i = 0; i < position.length; i++) {
	                h = 31 * h + position[i];
	            }
	            hash = h;
	        }
	        return h;
		}
		
		@Override
		public boolean equals(Object o) {
			if (o instanceof Position) {
				for (int i=0; i < position.length; i++) {
					if (this.position[i] != ((Position) o).position[i]) {
						return false;
					}
				}
				return true;
			} else {
				return false;
			}
		}
	}
	

	
	/**
	 * Positions on the board which can still reveal information about the game.
	 */
	private class Living implements Comparable<Living>{
		
		//private BigDecimal probability = BigDecimal.ZERO; // probability of winning the game if I reveal this position
		private boolean pruned = false;
		private final short index;
		private int mines = 0;  // number of remaining solutions which have a mine in this position
		private int maxSolutions = 0;    // the maximum number of solutions that can be remaining after clicking here
		private byte maxValue = -1;
		private byte minValue = -1;
		private byte count;  // number of possible values at this location
		
		private Node[] children;

		private Living(short index) {
			this.index = index;
		}

		@Override
		public int compareTo(Living o) {

			// return in most likely to be clear
			int test = this.mines - o.mines;
			if (test != 0) {
				return test;
			}
			
			// then by most number of different possible values
			test = o.count - this.count;
			if (test != 0) {
				return test;
			}
			
			// then by the maxSolutions - ascending
			return this.maxSolutions - o.maxSolutions;
	
		}
		
	}
	
	/**
	 * A representation of a possible state of the game
	 */
	private class Node {
		
		// this is the best probability from the list of living locations at this node
		private int winningLines = 0;
		private boolean fromCache = false; // indicates whether this position came from the cache
		
		// holds the position we are analysing / have reached
		private Position position ; 
		
		private int start;
		private int end;
		
		// these are the candidate positions which could be revealed next move
		private List<Living> livingLocations;
		
		private Living bestLiving;

		private Node() {
			position = new Position();
		}
		
		private Node(Position position) {
			this.position = position;
		}
		
		// create a child node which has an updated position
		//private Node(Node parent, int index, int value) {
        //
		//	position = new Position(parent.position, index, value);
        //
		//}
		
		private List<Living> getLivingLocations() {
			return livingLocations;
		}
		
		private int getSolutionSize() {
			return end - start;
		}
		
		/**
		 * Get the probability of winning the game from the position this node represents
		 * @return
		 */
		private BigDecimal getProbability() {
			
			return BigDecimal.valueOf(winningLines).divide(BigDecimal.valueOf(getSolutionSize()), Solver.DP, RoundingMode.HALF_UP); 
			
		}
		
		private Node[] getChildren(Living living) {
			
			//List<Node> result = new ArrayList<>();
			
			// sort the solutions by possible values
			//System.out.println("Sorting...");
			allSolutions.sortSolutions(start, end, living.index);
			int index = start;
			
			// skip over the mines
			while (index < end && allSolutions.get(index)[living.index] == GameStateModel.MINE) {
				index++;
			}
			
			Node[] work = new Node[9];
			for (int i=living.minValue; i < living.maxValue + 1; i++) {
				
				// if the node is in the cache then use it
				//Node temp = new Node(this, living.index, i);
				Position pos = new Position(this.position, living.index, i);
				
				Node temp1 = cache.get(pos);
				if (temp1 == null) {

					Node temp = new Node(pos);
					
					temp.start = index;
					// find all solutions for this values at this location
					while (index < end && allSolutions.get(index)[living.index] == i) {
						index++;
					}					
					temp.end = index;
					
					work[i] = temp;
					
				} else {
					//System.out.println("In cache " + temp.position.key + " " + temp1.position.key);
					//if (!temp.equals(temp1)) {
					//	System.out.println("Cache not equal!!");
					//}
					temp1.fromCache = true;
					work[i] = temp1;
					cacheHit++;
					cacheWinningLines = cacheWinningLines + temp1.winningLines;
					// skip past these details in the array
					while (index < end && allSolutions.get(index)[living.index] <= i) {
						index++;
					}					
				}

			}

			if (index != this.end) {
				System.out.println("Didn't read all the elements in the array; index = " + index + " end = " + this.end);
			}
			
			
			for (int i=living.minValue; i <= living.maxValue; i++) {
				if (work[i].getSolutionSize() > 0) {
					//if (!work[i].fromCache) {
					//	work[i].determineLivingLocations(this.livingLocations, living.index);
					//}
				} else {
					work[i] = null;   // if no solutions then don't hold on to the details
				}
				
			}

			return work;
			
		}
		
		/**
		 * this is a list of indices to Location that are still alive, i.e. have more than one possible value
		 */
		private void determineLivingLocations() {
			
			start = 0;
			end = allSolutions.size();
			
			List<Living> living = new ArrayList<>();
			
			for (short i=0; i < locations.size(); i++) {
				int value;
				//Living alive = new Living(i);
				
				//boolean[] values = new boolean[9];
				resetValues();
				int mines = 0;
				int maxSolutions = 0;
				byte count = 0;
				byte minValue = 0;
				byte maxValue = 0;
				
				for (int j=0; j < getSolutionSize(); j++) {
					if (allSolutions.get(j)[i] != GameStateModel.MINE) {
						value = allSolutions.get(j)[i];
						//values[value] = true;
						valueCount[value]++;
					} else {
						mines++;
					}
				}
				
				for (byte j=0; j < valueCount.length; j++) {
					if (valueCount[j] > 0) {
						if (count == 0) {
							minValue = j;
						}
						maxValue = j;
						count++;
						if (maxSolutions < valueCount[j]) {
							maxSolutions = valueCount[j];
						}
					}
				}
				if (count > 1) {
					Living alive = new Living(i);
					alive.mines = mines;
					alive.count = count;
					alive.minValue = minValue;
					alive.maxValue = maxValue;
					alive.maxSolutions = maxSolutions;
					living.add(alive);
				}
				
			}
			
			Collections.sort(living);
			
			this.livingLocations = living;
			
		}
		
		/**
		 * this is a list of indices to Location that are still alive, i.e. have more than one possible value
		 * Index is the move which has just be played (in terms of the off-set to the position[] array
		 */
		private void determineLivingLocations(List<Living> liveLocs, int index) {
			
			List<Living> living = new ArrayList<>(liveLocs.size());
			
			for (Living live: liveLocs) {
				
				if (live.index == index) {  // if this is the same move we just played then no need to analyse it - definitely now non-living.
					continue;
				}
				
				int value;
				//Living alive = new Living(live.index);
				
				//boolean[] values = new boolean[9];
				resetValues();
				int mines = 0;
				int maxSolutions = 0;
				byte count = 0;
				byte minValue = 0;
				byte maxValue = 0;
				
				for (int j=start; j < end; j++) {
					value = allSolutions.get(j)[live.index];
					if (value != GameStateModel.MINE) {
						//values[value] = true;
						valueCount[value]++;
					} else {
						mines++;
					}
				}
				
				// find the new minimum value and maximum value for this location (can't be wider than the previous min and max)
				for (byte j=live.minValue; j <= live.maxValue; j++) {
					if (valueCount[j] > 0) {
						if (count == 0) {
							minValue = j;
						}
						maxValue = j;
						count++;
						if (maxSolutions < valueCount[j]) {
							maxSolutions = valueCount[j];
						}
					}
				}
				if (count > 1) {
					Living alive = new Living(live.index);
					alive.mines = mines;
					alive.count = count;
					alive.minValue = minValue;
					alive.maxValue = maxValue;
					alive.maxSolutions = maxSolutions;
					living.add(alive);
				}
				
			}
			
			Collections.sort(living);
			
			this.livingLocations = living;
			
		}
		
		@Override
		public int hashCode() {
			return position.hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			if (o instanceof Node) {
				return position.equals(((Node) o).position); 
			} else {
				return false;
			}
		}
		
	}
	
	private static final String INDENT = "................................................................................";
	
	//private static final int MAX_PROCESSING = 20000;
	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
	
	private int processCount = 0;
	
	private final Solver solver;
	private final int maxSolutionSize;
	
	private final Node top;
	
	private final List<? extends Location> locations;
	
	private final SolutionTable allSolutions;
	
	private final String scope;
	
	private Node currentNode;
	private Location expectedMove;
	
	private final SortSolutions[] sorters;
	
	private int cacheHit = 0;
	private int cacheSize = 0;
	private int cacheWinningLines = 0;
	
	// some work areas to prevent having to instantiate many 1000's of copies of them 
	//private final boolean[] values = new boolean[9];
	private final int[] valueCount = new int[9];
	
	private Map<Position, Node> cache = new HashMap<>(5000);
	
	public BruteForceAnalysisOld(Solver solver, List<? extends Location> locations, int size, String scope) {
		
		this.solver = solver;
		this.locations = locations;
		this.maxSolutionSize = size;
		this.scope = scope;
		this.allSolutions = new SolutionTable(size);
		this.top = new Node();
		this.sorters = new SortSolutions[locations.size()];
		for (int i=0; i < sorters.length; i++) {
			this.sorters[i] = new SortSolutions(i);
		}
		

	}
	
	// this can be called by different threads when brute force is running on multiple threads
	@Override
	protected synchronized void addSolution(byte[] solution) {
		
		if (solution.length != locations.size()) {
			throw new RuntimeException("Solution does not have the correct number of locations");
		}
		
		if (allSolutions.size() >= maxSolutionSize) {
			tooMany = true;
			return;
		}
		
		/*
		String text = "";
		for (int i=0; i < solution.length; i++) {
			text = text + solution[i] + " ";
		}
		solver.display(text);
		*/
		
		allSolutions.addSolution(solution);
		
	}

	@Override
	protected void process() {

		long start = System.currentTimeMillis();
		
		solver.display("----- Brute Force Deep Analysis starting ----");
		solver.display(allSolutions.size() + " solutions in BruteForceAnalysis");
		
		// determine which locations are alive
		top.determineLivingLocations();
		
		int best = 0;
		
		for (Living alive: top.getLivingLocations()) {
			
			int prob = process(1, top, alive, best);
			
			if (best < prob || (top.bestLiving != null && best == prob && top.bestLiving.mines < alive.mines)) {
				best = prob;
				top.bestLiving = alive;
			}
			
			BigDecimal singleProb = BigDecimal.valueOf(allSolutions.size() - alive.mines).divide(BigDecimal.valueOf(allSolutions.size()), Solver.DP, RoundingMode.HALF_UP);
			
			if (alive.pruned) {
				solver.display(alive.index + " " + locations.get(alive.index).display() + " is living with " + alive.count + " possible values and probability " + percentage(singleProb) + ", this location was pruned");
			} else {
				solver.display(alive.index + " " + locations.get(alive.index).display() + " is living with " + alive.count + " possible values and probability " + percentage(singleProb) + ", winning lines " + prob);
			}
			
			
		}
		
		top.winningLines = best;
		
		currentNode = top;
		
		if (processCount < solver.preferences.BRUTE_FORCE_ANALYSIS_MAX_NODES) {
			this.completed = true;
			if (solver.isShowProbabilityTree()) {
				solver.newLine("--------- Probability Tree dump start ---------");
				showTree(0, 0, top);
				solver.newLine("---------- Probability Tree dump end ----------");
			}
		}
		
		
		// clear down the cache
		cache.clear();
		
		long end = System.currentTimeMillis();
		solver.display("Total nodes in cache = " + cacheSize + ", total cache hits = " + cacheHit + ", total winning lines saved = " + this.cacheWinningLines );
		solver.display("process took " + (end - start) + " milliseconds and explored " + processCount + " nodes" );
		solver.display("----- Brute Force Deep Analysis finished ----");
	}
	
	
	// cut off is the best solution we have so far
	private int process(int depth, Node parent, Living parentAlive, int cutoff) {

		int result = 0;
		
		processCount++;
		if (processCount > solver.preferences.BRUTE_FORCE_ANALYSIS_MAX_NODES) {
			return result;
		}

		int notMines = parent.getSolutionSize() - parentAlive.mines;
		
		//if we can never exceed the cutoff then no point continuing
		//if (notMines <= cutoff) {
		//	parentAlive.pruned = true;
		//	return 0;
		//}
		
		parentAlive.children = parent.getChildren(parentAlive);
		
		for (Node child: parentAlive.children) {

			if (child == null) {
				continue;  // continue the loop but ignore this entry
			}
			
			int maxWinningLines = result + notMines;
			
			// if the max possible winning lines is less than the current cutoff then no point doing the analysis
			if (Solver.PRUNE_BF_ANALYSIS && maxWinningLines <= cutoff) {
				parentAlive.pruned = true;
				return result;
			}

			
			if (child.fromCache) {  // nothing more to do, since we did it before

			} else {
				
				child.determineLivingLocations(parent.livingLocations, parentAlive.index);
				
				if (child.getLivingLocations().isEmpty()) {  // no further information ==> all solution indistinguishable ==> 1 winning line

					child.winningLines = 1;
						
				} else {  // not cached and not terminal node, so we need to do the recursion
				
					for (Living alive: child.getLivingLocations()) {
						
						// if the number of safe solutions <= the best winning lines then we can't do any better, so skip the rest
						if (child.getSolutionSize() - alive.mines <= child.winningLines) {
							break;
						}
						
						int winningLines = process(depth + 1, child, alive, child.winningLines);
						if (child.winningLines < winningLines || (child.bestLiving != null && child.winningLines == winningLines && child.bestLiving.mines < alive.mines)) {
							child.winningLines = winningLines;
							child.bestLiving = alive;
						}
						
						// if there are no mines then this is a 100% safe move, so skip any further analysis since it can't be any better
						if (alive.mines == 0) {
							break;
					 	}
						
					}

					// no need to hold onto the living positions once we have determined the best of them
					child.livingLocations = null;
					
					// add the child to the cache if it didn't come from there and it is carrying sufficient winning lines
					if (child.winningLines > 10) {
						cacheSize++;
						cache.put(child.position, child);
					}
					
				}
				
			}
		
			// store the aggregate best move 
			result = result + child.winningLines;	
			
			notMines = notMines - child.getSolutionSize();  // reduce the number of not mines
			
		}
		
		return result;
		
	}

	private void resetValues() {
		for (int i=0; i < valueCount.length; i++) {
			//values[i] = false;
			valueCount[i] = 0;
		}
	}
	
	@Override
	protected int getSolutionCount() {
		return allSolutions.size();
	}
	
	@Override
	protected int getNodeCount() {
		return processCount;
	}
	
	@Override
	protected Action getNextMove(BoardState boardState) {
		
		Living bestLiving = getBestLocation(currentNode);
		
		if (bestLiving == null) {
			return null;
		}
		
		Location loc = this.locations.get(bestLiving.index);

		//solver.display("first best move is " + loc.display());
		BigDecimal prob = BigDecimal.ONE.subtract(BigDecimal.valueOf(bestLiving.mines).divide(BigDecimal.valueOf(currentNode.getSolutionSize()), Solver.DP, RoundingMode.HALF_UP));
		
		while (boardState.isRevealed(loc)) {
			int value = boardState.getWitnessValue(loc);
			
			currentNode = bestLiving.children[value];
			bestLiving = getBestLocation(currentNode);
			if (bestLiving == null) {
				return null;
			}
			prob = BigDecimal.ONE.subtract(BigDecimal.valueOf(bestLiving.mines).divide(BigDecimal.valueOf(currentNode.getSolutionSize()), Solver.DP, RoundingMode.HALF_UP));

			loc = this.locations.get(bestLiving.index);
			
		}
		
		solver.display("mines = "  + bestLiving.mines + " solutions = " + currentNode.getSolutionSize());
		for (int i=0; i < bestLiving.children.length; i++) {
			if (bestLiving.children[i] == null) {
				//solver.display("Value of " + i + " is not possible");
				continue; //ignore this node but continue the loop
			}
			
			String probText;
			if (bestLiving.children[i].bestLiving == null) {
				probText =  Action.FORMAT_2DP.format(ONE_HUNDRED.divide(BigDecimal.valueOf(bestLiving.children[i].getSolutionSize()), Solver.DP, RoundingMode.HALF_UP)) + "%";
			} else {
				//probText = Action.FORMAT_2DP.format(bestLiving.children[i].bestLiving.probability.multiply(ONE_HUNDRED)) + "%";
				probText = Action.FORMAT_2DP.format(bestLiving.children[i].getProbability().multiply(ONE_HUNDRED)) + "%";
			}
			solver.display("Value of " + i + " leaves " + bestLiving.children[i].getSolutionSize() + " solutions and winning probability " + probText);
		}
		
		//String text = " (solve " + scope + " " + Action.FORMAT_2DP.format(bestLiving.probability.multiply(ONE_HUNDRED)) + "%)";
		String text = " (solve " + scope + " " + Action.FORMAT_2DP.format(currentNode.getProbability().multiply(ONE_HUNDRED)) + "%)";
		Action action = new Action(loc, Action.CLEAR, MoveMethod.BRUTE_FORCE_DEEP_ANALYSIS, text, prob);
		
		expectedMove = loc;
		
		return action;
		
	}
	
	private Living getBestLocation(Node node) {
		
		/*
		Living result = null;
		
		BigDecimal best = BigDecimal.ZERO;
		
		for (Living alive: node.getLivingLocations()) {
			
			if (alive.probability.compareTo(best) > 0 ) {
				best = alive.probability;
				result = alive;
			}
			
			
		}
		*/
		return node.bestLiving;
		//return result;
		
	}
	
	
	private void showTree(int depth, int value, Node node) {
		
		String condition;
		if (depth == 0) {
			condition = node.getSolutionSize() + " solutions remain"; 
		} else {
			condition = "When '" + value + "' ==> " + node.getSolutionSize() + " solutions remain";
		}
		
		if (node.bestLiving == null) {
			String line = INDENT.substring(0, depth*3) + condition + " Solve chance " + Action.FORMAT_2DP.format(node.getProbability().multiply(ONE_HUNDRED)) + "%";
			System.out.println(line);
			solver.newLine(line);
			return;
		}
		
		Location loc = this.locations.get(node.bestLiving.index);

		BigDecimal prob = BigDecimal.ONE.subtract(BigDecimal.valueOf(node.bestLiving.mines).divide(BigDecimal.valueOf(node.getSolutionSize()), Solver.DP, RoundingMode.HALF_UP));
		
		
		String line = INDENT.substring(0, depth*3) + condition + " play " + loc.display() + " Survival chance " + Action.FORMAT_2DP.format(prob.multiply(ONE_HUNDRED)) + "%, Solve chance " + Action.FORMAT_2DP.format(node.getProbability().multiply(ONE_HUNDRED)) + "%";
		
		System.out.println(line);
		solver.newLine(line);
		
		//for (Node nextNode: node.bestLiving.children) {
		for (int val=0; val < node.bestLiving.children.length; val++) {
			Node nextNode = node.bestLiving.children[val];
			if (nextNode != null) {
				showTree(depth + 1, val, nextNode);
			}
			
		}
		
	}
	
	
	@Override
	protected Location getExpectedMove() {
		return expectedMove;
	}
	
	private String percentage(BigDecimal prob) {
		
		return Action.FORMAT_2DP.format(prob.multiply(ONE_HUNDRED));
	}

	@Override
	protected boolean allDead() {
		// TODO Auto-generated method stub
		return false;
	}
	
}