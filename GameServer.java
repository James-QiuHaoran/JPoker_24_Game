import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Stack;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.naming.NamingException;

/**
 * Server side of 24-Game.
 * # RMI is used to handle user registration and login.
 * # JMS is used to handle the communication between clients and the server.
 * # JDBC is used to handle data retrieval and storage.
 * @author HAORAN
 * @version 2.0
 * @since 2017.4.2
 *
 */
public class GameServer extends UnicastRemoteObject implements Game {

	// Default serial version UID.
	private static final long serialVersionUID = 1L;
	
	// Set up MySQL login
	private static final String DB_HOST = "localhost";
	private static final String DB_USER = "root";
	private static final String DB_PASS = "c0402PASS";
	private static final String DB_NAME = "24Game";
	private static final String DB_TABLE_NAME = "24Game_UserList";
	
	private Connection conn;
	private JMSHelper jmsHelper;
	
	private ArrayList<GamePlayer> current_players;  // 4 current players
	private ArrayList<String> gameNumbers;          // 4 cards used in a game
	private ArrayList<GamePlayer> leader_players;   // up to 10 leading players
	private ArrayList<GamePlayer> all_players;      // all registered player
	private int status;                             // 1 for waiting for start; 2 for gaming
	private long startTime;                         // the system time when the first player joins the game OR when there lefts one player after other players quit at the waiting stage
	private boolean tenSecondsHasPassed;            // a flag marking whether ten seconds has passed after the startTime
	
	public GameServer() throws RemoteException, SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, NamingException, JMSException {
		// Set up JDBC connection
		Class.forName("com.mysql.jdbc.Driver").newInstance();
		conn = DriverManager.getConnection("jdbc:mysql://"+DB_HOST+
															"/"+DB_NAME+
															"?user="+DB_USER+
															"&password="+DB_PASS);
		System.out.println("Database connection successful.");
		
		jmsHelper = new JMSHelper();
		status = 0;
		tenSecondsHasPassed = false;
		current_players = new ArrayList<GamePlayer>();
		leader_players = new ArrayList<GamePlayer>();
		all_players = new ArrayList<GamePlayer>();
	}
	
