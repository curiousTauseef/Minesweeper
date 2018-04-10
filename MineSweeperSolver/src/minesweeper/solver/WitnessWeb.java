/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package minesweeper.solver;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import minesweeper.gamestate.Location;
import minesweeper.solver.constructs.Box;
import minesweeper.solver.constructs.Square;
import minesweeper.solver.constructs.Witness;

/**
 * A witness web is a construct which holds the connectivity information of the game. 
 * @author David
 */
public class WitnessWeb {
    
    final private List<Witness> witnesses = new ArrayList<>();
    final private List<Square> squares = new ArrayList<>();
    
    final private List<Box> boxes = new ArrayList<>();
    
    final private List<Witness> independentWitness = new ArrayList<>();
    private int independentMines;
    private BigInteger independentIterations = BigInteger.ONE;
    private int remainingSquares;
    
    private BoardState solver;
    
    private int pruned = 0;
    
    private int webNum = 0;
    
    private List<CrunchResult> solutions = new ArrayList<>();
    
    public WitnessWeb(BoardState solver, List<Location> allWit, List<Location> allSqu) {
        
        this.solver = solver;
        
        //GameStateModel gs = solver.getGame();

        // create squares for all the Square locations provided
        for (Location squ: allSqu) {
            squares.add(new Square(squ));
        }     
        
        // create witnesses for all the Witness locations provided
        // and attach adjacent Squares
        List<Square> adjSqu = new ArrayList<>();
        for (Location wit: allWit) {
            // calculate how many mines are left to find
            //int mines = gs.query(wit) - solver.countConfirmedFlags(wit);
            int mines = solver.getWitnessValue(wit) - solver.countAdjacentConfirmedFlags(wit);
            adjSqu.clear();
            //Witness newWit = new Witness(wit, mines);
            
            for (Square squ: squares) {
                if (squ.isAdjacent(wit)) {
                	adjSqu.add(squ);
                    //newWit.addSquare(squ);
                }
            }
            addWitness(new Witness(wit, mines, adjSqu));
        }        
        
        // this sorts the witnesses by the number of iterations around them
        Collections.sort(witnesses, Witness.SORT_BY_ITERATIONS_DESC);        
        
        // now attach non-pruned witnesses to adjacent Squares
        for (Witness wit: witnesses) {
            //System.out.println("Witness " + wit.getLocation().display() + " has " + wit.getSquares().size() + " squares");
            for (Square squ: squares) {
                if (squ.isAdjacent(wit)) {
                    squ.addWitness(wit);
                }
            }
            //solver.display(wit.getLocation().display() + " has " + wit.getMines() + " Mines to find " + wit.getSquares().size() + " adjacent squares");
        }                

        // after this we should have a web of witnesses and Squares

        remainingSquares = this.squares.size();
        
        // find the witnesses which don't share any squares
        for (Witness w: witnesses) {
            boolean okay = true;
            for (Witness iw: independentWitness) {
                if (w.overlap(iw)) {
                    okay = false;
                    break;
                }
            }
            if (okay) {
                remainingSquares = remainingSquares - w.getSquares().size();
                independentIterations = independentIterations.multiply(Solver.combination(w.getMines(), w.getSquares().size()));
                independentMines = independentMines + w.getMines();
                independentWitness.add(w);
            }
        }
       
        //System.out.println("Independent iterations = " + independentIterations);
        //System.out.println("remaining Squares = " + remainingSquares);
        
        /*
        for (Witness wit: independentWitness) {
            System.out.println("Independent Witness " + wit.getLocation().display() + " has " + wit.getSquares().size() + " squares");
        }
        */
        
        // determine how many subwebs we have
        webNum = 0;
        for (Square squ: squares) {
            if (squ.getWebNum() == 0) {
                webNum++;
                setWeb(squ, webNum);
            }
        }
        
        if (webNum > 1) {
            solver.display("There are " + witnesses.size() + " witnesses (" + pruned + " were pruned) and " + squares.size() + " squares across " + webNum + " subwebs");
 
        } else {
            solver.display("There are " + witnesses.size() + " witnesses (" + pruned + " were pruned) and " + squares.size() + " squares");
        }

        int boxCount = 0;
        // put each square in a box
        for (Square squ: squares) {
        	boolean found = false;
        	// see if the square fits an existing box
        	for (Box b: boxes) {
        		if (b.fitsBox(squ)) {
        			b.addSquare(squ);
        			found = true;
        			break;
        		}
        	}
        	// if not create a new box for it
        	if (!found) {
        		boxes.add(new Box(squ, boxCount));
        		boxCount++;
        	}
        }
        
        
        int minesLeft = solver.getMines() - solver.getConfirmedFlagCount();
    	for (Box b: boxes) {
    		b.calculate(minesLeft);
    		//b.display();
    	}
        
    }
    
   
    private void setWeb(Square squ, int num) {
        
        if (squ.getWebNum() != 0 && squ.getWebNum() != num) {
            System.err.println("Square already assigned to a different web!!!!");
        }
        
        // if the square is already part of this web then no more to do here
        if (squ.getWebNum() == num) {
            return;
        }
        
        // claim this square
        squ.setWebNum(num);
        
        // claim all the Witnesses around this square and
        // recursively claim all the other squares around those witnesses
        for (Witness w: squ.getWitnesses()) {
            w.setWebNum(num);
            for (Square s: w.getSquares()) {
                setWeb(s, num);
            }
        }

    }
    
