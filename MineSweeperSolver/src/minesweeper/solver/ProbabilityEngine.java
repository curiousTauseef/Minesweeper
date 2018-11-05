package minesweeper.solver;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import minesweeper.solver.constructs.Box;
import minesweeper.solver.constructs.CandidateLocation;
import minesweeper.solver.constructs.LinkedLocation;
import minesweeper.solver.constructs.Square;
import minesweeper.solver.constructs.Witness;
import minesweeper.structure.Area;
import minesweeper.structure.Location;

/**
 * This class uses a non iterative approach to calculating probabilities for each {@link minesweeper.solver.constructs.Box Box}. It is driven by a {@link minesweeper.solver.WitnessWeb witness web}.
 * 
 * @author David
 *
 */
public class ProbabilityEngine {

	private int[][] SMALL_COMBINATIONS = new int[][] {{1}, {1,1}, {1,2,1}, {1,3,3,1}, {1,4,6,4,1}, {1,5,10,10,5,1}, {1,6,15,20,15,6,1}, {1,7,21,35,35,21,7,1}, {1,8,28,56,70,56,28,8,1}};
	
	
	// used to hold a viable solution 
	private class ProbabilityLine implements Comparable<ProbabilityLine> {
		private int mineCount = 0;
		private BigInteger solutionCount = BigInteger.ZERO;
		private BigInteger[] mineBoxCount  = new BigInteger[boxCount];
		
		private BigInteger[] hashCount  = new BigInteger[boxCount];
		private BigInteger hash = new BigInteger(20, new Random());
		
		{
			for (int i=0; i < mineBoxCount.length; i++) {
				mineBoxCount[i] = BigInteger.ZERO;
				hashCount[i] = BigInteger.ZERO;
			}
		}
		
		@Override
		// sort by the number of mines in the solution
		public int compareTo(ProbabilityLine o) {
			return this.mineCount - o.mineCount;
		}
	}
	
	// used to hold what we need to analyse next
	private class NextWitness {
		
		private Witness witness;
		private List<Box> newBoxes = new ArrayList<>();
		private List<Box> oldBoxes = new ArrayList<>();
		
		private NextWitness(Witness w) {
			
			this.witness = w;
			
			for (Box b: w.getBoxes()) {
				if (b.isProcessed()) {
					oldBoxes.add(b);
				} else {
					newBoxes.add(b);
				}
			}

		}
	
	}
	
	private long duration;
	
	private List<ProbabilityLine> workingProbs = new ArrayList<>(); // as we work through an independent set of witnesses probabilities are held here
 	private List<ProbabilityLine> heldProbs = new ArrayList<>();  
	
	private BigDecimal[] boxProb;
	private BigInteger[] hashTally;
	private boolean offEdgeBest = true;
	private BigDecimal offEdgeProbability;
	private BigDecimal bestProbability;
	private BigDecimal cutoffProbability;

	//when set to true indicates that the box has been part of this analysis
	private boolean[] mask;           
	
	private List<LinkedLocation> linkedLocations = new ArrayList<>();
	private List<LinkedLocation> contraLinkedLocations = new ArrayList<>();
	
	final private BoardState solver;
	final private WitnessWeb web;
	final private int boxCount;
	final private List<Witness> witnesses;
	final private List<Box> boxes;
	final private int minesLeft;                 // number of mines undiscovered in the game
	final private int squaresLeft;               // number of squares undiscovered in the game and off the web
	final private Area deadLocations;
	
	private int independentGroups = 0;
	private int recursions = 0;
	
	private BigInteger finalSolutionsCount;
	
	// these are the limits that can be on the edge
	final private int minTotalMines;
	final private int maxTotalMines;
	
