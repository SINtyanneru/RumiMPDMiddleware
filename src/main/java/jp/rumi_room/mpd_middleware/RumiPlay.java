package jp.rumi_room.mpd_middleware;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

public class RumiPlay {
	private static AtomicReference<String> current_artist = new AtomicReference<>("");
	private static AtomicReference<String> current_title = new AtomicReference<>("");
	private static HttpClient hc = HttpClient.newBuilder().build();
	private static boolean first = true;

	public static void mpd_update(String artist, String title) {
		if (current_artist.get().equals(artist) && current_title.get().equals(title)) return;

		current_artist.set(artist);
		current_title.set(title);

		//起動直後はスキップ
		if (first) {
			first = false;
			return;
		}

		post();
	}

	private static void post() {
		String artist = current_artist.get();
		String title = current_title.get();
		String post_text = "#るみぷれ\n" + artist + " - " + title;

		try {
			post_misskey(post_text);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		try {
			post_fluxer(post_text);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		try {
			change_fluxer_status(artist + " - " + title);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void post_misskey(String text) throws IOException, InterruptedException {
		Map<String, Object> post_body = new HashMap<>();
		post_body.put("i", Config.Misskey.Token);
		post_body.put("text", text);
		post_body.put("localOnly", false);
		post_body.put("visibility", "public");
		post_body.put("cw", null);
		post_body.put("poll", null);
		post_body.put("reactionAcceptance", null);
		String json = new ObjectMapper().writeValueAsString(post_body);

		HttpRequest.Builder builder = HttpRequest.newBuilder();
		builder.uri(URI.create("https://" + Config.Misskey.Host + "/api/notes/create"));
		builder.header("Content-Type", "application/json; charset=UTF-8");
		builder.POST(HttpRequest.BodyPublishers.ofString(json));

		HttpRequest request = builder.build();
		/*HttpResponse<String> response = */hc.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static void post_fluxer(String text) throws IOException, InterruptedException {
		Map<String, Object> post_body = new HashMap<>();
		post_body.put("content", text);
		String json = new ObjectMapper().writeValueAsString(post_body);

		HttpRequest.Builder builder = HttpRequest.newBuilder();
		builder.uri(URI.create("https://api.fluxer.app/v1/channels/" + Config.Fluxer.ChannelID + "/messages"));
		builder.header("Content-Type", "application/json; charset=UTF-8");
		builder.header("Authorization", Config.Fluxer.Token);
		builder.POST(HttpRequest.BodyPublishers.ofString(json));

		HttpRequest request = builder.build();
		/*HttpResponse<String> response = */hc.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static void change_fluxer_status(String text) throws IOException, InterruptedException {
		Map<String, Object> post_body = new HashMap<>();
		post_body.put("custom_status", new HashMap<>(){{
			put("text", text);
			put("expires_at", null);
		}});
		String json = new ObjectMapper().writeValueAsString(post_body);

		HttpRequest.Builder builder = HttpRequest.newBuilder();
		builder.uri(URI.create("https://api.fluxer.app/v1/users/@me/settings"));
		builder.header("Content-Type", "application/json; charset=UTF-8");
		builder.header("Authorization", Config.Fluxer.Token);
		builder.method("PATCH", HttpRequest.BodyPublishers.ofString(json));

		HttpRequest request = builder.build();
		/*HttpResponse<String> response = */hc.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