	/**
	 * Receive message from clients, and broadcast message (the result) to all the clients if someone hits the answer.
	 * Otherwise, give private message to particular clients who submit their answers.
	 * @throws JMSException
	 */
	public void go() throws JMSException {
		MessageConsumer queueReader = jmsHelper.createQueueReader();
		
		while (true) {
			GameMessage outMessage = new GameMessage(); // message to be sent from the server
			
			Message jmsMessage = receiveMessage(queueReader);
			GameMessage gameMessage = (GameMessage)((ObjectMessage)jmsMessage).getObject();
			
			boolean noOutMessage = false;
	        if(gameMessage.getID() != null && !gameMessage.getID().isEmpty()) {
	        	String msg_id = gameMessage.getID();
	        	if (msg_id.equals("USER_PROFILE_REG")) {
	        		// handle registration of a new user
	        		// insert the record to the database
	        		System.out.println("Receive Message - register user profile: " + gameMessage.getUserName());
	        		GamePlayer onRequest = read(gameMessage.getUserName());
	        		outMessage.setGamePlayer(onRequest);
	        		outMessage.setID("USER_PROFILE");
	        		outMessage.setTo(gameMessage.getUserName());
	        	} else if (msg_id.equals("USER_PROFILE")) {
	        		// get the information from the database
	        		// send the information to that particular user
	        		System.out.println("Receive Message - user profile: " + gameMessage.getUserName());
	        		GamePlayer onRequest = read(gameMessage.getUserName());
	        		outMessage.setGamePlayer(onRequest);
	        		outMessage.setID("USER_PROFILE");
	        		outMessage.setTo(gameMessage.getUserName());
				} else if (msg_id.equals("LEADER_BOARD")) {
					// get the list of players
					// sort according to the number of games they win
					// send the result to that particular client
					System.out.println("Receive Message - Leader board: " + gameMessage.getUserName());
					list();
					outMessage.setPlayerList(leader_players);
					outMessage.setID("LEADER_BOARD");
					outMessage.setTo(gameMessage.getUserName());
				} else if (msg_id.equals("NEW_GAME")) {
					System.out.println("Receive Message - New game: " + gameMessage.getUserName());
					if (current_players.size() == 4) {
						// the game is full, try after a while
						outMessage.setID("FULL");
						outMessage.setTo(gameMessage.getUserName());
					} else if (current_players.size() == 3) {
						current_players.add(gameMessage.getGamePlayer());
						// start the game
						status = 2;
						outMessage.setID("START");
						outMessage.setPlayerList(current_players);
						
						ArrayList<String> suits = new ArrayList<String>();
						suits.add("c");
						suits.add("d");
						suits.add("h");
						suits.add("s");
						ArrayList<Integer> cardIDs = new ArrayList<Integer>();
						for (int i = 0; i < 13; i++) {
							cardIDs.add(i+1);
						}
						Collections.shuffle(suits);
						Collections.shuffle(cardIDs);
						
						ArrayList<String> cards = new ArrayList<String>();
						gameNumbers = new ArrayList<String>();
						for (int i = 0; i < 4; i++) {
							cards.add(""+cardIDs.get(i)+suits.get(i));
							gameNumbers.add(cardIDs.get(i)+"");
						}
						outMessage.setGameCards(cards);
						
						for (int i = 0; i < 4; i++) {
							outMessage.setTo(current_players.get(i).getUserName());
							sendMessage(outMessage.getTo(), outMessage);
						}
						outMessage.setID(null);
					} else if (current_players.size() == 1 || current_players.size() == 2) {
						// check whether 10 seconds has passed after the first player joined the game
						long currentTime = System.currentTimeMillis();
						if (currentTime > startTime + 10*1000) {
							if (tenSecondsHasPassed == false)
								tenSecondsHasPassed = true;
						}
						if (!tenSecondsHasPassed) {
							// tell other users that there's one more player in
							// add a player and wait for other players
							current_players.add(gameMessage.getGamePlayer());
							outMessage.setID("WAIT");
							status = 1;
							outMessage.setPlayerList(current_players);
							for (int i = 0; i < current_players.size(); i++) {
								outMessage.setTo(current_players.get(i).getUserName());
								sendMessage(outMessage.getTo(), outMessage);
							}
							outMessage.setID(null);
						} else {
							// start the game
							status = 2;
							tenSecondsHasPassed = false;
							outMessage.setID("START");
							current_players.add(gameMessage.getGamePlayer());
							outMessage.setPlayerList(current_players);
							ArrayList<String> suits = new ArrayList<String>();
							suits.add("c");
							suits.add("d");
							suits.add("h");
							suits.add("s");
							ArrayList<Integer> cardIDs = new ArrayList<Integer>();
							for (int i = 0; i < 13; i++) {
								cardIDs.add(i+1);
							}
							Collections.shuffle(suits);
							Collections.shuffle(cardIDs);
							ArrayList<String> cards = new ArrayList<String>();
							gameNumbers = new ArrayList<String>();
							for (int i = 0; i < 4; i++) {
								cards.add(""+cardIDs.get(i)+suits.get(i));
								gameNumbers.add(cardIDs.get(i)+"");
							}
							outMessage.setGameCards(cards);
							for (int i = 0; i < current_players.size(); i++) {
								outMessage.setTo(current_players.get(i).getUserName());
								sendMessage(outMessage.getTo(), outMessage);
							}
							outMessage.setID(null);
						}
					} else {
						// this is the first player, join the game and wait
						startTime = System.currentTimeMillis();
						current_players.add(gameMessage.getGamePlayer());
						outMessage.setID("WAIT");
						status = 1;
						outMessage.setTo(gameMessage.getUserName());
						outMessage.setPlayerList(current_players);
					}
				} else if (msg_id.equals("QUIT_GAME")) {
					System.out.println("Receive Message - Quit Game: " + gameMessage.getUserName());
					if (gameMessage.getGamePlayer() != null) {
						// gamePlayer here is the updated game player (#totalGame ++)
						update(gameMessage.getUserName(), gameMessage.getGamePlayer());
					}
					if (status == 2) { // in gaming status
						if (current_players.size() >= 2) {
							// game can still continue
							// send the message to those who are still playing about who quits the game
							outMessage.setID("QUIT");
							current_players.remove(gameMessage.getUserName());
							outMessage.setPlayerList(current_players);
							for (int i = 0; i < current_players.size(); i++) {
								outMessage.setTo(current_players.get(i).getUserName());
								sendMessage(outMessage.getTo(), outMessage);
							}
							outMessage.setID(null);
						} else { // size = 1, the last player want to quit the game
							// game over because all the players quit the game, no one wins
							status = 0;
							current_players.remove(gameMessage.getUserName());
							noOutMessage = true;
						}
					} else if (status == 1){
						// in waiting stage, still wait for the game to start
						// get rid of that particular player
						current_players.remove(gameMessage.getUserName());
						if (current_players.size() == 1) 
							startTime = System.currentTimeMillis(); // recount the starting time again
						outMessage.setPlayerList(current_players);
						outMessage.setTo(current_players.get(0).getUserName());
						sendMessage(outMessage.getTo(), outMessage);
			        	gameMessage.setID(null);
					}
				} else if (msg_id.equals("SUBMIT")) {
					System.out.println("Receiving Message - Submit Result: " + gameMessage.getUserName());
					double result;
					
					try {
						result = evaluate(gameMessage.getAnswer());
					} catch (Exception e) {
						result = -1;
						outMessage.setID("RESULT");
						outMessage.setTo(gameMessage.getUserName());
						outMessage.setResult(result);
					}
					
					//result = evaluate(gameMessage.getAnswer());
					System.out.println("Result is: " + result);
					if (result == 24) {
						// broadcast the message, game is over
						outMessage.setID("OVER");
						outMessage.setAnswer(gameMessage.getAnswer());
						outMessage.setUserName(gameMessage.getUserName());
						outMessage.setEndTime(System.currentTimeMillis());
						for (int i = 0; i < current_players.size(); i++) {
							outMessage.setTo(current_players.get(i).getUserName());
							sendMessage(outMessage.getTo(), outMessage);
						}
						outMessage.setID(null);
						status = 0;
						tenSecondsHasPassed = false;
						current_players = new ArrayList<GamePlayer>();
						leader_players = new ArrayList<GamePlayer>();
						all_players = new ArrayList<GamePlayer>();
					} else {
						// pass a private message to this player that the answer is not correct
						// game still on
						outMessage.setID("RESULT");
						outMessage.setTo(gameMessage.getUserName());
						outMessage.setResult(result);
					}
				} else if (msg_id.equals("UPDATE")) {
					// update user database records after a game is over
					System.out.println("Receive Messsage - Update Record: " + gameMessage.getUserName());
					update(gameMessage.getUserName(), gameMessage.getGamePlayer());
					noOutMessage = true;
				} else {
					// Unknown Message
					System.out.println("Message received from " + gameMessage.getUserName() + " is unknown: " + gameMessage.getID());
					outMessage.setID("UNKNOWN");
				}
	        	if (outMessage != null) {
	        		if (outMessage.getID() != null && !noOutMessage)
			        	sendMessage(outMessage.getTo(), outMessage);
	        	}
	        }
		}
	}
	
