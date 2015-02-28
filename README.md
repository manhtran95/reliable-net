# Overview
The task is to write a sender program and a receiver program which can reliably communicate with each other over UDP (User Datagram Protocol). In other words, we need to design and implement an application level protocol which can achieve reliable file transfer on top of UDP.

# Programs Provided
UnreliNET-Normal.java, UnreliNETCorrupt.java, UnreliNETReorder.java, UnreliNETDrop.java.

# Unreliable Network Simulator
Packets passing through the UnreliNet may be corrupted, dropped or reordered with certain probability. We need to design a reliable protocol so that files can be successfully transferred sucessfully.

# UnreliNet Program
To start the UnreliNet:
java UnreliNet sk1 port sk2 port sk3 port sk4 port ratio |
e.g. java UnreliNETNormal 10000 10001 10002 10003 |
If you want to test the corruption case then run: java UnreliNETCorrupt 10000 10001 10002 10003 0.3 |
If you want to test the packet drop case then run: java UnreliNETDrop 10000 10001 10002 10003 0.2 |
If you want to test the packet reorder case then run: java UnreliNETReorder 10000 10001 10002 10003 0.1 |
You can change the ratio to test whether your program run efficiently under different network conditions.

# Receiver Program
To start the receiver:
java Receiver sk2 port sk3 port outputFolderPath |
e.g. java Receiver 10001 10002 /outFiles/

# Sender Program
To start the sender:
java Sender sk1 port sk4 port inputFilePath outputFileName |
e.g. java Sender 10000 10003 /testFile.txt receiveFile.txt

# Problems solved
Checksum: Because the data can be corrupted, we need to calculate the checksum when
receive the data segment.
Timeout: With packet loss, we need to set timeout to retransmit the packet.
Sequence Number: To check whether the data received in order, you need to add a sequence number
to the payload.
