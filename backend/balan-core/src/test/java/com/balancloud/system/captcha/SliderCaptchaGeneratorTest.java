package com.balancloud.system.captcha;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SliderCaptchaGeneratorTest {

    private final SliderCaptchaGenerator generator = new SliderCaptchaGenerator();

    @Test
    void generatedChallengeStaysInsideCanvas() throws IOException {
        for (int i = 0; i < 20; i++) {
            SliderCaptchaGenerator.Challenge challenge = generator.generate();

            assertThat(challenge.targetX())
                    .isBetween(SliderCaptchaGenerator.PIECE_SIZE * 2, SliderCaptchaGenerator.MAX_OFFSET);
            assertThat(challenge.pieceY())
                    .isBetween(10, SliderCaptchaGenerator.CANVAS_HEIGHT - SliderCaptchaGenerator.PIECE_SIZE - 10);
            assertThat(challenge.backgroundImage()).startsWith("data:image/jpeg;base64,");
            assertThat(challenge.pieceImage()).startsWith("data:image/png;base64,");

            BufferedImage background = decode(challenge.backgroundImage());
            assertThat(background.getWidth()).isEqualTo(SliderCaptchaGenerator.CANVAS_WIDTH);
            assertThat(background.getHeight()).isEqualTo(SliderCaptchaGenerator.CANVAS_HEIGHT);

            BufferedImage piece = decode(challenge.pieceImage());
            assertThat(piece.getWidth())
                    .isEqualTo(SliderCaptchaGenerator.PIECE_SIZE + SliderCaptchaGenerator.KNOB_RADIUS);
            assertThat(piece.getHeight()).isEqualTo(SliderCaptchaGenerator.CANVAS_HEIGHT);
            assertThat(piece.getRGB(0, 0) >>> 24).isZero();
        }
    }

    private BufferedImage decode(String dataUrl) throws IOException {
        String payload = dataUrl.substring(dataUrl.indexOf(',') + 1);
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(payload)));
    }
}