	//final private Set<Integer> mineCounts = new HashSet<>();
	final private Map<Integer, BigInteger> mineCounts = new HashMap<>();
	
	
	public ProbabilityEngine(BoardState solver, WitnessWeb web, int squaresLeft, int minesLeft, Area deadLocations) {
		
		this.solver = solver;
		this.web = web;
		this.minesLeft = minesLeft;
		this.squaresLeft = squaresLeft - web.getSquares().size();
		this.deadLocations = deadLocations;
		
		this.minTotalMines = minesLeft - this.squaresLeft;  //we can't use so few mines that we can't fit the remainder elsewhere on the board
		this.maxTotalMines = minesLeft;    // we can't use more mines than are left in the game
		
		//solver.display("Total mines " + minTotalMines + " to " + maxTotalMines);
		
		this.witnesses = web.getPrunedWitnesses();
		this.boxes = web.getBoxes();
		
		this.boxCount = boxes.size();
		
		this.boxProb = new BigDecimal[boxCount];
		this.hashTally = new BigInteger[boxCount];
		
		for (Witness w: witnesses) {
			w.setProcessed(false);
		}
		
		for (Box b: boxes) {
			b.setProcessed(false);
		}
		
	}

	// run the probability engine
	public void process() {
		
		long startTime = System.currentTimeMillis();
		
		// create an initial solution of no mines anywhere
		ProbabilityLine held = new ProbabilityLine();
		held.solutionCount = BigInteger.ONE;
		heldProbs.add(held);
		
		// add an empty probability line to get us started
		workingProbs.add(new ProbabilityLine());
		
		// create an empty mask - indicating no boxes have been processed
		mask = new boolean[boxCount];           
		
		NextWitness witness = findFirstWitness();
		
		while (witness != null) {
			
			// mark the new boxes as processed - which they will be soon
			for (Box b: witness.newBoxes) {
				mask[b.getUID()] = true;
			}
			
			//System.out.println("Processing " + witness.witness.getLocation().display());
			
			workingProbs = mergeProbabilities(witness);
			
			witness = findNextWitness(witness);
			
		}
		
		// we have now processed all the independent sets of witness and they are being held in our list of list
		
		/*
		for (Box b: boxes) {
			System.out.print(b.getSquares().size() + " ");
		}
		System.out.println("");
		
		// display what we have found
		System.out.println("pre crunch by mine count");
		for (ProbabilityLine pl: probs) {
			System.out.print("Mines = " + pl.mineCount + " solutions = " + pl.solutionCount + " mines: ");
			for (int i=0; i < pl.mineBoxCount.length; i++) {
				System.out.print(" " + pl.mineBoxCount[i]);
			}
			System.out.println("");
		}
		*/
		
		//workingProbs = crunchByMineCount();
		
		
		calculateBoxProbabilities();
		
		/*
		System.out.println("post crunch by mine count");
		// display what we have found
		for (ProbabilityLine pl: probs) {
			System.out.print("Mines = " + pl.mineCount + " solutions = " + pl.solutionCount + " boxes: ");
			for (int i=0; i < pl.mineBoxCount.length; i++) {
				System.out.print(" " + pl.mineBoxCount[i]);
			}
			System.out.println("");
		}
		*/
		
		/*
		for (Box b: boxes) {
			solver.display("Box " + b.getUID() + " has probability " + boxProb[b.getUID()]);
		}
		*/
		
		duration = System.currentTimeMillis() - startTime;
	}
	
