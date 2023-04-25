package assignment2;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TCPClient {

    public static void main(String args[]) {
        //argument
        if (args.length != 3) {
            System.out.println("INVALID ARGUMENTS INPUT");
            System.exit(0);
        }
        String serverIPAddr = args[0];
        int portNum1 = Integer.parseInt(args[1]);
        int portNum2 = Integer.parseInt(args[2]);

        try {
            //initialize
            Socket clientSocket = null;
            Thread sender = new Thread();
            Thread receiver = new Thread();
            while (true) {
                //thread 종료 여부 확인
                if (sender.isAlive() || receiver.isAlive()) {
                    Thread.sleep(1000); //반복문의 resource 낭비 제약
                    continue; //종료되지 않았다면 다음으로 넘어갈 수 없음
                }
                //start with command
                String chatroomName = null;
                String userName = null;
                //create client socket, connect to server
                clientSocket = new Socket(serverIPAddr, portNum1);
                while (true) {
                    System.out.println("***");
                    System.out.println("채팅방을 생성하시려면 #CREATE (채팅방이름) (사용자이름),");
                    System.out.println("채팅방에 참여하시려면 #JOIN (채팅방이름) (사용자이름),");
                    System.out.println("채팅을 종료하시려면 #QUIT을 입력해주세요.");
                    System.out.println("***");
                    Scanner s = new Scanner(System.in);
                    String command = s.next();
                    //QUIT
                    if (command.equals("#QUIT")) {
                        clientSocket.close();
                        System.exit(0);
                    }
                    //CREATE or JOIN
                    else if (command.equals("#CREATE") || command.equals("#JOIN")) {
                        chatroomName = s.next();
                        userName = s.next();

                        //send create/join request msg to server
                        String request = command + " "+ chatroomName + " " + userName;
                        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                        outToServer.writeUTF(request);

                        //receive success/failure respond msg from server
                        DataInputStream inFromServer = new DataInputStream(clientSocket.getInputStream());
                        String respond = inFromServer.readUTF();
                        if(respond.equals("success")) {
                            System.out.println(command+" "+respond+".");
                            //thread client send/receive
                            sender = new Thread(new ClientSender(clientSocket, serverIPAddr, portNum2, chatroomName, userName));
                            receiver = new Thread(new ClientReceiver(clientSocket, serverIPAddr, portNum2, chatroomName, userName));
                            sender.start();
                            receiver.start();
                            break;
                        }
                        //FAIL -> input command again(loop back)
                        else if (respond.equals("failure")){
                            if(command.equals("#CREATE")) System.out.println("FAIL. Room name already exists OR User name already exists.");
                            else System.out.println("FAIL. Room name does not exist OR User name already exists.");
                        } else System.out.println("Unknown failure");

                    } else System.out.println("Command not valid.");
                }
                //loop back
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class ClientSender extends Thread {
        private Socket clientSocket;
        private String serverIPAddr;
        private int portNum2;
        private String chatroomName;
        private String userName;

        ClientSender(Socket clientSocket, String serverIPAddr, int portNum2, String chatroomName, String userName) {
            this.clientSocket = clientSocket;
            this.serverIPAddr = serverIPAddr;
            this.portNum2 = portNum2;
            this.chatroomName = chatroomName;
            this.userName = userName;
        }

        public void run() {
            try {
                while(true) {
                    //create input stream
                    BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
                    String msg = inFromUser.readLine();
                    // # 처리
                    if(!msg.isEmpty() && msg.charAt(0)=='#') {
                        if (msg.equals("#EXIT")) {
                            //send exit msg
                            msg = msg + " " + userName;
                            DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                            outToServer.writeUTF(msg);
                            break; //thread 종료
                        }
                        else if (msg.startsWith("#PUT ")) {
                            String fileName = msg.substring(5);
                            //send put msg to server
                            msg = msg + " " + userName;
                            DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                            outToServer.writeUTF(msg);

                            //new thread for sending
                            Thread fileClientSender = new Thread(new FileClientSender(serverIPAddr, portNum2, chatroomName, userName, fileName));
                            fileClientSender.start();
                        }
                        else if (msg.startsWith("#GET ")) {
                            String fileName = msg.substring(5);
                            //send get msg to server
                            msg = msg + " " + userName;
                            DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                            outToServer.writeUTF(msg);

                            //new thread for sending
                            Thread fileClientReceiver = new Thread(new FileClientReceiver(serverIPAddr, portNum2, chatroomName, userName, fileName));
                            fileClientReceiver.start();
                        }
                        else if (msg.equals("#STATUS")) {
                            msg = msg + " " + userName;
                            DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                            outToServer.writeUTF(msg);
                        }
                        else {
                            System.out.println("Command not valid.");
                            continue;
                        }
                    }
                    // just chatting msg
                    else {
                        msg = "FROM " + userName + " : " + msg;
                        //create output stream attached to socket
                        DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                        //send msg to server
                        outToServer.writeUTF(msg);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private static class ClientReceiver extends Thread {
        private Socket clientSocket;
        private String serverIPAddr;
        private int portNum2;
        private String chatroomName;
        private String userName;

        ClientReceiver(Socket clientSocket, String serverIPAddr, int portNum2, String chatroomName, String userName) {
            this.clientSocket = clientSocket;
            this.serverIPAddr = serverIPAddr;
            this.portNum2 = portNum2;
            this.chatroomName = chatroomName;
            this.userName = userName;
        }

        public void run() {
            try {
                while(true) {
                    //create input stream attached to socket
                    DataInputStream inFromServer = new DataInputStream(clientSocket.getInputStream());
                    //read line from server
                    String msg = inFromServer.readUTF();
                    //EXIT
                    if (msg.equals("#EXIT " + userName)) {
                        break; //thread 종료
                    }
                    System.out.println(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
    private static class FileClientSender extends Thread {
        private String serverIPAddr;
        private int portNum2;
        private String chatroomName;
        private String userName;
        private String fileName;

        FileClientSender(String serverIPAddr, int portNum2, String chatroomName, String userName, String fileName) {
            this.serverIPAddr = serverIPAddr;
            this.portNum2 = portNum2;
            this.chatroomName = chatroomName;
            this.userName = userName;
            this.fileName = fileName;
        }

        public void run() {
            try {
                File file = new File("assignment2/" + fileName);
                //file not found exception
                FileInputStream fis = new FileInputStream(file);
                if(file.length()!=0) {
                    //create client socket, connect to server
                    Socket clientFileSocket = new Socket(serverIPAddr, portNum2);

                    //initialize stream
                    BufferedInputStream bis = new BufferedInputStream(fis);
                    OutputStream os = clientFileSocket.getOutputStream();

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
                    clientFileSocket.close();
                    System.out.println("\nFile sent succesfully!");
                } else {
                    System.out.println("Can't send empty file");
                }
            } catch (FileNotFoundException fe) {
                System.out.println("No such file");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    private static class FileClientReceiver extends Thread {
        private String serverIPAddr;
        private int portNum2;
        private String chatroomName;
        private String userName;
        private String fileName;

        FileClientReceiver(String serverIPAddr, int portNum2, String chatroomName, String userName, String fileName) {
            this.serverIPAddr = serverIPAddr;
            this.portNum2 = portNum2;
            this.chatroomName = chatroomName;
            this.userName = userName;
            this.fileName = fileName;
        }

        public void run() {
            try {
                //create client socket, connect to server
                SocketAddress setTimeSocket = new InetSocketAddress(serverIPAddr, portNum2);
                Socket clientFileSocket = new Socket();
                // if file not found exception, no accept
                clientFileSocket.connect(setTimeSocket, 2000);

                //if input stream not null
                InputStream is = clientFileSocket.getInputStream();
                int bytesRead = 0;
                byte[] contents = new byte[65536];
                if ((bytesRead = is.read(contents)) != -1) {
                    //Initialize the FileOutputStream to the output file's full path.
                    //assignment2 package 하위에 저장됨
                    FileOutputStream fos = new FileOutputStream("assignment2/" + fileName);
                    BufferedOutputStream bos = new BufferedOutputStream(fos);

                    //number of bytes read in one read() call
                    System.out.print("Downloading file ... #");
                    bos.write(contents, 0, bytesRead);
                    while ((bytesRead = is.read(contents)) != -1) {
                        bos.write(contents, 0, bytesRead);
                        System.out.print("#");
                    }

                    bos.flush();
                    System.out.println("\nFile downloaded successfully!");

//                    //파일 출력
//                    BufferedReader br = new BufferedReader(new FileReader("assignment2/" + fileName));
//                    String line;
//                    while ((line = br.readLine()) != null) {
//                        System.out.println(line);
//                    }
                }
                clientFileSocket.close();
            } catch (SocketException se) {
                System.out.println("No such file");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}