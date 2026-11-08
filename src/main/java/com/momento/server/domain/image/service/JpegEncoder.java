package com.momento.server.domain.image.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * AI 서버가 준 PNG 를 JPEG 로 바꾼다. PNG 원본은 한 장에 약 1.7MB 라 편지 목록에서 여러 장을 휴대폰으로 받기에 무겁다. 일러스트라 JPEG 손실이 눈에
 * 띄지 않고 투명도도 쓰지 않는다. JDK 의 ImageIO 만 써서 의존성이 늘지 않는다.
 */
public final class JpegEncoder {

  public static final String CONTENT_TYPE = "image/jpeg";

  private static final float QUALITY = 0.9f;

  private JpegEncoder() {}

  /** 읽을 수 없는 이미지면 {@link IllegalArgumentException} 을 던진다. */
  public static byte[] fromPng(byte[] png) {
    BufferedImage source = read(png);
    // 알파 채널이 있는 이미지는 JPEG 인코더가 거절하므로 RGB 로 옮겨 그린다.
    BufferedImage rgb =
        new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
    rgb.createGraphics().drawImage(source, 0, 0, null);
    return write(rgb);
  }

  private static BufferedImage read(byte[] png) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
      if (image == null) {
        throw new IllegalArgumentException("이미지로 읽을 수 없는 응답이다.");
      }
      return image;
    } catch (IOException exception) {
      throw new IllegalArgumentException("이미지로 읽을 수 없는 응답이다.", exception);
    }
  }

  private static byte[] write(BufferedImage image) {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ImageWriteParam param = writer.getDefaultWriteParam();
    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    param.setCompressionQuality(QUALITY);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(stream);
      writer.write(null, new IIOImage(image, null, null), param);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }
}
