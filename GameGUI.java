import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.jms.MessageListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.naming.NamingException;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import java.rmi.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * User-side application (Clients) of Poker 24-game.
 * 
 * @author HAORAN
 * @version 2.0
 * @since 2017.4.2
 *
 */
public class GameGUI implements Runnable, MessageListener {
	
	public static void main(String[] args) {
		try {
			GameGUI game = new GameGUI(args[0]);
			SwingUtilities.invokeLater(game);
		} catch (NamingException | JMSException e) {
			System.err.println("Program aborted.");
		}
	}
	
	public GameGUI(String host) throws NamingException, JMSException {
		// set up JMS
		jmsHelper = new JMSHelper(host);
		try {
			Registry registry = LocateRegistry.getRegistry(host);
			// find a service from the RMI registry
			gameServer = (Game) registry.lookup("GameServer");
		} catch (Exception e) {
			System.err.println("Failed accessing RMI: " + e);
		}
	}
	
	public GameGUI() throws NamingException, JMSException {
		jmsHelper = new JMSHelper();
		try {
			gameServer = (Game) Naming.lookup("GameServer");
		} catch (Exception e) {
			System.err.println("Failed accessing RMI: " + e);
		}
	}
	
	public GameGUI getGameGUI() {
		return this;
	}
	
	private JMSHelper jmsHelper;
	private MessageProducer queueSender;
	private MessageConsumer topicReceiver;
	private Game gameServer;        // game server
	private JFrame frame;           // login page
	private JFrame registerFrame;   // register page
	private JFrame gameFrame;       // game page
	private MyPanel panel;          // panel for game window (change in action_listener)
	private MyTable table;          // the table of leader board
	private int wordCount = 0;      // for name
	private int wordCount2 = 0;     // for password
	private int wordCount3 = 0;     // for register name
	private int wordCount4 = 0;     // for register password
	private int wordCount5 = 0;     // for confirm password
	private JLabel wordCountLabel;  // show # of words in name text field
	private JLabel wordCountLabel2; // show # of words in password text field
	private JTextField name;        // name text field in login page
	private JPasswordField password;// password text field in login page
	private JTextField rname;         // name text field in register page
	private JPasswordField rpassword; // password text field in register page
	private JPasswordField cpassword; // confirm password in register page
	private JButton newGame;          // "New Game" button in game page
	private JTextPane gameRule;       // game rule in game page pre-start stage
	private JScrollPane game_Rule;    // the scroll pane that consist of game rule
	private JTextField answer;        // the answer text field area
	private JLabel result;            // the result label will show the value of the expression evaluated by the server
	private JPanel textField;         // the text field in the game page
	private int index = -1;                // current page id.  0 for user profile, 1 for game page
	private int preindex = -1;             // previous page id. 2 for leader board
	private String _username = "Default";  // store the user_name of the local player
	private String _password = "********"; // store the password of the local player
	private GamePlayer localPlayer;
	private ArrayList<GamePlayer> leaderList; // leader list for leader board page, max_num is 10
	private boolean leaderListUpdated = false;// a flag for marking whether the leader list has been updated
	private ArrayList<String> gameCards;      // game cards
	private ArrayList<GamePlayer> players;    // game players, max_num is 4
	private Image[] cards;                    // card images
	private int fromSource;                   // 0 represents from login page, 1 represents from the register page
	private double expressionValue = -1;      // the value of the expression you submitted
	private boolean inGame = false;           // true represents that it is in game stage or waiting for other players
	private long startTime;                   // the start time of the game
	
	// update the # of words in different text fields
	private class WordCountUpdater extends SwingWorker<Void, Void> {
		private String source;
		
		protected void setSource(String name) {
			source = name;
		}
		
		protected Void doInBackground() {
			String _source = source;
			updateCount(_source);
			return null;
		}
		protected void done() {
			if (source == "name") {
				wordCountLabel.setText("" + wordCount);
				wordCountLabel.invalidate();
			} else if (source == "password") {
				wordCountLabel2.setText("" + wordCount2);
				wordCountLabel2.invalidate();
			}
		}
	}
	
	// document listener installed for text field to detect word number changes
	private class MyDocumentListener implements DocumentListener {
		private String name;
		public MyDocumentListener(String name) {
			this.name = name;
		}
		@Override
		public void changedUpdate(DocumentEvent e) {
			WordCountUpdater updater = new WordCountUpdater();
			updater.setSource(name);
			updater.execute();
		}
		@Override
		public void insertUpdate(DocumentEvent e) {
			WordCountUpdater updater = new WordCountUpdater();
			updater.setSource(name);
			updater.execute();
		}
		@Override
		public void removeUpdate(DocumentEvent e) {
			WordCountUpdater updater = new WordCountUpdater();
			updater.setSource(name);
			updater.execute();
		}
	}
	
