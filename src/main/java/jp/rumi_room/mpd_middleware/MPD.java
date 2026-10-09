package jp.rumi_room.mpd_middleware;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import jp.rumi_room.mpd_middleware.Type.MPDState;
import su.rumishistem.rumi_java_logger.SeverityLevel;

public class MPD {
	private final static String MPD_HOST = "192.168.100.120";
	private final static int MPD_PORT = 6600;
	private final static String CRLF = "\n";
	private final static int IDLE_TIMEOUT = 500;
	private final static int REPLY_TIMEOUT = 5000;
	private final static int CONNECT_RETRY_SEC = 1000;
	private final static int CONNECT_RETRY_MAX = 5;
	private final static int CONNECT_RETRY_MAX_AFTER_SEC = 28800;

	private static Optional<Socket> socket = Optional.empty();
	private static Optional<BufferedReader> in = Optional.empty();
	private static Optional<BufferedWriter> out = Optional.empty();
	private static int connect_retry_count = 0;

	private static AtomicReference<MPDState> mpd_state = new AtomicReference<>(MPDState.Unknown);
	private static AtomicInteger mpd_volume = new AtomicInteger(0);
	private static AtomicBoolean mpd_repeat = new AtomicBoolean(false);
	private static AtomicBoolean mpd_random = new AtomicBoolean(false);
	private static AtomicInteger mpd_bitrate = new AtomicInteger(0);
	private static AtomicInteger mpd_sps = new AtomicInteger(0);
	private static AtomicBoolean mpd_stereo = new AtomicBoolean(false);

	private static AtomicReference<String> mpd_song_artist = new AtomicReference<>("Unknown");
	private static AtomicReference<String> mpd_song_title = new AtomicReference<>("Unknown");
	private static AtomicReference<String> mpd_song_album = new AtomicReference<>("Unknown");
	private static AtomicReference<Double> mpd_song_elapsed = new AtomicReference<>(0.0);
	private static AtomicReference<Double> mpd_song_duration = new AtomicReference<>(0.0);

