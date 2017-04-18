import java.io.Serializable;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * Used by clients and the server to initialize JMS communications.
 * @author HAORAN
 *
 */
public class JMSHelper {
	private static final String DEFAULT_HOST = "localhost";
	private static final int DEFAULT_PORT = 3700;
	
	private static final String JMS_CONNECTION_FACTORY = "jms/JPoker24GameConnectionFactory";
	private static final String JMS_QUEUE = "jms/JPoker24GameQueue";
	private static final String JMS_TOPIC = "jms/JPoker24GameTopic";
	
	private Context jndiContext;
	private ConnectionFactory connectionFactory;
	private Connection connection;
	
	private Session session;
	private Queue queue;
	private Topic topic;

	public JMSHelper() throws NamingException, JMSException {
		this(DEFAULT_HOST);
	}
	
	public JMSHelper(String host) throws NamingException, JMSException {
		int port = DEFAULT_PORT;
	    
	    System.setProperty("org.omg.CORBA.ORBInitialHost", host);
	    System.setProperty("org.omg.CORBA.ORBInitialPort", ""+port);
	    try {
	        jndiContext = new InitialContext();
	        connectionFactory = (ConnectionFactory)jndiContext.lookup(JMS_CONNECTION_FACTORY);
	        queue = (Queue)jndiContext.lookup(JMS_QUEUE);
	        topic = (Topic)jndiContext.lookup(JMS_TOPIC);
	    } catch (NamingException e) {
	        System.err.println("JNDI failed: " + e);
	        throw e;
	    }
	    
	    try {
	        connection = connectionFactory.createConnection();
	        connection.start();
	    } catch (JMSException e) {
	        System.err.println("Failed to create connection to JMS provider: " + e);
	        throw e;
	    }
	}
	
	/**
	 * Will be called when a session is needed. Keep using one session for all operations.
	 * @return Session object
	 * @throws JMSException
	 */
	public Session createSession() throws JMSException {
		if (session != null) {
			return session;
		} else {
			try {
				return connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			} catch (JMSException e) {
				System.err.println("Failed creating session: " + e);
				throw e;
			}
		}
	}
	
	/**
	 * When sending messages, createMessage() is used to get a JMS message to send.
	 * @param obj
	 * @return ObjectMessage object
	 * @throws JMSException
	 */
	public ObjectMessage createMessage(Serializable obj) throws JMSException {
		try {
			return createSession().createObjectMessage(obj);
		} catch (JMSException e) {
			System.err.println("Error preparing message: " + e);
			throw e;
		}
	}
	
	/**
	 * To send a message to queue, we need a MessageProducer object.
	 * @return MessageProducer object
	 * @throws JMSException
	 */
	public MessageProducer createQueueSender() throws JMSException {
		try {
			return createSession().createProducer(queue);
		} catch (JMSException e) {
			System.err.println("Failed sending to queue: " + e);
			throw e;
		}
	}
	
	/**
	 * To read from a queue, we need a MessageConsumer object to return.
	 * @return MessageConsumer object.
	 * @throws JMSException
	 */
	public MessageConsumer createQueueReader() throws JMSException {
		try {
			return createSession().createConsumer(queue);
		} catch (JMSException e) {
			System.err.println("Failed reading from queue: " + e);
			throw e;
		}
	}
	
	/**
	 * To send message to a topic, a MessageProducer object is needed.
	 * @return MessageProducer object
	 * @throws JMSException
	 */
	public MessageProducer createTopicSender() throws JMSException {
		try {
			return createSession().createProducer(topic);
		} catch (JMSException e) {
			System.err.println("Failed sending to queue: " + e);
			throw e;
		}
	}
	
	/**
	 * To receive message from topic, a MessageConsumer object is needed.
	 * @return MessageConsumer object
	 * @throws JMSException
	 */
	public MessageConsumer createTopicReader(String name) throws JMSException {
		try {
			name = name.replace("'", "''");
	        String selector = "(privateMessageFrom IS NULL AND privateMessageTo IS NULL) OR "+
	                           "privateMessageTo = '"+name+"' OR privateMessageFrom = '"+name+"'";
			return createSession().createConsumer(topic, selector); // no need to be durable in this case
		} catch (JMSException e) {
			System.err.println("Failed reading from queue: " + e);
			throw e;
		}
	}
}