	/** 
	 * User-defined Action listener
	 * Different actions are performed according to different sources. 
	 * - Change between pages in the windows.
	 * - Interact with the server. (Send messages)
	 * @author HAORAN
	 *
	 */
	private class MyActionListener implements ActionListener {
		private String source;    // mark where is the event from
		public MyActionListener(String name) {
			source = name;
		}
		
		@Override
		public void actionPerformed(ActionEvent arg0) {
			if (source.equals("login")) {
				if (wordCount == 0)
					JOptionPane.showMessageDialog(null, "Login name cannot be empty!", "ERROR", JOptionPane.ERROR_MESSAGE);
				else if (wordCount2 == 0)
					JOptionPane.showMessageDialog(null, "Password cannot be empty!", "ERROR", JOptionPane.ERROR_MESSAGE);
				else {
					if (gameServer != null) {
						try {
							int state = gameServer.Login(name.getText(), new String(password.getPassword()));
							if (state == 1) {
								// login successfully
								if (index == -1) {
									// first time to open the application
									preindex = -1;
									index = 0;
								} else {
									// not the first time to open the application
									preindex = index;
									index = 0;
								}
								
								// login successfully, then set the user-name and password of the local player
								_username = name.getText();
								name.setText("");
								_password = new String(password.getPassword());
								password.setText("");
								
								// send a message to the server to retrieve information of the user
								try {
									queueSender = jmsHelper.createQueueSender();
									topicReceiver = jmsHelper.createTopicReader(_username);
									topicReceiver.setMessageListener(getGameGUI());
								} catch (JMSException e) {
									System.err.println("Program aborted due to JMS connection failure.");
								}
								
								// send the message to the server
								GameMessage gameMessage = new GameMessage("USER_PROFILE", null, _username, _password);
								sendMessage(gameMessage);
								fromSource = 0; // 0 represents that this login is from login page
								// then receive the response message from the server by the listener
							} else if (state == 2)
								JOptionPane.showMessageDialog(null, "No such user! Please Register first!", "ERROR", JOptionPane.ERROR_MESSAGE);
							else if (state == 3)
								JOptionPane.showMessageDialog(null, "Wrong Password!", "ERROR", JOptionPane.ERROR_MESSAGE);
							else if (state == 4)
								JOptionPane.showMessageDialog(null, "User Already Login!", "ERROR", JOptionPane.ERROR_MESSAGE);
						} catch (RemoteException e) {
							System.err.println("Failed invoking RMI: ");
						}
					}
				}
			} else if (source.equals("register")) {   // the "register" button in login page
				// jump to the register window
				frame.setVisible(false);
				gameFrame.setVisible(false);
				registerFrame.setVisible(true);
			} else if (source.equals("rregister")) {  // the "register" button in register page
				// judge whether the register is successful
				if (wordCount3 == 0)
					JOptionPane.showMessageDialog(null, "User name cannot be empty!", "ERROR", JOptionPane.ERROR_MESSAGE);
				else if (wordCount4 == 0)
					JOptionPane.showMessageDialog(null, "Password cannot be empty!", "ERROR", JOptionPane.ERROR_MESSAGE);
				else if (wordCount5 == 0)
					JOptionPane.showMessageDialog(null, "Confirm-password cannot be empty", "ERROR", JOptionPane.ERROR_MESSAGE);
				else if (!Arrays.equals(rpassword.getPassword(), cpassword.getPassword()))
					JOptionPane.showMessageDialog(null, "Password not consistent!", "ERROR", JOptionPane.ERROR_MESSAGE);
				else if (gameServer != null) {
					try {
						int state = gameServer.Register(rname.getText(), new String(rpassword.getPassword()));
						if (state == 1) {
							// login successfully
							_username = rname.getText();
							rname.setText("");
							_password = new String(rpassword.getPassword());
							rpassword.setText("");
							
							try {
								queueSender = jmsHelper.createQueueSender();
								topicReceiver = jmsHelper.createTopicReader(_username);
								topicReceiver.setMessageListener(getGameGUI());
							} catch (JMSException e) {
								System.err.println("Program aborted due to JMS connection failure.");
							}
							
							// send the message to the server
							GameMessage gameMessage = new GameMessage("USER_PROFILE_REG", null, _username, _password);
							sendMessage(gameMessage);
							fromSource = 1; // 1 represents that the login is from register window
							// receive the responsive message from the server by the listener
							
							index = 0;
							preindex = -1;
						} else if (state == 5)
							JOptionPane.showMessageDialog(null, "Username already exists, choose another username!", "ERROR", JOptionPane.ERROR_MESSAGE);
					} catch (RemoteException e) {
						System.err.println("Failed invoking RMI: ");
					}
				}
				
			} else if (source.equals("cancel")) {
				// cancel register
				registerFrame.setVisible(false);
				gameFrame.setVisible(false);
				frame.setVisible(true);
			} else if (source.equals("userProfile")) {
				// show the user profile, i.e. draw UserProfilePanel
				preindex = index;
				index = 0;
				GameMessage gameMessage = new GameMessage("USER_PROFILE", null, _username, null);
				sendMessage(gameMessage);
				fromSource = 2;  // fromSource equals 2 means that the request is from the user profile page
				// wait for response from the server through the Message Listener.
			} else if (source.equals("game")) {
				// jump to the game page
				if (table != null) {
					gameFrame.remove(table);
					gameFrame.add(panel);
				}
				preindex = index;
				if (inGame) 
					index = 1; // is in game stage (wait or gaming)
				else 
					index = 3; // ask whether start a new game
				if (index == 3) {
					gameFrame.remove(panel);
					gameFrame.add(game_Rule, BorderLayout.CENTER);
					gameFrame.add(newGame, BorderLayout.SOUTH);
				}
				panel.repaint();
				gameFrame.repaint();
				gameFrame.pack();
				gameFrame.setVisible(true);
			} else if (source.equals("leaderBoard")) {
				// show the leader board
				preindex = index;
				index = 2;
				gameFrame.remove(panel);
				gameFrame.remove(newGame);
				gameFrame.remove(game_Rule);
				if (table == null && preindex != 2) {
					// send the message to the server to ask for leader board
					GameMessage gameMessage = new GameMessage("LEADER_BOARD", null, _username, null);
					sendMessage(gameMessage);
					// receive the message from the server by the listener
				} else if (preindex == 2) {  // this means to refresh the leader board
					gameFrame.remove(table);
					// send the message to the server to ask for leader board
					GameMessage gameMessage = new GameMessage("LEADER_BOARD", null, _username, null);
					sendMessage(gameMessage);
					// receive the message from the server by the listener
				}
			} else if (source.equals("logout")) {
				if (JOptionPane.showConfirmDialog(frame, "Are you sure to log out?", "LOGOUT", 
			            JOptionPane.YES_NO_OPTION,
			            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
			        	if (inGame) {
			        		// send the quit message to the server
				        	GameMessage gameMessage = new GameMessage("QUIT_GAME", null, _username, null);
				        	if (gameCards != null) {
				        		localPlayer.setNumberOfGames(localPlayer.getNumOfGames()+1);
					        	gameMessage.setGamePlayer(localPlayer);
					        	gameMessage.setInGame(true);
				        	}
				        	sendMessage(gameMessage);
				        }
			        	inGame = false;
			        	gameFrame.remove(newGame);
			        	gameFrame.remove(game_Rule);
			        	gameFrame.remove(panel);
			        	if (table != null)
			        		gameFrame.remove(table);
			        	if (textField != null)
			        		gameFrame.remove(textField);
						gameCards = null;
						cards = null;
						players = null;
						index = -1;
						preindex = -1;
			        	// send logout message to the server
			        	if (gameServer != null) {
							try {
								gameServer.Logout(_username);
								gameFrame.setVisible(false);
								registerFrame.setVisible(false);
								frame.setVisible(true);
							} catch (RemoteException e) {
								System.err.println("Failed invoking RMI: ");
							}
						}
			    }
			} else if (source.equals("New Game")) {
				// send a message to the server to join the game
				GameMessage gameMessage = new GameMessage("NEW_GAME", null, _username, null);
				gameMessage.setGamePlayer(localPlayer);
				sendMessage(gameMessage);
			}
		}
	}
	