	private List<ProbabilityLine> crunchByMineCount(List<ProbabilityLine> target) {
		
		if (target.isEmpty()) {
			return target;
		}
		
		// sort the solutions by number of mines
		Collections.sort(target);
		
		List<ProbabilityLine> result = new ArrayList<>();
		
		int mc = target.get(0).mineCount;
		ProbabilityLine npl = new ProbabilityLine();
		npl.mineCount = mc;
		
		for (ProbabilityLine pl: target) {
			if (pl.mineCount != mc) {
				result.add(npl);
				mc = pl.mineCount;
				npl = new ProbabilityLine();
				npl.mineCount = mc;
			}
			mergeProbabilities(npl, pl);
		}

		//if (npl.mineCount >= minTotalMines) {
			result.add(npl);
		//}	
		
		//solver.display(target.size() + " Probability Lines compressed to " + result.size()); 
			
		return result;
		
	}

	
	// calculate how many ways this solution can be generated and roll them into one
	private void mergeProbabilities(ProbabilityLine npl, ProbabilityLine pl) {
		
		BigInteger solutions = BigInteger.ONE;
		for (int i = 0; i < pl.mineBoxCount.length; i++) {
			solutions = solutions.multiply(BigInteger.valueOf(SMALL_COMBINATIONS[boxes.get(i).getSquares().size()][pl.mineBoxCount[i].intValue()]));
		}

		npl.solutionCount = npl.solutionCount.add(solutions);
		
		for (int i = 0; i < pl.mineBoxCount.length; i++) {
			if (mask[i]) {  // if this box has been involved in this solution - if we don't do this the hash gets corrupted by boxes = 0 mines because they weren't part of this edge
	 			npl.mineBoxCount[i] = npl.mineBoxCount[i].add(pl.mineBoxCount[i].multiply(solutions));
				
				if (pl.mineBoxCount[i].signum() == 0) {
					npl.hashCount[i] = npl.hashCount[i].subtract(pl.hash.multiply(BigInteger.valueOf(boxes.get(i).getSquares().size())));   // treat no mines as -1 rather than zero
				} else {
					npl.hashCount[i] = npl.hashCount[i].add(pl.mineBoxCount[i].multiply(pl.hash));
				}				
			}

			
		}
		
	}
	
	// this combines newly generated probabilities with ones we have already stored from other independent sets of witnesses
	private void storeProbabilities() {
		
		List<ProbabilityLine> result = new ArrayList<>(); 
		
		//if (workingProbs.isEmpty()) {
		//	solver.display("working probabilites list is empty!!");
		//	return;
		//} 
		
		// crunch the new ones down to one line per mine count
		List<ProbabilityLine> crunched = crunchByMineCount(workingProbs);

		//solver.display("New data has " + crunched.size() + " entries");
		
		for (ProbabilityLine pl: crunched) {
			
			for (ProbabilityLine epl: heldProbs) {
				
				ProbabilityLine npl = new ProbabilityLine();
				npl.mineCount = pl.mineCount + epl.mineCount;
				if (npl.mineCount <= maxTotalMines) {
					
					npl.solutionCount = pl.solutionCount.multiply(epl.solutionCount);
					
					for (int i=0; i < npl.mineBoxCount.length; i++) {
						
						BigInteger w1 = pl.mineBoxCount[i].multiply(epl.solutionCount);
						BigInteger w2 = epl.mineBoxCount[i].multiply(pl.solutionCount);
						npl.mineBoxCount[i] = w1.add(w2);
						
						npl.hashCount[i] = epl.hashCount[i].add(pl.hashCount[i]);

					}
					result.add(npl);
					
				}
				
			}
			
		}
	
		// sort into mine order 
		Collections.sort(result);
		
		heldProbs.clear();
		
		// if result is empty this is an impossible position
		if (result.isEmpty()) {
			return;
		}
		
		// and combine them into a single probability line for each mine count
		int mc = result.get(0).mineCount;
		ProbabilityLine npl = new ProbabilityLine();
		npl.mineCount = mc;
		
		for (ProbabilityLine pl: result) {
			if (pl.mineCount != mc) {
				heldProbs.add(npl);
				mc = pl.mineCount;
				npl = new ProbabilityLine();
				npl.mineCount = mc;
			}
			npl.solutionCount = npl.solutionCount.add(pl.solutionCount);
			
			for (int i = 0; i < pl.mineBoxCount.length; i++) {
				npl.mineBoxCount[i] = npl.mineBoxCount[i].add(pl.mineBoxCount[i]);
				
				npl.hashCount[i] = npl.hashCount[i].add(pl.hashCount[i]);
			}
		}

		heldProbs.add(npl);

		/*
		for (Box b: boxes) {
			System.out.print(b.getSquares().size() + " ");
		}
		System.out.println("");
		for (ProbabilityLine pl: heldProbs) {
			System.out.print("Mines = " + pl.mineCount + " solutions = " + pl.solutionCount + " boxes: ");
			for (int i=0; i < pl.mineBoxCount.length; i++) {
				System.out.print(" " + pl.mineBoxCount[i]);
			}
			System.out.println("");
		}
		*/
		
		
	}
	
