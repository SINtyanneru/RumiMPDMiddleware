package jp.rumi_room.mpd_middleware;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import io.javalin.http.Context;

public class WinampLikeStream {
	private static byte[] initial_segment = null;
	private static Set<BlockingQueue<byte[]>> client_list = ConcurrentHashMap.newKeySet();

	private static Process ffmpeg = null;
	private static OutputStream ffmpeg_in = null;
	private static boolean error_stop = false;

	public static void init() throws IOException {
		ProcessBuilder pb = new ProcessBuilder(
			"/usr/bin/ffmpeg", "-hide_banner", "-loglevel", "warning",
			"-fflags", "nobuffer", "-flags", "low_delay",
			"-f", "rawvideo", "-pix_fmt", "bgr24", "-s", WinampLike.WIDTH + "x" + WinampLike.HEIGHT, "-framerate", String.valueOf(WinampLike.FPS), "-i", "-",
			"-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p",
			"-r", String.valueOf(WinampLike.FPS), "-c:v", "libx264", "-preset", "ultrafast", "-tune", "zerolatency",
			"-g", String.valueOf(WinampLike.FPS), "-keyint_min", String.valueOf(WinampLike.FPS), "-sc_threshold", "0",
			"-movflags", "frag_keyframe+empty_moov+default_base_moof",
			"-flush_packets", "1",
			"-f", "mp4", "pipe:1"
		);

		pb.redirectError(ProcessBuilder.Redirect.INHERIT);

		ffmpeg = pb.start();
		ffmpeg_in = ffmpeg.getOutputStream();

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					DataInputStream dis = new DataInputStream(new BufferedInputStream(ffmpeg.getInputStream()));
					ByteArrayOutputStream init_buffer = new ByteArrayOutputStream();
					ByteArrayOutputStream fragment = new ByteArrayOutputStream();

					while (true) {
						byte[] box = read_box(dis);
						String type = new String(box, 4, 4, StandardCharsets.ISO_8859_1);

						//初期化セグメント
						if (initial_segment == null) {
							init_buffer.write(box);
							if (type.equals("moov")) initial_segment = init_buffer.toByteArray();
							continue;
						}

						fragment.write(box);

						//mdatで1断片が完成する
						if (type.equals("mdat")) {
							byte[] data = fragment.toByteArray();
							fragment.reset();

							//キューに入れて配送する
							for (BlockingQueue<byte[]> queue:client_list) {
								//配送し、詰まってたらそいつを亡き者にする
								if (!queue.offer(data)) client_list.remove(queue);
							}
						}
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}).start();

		new Thread(() -> {
			while (!error_stop) {
				try {
					byte[] img = WinampLike.get_raw_image();
					if (img != null) {
						ffmpeg_in.write(img);
						ffmpeg_in.flush();
					}
					Thread.sleep(66);
				} catch (Exception ex) {
					ex.printStackTrace();
					error_stop = true;
				}
			}
		}).start();
	}

	public static void httpserver_endpoint(Context ctx) {
		BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(32);
		client_list.add(queue);

		try {
			//無いだろうが、初期化セグメントが来るまで待ってもらう
			while (initial_segment == null) Thread.sleep(50);

			//ヘッダー
			ctx.contentType("video/mp4");
			ctx.header("Accept-Ranges", "none");
			ctx.header("Cache-Control", "no-store, no-cache, must-revalidate");
			ctx.header("Pragma", "no-cache");
			ctx.header("Content-Disposition", "inline");
			ctx.header("Connection", "keep-alive");
			ctx.header("X-Accel-Buffering", "no");
			ctx.header("Access-Control-Allow-Origin", "*");

			//送信先
			OutputStream out = ctx.res().getOutputStream();

			ctx.res().flushBuffer();

			//初期化セグメントを送る
			out.write(initial_segment);
			out.flush();

			//キューを処理
			while (client_list.contains(queue)) {
				byte[] data = queue.poll(1, java.util.concurrent.TimeUnit.SECONDS);
				if (data == null) continue;
				out.write(data);
				out.flush();
			}
		} catch (Exception e) {
			//クライアント切断とか
		} finally {
			client_list.remove(queue);
		}
	}

	private static byte[] read_box(DataInputStream dis) throws IOException {
		byte[] head = new byte[8];
		dis.readFully(head);

		int size = ((head[0] & 0xFF) << 24) | ((head[1] & 0xFF) << 16) | ((head[2] & 0xFF) << 8) | (head[3] & 0xFF);
		if (size < 8) throw new IOException("invalid box size: " + size);

		byte[] box = new byte[size];
		System.arraycopy(head, 0, box, 0, 8);
		dis.readFully(box, 8, size - 8);

		return box;
	}
}
