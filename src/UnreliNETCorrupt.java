package reliableNet;

import java.net.*;
import java.util.*;

public class UnreliNETCorrupt {
	static int buf_size = 1500;
	static float corrupt_pct = 0.1f;
	static float byte_corrupt_pct = 0.3f;

	// define thread which is used to handle one-direction of communication
	public class UnreliThread extends Thread {
		private DatagramSocket sk_in, sk_out;
		private Random rnd = new Random();
		private Random rnd_byte = new Random();
		private int dst_port;

		public UnreliThread(DatagramSocket in, DatagramSocket out,
				int dp) {
			sk_in = in;
			sk_out = out;
			dst_port = dp;
		}

		public void run() {
			try {
				byte[] in_data = new byte[buf_size];
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);

				while (true) {
					// read data from the incoming socket
					sk_in.receive(in_pkt);

					// check the length of the packet
					if (in_pkt.getLength() > 1000) {
						System.err.println("Error: received packet of length "
								+ in_pkt.getLength() + " from "
								+ in_pkt.getAddress().toString() + ":"
								+ in_pkt.getPort());
						System.exit(-1);
					}

					// decide if to corrupt the packet or not
					if (rnd.nextFloat() <= corrupt_pct) {
						for (int i = 0; i < in_pkt.getLength(); ++i)
							if (rnd_byte.nextFloat() <= byte_corrupt_pct)
								in_data[i] = (byte) ((in_data[i] + 1) % 10);
						System.out.println("Packet Corrupted");
					}

					// write data to the outgoing socket
					DatagramPacket out_pkt = new DatagramPacket(in_data, in_pkt.getLength(), dst_addr, dst_port);
					sk_out.send(out_pkt);
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public UnreliNETCorrupt(int sk1_dst_port, int sk2_dst_port, int sk3_dst_port, int sk4_dst_port, float ratio) {
		DatagramSocket sk1, sk2;
		
		corrupt_pct = ratio; 
		System.out.println("sk1_dst_port=" + sk1_dst_port
				+ ", sk2_dst_port=" + sk2_dst_port 
				+ ", sk3_dst_port="	+ sk3_dst_port
				+ ", sk4_dst_port=" + sk4_dst_port + " .");

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
					.println("Usage: java UnreliNETCorrupt sk1_dst_port, sk2_dst_port, sk3_dst_port, sk4_dst_port, ratio");
			System.exit(-1);
		} else {
			new UnreliNETCorrupt(Integer.parseInt(args[0]),
					Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),Float.parseFloat(args[4]));
		}
	}
}