	// here we expand the localised solution to one across the whole board and
	// sum them together to create a definitive probability for each box
	private void calculateBoxProbabilities() {
		
		BigInteger[] tally = new BigInteger[boxCount];
		for (int i=0; i < tally.length; i++) {
			tally[i] = BigInteger.ZERO;
			hashTally[i] =  BigInteger.ZERO;
		}

		// total game tally
		BigInteger totalTally = BigInteger.ZERO;
		
		// outside a box tally
		BigInteger outsideTally = BigInteger.ZERO;
		
		// calculate how many mines 
		for (ProbabilityLine pl: heldProbs) {
			
			if (pl.mineCount >= minTotalMines) {    // if the mine count for this solution is less than the minimum it can't be valid
				
				if (mineCounts.put(pl.mineCount, pl.solutionCount) != null) {
					System.out.println("Duplicate mines in probabiluity Engine");
				}
					
				
				BigInteger mult = Solver.combination(minesLeft - pl.mineCount, squaresLeft);  //# of ways the rest of the board can be formed
				
				outsideTally = outsideTally.add(mult.multiply(BigInteger.valueOf(minesLeft - pl.mineCount)).multiply(pl.solutionCount));
				
				// this is all the possible ways the mines can be placed across the whole game
				totalTally = totalTally.add(mult.multiply(pl.solutionCount));
				
				for (int i=0; i < tally.length; i++) {
					tally[i] = tally[i].add(mult.multiply(pl.mineBoxCount[i]).divide(BigInteger.valueOf( boxes.get(i).getSquares().size())));
					hashTally[i] = hashTally[i].add(pl.hashCount[i]);
				}				
			}

		}		
		
		
		for (int i=0; i < boxProb.length; i++) {
			if (totalTally.signum() != 0) {
				boxProb[i] = BigDecimal.ONE.subtract(new BigDecimal(tally[i]).divide(new BigDecimal(totalTally), Solver.DP, RoundingMode.HALF_UP));
			} else {
				boxProb[i] = BigDecimal.ZERO;
			}
		}

		for (int i=0; i < hashTally.length; i++) {
			//solver.display(boxes.get(i).getSquares().size() + " " + boxes.get(i).getSquares().get(0).display() + " " + hashTally[i].toString());
			for (int j=i+1; j < hashTally.length; j++) {
				
				BigInteger hash1 = hashTally[i].divide(BigInteger.valueOf(boxes.get(i).getSquares().size()));
				BigInteger hash2 = hashTally[j].divide(BigInteger.valueOf(boxes.get(j).getSquares().size()));
				
				//if (hashTally[i].compareTo(hashTally[j]) == 0 && boxes.get(i).getSquares().size() == 1 && boxes.get(j).getSquares().size() == 1) {
				if (hash1.compareTo(hash2) == 0) {
					addLinkedLocation(linkedLocations, boxes.get(i), boxes.get(j));
					addLinkedLocation(linkedLocations, boxes.get(j), boxes.get(i));
					//solver.display("Box " + boxes.get(i).getSquares().get(0).display() + " is linked to Box " + boxes.get(j).getSquares().get(0).display() + " prob " + boxProb[i]);
				}
				
				// if one hasTally is the negative of the other then   i flag <=> j clear
				//if (hashTally[i].compareTo(hashTally[j].negate()) == 0 && boxes.get(i).getSquares().size() == 1 && boxes.get(j).getSquares().size() == 1) {
				if (hash1.compareTo(hash2.negate()) == 0) {
					//solver.display("Box " + boxes.get(i).getSquares().get(0).display() + " is contra linked to Box " + boxes.get(j).getSquares().get(0).display() + " prob " + boxProb[i] + " " + boxProb[j]);
					addLinkedLocation(contraLinkedLocations, boxes.get(i), boxes.get(j));
					addLinkedLocation(contraLinkedLocations, boxes.get(j), boxes.get(i));					
				}
			}
		}
		
		// sort so that the locations with the most links are at the top
		Collections.sort(linkedLocations, LinkedLocation.SORT_BY_LINKS_DESC);
		
		// avoid divide by zero
		if (squaresLeft != 0 && totalTally.signum() != 0) {
			offEdgeProbability = BigDecimal.ONE.subtract(new BigDecimal(outsideTally).divide(new BigDecimal(totalTally), Solver.DP, RoundingMode.HALF_UP).divide(new BigDecimal(squaresLeft), Solver.DP, RoundingMode.HALF_UP));
		} else {
			offEdgeProbability = BigDecimal.ZERO;
		}
	
		finalSolutionsCount = totalTally;

		// see if we can find a guess which is better than outside the boxes
		BigDecimal hwm = offEdgeProbability;
		
		offEdgeBest = true;
		
		for (Box b: boxes) {
			boolean living = false;
			for (Square squ: b.getSquares()) {
				if (!deadLocations.contains(squ)) {
					living = true;
					break;
				}
			}
			BigDecimal prob = boxProb[b.getUID()];
			if (living || prob.compareTo(BigDecimal.ONE) == 0) {   // if living or 100% safe then consider this probability
				
				if (hwm.compareTo(prob) <= 0) {
					offEdgeBest = false;
					hwm = prob;
				}				
			}
		}
		
		//for (BigDecimal bd: boxProb) {
		//	if (hwm.compareTo(bd) <= 0) {
		//		offEdgeBest = false;
		//		hwm = bd;
		//	}
		//	hwm = hwm.max(bd);
		//}
		
		bestProbability = hwm;
		
		if (bestProbability.compareTo(BigDecimal.ONE) == 0) {
			cutoffProbability = BigDecimal.ONE;
		} else {
			cutoffProbability = bestProbability.multiply(Solver.PROB_ENGINE_TOLERENCE);
		}
		
		
		//solver.display("probability off web is " + outsideProb);
		
		
	}
	
	
	private void addLinkedLocation(List<LinkedLocation> list, Box box, Box linkTo) {
		
		top:for (Square s: box.getSquares()) {
			
			for (LinkedLocation ll: list) {
				if (s.equals(ll)) {
					ll.incrementLinks(linkTo.getSquares());
					continue top;
				}
			}		
			
			list.add(new LinkedLocation(s.x, s.y, linkTo.getSquares()));
		}
		
	}
	
	
	
