package minesweeper.solver.settings;

import java.math.BigInteger;


public class SolverSettings {

	protected int bruteForceMaxSolutions = 400;
	protected int bruteForceMaxNodes = 50000;
	protected int bruteForceTreeDepth = 50;
    protected BigInteger bruteForceMaxIterations = new BigInteger("50000000");  // 50 million

    protected boolean doTiebreak = true;
	
    private boolean locked;
    
    public SolverSettings lockSettings() {
    	locked = true;
    	
    	return this;
    }
    
    public SolverSettings setTieBreak(boolean doTiebreak) {
    	
    	if (!locked) {
        	this.doTiebreak = doTiebreak;
    	}
 
    	return this;
    }

	public int getBruteForceMaxSolutions() {
		return bruteForceMaxSolutions;
	}

	public int getBruteForceMaxNodes() {
		return bruteForceMaxNodes;
	}

	public int getBruteForceTreeDepth() {
		return bruteForceTreeDepth;
	}

	public BigInteger getBruteForceMaxIterations() {
		return bruteForceMaxIterations;
	}

	public boolean isDoTiebreak() {
		return doTiebreak;
	}

	public boolean isLocked() {
		return locked;
	}
    
}
