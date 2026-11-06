package com.weiyi.member.service;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.RedisService;
import com.weiyi.member.config.MemberProperties;
import com.weiyi.member.dto.CaptchaImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * 图形验证码：java.awt 自绘 PNG（老 kaptcha 是 javax 系不兼容 Boot 3），
 * 答案存 Redis（TTL 走 ntrain.member.captcha-code-cache，Nacos 可覆盖），一次一用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CHAR_POOL = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberProperties properties;
    private final RedisService redisService;

    public CaptchaImage generate() {
        char[] chars = new char[4];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = CHAR_POOL.charAt(RANDOM.nextInt(CHAR_POOL.length()));
        }
        String code = new String(chars);
        log.info("图形验证码：{}", code);
        String captchaKey = UUID.randomUUID().toString().replace("-", "");
        redisService.set(RedisKeys.captcha(captchaKey), code.toLowerCase(),
                Duration.ofMinutes(properties.getCaptchaCodeCache()));
        return new CaptchaImage(captchaKey, drawPng(code));
    }

    /** 校验并消费（一次一用）；不通过抛 CAPTCHA_ERROR */
    public void verifyAndConsume(String captchaKey, String code) {
        if (captchaKey == null || captchaKey.isBlank() || code == null || code.isBlank()) {
            log.warn("图形验证码校验失败：key 或验证码为空（captchaKey={} code={}）", captchaKey, code);
            throw new BusinessException(ErrorCode.CAPTCHA_ERROR);
        }
        String key = RedisKeys.captcha(captchaKey);
        String cached = redisService.get(key, String.class);
        redisService.delete(key);
        if (cached == null || !cached.equalsIgnoreCase(code.trim())) {
            log.warn("图形验证码校验失败：captchaKey={} 原因={}", captchaKey,
                    cached == null ? "已过期或不存在" : "验证码不匹配");
            throw new BusinessException(ErrorCode.CAPTCHA_ERROR);
        }
    }

    /** 仅探测答案是否匹配（不消费），下单验证码放行时先消费标记再校验 */
    public boolean matches(String captchaKey, String code) {
        if (captchaKey == null || code == null) {
            return false;
        }
        String cached = redisService.get(RedisKeys.captcha(captchaKey), String.class);
        return cached != null && cached.equalsIgnoreCase(code.trim());
    }

    public void consume(String captchaKey) {
        if (captchaKey != null) {
            redisService.delete(RedisKeys.captcha(captchaKey));
        }
    }

    @SuppressWarnings("java:S125")
    private String drawPng(String code) {
        int width = 120;
        int height = 40;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, width, height);
        for (int i = 0; i < 5; i++) {
            g.setColor(new Color(RANDOM.nextInt(200), RANDOM.nextInt(200), RANDOM.nextInt(200)));
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(RANDOM.nextInt(width), RANDOM.nextInt(height),
                    RANDOM.nextInt(width), RANDOM.nextInt(height));
        }
        g.setFont(new Font("Arial", Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(RANDOM.nextInt(120), RANDOM.nextInt(120), RANDOM.nextInt(160)));
            g.drawString(String.valueOf(code.charAt(i)), 12 + i * 26, 30 + RANDOM.nextInt(6));
        }
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "验证码生成失败");
        }
    }
}