	private List<ProbabilityLine> mergeProbabilities(NextWitness nw) {
		
		List<ProbabilityLine> newProbs = new ArrayList<>();
		
		for (ProbabilityLine pl: workingProbs) {
			
			int missingMines = nw.witness.getMines() - countPlacedMines(pl, nw);
			
			if (missingMines < 0) {
				// too many mines placed around this witness previously, so this probability can't be valid
			} else if (missingMines == 0) {
				newProbs.add(pl);   // witness already exactly satisfied, so nothing to do
			} else if (nw.newBoxes.isEmpty()) {
				// nowhere to put the new mines, so this probability can't be valid
			} else {
				newProbs.addAll(distributeMissingMines(pl, nw, missingMines, 0));
			}
	
		}
		
		return newProbs;
		
	}
	
	// this is used to recursively place the missing Mines into the available boxes for the probability line
	private List<ProbabilityLine> distributeMissingMines(ProbabilityLine pl, NextWitness nw, int missingMines, int index) {
		
		recursions++;
		if (recursions % 10000 == 0) {
			solver.display("Probability Engine recursision = " + recursions);
		}
		
		List<ProbabilityLine> result = new ArrayList<>();
		
		// if there is only one box left to put the missing mines we have reach this end of this branch of recursion
		if (nw.newBoxes.size() - index == 1) {
			// if there are too many for this box then the probability can't be valid
			if (nw.newBoxes.get(index).getMaxMines() < missingMines) {
				return result;
			}
			// if there are too few for this box then the probability can't be valid
			if (nw.newBoxes.get(index).getMinMines() > missingMines) {
				return result;
			}
			// if there are too many for this game then the probability can't be valid
			if (pl.mineCount + missingMines > maxTotalMines) {
				return result;
			}			
			
			// otherwise place the mines in the probability line
			pl.mineBoxCount[nw.newBoxes.get(index).getUID()] = BigInteger.valueOf(missingMines);
			pl.mineCount = pl.mineCount + missingMines;
			result.add(pl);
			return result;
		}
		
		
		// this is the recursion
		int maxToPlace = Math.min(nw.newBoxes.get(index).getMaxMines(), missingMines);
		
		for (int i=nw.newBoxes.get(index).getMinMines(); i <= maxToPlace; i++) {
			ProbabilityLine npl = extendProbabilityLine(pl, nw.newBoxes.get(index), i);
			
			result.addAll(distributeMissingMines(npl, nw, missingMines - i, index + 1));
		}
		
		return result;
		
	}
	
