package server;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

import shared.*;

public class ChatroomManager {
	
	private ConcurrentHashMap<Integer, Chatroom> chatrooms = new ConcurrentHashMap<Integer, Chatroom>();
	private List<Integer> chatroomIDs = Collections.synchronizedList(new ArrayList<Integer>());
	
	private String chatroomFile = "ChatroomFile";
	
	private static int chatroomCounter = 0;
	
	public ChatroomManager()
	{
		try //this will populate the valid user accounts
		{
			File myFile = new File(chatroomFile);
			Scanner reader = new Scanner(myFile);
		
			while (reader.hasNextLine())
			{
				//getline and set delimiters
				Scanner line = new Scanner(reader.nextLine()).useDelimiter("\\s+"); // \\s+ means whitespace
				
				ArrayList<String> token = new ArrayList<String>();
				line.tokens();
				
				//grab all the tokens
				while(line.hasNext())
				{
					token.add(line.next());
				}
				
				//if there are more or less than 1 token, then it is invalid
				if (token.size() != 1)
				{
					line.close(); //do nothing and skip this iteration
            		continue;
				}
				
				int chatroomID = Integer.valueOf(token.get(0));
				
				if(chatroomCounter < chatroomID) {
					chatroomCounter = chatroomID;
				}
				
				// create and add new chatrooms to chatmanager data
				Chatroom make = new Chatroom(chatroomID); // uses chatroom id for constructor
				chatroomIDs.add(chatroomID);
				chatrooms.put(chatroomID, make);
				
				line.close();
			}
			reader.close();
			
		}
		catch (Exception e) {
        	e.printStackTrace();
        }
	}
	
