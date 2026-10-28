package com.balancloud.system.vo;

import com.balancloud.system.captcha.SliderCaptchaGenerator;
import lombok.Data;

@Data
public class SliderCaptchaVo {

    private boolean enabled;
    private String id;
    private String backgroundImage;
    private String pieceImage;
    private int pieceY;
    private int canvasWidth;
    private int canvasHeight;
    private int pieceSize;
    private int pieceImageWidth;
    private int maxOffset;
    private int expiresIn;

    public static SliderCaptchaVo disabled() {
        SliderCaptchaVo vo = new SliderCaptchaVo();
        vo.setEnabled(false);
        return vo;
    }

    public static SliderCaptchaVo of(String id, SliderCaptchaGenerator.Challenge challenge, int expiresIn) {
        SliderCaptchaVo vo = new SliderCaptchaVo();
        vo.setEnabled(true);
        vo.setId(id);
        vo.setBackgroundImage(challenge.backgroundImage());
        vo.setPieceImage(challenge.pieceImage());
        vo.setPieceY(challenge.pieceY());
        vo.setCanvasWidth(SliderCaptchaGenerator.CANVAS_WIDTH);
        vo.setCanvasHeight(SliderCaptchaGenerator.CANVAS_HEIGHT);
        vo.setPieceSize(SliderCaptchaGenerator.PIECE_SIZE);
        vo.setPieceImageWidth(SliderCaptchaGenerator.PIECE_SIZE + SliderCaptchaGenerator.KNOB_RADIUS);
        vo.setMaxOffset(SliderCaptchaGenerator.MAX_OFFSET);
        vo.setExpiresIn(expiresIn);
        return vo;
    }
}