	// Server will read from the queue, send to topic and read again
	public Message receiveMessage(MessageConsumer queueReader) throws JMSException {
		try {
			Message jmsMessage = queueReader.receive();
			return jmsMessage;
		} catch (JMSException e) {
			System.out.println("Failed to receive message: " + e);
			throw e;
		}
	}
	
	public void sendMessage(String to, GameMessage outMessage) throws JMSException {
		MessageProducer topicSender = jmsHelper.createTopicSender();
		Message jmsMessage = jmsHelper.createMessage(outMessage);
		if (to != null && !to.isEmpty()) {
			System.out.print("To " + to + ". ");
			jmsMessage = jmsHelper.createMessage(outMessage);
            jmsMessage.setStringProperty("privateMessageTo", outMessage.getTo());
            jmsMessage.setStringProperty("privateMessageFrom", "Game Server");
		}
		broadcastMessage(topicSender, jmsMessage);
		System.out.println(outMessage.getID());
	}
	
	// Server will relay the message received to the topic
	public void broadcastMessage(MessageProducer topicSender, Message jmsMessage) throws JMSException {
		try {
			System.out.print("Trying to send message: ");
			topicSender.send(jmsMessage);
		} catch (JMSException e) {
			System.err.println("Failed to boardcast message: " + e);
			throw e;
		}
	}
	
