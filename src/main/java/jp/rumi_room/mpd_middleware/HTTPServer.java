package jp.rumi_room.mpd_middleware;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.javalin.Javalin;
import io.javalin.http.*;
import jp.rumi_room.mpd_middleware.Type.MPDState;

public class HTTPServer {
	private static XmlMapper xml = new XmlMapper();

	public static void init() {
		Javalin app = Javalin.create();

		app.get("/api/Status", new Handler() {
			@Override
			public void handle(Context ctx) throws Exception {
				Map<String, Object> mpd_status = new HashMap<>();
				mpd_status.put("STATE", MPD.get_mpd_state().name());
				mpd_status.put("VOLUME", MPD.get_mpd_volume());
				mpd_status.put("REPEAT", MPD.get_mpd_repeat());
				mpd_status.put("RANDOM", MPD.get_mpd_random());

				mpd_status.put("BITRATE", MPD.get_mpd_bitrate());
				mpd_status.put("SPS", MPD.get_mpd_sps());
				mpd_status.put("STEREO", MPD.get_mpd_stereo());

				if (MPD.get_mpd_state() == MPDState.Play || MPD.get_mpd_state() == MPDState.Pause) {
					Map<String, Object> song = new HashMap<>();
					song.put("TITLE", MPD.get_mpd_song_title());
					song.put("ARTIST", MPD.get_mpd_song_artist());
					song.put("ALBUM", MPD.get_mpd_song_album());
					song.put("ELAPSED", MPD.get_mpd_song_elapsed());
					song.put("DURATION", MPD.get_mpd_song_duration());
					mpd_status.put("PLAYING_SONG", song);
				}

				String response_body = xml.writer().withRootName("MPD").writeValueAsString(mpd_status);
				ctx.status(200);
				ctx.contentType("application/xml; charset=UTF-8");
				ctx.result(response_body);
			}
		});

		app.start(6601);
	}
}