	// update the # of words in different text fields
	public void updateCount(String source) {
		// use the word counting service
		if (gameServer != null) {
			try {
				if (source == "name")
					wordCount = gameServer.count(name.getText());
				else if (source == "password")
					wordCount2 = gameServer.count(new String(password.getPassword()));
				else if (source == "rname")
					wordCount3 = gameServer.count(rname.getText());
				else if (source == "rpassword")
					wordCount4 = gameServer.count(new String(rpassword.getPassword()));
				else if (source == "cpassword")
					wordCount5 = gameServer.count(new String(cpassword.getPassword()));
			} catch (RemoteException e) {
				System.err.println("Failed invoking RMI: ");
			}
		}
	}
	
	/**
	 * Table used for the leader board.
	 * Maximum # of rows: 10;
	 * @author HAORAN
	 *
	 */
	class MyTable extends JPanel {
		private static final long serialVersionUID = 8396615123500652641L;
		JTable table;
		public MyTable() {}

		public MyTable(ArrayList<GamePlayer> leaderList) {
			super(new GridLayout(1,0));
			// Random data is being used for now.
			String[] columnNames = {"Rank", "Player", "Games Won", "Games Played", "Average Time"};
			Object[][] data = new Object[10][5];
			if (leaderListUpdated) {
				for (int i = 0; i < leaderList.size(); i++) {
					data[i][0] = (i+1)+"";
					data[i][1] = leaderList.get(i).getUserName();
					data[i][2] = leaderList.get(i).getNumOfWins();
					data[i][3] = leaderList.get(i).getNumOfGames();
					data[i][4] = leaderList.get(i).getAverageTime();
				}
			} else {
				for (int i = 0; i < 10; i++) {
					data[i][0] = i;
					for (int j = 1; j < 5; j++) {
						data[i][j] = "default";
					}
				}
			};
			
			table = new JTable(data, columnNames);
	        table.setPreferredScrollableViewportSize(new Dimension(150, 300));
	        table.setFillsViewportHeight(true);
	        table.setRowHeight(27);
	        //Create the scroll pane and add the table to it.
	        JScrollPane scrollPane = new JScrollPane(table);

	        //Add the scroll pane to this panel.
	        add(scrollPane);
		}
	}
	
