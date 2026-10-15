package jp.rumi_room.mpd_middleware;

import java.util.concurrent.atomic.AtomicReference;

public class RumiPlay {
	private static AtomicReference<String> current_artist = new AtomicReference<>("");
	private static AtomicReference<String> current_title = new AtomicReference<>("");
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

		
	}
}
