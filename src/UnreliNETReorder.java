package reliableNet;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class UnreliNETReorder {
	static int buf_size = 1500;
	static float delay_pct = 0.1f;
	static int delay_value = 500;
	//static float drop_pct = 0.1f;

	// define thread which is used to handle each delayed packet
	public class DelayPktThread extends Thread {
		Semaphore sem;
		DatagramSocket sk_out;
		int dst_port;
		InetAddress dst_addr;
		byte[] in_data;
		int packetLength;

		public DelayPktThread(Semaphore sem, DatagramSocket sk_out,
				byte[] data, InetAddress addr, int dp, int len) {
			this.sem = sem;
			this.sk_out = sk_out;
			this.dst_port = dp;
			this.dst_addr = addr;
			this.in_data = data;
			this.packetLength = len;
		}

		public void run() {
			try {
				Thread.sleep(delay_value);
				// write data to the outgoing socket
				DatagramPacket out_pkt = new DatagramPacket(in_data,
						packetLength, dst_addr, dst_port);
				sem.acquire();
				sk_out.send(out_pkt);
				sem.release();
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	// define thread which is used to handle one-direction of communication
	public class UnreliThread extends Thread {
		private DatagramSocket sk_in, sk_out;
		private Semaphore sem = new Semaphore(1, true);
		private Random rnd_delay = new Random(System.currentTimeMillis());
		private int dst_port;
		private Random rnd = new Random();

		public UnreliThread(DatagramSocket in, DatagramSocket out,
				int dp) {
			sk_in = in;
			sk_out = out;
			dst_port = dp;
		}

		public void run() {
			try {
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");
				
				while (true) {
					// read data from the incoming socket
					byte[] in_data = new byte[buf_size];
					DatagramPacket in_pkt = new DatagramPacket(in_data,
							in_data.length);
					sk_in.receive(in_pkt);
		
					// check the length of the packet
					if (in_pkt.getLength() > 1000) {
						System.err.println("Error: received packet of length "
								+ in_pkt.getLength() + " from "
								+ in_pkt.getAddress().toString() + ":"
								+ in_pkt.getPort());
						System.exit(-1);
					}

					// decide if to delay the packet or not
					if (rnd_delay.nextFloat() <= delay_pct) {
						DelayPktThread delay_th = new DelayPktThread(sem,
								sk_out, in_data, dst_addr, dst_port, in_pkt.getLength());
						delay_th.start();
						System.out.println("Packet Reordered");
						continue;
					}

					// write data to the outgoing socket
					DatagramPacket out_pkt = new DatagramPacket(in_data,
							in_pkt.getLength(), dst_addr, dst_port);
					sem.acquire();
					sk_out.send(out_pkt);
					sem.release();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

public UnreliNETReorder(int sk1_dst_port, int sk2_dst_port, int sk3_dst_port, int sk4_dst_port, float ratio) {
		DatagramSocket sk1, sk2;
		
		delay_pct = ratio;
		System.out.println("sk1_dst_port=" + sk1_dst_port
				+ ", sk2_dst_port=" + sk2_dst_port 
				+ ", sk3_dst_port="	+ sk3_dst_port
				+ ", sk4_dst_port=" + sk4_dst_port
				+ ", ratio to reorder=" + ratio+".");

		try {
			// Create socket sk1 and sk2
			sk1 = new DatagramSocket(sk1_dst_port);
			sk2 = new DatagramSocket();

			// create threads to process sender's incoming data
			UnreliThread th1 = new UnreliThread(sk1, sk2, sk2_dst_port);
			th1.start();

			// Create the thread to receive from sk3 and send ack to senders
			DatagramSocket sk3 = new DatagramSocket(sk3_dst_port);
			DatagramSocket sk4 = new DatagramSocket();
			
			UnreliThread th2 = new UnreliThread(sk3, sk4, sk4_dst_port);
			th2.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

public static void main(String[] args) {
		// parse parameters
		if (args.length != 5) {
			System.err
					.println("Usage: java UnreliNETReorder sk1_dst_port, sk2_dst_port, sk3_dst_port, sk4_dst_port, ratio");
			System.exit(-1);
		} else {
			new UnreliNETReorder(Integer.parseInt(args[0]),
					Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Float.parseFloat(args[4]));
		}
	}
}
