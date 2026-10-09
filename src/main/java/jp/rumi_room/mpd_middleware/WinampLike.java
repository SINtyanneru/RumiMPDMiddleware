package jp.rumi_room.mpd_middleware;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import jp.rumi_room.mpd_middleware.Type.MPDState;
import su.rumishistem.rumi_java_logger.SeverityLevel;

public class WinampLike {
	private static AtomicReference<byte[]> png = new AtomicReference<>(new byte[0]);
	private static BufferedImage img;
	private static Graphics2D g;

	//点滅用
	private static boolean blink_t = false;
	private static long blink_last = 0;

	//タイトルスクロール用
	private static int title_scroll_offset = 0;
	private static long title_scroll_last = 0;

	//テーマ
	private static BufferedImage theme_main;
	private static BufferedImage theme_number;
	private static BufferedImage theme_text;
	private static BufferedImage theme_monoster;
	private static BufferedImage theme_playpause;
	private static BufferedImage theme_cbutton;
	private static BufferedImage theme_posbar;
	private static BufferedImage theme_volume;
	private static BufferedImage theme_pan;
	private static BufferedImage theme_shufrep;

	public static void init() {
		img = new BufferedImage(275, 116, BufferedImage.TYPE_INT_RGB);
		g = img.createGraphics();
		g.setComposite(AlphaComposite.SrcOver);

		//テーマファイルがなければエラー文を書く
		if (Files.exists(Path.of("theme.wsz")) == false) {
			g.setFont(new Font("Noto Sans JP", Font.PLAIN, 30));
			g.setColor(Color.red);
			g.drawString("theme.wszが", 10, 10 + 30);
			g.drawString("見つかりません。", 10, 10 + 30 * 2);

			write_image();
			return;
		}

		//theme.wszを解凍
		try {
			wsz_parse();
		} catch (Exception ex) {
			ex.printStackTrace();

			g.setFont(new Font("Noto Sans JP", Font.PLAIN, 30));
			g.setColor(Color.red);
			g.drawString("theme.wszを", 10, 10 + 30);
			g.drawString("解析できません。", 10, 10 + 30 * 2);

			write_image();

			return;
		}

		while (true) {
			int volume = MPD.get_mpd_volume();
			boolean stereo = MPD.get_mpd_stereo();
			double elapse = MPD.get_mpd_song_elapsed();
			double duration = MPD.get_mpd_song_duration();
			MPDState state = MPD.get_mpd_state();
			int bitrate = MPD.get_mpd_bitrate();
			int sps = MPD.get_mpd_sps();

			//ベース
			g.drawImage(theme_main, 0, 0, null);

			//再生ボタン類を描画
			draw_control_button();

			//シャッフルボタン
			draw_shuffre();

			//リピートボタン
			draw_repeat();

			//ボリューム
			draw_volume(volume);

			//パン
			draw_pan();

			//モノラルステレオ
			draw_monoste(state, stereo);

			//再生中か一時停止中のみ表示する物
			if (state == MPDState.Play || state == MPDState.Pause) {
				//シークバー
				draw_seekbar(duration, elapse);

				//再生時間
				draw_playtime(elapse, state);

				//ビットレート
				draw_bitrate(bitrate);

				//サンプリングレート
				draw_sps(sps);

				//タイトル
				draw_title(MPD.get_mpd_song_artist() + " - " + MPD.get_mpd_song_title());
			}

			//再生状態表示
			draw_state(state);

			write_image();

			//1秒後に再開
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	public static byte[] get_image() {
		return png.get();
	}

	private static void wsz_parse() throws IOException {
		ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream("theme.wsz")));
		try {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				if (entry.isDirectory()) continue;	

				switch (entry.getName()) {
					case "MAIN.BMP": {
						theme_main = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}

					case "NUMBERS.BMP": {
						theme_number = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}

					case "TEXT.PNG": {
						theme_text = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}

					case "MONOSTER.BMP": {
						theme_monoster = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}

					case "PLAYPAUS.BMP": {
						theme_playpause = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}

					case "CBUTTONS.BMP": {
						theme_cbutton = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}

					case "POSBAR.BMP": {
						theme_posbar = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}

					case "VOLUME.BMP": {
						theme_volume = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}

					case "BALANCE.BMP": {
						theme_pan = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}

					case "SHUFREP.BMP": {
						theme_shufrep = ImageIO.read(new ByteArrayInputStream(zis.readAllBytes()));
						break;
					}
				}

				Main.logger.print(SeverityLevel.Ok, "theme.wszから取得: " + entry.getName());
			}
		} finally {
			zis.close();
		}
	}

	private static void draw_image_crop(BufferedImage src, int sx, int sy, int sw, int sh, int dx, int dy) {
		g.drawImage(
			src,
			dx, dy, dx + sw, dy + sh,
			sx, sy, sx + sw, sy + sh,
			null
		);
	}

	/**
	 * 再生ボタン類を描画
	 */
	private static void draw_control_button() {
		//再生ボタン類
		draw_image_crop(theme_cbutton, 0, 0, 114, 17, 16, 88);

		//取り出しボタン
		draw_image_crop(theme_cbutton, 114, 0, 22, 16, 136, 89);

		//EQ
		draw_image_crop(theme_shufrep, 0, 61, 23, 12, 219, 58);

		//PL
		draw_image_crop(theme_shufrep, 23, 61, 23, 12, 242, 58);
	}

	/**
	 * シャッフルボタンを描画
	 */
	private static void draw_shuffre() {
		if (MPD.get_mpd_random()) {
			draw_image_crop(theme_shufrep, 28, 30, 47, 15, 164, 89);
		} else {
			draw_image_crop(theme_shufrep, 28, 0, 47, 15, 164, 89);
		}
	}

	/**
	 * リピートボタンを描画
	 */
	private static void draw_repeat() {
		if (MPD.get_mpd_repeat()) {
			draw_image_crop(theme_shufrep, 0, 30, 28, 15, 210, 89);
		} else {
			draw_image_crop(theme_shufrep, 0, 0, 28, 15, 210, 89);
		}
	}

	/**
	 * ボリュームつまみを描画
	 * 
	 * @param volume ボリューム
	 */
	private static void draw_volume(int volume) {
		//音量 - 背景
		int volume_bg_y = (int)(Math.round( (volume / 100.0) * 27) * 15 );
		draw_image_crop(theme_volume, 0, volume_bg_y, 64, 13, 107, 57);

		//音量 - つまみ
		int volume_thumb_x = 107 + (int)(Math.round( (volume / 100.0) * (68 - 14) ));
		draw_image_crop(theme_volume, 15, 422, 14, 11, volume_thumb_x, 58);
	}

	/**
	 * パンを描画
	 */
	private static void draw_pan() {
		//パン - 背景
		draw_image_crop(theme_volume, 9, 1, 38, 10, 177, 57);

		//パン - つまみ
		draw_image_crop(theme_pan, 15, 422, 14, 11, (177 + 38 / 2) - (14 / 2), 57);
	}

	/**
	 * モノラルステレオを描画
	 * 
	 * @param state MPDステート
	 * @param stereo ステレオか
	 */
	private static void draw_monoste(MPDState state, boolean stereo) {
		if (state == MPDState.Play || state == MPDState.Pause) {
			if (stereo) {
				//モノラル消灯
				draw_image_crop(theme_monoster, 29, 12, 27, 12, 212, 41);

				//ステレオ点灯
				draw_image_crop(theme_monoster, 0, 0, 27, 12, 239, 41);
			} else {
				//モノラル点灯
				draw_image_crop(theme_monoster, 29, 0, 27, 12, 212, 41);

				//ステレオ消灯
				draw_image_crop(theme_monoster, 0, 12, 27, 12, 239, 41);
			}
		} else {
			//どちらも消灯
			draw_image_crop(theme_monoster, 29, 12, 27, 12, 212, 41);
			draw_image_crop(theme_monoster, 0, 12, 27, 12, 239, 41);
		}
	}

	/**
	 * シークバーを描画する
	 * 
	 * @param duration 全体の長さ
	 * @param elapse 今の再生位置
	 */
	private static void draw_seekbar(double duration, double elapse) {
		//シークバー - 溝
		draw_image_crop(theme_posbar, 0, 0, 248, 10, 16, 72);

		//シークバー - つまみ
		double seekbar_ratio = (duration > 0) ? Math.max(0.0, Math.min(1.0, elapse / duration) ) : 0.0;
		int seekbar_thumb_x = 16 + (int)(Math.round(seekbar_ratio * 219));
		draw_image_crop(theme_posbar, 248, 0, 29, 10, seekbar_thumb_x, 72);
	}

	/**
	 * 再生時間を描画
	 * 
	 * @param elapse 今の再生位置
	 * @param state MPDステータス
	 */
	private static void draw_playtime(double elapse, MPDState state) {
		//再生時間を計算
		int time_total = (int)(Math.floor(elapse));
		int min = Math.min(time_total / 60, 99);	//2桁に納めろ
		int sec = time_total % 60;

		//一時停止中なら1秒ごとに点滅させる
		long now = System.currentTimeMillis();
		if (now - blink_last >= 1000) {
			blink_t = !blink_t;
			blink_last = now;
		}

		//再生中→表示 一時停止中&blink_tがtrueなら表示
		if (state == MPDState.Play || (state == MPDState.Pause && blink_t)){
			draw_image_crop(theme_number, (min / 10) * 9, 0, 9, 13, 48, 26);
			draw_image_crop(theme_number, (min % 10) * 9, 0, 9, 13, 60, 26);
			draw_image_crop(theme_number, (sec / 10) * 9, 0, 9, 13, 78, 26);
			draw_image_crop(theme_number, (sec % 10) * 9, 0, 9, 13, 90, 26);
		}
	}

	/**
	 * ビットレートを描画
	 * 
	 * @param bitrate ビットレート
	 */
	private static void draw_bitrate(int bitrate) {
		int kbps = Math.min(bitrate, 999);
		draw_text_number(kbps, 3, 111, 43);
	}

	/**
	 * サンプリングレートを描画
	 * 
	 * @param sps サンプリングレート
	 */
	private static void draw_sps(int sps) {
		int khz = Math.min(sps / 1000, 99);
		draw_text_number(khz, 2, 156, 43);
	}

	/**
	 * タイトルを描画
	 * 
	 * @param title タイトル
	 */
	private static void draw_title(String title) {
		g.setColor(new Color(0, 255, 0));
		g.setFont(new Font("DotGothic16", Font.PLAIN, 9));

		final FontMetrics fm = g.getFontMetrics();
		final int area_x = 111;
		final int area_y = 22;
		final int area_w = 154;
		final Shape old_clip = g.getClip();

		g.setClip(area_x, area_y, area_w, fm.getHeight());

		if (fm.stringWidth(title) <= area_w) {
			//タイトル普通に収まるならそのままね
			g.drawString(title, area_x, area_y + fm.getAscent());
		} else {
			//はみ出すやんけ！
			final String loop = title + "   ";

			//300msごとにスクロール
			long now = System.currentTimeMillis();
			if (now - title_scroll_last >= 300) {
				title_scroll_offset = (title_scroll_offset + 1) % loop.length();
				title_scroll_last = now;
			}

			//offsetから始まる循環文字列を、領域幅を超えるまで作る
			final String rotated = loop.substring(title_scroll_offset) + loop.substring(0, title_scroll_offset);
			final String edited_title = rotated + rotated;//二周
			g.drawString(edited_title, area_x, area_y + fm.getAscent());
		}

		g.setClip(old_clip);
	}

	/**
	 * ステートを描画
	 * 
	 * @param state MPDステート
	 */
	private static void draw_state(MPDState state) {
		int playpaus_x = 18;
		if (state == MPDState.Play) playpaus_x = 0;
		if (state == MPDState.Pause) playpaus_x = 9;
		draw_image_crop(theme_playpause, playpaus_x, 0, 9, 9, 24, 28);
	}

	/**
	 * TEXT.BMPから数字を描画します
	 * 
	 * @param value 数値
	 * @param digets 桁
	 * @param x X
	 * @param y Y
	 */
	private static void draw_text_number(int value, int digets, int x, int y) {
		int v = Math.max(0, value);
		for (int i = digets - 1; i >= 0; i--) {
			int d = v % 10;
			v /= 10;
			if (i == digets - 1 || d != 0 || v != 0) {
				draw_image_crop(theme_text, d * 5, 6, 5, 6, x + i * 5, y);
			}
		}
	}

	/**
	 * PNGとしてメモリに書き込みます
	 */
	private static void write_image() {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(img, "png", baos);
			png.set(baos.toByteArray());
			baos.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			return;
		}
	}
}
