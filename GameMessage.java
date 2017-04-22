import java.io.Serializable;
import java.util.ArrayList;

/**
 * Message class has all attributes of a communication message between clients and the server.
 * Valid Identifications for the server are: "USER_PROFILEPREG" "USER_PROFILE" "LEADER_BOARD" "NEW_GAME" "QUIT_GAME" "SUBMIT" "UPDATE"
 * Valid Identifications for the client are: "USER_PROFILE" "LEADER_BOARD" "FULL" "WAIT" "START" "QUIT" "RESULT" "OVER"
 * @author HAORAN
 *
 */
public class GameMessage implements Serializable {
	private static final long serialVersionUID = -2197094784832194714L;
	
	private String identification;  // specify what the message is used for
	private String to;              // used for sending private message
	private String userName;
	private String password;
	
	private GamePlayer gamePlayer;
	
	private ArrayList<GamePlayer> playerList;
	
	private ArrayList<String> gameCards;
	
	private String answer;          // the answer submitted by the user
	private long timeUsed;          // time used to come up with this answer
	
	private double result;          // what's the result of the expression submitted by the player
	private String winner;          // the winner's user_name if it is the case
	private long endTime;           // the end time for the winner
	
	private boolean inGame;         // whether in game or not when log out
	private boolean isWaiting;      // whether is waiting stage or not when log out
	
	public GameMessage() {
		identification = null;
	}
	
	public GameMessage(String id, String to, String userName, String password) {
		this.identification = id;
		this.to = to;
		this.userName = userName;
		this.password = password;
	}
	
	public String getID() {
		return identification;
	}
	
	public void setID(String id) {
		identification = id;
	}
	
	public String getTo() {
		return to;
	}
	
	public void setTo(String to) {
		this.to = to;
	}
	
	public String getUserName() {
		return userName;
	}
	
	public void setUserName(String name) {
		userName = name;
	}
	
	public String getPassword() {
		return password;
	}
	
	public void setPassword(String password) {
		this.password = password;
	}
	
	public GamePlayer getGamePlayer() {
		return gamePlayer;
	}
	
	public void setGamePlayer(GamePlayer newPlayer) {
		this.gamePlayer = newPlayer;
	}
	
	public ArrayList<GamePlayer> getPlayerList() {
		return this.playerList;
	}
	
	public void setPlayerList(ArrayList<GamePlayer> playerList) {
		this.playerList = playerList;
	}
	
	public ArrayList<String> getGameCards() {
		return gameCards;
	}
	
	public void setGameCards(ArrayList<String> gameCards) {
		this.gameCards = gameCards;
	}
	
	public String getAnswer() {
		return answer;
	}
	
	public void setAnswer(String answer) {
		this.answer = answer;
	}
	
	public long getTimeUsed() {
		return timeUsed;
	}
	
	public void setTimeUsed(long timeUsed) {
		this.timeUsed = timeUsed;
	}
	
	public double getResult() {
		return this.result;
	}
	
	public void setResult(double trueResult) {
		this.result = trueResult;
	}
	
	public String getWinner() {
		return winner;
	}
	
	public void setWinner(String winner) {
		this.winner = winner;
	}

	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}
	
	public String toString() {
		String stringForm;
		if (userName == null)
			stringForm = "Message ID: " + identification;
		else 
			stringForm = "Message ID: " + identification + "; Username: " + userName;
		if (to != null)
			stringForm = stringForm + "; To: " + to;
		return stringForm;
	}

	public boolean isInGame() {
		return inGame;
	}

	public void setInGame(boolean inGame) {
		this.inGame = inGame;
	}

	public boolean isWaiting() {
		return isWaiting;
	}

	public void setWaiting(boolean isWaiting) {
		this.isWaiting = isWaiting;
	}

}