    private void addWitness(Witness wit) {
        
        // if the witness is a duplicate then don't store it
        for (Witness w: witnesses) {
            if (w.equivalent(wit)) {
                pruned++;
                return;
            }
        }
        
        witnesses.add(wit);
        
    }
    
    /**
     * Returns the number of mines around the independent witnesses. The number of mines in any solution can't be less than this.
     * @return
     */
    public int getMinesPlaced() {
        
        return independentMines;
        
    }
    
    public List<Square> getSquares() {
        
        return squares;
        
    }
    
    
    public List<Witness> getWitnesses() {
        
        return witnesses;
        
    }
    
    public List<Witness> getIndependentWitnesses() {
        
        return independentWitness;
        
    }
        
    
    public List<Box> getBoxes() {
    	return this.boxes;
    }
    
    
    // how many iterations will be required to process this web with the provided number of mines
    public BigInteger getIterations(int mines) {
        
        // if too many or too few mines then no work needs to be done
        if (mines < independentMines || mines > independentMines + remainingSquares) {
            return BigInteger.ZERO;
        }
        
        BigInteger result = independentIterations.multiply(Solver.combination(mines - independentMines, remainingSquares));
        
        return result;
        
    }
    
    /**
     * The number of ways the non-independent squares and mines can be arranged
     * @param mines
     * @return
     */
    public BigInteger getNonIndependentIterations(int mines) {
    	
        // if too many or too few mines then no work needs to be done
        if (mines < independentMines || mines > independentMines + remainingSquares) {
            return BigInteger.ZERO;
        }
        
        BigInteger result = Solver.combination(mines - independentMines, remainingSquares);
        
        return result;    	
    	
    }
    
    
    
    public int getSharedSquares() {
        return remainingSquares;
    }
    
    public int getIndependentMines() {
        return independentMines;
    }
        
    
    public List<WitnessWeb> getSubWebs() {
        
        List<WitnessWeb> result = new ArrayList<>();
                
        if (webNum == 1) {
            result.add(this);
        } else {
            for (int i=0; i < webNum; i++) {
                result.add(createSubWeb(i+1));
            }            
        }

        return result;
        
    }
    
    
    // create a WitnessWeb from a sub web of this one
    public WitnessWeb createSubWeb(int n) {
        
        if (n < 1 || n > webNum ) {
            System.err.println("requesting sub-web " + n + " of ( 1  to " + webNum + ")");
        }
        
        List<Location> wit = new ArrayList<Location>();
        List<Location> squ = new ArrayList<Location>();
        
        for (Witness w: witnesses) {
            if (w.getWebNum() == n) {
                wit.add(w);
            }
        }
        for (Square s: squares) {
            if (s.getWebNum() == n) {
                squ.add(s);
            }
        }        
        
        WitnessWeb result = new WitnessWeb(solver, wit, squ);
        
        return result;
        
    }
    
    // if the location passed is a square in the web then return true;
    public boolean isOnWeb(Location l) {
        
        for (Square s: squares) {
            if (s.equals(l)) {
                return true;
            }
        }
        
        return false;
        
    }
    
    public void addSolution(CrunchResult e) {
        solutions.add(e);
    }
    
    public List<CrunchResult> getSolutions() {
        return solutions;
    }
    
}