	public static void init() {
		while (true) {
			try {
				socket = Optional.of(new Socket());
				socket.get().connect(new InetSocketAddress(MPD_HOST, MPD_PORT));

				in = Optional.of(new BufferedReader(new InputStreamReader(socket.get().getInputStream(), StandardCharsets.UTF_8)));
				out = Optional.of(new BufferedWriter(new OutputStreamWriter(socket.get().getOutputStream(), StandardCharsets.UTF_8)));

				//Helloを読む
				String hello_message = in.get().readLine();
				if (hello_message == null || hello_message.startsWith("OK MPD") == false) {
					socket.get().close();
					throw new IOException("MPDサーバーではありません");
				}

				Main.logger.print(SeverityLevel.Ok, "MPDｻｰﾊﾞｰ("+MPD_HOST + ":" + MPD_PORT +")へ接続しました！");

				connect_retry_count = 0;

				sync();

				while (true) {
					send_command("idle");
					socket.get().setSoTimeout(IDLE_TIMEOUT);

					//500ms待つ仕組み
					try {
						String idle_retract_message = in.get().readLine();
						if (idle_retract_message == null) throw new IOException("切断された");
						if (idle_retract_message.startsWith("changed") == false) throw new IOException("なんか変なので死にまーす");

						//「OK」を消費
						in.get().readLine();
					} catch (SocketTimeoutException ex) {
						socket.get().setSoTimeout(REPLY_TIMEOUT);
						send_command("noidle");
						while (true) {
							String read_line = in.get().readLine();
							if (read_line == null) throw new IOException("切断された");
							if (read_line.equals("OK")) break;
							if (read_line.startsWith("ACK")) throw new IOException(read_line);
						}
					}

					socket.get().setSoTimeout(0);

					//同期
					sync();
				}
			} catch (IOException ex) {
				ex.printStackTrace();

				try {
					socket.get().close();
				} catch (Exception e) {
				}


				socket = Optional.empty();
				in = Optional.empty();
				out = Optional.empty();

				Main.logger.print(SeverityLevel.Error, "MPDｻｰﾊﾞｰ("+MPD_HOST + ":" + MPD_PORT +")へ接続できません、再試行します。");

				if (connect_retry_count > CONNECT_RETRY_MAX) {
					Main.logger.print(SeverityLevel.Error, CONNECT_RETRY_MAX_AFTER_SEC + "秒後に再試行します");

					try {
						Thread.sleep(CONNECT_RETRY_MAX_AFTER_SEC);
						connect_retry_count += 1;
					} catch (InterruptedException e) {
						return;
					}
					continue;
				}

				try {
					Thread.sleep(CONNECT_RETRY_SEC);
					connect_retry_count += 1;
				} catch (InterruptedException e) {
					return;
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public static MPDState get_mpd_state() {
		return mpd_state.get();
	}

	public static int get_mpd_volume() {
		return mpd_volume.get();
	}

	public static boolean get_mpd_repeat() {
		return mpd_repeat.get();
	}

	public static boolean get_mpd_random() {
		return mpd_random.get();
	}

	public static int get_mpd_bitrate() {
		return mpd_bitrate.get();
	}

	public static int get_mpd_sps() {
		return mpd_sps.get();
	}

	public static boolean get_mpd_stereo() {
		return mpd_stereo.get();
	}

	public static String get_mpd_song_artist() {
		return mpd_song_artist.get();
	}

	public static String get_mpd_song_title() {
		return mpd_song_title.get();
	}

	public static String get_mpd_song_album() {
		return mpd_song_album.get();
	}

	public static double get_mpd_song_elapsed() {
		return mpd_song_elapsed.get();
	}

	public static double get_mpd_song_duration() {
		return mpd_song_duration.get();
	}

	/**
	 * コマンドを送信
	 * 
	 * @param command コマンド
	 */
	private static void send_command(String command) throws IOException {
		if (out.isEmpty()) return;
		out.get().write(command + CRLF);
		out.get().flush();
	}

	/**
	 * 同期する
	 */
	private static void sync() throws IOException {
		String read_line;

		//statusを聞く
		send_command("status");
		while ((read_line = in.get().readLine()) != null) {
			if (read_line.equals("OK")) break;
			parse_status_response(read_line);
		}

		//currentsongを聞く
		send_command("currentsong");
		while ((read_line = in.get().readLine()) != null) {
			if (read_line.equals("OK")) break;
			parse_song_response(read_line);
		}
	}

	/**
	 * statusの応答を解析する
	 * 
	 * @param line 行
	 */
	private static void parse_status_response(String line) {
		int index = line.indexOf(':');
		String key = line.substring(0, index).toUpperCase();
		String value = line.substring(index + 1).replaceFirst(" ", "");

		switch (key) {
			case "STATE": {
				switch (value) {
					case "play":
						mpd_state.set(MPDState.Play);
						break;
					case "pause":
						mpd_state.set(MPDState.Pause);
						break;
					default:
						mpd_song_artist.set("");
						mpd_song_title.set("");
						mpd_song_album.set("");
						mpd_song_elapsed.set(0.0);
						mpd_song_duration.set(0.0);

						mpd_state.set(MPDState.Unknown);
						break;
				}
				break;
			}

			case "VOLUME": {
				mpd_volume.set(Integer.parseInt(value));
				break;
			}

			case "REPEAT": {
				mpd_repeat.set(value.equals("1"));
				break;
			}

			case "RANDOM": {
				mpd_random.set(value.equals("1"));
				break;
			}

			case "ELAPSED": {
				mpd_song_elapsed.set(Double.parseDouble(value));
				break;
			}

			case "DURATION": {
				mpd_song_duration.set(Double.parseDouble(value));
				break;
			}

			case "BITRATE": {
				mpd_bitrate.set(Integer.parseInt(value));
				break;
			}
		}
	}

	/**
	 * currentsongの応答を解析する
	 * 
	 * @param line 行
	 */
	private static void parse_song_response(String line) {
		int index = line.indexOf(':');
		String key = line.substring(0, index).toUpperCase();
		String value = line.substring(index + 1).replaceFirst(" ", "");

		switch (key) {
			case "ARTIST": {
				mpd_song_artist.set(value);
				break;
			}

			case "TITLE": {
				mpd_song_title.set(value);
				break;
			}

			case "ALBUM": {
				mpd_song_album.set(value);
				break;
			}

			case "FORMAT": {
				String[] format = value.split(":");
				mpd_sps.set(Integer.parseInt(format[0]));
				mpd_stereo.set(format[2].equals("2"));
				break;
			}
		}
	}
}
