package reliableNet;

import java.net.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.nio.ByteBuffer;

public class Receiver {
	static int pkt_size = 900;
	static short base = 0;
	static String fileName;
	static int max = 1000;
	static boolean firstPacket = true;
	static Timer timer;
	static boolean timerOn = false;
	static DatagramSocket sk2, sk3;
	static long timeout = 500;
	BufferedWriter bw;
	boolean bwClose = false;
	boolean check = false;
	byte[] buffered = new byte[max];

	// *** Calculate Checksum *** //

	public byte[] calChecksum(byte[] buf, int length) {
		Checksum checksum = new CRC32();
		try {
			BufferedInputStream is = new BufferedInputStream(
					new ByteArrayInputStream(buf, 0, length));
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
		byte[] newChecksumArr = calChecksum(in_data, length-10);
		for (int i = 0; i < 8; i++)
			if (newChecksumArr[i] != receivedChecksumArr[i]) {
				return true;
			}
		return false;
	}

	public void startTimer() {

		TimerTask timerTask = new TimerTask() {
			@Override
			public void run() {
				try {
					if (check) {
						boolean finished = true;
						for (int i = 0; i < max; i++)
							if (buffered[i] == 1) {
								finished = false;
							}
						if (finished) {
							bw.close();
							sk2.close();
							sk3.close();
							System.exit(2);
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		if (timeout <= 0)
			timeout = 500;

		timer = new Timer();
		timerOn = true;
		timer.schedule(timerTask, timeout, timeout);
	}

	// send packet
	public void sendPacket(byte[] out_data, InetAddress dst_addr,
			int sk3_dst_port) throws IOException {
		byte[] checksum = calChecksum(out_data, 2);
		for (byte i = 2; i < 10; i++)
			out_data[i] = checksum[i - 2];
		DatagramPacket out_pkt = new DatagramPacket(out_data, 10, dst_addr,
				sk3_dst_port);
		sk3.send(out_pkt);
	}

	public long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getLong();
	}
	
	public short bytesToShort(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getShort();
	}

	public Receiver(int sk2_dst_port, int sk3_dst_port, String outputPath)
			throws IOException {

		System.out.println("sk2_dst_port=" + sk2_dst_port + ", "
				+ "sk3_dst_port=" + sk3_dst_port + ".");

		// create sockets
		try {
			sk2 = new DatagramSocket(sk2_dst_port);
			sk3 = new DatagramSocket();
			boolean firstEnd = true;
			try {
				byte[] in_data = new byte[pkt_size];
				DatagramPacket in_pkt = new DatagramPacket(in_data,
						in_data.length);
				InetAddress dst_addr = InetAddress.getByName("127.0.0.1");
				byte[] out_data = new byte[10];
				DatagramPacket out_pkt;
				String[] store = new String[max];
				boolean inWindow = false;
				boolean noted = false;
				byte seqArr[] = new byte[2];
				short seq;
				
				while (true) {
					sk2.receive(in_pkt);
					if (!corrupt(in_pkt.getLength(), in_pkt.getData())) {
						
						seqArr[0] = in_pkt.getData()[in_pkt.getLength() - 10];
						seqArr[1] = in_pkt.getData()[in_pkt.getLength() - 9];
						seq = bytesToShort(seqArr);

						if (seq == 0)
							if (!noted) {
								fileName = new String(in_pkt.getData(),
										in_pkt.getOffset(),
										in_pkt.getLength() - 10);
								
								out_data[0] = seqArr[0];
								out_data[1] = seqArr[1];
								sendPacket(out_data, dst_addr, sk3_dst_port);								
								noted = true;
							}

						if (noted)
							if (seq != 0) {
								break;
							}						
					}
				}
				base = 1;

				String filePath = outputPath + "" + fileName;

				File file = new File(filePath);
				if (file.exists())
					file.delete();

				File folder = new File(outputPath);
				if (!folder.exists())
					folder.mkdirs();

				bw = new BufferedWriter(new FileWriter(new File(filePath)));
				store[0] = null;

				while (true) {
					inWindow = false;
					sk2.receive(in_pkt);

					if (timerOn) {
						timer.cancel();
						timerOn = false;
						startTimer();
					}

					seqArr[0] = in_pkt.getData()[in_pkt.getLength() - 10];
					seqArr[1] = in_pkt.getData()[in_pkt.getLength() - 9];
					seq = bytesToShort(seqArr);
					
					out_data[0] = seqArr[0];
					out_data[1] = seqArr[1];

					String str = new String(in_pkt.getData(),
							in_pkt.getOffset(), in_pkt.getLength() - 10);
					
					short base1 = base;

					if ((base1 <= max-10 && (seq >= base1 && seq <= base1 + 9))
							|| ((base1 > max-10) && !(seq < base1 && seq > base1 - (max-9)))) {
						inWindow = true;
					}

					if (inWindow
							&& !corrupt(in_pkt.getLength(), in_pkt.getData())) {

						if (str.substring(0, 15).equals("V4&g!d)56#()VJD")) {							
							
							check = true;
							byte[] timeoutArr = new byte[8];
							byte[] endArr = (new String("V4&g!d)56#()VJD"))
									.getBytes();
							int len = endArr.length;
							for (int i = len; i < len + 8; i++)
								timeoutArr[i - len] = in_pkt.getData()[i];
							timeout = bytesToLong(timeoutArr) * 40;
							startTimer();

							if (seq == base) {
								if (base == max-1)
									base = 0;
								else
									base++;

								if (base == 0) {
									buffered[max-1] = 0;
								}
								else {
									buffered[base - 1] = 0;
								}
							}
							continue;
						}

						sendPacket(out_data, dst_addr, sk3_dst_port);

						store[seq] = str;
						buffered[seq] = 1;

						short k = seq;						
						if (seq == base)
							while (buffered[k] == 1 && store[k] != null) {
								bw.write(store[k], 0, store[k].length());
								bw.flush();
								buffered[k] = 3;
								store[k] = null;
								if (k == max-1)
									k = 0;
								else
									k++;
								if (base == max-1)
									base = 0;
								else
									base++;

								if (base == 0) {
									buffered[max-1] = 0;
								}
								else {
									buffered[base - 1] = 0;
								}
							}
					}
					else if ((((base1 >= 10) && (seq >= base1 - 10 && seq <= base1 - 1)) || (base1 < 10 && !(seq >= base1 && seq <= base1 + max-11)))
							&& !corrupt(in_pkt.getLength(), in_pkt.getData())) {
						sendPacket(out_data, dst_addr, sk3_dst_port);
					}
				}
			} catch (IOException e) {
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(-1);
			} finally {
			}
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
		// parse parameters
		if (args.length != 3) {
			System.err
					.println("Usage: java TestReceiver sk2_dst_port, sk3_dst_port, outputFolderPath");
			System.exit(-1);
		} else
			new Receiver(Integer.parseInt(args[0]), Integer.parseInt(args[1]),
					args[2]);
	}
}