	// create a new probability line by taking the old and adding the mines to the new Box
	private ProbabilityLine extendProbabilityLine(ProbabilityLine pl, Box newBox, int mines) {
		
		ProbabilityLine result = new ProbabilityLine();
		
		result.mineCount = pl.mineCount + mines;
		//result.solutionCount = pl.solutionCount;
		
		// copy the probability array
		System.arraycopy(pl.mineBoxCount, 0, result.mineBoxCount, 0, pl.mineBoxCount.length);
		
		result.mineBoxCount[newBox.getUID()] = BigInteger.valueOf(mines);
		
		return result;
	}
	
	// counts the number of mines already placed
	private int countPlacedMines(ProbabilityLine pl, NextWitness nw) {
		
		int result = 0;
		
		for (Box b: nw.oldBoxes) {
			result = result + pl.mineBoxCount[b.getUID()].intValue();
		}
		
		return result;
	}
	
	// return any witness which hasn't been processed
	private NextWitness findFirstWitness() {
		
		for (Witness w: witnesses) {
			if (!w.isProcessed()) {
				return new NextWitness(w);
			}
		}
		
		// if we are here all witness have been processed
		return null;

	}
	
	
	// look for the next witness to process
	private NextWitness findNextWitness(NextWitness prevWitness) {
		
		// flag the last set of details as processed
		prevWitness.witness.setProcessed(true);
		for (Box b: prevWitness.newBoxes) {
			b.setProcessed(true);
		}

		int bestTodo = 99999;
		Witness bestWitness = null;
		
		// and find a witness which is on the boundary of what has already been processed
		for (Box b: boxes) {
			if (b.isProcessed()) {
				for (Witness w: b.getWitnesses()) {
					if (!w.isProcessed()) {
						int todo = 0;
						for (Box b1: w.getBoxes()) {
							if (!b1.isProcessed()) {
								todo++;
							}
						}
						if (todo == 0) {
							return new NextWitness(w);
						} else if (todo < bestTodo) {
							bestTodo = todo;
							bestWitness = w;
						}
					}
				}
			}
		}
		
		if (bestWitness != null) {
			return new NextWitness(bestWitness);
		}
		
		// if we are down here then there is no witness which is on the boundary, so we have processed a complete set of independent witnesses 
		
		independentGroups++;
		
		// since we have calculated all the mines in an independent set of witnesses we can crunch them down and store them for later
		
		// get an unprocessed witness
		NextWitness nw =  findFirstWitness();
		
		// only crunch it down for non-trivial probability lines unless it is the last set - this is an efficiency decision
		if (workingProbs.size() > 1 || nw == null) {
			storeProbabilities();
			
			// reset the working array so we can start building up one for the new set of witnesses
			workingProbs.clear();
			workingProbs.add(new ProbabilityLine());
			
			// reset the mask indicating that no boxes have been processed 
			mask = new boolean[boxCount]; 
		}

		// return the next witness to process
		return nw;
		
	}
	