	// registration needed
	// insert a new player to the database
	private void insert(String newPlayer, String password) {
		System.out.println("Inserting to the database: " + newPlayer);
		try {
			// use prepared statement to input parameters
			PreparedStatement stmt =
					conn.prepareStatement("INSERT INTO "+DB_TABLE_NAME+" (username, password, win_number, game_number, avg_time) VALUES (?, ?, ?, ?, ?)");
			
			stmt.setString(1, newPlayer);
			stmt.setString(2, password);
			stmt.setInt(3, 0);
			stmt.setInt(4, 0);
			stmt.setFloat(5, 0f);
			stmt.execute();

			System.out.println("New Player created.");

		} catch (SQLException | IllegalArgumentException e) {
			System.err.println("Error inserting record: " + e);
		}
	}
	
	// login needed
	// retrieved the password of a particular user
	private int passwordCorrected (String userName, String password) {
		System.out.println("Checking the password: " + userName);
		try {
			PreparedStatement stmt = conn.prepareStatement("SELECT password FROM "+DB_TABLE_NAME+" WHERE username = ?");
			stmt.setString(1, userName);
			
			// use result set object to retrieve results
			ResultSet rs = stmt.executeQuery();
			if(rs.next()) {
				String correctPassword = rs.getString(1);
				if (!correctPassword.equals(password)) {
					return 3; // 3 for password not correct
				} else 
					return 1; // 1 for password correct
			} else {
				return 2;     // 2 for user_name does not exist
			}
		} catch (SQLException e) {
			System.err.println("Error reading record: "+e);
		}
		return -1;  // connection error with the database
	}
	
	// user profile request needed
	// read the information of a player from the database
	private GamePlayer read(String userName) {
		System.out.println("Retrieving user profile: " + userName);
		GamePlayer requested = null;
		int rank = 1;
		try {
			PreparedStatement stmt = conn.prepareStatement("SELECT * FROM "+DB_TABLE_NAME+" WHERE username = ?");
			stmt.setString(1, userName);
			
			// use result set object to retrieve results
			ResultSet rs = stmt.executeQuery();
			if(rs.next()) {
				int win_number = rs.getInt(3);
				//System.out.println(win_number);
				
				PreparedStatement stmt2 = conn.prepareStatement("SELECT win_number FROM "+DB_TABLE_NAME+"");
				ResultSet rs2 = stmt2.executeQuery();
				while (rs2.next()) {
					//System.out.println(rs2.getInt(1) + " " + win_number);
					if (rs2.getInt(1) > win_number)
						rank++;
				}
				requested = new GamePlayer(userName, rs.getInt(3), rs.getInt(4), rs.getFloat(5), rank);
			} else {
				System.out.println(userName+" not found!");
				return null;
			}
			return requested;
		} catch (SQLException e) {
			System.err.println("Error reading record: "+e);
		}
		return requested;
	}
	
	// list all the players in the database
	// prepared for sorting according to "number_wins"
	private void list() {
		System.out.println("Getting all user list from the database...");
		try {
			// use statement object for queries without parameters
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT * FROM "+DB_TABLE_NAME+"");
			
			// use while loop to read all results
			all_players.clear();
			while(rs.next()) {
				GamePlayer tmp = new GamePlayer(rs.getString(1), rs.getInt(3), rs.getInt(4), rs.getFloat(5), -1);
				all_players.add(tmp);
			}
		} catch (SQLException e) {
			System.err.println("Error listing records: "+e);
		}
		
		Collections.sort(all_players, new Comparator<GamePlayer>() {
			@Override
			public int compare(GamePlayer a, GamePlayer b) {
				return a.getNumOfWins() > b.getNumOfWins() ? -1 : (a.getNumOfWins() < b.getNumOfWins()) ? 1 : 0;
			}
		});
		
		leader_players.clear();
		int i = 0;
		while (leader_players.size() < 10 && leader_players.size() < all_players.size()) {
			all_players.get(i).setRank(i+1);
			leader_players.add(all_players.get(i));
			i++;
		}
	}
	
