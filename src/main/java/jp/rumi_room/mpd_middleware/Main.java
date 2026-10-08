package jp.rumi_room.mpd_middleware;

import java.io.IOException;
import su.rumishistem.rumi_java_logger.*;

public class Main {
	public static RumiJavaLogger logger;

	public static void main(String[] args) throws IOException {
		logger = new RumiJavaLogger();
		logger.hijack_std(SeverityLevel.Debug);

		new Thread(new Runnable() {
			@Override
			public void run() {
				MPD.init();
			}
		}).run();

		new Thread(new Runnable() {
			@Override
			public void run() {
				HTTPServer.init();
			}
		}).run();
	}
}