	/**
	 * User defined JPanel to draw the user profile window
	 * State: 0 - user profile
	 * 		  1 - game
	 *        3 - page consisting of a "New Game" button
	 * @author HAORAN
	 *
	 */
	class MyPanel extends JPanel {
		private static final long serialVersionUID = 4645200936970351794L;

		public MyPanel() {
			this.setPreferredSize(new Dimension(150, 340));
		}
		
		public void paintComponent(Graphics g) {
			if (index == 0) {
				// user profile page
				Graphics2D g2 = (Graphics2D) g;
				int fontSize = 34;
		        Font f = new Font("Arial", Font.BOLD, fontSize);
		        g2.setFont(f);
		        g2.setStroke(new BasicStroke(2));
		        g2.drawString(_username, 30, 50);
		        
		        fontSize = 20;
				f = new Font("Arial", Font.PLAIN, fontSize);
				g2.setFont(f);
				g2.drawString("Number of wins: " + localPlayer.getNumOfWins(), 30, 90);
				g2.drawString("Number of games: " + localPlayer.getNumOfGames(), 30, 118);
				g2.drawString("Average time to win: " + localPlayer.getAverageTime(), 30, 146);
				
				fontSize = 29;
				f = new Font("Arial", Font.BOLD, fontSize);
				g2.setFont(f);
				g2.drawString("Rank: #" + localPlayer.getRank(), 30, 184);
				
				gameFrame.pack();
				gameFrame.setVisible(true);
			} else if (index == 1) {    
				// game page
				Graphics2D g2 = (Graphics2D) g;
				int fontSize = 30;
				Font f = new Font("Arial", Font.BOLD, fontSize);
				g2.setFont(f);
				cards = new Image[4];
				if (gameCards != null) {
					for (int i = 0; i < 4; i++)
						cards[i] = new ImageIcon("images/"+gameCards.get(i)+".gif").getImage();
					g2.drawString("Cards:", 10, 40);
					
					for (int i = 0; i < 4; i++)
						g2.drawImage(cards[i], 20 + 95*i, 90, this);
				} else {
					g2.drawString("Waiting for game to start...", 13, 43);
				}
				
				fontSize = 20;
				f = new Font("Arial", Font.PLAIN, fontSize);
				g2.setFont(f);
				g2.drawString("Your Answer:", 10, 250);
				
				fontSize = 15;
				for (int i = 0; i < players.size(); i++) {
					g2.drawRect(430, 18+82*i, 165, 75);
					f = new Font("Arial", Font.BOLD, fontSize);
					g2.setFont(f);
					g2.drawString(players.get(i).getUserName(), 440, 35+82*i);
					f = new Font("Arial", Font.PLAIN, fontSize);
					g2.setFont(f);
					g2.drawString(players.get(i).getNumOfWins() + " wins, " + players.get(i).getNumOfGames() + " total", 440, 60+82*i);
					g2.drawString("Avg Time: " + players.get(i).getAverageTime() + " s", 440, 85+82*i);
				}
				
				gameFrame.pack();
				gameFrame.setVisible(true);
			} else if (index == 3) {
				// there's only one button "New Game" when index is 3.
			}
		}
	}
	
