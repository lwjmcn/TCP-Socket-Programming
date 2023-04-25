# TCP Socket Programming

## 개발환경

Window10 / IntelliJ IDEA

## 실행환경

IntelliJ IDEA 2022.2

SDK openjdk-15 Amazon Corretto version 15.0.2

## 개요

- TCP protocol을 이용한 Client-Server 방식 오픈 채팅 프로그램 구현

## 과제 명세

### 1. 채팅 프로그램 실행

- Server 역할: 채팅 중계 (클라이언트 간 연결 수행, 메시지 송수신)
- Port number 1: 채팅 메시지 송수신, 명령어 전송
- Port number 2: #PUT, #GET 동작
    - Client 프로그램 실행 인자: Server IP address, port number 1, port number2
    - Server 프로그램 실행 인자: port number 1, port number2
- ‘#’로 시작하는 문장은 명령어로 처리(==’#’로 시작하는 메시지를 보낼 수 없다)

### 2. 채팅 명령

- Client
    1. #CREATE (chatroom-name) (user-name)
        1. 채팅방 create request msg
        2. 생성 성공 시 채팅 시작 가능
    2. #JOIN (chatroom-name) (user-name)
        1. 채팅방 join request msg
        2. 참여 성공 시 채팅 시작 가능
    3. 채팅
        1. msg 서버로 전송
        2. 송신자를 제외한 채팅방 참여자 클라이언트는 서버로부터 메시지 수신
    4. 파일 송수신
        1. port number 2를 이용해 별도의 TCP 연결 생성
        2. 클라이언트 화면에 파일 송수신 진척도 표시 (64Kbyte 당 ‘#’ 문자 1개 출력)
        3. #PUT (file-name)
            1. 서버로 file 전송
        4. #GET (file-name)
            1. 수신자는 서버로부터 file-name을 전달 받음
            2. file-name을 통해 서버로부터 파일을 다운로드
    5. #EXIT
        1. 현재 채팅방에서 떠남 
    6. #STATUS
        1. 현재 채팅방의 이름, 구성원의 정보(이름) 출력
- Server
    - N개의 채팅방 동시 지원(multi-thread)
    1. create request msg 수신 시 채팅방 생성 + 중계 수행
        1. 성공 시 success msg 전송
        2. 채팅방 이름이 이미 존재할 시 failure msg 전송
    2. join request msg 수신 시 채팅방에 추가 + 중계 수행
        1. 성공 시 success msg 전송
        2. 채팅방이 존재하지 않을 시 failure msg 전송
    3. 채팅
        1. 송신자의 msg 수신
        2. 모든 수신자에게 메시지 송신
    4. 파일 송수신
        1. port number 2를 이용해 별도의 TCP 연결 생성
        2. #PUT 송신자로부터 파일을 전달받음
        3. #GET 수신자에게 파일을 전달
    5. #EXIT
        1. 채팅방을 떠난 클라이언트를 채팅방 멤버에서 제거
    6. #STATUS
        1. 현재 채팅방의 이름, 구성원의 정보(이름) 전달

### 3. 추가구현

Single thread + non-blocking IO

java.nio.channels.Selector
java.nio.channels.ServerSocketChannel
java.nio.channels.SocketChannel
java.nio.channels.SelectionKey
java.nio.ByteBuffer

## 프로그램 구조

![Untitled](https://user-images.githubusercontent.com/114637188/234229117-997e1dd0-0027-4976-858e-8c399fbe7797.png)

**TCPClient(클라이언트측)**

void main : 채팅 시작/종료 명령어 입력, 메시지 송수신 스레드 시작

ClientSender: 메시지 송신 스레드, PUT/GET/STATUS/EXIT 명령어 입력, 파일 송수신 스레드 시작

ClientReceiver: 서버로부터 채팅 메시지를 수신하여 화면에 출력

FileClientSender: 하나의 파일을 서버에게 송신하는 스레드

FileClientReceiver: 하나의 파일을 서버로부터 수신하는 스레드

**TCPServer(서버측)**

void main: argument 처리 후 start 함수 시작

void start: 클라이언트 측 연결 요청을 기다리고 수락하는 것을 반복, 메시지 수신 스레드 시작

ServerReceiver: 메시지 수신, 명령어 처리, 파일 송수신 스레드 시작

void sendBroad: 메시지를 채팅방 멤버들에게 수신

FileServerReceiver: 클라이언트로부터 1개의 파일을 수신, 채팅방별 폴더에 저장하고, 채팅방 멤버들에게 파일이 업로드 됨을 알림

FileServerSender: 클라이언트가 다운로드 요청한 파일을 송신함

## 코드 설명

<aside>
💡 자세한 내용은 코드 주석을 참고해주세요.

</aside>

package assignment2/

### **TCPClient**

- void main (프로그램 시작)
    - String[] args parsing
        - 3개의 input이 없으면 invalid 처리 후 프로그램 종료
    - while 반복문
        - sender thread와 receiver thread가 모두 종료 상태인지 확인
        - 서버IP주소, 포트번호1로 clientSocket 생성
        - while 반복문
            - 채팅 프로그램 시작 메시지 출력, 명령어 입력(case-sensitive)
            - #CREATE / JOIN :
                - 서버에 request 메시지를 보냄
                
                ```java
                String request = "서버로 전송할 메시지";
                DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
                outToServer.writeUTF(request);
                ```
                
                - 서버로부터 respond 메시지를 받음
                
                ```java
                DataInputStream inFromServer = new DataInputStream(clientSocket.getInputStream());
                String respond = inFromServer.readUTF();
                ```
                
                - respond “failure”: 에러메시지 출력 후 loop back
                - respond “success”
                    - clientSocket(메시지송수신), serverIPAddr, portNum2(파일송수신 소켓 생성), chatroomName, userName을 인자로 `ClientSender`, `ClientReceiver` thread 생성(constructor)
                        
                        → 메시지 송수신 동시동작
                        
                    - thread start() → run() 함수 실행
                    - loop back 후 thread 종료 여부 확인
            - #QUIT : 프로그램 종료
            - 이 외의 입력은 Invalid로 처리한 후 loop back
    
    **Inner classes**
    
- ClientSender class (채팅메시지 또는 명령어를 입력받아 서버에게 전송)
    - member variables
    - ClientSender constructor
    - void run()
        - while 반복문 (사용자 입력 create input stream)
        
        ```java
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
        String msg = inFromUser.readLine();
        ```
        
        ※ msg 전송 시 반드시 user name을 포함시킨다
        
        - #EXIT: 서버로 EXIT msg 전송, thread 종료
        - #STATUS: 서버로 STATUS request msg 전송
        - #PUT
            - 서버로 PUT request msg 전송
            - serverIPAddr, portNum2, chatroomName, userName, fileName를 인자로 `FileClientSender` thread 생성 후 start()
                
                → 파일 전송, 메시지 송수신 동시동작
                
        - #GET
            - 서버로 GET request msg 전송
            - serverIPAddr, portNum2, chatroomName, userName, fileName를 인자로 `FileClientSender` thread 생성 후 start()
                
                → 파일 수신, 메시지 송수신 동시동작
                
        - 이 외의 #로 시작하는 입력은 Invalid 처리, loop back
        - 채팅 메시지: 서버로 전송
    
- ClientReceiver class (서버로부터 채팅메시지를 수신하여 화면에 출력)
    - member variables
    - ClientReceiver constructor
    - void run()
        - while 반복문
            - input stream을 생성하여 서버로부터 메시지 수신
            - #EXIT msg라면 thread 종료
            - 화면에 메시지 출력
        
- FileClientSender class (1개의 파일을 서버에게 전송)
    - member variables
    - FileClientSender constructor
    - void run()
        - File inputstream을 먼저 생성하여 file not found exception이 발생하면 clientFileSocket을 생성하지 않고 catch문으로 넘어가도록 함
        - File length가 0이 아니라면, serverIPAddr, portNum2로 clientFileSocket을 새로 생성
        - input stream, output stream 생성
        
        ```java
        FileInputStream fis = new FileInputStream(file);
        // file.length() != 0
        BufferedInputStream bis = new BufferedInputStream(fis);
        OutputStream os = clientFileSocket.getOutputStream();
        ```
        
        - 파일 전송, 64 Kbytes = 65536 bytes 단위로 ‘#’ 출력
        
        ```java
        while(true) {
        	...
        	contents = new byte[size];
        	bis.read(contents, 0, size);
        	os.write(contents);
        }
        os.flush();
        ```
        
        - clientFileSocket.close(), thread 종료
        - FileNotFoundException catch문: 파일 경로에  파일이 존재하지 않으면 화면에 “No such file” 출력
        
- FileClientReceiver class (서버로부터 1개의 파일 수신/다운로드)
    - member variables
    - FileClientReceiver constructor
    - void run()
        - serverIPAddr, portNum2로 Socket 생성
        - SocketAddress class를 활용해 clientFileSocket의 connection timer를 설정 (Server측에서 file not found이면 accept 하지 않음)
        - clientFileSocket의 Input stream이 비어있지 않다면 input stream, output stream 생성
        
        ```java
        FileOutputStream fos = new FileOutputStream("assignment2/"+fileName);
        //is.read != -1
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        InputStream is = clientFileSocket.getInputStream();
        ```
        
        - 파일 수신, 64 Kbytes = 65536 bytes 단위로 ‘#’ 출력
        
        ```java
        while ((bytesRead = is.read(contents)) != -1) {
        	bos.write(contents, 0, bytesRead);
        	System.out.print("#");
        }
        bos.flush();
        ```
        
        - clientFileSocket.close(), thread 종료
        - SocketException catch문: clientFileSocket timer가 expire되면 화면에 “No such file” 출력

### **TCPServer**

- member variables
    - `Map<String,String> *chatStatuses*` (사용자 이름,  사용자가 참여 중인 채팅방 이름)
    - `Map<String,Socket> *clientSockets*` (사용자 이름, 연결 소켓)
- TCPServer constructor
    - `Collections.*synchronizedMap*(new HashMap<>())` Map의 데이터 무결성 보장
- void main
    - String[] args parsing
        - 2개의 input이 없으면 invalid 처리 후 프로그램 종료
        - portNum 2가지를 인수로 TCPServer()의 `start()` 함수 call
- void start
    - portNum1으로 welcomeSocket 생성
    - while 반복문 (새로운 client connection을 계속해서 accept)
        - client로부터 온 connection 요청을 welcomeSocket이 accept하여 connectionSocket 생성
        - 연결된 client마다 connectionSocket, portNum2를 인자로 `ServerReceiver` thread를 생성, start()

- void sendBroad(userName, msg)
    - chatStatuses 멤버 변수를 통해 userName이 속한 chatroomName을 찾음
    - 다시 chatStatuses 멤버 변수를 통해 chatroomName에 속한 userName들을 찾고, clientSockets 멤버 변수를 통해 각 user별 소켓을 찾아 msg를 전송

**Inner classes**

- ServerReceiver
    - member variables
    - ServerReceiver constructor
    - void run()
        - client로부터 메시지를 수신
        
        ```java
        DataInputStream inFromClient = new DataInputStream(connectionSocket.getInputStream());
        String clientMsg = inFromClient.readUTF();
        ```
        
        - clientMsg를 split하여 명령어 여부, userName을 알 수 있다
        - #CREATE
            - chatStatuses 멤버 변수를 통해 채팅방이름, 사용자이름이 모두 존재하지 않는 것을 확인하면 “success”
            - 아닌 경우 “failure”
            - “success” 또는 “failure” respond msg를 요청한 client에게 전송
            
            ```java
            DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
            outToClient.writeUTF(respond);
            outToClient.flush();
            ```
            
        - #JOIN
            - chatStatuses 멤버 변수를 통해 채팅방이름은 존재하고, 사용자이름은 존재하지 않는 것을 확인하면 “success”
            - 아닌 경우 “failure”
            - “success” 또는 “failure” respond msg를 요청한 client에게 전송
            - `sendBroad` 함수를 통해 채팅방에 있던 멤버들에게 client의 입장을 알림
        - #EXIT
            - exit을 요청한 client에게 exit msg 재전송하여 ClientReceiver가 socket을 close하도록 함
            - `sendBroad` 함수를 통해 채팅방 멤버들에게 client의 퇴장을 알림
            - chatStatuses, clientSockets 멤버 변수에서 사용자를 삭제함
            - thread 종료
        - #STATUS
            - chatStatuses를 통해 요청한 client가 속한 채팅방의 구성원 정보를 respond로 저장하여 요청한 client에게 전송
        - #PUT
            - msg를 parsing하여 정보를 얻음
            - portNum2로 welcomeFileSocket 생성
            - welcomeFileSocket, connectionSocket, chatroomName, userName, fileName을 인자로 `FileServerReceiver` thread를 생성, start()
        - #GET
            - msg를 parsing하여 정보를 얻음
            - portNum2로 welcomeFileSocket 생성
            - welcomeFileSocket, connectionSocket, chatroomName, userName, fileName을 인자로 `FileServerSender` thread를 생성, start()
        - else, 채팅 메시지
            - `sendBroad` 함수를 통해 메시지를 채팅방 멤버들에게 전송

- FileServerReceiver class
    - member variables
    - ServerReceiver constructor
    - void run()
        - welcomeFileSocket에 timer를 설정하여, client측에서 file not found exception이 발생하여 connection 요청이 오지 않는 경우 SocketTimeoutException을 발생시킴
        - welcomeFileSocket으로 client의 연결 요청을 accept하여 fileConnectionSocket을 생성
        - File과 mkdir()를 통해 src 폴더 하위에 채팅방 전용 폴더를 생성하고, 그 하위에서 파일을 수신한다.
        - input stream, output stream 생성
        
        ```java
        FileOutputStream fos = new FileOutputStream(path+"/"+fileName);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        InputStream is = fileConnectionSocket.getInputStream();
        ```
        
        - 파일 수신, 64 Kbytes = 65536 bytes 단위로 ‘#’ 출력
        
        ```java
        while ((bytesRead = is.read(contents)) != -1) {
        	bos.write(contents, 0, bytesRead);
        	System.out.print("#");
        }
        bos.flush();
        ```
        
        - `sendBroad` 함수를 통해 채팅방 멤버들에게 파일 업로드 정보를 알림
        - fileConnectionSocket.close(); welcomeFileSocket.close(); thread 종료
        - SocketTimeoutException catch문 : welcomeFileSocket.close()

- FileServerSender class
    - member variables
    - FileClientSender constructor
    - void run()
        - File inputstream을 먼저 생성하여 file not found exception이 발생하면 clientFileSocket을 생성하지 않고 catch문으로 넘어가도록 함
        - welcomeFileSocket으로 client의 연결 요청을 accept하여 fileConnectionSocket을 생성
        - input stream, output stream 생성
        
        ```java
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        OutputStream os = clientFileSocket.getOutputStream();
        ```
        
        - 파일 전송, 64 Kbytes = 65536 bytes 단위
        
        ```java
        while(true) {
        	...
        	contents = new byte[size];
        	bis.read(contents, 0, size);
        	os.write(contents);
        }
        os.flush();
        ```
        
        - fileConnectionSocket.close(); welcomeFileSocket.close(), thread 종료
        - FileNotFoundException catch문: welcomeFileSocket.close()

## 실행(cmd)

❕ 서버를 재시작하려는 경우 assignment2 dir 하위에 채팅방별로 생성된 폴더를 직접 삭제해주어야 함

### 컴파일 방법

- \src> javac assignment2/TCPServer.java -encoding utf-8
- \src> javac assignment2/TCPClient.java -encoding utf-8

### 실행 방법

- 송신하고자 하는 파일은 assignment2 package 하위에 있어야 함
    - 예시: a.txt(null파일), alice.txt(텍스트파일), cat.jpg(이미지파일)
- 예시로 alice.txt 텍스트 파일과 cat.jpg 이미지 파일을 넣어둠.
1. TCPServer를 먼저 실행
    - cmd : \src> java assignment2.TCPServer 2021 2022
2. TCPClient를 실행 (localhost:127.0.0.1)
    - cmd : \src> java assignment2.TCPClient 127.0.0.1 2021 2022
3. Client 측에서 명령어를 입력
    1. #CREATE chatroomName userName 
    2. #JOIN chatroomName userName
    3. #QUIT
4. 채팅방 참여 성공 시 명령어 또는 메시지 입력 가능
    1. #PUT fileName
    2. #GET fileName
    3. #STATUS
    4. #EXIT
    5. 일반 메시지

### 실행 결과

- 컴파일, CREATE/JOIN 중복 처리

![Untitled 1](https://user-images.githubusercontent.com/114637188/234229232-323e350a-76c4-49ff-a866-d8cb27edbc51.png)

- 메시지 송수신

![Untitled 2](https://user-images.githubusercontent.com/114637188/234229251-1f5c488c-3e1b-49f0-9db5-0247367ebcfd.png)

- 파일 송수신(localhost이므로 client측은 파일을 덮어쓰기)

![Untitled 3](https://user-images.githubusercontent.com/114637188/234229270-f24da9b4-c49c-41d9-838a-91ba1cc62312.png)

- 파일 송수신 error handling (file not found)

![Untitled 4](https://user-images.githubusercontent.com/114637188/234229285-a1b8be3f-2111-47ac-a364-fd77ec619718.png)

- 구성원 정보 확인

![Untitled 5](https://user-images.githubusercontent.com/114637188/234229301-6ef5d9a3-e96c-4259-b245-e0ada07be12e.png)

- 퇴장, 입장 error handling

![Untitled 6](https://user-images.githubusercontent.com/114637188/234229328-afa5b6dc-9fc2-464e-8db7-a9d98ad261ec.png)

- 프로그램 종료 (EOFException은 프로그램에 영향을 주지 않고, printstacktrace 출력은 사용자 측에 나타나지 않는 서버 화면이기 때문에 추가로 handling하지 않음)

![Untitled 7](https://user-images.githubusercontent.com/114637188/234229360-2d403365-33ca-43ae-bd97-f7a4fc70ac2f.png)

- 서버 프로그램을 종료하지 않으면, 클라이언트 측에서 프로그램을 종료했다가 재시작하더라도 채팅방 정보가 그대로 남아있는다
- (추가) 비어있는 a.txt 파일을 생성, 내용이 비어있는 파일은 보낼 수 없게 처리함

![Untitled 8](https://user-images.githubusercontent.com/114637188/234229387-d0fb433f-705d-4250-9ea6-97d85647c73a.png)