	// update the records of a player in the database
	private void update(String name, GamePlayer updated_player) {
		System.out.println("Updating record for: " + name);
		try {
			PreparedStatement stmt = conn.prepareStatement("UPDATE "+DB_TABLE_NAME+" SET win_number = ?, game_number = ?, avg_time = ? WHERE username = ?");
			stmt.setInt(1, updated_player.getNumOfWins());
			stmt.setInt(2, updated_player.getNumOfGames());
			stmt.setFloat(3, updated_player.getAverageTime());
			stmt.setString(4, name);
			
			// use executableUpdate() for update
			int rows = stmt.executeUpdate();
			if(rows > 0) {  // return number of rows updated
				System.out.println("Record of "+name+" updated");
			} else {
				System.out.println(name+" not found!");
			}
		} catch (SQLException e) {
			System.err.println("Error reading record: "+e);
		}
	}

	@Override
	// Count the number of words in a string message.
	public int count(String message) throws RemoteException {
		if (message.isEmpty())
			return 0;
		else
			return message.split(" +").length;
	}

	@Override
	/**
	 * @return integer: state
	 * 1 stands for valid (update online user list)
	 * 2 stands for no such user (need to register first)
	 * 3 stands for password wrong (need to enter password again)
	 * 4 stands for already login
	 * 
	 */
	public int Login(String username, String password) throws RemoteException {
		int state = passwordCorrected(username, password);
		if (state == 1) {
			if (onlineUserList.contains(username))
				state = 4;
			else
				onlineUserList.add(username);
		}
		return state;
	}

	@Override
	/**
	 * @return integer: state
	 * 1 stands for valid (register successfully -> update user list -> login)
	 * 5 stands for user name already exist (enter user name again)
	 * 
	 */
	public int Register(String username, String password) throws RemoteException {
		int state = 0;
		if (read(username) != null)
			state = 5;
		else {
			// add the user to the database
			System.out.println("Inserting the new player:");
			insert(username, password);
			// login
			onlineUserList.add(username);
			state = 1; // succeed
		}
		return state;
	}

	@Override
	public void Logout(String username) throws RemoteException {
		// update online user list
		removeUser(username);
	}

	@Override
	// Remove the user from the online user list
	public void removeUser(String username) throws RemoteException {
		// remove from the array list
		onlineUserList.remove(username);
	}
	
	// return true if op1 has higher or the same priority than op2
	public static boolean isPrior(String op1, String op2) {
		if (op1.charAt(0) == "*".charAt(0) || op1.charAt(0) == "/".charAt(0)) {
			return true;
		} else if (op1.charAt(0) == "+".charAt(0) || op1.charAt(0) == "-".charAt(0)) {
			if (op2.equals("*") || op2.equals("*"))
				return false;
			else 
				return true;
		} else if (op1.charAt(0) == "(".charAt(0))
			return false;
		else
			return false;
	}
	
