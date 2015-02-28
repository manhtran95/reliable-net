package reliableNet;

import java.net.*;
import java.util.*;
import java.io.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import java.nio.ByteBuffer;

public class Sender {
	private static final int WINDOW_SIZE = 10;
	private static final int MAX_SIZE = 1000;
	
	static Timer[] timerArray = new Timer[MAX_SIZE];
	static byte[] timerOn = new byte[MAX_SIZE];
	static byte[] acked = new byte[MAX_SIZE];
	static boolean inThreadDone = false;
	static boolean socketInthreadClosed = false;
	static short endSeq = -3;
	static boolean endSent = false;
	static DatagramPacket[] storedPackets = new DatagramPacket[MAX_SIZE];
	private double esRTT = 0;
	private double devRTT = 0;
	private long[] startTime = new long[MAX_SIZE];
	private boolean firstReceivedPacket = true;	
	static short base = 0;
	static long timeout = 20;

	// *** OutThread *** //	

	public class OutThread extends Thread {
		private static final String KEY = "V4&g!d)56#()VJD";
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
			short baseValue = base;
			short nextSeqValue = nextSeq;
			if ((baseValue >= 0) && (baseValue <= MAX_SIZE - WINDOW_SIZE - 1)
					&& (nextSeqValue == baseValue + WINDOW_SIZE))
				return true;
			if ((baseValue >= MAX_SIZE - WINDOW_SIZE) && (baseValue <= MAX_SIZE - 1)
					&& (nextSeqValue == baseValue - (MAX_SIZE - WINDOW_SIZE)))
				return true;
			return false;
		}

		public void resendPackets(short seq) throws IOException {
			if (acked[seq] == 1 && storedPackets[seq] != null) {
				DatagramPacket pkt = storedPackets[seq];
				sk_out.send(pkt);
				startTime[seq] = System.currentTimeMillis();
			}
		}

		public void startTimer(long setTimeout, short seq)
				throws InterruptedException, IOException {
			final short seqValue = seq;
			if (setTimeout <= 0)
				setTimeout = 20;
			if (endSent)
				setTimeout = 2 * setTimeout;
			TimerTask timerTask = new TimerTask() {

				@Override
				public void run() {
					try {
						resendPackets(seqValue);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
			if (acked[seq] == 1 && timerOn[seq] == 0) {
				timerArray[seq] = new Timer();
				timerArray[seq].schedule(timerTask, setTimeout, setTimeout);
				timerOn[seq] = 1;
			}
		}

		public byte[] shortToBytes(short value) {
			ByteBuffer buffer = ByteBuffer.allocate(2);
			buffer.putShort(value);
			return buffer.array();
		}

		public void createStoreAndSendPackets(byte[] buffer, int length,
				InetAddress dst_addr) throws IOException, InterruptedException,
				SocketException {
			short nextSeqValue = nextSeq;
			byte[] seqArr = shortToBytes(nextSeqValue);
			byte[] checksumArr = calChecksum(buffer, length);
			buffer[length] = seqArr[0];
			buffer[length+1] = seqArr[1];
			length=length+2;
			for (int i = 0; i < 8; i++)
				buffer[length + i] = checksumArr[i];

			DatagramPacket out_pkt = new DatagramPacket(buffer, 0, length + 8,
					dst_addr, dst_port);

			storedPackets[nextSeqValue] = out_pkt;
			acked[nextSeqValue] = 1;
			timerOn[nextSeqValue] = 0;
			sk_out.send(out_pkt);

			startTime[nextSeqValue] = System.currentTimeMillis();

			if (nextSeq == MAX_SIZE - 1)
				nextSeq = 0;
			else
				nextSeq++;

			startTimer(timeout, nextSeqValue);
		}

		// *** OutThread run *** //

		public void run() {
			try {
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");

				try {
					File file = new File(path);
					InputStream is = new FileInputStream(file);
					int numBytesRead;
					byte[] buffer = new byte[200];
					byte[] bufFile = fileName.getBytes();
					for (int i = 0; i < bufFile.length; i++)
						buffer[i] = bufFile[i];

					createStoreAndSendPackets(buffer, bufFile.length, dst_addr);

					buffer = new byte[900];
					while ((numBytesRead = is.read(buffer, 0, 890)) != -1) {
						createStoreAndSendPackets(buffer, numBytesRead, dst_addr);
						buffer = new byte[900];

							while (fullWindow()) {
							}
					}

					is.close();

					byte[] bufEnd = new byte[100];
					endSeq = nextSeq;
					byte[] bufEndBytes = (new String(KEY))
							.getBytes();
					int len = bufEndBytes.length;
					for (int i = 0; i < len; i++)
						bufEnd[i] = bufEndBytes[i];
					byte[] arr = ByteBuffer.allocate(8).putLong(timeout)
							.array();
					for (int i = 0; i < 8; i++)
						bufEnd[len + i] = arr[i];
					endSent = true;

					createStoreAndSendPackets(bufEnd, len + 8, dst_addr);

					while (!socketInthreadClosed) {
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
					while (!inThreadDone) {
						sk_in.receive(in_pkt);
						short base1 = base;
						byte[] seqArr = new byte[2];
						seqArr[0] = in_pkt.getData()[0];
						seqArr[1] = in_pkt.getData()[1];
						short seq = bytesToShort(seqArr);
						boolean inWindow = false;

						if ( ( base1 <= (MAX_SIZE - WINDOW_SIZE) && (seq >= base1 && seq <= (base1 + WINDOW_SIZE - 1) ))
								|| ((base1 > (MAX_SIZE - WINDOW_SIZE)) && !(seq < base1 && seq > base1
										- (MAX_SIZE - WINDOW_SIZE + 1))))
							inWindow = true;

						if (inWindow
								&& !corrupt(in_pkt.getLength(),
										in_pkt.getData())) {

							if (timerOn[seq] == 1) {
								timerArray[seq].cancel();
								timerOn[seq] = -1;
							}

							acked[seq] = -1;
							short update = seq;
							if (seq == base)
								while (acked[update] == -1) {

									if (update == MAX_SIZE - 1)
										base = 0;
									else
										base = (short) (update + 1);

									if (base == 0)
										acked[MAX_SIZE - 1] = 0;
									else
										acked[base - 1] = 0;

									if (update == MAX_SIZE - 1)
										update = 0;
									else
										update++;
								}

							storedPackets[seq] = null;

							if (endSent) {
								boolean finished = true;
								for (int i = 0; i < MAX_SIZE; i++)
									if (acked[i] == 1)
										finished = false;
								if (finished) {
									inThreadDone = true;
								}
							}

							long endTime = System.currentTimeMillis();
							double samRTT = endTime - startTime[seq];

							if (!firstReceivedPacket) {
								devRTT = (0.75 * devRTT + 0.25 * Math
										.abs(samRTT - esRTT));
								esRTT = (0.875 * esRTT + 0.125 * samRTT);
							} else {
								devRTT = samRTT / 2;
								esRTT = samRTT;
								firstReceivedPacket = false;
							}

							timeout = (long) (esRTT + 4 * devRTT);
						}
					}

					socketInthreadClosed = true;
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