	protected BigDecimal getProbability(Location l) {
		
		for (Box b: boxes) {
			if (b.contains(l)) {
				return boxProb[b.getUID()];
			}
		}
		
		return offEdgeProbability;
	}
	
	/**
	 * The probability of a mine being in a square not considered by this process
	 * @return
	 */
	protected BigDecimal getOffEdgeProb() {
		return offEdgeProbability;
	}
	
	/**
	 * The probability of a mine being in a square not considered by this process
	 * @return
	 */
	protected BigDecimal getBestOnEdgeProb() {
		return bestProbability;
	}
	
	/**
	 * Moves with probability above this are candidate moves
	 * @return
	 */
	//protected BigDecimal geCutoffProb() {
	//	return cutoffProbability;
	//}
	
	protected boolean isBestGuessOffEdge() {
		return this.offEdgeBest;
	}
	
	/**
	 * true if a 100% certain move has been found
	 * @return
	 */
	protected boolean foundCertainty() {
		return (bestProbability.compareTo(BigDecimal.ONE) == 0);
	}
	
	
	
	protected List<CandidateLocation> getBestCandidates(BigDecimal freshhold) {
		
		List<CandidateLocation> best = new ArrayList<>();
		
		//solver.display("Squares left " + this.squaresLeft + " squares analysed " + web.getSquares().size());
		
		// if the outside probability is the best then return an empty list
		BigDecimal test;
		//if (offEdgeBest) {
		//	solver.display("Best probability is off the edge " + bestProbability + " but will look for options on the edge only slightly worse");
		//	//test = bestProbability.multiply(Solver.EDGE_TOLERENCE);
		//	test = bestProbability.multiply(freshhold);
		//} else 
		
		if (bestProbability.compareTo(BigDecimal.ONE) == 0){  // if we have a probability of one then don't allow lesser probs to get a look in
			test = bestProbability;
		} else {
			test = bestProbability.multiply(freshhold);
		}

		solver.display("Best probability is " + bestProbability + " freshhold is " + test);
		
		for (int i=0; i < boxProb.length; i++) {
			if (boxProb[i].compareTo(test) >= 0) {
				for (Square squ: boxes.get(i).getSquares()) {
					if (!deadLocations.contains(squ) || boxProb[i].compareTo(BigDecimal.ONE) == 0) {  // if not a dead location or 100% safe then use it
						best.add(new CandidateLocation(squ.x, squ.y, boxProb[i], solver.countAdjacentUnrevealed(squ), solver.countAdjacentConfirmedFlags(squ)));
					} else {
						solver.display("Location " + squ.display() + " is ignored because it is dead");
					}
				}
			}
		}
		
		// sort in to best order
		best.sort(CandidateLocation.SORT_BY_PROB_FLAG_FREE);
		
		return best;
		
	}
	
	/**
	 * The number of ways the mines can be placed in the game position
	 * @return
	 */
	protected BigInteger getSolutionCount() {
		return finalSolutionsCount;
	}
	
	/**
	 * The duration to do the processing in milliseconds
	 * @return
	 */
	protected long getDuration() {
		return this.duration;
	}
	
	/**
	 * How many independent groups we encountered in the processing
	 * @return
	 */
	protected long getIndependentGroups() {
		return this.independentGroups;
	}
	
	protected List<LinkedLocation> getLinkedLocations() {
		return this.linkedLocations;
	}
	
	/**
	 * If this tile is a linked Location return the number of links it has, otherwise return 0
	 * @param tile
	 * @return
	 */
	protected LinkedLocation getLinkedLocation(Location tile) {
		for (LinkedLocation ll: linkedLocations) {
			if (ll.equals(tile)) {
				return ll;
			}
		}
		
		return null;
		
	}
	
	protected List<LinkedLocation> getContraLinkedLocations() {
		return this.contraLinkedLocations;
	}
	
	protected Map<Integer, BigInteger> getValidMineCounts() {
		return mineCounts;
	}
	
}