	/**
	 * Evaluate the answer and return the result of the expression if the expression is valid.
	 * Return -1 if the answer consists of invalid signs or numbers (invalid expression).
	 * @param answer
	 * @return
	 */
	public double evaluate(String answer) throws Exception {
		// parse the expression
		ArrayList<String> validSigns = new ArrayList<String>(Arrays.asList("+","-","*","/"));
		ArrayList<String> validValues = new ArrayList<String>(Arrays.asList("A","2","3","4","5","6","7","8","9","10","J","Q","K"));
		ArrayList<String> validNumbers = new ArrayList<String>(Arrays.asList("1","2","3","4","5","6","7","8","9","10","11","12","13"));
		
		// Still need to convert the answer from infix to post-fix, which is easier to evaluate.
		String[] tokens = new String[answer.length()];  // infix
		int index = 0;
		int length = 0;
		for (int i = 0; i < answer.length(); i++) {
			if (answer.charAt(i) == '1') {         // Check whether it is "10"
				if (i == answer.length()-1)
					return -1;
				else if (answer.charAt(i+1) == '0') {
					tokens[index] = "" + answer.charAt(i) + answer.charAt(i+1);
					index++;
					length++;
					i++;
				} else 
					return -1;
			}
			if (validSigns.contains(answer.charAt(i)+"") || validValues.contains(answer.charAt(i)+"")) {
				tokens[index] = "" + answer.charAt(i);
				index++;
				length++;
			} else if (answer.charAt(i) == '(' || answer.charAt(i) == ')') {
				tokens[index] = "" + answer.charAt(i);
				index++;
			}
		}
		
		String[] outputs = new String[length]; // post-fix
		Stack<String> opstack = new Stack<String>();
		length = index;
		index = 0;
		for (int i = 0; i< length; i++) {
			if (validValues.contains(tokens[i])) {
				if (answer.charAt(i) == 'A')
					outputs[index] = "1";
				else if (answer.charAt(i) == 'J')
					outputs[index] = "11";
				else if (answer.charAt(i) == 'Q')
					outputs[index] = "12";
				else if (answer.charAt(i) == 'K')
					outputs[index] = "13";
				else
					outputs[index] = tokens[i];
				index++;
			} else if (tokens[i].charAt(0) == "(".charAt(0)) {
				opstack.push(tokens[i]);
			} else if (tokens[i].charAt(0) == ")".charAt(0)) {
				String top = opstack.pop();
				while (top.charAt(0) != "(".charAt(0)) {
					outputs[index] = top;
					index++;
					top = opstack.pop();
				}
			} else if (validSigns.contains(tokens[i])) {
				Stack<String> tmp = new Stack<String>();
				while (!opstack.isEmpty()) {
					if (opstack.peek().equals("(")) {
						tmp.push(opstack.pop());
						System.out.println(tmp.peek() + " is popped from the op_stack and pushed to the tmp_stack!");
						break;
					}
					if (isPrior(opstack.peek(), tokens[i])) {
						outputs[index] = opstack.pop();
						index++;
					} else {
						tmp.push(opstack.pop());
					}
				}
				while (!tmp.isEmpty()) {
					opstack.push(tmp.pop());
				}
				opstack.push(tokens[i]);
			} else {
				return -1; // invalid sign used
			}
		}
		while(!opstack.isEmpty()) {
			outputs[index] = opstack.pop();
			index++;
		}
		
		// check the validity of numbers
		for (String token: outputs) {
			if (validNumbers.contains(token) && !gameNumbers.contains(token)) 
				return -1; // invalid number used
		}
		
		// evaluation using post-fix order
		Stack<Double> stack = new Stack<Double>();
		for(String token: outputs) {
			if(token.equals("+")) {
				stack.push(stack.pop()+stack.pop());	
			} else if(token.equals("-")) {
				Double p1 = stack.pop();
				Double p2 = stack.pop();
		        stack.push(p2-p1);
			} else if(token.equals("*")) {
		        stack.push(stack.pop()*stack.pop());
			} else if(token.equals("/")) {
				Double p1 = stack.pop();
				Double p2 = stack.pop();
		        stack.push(p2/p1);
			} else {
		        stack.push((double) Integer.parseInt(""+token));
			}
		}
		return stack.pop();
	}
	
	public static void main(String[] args) {
		try {
			GameServer app = new GameServer();
			
			// Register the service
			System.setSecurityManager(new SecurityManager());
			Naming.rebind("GameServer", app);
			
			// Start running
			app.go();
		} catch (NamingException | JMSException e) {
			System.err.println("Program aborted."+e);
		} catch (InstantiationException | IllegalAccessException
				| ClassNotFoundException | SQLException e) {
			System.err.println("Connection failed: "+e);
		} catch(Exception e) {
			System.err.println("Exception thrown: "+e);
		}
	}
}