	public void sendMessageToChatroom(ObjectOutputStream out, Message message, ConcurrentHashMap<Integer, ObjectOutputStream> clients)
	{
		try
		{
			//message variable
			Message Send;
			MessageCreator create;
			create = new MessageCreator(MessageType.UTC);
			create.setContents("Error"); 
			Send = new Message(create);// have message ready to return a deny
			
			
			String input = message.getContents();
			
			Integer id = message.getToChatroomID(); // get chatroom id
			
			if (input == null || id == null) //check if input is good
			{
				//don't send a message
				out.writeObject(Send); //send the deny message
				return;
			}
			
			
			Chatroom receive = chatrooms.get(id);
			if(receive == null)
			{
				out.writeObject(Send); //send the deny message
				return;
			}
			
			
			receive.addMessage(message); //give message to chatroom so they can store it
			
			clients.keySet().parallelStream().forEach(client ->{
				try {
					if(receive.findMember(client)) {
						clients.get(client).writeObject(message);
						clients.get(client).flush();
					}
				}
				catch(IOException e) {
					System.err.println("Error sending update to a client!");
				}
			});

			
			create.setContents("Success");
			Send = new Message(create);// create an accept message
		    out.writeObject(Send); //send message
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public Chatroom getChatroom(Integer chatroomID)
	{
		Chatroom find;
		find = chatrooms.get(chatroomID);
		//if id is not found, 'find' will be labeled as null
		return find;
	}
	
	public void joinChatroom(ObjectOutputStream out, Message message, ConcurrentHashMap<Integer, ObjectOutputStream> clients) {
		try {
			Message Send;
			MessageCreator create;
			create = new MessageCreator(MessageType.JC);
			create.setContents("Error"); 
			Send = new Message(create);// have message ready to return a deny
			
			Integer id = message.getToChatroomID();
			if(id == null) {
				out.writeObject(Send);
				return;
			}
			
			Chatroom join = chatrooms.get(id);
			// If chatroom doesn't exist or if already apart of the chatroom
			if(join == null || join.findMember(message.getFromUserID())) {
				out.writeObject(Send);
				return;
			}
			
			create.setContents("Add");
			create.setFromUserID(message.getFromUserID());
			
			// Let others know client is joining the chatroom
			clients.keySet().parallelStream().forEach(client ->{
				try {
					if(join.findMember(client)) {
						clients.get(client).writeObject(create.createMessage());
						clients.get(client).flush();
					}
				}
				catch(IOException e) {
					System.err.println("Error sending update to a client!");
				}
			});

			join.addMember(message.getFromUserID());
			
			create.setContents("Success");
			create.setChatroom(join);
			create.setToChatroom(join.getChatroomID());
			out.writeObject(create.createMessage());
			
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	//inorder to get userToInvite, have the server get that value with the user manager function
	public void addUsertoChatroom(ObjectOutputStream out, Message message, ConcurrentHashMap<Integer, ObjectOutputStream> clients)
	{
		try
		{
			//message variable
			Message Send;
			MessageCreator create;
			create = new MessageCreator(MessageType.IUC);
			create.setContents("Error"); 
			Send = new Message(create);// have message ready to return a deny
			
			Integer id = message.getToChatroomID(); // get chatroom id
			
			if (id == null) //check if input is good
			{
				//don't send a message
				out.writeObject(Send); //send the deny message
				return;
			}
			
			
			Chatroom receive = chatrooms.get(id); //check if it exists
			if(receive == null)
			{
				out.writeObject(Send); //send the deny message
				return;
			}
			
			create.setContents("Add");
			create.setFromUserID(message.getToUserID());
			
			// Let others know client is joining the chatroom
			clients.keySet().parallelStream().forEach(client ->{
				try {
					if(receive.findMember(client)) {
						clients.get(client).writeObject(create.createMessage());
						clients.get(client).flush();
					}
				}
				catch(IOException e) {
					System.err.println("Error sending update to a client!");
				}
			});
			
			receive.addMember(message.getToUserID()); //give user ID to chatroom so they can store it
			
			create.setChatroom(receive);
			create.setContents("Success");
			Send = new Message(create);// create an accept message
		    out.writeObject(Send); //send message
			
			
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void createChatroom(ObjectOutputStream out, Message message)
	{
		try
		{
			//message variable
			Message Send;
			MessageCreator create;
			create = new MessageCreator(MessageType.CC);
			create.setContents("Error"); 
			Send = new Message(create);// have message ready to return a deny
			
			Integer id = message.getToChatroomID(); // get chatroom id
			
			if (id == null) //check if input is good
			{
				//don't send a message
				out.writeObject(Send); //send the deny message
				return;
			}
			
			//make a new chatroom and add it to list 
			Chatroom make = new Chatroom(++chatroomCounter, message.getFromUserID());
			Integer ChatId = make.getChatroomID();
			chatroomIDs.add(ChatId);
			chatrooms.put(ChatId, make);
			
			create.setContents("Success");
			create.setToChatroom(make.getChatroomID()); //add the chatroom ID in the return message
			create.setChatroom(make); //add the chatroom in return message
			Send = new Message(create);// create an accept message
		    out.writeObject(Send); //send message
			
			
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public void deleteChatroom(ObjectOutputStream out, Message message, ConcurrentHashMap<Integer,ObjectOutputStream> clients)
	{
		try {
			Message Send;
			MessageCreator create;
			create = new MessageCreator(MessageType.UPDATECM);
			create.setContents("Error");
			Send = new Message(create);
			
			Integer id = message.getToChatroomID();
			
			if(id == null) {
				out.writeObject(Send);
				return;
			}
			
			Chatroom receive = chatrooms.get(id);
			if(receive == null) {
				out.writeObject(Send);
				return;
			}
			
			create.setContents("Remove");
			create.setToChatroom(id);
			
			clients.keySet().parallelStream().forEach(client ->{
				try {
					if(receive.findMember(client)) {
						clients.get(client).writeObject(create.createMessage());
						clients.get(client).flush();
					}
				}
				catch(IOException e) {
					System.err.println("Error sending update to a client!");
				}
			});
			
			chatroomIDs.remove(id);
			chatrooms.remove(id);
			
			create.setContents("Success");
			Send = new Message(create);// create an accept message
		    out.writeObject(Send); //send message
			
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public void removeUserfromChatroom(ObjectOutputStream out, Message message, ConcurrentHashMap<Integer,ObjectOutputStream> clients)
	{
		try
		{
			//message variable
			Message Send;
			MessageCreator create;
			create = new MessageCreator(MessageType.LC);
			create.setContents("Error"); 
			Send = new Message(create);// have message ready to return a deny
			
			Integer id = message.getToChatroomID(); // get chatroom id
			
			if (id == null) //check if input is good
			{
				//don't send a message
				out.writeObject(Send); //send the deny message
				return;
			}
			
			
			Chatroom receive = chatrooms.get(id); //check if it exists
			if(receive == null)
			{
				out.writeObject(Send); //send the deny message
				return;
			}
			
			receive.removeMember(message.getToUserID()); //give user ID to chatroom so they can remove it
			
			create.setContents("Remove");
			create.setToUserID(message.getToUserID());
			
			clients.keySet().parallelStream().forEach(client ->{
				try {
					if(receive.findMember(client)) {
						clients.get(client).writeObject(message);
						clients.get(client).flush();
					}
				}
				catch(IOException e) {
					System.err.println("Error sending update to a client!");
				}
			});
			
			create.setContents("Success");
			Send = new Message(create);
		    out.writeObject(Send);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		
	}
	
	public void removeUserFromChatrooms(List<Integer>chatroomIds, Integer userID, ConcurrentHashMap<Integer, ObjectOutputStream> clients) {
		Message Send;
		MessageCreator create;
		create = new MessageCreator(MessageType.LC);
		create.setToUserID(userID);
		Send = new Message(create);// have message ready to return a deny
		
		for(Integer chatroomID : chatroomIDs) {
			// Remove from data, send to all online clients that user is not apart of chatroom anymore
			chatrooms.get(chatroomID).removeMember(userID);
			clients.keySet().parallelStream().forEach(client ->{
				try {
					if(chatrooms.get(chatroomID).findMember(client) && client != userID) {
						clients.get(client).writeObject(Send);
						clients.get(client).flush();
					}
				}
				catch(IOException e) {
					System.err.println("Error sending update to a client!");
				}
			});

		}
	}
}
