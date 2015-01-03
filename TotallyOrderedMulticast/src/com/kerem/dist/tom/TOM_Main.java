package com.kerem.dist.tom;

import com.kerem.dist.tom.communication.CommThread;
import com.kerem.dist.tom.model.ProcessConfigModel;
import com.kerem.dist.tom.server.ServerThread;
import com.kerem.dist.tom.util.ProcessConfigParser;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;

///Users/keremgocen/Blesh/git-repos/token-ring/TotallyOrderedMulticast/src/com/kerem/dist/tom/config/process_1_conf

public class TOM_Main {

    private static String serverName = "localhost";

    public static void main(String[] args) {

        // retrieve process configuration
        ProcessConfigParser configParser = new ProcessConfigParser();

        // debug command line argument
        System.out.println("received args[0]:" + args[0]);

        ProcessConfigModel processConfigModel = configParser.readConfigFile(args[0]);
        if(processConfigModel != null) {
            System.out.println("Processed config file:" + args[0] + " as:" + processConfigModel.toString());

            initialize(processConfigModel);
        } else {
            System.out.println("Failed to start up process...");
        }
    }

    /*
     * Initialization:
     * During this phase, processes establish socket connections with other processes so that every process gets
     * connected with every other process (to achieve multicast communication). Steps of the initialization phase as follows:
     *
     * • Processes start executing in process ID order. That is, process 0 will start first.
     *
     * • When started, process i parses its configuration file (detailed below) and connects with every
     * process j where 0 <= j < i. Process j tells i its process ID.
     *
     * • Process i then opens a server socket and starts listening for connections. This server socket will
     * be used by every process j where i < j < n. Here, n is the total number of processes. Once all such processes
     * connect with i, i closes its server socket.
     */

    private static void initialize(ProcessConfigModel config) {

        int pid;
        List<Integer> portList;
        int port;
        int totalProcessCount;

        try {
            pid = config.getProcessId();
            portList = config.getPortList();
            port = portList.get(pid);
            totalProcessCount = config.getTotalNumberOfProcesses();
        } catch (NullPointerException e){
            e.printStackTrace();
            System.out.println("Failed to get config parameters!");
            return;
        }

        System.out.println("Starting up process with id:" + config.getProcessId() + " on port:" + port);

        // create a map where key is the pid and value is a socket to a process we will communicate through
//        final HashMap<Integer, Socket> multicastMap = new HashMap<>();

        // connect to the rest of the processes with lower pid
        for(int k = 0; k < totalProcessCount; k++) {
            if(k < pid) {
                CommThread commThread = new CommThread(serverName, portList.get(k));
                new Thread(commThread).start();

//                final Socket commSocket = retrieveSocketFromProcess(serverName, portList.get(k));
//
//                if(commSocket != null) {
//                    multicastMap.put(k, commSocket);
//                } else {
//                    System.out.println("Failed to connect to process pid:" + k + " on port:" + portList.get(k));
//                }
            }
        }

        // assuming process id's will be consecutive and start from 0, totalProcessCount should always be (total process count - i + 1)
        // where i is the process id
        totalProcessCount -= (pid + 1);

        if(totalProcessCount > 1) {
            System.out.println("Opening up a server socket, number of remaining connections:" + totalProcessCount);

            // open up a server socket and wait for connection from other processes until all is connected
            ServerThread serverThread = new ServerThread(pid, port, totalProcessCount);
            new Thread(serverThread).start();
        }

    }

//    /**
//     * Returns a communication socket from the target process on the specified port.
//     * A null socket object is returned if failed.
//     *
//     * @param   portNumber      the port target process is running on
//     * @return  Socket          Socket object for communication
//     */
//    private static Socket retrieveSocketFromProcess(String serverName, Integer portNumber) {
//        Socket commSocket = null;
//
//        try {
//			//create socket and connect to the server
//            commSocket = new Socket(serverName, portNumber);
//        } catch (UnknownHostException e) { //if serverName cannot be resolved to an address
//			System.out.println("Who is " + serverName + "?");
//			e.printStackTrace();
//		} catch (IOException e) {
//			System.out.println("Cannot get I/O for the connection.");
//			e.printStackTrace();
//		}
//
//        return commSocket;
//    }
}

/*
• You will code in Java using socket programming. Processes will be executing on the local host.
• Every process has an ID number. IDs start at 0 and are increment by 1 for each new process.
• Total ordering of the processes is based on these ID numbers.
• Every process will start executing with its own configuration file.
• Queue contents will be displayed on the console/screen upon each event.
 */
