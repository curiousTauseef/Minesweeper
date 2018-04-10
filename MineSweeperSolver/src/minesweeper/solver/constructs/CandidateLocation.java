package minesweeper.solver.constructs;

import java.math.BigDecimal;
import java.util.Comparator;

import minesweeper.gamestate.Action;
import minesweeper.gamestate.Location;
import minesweeper.solver.Solver;
public class CandidateLocation extends Location {
	
	private final BigDecimal prob;
	private String description = "";
	private final int adjSquares;
	private final int adjFlags;
	private final int count;  // the number of different values this square can be (other than a mine)
	
	public CandidateLocation(int x, int y, BigDecimal prob, int adjSquares, int adjFlags) {
		this(x,y, prob, adjSquares, adjFlags, 0);
		
	}

	public CandidateLocation(int x, int y, BigDecimal prob, int adjSquares, int adjFlags, int count) {
		super(x,y);
		
		this.prob = prob;
		this.adjSquares = adjSquares;
		this.adjFlags = adjFlags;
		this.count = count;
		
	}
	
	public BigDecimal getProbability() {
		return this.prob;
	}
	
	public int getCount() {
		return this.count;
	}
	
	public void setDescription(String desc) {
		this.description = desc;
	}

	public void appendDescription(String desc) {
		if (this.description != "") {
			this.description = this.description + " " + desc;
		} else {
			this.description = desc;
		}
		
	}
	
	public String getDescription() {
		return this.description;
	}
	
	public Action buildAction(int method) {
		
        String comment = Solver.METHOD[method] + description;
        
        return new Action(this, Action.CLEAR, method, comment, prob);		
		
	}
	
	/**
	 * This sorts by highest probability of not being a mine then the number of adjacent flags, unrevealed squares and finally Location order
	 */
	static public final Comparator<CandidateLocation> SORT_BY_PROB_FLAG_FREE  = new Comparator<CandidateLocation>() {
		@Override
		public int compare(CandidateLocation o1, CandidateLocation o2) {
			
			int c = 0;
			
			c = -o1.prob.compareTo(o2.prob);  // highest probability first
			if (c == 0) {
				c = -(o1.count  - o2.count);
				if (c == 0) {
					c = -(o1.adjFlags - o2.adjFlags);  // highest number of flags 2nd
					if (c == 0) {
						c=  o1.adjSquares - o2.adjSquares;  // lowest adjacent free squares
						if (c == 0) {
							c = o1.sortOrder - o2.sortOrder;  // location order
						}
					}
				}
			}
			
			return c;
		
		}
	};
	
	/**
	 * This sorts by highest probability of not being a mine then the number unrevealed squares (lowest first), then of adjacent flags (highest first) ,and finally Location order
	 */
	static public final Comparator<CandidateLocation> SORT_BY_PROB_FREE_FLAG  = new Comparator<CandidateLocation>() {
		@Override
		public int compare(CandidateLocation o1, CandidateLocation o2) {
			
			int c = -o1.prob.compareTo(o2.prob);  // highest probability first
			if (c == 0) {
				c=  o1.adjSquares - o2.adjSquares;   // lowest adjacent free squares
				if (c == 0) {
					c = -(o1.adjFlags - o2.adjFlags);  // highest number of flags 
					if (c == 0) {
						c = o1.sortOrder - o2.sortOrder;  // location order
					}
				}
			}
			
			return c;
		
		}
	};
	
}
