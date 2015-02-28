package reliableNet;

import java.net.*;
import java.util.*;
import java.io.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.nio.ByteBuffer;
//
public class Sender {
	static int pkt_size = 900;
	static int windowSize = 10;
	static short base = 0;
	static long timeout = 20;
	static int max = 1000;
	static Timer[] timerArr = new Timer[max];
	static byte[] timerOn = new byte[max];
	static byte[] acked = new byte[max];
	static boolean inDone = false;
	static boolean sk_in_close = false;
	static short endSeq = -3;
	static boolean endSent = false;
	static DatagramPacket[] store = new DatagramPacket[max];
	boolean check = false;
	private double esRTT = 0;
	private double devRTT = 0;
	private long[] startTime = new long[max];
	private boolean first = true;

	// *** OutThread *** //	

	public class OutThread extends Thread {
		private DatagramSocket sk_out;
		private int dst_port;
		private String path;
		private String fileName;
		private short nextSeq = 0;

		public OutThread(DatagramSocket sk_out, int dst_port, String path,
				String fileName) {
			this.sk_out = sk_out;
			this.dst_port = dst_port;
			this.path = path;
			this.fileName = fileName;
		}

		public boolean fullWindow() {
			short base1 = base;
			short nextSeq1 = nextSeq;
			if ((base1 >= 0) && (base1 <= max - 11)
					&& (nextSeq1 == base1 + windowSize))
				return true;
			if ((base1 >= max - 10) && (base1 <= max - 1)
					&& (nextSeq1 == base1 - (max - 10)))
				return true;
			return false;
		}

		public void resendPackets(short seq) throws IOException {
			if (acked[seq] == 1 && store[seq] != null) {
				DatagramPacket pkt = store[seq];
				sk_out.send(pkt);
				startTime[seq] = System.currentTimeMillis();
			}
		}

