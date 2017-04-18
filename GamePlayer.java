import java.io.Serializable;

/**
 * Game Player Class.
 * Member variables are about the information of a player: name, password, # of wins, # of games, and avg_time, rank.
 * Member methods are getters and setters of member variables.
 * @author HAORAN
 *
 */
public class GamePlayer implements Serializable {
	private static final long serialVersionUID = 5565697024080360666L;
	private String userName;
	private int number_wins;
	private int number_games;
	private float avg_time;
	private int rank;
	
	public GamePlayer() {
		
	}
	
	public GamePlayer(String userName, int number_wins, int number_games, float avg_time, int rank) {
		this.userName = userName;
		this.number_wins = number_wins;
		this.number_games = number_games;
		this.avg_time = avg_time;
		this.rank = rank;
	}
	
	public String getUserName() {
		return userName;
	}
	
	public void setUserName(String name) {
		userName = name;
	}
	
	public int getNumOfWins() {
		return number_wins;
	}
	
	public void setNumOfWins(int number_wins) {
		this.number_wins = number_wins;
	}
	
	public int getNumOfGames() {
		return number_games;
	}
	
	public void setNumberOfGames(int number_games) {
		this.number_games = number_games;
	}
	
	public float getAverageTime() {
		return this.avg_time;
	}
	
	public void setAverageTime(float avg_time) {
		this.avg_time = avg_time;
	}
	
	public int getRank() {
		return rank;
	}
	
	public void setRank(int rank) {
		this.rank = rank;
	}
}
