package com.kerem.dist.tom.communication;

import com.kerem.dist.tom.model.MulticastMessageModel;
import com.kerem.dist.tom.model.ResponseModel;
import com.kerem.dist.tom.util.BlockingConsoleLogger;
import com.kerem.dist.tom.util.LocalLamportClock;
import com.kerem.dist.tom.util.MessageDeserializer;
import com.kerem.dist.tom.util.ResponseDeserializer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

/**
 * Created by keremgocen on 1/3/15.
 */
public class CommThread implements Runnable {
	// server's name or IP number
	private String serverName;
	// the port of the server to connect to
	private int portNumber;
	// process id this thread is running on
	private int processId;
	// get instance of the communication queue
	private static final MulticastOrganizerQueue commQueue = MulticastOrganizerQueue.INSTANCE;
	
	// control receiverId to avoid sending multiple broadcast to the same process and mark ACK on receiver
	private int receiverId = -1;

    public CommThread(String serverName, int portNumber, int processId) {
        this.serverName = serverName;
        this.portNumber = portNumber;
		this.processId = processId;
    }

    @Override
    public void run() {
        Socket clientSocket = null;
		PrintWriter socketOut = null;
		BufferedReader socketIn = null;

		try {
			// create socket and connect to the server
			clientSocket = new Socket(serverName, portNumber);
			// will use socketOut to send text to the server over the socket
			socketOut = new PrintWriter(clientSocket.getOutputStream(), true);
			// will use socketIn to receive text from the server over the socket
			socketIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		} catch(UnknownHostException e) { //if serverName cannot be resolved to an address
			BlockingConsoleLogger.println("Who is " + serverName + "?");
			e.printStackTrace();
			System.exit(0);
		} catch (IOException e) {
			BlockingConsoleLogger.println("Cannot get I/O for the connection.");
			e.printStackTrace();
			System.exit(0);
		}
		
		BlockingConsoleLogger.println("Starting up comm thread on port:" + portNumber);

		/* TODO
		 * Check the queue and broadcast message on top of it, wait for ack
		 * Double check pid so message is not broadcasted to the same process twice ?
		 */

		BlockingConsoleLogger.println(processId + "-before mark");
		MulticastOrganizerQueue.displayQueue();

		int i = 0;

		while(true) {

			MulticastMessageModel message = null;
			try {
				// peek top of queue for existing message
				message = MulticastOrganizerQueue.peekMessage();
				
				message.getAckArray()[processId] = true;
				
				
				if(message == null) {
					continue;
				} else {
					BlockingConsoleLogger.println(processId + "-after mark");
					MulticastOrganizerQueue.displayQueue();
					
					BlockingConsoleLogger.println(processId + "-message found in queue!");
					
					// check ACK array for delivery confirmation
					boolean abortDelivery = false;
					for(Boolean deliver : message.getAckArray()) {
						if(!deliver)
							abortDelivery = true;
					}

					if(!abortDelivery) {
						if(MulticastOrganizerQueue.deliverMessage(message)) {
							BlockingConsoleLogger.println(processId + "-MESSAGE DELIVERED. message:" + message.toString());
							MulticastOrganizerQueue.displayQueue();
						} else {
							BlockingConsoleLogger.println(processId + "-MESSAGE DELIVERY FAILED!");
						}
						continue;
					}

					// mark with own process id and broadcast message
					message.setSenderId(processId);
					socketOut.println(MulticastMessageModel.TOM_MSG_CONTENT + "|" + MessageDeserializer.createStringFromMessage(message));
				}
				
			} catch (Exception e) {
				if(message == null) {
					continue;
				}
				e.printStackTrace();
			}

			BlockingConsoleLogger.println(processId + "-Message sent, waiting for the server's response. message:" + message.toString());
			
			String responseString = null;
			try {
				responseString = socketIn.readLine();
				ResponseModel responseModel = null;
				try {
					responseModel = ResponseDeserializer.responseFromString(responseString);
					BlockingConsoleLogger.println(processId + "-received responseModel:" + responseModel);
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				if(responseModel.getMessageType().equals(MulticastMessageModel.TOM_MSG_ACK)) {
					BlockingConsoleLogger.println(processId + "-server ACK received on client:" + clientSocket.getInetAddress());
					
					receiverId = responseModel.getSenderId();
					
					// record ack received for sender
					message.getAckArray()[receiverId] = true;
					
					MulticastOrganizerQueue.displayQueue();
					
					// respond with ACK
					socketOut.println(MulticastMessageModel.TOM_MSG_ACK + "|" + processId);

				} else if(responseModel.getMessageType().equals(MulticastMessageModel.TOM_KILL_ALL)) {
					break;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		BlockingConsoleLogger.println(processId + "-terminating comm thread on client:" + clientSocket.getInetAddress());

		LocalLamportClock.setStopClock();
		
		//close all streams
		socketOut.close();
		try {
			socketIn.close();
			clientSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
    }
}
