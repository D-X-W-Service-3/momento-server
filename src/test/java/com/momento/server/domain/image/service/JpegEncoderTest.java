package com.momento.server.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpegEncoderTest {

  @Test
  @DisplayName("PNG 를 같은 크기의 JPEG 로 바꾼다. 알파 채널이 있어도 된다")
  void convertsPngToJpeg() throws Exception {
    byte[] png = png(BufferedImage.TYPE_INT_ARGB, 64, 48);

    byte[] jpeg = JpegEncoder.fromPng(png);

    assertThat(jpeg).startsWith(0xFF, 0xD8);
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
    assertThat(decoded.getWidth()).isEqualTo(64);
    assertThat(decoded.getHeight()).isEqualTo(48);
  }

  @Test
  @DisplayName("이미지가 아니면 IllegalArgumentException 이다")
  void rejectsNonImage() {
    assertThatThrownBy(() -> JpegEncoder.fromPng("{\"detail\":\"x\"}".getBytes()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** 테스트 데이터용 PNG. */
  private static byte[] png(int type, int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, type);
    var graphics = image.createGraphics();
    graphics.setColor(Color.PINK);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