	/**
	 * Build the login frame, register frame, and game frame.
	 */
	public void run() {
		frame = new JFrame("Login");
		frame.addWindowListener(new java.awt.event.WindowAdapter(){
			@Override
		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		        if (JOptionPane.showConfirmDialog(frame, "Are you sure to close?", "Close the window", 
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
		        	System.exit(0);
		        }
		    }
		});
		//frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		registerFrame = new JFrame("Register");
		registerFrame.addWindowListener(new java.awt.event.WindowAdapter(){
			@Override
		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		        if (JOptionPane.showConfirmDialog(frame, "Are you sure to close?", "Close the window", 
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
		        	System.exit(0);
		        }
		    }
		});
		//registerFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		gameFrame = new JFrame("JPoker 24-Game");
		//gameFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		// GUI Design for Login window
		JPanel title = new JPanel();
		title.setPreferredSize(new Dimension(260,150));
		title.setBorder(BorderFactory.createTitledBorder("LOGIN"));
		
		JPanel login = new JPanel();
		login.setLayout(new BoxLayout(login, BoxLayout.Y_AXIS));
		JPanel field1 = new JPanel();
		field1.setLayout(new BorderLayout());
		JLabel title1 = new JLabel("Login Name");
		name = new JTextField(); // login name
		name.getDocument().addDocumentListener(new MyDocumentListener("name"));
		name.setPreferredSize(new Dimension(250, 25));
		field1.add(title1, BorderLayout.CENTER);
		field1.add(name, BorderLayout.PAGE_END);
		login.add(field1);
		
		JPanel field2 = new JPanel();
		field2.setLayout(new BorderLayout());
		JLabel title2 = new JLabel("Password");
		password = new JPasswordField(); // login password
		password.getDocument().addDocumentListener(new MyDocumentListener("password"));
		password.setPreferredSize(new Dimension(250, 25));
		field2.add(title2, BorderLayout.CENTER);
		field2.add(password, BorderLayout.PAGE_END);
		login.add(field2);
		
		JPanel buttons = new JPanel();
		JButton loginButton = new JButton("Login");
		loginButton.addActionListener(new MyActionListener("login"));
		JButton registerButton = new JButton("Register");
		registerButton.addActionListener(new MyActionListener("register"));
		buttons.add(loginButton);
		buttons.add(registerButton);
		login.add(buttons);
		title.add(login, new Integer(0));
		frame.add(title, BorderLayout.CENTER);
		
		JPanel wordCountPane = new JPanel();
		wordCountPane.add(new JLabel("Word count, name:"));
		
		wordCount = 0;
		wordCount2 = 0;
		wordCountLabel = new JLabel("" + wordCount);
		wordCountPane.add(wordCountLabel);
		wordCountPane.add(new JLabel(" password:"));
		wordCountLabel2 = new JLabel("" + wordCount2);
		wordCountPane.add(wordCountLabel2);
		frame.add(wordCountPane, BorderLayout.PAGE_END);
		
		frame.pack();
		frame.setVisible(true);
		
		// GUI Design for Register window
		JPanel register = new JPanel();
		register.setBorder(BorderFactory.createTitledBorder("Register"));
		register.setPreferredSize(new Dimension(260, 170));
		
		JPanel rfield1 = new JPanel();
		rfield1.setLayout(new BorderLayout());
		JLabel rtitle1 = new JLabel("Login Name");
		rname = new JTextField(); // login name
		rname.getDocument().addDocumentListener(new MyDocumentListener("rname"));
		rname.setPreferredSize(new Dimension(250, 25));
		rfield1.add(rtitle1, BorderLayout.CENTER);
		rfield1.add(rname, BorderLayout.PAGE_END);
		register.add(rfield1);
		
		JPanel rfield2 = new JPanel();
		rfield2.setLayout(new BorderLayout());
		JLabel rtitle2 = new JLabel("Password");
		rpassword = new JPasswordField(); // password
		rpassword.getDocument().addDocumentListener(new MyDocumentListener("rpassword"));
		rpassword.setPreferredSize(new Dimension(250, 25));
		rfield2.add(rtitle2, BorderLayout.CENTER);
		rfield2.add(rpassword, BorderLayout.PAGE_END);
		register.add(rfield2);
		
		JPanel rfield3 = new JPanel();
		rfield3.setLayout(new BorderLayout());
		JLabel rtitle3 = new JLabel("Confirm Password");
		cpassword = new JPasswordField(); // confirm password
		cpassword.getDocument().addDocumentListener(new MyDocumentListener("cpassword"));
		cpassword.setPreferredSize(new Dimension(250, 25));
		rfield3.add(rtitle3, BorderLayout.CENTER);
		rfield3.add(cpassword, BorderLayout.PAGE_END);
		register.add(rfield3);
		
		JPanel buttonPanel = new JPanel();
		JButton registerB = new JButton("Register");
		registerB.addActionListener(new MyActionListener("rregister"));
		JButton cancelB = new JButton("Cancel");
		cancelB.addActionListener(new MyActionListener("cancel"));
		buttonPanel.add(registerB);
		buttonPanel.add(cancelB);
		
		registerFrame.add(register, BorderLayout.CENTER);
		registerFrame.add(buttonPanel, BorderLayout.SOUTH);
		registerFrame.pack();
		//registerFrame.setVisible(true);
		
		// GUI Design for the Game Window
		gameFrame.addWindowListener(new java.awt.event.WindowAdapter(){
			@Override
		    public void windowClosing(java.awt.event.WindowEvent windowEvent) {
		        if (JOptionPane.showConfirmDialog(frame, "Are you sure to leave the game?", "Close the window", 
		            JOptionPane.YES_NO_OPTION,
		            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
		        	if (inGame) {
		        		// send the quit message to the server
			        	GameMessage gameMessage = new GameMessage("QUIT_GAME", null, _username, null);
			        	if (gameCards != null) {
			        		localPlayer.setNumberOfGames(localPlayer.getNumOfGames()+1);
				        	gameMessage.setGamePlayer(localPlayer);
				        	gameMessage.setInGame(true);
			        	}
			        	sendMessage(gameMessage);
			        } 
		        	// logout first
		        	if (gameServer != null) {
						try {
							gameServer.Logout(_username);
							gameFrame.setVisible(false);
							registerFrame.setVisible(false);
							frame.setVisible(true);
						} catch (RemoteException e) {
							System.err.println("Failed invoking RMI: ");
						}
					}
		        }
		    }
		});
		
		JPanel menu =new JPanel(new FlowLayout());
		JButton profile = new JButton("User Profile");
		profile.setPreferredSize(new Dimension(149, 30));
		profile.addActionListener(new MyActionListener("userProfile"));
		menu.add(profile);
		JButton game = new JButton("Play Game");
		game.setPreferredSize(new Dimension(149, 30));
		game.addActionListener(new MyActionListener("game"));
		menu.add(game);
		JButton board = new JButton("Leader Board");
		board.setPreferredSize(new Dimension(149, 30));
		board.addActionListener(new MyActionListener("leaderBoard"));
		menu.add(board);
		JButton logout = new JButton("Logout");
		logout.setPreferredSize(new Dimension(149, 30));
		logout.addActionListener(new MyActionListener("logout"));
		menu.add(logout);
		gameFrame.add(menu, BorderLayout.NORTH);
		
		panel = new MyPanel();
		
		// for the game stage
		Font f = new Font("Arial", Font.PLAIN, 20);
		answer = new JTextField();
		answer.setFont(f);
		answer.setBackground(Color.white);
		answer.setPreferredSize(new Dimension(200,30));
		KeyListener myKeyListener = new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {}

			@Override
			public void keyPressed(KeyEvent e) {
				// if the answer is not empty, send it to the server to retrieve the result
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					String myAnswer = answer.getText();
					GameMessage gameMessage = new GameMessage();
					gameMessage.setID("SUBMIT");
					gameMessage.setUserName(_username);
					gameMessage.setAnswer(myAnswer);
					sendMessage(gameMessage);
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {}
		};
		answer.addKeyListener(myKeyListener);
		
		f = new Font("Arial", Font.PLAIN, 16);
		result = new JLabel(" Press \"Enter\" to submit.");
		result.setFont(f);
		result.setPreferredSize(new Dimension(80,30));
		textField = new JPanel(new FlowLayout(FlowLayout.LEFT));
		textField.add(answer);
		textField.add(result);
		//gameFrame.add(textField, BorderLayout.SOUTH);
		
		// for the pre-game stage, there's only a button "New Game"
		String rule = "JPoker 24-Game Rule\n: There will be four players in this game,"
				+ "but if ten seconds has been passed and there're more than two players, the game can also be started. "
				+ "After the game is started, there'll be four cards showing in the game panel. "
				+ "You can only use each of them once. In particular, A, J, Q, K represent 1, 11, 12, 13 respectively. "
				+ "In the text field, you can only use A, J, Q, K. Press enter to submit your answer. "
				+ "The game will be over if one of the four players submit the right answer. "
				+ "Please press \"Enter\" to submit your answer.";
		StyleContext context = new StyleContext();
	    StyledDocument document = new DefaultStyledDocument(context);

	    Style style = context.getStyle(StyleContext.DEFAULT_STYLE);
	    StyleConstants.setAlignment(style, StyleConstants.ALIGN_CENTER);
	    StyleConstants.setFontSize(style, 14);
	    StyleConstants.setSpaceAbove(style, 4);
	    StyleConstants.setSpaceBelow(style, 4);

	    // Insert content
	    try {
	      document.insertString(document.getLength(), rule, style);
	    } catch (BadLocationException badLocationException) {
	      System.err.println("Error showing the game rule.");
	    }

		gameRule = new JTextPane(document);
		gameRule.setPreferredSize(new Dimension(150,300));
		gameRule.setEditable(false);
	    game_Rule = new JScrollPane(gameRule);
		newGame = new JButton("New Game");
		newGame.addActionListener(new MyActionListener("New Game"));
		//panel.add(newGame);
		
		gameFrame.add(panel, BorderLayout.CENTER);
		gameFrame.pack();
		//gameFrame.setVisible(true);
	}
	
	/**
	 * Send the game message to the server.
	 * @param gameMessage
	 */
	public void sendMessage(GameMessage gameMessage) {
		System.out.println("Trying to send message: " + gameMessage);
		Message message = null;
		try {
			message = jmsHelper.createMessage(gameMessage);
		} catch (JMSException e) {
		}
		if(message != null) {
			try {
				queueSender.send(message);
			} catch (JMSException e) {
				System.err.println("Failed to send message");
			}
		}
	}
	
	public Message receiveMessage(MessageConsumer queueReader) throws JMSException {
		try {
			Message jmsMessage = queueReader.receive();
			return jmsMessage;
		} catch (JMSException e) {
			System.out.println("Failed to receive message: " + e);
			throw e;
		}
	}

	@Override
	/**
	 * Asynchronously receiving messages from the server.
	 * Different actions are performed according to different message IDs.
	 */
	public void onMessage(Message jmsMessage) {
		try {
			GameMessage gameMessage = (GameMessage) ((ObjectMessage) jmsMessage).getObject();
			if (gameMessage == null || gameMessage.getID() == null) {
				System.out.println("Unknown Message");
			}
			// present the result in the GUI according to different message identification
			else if (gameMessage.getID().equals("USER_PROFILE")) {
				System.out.println("Get user profile!");
				if (gameMessage.getGamePlayer() != null) {
					localPlayer = gameMessage.getGamePlayer();
				} else {
					System.out.println("Error Receiving Message!");
				}
				
				if (fromSource == 1) {
					// from register page
					if (index == -1) {
						// first time to open the application
						preindex = -1;
						index = 0;
						panel = new MyPanel();
					} else {
						preindex = index;
						index = 0;
						if (preindex == 2) {
							gameFrame.remove(table);
						}
					}
					gameFrame.add(panel);
					gameFrame.pack();
					panel.repaint();
					gameFrame.repaint();
					frame.setVisible(false);
					registerFrame.setVisible(false);
					gameFrame.setVisible(true); // jump to game page
				} else if (fromSource == 0) {
					// from the login page
					panel = new MyPanel();
					panel.repaint();
					if (preindex == 2) {
						gameFrame.remove(table);
						gameFrame.add(panel);
					}
					gameFrame.pack();
					gameFrame.repaint();
					frame.setVisible(false);
					registerFrame.setVisible(false);
					gameFrame.setVisible(true); // jump to game page
				} else if (fromSource == 2) {
					// refresh the user profile page
					panel.repaint();
					if (table != null)
						gameFrame.remove(table);
					gameFrame.remove(game_Rule);
					gameFrame.remove(newGame);
					if (table != null) {
						gameFrame.remove(table);
						gameFrame.add(panel);
					}
					gameFrame.pack();
					gameFrame.setVisible(true);
					gameFrame.repaint();
				}
			} else if (gameMessage.getID().equals("LEADER_BOARD")) {
				System.out.println("Get leader board!");
				if (gameMessage.getPlayerList() != null) {
					leaderList = gameMessage.getPlayerList();
					leaderListUpdated = true;
				} else {
					System.out.println("Error Receiving Message!");
				}
				table = new MyTable(leaderList);
				leaderListUpdated = false;
				
				gameFrame.remove(panel);
				gameFrame.add(table);
				gameFrame.pack();
				gameFrame.setVisible(true);
				gameFrame.repaint();
			} else if (gameMessage.getID().equals("FULL")) {
				System.out.println("The game is full!");
				// Prompt out a window reminding that the game is full, try next time
				inGame = false;
				JOptionPane.showMessageDialog(null, "The game is full, please try after a while!", "Game Full", JOptionPane.INFORMATION_MESSAGE);
		    } else if (gameMessage.getID().equals("WAIT")) {
		    	System.out.println("Wait for start!");
		    	gameFrame.remove(newGame);
		    	gameFrame.remove(game_Rule);
		    	gameFrame.add(panel, BorderLayout.CENTER);
				// update the current players
		    	inGame = true;
		    	players = gameMessage.getPlayerList();
		    	preindex = index;
		    	index = 1;
		    	// refresh the page
		    	panel.repaint();
		    	gameFrame.repaint();
			} else if (gameMessage.getID().equals("QUIT")) {
				System.out.println("Receive quit message from other user!");
				// some player quit the game
				inGame = true;
				players = gameMessage.getPlayerList();
				// refresh the page
		    	panel.repaint();
		    	gameFrame.remove(panel);
		    	gameFrame.add(panel, BorderLayout.CENTER);
		    	gameFrame.repaint();
			} else if (gameMessage.getID().equals("START")) {
				System.out.println("Game starts!");
				gameFrame.remove(newGame);
				gameFrame.remove(game_Rule);
		    	gameFrame.add(panel, BorderLayout.CENTER);
				// start the game
				inGame = true;
				expressionValue = -1;
				if (expressionValue == -1)
					result = new JLabel(" Press \"Enter\" to submit.");
				else {
					result = new JLabel(" = " + expressionValue);
					expressionValue = -1;
				}
				gameFrame.add(textField, BorderLayout.SOUTH);
				gameCards = gameMessage.getGameCards();
				players = gameMessage.getPlayerList();
				startTime = System.currentTimeMillis();
				// refresh the page
		    	panel.repaint();
		    	gameFrame.repaint();
		    	panel.repaint();
		    	gameFrame.repaint();
			} else if (gameMessage.getID().equals("RESULT")) {
				System.out.println("Answer is wrong!");
				// the answer submitted is wrong
				inGame = true;
				expressionValue = gameMessage.getResult();
				JOptionPane.showMessageDialog(null, "Answer not correct! " + (expressionValue == -1 ? "Invalid Expression" : "The value of your expression is: "+expressionValue), "Wrong Answer", JOptionPane.INFORMATION_MESSAGE);
				// refresh the page
		    	panel.repaint();
		    	gameFrame.repaint();
			} else if (gameMessage.getID().equals("OVER")) {
				System.out.println("Game over!");
				// the game is over
				inGame = false;
				gameCards = null;
				players = null;
				if (gameMessage.getUserName().equals(_username)) {
					// local player is the winner: avg_time is updated
					float duration = (gameMessage.getEndTime() - startTime)/1000;
					localPlayer.setAverageTime((localPlayer.getAverageTime()*localPlayer.getNumOfWins() + duration)/(localPlayer.getNumOfWins()+1));
					localPlayer.setNumOfWins(localPlayer.getNumOfWins()+1);
					localPlayer.setNumberOfGames(localPlayer.getNumOfGames()+1);
				} else {
					// local player is not the winner, avg_time is not updated
					localPlayer.setNumberOfGames(localPlayer.getNumOfGames()+1);
				}
				// send the message to the server to update the database record
				GameMessage outMessage = new GameMessage("UPDATE", null, _username, null);
				outMessage.setGamePlayer(localPlayer);
				sendMessage(outMessage);
				
				// jump out a window to confirm whether to join the next game or not
				int choice;
				if (gameMessage.getUserName().equals(_username)) {
					System.out.println("Local PLayer is the winner!");
					choice = JOptionPane.showConfirmDialog(null, "Game Over! The winner is "+gameMessage.getUserName()+" whose answer is: "+gameMessage.getAnswer()+" Do you want to start a new game?", "Game Over", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				} else {
					System.out.println("The winner is: " + gameMessage.getUserName());
					choice = JOptionPane.showConfirmDialog(null, "Game Over! The winner is "+gameMessage.getUserName()+" whose answer is: "+gameMessage.getAnswer()+" Do you want to start a new game?", "Game Over", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
				}
				if (choice == JOptionPane.YES_OPTION) {
					// send the message to the server to join a new game
					outMessage = new GameMessage("NEW_GAME", null, _username, null);
					outMessage.setGamePlayer(localPlayer);
					sendMessage(outMessage);
					answer.setText("");
				} else if (choice == JOptionPane.NO_OPTION) {
					// return the the game page consisting of the "New Game" button
					answer.setText("");
					gameFrame.remove(textField);
					preindex = index;
					index = 3;
					cards = null;
					players = null;
					panel.repaint();
					gameFrame.repaint();
				}
			} else {
				// unknown message
				System.out.println("Unknown Message:" + gameMessage.getID());
			}
		} catch (JMSException e) {
			System.err.println("Failed to receive message");
		}
	}
}