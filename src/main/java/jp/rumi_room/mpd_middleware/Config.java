package jp.rumi_room.mpd_middleware;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;

public class Config {
	public static class MPD {
		public static String Host = null;
		public static int Port = 0;
	}

	public static class HTTP {
		public static int Port = 0;
	}

	public static class Misskey {
		public static String Host = null;
		public static String Token = null;
	}

	public static class Discord {
		public static String Token = null;
	}

	public static class Fluxer {
		public static String Host = null;
		public static String Token = null;
		public static String ChannelID = null;
	}

	public static void load() throws IOException {
		HashMap<String, Object> config = new HashMap<>();

		Path path = Path.of("./Config.ini");
		BufferedReader br = Files.newBufferedReader(path);

		try {
			String field = null;
			String line;
			while ((line = br.readLine()) != null) {
				if (line.equals("")) continue;

				if (line.startsWith("[") && line.endsWith("]")) {
					field = line.substring(1, line.length() - 1);
				} else {
					int index = line.indexOf('=');
					String key = field + "." + line.substring(0, index);
					String value = line.substring(index + 1);

					if (value.startsWith("\"") && value.endsWith("\"")) {
						config.put(key, value.substring(1, value.length() - 1));
					} else if (value.equals("true") || value.equals("false")) {
						config.put(key, value.equals("true"));
					} else {
						config.put(key, Integer.parseInt(value));
					}
				}
			}
		} finally {
			br.close();
		}

		MPD.Host = (String)config.get("MPD.HOST");
		MPD.Port = (int)config.get("MPD.PORT");

		HTTP.Port = (int)config.get("HTTP.PORT");

		Misskey.Host = (String)config.get("MISSKEY.HOST");
		Misskey.Token = (String)config.get("MISSKEY.TOKEN");

		Discord.Token = (String)config.get("DISCORD.TOKEN");

		Fluxer.Host = (String)config.get("FLUXER.HOST");
		Fluxer.Token = (String)config.get("FLUXER.TOKEN");
		Fluxer.ChannelID = (String)config.get("FLUXER.CHANNNEL_ID");
	}
}
