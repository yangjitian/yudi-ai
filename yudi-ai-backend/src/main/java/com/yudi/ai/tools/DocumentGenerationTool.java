package com.yudi.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.yudi.ai.constant.FileConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class DocumentGenerationTool {

    private static final String IMAGE_PATTERN = "!\\[([^\\]]*)\\]\\(([^)]+)\\)";
    private static final Pattern IMAGE_PATTERN_COMPILED = Pattern.compile(IMAGE_PATTERN);
    private static final String DOC_DIR = FileConstant.FILE_SAVE_DIR + "/document";

    /**
     * 生成 Markdown 文件
     */
    @Tool(name = "generateMarkdown", description = "Generate a Markdown file with support for images")
    public String generateMarkdown(@ToolParam(description = "Markdown Filename") String fileName,
                                   @ToolParam(description = "Markdown Content") String content) {
        if (StrUtil.hasBlank(fileName, content)) {
            return "文件名或内容不能为空。";
        }

        String filePath = buildFilePath(fileName, ".md");
        try {
            FileUtil.writeUtf8String(content, filePath);
            return "Markdown 文件生成成功，路径：" + filePath;
        } catch (Exception e) {
            return "Markdown 文件生成失败：" + e.getMessage();
        }
    }

    /**
     * 生成 PDF 文件（支持中文和图片）
     */
    @Tool(name = "generatePdf", description = "Generate PDF files with support for Chinese characters and images")
    public String generatePdf(@ToolParam(description = "PDF Filename") String fileName,
                              @ToolParam(description = "PDF Content in Markdown format") String content) {
        if (StrUtil.hasBlank(fileName, content)) {
            return "文件名或内容不能为空。";
        }

        String filePath = buildFilePath(fileName, ".pdf");
        try {
            PdfWriter writer = new PdfWriter(filePath);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(50, 50, 50, 50);

            FontSet fonts = createPdfFonts();
            
            for (String line : content.split("\n")) {
                line = line.trim();
                if (StrUtil.isBlank(line)) {
                    document.add(new Paragraph(" ").setFont(fonts.font));
                    continue;
                }

                if (line.startsWith("#")) {
                    handleHeading(document, line, fonts);
                } else if (line.startsWith("![")) {
                    handleImage(document, line, fonts);
                } else {
                    handleText(document, line, fonts);
                }
            }

            document.close();
            return "PDF 文件生成成功，路径：" + filePath;
        } catch (Exception e) {
            return "PDF 生成失败：" + e.getMessage();
        }
    }

    /**
     * 生成 Word 文件（支持 Markdown 格式和图片）
     */
    @Tool(name = "generateWord", description = "Generate a Word file with support for Markdown format and images")
    public String generateWord(
            @ToolParam(description = "Word Filename") String fileName,
            @ToolParam(description = "Word Content in Markdown format") String content) {

        if (StrUtil.hasBlank(fileName, content)) {
            return "文件名或内容不能为空。";
        }

        String filePath = buildFilePath(fileName, ".docx");
        try {
            try (XWPFDocument document = new XWPFDocument();
                 FileOutputStream out = new FileOutputStream(filePath)) {

                for (String line : content.split("\n")) {
                    line = line.trim();
                    if (StrUtil.isBlank(line)) {
                        document.createParagraph();
                        continue;
                    }

                    if (line.startsWith("#")) {
                        handleWordHeading(document, line);
                    } else if (line.startsWith("![")) {
                        handleWordImage(document, line);
                    } else {
                        handleWordText(document, line);
                    }
                }

                document.write(out);
            }
            return "Word 文件生成成功，路径：" + filePath;
        } catch (Exception e) {
            return "Word 生成失败：" + e.getMessage();
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 构建文件路径
     */
    private String buildFilePath(String fileName, String extension) {
        if (!StrUtil.endWithIgnoreCase(fileName, extension)) {
            fileName += extension;
        }
        return DOC_DIR + "/" + fileName;
    }

    /**
     * 创建 PDF 字体集
     */
    private FontSet createPdfFonts() {
        PdfFont font = createFont("STSongStd-Light", "UniGB-UCS2-H",
                () -> createFont("STSong-Light", "UniGB-UCS2-H",
                        () -> createFont("", "",
                                () -> {
                                    try {
                                        return PdfFontFactory.createFont(StandardFonts.HELVETICA);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                })));

        PdfFont boldFont = null;
        PdfFont italicFont = null;
        try {
            boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            italicFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);
        } catch (Exception ignored) {
        }

        return new FontSet(font, boldFont, italicFont);
    }

    /**
     * 创建字体（带回退机制）
     */
    private PdfFont createFont(String fontName, String encoding, java.util.function.Supplier<PdfFont> fallback) {
        try {
            if (StrUtil.isBlank(fontName)) {
                return fallback.get();
            }
            return PdfFontFactory.createFont(fontName, encoding);
        } catch (Exception e) {
            return fallback.get();
        }
    }

    /**
     * 处理 PDF 标题
     */
    private void handleHeading(Document document, String line, FontSet fonts) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        String text = filterNonBmpCharacters(line.substring(level).trim());
        PdfFont titleFont = fonts.boldFont != null ? fonts.boldFont : fonts.font;
        int fontSize = switch (level) {
            case 1 -> 24;
            case 2 -> 20;
            default -> 16;
        };
        document.add(new Paragraph(new Text(text).setFont(titleFont)).setFont(fonts.font).setFontSize(fontSize));
    }

    /**
     * 处理 PDF 图片（直接从URL加载）
     */
    private void handleImage(Document document, String line, FontSet fonts) {
        Matcher matcher = IMAGE_PATTERN_COMPILED.matcher(line);
        if (!matcher.find()) {
            return;
        }
        String altText = matcher.group(1);
        String imageUrl = matcher.group(2);
        
        if (StrUtil.isBlank(imageUrl)) {
            return;
        }

        try {
            // 直接从URL加载图片
            ImageData imageData = ImageDataFactory.create(new URI(imageUrl).toURL());
            Image pdfImage = new Image(imageData);
            pdfImage.setAutoScale(true);
            pdfImage.setMaxWidth(PageSize.A4.getWidth() - 100);
            pdfImage.setMargins(10, 10, 10, 10);
            document.add(pdfImage);

            if (StrUtil.isNotBlank(altText)) {
                String filteredAlt = filterNonBmpCharacters(altText);
                document.add(new Paragraph(filteredAlt).setFont(fonts.font).setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY));
            }
        } catch (Exception e) {
            log.warn("加载图片失败: {}, 错误: {}", imageUrl, e.getMessage());
            PdfFont errorFont = fonts.italicFont != null ? fonts.italicFont : fonts.font;
            String errorText = filterNonBmpCharacters("[图片: " + altText + " - " + imageUrl + "]");
            document.add(new Paragraph(new Text(errorText).setFont(errorFont))
                    .setFont(fonts.font).setFontColor(ColorConstants.GRAY));
        }
    }

    /**
     * 处理 PDF 文本
     */
    private void handleText(Document document, String line, FontSet fonts) {
        String text = removeMarkdownFormat(line);
        text = filterNonBmpCharacters(text);
        if (StrUtil.isNotBlank(text)) {
            document.add(new Paragraph(text).setFont(fonts.font).setFontSize(12));
        }
    }

    /**
     * 处理 Word 标题
     */
    private void handleWordHeading(XWPFDocument document, String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }
        String text = line.substring(level).trim();
        int fontSize = switch (level) {
            case 1 -> 24;
            case 2 -> 20;
            default -> 16;
        };

        XWPFParagraph para = document.createParagraph();
        para.setStyle("Heading" + level);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(fontSize);
    }

    /**
     * 处理 Word 图片（直接从URL加载）
     */
    private void handleWordImage(XWPFDocument document, String line) {
        Matcher matcher = IMAGE_PATTERN_COMPILED.matcher(line);
        if (!matcher.find()) {
            return;
        }
        String altText = matcher.group(1);
        String imageUrl = matcher.group(2);
        
        if (StrUtil.isBlank(imageUrl)) {
            return;
        }

        try {
            XWPFParagraph imgPara = document.createParagraph();
            imgPara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun imgRun = imgPara.createRun();

            // 直接从URL下载图片字节
            byte[] imageBytes;
            try (InputStream imageStream = new URI(imageUrl).toURL().openStream()) {
                imageBytes = IoUtil.readBytes(imageStream);
            }
            
            String ext = FileUtil.extName(imageUrl).toLowerCase();
            int format = getWordImageFormat(ext);

            imgRun.addPicture(new java.io.ByteArrayInputStream(imageBytes),
                    format, imageUrl, Units.toEMU(400), Units.toEMU(300));

            if (StrUtil.isNotBlank(altText)) {
                XWPFParagraph captionPara = document.createParagraph();
                captionPara.setAlignment(ParagraphAlignment.CENTER);
                XWPFRun captionRun = captionPara.createRun();
                captionRun.setText(altText);
                captionRun.setFontSize(10);
                captionRun.setColor("808080");
                captionRun.setItalic(true);
            }
        } catch (Exception e) {
            log.warn("加载图片失败: {}, 错误: {}", imageUrl, e.getMessage());
            XWPFParagraph para = document.createParagraph();
            XWPFRun run = para.createRun();
            run.setText(StrUtil.format("[图片: {} - {}]", altText, imageUrl));
            run.setColor("808080");
            run.setItalic(true);
        }
    }

    /**
     * 处理 Word 文本
     */
    private void handleWordText(XWPFDocument document, String line) {
        String text = removeMarkdownFormat(line);
        if (StrUtil.isNotBlank(text)) {
            XWPFParagraph para = document.createParagraph();
            XWPFRun run = para.createRun();
            run.setText(text);
        }
    }

    /**
     * 移除 Markdown 格式标记
     */
    private String removeMarkdownFormat(String text) {
        return ReUtil.replaceAll(text, "\\*\\*([^*]+)\\*\\*", "$1")
                .replaceAll("\\*([^*]+)\\*", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
    }

    /**
     * 过滤掉超出 BMP 范围的字符
     */
    private String filterNonBmpCharacters(String text) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        return text.codePoints()
                .mapToObj(cp -> cp <= 0xFFFF ? String.valueOf((char) cp) : "?")
                .collect(java.util.stream.Collectors.joining());
    }

    /**
     * 获取 Word 图片格式
     */
    private int getWordImageFormat(String ext) {
        return switch (ext.toLowerCase()) {
            case "png" -> XWPFDocument.PICTURE_TYPE_PNG;
            case "gif" -> XWPFDocument.PICTURE_TYPE_GIF;
            case "bmp" -> XWPFDocument.PICTURE_TYPE_BMP;
            default -> XWPFDocument.PICTURE_TYPE_JPEG;
        };
    }


    /**
     * PDF 字体集合
     */
    private static class FontSet {
        final PdfFont font;
        final PdfFont boldFont;
        final PdfFont italicFont;

        FontSet(PdfFont font, PdfFont boldFont, PdfFont italicFont) {
            this.font = font;
            this.boldFont = boldFont;
            this.italicFont = italicFont;
        }
    }
}