package com.yudi.ai.service.impl;

import com.yudi.ai.service.EmailService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件服务实现类
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
    
    @Resource
    private JavaMailSender javaMailSender;
    
    @Value("${spring.mail.from}")
    private String from;
    
    @Override
    public void sendEmail(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);

            javaMailSender.send(message);
            log.info("邮件发送成功，收件人：{}，主题：{}", to, subject);
        } catch (Exception e) {
            log.error("邮件发送失败，收件人：{}，主题：{}，错误：{}", to, subject, e.getMessage(), e);
        }
    }
}