		public void startTimer(long setTimeout, short seq)
				throws InterruptedException, IOException {
			final short seq1 = seq;
			if (setTimeout <= 0)
				setTimeout = 20;
			if (endSent)
				setTimeout = 2 * setTimeout;
			TimerTask timerTask = new TimerTask() {

				@Override
				public void run() {
					try {
						resendPackets(seq1);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
			if (acked[seq] == 1 && timerOn[seq] == 0) {
				timerArr[seq] = new Timer();
				timerArr[seq].schedule(timerTask, setTimeout, setTimeout);
				timerOn[seq] = 1;
			}
		}

		public byte[] shortToBytes(short value) {
			ByteBuffer buffer = ByteBuffer.allocate(2);
			buffer.putShort(value);
			return buffer.array();
		}

		public void makeStoreAndSendPackets(byte[] buf, int c,
				InetAddress dst_addr) throws IOException, InterruptedException,
				SocketException {
			short nextSeq1 = nextSeq;
			byte[] seqArr = shortToBytes(nextSeq1);
			byte[] checksumArr = calChecksum(buf, c);
			buf[c] = seqArr[0];
			buf[c+1] = seqArr[1];
			c=c+2;
			for (int i = 0; i < 8; i++)
				buf[c + i] = checksumArr[i];

			DatagramPacket out_pkt = new DatagramPacket(buf, 0, c + 8,
					dst_addr, dst_port);

			store[nextSeq1] = out_pkt;
			acked[nextSeq1] = 1;
			timerOn[nextSeq1] = 0;
			sk_out.send(out_pkt);

			startTime[nextSeq1] = System.currentTimeMillis();

			if (nextSeq == max - 1)
				nextSeq = 0;
			else
				nextSeq++;

			startTimer(timeout, nextSeq1);
		}

		// *** OutThread run *** //

		public void run() {
			try {
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");

				try {
					File file = new File(path);
					InputStream is = new FileInputStream(file);
					int c;
					byte[] buf = new byte[200];
					byte[] bufFile = fileName.getBytes();
					for (int i = 0; i < bufFile.length; i++)
						buf[i] = bufFile[i];

					makeStoreAndSendPackets(buf, bufFile.length, dst_addr);

					buf = new byte[900];
					while ((c = is.read(buf, 0, 890)) != -1) {
						makeStoreAndSendPackets(buf, c, dst_addr);
						buf = new byte[900];

							while (fullWindow()) {
							}
					}

					is.close();

					byte[] bufEnd = new byte[100];
					endSeq = nextSeq;
					byte[] bufEndBytes = (new String("V4&g!d)56#()VJD"))
							.getBytes();
					int len = bufEndBytes.length;
					for (int i = 0; i < len; i++)
						bufEnd[i] = bufEndBytes[i];
					byte[] arr = ByteBuffer.allocate(8).putLong(timeout)
							.array();
					for (int i = 0; i < 8; i++)
						bufEnd[len + i] = arr[i];
					endSent = true;
					check = true;

					makeStoreAndSendPackets(bufEnd, len + 8, dst_addr);

					while (!sk_in_close) {
					}
					sk_out.close();
					System.exit(3);

				} catch (Exception e) {
					e.printStackTrace();
				} finally {
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	public byte[] calChecksum(byte[] buf, int c) {
		Checksum checksum = new CRC32();
		try {
			BufferedInputStream is = new BufferedInputStream(
					new ByteArrayInputStream(buf, 0, c));
			byte[] bytes = new byte[1000];
			int len;

			while ((len = is.read(bytes, 0, bytes.length)) >= 0) {
				checksum.update(bytes, 0, len);
			}
			is.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		long longChecksum = checksum.getValue();
		byte[] arr = ByteBuffer.allocate(8).putLong(longChecksum).array();
		return arr;
	}

	public boolean corrupt(int length, byte[] in_data) {
		byte[] receivedChecksumArr = new byte[8];
		int k = 7;
		for (int i = length - 1; i >= length - 8; i--) {
			receivedChecksumArr[k] = in_data[i];
			k--;
		}
		byte[] newChecksumArr = calChecksum(in_data, 2);
		for (int i = 0; i < 8; i++)
			if (newChecksumArr[i] != receivedChecksumArr[i]) {
				return true;
			}
		return false;
	}

	public short bytesToShort(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getShort();
	}
	
	// *** InThread *** //


	public class InThread extends Thread {
		private DatagramSocket sk_in;

		public InThread(DatagramSocket sk_in) {
			this.sk_in = sk_in;
		}

		public void run() {
			try {
				byte[] in_data = new byte[10];
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);

				try {
					while (!inDone) {
						sk_in.receive(in_pkt);
						short base1 = base;
						byte[] seqArr = new byte[2];
						seqArr[0] = in_pkt.getData()[0];
						seqArr[1] = in_pkt.getData()[1];
						short seq = bytesToShort(seqArr);
						boolean inWindow = false;

						if ( ( base1 <= (max - 10) && (seq >= base1 && seq <= (base1 + 9) ))
								|| ((base1 > (max - 10)) && !(seq < base1 && seq > base1
										- (max - 9))))
							inWindow = true;

						if (inWindow
								&& !corrupt(in_pkt.getLength(),
										in_pkt.getData())) {

							if (timerOn[seq] == 1) {
								timerArr[seq].cancel();
								timerOn[seq] = -1;
							}

							acked[seq] = -1;
							short update = seq;
							if (seq == base)
								while (acked[update] == -1) {

									if (update == max - 1)
										base = 0;
									else
										base = (short) (update + 1);

									if (base == 0)
										acked[max - 1] = 0;
									else
										acked[base - 1] = 0;

									if (update == max - 1)
										update = 0;
									else
										update++;
								}

							store[seq] = null;

							if (check) {
								boolean finished = true;
								for (int i = 0; i < max; i++)
									if (acked[i] == 1)
										finished = false;
								if (finished) {
									inDone = true;
								}
							}

							long endTime = System.currentTimeMillis();
							double samRTT = endTime - startTime[seq];

							if (!first) {
								devRTT = (0.75 * devRTT + 0.25 * Math
										.abs(samRTT - esRTT));
								esRTT = (0.875 * esRTT + 0.125 * samRTT);
							} else {
								devRTT = samRTT / 2;
								esRTT = samRTT;
								first = false;
							}

							timeout = (long) (esRTT + 4 * devRTT);
						}
					}

					sk_in_close = true;
					sk_in.close();

				} catch (Exception e) {
					e.printStackTrace();
				} finally {
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			}
		}
	}

	// *** Sender *** //

	public Sender(int sk1_dst_port, int sk4_dst_port, String path,
			String fileName) {
		DatagramSocket sk1, sk4;
		System.out.println("sk1_dst_port=" + sk1_dst_port + ", "
				+ "sk4_dst_port=" + sk4_dst_port + ".");

		try {
			// create sockets
			sk1 = new DatagramSocket();
			sk4 = new DatagramSocket(sk4_dst_port);

			// create threads to process data
			InThread th_in = new InThread(sk4);
			OutThread th_out = new OutThread(sk1, sk1_dst_port, path, fileName);

			th_in.start();
			th_out.start();
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	// *** main *** //

	public static void main(String[] args) {
		// parse parameters
		if (args.length != 4) {
			System.err
					.println("Usage: java TestSender sk1_dst_port, sk4_dst_port, inputFilePath, outputFileName");
			System.exit(-1);
		} else
			new Sender(Integer.parseInt(args[0]), Integer.parseInt(args[1]),
					args[2], args[3]);
	}
}
