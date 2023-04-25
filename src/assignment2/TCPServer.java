package assignment2;

import java.io.*;
import java.net.*;
import java.util.*;

public class TCPServer {
    private static Map<String,String> chatStatuses; // 채팅방 멤버 사용자 이름, 채팅방이름
    private static Map<String,Socket> clientSockets; // 사용자 이름, 연결 소켓
    TCPServer() {
        //ensure data integrity
        chatStatuses = Collections.synchronizedMap(new HashMap<>());
        clientSockets = Collections.synchronizedMap(new HashMap<>());
    }
    public static void main(String args[]) {
        //argument
        if(args.length!=2) {
            System.out.println("INVALID ARGUMENTS INPUT");
            System.exit(0);
        }
        int portNum1 = Integer.parseInt(args[0]);
        int portNum2 = Integer.parseInt(args[1]);
        try {
            //call start() function
            new TCPServer().start(portNum1,portNum2);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void start(int portNum1,int portNum2) {
        ServerSocket welcomeSocket = null;
        Socket connectionSocket = null;
        try {
            welcomeSocket = new ServerSocket(portNum1);
            System.out.println("Server ready.");
            while(true) {
                //accept connection from client
                connectionSocket = welcomeSocket.accept();
                //client마다 thread 생성
                ServerReceiver thread = new ServerReceiver(connectionSocket, portNum2);
                System.out.println("Accept connection.");
                thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //inner class
    private class ServerReceiver extends Thread {
        private Socket connectionSocket;
        private int portNum2;
        ServerReceiver(Socket connectionSocket, int portNum2) {
            this.connectionSocket = connectionSocket;
            this.portNum2 = portNum2;
        }

        public void run() {
            try {
                while(true) {
                    System.out.println("Waiting for msg.");

                    //receive msg from client
                    DataInputStream inFromClient = new DataInputStream(connectionSocket.getInputStream());
                    String clientMsg = inFromClient.readUTF();

                    System.out.println(clientMsg);

                    String[] msgSplit = clientMsg.split(" ");
                    String respond = null;
                    //command
                    if (msgSplit[0].equals("#CREATE")) {
                        //chatroom name, user name not exist
                        if (!chatStatuses.containsValue(msgSplit[1]) && !chatStatuses.containsKey(msgSplit[2])) {
                            respond = "success";
                            chatStatuses.put(msgSplit[2], msgSplit[1]);
                            clientSockets.put(msgSplit[2], connectionSocket);
                        } else {
                            respond = "failure";
                        }
                        //send respond
                        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                        outToClient.writeUTF(respond);
                        outToClient.flush();
                    } else if (msgSplit[0].equals("#JOIN")) {
                        //chatroom name already exists
                        if (chatStatuses.containsValue(msgSplit[1]) && !chatStatuses.containsKey(msgSplit[2])) {
                            respond = "success";
                            chatStatuses.put(msgSplit[2], msgSplit[1]);
                            clientSockets.put(msgSplit[2], connectionSocket);
                        } else {
                            respond = "failure";
                        }
                        //send respond
                        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                        outToClient.writeUTF(respond);
                        outToClient.flush();
                        String msg = msgSplit[2] + " 님이 입장하셨습니다.";
                        sendBroad(msgSplit[2],msg);
                    } else if (msgSplit[0].equals("#EXIT")) {
                        //send EXIT msg to client receiver
                        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                        outToClient.writeUTF(clientMsg);
                        outToClient.flush();
                        //send EXIT msg to broad
                        String msg = msgSplit[1] + " 님이 퇴장하셨습니다.";
                        sendBroad(msgSplit[1], msg);

                        //delete member
                        chatStatuses.remove(msgSplit[1]);
                        clientSockets.remove(msgSplit[1]);
                        break;
                    } else if (msgSplit[0].equals("#STATUS")) {
                        //write respond
                        respond = "현재 채팅방 이름: "+chatStatuses.get(msgSplit[1]);
                        respond += "\n구성원 정보:";
                        int i = 1;
                        for (Map.Entry<String, String> pair : chatStatuses.entrySet()) {
                            if (pair.getValue().equals(chatStatuses.get(msgSplit[1]))) {
                                respond += "\n(" + i + ") " + pair.getKey();
                                i++;
                            }
                        }
                        //send respond
                        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                        outToClient.writeUTF(respond);
                        outToClient.flush();
                    }  else if (msgSplit[0].equals("#PUT")) {
                        String fileName = msgSplit[1];
                        String userName = msgSplit[2];
                        String chatroomName = chatStatuses.get(userName);
                        ServerSocket welcomeFileSocket = new ServerSocket(portNum2);
                        Thread fileServerReceiver = new Thread(new FileServerReceiver(welcomeFileSocket, connectionSocket, chatroomName, userName, fileName));
                        fileServerReceiver.start();

                    } else if (msgSplit[0].equals("#GET")) {
                        String fileName = msgSplit[1];
                        String userName = msgSplit[2];
                        String chatroomName = chatStatuses.get(userName);
                        ServerSocket welcomeFileSocket = new ServerSocket(portNum2);
                        Thread fileServerSender = new Thread(new FileServerSender(welcomeFileSocket, connectionSocket, chatroomName, userName, fileName));
                        fileServerSender.start();
                    } else {
                        //just send chatting msg
                        sendBroad(msgSplit[1], clientMsg);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private static void sendBroad(String userName, String msg) {
        try {
            for (Map.Entry<String, String> pair : chatStatuses.entrySet()) {
                System.out.println("chatroomname: "+pair.getValue()+" userName: "+pair.getKey());

            }
            String chatroomName = chatStatuses.get(userName);
            for (Map.Entry<String, String> pair : chatStatuses.entrySet()) {
                if (pair.getValue().equals(chatroomName) && !pair.getKey().equals(userName)) {
                    System.out.println("send to");
                    System.out.println("chatroomname: "+chatroomName+" userName: "+userName);

                    Socket connectionSocket = clientSockets.get(pair.getKey());
                    //send msg to connected client
                    DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
                    outToClient.writeUTF(msg);
                    outToClient.flush();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static class FileServerReceiver extends Thread {
        private ServerSocket welcomeFileSocket;
        private Socket connectionSocket;
        private String chatroomName;
        private String userName;
        private String fileName;
        FileServerReceiver(ServerSocket welcomeFileSocket, Socket connectionSocket, String chatroomName, String userName, String fileName) {
            this.welcomeFileSocket = welcomeFileSocket;
            this.connectionSocket = connectionSocket;
            this.chatroomName = chatroomName;
            this.userName = userName;
            this.fileName = fileName;
        }

        public void run() {
            try {
                //create socket, wait to accept
                System.out.println("Waiting for file socket connection");
                welcomeFileSocket.setSoTimeout(100); // if file not found exception
                Socket fileConnectionSocket = welcomeFileSocket.accept();
                System.out.println("Accept connection");
                byte[] contents = new byte[65536];

                //Initialize the FileOutputStream to the output file's full path.
                //src dir 하위에 채팅방 폴더 생성
                String path = "assignment2/"+chatroomName;
                File dir = new File(path);
                if(!dir.exists()) dir.mkdir();

                FileOutputStream fos = new FileOutputStream(path+"/"+fileName);
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                InputStream is = fileConnectionSocket.getInputStream();

                //number of bytes read in one read() call
                int bytesRead = 0;
                System.out.print("Saving...");
                while ((bytesRead = is.read(contents)) != -1) {
                    bos.write(contents, 0, bytesRead);
                    System.out.print("#");
                }
                bos.flush();
                fileConnectionSocket.close();
                welcomeFileSocket.close();
                System.out.println("File saved successfully!");
                System.out.println("file connection socket closed");
                //Upload msg broadcast
                String msg = userName + " 님이 파일(" + fileName + ")을 업로드했습니다. 다운로드 하려면 #GET " + fileName + "을 입력하세요.";
                sendBroad(userName, msg);

            } catch (SocketTimeoutException se){
                System.out.println("No such file");
                try {
                    welcomeFileSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private static class FileServerSender extends Thread {
        private ServerSocket welcomeFileSocket;
        private Socket connectionSocket;
        private String chatroomName;
        private String userName;
        private String fileName;
        FileServerSender(ServerSocket welcomeFileSocket, Socket connectionSocket, String chatroomName, String userName, String fileName) {
            this.welcomeFileSocket = welcomeFileSocket;
            this.connectionSocket = connectionSocket;
            this.chatroomName = chatroomName;
            this.userName = userName;
            this.fileName = fileName;
        }

        public void run() {
            try {
                //file not found exception
                File file = new File("assignment2/" + chatroomName + "/" + fileName);
                FileInputStream fis = new FileInputStream(file);

                //create socket, wait to accept
                System.out.println("Waiting for file socket connection");
                Socket fileConnectionSocket = welcomeFileSocket.accept();
                System.out.println("Accept connection");

                //initialize stream
                BufferedInputStream bis = new BufferedInputStream(fis);
                OutputStream os = fileConnectionSocket.getOutputStream();

                //Read File Contents into contents array
                byte[] contents;
                System.out.print("Sending file ... ");
                long fileLength = file.length();
                long current = 0;
                while (current != fileLength) {
                    int size = 65536;
                    if (fileLength - current >= size)
                        current += size;
                    else {
                        size = (int) (fileLength - current);
                        current = fileLength;
                    }
                    contents = new byte[size];
                    bis.read(contents, 0, size);
                    os.write(contents);
                    System.out.print("#");
                }
                os.flush();
                fileConnectionSocket.close();
                welcomeFileSocket.close();
                System.out.println("\nFile sent successfully!");

            } catch(FileNotFoundException fe) {
                System.out.println("No such file");
                try {
                    welcomeFileSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
