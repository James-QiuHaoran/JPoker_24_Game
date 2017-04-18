import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

/**
 * RMI methods definition.
 * All methods on RMI must throw RemoteException.
 * @author HAORAN
 *
 */
public interface Game extends Remote {
	/*
	 * Count the number of words in message.
	 */
	int count (String message) throws RemoteException;
		
	/*
	 * Validate user in the database.
	 * Avoid Repeated login by checking against the online user list.
	 * Update OnlineUser list.
	 */
	int Login(String username, String password) throws RemoteException;
	
	/*
	 * Avoid duplicating user name by checking the database
	 * Login user and update OnlineUser list if register successfully.
	 */
	int Register(String username, String password) throws RemoteException;
	
	/*
	 * Update OnlineUser list.
	 */
	void Logout(String username) throws RemoteException;
	
	ArrayList<String> userList = new ArrayList<String>();
	ArrayList<String> password = new ArrayList<String>();
	ArrayList<String> onlineUserList = new ArrayList<String>();
		
	// remove the user from online user list
	void removeUser(String username) throws RemoteException;
}
