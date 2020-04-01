package minesweeper.explorer.main;

import minesweeper.gamestate.GameFactory;
import minesweeper.gamestate.GameStateModel;
import minesweeper.random.DefaultRNG;
import minesweeper.random.RNG;
import minesweeper.settings.GameSettings;
import minesweeper.settings.GameType;
import minesweeper.solver.Preferences;
import minesweeper.solver.RolloutGenerator;
import minesweeper.solver.Solver;
import minesweeper.structure.Action;
import minesweeper.structure.Location;

public class BulkRunner implements Runnable {
	
	private boolean stop = false;
	private final int maxSteps;
	//private final BulkController controller;
	private final Location startLocation;
	private final RolloutGenerator rollout;
	
	//private final Random seeder;
	private int steps = 0;
	private int wins = 0;
	
	//private ResultsController resultsController;
	private boolean showGames;
	private boolean winsOnly;
	
	public BulkRunner(int iterations, RolloutGenerator rollout, Location startLocation) {
		
		this.maxSteps = iterations;
		this.rollout = rollout;
		this.startLocation = startLocation;
		
		if (showGames) {
			//resultsController = ResultsController.launch(null, gameSettings, gameType);
		}
		
		
	}

	@Override
	public void run() {
		
		System.out.println("At BulkRunner run method");
		
		while (!stop && steps < maxSteps) {
			
			steps++;
			
			GameStateModel gs = rollout.generateGame();

			Solver solver = new Solver(gs, Preferences.SMALL_ANALYSIS, false);
			
			
			gs.doAction(new Action(startLocation,Action.CLEAR));
			int state = gs.getGameState();

			boolean win;
			if (state == GameStateModel.LOST || state == GameStateModel.WON) {  // if we have won or lost on the first move nothing more to do
				win = (state == GameStateModel.WON);
			} else { // otherwise use the solver to play the game
				 win = playGame(gs, solver);
			}
		
			if (win) {
				wins++;
			}

			/*
			if (showGames && (win || !win && !winsOnly)) {
				if (!resultsController.update(gs)) {  // this returns false if the window has been closed
					showGames = false;
					resultsController = null;
					System.out.println("Results window has been closed... will no longer send data to it");
				}
			}
			*/
			
			//controller.update(steps, maxSteps, wins);
			
		}
		
		stop = true;
		System.out.println("BulkRunner run method ending with wins = " +  wins + " of " + steps);
		
	}
	
	
	private boolean playGame(GameStateModel gs, Solver solver) {

		int state;
		
		play: while (true) {

			Action[] moves;
			try {
				solver.start();
				moves = solver.getResult();
			} catch (Exception e) {
				System.out.println("Game " + gs.showGameKey() + " has thrown an exception!");
				stop = true;
				return false;
			}

			if (moves.length == 0) {
				System.err.println("No moves returned by the solver for game " + gs.showGameKey());
				stop = true;
				return false;
			}            

			// play all the moves until all done, or the game is won or lost
			for (int i=0; i < moves.length; i++) {

				boolean result = gs.doAction(moves[i]);

				state = gs.getGameState();

				if (state == GameStateModel.LOST || state == GameStateModel.WON) {
					break play;
				}
			}            
		}


		if (state == GameStateModel.LOST) {
			return false;
		} else {
			return true;
		}


	}
	
	public void forceStop() {
		System.out.println("Bulk run being requested to stop");
		
		stop = true;
	}
	
	public boolean isFinished() {
		return stop;
	}
	
	public int getWins() {
		return wins;
	}

}
